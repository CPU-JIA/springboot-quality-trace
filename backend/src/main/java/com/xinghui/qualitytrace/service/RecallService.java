package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.RecallLevel;
import com.xinghui.qualitytrace.common.enums.RecallStatus;
import com.xinghui.qualitytrace.common.enums.RecoveryStatus;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.dto.recall.RecallCreateRequest;
import com.xinghui.qualitytrace.dto.recall.RecallDetailResponse;
import com.xinghui.qualitytrace.dto.recall.RecallDetailUpdateRequest;
import com.xinghui.qualitytrace.dto.recall.RecallOrderResponse;
import com.xinghui.qualitytrace.dto.trace.TraceDownstreamRow;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.Customer;
import com.xinghui.qualitytrace.entity.RecallDetail;
import com.xinghui.qualitytrace.entity.RecallOrder;
import com.xinghui.qualitytrace.entity.Shipment;
import com.xinghui.qualitytrace.mapper.BatchMapper;
import com.xinghui.qualitytrace.mapper.CustomerMapper;
import com.xinghui.qualitytrace.mapper.RecallDetailMapper;
import com.xinghui.qualitytrace.mapper.RecallOrderMapper;
import com.xinghui.qualitytrace.mapper.ShipmentMapper;
import com.xinghui.qualitytrace.mapper.TraceMapper;
import com.xinghui.qualitytrace.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 召回服务 —— 发起召回、明细跟踪、完成归档（F7）
 *
 * <p>召回发起是系统追溯价值的集中体现：事务内执行正向追溯、生成召回单与明细、
 * 将源头批次和受影响批次置为 RECALLED。任何一步失败都整体回滚，避免漏召回。</p>
 */
