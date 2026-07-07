package com.xinghui.qualitytrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinghui.qualitytrace.common.enums.RecallLevel;
import com.xinghui.qualitytrace.common.enums.RecallStatus;
import com.xinghui.qualitytrace.entity.RecallOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 召回单 Mapper —— 单表 CRUD + 高风险事务辅助查询 */
@Mapper
public interface RecallOrderMapper extends BaseMapper<RecallOrder> {

    /**
     * 召回单分页查询。
     *
     * <p>关键字覆盖召回单号、原因与源头批次号，保持前端筛选框提示与后端真实能力一致。</p>
     */
    @Select("""
            <script>
            SELECT ro.id, ro.recall_no, ro.source_batch_id, ro.recall_level, ro.reason,
                   ro.status, ro.initiator_id, ro.completed_at, ro.created_at, ro.updated_at
            FROM recall_order ro
                     LEFT JOIN batch b ON b.id = ro.source_batch_id
            <where>
                <if test="status != null">
                    AND ro.status = #{status}
                </if>
                <if test="recallLevel != null">
                    AND ro.recall_level = #{recallLevel}
                </if>
                <if test="keyword != null and keyword != ''">
                    AND (
                        ro.recall_no LIKE CONCAT('%', #{keyword}, '%')
                        OR ro.reason LIKE CONCAT('%', #{keyword}, '%')
                        OR b.batch_no LIKE CONCAT('%', #{keyword}, '%')
                    )
                </if>
            </where>
            ORDER BY ro.created_at DESC, ro.id DESC
            </script>
            """)
    Page<RecallOrder> selectPageForList(Page<RecallOrder> page,
                                        @Param("status") RecallStatus status,
                                        @Param("recallLevel") RecallLevel recallLevel,
                                        @Param("keyword") String keyword);

    /** 按主键查询并加行锁，用于召回明细更新/召回单关闭时串行化同一召回单。 */
    @Select("""
            SELECT id, recall_no, source_batch_id, recall_level, reason, status,
                   initiator_id, completed_at, created_at, updated_at
            FROM recall_order
            WHERE id = #{id}
            FOR UPDATE
            """)
    RecallOrder selectByIdForUpdate(@Param("id") Long id);
}
