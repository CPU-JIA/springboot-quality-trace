package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.dto.shipment.ShipmentCreateRequest;
import com.xinghui.qualitytrace.dto.shipment.ShipmentResponse;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.Customer;
import com.xinghui.qualitytrace.entity.Material;
import com.xinghui.qualitytrace.entity.Shipment;
import com.xinghui.qualitytrace.mapper.BatchMapper;
import com.xinghui.qualitytrace.mapper.CustomerMapper;
import com.xinghui.qualitytrace.mapper.MaterialMapper;
import com.xinghui.qualitytrace.mapper.ShipmentMapper;
import com.xinghui.qualitytrace.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 出货服务 —— 成品出货登记（F6-1）
 *
 * <p>出货与生产领料采用同一类原子扣减模型：数据库在 UPDATE 条件中同时校验
 * 批次状态和余量，防止并发下超出货或召回后仍出货。</p>
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ShipmentMapper shipmentMapper;
    private final BatchMapper batchMapper;
    private final MaterialMapper materialMapper;
    private final CustomerMapper customerMapper;
    private final SerialNumberService serialNumberService;

    /** 出货记录分页查询。 */
    public PageResult<ShipmentResponse> page(long current, long size, Long batchId, Long customerId, String keyword) {
        Page<Shipment> page = shipmentMapper.selectPageForList(PageBounds.page(current, size),
                batchId, customerId, StrUtil.trimToNull(keyword));
        return PageResult.of(assemble(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 成品出货：扣减成品批次库存并记录客户流向。
     */
    @Transactional(rollbackFor = Exception.class)
    public Shipment create(ShipmentCreateRequest request) {
        Batch batch = batchMapper.selectById(request.getBatchId());
        if (batch == null) {
            throw new BusinessException("出货批次不存在");
        }
        Material material = materialMapper.selectById(batch.getMaterialId());
        if (material == null || material.getCategory() != MaterialCategory.PRODUCT) {
            throw new BusinessException("只有成品批次允许出货");
        }
        Customer customer = customerMapper.selectById(request.getCustomerId());
        if (customer == null) {
            throw new BusinessException("客户不存在或已被删除");
        }
        if (customer.getStatus() == null || customer.getStatus() != 1) {
            throw new BusinessException("客户已停用，不可登记出货");
        }
        validateShipDate(batch, request.getShipDate());

        int affected = batchMapper.deductQualifiedStock(batch.getId(), request.getQuantity());
        if (affected == 0) {
            throw new BusinessException("出货批次不是合格在库状态或余量不足");
        }

        Shipment shipment = new Shipment();
        shipment.setShipmentNo(nextShipmentNo(request.getShipDate()));
        shipment.setBatchId(batch.getId());
        shipment.setCustomerId(customer.getId());
        shipment.setQuantity(request.getQuantity());
        shipment.setShipDate(request.getShipDate());
        shipment.setOperatorId(UserContext.currentUserId());
        shipment.setRemark(request.getRemark());
        shipmentMapper.insert(shipment);
        return shipment;
    }

    private void validateShipDate(Batch batch, LocalDate shipDate) {
        if (shipDate.isAfter(LocalDate.now())) {
            throw new BusinessException("出货日期不能晚于今天");
        }
        if (batch.getProductionDate() != null && shipDate.isBefore(batch.getProductionDate())) {
            throw new BusinessException("出货日期不能早于批次生产日期");
        }
    }

    private List<ShipmentResponse> assemble(List<Shipment> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Batch> batchById = batchMapper.selectByIds(rows.stream()
                        .map(Shipment::getBatchId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Batch::getId, Function.identity()));
        Map<Long, Customer> customerById = customerMapper.selectByIds(rows.stream()
                        .map(Shipment::getCustomerId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        return rows.stream().map(row -> {
            Batch batch = batchById.get(row.getBatchId());
            Customer customer = customerById.get(row.getCustomerId());
            return ShipmentResponse.builder()
                    .id(row.getId())
                    .shipmentNo(row.getShipmentNo())
                    .batchId(row.getBatchId())
                    .batchNo(batch == null ? null : batch.getBatchNo())
                    .customerId(row.getCustomerId())
                    .customerName(customer == null ? null : customer.getName())
                    .quantity(row.getQuantity())
                    .shipDate(row.getShipDate())
                    .operatorId(row.getOperatorId())
                    .remark(row.getRemark())
                    .createdAt(row.getCreatedAt())
                    .build();
        }).toList();
    }

    private String nextShipmentNo(LocalDate date) {
        String prefix = "SH-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }
}