@Service
@RequiredArgsConstructor
public class RecallService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RecallOrderMapper recallOrderMapper;
    private final RecallDetailMapper recallDetailMapper;
    private final BatchMapper batchMapper;
    private final ShipmentMapper shipmentMapper;
    private final CustomerMapper customerMapper;
    private final TraceMapper traceMapper;
    private final SerialNumberService serialNumberService;

    /** 召回单分页查询。 */
    public PageResult<RecallOrderResponse> page(long current,
                                                long size,
                                                RecallStatus status,
                                                RecallLevel recallLevel,
                                                String keyword) {
        Page<RecallOrder> page = recallOrderMapper.selectPageForList(PageBounds.page(current, size),
                status, recallLevel, StrUtil.trimToNull(keyword));
        List<RecallOrderResponse> rows = page.getRecords().stream()
                .map(order -> toResponse(order, List.of()))
                .toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 发起召回：正向追溯源头批次，按受影响批次与出货流向生成召回明细。
     */
    @Transactional(rollbackFor = Exception.class)
    public RecallOrder create(RecallCreateRequest request) {
        Batch source = requireBatchForUpdate(request.getSourceBatchId());
        if (source.getStatus() == BatchStatus.RECALLED) {
            throw new BusinessException("源头批次已处于召回状态，不能重复发起召回");
        }
        if (source.getStatus() == BatchStatus.SCRAPPED || source.getStatus() == BatchStatus.RETURNED) {
            throw new BusinessException("源头批次已归档为报废/退货，不能发起召回");
        }

        List<TraceDownstreamRow> downstream = traceMapper.downstream(source.getId());
        Map<Long, List<TraceDownstreamRow>> affected = downstream.stream()
                .collect(Collectors.groupingBy(TraceDownstreamRow::getBatchId, LinkedHashMap::new, Collectors.toList()));
        if (affected.isEmpty()) {
            affected.put(source.getId(), List.of());
        }
        List<Batch> affectedBatches = new ArrayList<>();
        boolean hasRecoverableScope = false;
        for (Map.Entry<Long, List<TraceDownstreamRow>> entry : affected.entrySet()) {
            Batch batch = source.getId().equals(entry.getKey()) ? source : requireBatchForUpdate(entry.getKey());
            affectedBatches.add(batch);
            boolean hasShipment = entry.getValue().stream().anyMatch(row -> row.getShipmentId() != null);
            hasRecoverableScope = hasRecoverableScope || hasShipment || hasRemainingStock(batch);
        }
        if (!hasRecoverableScope) {
            throw new BusinessException("该批次无出货流向且无在库余量，不能生成召回单");
        }

        RecallOrder order = new RecallOrder();
        order.setRecallNo(nextRecallNo(LocalDate.now()));
        order.setSourceBatchId(source.getId());
        order.setRecallLevel(request.getRecallLevel());
        order.setReason(request.getReason());
        order.setStatus(RecallStatus.IN_PROGRESS);
        order.setInitiatorId(UserContext.currentUserId());
        recallOrderMapper.insert(order);

        for (Batch batch : affectedBatches) {
            markRecalled(batch);
            createDetailsForAffectedBatch(order.getId(), batch, affected.get(batch.getId()));
        }
        return order;
    }

    /** 召回单详情。 */
    public RecallOrderResponse detail(Long id) {
        RecallOrder order = requireRecallOrder(id);
        List<RecallDetail> details = recallDetailMapper.selectList(new LambdaQueryWrapper<RecallDetail>()
                .eq(RecallDetail::getRecallOrderId, id)
                .orderByAsc(RecallDetail::getId));
        return toResponse(order, assembleDetails(details));
    }

    /** 更新召回明细回收状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateDetail(Long detailId, RecallDetailUpdateRequest request) {
        RecallDetail probe = recallDetailMapper.selectById(detailId);
        if (probe == null) {
            throw new BusinessException("召回明细不存在或已被删除");
        }
        RecallOrder order = requireRecallOrderForUpdate(probe.getRecallOrderId());
        if (order.getStatus() == RecallStatus.COMPLETED) {
            throw new BusinessException("召回单已完成，不能修改明细状态");
        }
        RecallDetail detail = recallDetailMapper.selectByIdForUpdate(detailId);
        if (detail == null) {
            throw new BusinessException("召回明细不存在或已被删除");
        }
        if (!detail.getRecallOrderId().equals(order.getId())) {
            throw new BusinessException("召回明细所属召回单已变化，请刷新后重试");
        }
        checkRecoveryTransition(detail, request.getRecoveryStatus());
        if (request.getRecoveryStatus() == RecoveryStatus.UNRECOVERABLE && StrUtil.isBlank(request.getRemark())) {
            throw new BusinessException("无法回收必须填写原因说明");
        }
        detail.setRecoveryStatus(request.getRecoveryStatus());
        detail.setRemark(request.getRemark());
        recallDetailMapper.updateById(detail);
    }

    /** 完成召回单：所有明细必须达到终态。 */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long recallOrderId) {
        RecallOrder order = requireRecallOrderForUpdate(recallOrderId);
        if (order.getStatus() == RecallStatus.COMPLETED) {
            throw new BusinessException("召回单已完成，不能重复关闭");
        }
        List<RecallDetail> details = recallDetailMapper.selectByRecallOrderIdForUpdate(recallOrderId);
        if (details.isEmpty()) {
            throw new BusinessException("召回单无明细，不能完成");
        }
        boolean allFinal = details.stream().allMatch(detail -> detail.getRecoveryStatus().isFinal());
        if (!allFinal) {
            throw new BusinessException("仍有召回明细未达到终态，不能完成召回单");
        }
        order.setStatus(RecallStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        recallOrderMapper.updateById(order);
    }

    // ---------------- 私有辅助 ----------------

    private Batch requireBatch(Long id) {
        Batch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException("批次不存在或已被删除");
        }
        return batch;
    }

    private Batch requireBatchForUpdate(Long id) {
        Batch batch = batchMapper.selectByIdForUpdate(id);
        if (batch == null) {
            throw new BusinessException("批次不存在或已被删除");
        }
        return batch;
    }

    private RecallOrder requireRecallOrder(Long id) {
        RecallOrder order = recallOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("召回单不存在或已被删除");
        }
        return order;
    }

    private RecallOrder requireRecallOrderForUpdate(Long id) {
        RecallOrder order = recallOrderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException("召回单不存在或已被删除");
        }
        return order;
    }

    private void markRecalled(Batch batch) {
        if (batch.getStatus() == BatchStatus.RECALLED
                || batch.getStatus() == BatchStatus.SCRAPPED
                || batch.getStatus() == BatchStatus.RETURNED) {
            return;
        }
        batch.getStatus().checkTransitionTo(BatchStatus.RECALLED);
        batch.setStatus(BatchStatus.RECALLED);
        batchMapper.updateById(batch);
    }

    private void checkRecoveryTransition(RecallDetail detail, RecoveryStatus target) {
        RecoveryStatus current = detail.getRecoveryStatus();
        if (current.canTransitionTo(target)) {
            return;
        }
        boolean inStockIsolation = detail.getShipmentId() == null;
        if (inStockIsolation && current == RecoveryStatus.PENDING && target.isFinal()) {
            return;
        }
        if (!inStockIsolation && current == RecoveryStatus.PENDING && target.isFinal()) {
            throw new BusinessException("已出货召回明细需先通知客户，不能从待通知直接进入终态");
        }
        current.checkTransitionTo(target);
    }

    private void createDetailsForAffectedBatch(Long recallOrderId, Batch batch, List<TraceDownstreamRow> rows) {
        Set<Long> shipmentIds = rows.stream()
                .map(TraceDownstreamRow::getShipmentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        for (Long shipmentId : shipmentIds) {
            RecallDetail detail = new RecallDetail();
            detail.setRecallOrderId(recallOrderId);
            detail.setAffectedBatchId(batch.getId());
            detail.setShipmentId(shipmentId);
            detail.setRecoveryStatus(RecoveryStatus.PENDING);
            recallDetailMapper.insert(detail);
        }
        if (hasRemainingStock(batch)) {
            RecallDetail inStock = new RecallDetail();
            inStock.setRecallOrderId(recallOrderId);
            inStock.setAffectedBatchId(batch.getId());
            inStock.setRecoveryStatus(RecoveryStatus.PENDING);
            inStock.setRemark("在库部分需就地隔离");
            recallDetailMapper.insert(inStock);
        }
    }

    private boolean hasRemainingStock(Batch batch) {
        return batch != null
                && batch.getRemainingQuantity() != null
                && batch.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0;
    }

    private RecallOrderResponse toResponse(RecallOrder order, List<RecallDetailResponse> details) {
        Batch source = batchMapper.selectById(order.getSourceBatchId());
        return RecallOrderResponse.builder()
                .id(order.getId())
                .recallNo(order.getRecallNo())
                .sourceBatchId(order.getSourceBatchId())
                .sourceBatchNo(source == null ? null : source.getBatchNo())
                .recallLevel(order.getRecallLevel())
                .reason(order.getReason())
                .status(order.getStatus())
                .initiatorId(order.getInitiatorId())
                .completedAt(order.getCompletedAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .details(details)
                .build();
    }

    private List<RecallDetailResponse> assembleDetails(List<RecallDetail> details) {
        if (details.isEmpty()) {
            return List.of();
        }
        Map<Long, Batch> batchById = batchMapper.selectByIds(details.stream()
                        .map(RecallDetail::getAffectedBatchId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Batch::getId, Function.identity()));
        List<Long> shipmentIds = details.stream()
                .map(RecallDetail::getShipmentId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<Shipment> shipments = shipmentIds.isEmpty()
                ? List.of()
                : shipmentMapper.selectByIds(shipmentIds);
        Map<Long, Shipment> shipmentById = shipments.stream()
                .collect(Collectors.toMap(Shipment::getId, Function.identity()));
        Map<Long, Customer> customerById = shipments.isEmpty()
                ? Map.of()
                : customerMapper.selectByIds(shipments.stream().map(Shipment::getCustomerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));

        List<RecallDetailResponse> rows = new ArrayList<>();
        for (RecallDetail detail : details) {
            Batch batch = batchById.get(detail.getAffectedBatchId());
            Shipment shipment = detail.getShipmentId() == null ? null : shipmentById.get(detail.getShipmentId());
            Customer customer = shipment == null ? null : customerById.get(shipment.getCustomerId());
            rows.add(RecallDetailResponse.builder()
                    .id(detail.getId())
                    .recallOrderId(detail.getRecallOrderId())
                    .affectedBatchId(detail.getAffectedBatchId())
                    .affectedBatchNo(batch == null ? null : batch.getBatchNo())
                    .shipmentId(detail.getShipmentId())
                    .shipmentNo(shipment == null ? null : shipment.getShipmentNo())
                    .customerName(customer == null ? null : customer.getName())
                    .recoveryStatus(detail.getRecoveryStatus())
                    .remark(detail.getRemark())
                    .createdAt(detail.getCreatedAt())
                    .updatedAt(detail.getUpdatedAt())
                    .build());
        }
        return rows.stream()
                .sorted(Comparator.comparing(RecallDetailResponse::getAffectedBatchNo,
                        Comparator.nullsLast(String::compareTo)).thenComparing(RecallDetailResponse::getId))
                .toList();
    }

    private String nextRecallNo(LocalDate date) {
        String prefix = "RC-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }
}
