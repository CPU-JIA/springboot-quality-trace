package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.Conclusion;
import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.enums.MaterialCategory;
import com.xinghui.qualitytrace.common.enums.OrderStatus;
import com.xinghui.qualitytrace.common.enums.ProcessStatus;
import com.xinghui.qualitytrace.common.enums.SourceType;
import com.xinghui.qualitytrace.common.enums.TaskStatus;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.dto.production.ConsumptionResponse;
import com.xinghui.qualitytrace.dto.production.MaterialIssueRequest;
import com.xinghui.qualitytrace.dto.production.MaterialRequirementResponse;
import com.xinghui.qualitytrace.dto.production.OrderCompleteRequest;
import com.xinghui.qualitytrace.dto.production.ProcessCompleteRequest;
import com.xinghui.qualitytrace.dto.production.ProcessRecordResponse;
import com.xinghui.qualitytrace.dto.production.ProductionOrderCreateRequest;
import com.xinghui.qualitytrace.dto.production.ProductionOrderDetailResponse;
import com.xinghui.qualitytrace.dto.production.ProductionOrderResponse;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.BatchConsumption;
import com.xinghui.qualitytrace.entity.Bom;
import com.xinghui.qualitytrace.entity.InspectionTask;
import com.xinghui.qualitytrace.entity.Material;
import com.xinghui.qualitytrace.entity.ProcessDef;
import com.xinghui.qualitytrace.entity.ProcessRecord;
import com.xinghui.qualitytrace.entity.ProcessRoute;
import com.xinghui.qualitytrace.entity.ProductionOrder;
import com.xinghui.qualitytrace.mapper.BatchConsumptionMapper;
import com.xinghui.qualitytrace.mapper.BatchMapper;
import com.xinghui.qualitytrace.mapper.BomMapper;
import com.xinghui.qualitytrace.mapper.InspectionItemMapper;
import com.xinghui.qualitytrace.mapper.InspectionTaskMapper;
import com.xinghui.qualitytrace.mapper.MaterialMapper;
import com.xinghui.qualitytrace.mapper.ProcessDefMapper;
import com.xinghui.qualitytrace.mapper.ProcessRecordMapper;
import com.xinghui.qualitytrace.mapper.ProcessRouteMapper;
import com.xinghui.qualitytrace.mapper.ProductionOrderMapper;
import com.xinghui.qualitytrace.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 生产工单服务 —— 工单创建、领料、工序流转、完工入库（F4）
 *
 * <p>本类是生产链的事务边界：领料必须同时扣减批次余量并写入消耗边；完工入库必须
 * 同时更新工单、创建产出批次并生成 FQC 检验任务。任何一步失败都整体回滚，避免追溯链断裂。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductionOrderService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ProductionOrderMapper orderMapper;
    private final ProcessRecordMapper processRecordMapper;
    private final ProcessRouteMapper routeMapper;
    private final ProcessDefMapper processDefMapper;
    private final BatchMapper batchMapper;
    private final BatchConsumptionMapper consumptionMapper;
    private final InspectionTaskMapper taskMapper;
    private final InspectionItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final BomMapper bomMapper;
    private final SerialNumberService serialNumberService;

    /** 工单分页查询。 */
    public PageResult<ProductionOrderResponse> page(long current, long size, String keyword, OrderStatus status, Long materialId) {
        Page<ProductionOrderResponse> page = orderMapper.selectPageWithMaterial(
                PageBounds.page(current, size), StrUtil.trimToNull(keyword), status, materialId);
        return PageResult.of(page);
    }

    /**
     * 创建生产工单：校验生产物料、BOM 与路线，然后按路线生成工序快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder create(ProductionOrderCreateRequest request) {
        Material material = requireMaterialForUpdate(request.getMaterialId());
        validateOrderMaterial(material);
        if (request.getPlanEndDate().isBefore(request.getPlanStartDate())) {
            throw new BusinessException("计划结束日期不能早于计划开始日期");
        }

        List<Bom> bomRows = directBomRows(material.getId());
        if (bomRows.isEmpty()) {
            throw new BusinessException("该生产物料尚未配置BOM，不能创建工单");
        }
        ensureBomMaterialsActive(bomRows);
        List<ProcessRoute> routes = routeRows(material.getId());
        if (routes.isEmpty()) {
            throw new BusinessException("该生产物料尚未配置工艺路线，不能创建工单");
        }
        ensureProductionInspectionStandards(material, routes);

        ProductionOrder order = new ProductionOrder();
        order.setOrderNo(nextOrderNo(LocalDate.now()));
        order.setMaterialId(material.getId());
        order.setPlanQuantity(request.getPlanQuantity());
        order.setStatus(OrderStatus.CREATED);
        order.setPlanStartDate(request.getPlanStartDate());
        order.setPlanEndDate(request.getPlanEndDate());
        order.setManagerId(UserContext.currentUserId());
        order.setRemark(request.getRemark());
        orderMapper.insert(order);

        for (ProcessRoute route : routes) {
            ProcessRecord record = new ProcessRecord();
            record.setProductionOrderId(order.getId());
            record.setProcessDefId(route.getProcessDefId());
            record.setStepNo(route.getStepNo());
            record.setStatus(ProcessStatus.PENDING);
            processRecordMapper.insert(record);
        }
        return order;
    }

    /**
     * 生产领料：原子扣减合格在库批次余量，并写入批次消耗关系。
     */
    @Transactional(rollbackFor = Exception.class)
    public void issueMaterials(Long orderId, MaterialIssueRequest request) {
        ProductionOrder order = requireOrderForUpdate(orderId);
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CLOSED) {
            throw new BusinessException("工单已完工或关闭，不能继续领料");
        }
        List<Bom> bomRows = directBomRows(order.getMaterialId());
        Set<Long> allowedMaterialIds = bomRows.stream().map(Bom::getChildMaterialId).collect(Collectors.toSet());
        if (allowedMaterialIds.isEmpty()) {
            throw new BusinessException("工单物料未配置BOM，不能领料");
        }

        Map<Long, BigDecimal> issueByMaterial = new HashMap<>();
        Map<Long, Batch> batchById = new HashMap<>();
        for (MaterialIssueRequest.Item item : request.getItems()) {
            Batch batch = batchMapper.selectById(item.getBatchId());
            if (batch == null) {
                throw new BusinessException("领料批次不存在：" + item.getBatchId());
            }
            if (!allowedMaterialIds.contains(batch.getMaterialId())) {
                throw new BusinessException("批次[" + batch.getBatchNo() + "]的物料不属于该工单BOM直接子项");
            }
            batchById.put(item.getBatchId(), batch);
            issueByMaterial.merge(batch.getMaterialId(), item.getQuantity(), BigDecimal::add);
        }
        ensureIssueWithinPlan(order, issueByMaterial);

        for (MaterialIssueRequest.Item item : request.getItems()) {
            Batch batch = batchById.get(item.getBatchId());
            int affected = batchMapper.deductQualifiedStock(batch.getId(), item.getQuantity());
            if (affected == 0) {
                throw new BusinessException("批次[" + batch.getBatchNo() + "]不是合格在库状态或余量不足");
            }
            consumptionMapper.insertOrIncrease(orderId, batch.getId(), item.getQuantity());
        }
    }

    /**
     * 工序开工：前道工序必须完成；若前道为 IPQC 点，最新 IPQC 必须合格/让步。
     */
    @Transactional(rollbackFor = Exception.class)
    public void startProcess(Long processRecordId) {
        ProcessRecord snapshot = requireProcessRecord(processRecordId);
        ProductionOrder order = requireOrderForUpdate(snapshot.getProductionOrderId());
        ProcessRecord record = requireProcessRecordForUpdate(processRecordId);
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CLOSED) {
            throw new BusinessException("工单已完工或关闭，不能开工");
        }
        if (record.getStatus() != ProcessStatus.PENDING) {
            throw new BusinessException("只有待开工工序才能执行开工操作");
        }
        if (order.getStatus() == OrderStatus.CREATED && record.getStepNo() == 1) {
            ensureDirectMaterialsIssuedBeforeStart(order);
        }
        ensurePreviousProcessReleased(record);

        LocalDateTime now = LocalDateTime.now();
        record.setStatus(ProcessStatus.IN_PROGRESS);
        record.setOperatorId(UserContext.currentUserId());
        record.setStartTime(now);
        processRecordMapper.updateById(record);

        if (order.getStatus() == OrderStatus.CREATED) {
            if (record.getStepNo() != 1) {
                throw new BusinessException("首道工序尚未开工，不能跳过前序步骤");
            }
            order.setStatus(OrderStatus.IN_PROGRESS);
            order.setActualStartTime(now);
            orderMapper.updateById(order);
        }
    }

    /**
     * 工序报完工：若该工序为 IPQC 检验点，自动生成过程检验任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeProcess(Long processRecordId, ProcessCompleteRequest request) {
        ProcessRecord snapshot = requireProcessRecord(processRecordId);
        ProductionOrder order = requireOrderForUpdate(snapshot.getProductionOrderId());
        ProcessRecord record = requireProcessRecordForUpdate(processRecordId);
        if (record.getStatus() != ProcessStatus.IN_PROGRESS) {
            throw new BusinessException("只有进行中的工序才能报完工");
        }
        ProcessDef processDef = requireProcessDef(record.getProcessDefId());
        if (processDef.getNeedIpqc() == 1) {
            requireMaterialForUpdate(order.getMaterialId());
            ensureInspectionStandardsConfigured(order.getMaterialId(), InspectType.IPQC, processDef.getId(),
                    "工序[" + processDef.getProcessName() + "]未配置IPQC检验标准，不能报完工");
        }
        record.setStatus(ProcessStatus.COMPLETED);
        record.setEndTime(LocalDateTime.now());
        record.setRemark(request.getRemark());
        processRecordMapper.updateById(record);

        if (processDef.getNeedIpqc() == 1) {
            InspectionTask task = new InspectionTask();
            task.setTaskNo(nextTaskNo(LocalDate.now()));
            task.setInspectType(InspectType.IPQC);
            task.setProcessRecordId(record.getId());
            task.setStatus(TaskStatus.PENDING);
            taskMapper.insert(task);
        }
    }

    /**
     * 工单完工入库：校验所有工序已完成且 IPQC 点均已放行，然后生成产出批次与 FQC 任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Batch completeOrder(Long orderId, OrderCompleteRequest request) {
        ProductionOrder order = requireOrderForUpdate(orderId);
        if (order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw new BusinessException("只有生产中的工单才能完工入库");
        }
        if (request.getActualQuantity().compareTo(order.getPlanQuantity()) > 0) {
            throw new BusinessException("实际完工数量不能超过计划数量");
        }
        validateCompletionDate(order, request.getProductionDate());
        if (outputBatchOf(orderId) != null) {
            throw new BusinessException("该工单已生成产出批次，不能重复完工入库");
        }
        ensureMaterialsIssuedForCompletion(order, request.getActualQuantity());
        List<ProcessRecord> records = processRecordsOf(orderId);
        if (records.isEmpty()) {
            throw new BusinessException("工单缺少工序记录，不能完工入库");
        }
        Map<Long, ProcessDef> defById = loadProcessDefs(records);
        for (ProcessRecord record : records) {
            if (record.getStatus() != ProcessStatus.COMPLETED) {
                throw new BusinessException("仍有未完成工序，不能完工入库");
            }
            ProcessDef def = defById.get(record.getProcessDefId());
            if (def.getNeedIpqc() == 1) {
                ensureIpqcPassed(record);
            }
        }

        Material material = requireMaterialForUpdate(order.getMaterialId());
        ensureInspectionStandardsConfigured(material.getId(), InspectType.FQC, null,
                "该生产物料未配置FQC检验标准，不能完工入库");
        Batch output = new Batch();
        output.setBatchNo(nextBatchNo(material.getCategory(), request.getProductionDate()));
        output.setMaterialId(material.getId());
        output.setSourceType(SourceType.PRODUCTION);
        output.setProductionOrderId(order.getId());
        output.setQuantity(request.getActualQuantity());
        output.setRemainingQuantity(request.getActualQuantity());
        output.setStatus(BatchStatus.PENDING_INSPECT);
        output.setProductionDate(request.getProductionDate());
        output.setExpireDate(material.getShelfLifeDays() == null
                ? null
                : request.getProductionDate().plusDays(material.getShelfLifeDays()));
        output.setWarehouseLocation(request.getWarehouseLocation());
        output.setRemark(request.getRemark());
        batchMapper.insert(output);

        InspectionTask task = new InspectionTask();
        task.setTaskNo(nextTaskNo(LocalDate.now()));
        task.setInspectType(InspectType.FQC);
        task.setBatchId(output.getId());
        task.setStatus(TaskStatus.PENDING);
        taskMapper.insert(task);

        order.setActualQuantity(request.getActualQuantity());
        order.setActualEndTime(LocalDateTime.now());
        order.setStatus(OrderStatus.COMPLETED);
        orderMapper.updateById(order);
        return output;
    }

    /** 工单详情：主信息 + 工序快照 + 领料消耗 + 产出批次。 */
    public ProductionOrderDetailResponse detail(Long orderId) {
        ProductionOrder order = requireOrder(orderId);
        Material material = requireMaterial(order.getMaterialId());
        return ProductionOrderDetailResponse.builder()
                .order(order)
                .materialCode(material.getMaterialCode())
                .materialName(material.getName())
                .processRecords(assembleProcessRecords(processRecordsOf(orderId)))
                .materialRequirements(assembleMaterialRequirements(order))
                .consumptions(assembleConsumptions(orderId))
                .outputBatch(outputBatchOf(orderId))
                .build();
    }

    // ---------------- 私有辅助 ----------------

    private ProductionOrder requireOrder(Long id) {
        ProductionOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("生产工单不存在或已被删除");
        }
        return order;
    }

    private ProductionOrder requireOrderForUpdate(Long id) {
        ProductionOrder order = orderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException("生产工单不存在或已被删除");
        }
        return order;
    }

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

    private ProcessRecord requireProcessRecord(Long id) {
        ProcessRecord record = processRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("工序记录不存在或已被删除");
        }
        return record;
    }

    private ProcessRecord requireProcessRecordForUpdate(Long id) {
        ProcessRecord record = processRecordMapper.selectByIdForUpdate(id);
        if (record == null) {
            throw new BusinessException("工序记录不存在或已被删除");
        }
        return record;
    }

    private ProcessDef requireProcessDef(Long id) {
        ProcessDef def = processDefMapper.selectById(id);
        if (def == null) {
            throw new BusinessException("工序定义不存在或已被删除");
        }
        return def;
    }

    private void validateOrderMaterial(Material material) {
        if (material.getCategory() == MaterialCategory.RAW) {
            throw new BusinessException("原材料为外购物料，不能创建生产工单");
        }
        if (material.getStatus() == null || material.getStatus() != 1) {
            throw new BusinessException("该物料已停用，不能创建生产工单");
        }
    }

    private List<Bom> directBomRows(Long materialId) {
        return bomMapper.selectList(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getParentMaterialId, materialId)
                .orderByAsc(Bom::getId));
    }

    private List<ProcessRoute> routeRows(Long materialId) {
        return routeMapper.selectList(new LambdaQueryWrapper<ProcessRoute>()
                .eq(ProcessRoute::getMaterialId, materialId)
                .orderByAsc(ProcessRoute::getStepNo));
    }

    private void ensureBomMaterialsActive(List<Bom> bomRows) {
        Map<Long, Material> materialById = materialMapper.selectByIds(bomRows.stream()
                        .map(Bom::getChildMaterialId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        for (Bom bom : bomRows) {
            Material child = materialById.get(bom.getChildMaterialId());
            if (child == null) {
                throw new BusinessException("BOM子项物料不存在或已被删除，不能创建工单");
            }
            if (child.getStatus() == null || child.getStatus() != 1) {
                throw new BusinessException("BOM子项物料[" + child.getMaterialCode() + "]已停用，不能创建工单");
            }
        }
    }

    private void ensureProductionInspectionStandards(Material material, List<ProcessRoute> routes) {
        ensureInspectionStandardsConfigured(material.getId(), InspectType.FQC, null,
                "该生产物料未配置FQC检验标准，不能创建工单");
        Map<Long, ProcessDef> defById = processDefMapper.selectByIds(routes.stream()
                        .map(ProcessRoute::getProcessDefId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ProcessDef::getId, Function.identity()));
        for (ProcessRoute route : routes) {
            ProcessDef def = defById.get(route.getProcessDefId());
            if (def == null) {
                throw new BusinessException("工艺路线包含不存在的工序");
            }
            if (def.getNeedIpqc() == 1) {
                ensureInspectionStandardsConfigured(material.getId(), InspectType.IPQC, def.getId(),
                        "工序[" + def.getProcessName() + "]未配置IPQC检验标准，不能创建工单");
            }
        }
    }

    private void ensureInspectionStandardsConfigured(Long materialId,
                                                     InspectType inspectType,
                                                     Long processDefId,
                                                     String message) {
        Long count = itemMapper.countStandards(materialId, inspectType, processDefId);
        if (count == null || count == 0) {
            throw new BusinessException(message);
        }
    }

    private void validateCompletionDate(ProductionOrder order, LocalDate productionDate) {
        if (productionDate.isAfter(LocalDate.now())) {
            throw new BusinessException("生产日期不能晚于今天");
        }
        if (productionDate.isBefore(order.getPlanStartDate())) {
            throw new BusinessException("生产日期不能早于计划开始日期");
        }
    }

    private void ensureIssueWithinPlan(ProductionOrder order, Map<Long, BigDecimal> issueByMaterial) {
        Map<Long, BigDecimal> limitByMaterial = requiredQuantityByMaterial(order, order.getPlanQuantity());
        Map<Long, BigDecimal> consumedByMaterial = consumedQuantityByMaterial(order.getId());
        for (Map.Entry<Long, BigDecimal> entry : issueByMaterial.entrySet()) {
            BigDecimal limit = limitByMaterial.get(entry.getKey());
            BigDecimal afterIssue = consumedByMaterial
                    .getOrDefault(entry.getKey(), BigDecimal.ZERO)
                    .add(entry.getValue());
            if (limit == null || afterIssue.compareTo(limit) > 0) {
                Material material = requireMaterial(entry.getKey());
                throw new BusinessException("物料[" + material.getMaterialCode() + "]累计领料超过工单BOM需求");
            }
        }
    }

    private void ensureMaterialsIssuedForCompletion(ProductionOrder order, BigDecimal actualQuantity) {
        Map<Long, BigDecimal> requiredByMaterial = requiredQuantityByMaterial(order, actualQuantity);
        Map<Long, BigDecimal> consumedByMaterial = consumedQuantityByMaterial(order.getId());
        for (Map.Entry<Long, BigDecimal> entry : requiredByMaterial.entrySet()) {
            BigDecimal consumed = consumedByMaterial.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (consumed.compareTo(entry.getValue()) < 0) {
                Material material = requireMaterial(entry.getKey());
                throw new BusinessException("物料[" + material.getMaterialCode() + "]尚未足额领料，不能完工入库");
            }
        }
    }

    private void ensureDirectMaterialsIssuedBeforeStart(ProductionOrder order) {
        Map<Long, BigDecimal> requiredByMaterial = requiredQuantityByMaterial(order, BigDecimal.ONE);
        Map<Long, BigDecimal> consumedByMaterial = consumedQuantityByMaterial(order.getId());
        for (Long materialId : requiredByMaterial.keySet()) {
            BigDecimal consumed = consumedByMaterial.getOrDefault(materialId, BigDecimal.ZERO);
            if (consumed.compareTo(BigDecimal.ZERO) <= 0) {
                Material material = requireMaterial(materialId);
                throw new BusinessException("物料[" + material.getMaterialCode() + "]尚未领料，不能开工");
            }
        }
    }

    private Map<Long, BigDecimal> requiredQuantityByMaterial(ProductionOrder order, BigDecimal outputQuantity) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Bom bom : directBomRows(order.getMaterialId())) {
            result.merge(bom.getChildMaterialId(), bom.getQuantity().multiply(outputQuantity), BigDecimal::add);
        }
        return result;
    }

    private Map<Long, BigDecimal> consumedQuantityByMaterial(Long orderId) {
        List<BatchConsumption> rows = consumptionMapper.selectList(new LambdaQueryWrapper<BatchConsumption>()
                .eq(BatchConsumption::getProductionOrderId, orderId));
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, Batch> batchById = batchMapper.selectByIds(rows.stream()
                        .map(BatchConsumption::getConsumedBatchId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Batch::getId, Function.identity()));
        Map<Long, BigDecimal> result = new HashMap<>();
        for (BatchConsumption row : rows) {
            Batch batch = batchById.get(row.getConsumedBatchId());
            if (batch != null) {
                result.merge(batch.getMaterialId(), row.getQuantity(), BigDecimal::add);
            }
        }
        return result;
    }

    private List<ProcessRecord> processRecordsOf(Long orderId) {
        return processRecordMapper.selectList(new LambdaQueryWrapper<ProcessRecord>()
                .eq(ProcessRecord::getProductionOrderId, orderId)
                .orderByAsc(ProcessRecord::getStepNo));
    }

    private Batch outputBatchOf(Long orderId) {
        return batchMapper.selectOne(new LambdaQueryWrapper<Batch>()
                .eq(Batch::getProductionOrderId, orderId)
                .last("LIMIT 1"));
    }

    private void ensurePreviousProcessReleased(ProcessRecord current) {
        if (current.getStepNo() == 1) {
            return;
        }
        ProcessRecord previous = processRecordMapper.selectOne(new LambdaQueryWrapper<ProcessRecord>()
                .eq(ProcessRecord::getProductionOrderId, current.getProductionOrderId())
                .eq(ProcessRecord::getStepNo, current.getStepNo() - 1));
        if (previous == null || previous.getStatus() != ProcessStatus.COMPLETED) {
            throw new BusinessException("前道工序尚未完工，不能开工当前工序");
        }
        ProcessDef previousDef = requireProcessDef(previous.getProcessDefId());
        if (previousDef.getNeedIpqc() == 1) {
            ensureIpqcPassed(previous);
        }
    }

    private void ensureIpqcPassed(ProcessRecord record) {
        InspectionTask latest = taskMapper.selectOne(new LambdaQueryWrapper<InspectionTask>()
                .eq(InspectionTask::getProcessRecordId, record.getId())
                .orderByDesc(InspectionTask::getCreatedAt)
                .orderByDesc(InspectionTask::getId)
                .last("LIMIT 1"));
        if (latest == null || latest.getStatus() != TaskStatus.COMPLETED
                || (latest.getConclusion() != Conclusion.QUALIFIED && latest.getConclusion() != Conclusion.CONCESSION)) {
            throw new BusinessException("工序[" + record.getStepNo() + "]的IPQC尚未通过，不能继续流转");
        }
    }

    private Map<Long, ProcessDef> loadProcessDefs(List<ProcessRecord> records) {
        return processDefMapper.selectByIds(records.stream()
                        .map(ProcessRecord::getProcessDefId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ProcessDef::getId, Function.identity()));
    }

    private List<ProcessRecordResponse> assembleProcessRecords(List<ProcessRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, ProcessDef> defById = loadProcessDefs(records);
        Map<Long, InspectionTask> latestIpqcByRecordId = latestIpqcTasks(records);
        return records.stream().map(record -> {
            ProcessDef def = defById.get(record.getProcessDefId());
            InspectionTask ipqcTask = def.getNeedIpqc() == 1 ? latestIpqcByRecordId.get(record.getId()) : null;
            return ProcessRecordResponse.builder()
                    .id(record.getId())
                    .productionOrderId(record.getProductionOrderId())
                    .processDefId(record.getProcessDefId())
                    .processCode(def.getProcessCode())
                    .processName(def.getProcessName())
                    .needIpqc(def.getNeedIpqc())
                    .stepNo(record.getStepNo())
                    .status(record.getStatus())
                    .ipqcTaskStatus(ipqcTask == null ? null : ipqcTask.getStatus())
                    .ipqcConclusion(ipqcTask == null ? null : ipqcTask.getConclusion())
                    .ipqcReleased(isReleasedIpqc(ipqcTask))
                    .operatorId(record.getOperatorId())
                    .startTime(record.getStartTime())
                    .endTime(record.getEndTime())
                    .remark(record.getRemark())
                    .build();
        }).toList();
    }

    private Map<Long, InspectionTask> latestIpqcTasks(List<ProcessRecord> records) {
        List<Long> ids = records.stream()
                .map(ProcessRecord::getId)
                .toList();
        List<InspectionTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<InspectionTask>()
                .in(InspectionTask::getProcessRecordId, ids)
                .eq(InspectionTask::getInspectType, InspectType.IPQC)
                .orderByDesc(InspectionTask::getCreatedAt)
                .orderByDesc(InspectionTask::getId));
        Map<Long, InspectionTask> result = new HashMap<>();
        for (InspectionTask task : tasks) {
            result.putIfAbsent(task.getProcessRecordId(), task);
        }
        return result;
    }

    private boolean isReleasedIpqc(InspectionTask task) {
        return task != null
                && task.getStatus() == TaskStatus.COMPLETED
                && (task.getConclusion() == Conclusion.QUALIFIED || task.getConclusion() == Conclusion.CONCESSION);
    }

    private List<MaterialRequirementResponse> assembleMaterialRequirements(ProductionOrder order) {
        List<Bom> bomRows = directBomRows(order.getMaterialId());
        if (bomRows.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> consumedByMaterial = consumedQuantityByMaterial(order.getId());
        Map<Long, Material> materialById = materialMapper.selectByIds(bomRows.stream()
                        .map(Bom::getChildMaterialId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        return bomRows.stream().map(row -> {
            Material material = materialById.get(row.getChildMaterialId());
            BigDecimal required = row.getQuantity().multiply(order.getPlanQuantity());
            BigDecimal issued = consumedByMaterial.getOrDefault(row.getChildMaterialId(), BigDecimal.ZERO);
            BigDecimal missing = required.subtract(issued);
            if (missing.compareTo(BigDecimal.ZERO) < 0) {
                missing = BigDecimal.ZERO;
            }
            return MaterialRequirementResponse.builder()
                    .bomId(row.getId())
                    .materialId(row.getChildMaterialId())
                    .materialCode(material.getMaterialCode())
                    .materialName(material.getName())
                    .unitQuantity(row.getQuantity())
                    .requiredQuantity(required)
                    .issuedQuantity(issued)
                    .missingQuantity(missing)
                    .issuedEnough(issued.compareTo(required) >= 0)
                    .build();
        }).toList();
    }

    private List<ConsumptionResponse> assembleConsumptions(Long orderId) {
        List<BatchConsumption> rows = consumptionMapper.selectList(new LambdaQueryWrapper<BatchConsumption>()
                .eq(BatchConsumption::getProductionOrderId, orderId)
                .orderByAsc(BatchConsumption::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Batch> batchById = batchMapper.selectByIds(rows.stream()
                        .map(BatchConsumption::getConsumedBatchId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Batch::getId, Function.identity()));
        Map<Long, Material> materialById = materialMapper.selectByIds(batchById.values().stream()
                        .map(Batch::getMaterialId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        return rows.stream().map(row -> {
            Batch batch = batchById.get(row.getConsumedBatchId());
            Material material = materialById.get(batch.getMaterialId());
            return ConsumptionResponse.builder()
                    .id(row.getId())
                    .consumedBatchId(row.getConsumedBatchId())
                    .batchNo(batch.getBatchNo())
                    .materialCode(material.getMaterialCode())
                    .materialName(material.getName())
                    .quantity(row.getQuantity())
                    .batchStatus(batch.getStatus())
                    .createdAt(row.getCreatedAt())
                    .build();
        }).toList();
    }

    private String nextOrderNo(LocalDate date) {
        String prefix = "MO-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }

    private String nextBatchNo(MaterialCategory category, LocalDate date) {
        String prefix = category.getBatchPrefix() + "-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }

    private String nextTaskNo(LocalDate date) {
        String prefix = "QC-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }
}
