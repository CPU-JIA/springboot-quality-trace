package com.xinghui.qualitytrace.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.BatchStatus;
import com.xinghui.qualitytrace.common.enums.DefectType;
import com.xinghui.qualitytrace.common.enums.HandleMethod;
import com.xinghui.qualitytrace.common.enums.HandleStatus;
import com.xinghui.qualitytrace.common.enums.InspectType;
import com.xinghui.qualitytrace.common.enums.OrderStatus;
import com.xinghui.qualitytrace.common.enums.ProcessStatus;
import com.xinghui.qualitytrace.common.enums.SourceType;
import com.xinghui.qualitytrace.common.enums.TaskStatus;
import com.xinghui.qualitytrace.common.exception.BusinessException;
import com.xinghui.qualitytrace.common.result.PageBounds;
import com.xinghui.qualitytrace.common.result.PageResult;
import com.xinghui.qualitytrace.dto.defect.DefectCreateRequest;
import com.xinghui.qualitytrace.dto.defect.DefectHandleRequest;
import com.xinghui.qualitytrace.dto.defect.DefectResponse;
import com.xinghui.qualitytrace.dto.defect.ProcessDefectTargetResponse;
import com.xinghui.qualitytrace.entity.Batch;
import com.xinghui.qualitytrace.entity.DefectRecord;
import com.xinghui.qualitytrace.entity.InspectionTask;
import com.xinghui.qualitytrace.entity.ProductionOrder;
import com.xinghui.qualitytrace.entity.ProcessDef;
import com.xinghui.qualitytrace.entity.ProcessRecord;
import com.xinghui.qualitytrace.mapper.BatchMapper;
import com.xinghui.qualitytrace.mapper.DefectRecordMapper;
import com.xinghui.qualitytrace.mapper.InspectionTaskMapper;
import com.xinghui.qualitytrace.mapper.ProcessDefMapper;
import com.xinghui.qualitytrace.mapper.ProcessRecordMapper;
import com.xinghui.qualitytrace.mapper.ProductionOrderMapper;
import com.xinghui.qualitytrace.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 缺陷服务 —— 缺陷登记与质量主管处置（F5-4/F5-5）
 *
 * <p>批次类缺陷的处置会联动批次状态；IPQC 过程缺陷发生时尚无产出批次，
 * 其处置只关闭缺陷并保持工序返修状态，避免伪造不存在的批次流转。</p>
 */
