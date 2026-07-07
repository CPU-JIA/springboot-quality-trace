package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.Conclusion;
import com.xinghui.qualitytrace.common.enums.HandleStatus;
import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.enums.OrderStatus;
import com.xinghui.qualitytrace.common.enums.ProcessStatus;
import com.xinghui.qualitytrace.common.enums.Severity;
import com.xinghui.qualitytrace.common.enums.TaskStatus;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.dto.inspection.InspectionRecordResponse;
import com.xinghui.qualitytrace.dto.inspection.InspectionSubmitRequest;
import com.xinghui.qualitytrace.dto.inspection.InspectionTaskResponse;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.DefectRecord;
import com.xinghui.qualitytrace.entity.InspectionItem;
import com.xinghui.qualitytrace.entity.InspectionRecord;
import com.xinghui.qualitytrace.entity.InspectionTask;
import com.xinghui.qualitytrace.entity.ProcessDef;
import com.xinghui.qualitytrace.entity.ProcessRecord;
import com.xinghui.qualitytrace.entity.ProductionOrder;
import com.xinghui.qualitytrace.mapper.BatchMapper;
import com.xinghui.qualitytrace.mapper.DefectRecordMapper;
import com.xinghui.qualitytrace.mapper.InspectionItemMapper;
import com.xinghui.qualitytrace.mapper.InspectionRecordMapper;
import com.xinghui.qualitytrace.mapper.InspectionTaskMapper;
import com.xinghui.qualitytrace.mapper.ProcessDefMapper;
import com.xinghui.qualitytrace.mapper.ProcessRecordMapper;
import com.xinghui.qualitytrace.mapper.ProductionOrderMapper;
import com.xinghui.qualitytrace.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检验任务服务 —— 任务池、领取、提交检验记录与结论联动（F5-1/F5-2/F5-3）
 *
 * <p>提交检验采用同一事务保存明细、更新检验单、联动批次/工序状态。
 * 这样可以避免"结论已完成但明细缺失"或"不合格结论未冻结批次"这类质量闭环断裂。</p>
 */
