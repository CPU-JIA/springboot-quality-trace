package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.enums.SourceType;
import com.xinghui.qualitytrace.common.enums.TaskStatus;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.dto.batch.BatchConsumptionBrief;
import com.xinghui.qualitytrace.dto.batch.BatchDetailResponse;
import com.xinghui.qualitytrace.dto.batch.BatchDefectBrief;
import com.xinghui.qualitytrace.dto.batch.BatchShipmentBrief;
import com.xinghui.qualitytrace.dto.batch.InspectionTaskBrief;
import com.xinghui.qualitytrace.dto.batch.PurchaseInboundRequest;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.BatchConsumption;
import com.xinghui.qualitytrace.entity.BatchOverview;
import com.xinghui.qualitytrace.entity.Customer;
import com.xinghui.qualitytrace.entity.DefectRecord;
import com.xinghui.qualitytrace.entity.InspectionTask;
import com.xinghui.qualitytrace.entity.Material;
import com.xinghui.qualitytrace.entity.ProductionOrder;
import com.xinghui.qualitytrace.entity.Shipment;
import com.xinghui.qualitytrace.entity.Supplier;
import com.xinghui.qualitytrace.mapper.BatchConsumptionMapper;
import com.xinghui.qualitytrace.mapper.BatchMapper;
import com.xinghui.qualitytrace.mapper.BatchOverviewMapper;
import com.xinghui.qualitytrace.mapper.CustomerMapper;
import com.xinghui.qualitytrace.mapper.DefectRecordMapper;
import com.xinghui.qualitytrace.mapper.InspectionItemMapper;
import com.xinghui.qualitytrace.mapper.InspectionTaskMapper;
import com.xinghui.qualitytrace.mapper.MaterialMapper;
import com.xinghui.qualitytrace.mapper.ProductionOrderMapper;
import com.xinghui.qualitytrace.mapper.ShipmentMapper;
import com.xinghui.qualitytrace.mapper.SupplierMapper;
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
 * 批次服务 —— 原材料入库、批次台账与批次详情（F3-1/F3-2）
 *
 * <p>当前阶段先落采购入库最小闭环：创建原材料批次 + 自动生成 IQC 检验任务。
 * 两个写操作在同一事务中提交，避免"有批次无检验任务"导致来料绕过检验流出。</p>
 */