@Service
@RequiredArgsConstructor
public class DefectService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final DefectRecordMapper defectMapper;
    private final BatchMapper batchMapper;
    private final ProcessRecordMapper processRecordMapper;
    private final ProcessDefMapper processDefMapper;
    private final InspectionTaskMapper taskMapper;
    private final ProductionOrderMapper orderMapper;
    private final SerialNumberService serialNumberService;

    /** 缺陷分页查询。 */
    public PageResult<DefectResponse> page(long current,
                                           long size,
                                           DefectType defectType,
                                           HandleStatus handleStatus,
                                           Long batchId,
                                           Long processRecordId,
                                           String keyword) {
        Page<DefectRecord> page = defectMapper.selectPage(PageBounds.page(current, size),
                new LambdaQueryWrapper<DefectRecord>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(DefectRecord::getDefectNo, keyword)
                                .or().like(DefectRecord::getDescription, keyword))
                        .eq(defectType != null, DefectRecord::getDefectType, defectType)
                        .eq(handleStatus != null, DefectRecord::getHandleStatus, handleStatus)
                        .eq(batchId != null, DefectRecord::getBatchId, batchId)
                        .eq(processRecordId != null, DefectRecord::getProcessRecordId, processRecordId)
                        .orderByDesc(DefectRecord::getCreatedAt)
                        .orderByDesc(DefectRecord::getId));
        return PageResult.of(assemble(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 当前允许人工登记过程缺陷的工序选项。 */
    public PageResult<ProcessDefectTargetResponse> processDefectTargets(long current, long size, String keyword) {
        Page<ProcessDefectTargetResponse> page = processRecordMapper.selectDefectTargetPage(
                PageBounds.page(current, size), StrUtil.trimToNull(keyword));
        return PageResult.of(page);
    }

    /** 人工登记缺陷。 */
    @Transactional(rollbackFor = Exception.class)
    public DefectRecord create(DefectCreateRequest request) {
        DefectTarget target = validateTarget(request);
        DefectRecord defect = new DefectRecord();
        defect.setDefectNo(nextDefectNo(LocalDate.now()));
        defect.setBatchId(request.getBatchId());
        defect.setProcessRecordId(request.getProcessRecordId());
        defect.setInspectionTaskId(request.getInspectionTaskId());
        defect.setDefectType(request.getDefectType());
        defect.setSeverity(request.getSeverity());
        defect.setQuantity(request.getQuantity());
        defect.setDescription(request.getDescription());
        defect.setHandleStatus(HandleStatus.PENDING);
        defectMapper.insert(defect);
        isolateBatchIfUsable(target.batch());
        return defect;
    }

    /** 质量主管处置缺陷，并按处置方式联动批次状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, DefectHandleRequest request) {
        DefectRecord defect = requireDefectForUpdate(id);
        if (defect.getHandleStatus() == HandleStatus.COMPLETED) {
            throw new BusinessException("该缺陷已处置，不能重复处理");
        }
        if (defect.getBatchId() == null) {
            handleProcessOnlyDefect(defect, request.getHandleMethod());
        } else {
            handleBatchDefect(defect, request.getHandleMethod());
        }
        defect.setHandleMethod(request.getHandleMethod());
        defect.setHandleStatus(HandleStatus.COMPLETED);
        defect.setHandlerId(UserContext.currentUserId());
        defect.setHandledAt(LocalDateTime.now());
        defectMapper.updateById(defect);
    }

    // ---------------- 私有辅助 ----------------

    private DefectRecord requireDefect(Long id) {
        DefectRecord defect = defectMapper.selectById(id);
        if (defect == null) {
            throw new BusinessException("缺陷记录不存在或已被删除");
        }
        return defect;
    }

    private DefectRecord requireDefectForUpdate(Long id) {
        DefectRecord defect = defectMapper.selectByIdForUpdate(id);
        if (defect == null) {
            throw new BusinessException("缺陷记录不存在或已被删除");
        }
        return defect;
    }

    private DefectTarget validateTarget(DefectCreateRequest request) {
        if (request.getBatchId() == null && request.getProcessRecordId() == null) {
            throw new BusinessException("缺陷必须关联批次或工序记录");
        }
        if (request.getBatchId() != null && request.getProcessRecordId() != null) {
            throw new BusinessException("缺陷载体只能选择批次或工序其一");
        }
        Batch batch = null;
        ProcessRecord record = null;
        if (request.getBatchId() != null) {
            batch = batchMapper.selectByIdForUpdate(request.getBatchId());
            if (batch == null) {
                throw new BusinessException("关联批次不存在");
            }
            if (request.getQuantity().compareTo(batch.getQuantity()) > 0) {
                throw new BusinessException("缺陷数量不能超过批次初始数量");
            }
            if (batch.getStatus() == BatchStatus.PENDING_INSPECT || batch.getStatus() == BatchStatus.INSPECTING) {
                throw new BusinessException("批次仍处于检验流程中，请通过检验任务提交不合格结论");
            }
        }
        if (request.getProcessRecordId() != null) {
            record = processRecordMapper.selectByIdForUpdate(request.getProcessRecordId());
            if (record == null) {
                throw new BusinessException("关联工序记录不存在");
            }
            ProductionOrder order = orderMapper.selectById(record.getProductionOrderId());
            if (order == null) {
                throw new BusinessException("缺陷关联工序所属工单不存在");
            }
            if (request.getQuantity().compareTo(order.getPlanQuantity()) > 0) {
                throw new BusinessException("缺陷数量不能超过工单计划数量");
            }
            ensureManualProcessDefectLifecycle(record, order);
        }
        if (request.getInspectionTaskId() != null) {
            InspectionTask task = taskMapper.selectById(request.getInspectionTaskId());
            if (task == null) {
                throw new BusinessException("关联检验任务不存在");
            }
            if (request.getBatchId() != null && task.getInspectType() == InspectType.IPQC) {
                throw new BusinessException("过程检验任务不能关联批次缺陷");
            }
            if (request.getProcessRecordId() != null && task.getInspectType() != InspectType.IPQC) {
                throw new BusinessException("来料/成品检验任务不能关联过程缺陷");
            }
            if (request.getBatchId() != null && task.getBatchId() != null && !request.getBatchId().equals(task.getBatchId())) {
                throw new BusinessException("缺陷批次与检验任务批次不一致");
            }
            if (request.getProcessRecordId() != null && task.getProcessRecordId() != null
                    && !request.getProcessRecordId().equals(task.getProcessRecordId())) {
                throw new BusinessException("缺陷工序与检验任务工序不一致");
            }
        }
        return new DefectTarget(batch);
    }

    private void ensureManualProcessDefectLifecycle(ProcessRecord record, ProductionOrder order) {
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CLOSED) {
            throw new BusinessException("工单已产出批次，过程问题请登记批次缺陷");
        }
        if (order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw new BusinessException("过程缺陷只能登记在生产中的工单");
        }
        if (record.getStatus() != ProcessStatus.IN_PROGRESS) {
            throw new BusinessException("手工过程缺陷只能登记在进行中的工序");
        }
        Long startedLaterSteps = processRecordMapper.selectCount(new LambdaQueryWrapper<ProcessRecord>()
                .eq(ProcessRecord::getProductionOrderId, record.getProductionOrderId())
                .gt(ProcessRecord::getStepNo, record.getStepNo())
                .ne(ProcessRecord::getStatus, ProcessStatus.PENDING));
        if (startedLaterSteps != null && startedLaterSteps > 0) {
            throw new BusinessException("后续工序已开始，不能补登前道过程缺陷");
        }
    }

    private void isolateBatchIfUsable(Batch batch) {
        if (batch == null || batch.getStatus() != BatchStatus.QUALIFIED) {
            return;
        }
        batch.setStatus(BatchStatus.FROZEN);
        batchMapper.updateById(batch);
    }

    private void handleProcessOnlyDefect(DefectRecord defect, HandleMethod method) {
        if (method != HandleMethod.REWORK && method != HandleMethod.CONCESSION) {
            throw new BusinessException("过程缺陷无批次载体，只允许返工或让步关闭");
        }
        if (method == HandleMethod.REWORK && defect.getProcessRecordId() != null) {
            ProcessRecord record = processRecordMapper.selectByIdForUpdate(defect.getProcessRecordId());
            if (record != null) {
                record.setStatus(ProcessStatus.IN_PROGRESS);
                record.setEndTime(null);
                processRecordMapper.updateById(record);
            }
        }
    }

    private void handleBatchDefect(DefectRecord defect, HandleMethod method) {
        Batch batch = batchMapper.selectByIdForUpdate(defect.getBatchId());
        if (batch == null) {
            throw new BusinessException("缺陷关联批次不存在");
        }
        BatchStatus target = switch (method) {
            case REWORK -> BatchStatus.PENDING_INSPECT;
            case SCRAP -> BatchStatus.SCRAPPED;
            case CONCESSION -> BatchStatus.QUALIFIED;
            case RETURN -> BatchStatus.RETURNED;
        };
        if (method == HandleMethod.RETURN && batch.getSourceType() != SourceType.PURCHASE) {
            throw new BusinessException("退货处置仅适用于采购来源批次");
        }
        if (batch.getStatus() != target) {
            batch.getStatus().checkTransitionTo(target);
            batch.setStatus(target);
            batchMapper.updateById(batch);
        }
        if (method == HandleMethod.REWORK) {
            createRetestTask(defect, batch);
        }
    }

    /** 返工完成后自动生成同类型重检任务；无原检验单时按批次来源推断 IQC/FQC。 */
    private void createRetestTask(DefectRecord defect, Batch batch) {
        InspectType inspectType = inferRetestType(defect, batch);
        InspectionTask task = new InspectionTask();
        task.setTaskNo(nextTaskNo(LocalDate.now()));
        task.setInspectType(inspectType);
        task.setStatus(TaskStatus.PENDING);
        if (inspectType == InspectType.IPQC) {
            task.setProcessRecordId(defect.getProcessRecordId());
        } else {
            task.setBatchId(batch.getId());
        }
        taskMapper.insert(task);
    }

    private InspectType inferRetestType(DefectRecord defect, Batch batch) {
        if (defect.getInspectionTaskId() != null) {
            InspectionTask task = taskMapper.selectById(defect.getInspectionTaskId());
            if (task != null) {
                return task.getInspectType();
            }
        }
        return batch.getSourceType() == SourceType.PURCHASE ? InspectType.IQC : InspectType.FQC;
    }

    private List<DefectResponse> assemble(List<DefectRecord> defects) {
        if (defects.isEmpty()) {
            return List.of();
        }
        List<Long> batchIds = defects.stream()
                        .map(DefectRecord::getBatchId)
                        .filter(v -> v != null)
                        .distinct()
                        .toList();
        Map<Long, Batch> batchById = batchIds.isEmpty()
                ? Map.of()
                : batchMapper.selectByIds(batchIds).stream()
                .collect(Collectors.toMap(Batch::getId, Function.identity()));

        List<Long> processRecordIds = defects.stream()
                .map(DefectRecord::getProcessRecordId)
                .filter(v -> v != null)
                .distinct()
                .toList();
        List<ProcessRecord> records = processRecordIds.isEmpty()
                ? List.of()
                : processRecordMapper.selectByIds(processRecordIds);
        Map<Long, ProcessRecord> recordById = records.stream()
                .collect(Collectors.toMap(ProcessRecord::getId, Function.identity()));
        Map<Long, ProcessDef> processById = records.isEmpty()
                ? Map.of()
                : processDefMapper.selectByIds(records.stream().map(ProcessRecord::getProcessDefId).distinct().toList())
                .stream().collect(Collectors.toMap(ProcessDef::getId, Function.identity()));

        List<Long> taskIds = defects.stream()
                        .map(DefectRecord::getInspectionTaskId)
                        .filter(v -> v != null)
                        .distinct()
                        .toList();
        Map<Long, InspectionTask> taskById = taskIds.isEmpty()
                ? Map.of()
                : taskMapper.selectByIds(taskIds).stream()
                .collect(Collectors.toMap(InspectionTask::getId, Function.identity()));

        return defects.stream().map(defect -> {
            Batch batch = defect.getBatchId() == null ? null : batchById.get(defect.getBatchId());
            ProcessRecord record = defect.getProcessRecordId() == null ? null : recordById.get(defect.getProcessRecordId());
            ProcessDef process = record == null ? null : processById.get(record.getProcessDefId());
            InspectionTask task = defect.getInspectionTaskId() == null ? null : taskById.get(defect.getInspectionTaskId());
            return DefectResponse.builder()
                    .id(defect.getId())
                    .defectNo(defect.getDefectNo())
                    .batchId(defect.getBatchId())
                    .batchNo(batch == null ? null : batch.getBatchNo())
                    .batchStatus(batch == null ? null : batch.getStatus())
                    .batchSourceType(batch == null ? null : batch.getSourceType())
                    .processRecordId(defect.getProcessRecordId())
                    .stepNo(record == null ? null : record.getStepNo())
                    .processName(process == null ? null : process.getProcessName())
                    .inspectionTaskId(defect.getInspectionTaskId())
                    .taskNo(task == null ? null : task.getTaskNo())
                    .defectType(defect.getDefectType())
                    .severity(defect.getSeverity())
                    .quantity(defect.getQuantity())
                    .description(defect.getDescription())
                    .handleMethod(defect.getHandleMethod())
                    .handleStatus(defect.getHandleStatus())
                    .handlerId(defect.getHandlerId())
                    .handledAt(defect.getHandledAt())
                    .createdAt(defect.getCreatedAt())
                    .updatedAt(defect.getUpdatedAt())
                    .build();
        }).toList();
    }

    private String nextDefectNo(LocalDate date) {
        String prefix = "DF-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }

    private String nextTaskNo(LocalDate date) {
        String prefix = "QC-" + date.format(NO_DATE);
        return serialNumberService.nextNo(prefix);
    }

    private record DefectTarget(Batch batch) {
    }
}
