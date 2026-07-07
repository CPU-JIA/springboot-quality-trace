package com.xinghui.qualitytrace.mapper;

import com.xinghui.qualitytrace.dto.trace.TraceDownstreamRow;
import com.xinghui.qualitytrace.dto.trace.TraceUpstreamRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 追溯 Mapper —— 手写递归 CTE（数据库课设核心展示点）
 *
 * <p>追溯链由"工单产出批次"与"工单消耗批次"两跳组成。这里直接使用
 * WITH RECURSIVE 展开任意层级上下游，返回按路径枚举的结果行。</p>
 */
@Mapper
public interface TraceMapper {

    /** 反向追溯：给定批次，逐级向上游展开它由哪些批次构成。 */
    @Select("""
            WITH RECURSIVE upstream AS (
                SELECT b.id,
                       b.batch_no,
                       b.material_id,
                       b.production_order_id,
                       CAST(NULL AS DECIMAL(12, 3)) AS consumed_quantity,
                       0 AS level,
                       CAST(b.id AS CHAR(500)) AS path
                FROM batch b
                WHERE b.id = #{batchId}
                UNION ALL
                SELECT pb.id,
                       pb.batch_no,
                       pb.material_id,
                       pb.production_order_id,
                       bc.quantity,
                       u.level + 1,
                       CONCAT(u.path, '>', pb.id)
                FROM upstream u
                         JOIN batch_consumption bc ON bc.production_order_id = u.production_order_id
                         JOIN batch pb ON pb.id = bc.consumed_batch_id
                WHERE u.production_order_id IS NOT NULL
                  AND u.level < 10
                  AND LOCATE(CONCAT('>', pb.id, '>'), CONCAT('>', u.path, '>')) = 0
            )
            SELECT u.id AS batch_id,
                   u.batch_no,
                   m.material_code,
                   m.name AS material_name,
                   m.category AS material_category,
                   s.name AS supplier_name,
                   po.order_no AS production_order_no,
                   (
                       SELECT GROUP_CONCAT(
                                  CONCAT(pr.step_no, '.', pd.process_name, '（',
                                         CASE pr.status
                                             WHEN 'PENDING' THEN '待开工'
                                             WHEN 'IN_PROGRESS' THEN '进行中'
                                             WHEN 'COMPLETED' THEN '已完工'
                                             ELSE pr.status
                                         END, '）')
                                  ORDER BY pr.step_no
                                  SEPARATOR '；'
                              )
                       FROM process_record pr
                                JOIN process_def pd ON pd.id = pr.process_def_id
                       WHERE pr.production_order_id = u.production_order_id
                   ) AS process_summary,
                   (
                       SELECT GROUP_CONCAT(
                                  CONCAT(it.task_no, ' ', it.inspect_type, ' ',
                                         CASE it.status
                                             WHEN 'PENDING' THEN '待领取'
                                             WHEN 'IN_PROGRESS' THEN '检验中'
                                             WHEN 'COMPLETED' THEN '已完成'
                                             ELSE it.status
                                         END,
                                         CASE
                                             WHEN it.conclusion IS NULL THEN ''
                                             ELSE CONCAT('/', CASE it.conclusion
                                                 WHEN 'QUALIFIED' THEN '合格'
                                                 WHEN 'UNQUALIFIED' THEN '不合格'
                                                 WHEN 'CONCESSION' THEN '让步接收'
                                                 ELSE it.conclusion
                                             END)
                                         END)
                                  ORDER BY it.created_at, it.id
                                  SEPARATOR '；'
                              )
                       FROM inspection_task it
                                LEFT JOIN process_record ipr ON ipr.id = it.process_record_id
                       WHERE it.batch_id = u.id
                          OR (u.production_order_id IS NOT NULL AND ipr.production_order_id = u.production_order_id)
                   ) AS inspection_summary,
                   u.consumed_quantity,
                   u.level,
                   u.path
            FROM upstream u
                     JOIN material m ON m.id = u.material_id
                     LEFT JOIN batch b ON b.id = u.id
                     LEFT JOIN supplier s ON s.id = b.supplier_id
                     LEFT JOIN production_order po ON po.id = u.production_order_id
            ORDER BY u.level, u.batch_no
            """)
    List<TraceUpstreamRow> upstream(@Param("batchId") Long batchId);

    /** 正向追溯：给定批次，逐级向下游展开被做成了哪些批次并出货给哪些客户。 */
    @Select("""
            WITH RECURSIVE downstream AS (
                SELECT b.id,
                       b.batch_no,
                       b.material_id,
                       0 AS level,
                       CAST(b.id AS CHAR(500)) AS path
                FROM batch b
                WHERE b.id = #{batchId}
                UNION ALL
                SELECT nb.id,
                       nb.batch_no,
                       nb.material_id,
                       d.level + 1,
                       CONCAT(d.path, '>', nb.id)
                FROM downstream d
                         JOIN batch_consumption bc ON bc.consumed_batch_id = d.id
                         JOIN batch nb ON nb.production_order_id = bc.production_order_id
                WHERE d.level < 10
                  AND LOCATE(CONCAT('>', nb.id, '>'), CONCAT('>', d.path, '>')) = 0
            )
            SELECT d.id AS batch_id,
                   d.batch_no,
                   m.material_code,
                   m.name AS material_name,
                   m.category AS material_category,
                   b.status AS batch_status,
                   b.remaining_quantity,
                   d.level,
                   d.path,
                   sh.id AS shipment_id,
                   sh.shipment_no,
                   c.name AS customer_name,
                   sh.quantity AS shipped_quantity,
                   sh.ship_date
            FROM downstream d
                     JOIN material m ON m.id = d.material_id
                     JOIN batch b ON b.id = d.id
                     LEFT JOIN shipment sh ON sh.batch_id = d.id
                     LEFT JOIN customer c ON c.id = sh.customer_id
            ORDER BY d.level, d.batch_no, sh.ship_date
            """)
    List<TraceDownstreamRow> downstream(@Param("batchId") Long batchId);
}
