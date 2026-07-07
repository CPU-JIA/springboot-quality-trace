package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.dto.defect.ProcessDefectTargetResponse;
import com.xinghui.qualitytrace.entity.ProcessRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 工序执行记录 Mapper —— 单表 CRUD */
@Mapper
public interface ProcessRecordMapper extends BaseMapper<ProcessRecord> {

    /** 按主键查询并加行锁，用于 IPQC 返工处置等高风险状态流转。 */
    @Select("""
            SELECT id, production_order_id, process_def_id, step_no, status,
                   operator_id, start_time, end_time, remark, created_at
            FROM process_record
            WHERE id = #{id}
            FOR UPDATE
            """)
    ProcessRecord selectByIdForUpdate(@Param("id") Long id);

    /**
     * 统计仍未关闭的工单中，有多少工序快照引用了指定工序定义。
     *
     * <p>工序定义的 need_ipqc 会影响后续报工是否生成 IPQC 任务、前后道门禁与完工入库校验。
     * 若未关闭工单已经引用该工序，修改 need_ipqc 会改变在制工单的质量门禁口径，必须拒绝。</p>
     */
    @Select("""
            SELECT COUNT(*)
            FROM process_record pr
                     JOIN production_order po ON po.id = pr.production_order_id
            WHERE pr.process_def_id = #{processDefId}
              AND po.status <> 'CLOSED'
            """)
    Long countOpenOrderReferences(@Param("processDefId") Long processDefId);

    /**
     * 分页查询当前可人工登记过程缺陷的工序。
     *
     * <p>条件与 DefectService#ensureManualProcessDefectLifecycle 保持一致：
     * 工单生产中、工序进行中、工单尚未产出批次、后续工序尚未开始。</p>
     */
    @Select("""
            <script>
            SELECT pr.id,
                   pr.production_order_id,
                   po.order_no,
                   po.status AS order_status,
                   pr.process_def_id,
                   pd.process_code,
                   pd.process_name,
                   pr.step_no,
                   pr.status,
                   m.material_code,
                   m.name AS material_name
            FROM process_record pr
                     JOIN production_order po ON po.id = pr.production_order_id
                     JOIN process_def pd ON pd.id = pr.process_def_id
                     JOIN material m ON m.id = po.material_id
            WHERE po.status = 'IN_PROGRESS'
              AND pr.status = 'IN_PROGRESS'
              AND NOT EXISTS (
                  SELECT 1
                  FROM batch b
                  WHERE b.production_order_id = po.id
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM process_record later
                  WHERE later.production_order_id = pr.production_order_id
                    AND later.step_no > pr.step_no
                    AND later.status != 'PENDING'
              )
            <if test="keyword != null and keyword != ''">
              AND (
                  po.order_no LIKE CONCAT('%', #{keyword}, '%')
                  OR pd.process_code LIKE CONCAT('%', #{keyword}, '%')
                  OR pd.process_name LIKE CONCAT('%', #{keyword}, '%')
                  OR m.material_code LIKE CONCAT('%', #{keyword}, '%')
                  OR m.name LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY po.created_at DESC, po.id DESC, pr.step_no ASC, pr.id ASC
            </script>
            """)
    Page<ProcessDefectTargetResponse> selectDefectTargetPage(Page<ProcessDefectTargetResponse> page,
                                                             @Param("keyword") String keyword);
}