@Service
@RequiredArgsConstructor
public class BatchService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final BatchMapper batchMapper;
    private final BatchOverviewMapper overviewMapper;
    private final InspectionTaskMapper taskMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final DefectRecordMapper defectMapper;
    private final BatchConsumptionMapper consumptionMapper;
    private final ProductionOrderMapper orderMapper;
    private final ShipmentMapper shipmentMapper;
    private final CustomerMapper customerMapper;
    private final InspectionItemMapper itemMapper;
    private final SerialNumberService serialNumberService;

    /**
     * 批次台账分页查询。keyword 对批次号/物料编码/物料名称/供应商名称/工单号模糊匹配。
     */
    public PageResult<BatchOverview> page(long current,
                                          long size,
                                          String keyword,
                                          BatchStatus status,
                                          SourceType sourceType,
                                          MaterialCategory materialCategory,
                                          Long materialId) {
        Page<BatchOverview> page = overviewMapper.selectPage(PageBounds.page(current, size),
                new LambdaQueryWrapper<BatchOverview>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(BatchOverview::getBatchNo, keyword)
                                .or().like(BatchOverview::getMaterialCode, keyword)
                                .or().like(BatchOverview::getMaterialName, keyword)
                                .or().like(BatchOverview::getSupplierName, keyword)
                                .or().like(BatchOverview::getProductionOrderNo, keyword))
                        .eq(status != null, BatchOverview::getStatus, status)
                        .eq(sourceType != null, BatchOverview::getSourceType, sourceType)
                        .eq(materialCategory != null, BatchOverview::getMaterialCategory, materialCategory)
                        .eq(materialId != null, BatchOverview::getMaterialId, materialId)
                        .orderByDesc(BatchOverview::getCreatedAt)
                        .orderByDesc(BatchOverview::getId));
        return PageResult.of(page);
    }

    /**
     * 原材料采购入库：生成采购批次并自动生成 IQC 检验任务。
     *
     * @return 新建批次实体（含系统生成的批次号）
     */
    @Transactional(rollbackFor = Exception.class)
    public Batch purchaseInbound(PurchaseInboundRequest request) {
        Material material = requireMaterialForUpdate(request.getMaterialId());
        Supplier supplier = requireSupplier(request.getSupplierId());
        validateInbound(material, supplier);
        validateProductionDate(request.getProductionDate());
        ensureInspectionStandardsConfigured(material.getId());

        Batch batch = new Batch();
        batch.setBatchNo(nextBatchNo(material.getCategory(), request.getProductionDate()));
        batch.setMaterialId(material.getId());
        batch.setSourceType(SourceType.PURCHASE);
        batch.setSupplierId(supplier.getId());
        batch.setProductionOrderId(null);
        batch.setQuantity(request.getQuantity());
        batch.setRemainingQuantity(request.getQuantity());
        batch.setStatus(BatchStatus.PENDING_INSPECT);
        batch.setProductionDate(request.getProductionDate());
        batch.setExpireDate(calculateExpireDate(material, request.getProductionDate()));
        batch.setWarehouseLocation(request.getWarehouseLocation());
        batch.setRemark(request.getRemark());
        batchMapper.insert(batch);

        InspectionTask task = new InspectionTask();
        task.setTaskNo(nextTaskNo(LocalDate.now()));
        task.setInspectType(InspectType.IQC);
        task.setBatchId(batch.getId());
        task.setProcessRecordId(null);
        task.setStatus(TaskStatus.PENDING);
        taskMapper.insert(task);

        return batch;
    }

    /** 批次详情：主表 + 台账视图投影 + 检验/缺陷/消耗/出货全景。 */
    public BatchDetailResponse detail(Long id) {
        Batch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException("批次不存在或已被删除");
        }
        BatchOverview overview = overviewMapper.selectById(id);
        List<InspectionTaskBrief> tasks = taskMapper.selectList(new LambdaQueryWrapper<InspectionTask>()
                        .eq(InspectionTask::getBatchId, id)
                        .orderByDesc(InspectionTask::getCreatedAt)
                        .orderByDesc(InspectionTask::getId))
                .stream()
                .map(this::toBrief)
                .toList();
        List<BatchDefectBrief> defects = defectMapper.selectList(new LambdaQueryWrapper<DefectRecord>()
                        .eq(DefectRecord::getBatchId, id)
                        .orderByDesc(DefectRecord::getCreatedAt)
                        .orderByDesc(DefectRecord::getId))
                .stream()
                .map(this::toDefectBrief)
                .toList();
        List<BatchConsumptionBrief> consumptions = consumptionBriefs(id);
        List<BatchShipmentBrief> shipments = shipmentBriefs(id);
        return BatchDetailResponse.builder()
                .batch(batch)
                .overview(overview)
                .inspectionTasks(tasks)
                .defects(defects)
                .consumptions(consumptions)
                .shipments(shipments)
                .build();
    }

    // ---------------- 私有辅助 ----------------

    private Material requireMaterial(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException("物料不存在或已被删除");
        }
        return material;
    }

    private Material requireMaterialForUpdate(Long id) {
        Material material = materialMapper.selectByIdForUpdate(id);
        if (material == null) {
            throw new BusinessException("物料不存在或已被删除");
        }
        return material;
    }

    private Supplier requireSupplier(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException("供应商不存在或已被删除");
        }
        return supplier;
    }

    private void validateInbound(Material material, Supplier supplier) {
        if (material.getCategory() != MaterialCategory.RAW) {
            throw new BusinessException("采购入库只允许选择原材料物料");
        }
        if (material.getStatus() == null || material.getStatus() != 1) {
            throw new BusinessException("该物料已停用，不可入库");
        }
        if (supplier.getStatus() == null || supplier.getStatus() != 1) {
            throw new BusinessException("该供应商已停用，不可登记入库");
        }
    }

    private void validateProductionDate(LocalDate productionDate) {
        if (productionDate.isAfter(LocalDate.now())) {
            throw new BusinessException("入库日期不能晚于今天");
        }
    }

    private void ensureInspectionStandardsConfigured(Long materialId) {
        Long count = itemMapper.countStandards(materialId, InspectType.IQC, null);
        if (count == null || count == 0) {
            throw new BusinessException("该原材料未配置IQC检验标准，不能登记入库");
        }
    }

    private LocalDate calculateExpireDate(Material material, LocalDate productionDate) {
        return material.getShelfLifeDays() == null
                ? null
                : productionDate.plusDays(material.getShelfLifeDays());
    }

    private String nextBatchNo(MaterialCategory category, LocalDate date) {
        String prefix = category.getBatchPrefix() + "-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }

    private String nextTaskNo(LocalDate date) {
        String prefix = "QC-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }

    private InspectionTaskBrief toBrief(InspectionTask task) {
        return InspectionTaskBrief.builder()
                .id(task.getId())
                .taskNo(task.getTaskNo())
                .inspectType(task.getInspectType())
                .status(task.getStatus())
                .conclusion(task.getConclusion())
                .inspectorId(task.getInspectorId())
                .assignedAt(task.getAssignedAt())
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private BatchDefectBrief toDefectBrief(DefectRecord defect) {
        return BatchDefectBrief.builder()
                .id(defect.getId())
                .defectNo(defect.getDefectNo())
                .defectType(defect.getDefectType())
                .severity(defect.getSeverity())
                .quantity(defect.getQuantity())
                .description(defect.getDescription())
                .handleMethod(defect.getHandleMethod())
                .handleStatus(defect.getHandleStatus())
                .createdAt(defect.getCreatedAt())
                .build();
    }

    private List<BatchConsumptionBrief> consumptionBriefs(Long batchId) {
        List<BatchConsumption> rows = consumptionMapper.selectList(new LambdaQueryWrapper<BatchConsumption>()
                .eq(BatchConsumption::getConsumedBatchId, batchId)
                .orderByDesc(BatchConsumption::getCreatedAt)
                .orderByDesc(BatchConsumption::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = rows.stream()
                .map(BatchConsumption::getProductionOrderId)
                .distinct()
                .toList();
        Map<Long, ProductionOrder> orderById = orderIds.isEmpty()
                ? Map.of()
                : orderMapper.selectByIds(orderIds).stream()
                .collect(Collectors.toMap(ProductionOrder::getId, Function.identity()));
        return rows.stream().map(row -> {
            ProductionOrder order = orderById.get(row.getProductionOrderId());
            return BatchConsumptionBrief.builder()
                    .id(row.getId())
                    .productionOrderId(row.getProductionOrderId())
                    .productionOrderNo(order == null ? null : order.getOrderNo())
                    .quantity(row.getQuantity())
                    .createdAt(row.getCreatedAt())
                    .build();
        }).toList();
    }

    private List<BatchShipmentBrief> shipmentBriefs(Long batchId) {
        List<Shipment> rows = shipmentMapper.selectList(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getBatchId, batchId)
                .orderByDesc(Shipment::getShipDate)
                .orderByDesc(Shipment::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> customerIds = rows.stream()
                .map(Shipment::getCustomerId)
                .distinct()
                .toList();
        Map<Long, Customer> customerById = customerIds.isEmpty()
                ? Map.of()
                : customerMapper.selectByIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        return rows.stream().map(row -> {
            Customer customer = customerById.get(row.getCustomerId());
            return BatchShipmentBrief.builder()
                    .id(row.getId())
                    .shipmentNo(row.getShipmentNo())
                    .customerId(row.getCustomerId())
                    .customerName(customer == null ? null : customer.getName())
                    .quantity(row.getQuantity())
                    .shipDate(row.getShipDate())
                    .remark(row.getRemark())
                    .createdAt(row.getCreatedAt())
                    .build();
        }).toList();
    }
}