@Service
@RequiredArgsConstructor
public class InspectionTaskService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final InspectionTaskMapper taskMapper;
    private final InspectionRecordMapper recordMapper;
    private final InspectionItemMapper itemMapper;
    private final BatchMapper batchMapper;
    private final ProcessRecordMapper processRecordMapper;
    private final ProcessDefMapper processDefMapper;
    private final ProductionOrderMapper orderMapper;
    private final DefectRecordMapper defectMapper;
    private final SerialNumberService serialNumberService;

    /** 检验任务池分页查询。 */
    public PageResult<InspectionTaskResponse> page(long current,
                                                   long size,
                                                   TaskStatus status,
                                                   InspectType inspectType,
                                                   Long batchId,
                                                   Long processRecordId,
                                                   String keyword) {
        Page<InspectionTask> page = taskMapper.selectPage(PageBounds.page(current, size),
                new LambdaQueryWrapper<InspectionTask>()
                        .like(StrUtil.isNotBlank(keyword), InspectionTask::getTaskNo, keyword)
                        .eq(status != null, InspectionTask::getStatus, status)
                        .eq(inspectType != null, InspectionTask::getInspectType, inspectType)
                        .eq(batchId != null, InspectionTask::getBatchId, batchId)
                        .eq(processRecordId != null, InspectionTask::getProcessRecordId, processRecordId)
                        .orderByAsc(InspectionTask::getStatus)
                        .orderByDesc(InspectionTask::getCreatedAt)
                        .orderByDesc(InspectionTask::getId));
        return PageResult.of(assembleTasks(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 领取检验任务。IQC/FQC 领取时同步将批次从待检置为检验中。 */
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id) {
        InspectionTask task = requireTaskForUpdate(id);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new BusinessException("只有待领取任务可以领取");
        }
        if (task.getInspectType() != InspectType.IPQC) {
            Batch batch = requireBatchForUpdate(task.getBatchId());
            if (batch.getStatus() != BatchStatus.PENDING_INSPECT) {
                throw new BusinessException("受检批次不是待检状态，不能领取检验任务");
            }
            batch.setStatus(BatchStatus.INSPECTING);
            batchMapper.updateById(batch);
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setInspectorId(UserContext.currentUserId());
        task.setAssignedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 提交检验结果：校验标准项覆盖完整，保存明细，更新结论，并联动批次/工序状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, InspectionSubmitRequest request) {
        InspectionTask task = requireTaskForUpdate(id);
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BusinessException("只有检验中的任务可以提交结论");
        }
        if (task.getInspectorId() == null || !task.getInspectorId().equals(UserContext.currentUserId())) {
            throw new BusinessException("检验任务只能由领取人提交结论");
        }
        TargetContext target = loadTargetContext(task, true);
        List<InspectionItem> expectedItems = expectedItems(task, target);
        validateSubmit(request, expectedItems);

        Map<Long, InspectionSubmitRequest.RecordItem> submittedByItemId = request.getRecords().stream()
                .collect(Collectors.toMap(InspectionSubmitRequest.RecordItem::getInspectionItemId, Function.identity()));
        List<InspectionRecord> records = new ArrayList<>(expectedItems.size());
        boolean hasFailedItem = false;
        for (InspectionItem item : expectedItems) {
            InspectionRecord record = toInspectionRecord(task, item, submittedByItemId.get(item.getId()));
            if (record.getIsPass() == 0) {
                hasFailedItem = true;
            }
            records.add(record);
        }
        validateConclusion(request, hasFailedItem);
        validateDefectQuantity(request, target);

        recordMapper.delete(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getInspectionTaskId, task.getId()));
        for (InspectionRecord record : records) {
            recordMapper.insert(record);
        }

        task.setConclusion(request.getConclusion());
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task.setRemark(request.getRemark());
        taskMapper.updateById(task);

        applyConclusion(task, request.getConclusion(), target);
        if (request.getConclusion() == Conclusion.CONCESSION) {
            createConcessionDefect(task, request.getConcessionDefect(), target);
        } else if (request.getConclusion() == Conclusion.UNQUALIFIED) {
            createUnqualifiedDefect(task, request.getUnqualifiedDefect(), target);
        }
    }

    /** 查询某检验任务的明细记录。 */
    public List<InspectionRecordResponse> recordsOf(Long taskId) {
        requireTask(taskId);
        List<InspectionRecord> records = recordMapper.selectList(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getInspectionTaskId, taskId)
                .orderByAsc(InspectionRecord::getId));
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, InspectionItem> itemById = itemMapper.selectByIds(records.stream()
                        .map(InspectionRecord::getInspectionItemId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(InspectionItem::getId, Function.identity()));
        return records.stream().map(record -> {
            InspectionItem item = itemById.get(record.getInspectionItemId());
            return InspectionRecordResponse.builder()
                    .id(record.getId())
                    .inspectionItemId(record.getInspectionItemId())
                    .itemCode(item.getItemCode())
                    .itemName(item.getItemName())
                    .measuredValue(record.getMeasuredValue())
                    .resultDesc(record.getResultDesc())
                    .isPass(record.getIsPass())
                    .inspectedAt(record.getInspectedAt())
                    .build();
        }).toList();
    }

    /**
     * 查询某检验任务应填写的标准项。
     *
     * <p>前端执行检验时必须动态渲染"该任务目标对象适用的全部检验项目"；
     * 这里复用提交校验时的目标解析逻辑，保证页面展示与服务端完整性校验同源，
     * 避免 IQC/FQC/IPQC 三类任务各自猜字段。</p>
     */
    public List<InspectionItem> itemsOf(Long taskId) {
        InspectionTask task = requireTask(taskId);
        TargetContext target = loadTargetContext(task, false);
        return expectedItems(task, target);
    }

    // ---------------- 私有辅助 ----------------

    private InspectionTask requireTask(Long id) {
        InspectionTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("检验任务不存在或已被删除");
        }
        return task;
    }

    private InspectionTask requireTaskForUpdate(Long id) {
        InspectionTask task = taskMapper.selectByIdForUpdate(id);
        if (task == null) {
            throw new BusinessException("检验任务不存在或已被删除");
        }
        return task;
    }

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

    private TargetContext loadTargetContext(InspectionTask task, boolean lockForUpdate) {
        if (task.getInspectType() == InspectType.IPQC) {
            ProcessRecord record = lockForUpdate
                    ? processRecordMapper.selectByIdForUpdate(task.getProcessRecordId())
                    : processRecordMapper.selectById(task.getProcessRecordId());
            if (record == null) {
                throw new BusinessException("检验任务关联的工序记录不存在");
            }
            ProductionOrder order = orderMapper.selectById(record.getProductionOrderId());
            if (order == null) {
                throw new BusinessException("检验任务关联的生产工单不存在");
            }
            return new TargetContext(null, record, order.getMaterialId(), record.getProcessDefId(), order.getPlanQuantity());
        }
        Batch batch = lockForUpdate ? requireBatchForUpdate(task.getBatchId()) : requireBatch(task.getBatchId());
        return new TargetContext(batch, null, batch.getMaterialId(), null, batch.getQuantity());
    }

    private List<InspectionItem> expectedItems(InspectionTask task, TargetContext target) {
        LambdaQueryWrapper<InspectionItem> wrapper = new LambdaQueryWrapper<InspectionItem>()
                .eq(InspectionItem::getMaterialId, target.materialId())
                .eq(InspectionItem::getInspectType, task.getInspectType());
        if (task.getInspectType() == InspectType.IPQC) {
            wrapper.eq(InspectionItem::getProcessDefId, target.processDefId());
        }
        List<InspectionItem> items = itemMapper.selectList(wrapper.orderByAsc(InspectionItem::getId));
        if (items.isEmpty()) {
            throw new BusinessException("该对象未配置检验标准项，不能提交检验结论");
        }
        return items;
    }

    private void validateSubmit(InspectionSubmitRequest request, List<InspectionItem> expectedItems) {
        Set<Long> expectedIds = expectedItems.stream().map(InspectionItem::getId).collect(Collectors.toSet());
        Set<Long> actualIds = request.getRecords().stream()
                .map(InspectionSubmitRequest.RecordItem::getInspectionItemId)
                .collect(Collectors.toSet());
        if (actualIds.size() != request.getRecords().size()) {
            throw new BusinessException("检验明细中存在重复项目");
        }
        if (!actualIds.equals(expectedIds)) {
            throw new BusinessException("检验明细必须完整覆盖全部标准项，不能缺项或多项");
        }
    }

    private void validateConclusion(InspectionSubmitRequest request, boolean hasFailedItem) {
        if (request.getConclusion() == Conclusion.QUALIFIED && hasFailedItem) {
            throw new BusinessException("存在不合格明细时，整单结论不能为合格");
        }
        if ((request.getConclusion() == Conclusion.UNQUALIFIED || request.getConclusion() == Conclusion.CONCESSION)
                && !hasFailedItem) {
            throw new BusinessException("不合格或让步接收结论至少需要一项检验明细不通过");
        }
        if (request.getConclusion() == Conclusion.CONCESSION) {
            if (request.getConcessionDefect() == null) {
                throw new BusinessException("让步接收必须同步登记轻微缺陷");
            }
            if (StrUtil.isBlank(request.getConcessionDefect().getDescription())) {
                throw new BusinessException("让步缺陷描述不能为空");
            }
        }
        if (request.getConclusion() == Conclusion.UNQUALIFIED) {
            if (request.getUnqualifiedDefect() == null) {
                throw new BusinessException("不合格结论必须同步登记缺陷");
            }
            if (StrUtil.isBlank(request.getUnqualifiedDefect().getDescription())) {
                throw new BusinessException("不合格缺陷描述不能为空");
            }
        }
    }

    private void validateDefectQuantity(InspectionSubmitRequest request, TargetContext target) {
        if (request.getConclusion() == Conclusion.CONCESSION
                && request.getConcessionDefect() != null
                && request.getConcessionDefect().getQuantity().compareTo(target.targetQuantity()) > 0) {
            throw new BusinessException(target.batch() == null
                    ? "让步缺陷数量不能超过工单计划数量"
                    : "让步缺陷数量不能超过批次初始数量");
        }
        if (request.getConclusion() == Conclusion.UNQUALIFIED
                && request.getUnqualifiedDefect() != null
                && request.getUnqualifiedDefect().getQuantity().compareTo(target.targetQuantity()) > 0) {
            throw new BusinessException(target.batch() == null
                    ? "不合格缺陷数量不能超过工单计划数量"
                    : "不合格缺陷数量不能超过批次初始数量");
        }
    }

    private InspectionRecord toInspectionRecord(InspectionTask task,
                                                InspectionItem item,
                                                InspectionSubmitRequest.RecordItem submitted) {
        InspectionRecord record = new InspectionRecord();
        record.setInspectionTaskId(task.getId());
        record.setInspectionItemId(item.getId());
        record.setMeasuredValue(submitted.getMeasuredValue());
        record.setResultDesc(submitted.getResultDesc());
        record.setIsPass(resolvePass(item, submitted));
        record.setInspectedAt(LocalDateTime.now());
        return record;
    }

    private Integer resolvePass(InspectionItem item, InspectionSubmitRequest.RecordItem submitted) {
        if (item.getIsQuantitative() == 1) {
            BigDecimal value = submitted.getMeasuredValue();
            if (value == null) {
                throw new BusinessException("定量项目[" + item.getItemName() + "]必须填写实测值");
            }
            boolean lowerOk = item.getLowerLimit() == null || value.compareTo(item.getLowerLimit()) >= 0;
            boolean upperOk = item.getUpperLimit() == null || value.compareTo(item.getUpperLimit()) <= 0;
            return lowerOk && upperOk ? 1 : 0;
        }
        if (submitted.getIsPass() == null || (submitted.getIsPass() != 0 && submitted.getIsPass() != 1)) {
            throw new BusinessException("定性项目[" + item.getItemName() + "]必须填写单项判定");
        }
        return submitted.getIsPass();
    }

    private void applyConclusion(InspectionTask task, Conclusion conclusion, TargetContext target) {
        if (task.getInspectType() == InspectType.IPQC) {
            if (conclusion == Conclusion.UNQUALIFIED) {
                ProcessRecord record = target.processRecord();
                record.setStatus(ProcessStatus.IN_PROGRESS);
                record.setEndTime(null);
                processRecordMapper.updateById(record);
            }
            return;
        }
        Batch batch = target.batch();
        BatchStatus targetStatus = conclusion == Conclusion.UNQUALIFIED ? BatchStatus.FROZEN : BatchStatus.QUALIFIED;
        if (batch.getStatus() != targetStatus) {
            batch.getStatus().checkTransitionTo(targetStatus);
            batch.setStatus(targetStatus);
        }
        batchMapper.updateById(batch);
        if (task.getInspectType() == InspectType.FQC && batch.getProductionOrderId() != null) {
            ProductionOrder order = orderMapper.selectById(batch.getProductionOrderId());
            if (order != null && order.getStatus() == OrderStatus.COMPLETED) {
                order.setStatus(OrderStatus.CLOSED);
                orderMapper.updateById(order);
            }
        }
    }

    private void createConcessionDefect(InspectionTask task,
                                        InspectionSubmitRequest.ConcessionDefect request,
                                        TargetContext target) {
        DefectRecord defect = new DefectRecord();
        defect.setDefectNo(nextDefectNo(LocalDate.now()));
        defect.setBatchId(target.batch() == null ? null : target.batch().getId());
        defect.setProcessRecordId(target.processRecord() == null ? null : target.processRecord().getId());
        defect.setInspectionTaskId(task.getId());
        defect.setDefectType(request.getDefectType());
        defect.setSeverity(Severity.MINOR);
        defect.setQuantity(request.getQuantity());
        defect.setDescription(request.getDescription());
        defect.setHandleStatus(HandleStatus.PENDING);
        defectMapper.insert(defect);
    }

    private void createUnqualifiedDefect(InspectionTask task,
                                         InspectionSubmitRequest.UnqualifiedDefect request,
                                         TargetContext target) {
        DefectRecord defect = new DefectRecord();
        defect.setDefectNo(nextDefectNo(LocalDate.now()));
        defect.setBatchId(target.batch() == null ? null : target.batch().getId());
        defect.setProcessRecordId(target.processRecord() == null ? null : target.processRecord().getId());
        defect.setInspectionTaskId(task.getId());
        defect.setDefectType(request.getDefectType());
        defect.setSeverity(request.getSeverity());
        defect.setQuantity(request.getQuantity());
        defect.setDescription(request.getDescription());
        defect.setHandleStatus(HandleStatus.PENDING);
        defectMapper.insert(defect);
    }

    private List<InspectionTaskResponse> assembleTasks(List<InspectionTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Long> batchIds = tasks.stream()
                        .map(InspectionTask::getBatchId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList();
        Map<Long, Batch> batchById = batchIds.isEmpty()
                ? Map.of()
                : batchMapper.selectByIds(batchIds).stream()
                .collect(Collectors.toMap(Batch::getId, Function.identity()));

        List<Long> processRecordIds = tasks.stream()
                        .map(InspectionTask::getProcessRecordId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList();
        List<ProcessRecord> records = processRecordIds.isEmpty()
                ? List.of()
                : processRecordMapper.selectByIds(processRecordIds);
        Map<Long, ProcessRecord> recordById = records.stream()
                .collect(Collectors.toMap(ProcessRecord::getId, Function.identity()));
        Map<Long, ProcessDef> processById = records.isEmpty()
                ? Map.of()
                : processDefMapper.selectByIds(records.stream()
                        .map(ProcessRecord::getProcessDefId)
                        .distinct()
                        .toList()).stream().collect(Collectors.toMap(ProcessDef::getId, Function.identity()));

        return tasks.stream().map(task -> {
            Batch batch = task.getBatchId() == null ? null : batchById.get(task.getBatchId());
            ProcessRecord record = task.getProcessRecordId() == null ? null : recordById.get(task.getProcessRecordId());
            ProcessDef process = record == null ? null : processById.get(record.getProcessDefId());
            return InspectionTaskResponse.builder()
                    .id(task.getId())
                    .taskNo(task.getTaskNo())
                    .inspectType(task.getInspectType())
                    .batchId(task.getBatchId())
                    .batchNo(batch == null ? null : batch.getBatchNo())
                    .processRecordId(task.getProcessRecordId())
                    .stepNo(record == null ? null : record.getStepNo())
                    .processName(process == null ? null : process.getProcessName())
                    .status(task.getStatus())
                    .conclusion(task.getConclusion())
                    .inspectorId(task.getInspectorId())
                    .assignedAt(task.getAssignedAt())
                    .completedAt(task.getCompletedAt())
                    .remark(task.getRemark())
                    .createdAt(task.getCreatedAt())
                    .build();
        }).toList();
    }

    private String nextDefectNo(LocalDate date) {
        String prefix = "DF-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }

    private record TargetContext(Batch batch,
                                 ProcessRecord processRecord,
                                 Long materialId,
                                 Long processDefId,
                                 BigDecimal targetQuantity) {
    }
}
