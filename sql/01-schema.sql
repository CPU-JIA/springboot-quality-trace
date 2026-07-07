-- =====================================================================
-- 产品质量追踪系统 · 数据库建库脚本
-- =====================================================================
-- 数据库 : MySQL 8.4.10（8.4 LTS 线。8.0 线已于 2026-04 EOL，故不采用；
--          依赖特性：递归 CTE、强制 CHECK 约束、utf8mb4_0900_ai_ci 排序规则、
--          窗口函数——以上特性 8.0/8.4 行为完全同源）
-- 文档   : docs/02-数据库设计.md（ER 图与设计决策）
-- 内容   : 建库 → 21 张表（含全部约束与索引）→ 3 个视图 → 2 个触发器
--          → 2 个存储过程 → （可选）最小权限应用账号
-- 执行   : mysql -uroot -p < 01-schema.sql
-- 注意   : 脚本可重复执行（先 DROP 后 CREATE），生产环境严禁如此，
--          课设演示环境为便于重建数据库特意保留
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 建库：utf8mb4 完整支持中文，0900_ai_ci 为 MySQL 8 默认排序规则
-- ---------------------------------------------------------------------
DROP DATABASE IF EXISTS quality_trace;
CREATE DATABASE quality_trace
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE quality_trace;

-- =====================================================================
-- 1. 系统权限域（RBAC：用户 - 角色）
-- =====================================================================

-- 1.1 用户表：系统登录主体，密码以 BCrypt 摘要存储（不可逆）
CREATE TABLE sys_user (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID（代理主键）',
    username    VARCHAR(50)     NOT NULL                COMMENT '登录账号（唯一）',
    password    VARCHAR(100)    NOT NULL                COMMENT '密码 BCrypt 摘要（60字符，永不存明文）',
    real_name   VARCHAR(50)     NOT NULL                COMMENT '真实姓名',
    phone       VARCHAR(20)     NULL                    COMMENT '手机号',
    status      TINYINT         NOT NULL DEFAULT 1      COMMENT '账号状态：1=启用 0=禁用（禁用即无法登录，不做物理删除）',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    CONSTRAINT chk_user_status CHECK (status IN (0, 1))
) ENGINE = InnoDB COMMENT = '系统用户表';

-- 1.2 角色表：五种预置角色，权限粒度到菜单级（详见需求分析 2.1）
CREATE TABLE sys_role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_code   VARCHAR(30)     NOT NULL                COMMENT '角色编码（如 ADMIN / INSPECTOR，程序中据此判断权限）',
    role_name   VARCHAR(50)     NOT NULL                COMMENT '角色显示名称',
    description VARCHAR(200)    NULL                    COMMENT '角色职责描述',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB COMMENT = '系统角色表';

-- 1.3 用户角色关联表：由 ER 图 M:N 联系"拥有"转换而来
CREATE TABLE sys_user_role (
    id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    user_id BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
    role_id BIGINT UNSIGNED NOT NULL                COMMENT '角色ID',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    PRIMARY KEY (id),
    -- 组合唯一：同一用户不可重复授予同一角色（M:N 联系的候选码）
    UNIQUE KEY uk_user_role (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB COMMENT = '用户-角色关联表（RBAC）';

-- =====================================================================
-- 2. 往来单位（供应商 / 客户）
-- =====================================================================

-- 2.1 供应商表：原材料批次的来源，供应商质量排名的统计主体
CREATE TABLE supplier (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
    supplier_code  VARCHAR(30)     NOT NULL                COMMENT '供应商编码（如 SUP-001）',
    name           VARCHAR(100)    NOT NULL                COMMENT '供应商名称',
    contact_person VARCHAR(50)     NULL                    COMMENT '联系人',
    phone          VARCHAR(20)     NULL                    COMMENT '联系电话',
    address        VARCHAR(200)    NULL                    COMMENT '地址',
    status         TINYINT         NOT NULL DEFAULT 1      COMMENT '合作状态：1=合作中 0=停用（停用后不可新建入库，历史追溯不受影响）',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_code (supplier_code),
    CONSTRAINT chk_supplier_status CHECK (status IN (0, 1))
) ENGINE = InnoDB COMMENT = '供应商表';

-- 2.2 客户表：成品出货的去向，召回通知的对象
CREATE TABLE customer (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '客户ID',
    customer_code  VARCHAR(30)     NOT NULL                COMMENT '客户编码（如 CUS-001）',
    name           VARCHAR(100)    NOT NULL                COMMENT '客户名称',
    contact_person VARCHAR(50)     NULL                    COMMENT '联系人',
    phone          VARCHAR(20)     NULL                    COMMENT '联系电话',
    address        VARCHAR(200)    NULL                    COMMENT '地址',
    status         TINYINT         NOT NULL DEFAULT 1      COMMENT '合作状态：1=合作中 0=停用',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_code (customer_code),
    CONSTRAINT chk_customer_status CHECK (status IN (0, 1))
) ENGINE = InnoDB COMMENT = '客户表';

-- =====================================================================
-- 3. 主数据域（物料 / BOM / 工序 / 工艺路线 / 检验标准）
-- =====================================================================

-- 3.1 物料表：原材料、半成品、成品统一建模（category 区分），追溯网络的"节点类型"定义
CREATE TABLE material (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '物料ID',
    material_code   VARCHAR(30)     NOT NULL                COMMENT '物料编码（如 RM-001 温控器）',
    name            VARCHAR(100)    NOT NULL                COMMENT '物料名称',
    category        VARCHAR(10)     NOT NULL                COMMENT '物料类别：RAW=原材料 SEMI=半成品 PRODUCT=成品',
    spec            VARCHAR(100)    NULL                    COMMENT '规格型号',
    unit            VARCHAR(10)     NOT NULL                COMMENT '计量单位（个/kg/m 等）',
    shelf_life_days INT UNSIGNED    NULL                    COMMENT '保质期天数（NULL=不限；入库时据此推算批次到期日）',
    status          TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1=启用 0=停用',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_code (material_code),
    CONSTRAINT chk_material_category CHECK (category IN ('RAW', 'SEMI', 'PRODUCT')),
    CONSTRAINT chk_material_status   CHECK (status IN (0, 1))
) ENGINE = InnoDB COMMENT = '物料表（原材料/半成品/成品统一建模）';

-- 3.2 BOM 物料清单表：由 ER 图中物料对自身的 M:N 联系"构成"转换而来
--     语义：生产 1 单位父项物料需要 quantity 单位子项物料（支持多级嵌套）
CREATE TABLE bom (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'BOM行ID',
    parent_material_id BIGINT UNSIGNED NOT NULL                COMMENT '父项物料ID（半成品或成品）',
    child_material_id  BIGINT UNSIGNED NOT NULL                COMMENT '子项物料ID（原材料或半成品）',
    quantity           DECIMAL(12, 3)  NOT NULL                COMMENT '单位用量：生产1单位父项所需子项数量',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    -- 同一父项下同一子项只允许一行（M:N 联系候选码）
    UNIQUE KEY uk_bom_parent_child (parent_material_id, child_material_id),
    CONSTRAINT fk_bom_parent FOREIGN KEY (parent_material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_bom_child  FOREIGN KEY (child_material_id)  REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- 禁止"自己构成自己"（防御最直接的环；深层环由应用层校验）
    CONSTRAINT chk_bom_no_self  CHECK (parent_material_id <> child_material_id),
    CONSTRAINT chk_bom_quantity CHECK (quantity > 0)
) ENGINE = InnoDB COMMENT = '物料清单表（BOM，物料自关联M:N）';

-- 3.3 工序定义表：全局工序字典（注塑/装配/总装/测试/包装…）
CREATE TABLE process_def (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工序ID',
    process_code VARCHAR(30)     NOT NULL                COMMENT '工序编码（如 PR-ASSY 总装）',
    process_name VARCHAR(50)     NOT NULL                COMMENT '工序名称',
    need_ipqc    TINYINT         NOT NULL DEFAULT 0      COMMENT '是否IPQC检验点：1=该工序完工时自动生成过程检验任务 0=否',
    description  VARCHAR(200)    NULL                    COMMENT '作业内容说明',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_code (process_code),
    CONSTRAINT chk_process_need_ipqc CHECK (need_ipqc IN (0, 1))
) ENGINE = InnoDB COMMENT = '工序定义表（全局工序字典）';

-- 3.4 工艺路线表：由 M:N 联系"工艺路线"转换而来
--     语义：某物料（半成品/成品）的生产依次经过哪些工序
CREATE TABLE process_route (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '路线行ID',
    material_id    BIGINT UNSIGNED NOT NULL                COMMENT '物料ID（被生产的半成品/成品）',
    process_def_id BIGINT UNSIGNED NOT NULL                COMMENT '工序ID',
    step_no        INT UNSIGNED    NOT NULL                COMMENT '步骤序号（从1连续递增，决定工序执行顺序）',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    -- 同一物料的步骤序号唯一（保证路线是一个全序）
    UNIQUE KEY uk_route_material_step (material_id, step_no),
    CONSTRAINT fk_route_material FOREIGN KEY (material_id)    REFERENCES material (id)    ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_route_process  FOREIGN KEY (process_def_id) REFERENCES process_def (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_route_step CHECK (step_no >= 1)
) ENGINE = InnoDB COMMENT = '工艺路线表（物料-工序 M:N，带顺序）';

-- 3.5 检验项目表：检验标准的最小单元，按物料+检验类型组织
--     定量项（is_quantitative=1）按上下限自动判定；定性项人工判定
CREATE TABLE inspection_item (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '检验项目ID',
    item_code       VARCHAR(30)     NOT NULL                COMMENT '项目编码（如 II-001）',
    item_name       VARCHAR(100)    NOT NULL                COMMENT '项目名称（如 触点接触电阻）',
    inspect_type    VARCHAR(10)     NOT NULL                COMMENT '检验类型：IQC=来料 IPQC=过程 FQC=成品',
    material_id     BIGINT UNSIGNED NOT NULL                COMMENT '适用物料ID（该物料做此类检验时需检此项）',
    process_def_id  BIGINT UNSIGNED NULL                    COMMENT '挂靠工序ID（仅IPQC项目填写：该工序完工时检验）',
    is_quantitative TINYINT         NOT NULL                COMMENT '是否定量：1=录实测值按上下限判定 0=定性人工判定',
    standard_desc   VARCHAR(200)    NOT NULL                COMMENT '检验标准描述（判定依据的文字表述）',
    lower_limit     DECIMAL(12, 3)  NULL                    COMMENT '下限（定量项，可单边）',
    upper_limit     DECIMAL(12, 3)  NULL                    COMMENT '上限（定量项，可单边）',
    unit            VARCHAR(10)     NULL                    COMMENT '计量单位（如 mΩ / V）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_code (item_code),
    KEY idx_item_material_type (material_id, inspect_type),
    CONSTRAINT fk_item_material FOREIGN KEY (material_id)    REFERENCES material (id)    ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_item_process  FOREIGN KEY (process_def_id) REFERENCES process_def (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_item_type  CHECK (inspect_type IN ('IQC', 'IPQC', 'FQC')),
    CONSTRAINT chk_item_quant CHECK (is_quantitative IN (0, 1)),
    -- IPQC 项目必须挂靠工序；IQC/FQC 项目不挂工序（互斥性约束）
    CONSTRAINT chk_item_ipqc_process CHECK (
        (inspect_type = 'IPQC' AND process_def_id IS NOT NULL)
        OR (inspect_type IN ('IQC', 'FQC') AND process_def_id IS NULL)
    )
) ENGINE = InnoDB COMMENT = '检验项目表（检验标准）';

-- =====================================================================
-- 4. 生产与追溯域（工单 / 批次 / 消耗关系 / 工序记录）
-- =====================================================================

-- 4.1 生产工单表：生产活动的组织单元；先建（batch 引用它，无循环依赖）
CREATE TABLE production_order (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    order_no          VARCHAR(30)     NOT NULL                COMMENT '工单号（格式 MO-yyyyMMdd-3位序号）',
    material_id       BIGINT UNSIGNED NOT NULL                COMMENT '生产物料ID（仅 SEMI/PRODUCT，应用层校验）',
    plan_quantity     DECIMAL(12, 3)  NOT NULL                COMMENT '计划生产数量',
    actual_quantity   DECIMAL(12, 3)  NULL                    COMMENT '实际完工数量（完工时填写，可能有工损）',
    status            VARCHAR(20)     NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED=已创建 IN_PROGRESS=生产中 COMPLETED=已完工 CLOSED=已关闭',
    plan_start_date   DATE            NOT NULL                COMMENT '计划开始日期',
    plan_end_date     DATE            NOT NULL                COMMENT '计划结束日期',
    actual_start_time DATETIME        NULL                    COMMENT '实际开工时间（首道工序开工时记录）',
    actual_end_time   DATETIME        NULL                    COMMENT '实际完工时间',
    manager_id        BIGINT UNSIGNED NOT NULL                COMMENT '负责人ID（生产人员）',
    remark            VARCHAR(500)    NULL                    COMMENT '备注',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_material (material_id),
    KEY idx_order_status (status),
    CONSTRAINT fk_order_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_order_manager  FOREIGN KEY (manager_id)  REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_order_status   CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED')),
    CONSTRAINT chk_order_plan_qty CHECK (plan_quantity > 0),
    CONSTRAINT chk_order_dates    CHECK (plan_end_date >= plan_start_date)
) ENGINE = InnoDB COMMENT = '生产工单表';

-- 4.2 批次表：全系统追溯基本单元（★核心表）
--     原材料批次（采购入库产生）与半成品/成品批次（工单完工产生）统一建模
CREATE TABLE batch (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批次ID',
    batch_no            VARCHAR(30)     NOT NULL                COMMENT '批次号（RM/SF/FP-yyyyMMdd-3位序号，前缀对应物料类别）',
    material_id         BIGINT UNSIGNED NOT NULL                COMMENT '所属物料ID',
    source_type         VARCHAR(10)     NOT NULL                COMMENT '来源类型：PURCHASE=采购入库 PRODUCTION=生产完工',
    supplier_id         BIGINT UNSIGNED NULL                    COMMENT '供应商ID（仅采购来源批次填写）',
    production_order_id BIGINT UNSIGNED NULL                    COMMENT '产出工单ID（仅生产来源批次填写；UNIQUE 体现"一工单产出一批次"的1:1联系）',
    quantity            DECIMAL(12, 3)  NOT NULL                COMMENT '初始数量',
    remaining_quantity  DECIMAL(12, 3)  NOT NULL                COMMENT '剩余数量（受控冗余：领料/出货原子扣减，兼任库存角色，见设计文档§5.2）',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING_INSPECT' COMMENT '批次状态（8态状态机，见设计文档§5.6）：PENDING_INSPECT=待检 INSPECTING=检验中 QUALIFIED=合格在库 FROZEN=冻结 SCRAPPED=已报废 RETURNED=已退货 DEPLETED=已耗尽 RECALLED=已召回',
    production_date     DATE            NOT NULL                COMMENT '生产日期（采购批次为入库日期）',
    expire_date         DATE            NULL                    COMMENT '到期日期（生产日期+物料保质期，无保质期为NULL）',
    warehouse_location  VARCHAR(50)     NULL                    COMMENT '库位（简化为文本描述）',
    created_by          BIGINT UNSIGNED NOT NULL                COMMENT '登记人ID',
    remark              VARCHAR(500)    NULL                    COMMENT '备注',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no),
    -- UNIQUE 外键列：数据库层强制"一张工单至多产出一个批次"（1:1联系）
    UNIQUE KEY uk_batch_order (production_order_id),
    -- 复合索引：领料选批次的高频查询路径（按物料查合格在库批次）
    KEY idx_batch_material_status (material_id, status),
    KEY idx_batch_supplier (supplier_id),
    CONSTRAINT fk_batch_material FOREIGN KEY (material_id)         REFERENCES material (id)         ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_batch_supplier FOREIGN KEY (supplier_id)         REFERENCES supplier (id)         ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_batch_order    FOREIGN KEY (production_order_id) REFERENCES production_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_batch_creator  FOREIGN KEY (created_by)          REFERENCES sys_user (id)         ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_batch_source CHECK (source_type IN ('PURCHASE', 'PRODUCTION')),
    CONSTRAINT chk_batch_status CHECK (status IN ('PENDING_INSPECT', 'INSPECTING', 'QUALIFIED', 'FROZEN',
                                                  'SCRAPPED', 'RETURNED', 'DEPLETED', 'RECALLED')),
    CONSTRAINT chk_batch_qty       CHECK (quantity > 0),
    -- 余量非负 + 不得超过初始量：防超扣的最后一道防线（应用层扣减语句为第一道）
    CONSTRAINT chk_batch_remaining CHECK (remaining_quantity >= 0 AND remaining_quantity <= quantity),
    -- 来源与外键互斥：采购批次必有供应商且无工单；生产批次必有工单且无供应商
    CONSTRAINT chk_batch_source_fk CHECK (
        (source_type = 'PURCHASE'   AND supplier_id IS NOT NULL AND production_order_id IS NULL)
        OR (source_type = 'PRODUCTION' AND production_order_id IS NOT NULL AND supplier_id IS NULL)
    )
) ENGINE = InnoDB COMMENT = '批次表（追溯基本单元，兼任库存）';

-- 4.3 批次消耗表：由 M:N 联系"投料"转换而来 —— 追溯链的"边"（★★全系统最核心的表）
--     语义：某工单生产过程中消耗了某批次多少数量
--     追溯即沿本表 + batch.production_order_id 两跳递归遍历（见 §7 存储过程）
CREATE TABLE batch_consumption (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消耗记录ID',
    production_order_id BIGINT UNSIGNED NOT NULL                COMMENT '消耗方工单ID',
    consumed_batch_id   BIGINT UNSIGNED NOT NULL                COMMENT '被消耗批次ID（原材料或半成品批次）',
    quantity            DECIMAL(12, 3)  NOT NULL                COMMENT '消耗数量',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领料时间',
    PRIMARY KEY (id),
    -- 同一工单对同一批次的消耗合并为一条（多次领料累加数量，应用层处理）
    UNIQUE KEY uk_bc_order_batch (production_order_id, consumed_batch_id),
    -- ★双向索引成对出现，是双向追溯查询性能的关键：
    --   idx_bc_order 支撑反向追溯（工单→消耗了哪些批次）
    --   （uk_bc_order_batch 最左前缀已覆盖 production_order_id，无需重复建）
    --   idx_bc_batch 支撑正向追溯（批次→被哪些工单消耗）
    KEY idx_bc_batch (consumed_batch_id),
    CONSTRAINT fk_bc_order FOREIGN KEY (production_order_id) REFERENCES production_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_bc_batch FOREIGN KEY (consumed_batch_id)   REFERENCES batch (id)            ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_bc_quantity CHECK (quantity > 0)
) ENGINE = InnoDB COMMENT = '批次消耗表（追溯链的边：工单-批次 M:N）';

-- 4.4 工序执行记录表：由弱实体"工序执行记录"转换而来（标识实体=生产工单，
--     其存在依赖工单、以"工单+步骤序号"定位，见设计文档§2.2.3）——
--     检验单（IPQC）与缺陷记录（过程缺陷）均引用它，故在概念模型中实体化
--     工单创建时按工艺路线快照生成（step_no 复制固化，防路线日后变更破坏历史记录）
CREATE TABLE process_record (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工序记录ID',
    production_order_id BIGINT UNSIGNED NOT NULL                COMMENT '所属工单ID',
    process_def_id      BIGINT UNSIGNED NOT NULL                COMMENT '工序ID',
    step_no             INT UNSIGNED    NOT NULL                COMMENT '执行顺序（自工艺路线快照）',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待开工 IN_PROGRESS=进行中 COMPLETED=已完工',
    operator_id         BIGINT UNSIGNED NULL                    COMMENT '操作员ID（开工时记录）',
    start_time          DATETIME        NULL                    COMMENT '实际开工时间',
    end_time            DATETIME        NULL                    COMMENT '实际完工时间',
    remark              VARCHAR(500)    NULL                    COMMENT '备注',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（工单创建时按工艺路线快照生成）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pr_order_step (production_order_id, step_no),
    KEY idx_pr_process (process_def_id),
    CONSTRAINT fk_pr_order    FOREIGN KEY (production_order_id) REFERENCES production_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pr_process  FOREIGN KEY (process_def_id)      REFERENCES process_def (id)      ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_pr_operator FOREIGN KEY (operator_id)         REFERENCES sys_user (id)         ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_pr_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED'))
) ENGINE = InnoDB COMMENT = '工序执行记录表（弱实体：依附生产工单，按工艺路线快照生成）';

-- =====================================================================
-- 5. 质量检验域（检验单 / 检验明细 / 缺陷）
-- =====================================================================

-- 5.1 检验单表：IQC/IPQC/FQC 三类检验任务的统一载体
--     多态关联采用"双可空外键+CHECK互斥"方案（设计决策见文档§5.4）
CREATE TABLE inspection_task (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '检验单ID',
    task_no           VARCHAR(30)     NOT NULL                COMMENT '检验单号（QC-yyyyMMdd-3位序号）',
    inspect_type      VARCHAR(10)     NOT NULL                COMMENT '检验类型：IQC=来料 IPQC=过程 FQC=成品',
    batch_id          BIGINT UNSIGNED NULL                    COMMENT '受检批次ID（IQC/FQC 必填）',
    process_record_id BIGINT UNSIGNED NULL                    COMMENT '受检工序执行ID（IPQC 必填）',
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING=待领取 IN_PROGRESS=检验中 COMPLETED=已完成',
    conclusion        VARCHAR(20)     NULL                    COMMENT '检验结论（完成时必填）：QUALIFIED=合格 UNQUALIFIED=不合格 CONCESSION=让步接收',
    inspector_id      BIGINT UNSIGNED NULL                    COMMENT '检验员ID（领取任务时记录）',
    assigned_at       DATETIME        NULL                    COMMENT '领取时间',
    completed_at      DATETIME        NULL                    COMMENT '完成时间',
    remark            VARCHAR(500)    NULL                    COMMENT '备注',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（任务由业务动作自动生成）',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（任务状态多次流转，需审计）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    -- 任务池高频筛选路径：按状态+类型
    KEY idx_task_status_type (status, inspect_type),
    KEY idx_task_batch (batch_id),
    KEY idx_task_process_record (process_record_id),
    CONSTRAINT fk_task_batch     FOREIGN KEY (batch_id)          REFERENCES batch (id)          ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_task_pr        FOREIGN KEY (process_record_id) REFERENCES process_record (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_task_inspector FOREIGN KEY (inspector_id)      REFERENCES sys_user (id)       ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_task_type       CHECK (inspect_type IN ('IQC', 'IPQC', 'FQC')),
    CONSTRAINT chk_task_status     CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_task_conclusion CHECK (conclusion IS NULL OR conclusion IN ('QUALIFIED', 'UNQUALIFIED', 'CONCESSION')),
    -- 检验对象互斥：IQC/FQC 针对批次，IPQC 针对工序执行记录
    CONSTRAINT chk_task_target CHECK (
        (inspect_type IN ('IQC', 'FQC') AND batch_id IS NOT NULL AND process_record_id IS NULL)
        OR (inspect_type = 'IPQC' AND process_record_id IS NOT NULL AND batch_id IS NULL)
    ),
    -- 完成必有结论：防止"已完成却无结论"的单据静默漏出质量闭环（冻结触发器依赖结论）
    CONSTRAINT chk_task_complete CHECK (status <> 'COMPLETED' OR conclusion IS NOT NULL)
) ENGINE = InnoDB COMMENT = '检验单表（IQC/IPQC/FQC统一）';

-- 5.2 检验记录明细表：由 M:N 联系"检测"转换而来（检验单 × 检验项目 → 实测结果）
CREATE TABLE inspection_record (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '检验记录ID',
    inspection_task_id BIGINT UNSIGNED NOT NULL                COMMENT '所属检验单ID',
    inspection_item_id BIGINT UNSIGNED NOT NULL                COMMENT '检验项目ID',
    measured_value     DECIMAL(12, 3)  NULL                    COMMENT '实测值（定量项填写，系统按项目上下限自动判定）',
    result_desc        VARCHAR(200)    NULL                    COMMENT '结果描述（定性项的观察记录）',
    is_pass            TINYINT         NOT NULL                COMMENT '单项判定：1=合格 0=不合格（定量项为判定时点快照，见设计文档§3.3）',
    inspected_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检验时间',
    PRIMARY KEY (id),
    -- 一张检验单对一个项目只允许一条记录
    UNIQUE KEY uk_ir_task_item (inspection_task_id, inspection_item_id),
    CONSTRAINT fk_ir_task FOREIGN KEY (inspection_task_id) REFERENCES inspection_task (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ir_item FOREIGN KEY (inspection_item_id) REFERENCES inspection_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_ir_pass CHECK (is_pass IN (0, 1))
) ENGINE = InnoDB COMMENT = '检验记录明细表（检验单-检验项目 M:N）';

-- 5.3 缺陷记录表：检验发现或人工登记的质量缺陷，帕累托分析的数据源
--     缺陷载体采用"双可空外键"方案（与检验单同款，DRY）：
--     IQC/FQC/售后缺陷挂批次；IPQC 过程缺陷发生时成品批次尚未生成，挂工序执行记录
CREATE TABLE defect_record (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '缺陷记录ID',
    defect_no          VARCHAR(30)     NOT NULL                COMMENT '缺陷编号（DF-yyyyMMdd-3位序号）',
    batch_id           BIGINT UNSIGNED NULL                    COMMENT '所属批次ID（IQC/FQC/售后缺陷填写；与工序记录至少其一非空）',
    process_record_id  BIGINT UNSIGNED NULL                    COMMENT '所属工序执行ID（IPQC过程缺陷填写：此时成品批次尚未生成）',
    inspection_task_id BIGINT UNSIGNED NULL                    COMMENT '发现于检验单ID（人工巡检/售后登记时为NULL）。注：任务非空时批次可由任务推导，batch_id为受控冗余（见设计文档§3.3），一致性由应用层校验',
    defect_type        VARCHAR(20)     NOT NULL                COMMENT '缺陷类型：APPEARANCE=外观 DIMENSION=尺寸 FUNCTION=功能 SAFETY=安全 OTHER=其他',
    severity           VARCHAR(10)     NOT NULL                COMMENT '严重度：CRITICAL=致命 MAJOR=严重 MINOR=轻微',
    quantity           DECIMAL(12, 3)  NOT NULL                COMMENT '缺陷数量',
    description        VARCHAR(500)    NOT NULL                COMMENT '缺陷描述',
    handle_method      VARCHAR(20)     NULL                    COMMENT '处置方式（质量主管评审后填写）：REWORK=返工 SCRAP=报废 CONCESSION=让步 RETURN=退货',
    handle_status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '处置状态：PENDING=待处置 COMPLETED=已处置',
    handler_id         BIGINT UNSIGNED NULL                    COMMENT '处置人ID（质量主管）',
    handled_at         DATETIME        NULL                    COMMENT '处置时间',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（处置状态可变，需审计）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_defect_no (defect_no),
    KEY idx_defect_batch (batch_id),
    KEY idx_defect_type (defect_type),
    CONSTRAINT fk_defect_batch   FOREIGN KEY (batch_id)           REFERENCES batch (id)           ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_defect_pr      FOREIGN KEY (process_record_id)  REFERENCES process_record (id)  ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_defect_task    FOREIGN KEY (inspection_task_id) REFERENCES inspection_task (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_defect_handler FOREIGN KEY (handler_id)         REFERENCES sys_user (id)        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_defect_type     CHECK (defect_type IN ('APPEARANCE', 'DIMENSION', 'FUNCTION', 'SAFETY', 'OTHER')),
    CONSTRAINT chk_defect_severity CHECK (severity IN ('CRITICAL', 'MAJOR', 'MINOR')),
    CONSTRAINT chk_defect_handle   CHECK (handle_method IS NULL OR handle_method IN ('REWORK', 'SCRAP', 'CONCESSION', 'RETURN')),
    CONSTRAINT chk_defect_hstatus  CHECK (handle_status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT chk_defect_quantity CHECK (quantity > 0),
    -- 缺陷载体：批次与工序执行记录至少其一非空（IPQC缺陷仅有工序，其余必有批次）
    CONSTRAINT chk_defect_target   CHECK (batch_id IS NOT NULL OR process_record_id IS NOT NULL)
) ENGINE = InnoDB COMMENT = '缺陷记录表';

-- =====================================================================
-- 6. 出货与召回域
-- =====================================================================

-- 6.1 出货记录表：由 M:N 联系"出货"转换而来（批次 × 客户），追溯链的末端
CREATE TABLE shipment (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '出货记录ID',
    shipment_no VARCHAR(30)     NOT NULL                COMMENT '出货单号（SH-yyyyMMdd-3位序号）',
    batch_id    BIGINT UNSIGNED NOT NULL                COMMENT '出货批次ID（须为 QUALIFIED 状态的成品批次，应用层校验）',
    customer_id BIGINT UNSIGNED NOT NULL                COMMENT '客户ID',
    quantity    DECIMAL(12, 3)  NOT NULL                COMMENT '出货数量',
    ship_date   DATE            NOT NULL                COMMENT '出货日期',
    operator_id BIGINT UNSIGNED NOT NULL                COMMENT '经办人ID（仓库人员）',
    remark      VARCHAR(500)    NULL                    COMMENT '备注',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_no (shipment_no),
    KEY idx_shipment_batch (batch_id),
    KEY idx_shipment_customer (customer_id),
    CONSTRAINT fk_shipment_batch    FOREIGN KEY (batch_id)    REFERENCES batch (id)    ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_shipment_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_shipment_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_shipment_quantity CHECK (quantity > 0)
) ENGINE = InnoDB COMMENT = '出货记录表（批次-客户 M:N）';

-- 6.2 召回单表：质量事件的处置载体，源头批次经正向追溯圈定影响面
CREATE TABLE recall_order (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '召回单ID',
    recall_no       VARCHAR(30)     NOT NULL                COMMENT '召回单号（RC-yyyyMMdd-3位序号）',
    source_batch_id BIGINT UNSIGNED NOT NULL                COMMENT '问题源头批次ID（通常为原材料批次）',
    recall_level    VARCHAR(10)     NOT NULL                COMMENT '召回级别：I=一级(严重健康危害) II=二级(暂时性危害) III=三级(一般不会危害)',
    reason          VARCHAR(500)    NOT NULL                COMMENT '召回原因',
    status          VARCHAR(20)     NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED=已创建 IN_PROGRESS=执行中 COMPLETED=已完成',
    initiator_id    BIGINT UNSIGNED NOT NULL                COMMENT '发起人ID（质量主管）',
    completed_at    DATETIME        NULL                    COMMENT '完成时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（召回状态可变，需审计）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recall_no (recall_no),
    KEY idx_recall_source (source_batch_id),
    CONSTRAINT fk_recall_batch     FOREIGN KEY (source_batch_id) REFERENCES batch (id)    ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_recall_initiator FOREIGN KEY (initiator_id)    REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_recall_level  CHECK (recall_level IN ('I', 'II', 'III')),
    CONSTRAINT chk_recall_status CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED'))
) ENGINE = InnoDB COMMENT = '召回单表';

-- 6.3 召回明细表：由弱实体"召回明细"转换而来（标识实体=召回单，
--     并入"包含/针对"联系及出货流向引用，见设计文档§2.2.5/§3.1规则5）
--     发起召回时由正向追溯结果自动生成；已出货部分逐客户跟踪回收，
--     在库部分 shipment_id 为 NULL、就地冻结
CREATE TABLE recall_detail (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '召回明细ID',
    recall_order_id   BIGINT UNSIGNED NOT NULL                COMMENT '所属召回单ID',
    affected_batch_id BIGINT UNSIGNED NOT NULL                COMMENT '受影响批次ID（正向追溯计算所得）。注：shipment_id非空时批次可由出货记录推导，此列为受控冗余（见设计文档§3.3），一致性由应用层校验',
    shipment_id       BIGINT UNSIGNED NULL                    COMMENT '关联出货记录ID（已出货部分填写，据此定位客户；在库部分为NULL）',
    recovery_status   VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '回收状态：PENDING=待通知 NOTIFIED=已通知 RECOVERED=已回收 UNRECOVERABLE=无法回收',
    remark            VARCHAR(500)    NULL                    COMMENT '备注（如客户反馈）',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    -- 防重复明细：shipment_id 以 COALESCE 函数键参与唯一约束——
    -- InnoDB 唯一索引不判 NULL 相等，直接用可空列会导致"在库明细"（NULL）可无限重复；
    -- 函数键把 NULL 归一为 0 后，(召回, 批次, 在库) 组合同样只允许一行（MySQL 8.0.13+ 函数索引）
    UNIQUE KEY uk_rd_recall_batch_ship (recall_order_id, affected_batch_id, (COALESCE(shipment_id, 0))),
    KEY idx_rd_recall (recall_order_id),
    KEY idx_rd_batch (affected_batch_id),
    CONSTRAINT fk_rd_recall   FOREIGN KEY (recall_order_id)   REFERENCES recall_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rd_batch    FOREIGN KEY (affected_batch_id) REFERENCES batch (id)        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_rd_shipment FOREIGN KEY (shipment_id)       REFERENCES shipment (id)     ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_rd_status CHECK (recovery_status IN ('PENDING', 'NOTIFIED', 'RECOVERED', 'UNRECOVERABLE'))
) ENGINE = InnoDB COMMENT = '召回明细表（弱实体：依附召回单，关联批次与出货流向）';

-- =====================================================================
-- 7. 技术支撑域（单号流水）
-- =====================================================================

-- 7.1 单号流水表：按"业务前缀 + 日期"维护当前已分配序号
--     由应用层执行 INSERT ... ON DUPLICATE KEY UPDATE + LAST_INSERT_ID 原子分配。
--     该表不参与业务 ER 主体，只承载并发下的单号串行化；各业务表的 UNIQUE 单号
--     约束仍保留，作为最后一道数据库正确性兜底。
CREATE TABLE serial_number (
    serial_key    VARCHAR(30)  NOT NULL                COMMENT '单号前缀（如 MO-20260612 / RM-20260601）',
    current_value INT UNSIGNED NOT NULL DEFAULT 0      COMMENT '当前已分配序号',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (serial_key)
) ENGINE = InnoDB COMMENT = '业务单号流水表（并发原子分配）';

-- =====================================================================
-- 8. 视图：封装高频联接与统一统计口径（DRY）
-- =====================================================================

-- 8.1 批次全景视图：批次台账页的直接数据源，一次联接物料/供应商/工单/登记人
CREATE OR REPLACE VIEW v_batch_overview AS
SELECT b.id,
       b.batch_no,
       b.material_id,
       m.material_code,
       m.name              AS material_name,
       m.category          AS material_category,
       m.unit,
       b.source_type,
       s.name              AS supplier_name,   -- 采购批次的供应商（生产批次为 NULL）
       po.order_no         AS production_order_no, -- 生产批次的工单号（采购批次为 NULL）
       b.quantity,
       b.remaining_quantity,
       b.status,
       b.production_date,
       b.expire_date,
       b.warehouse_location,
       u.real_name         AS created_by_name,
       b.created_at
FROM batch b
         JOIN material m          ON m.id = b.material_id
         LEFT JOIN supplier s     ON s.id = b.supplier_id
         LEFT JOIN production_order po ON po.id = b.production_order_id
         JOIN sys_user u          ON u.id = b.created_by;

-- 8.2 供应商质量统计视图：供应商排名报表的数据源
--     口径：仅统计采购来源批次的 IQC 检验单；合格率 =（合格+让步）/ 已完成检验单数；
--     另统计"引发召回数"——IQC 合格率无法反映抽检漏过的隐性缺陷（如演示数据中
--     华南电子 IQC 100% 却引发 I 级召回），召回归因是供应商质量的更硬指标
CREATE OR REPLACE VIEW v_supplier_quality AS
SELECT s.id                                                                   AS supplier_id,
       s.supplier_code,
       s.name                                                                 AS supplier_name,
       COUNT(DISTINCT b.id)                                                   AS batch_total,      -- 供货批次总数
       COUNT(DISTINCT t.id)                                                   AS iqc_total,        -- 已完成 IQC 检验单数
       COUNT(DISTINCT CASE WHEN t.conclusion IN ('QUALIFIED', 'CONCESSION') THEN t.id END) AS iqc_pass, -- 合格（含让步）单数
       ROUND(COUNT(DISTINCT CASE WHEN t.conclusion IN ('QUALIFIED', 'CONCESSION') THEN t.id END)
                 / NULLIF(COUNT(DISTINCT t.id), 0) * 100, 2)                  AS pass_rate,        -- 合格率%（无检验记录时为 NULL）
       COUNT(DISTINCT d.id)                                                   AS defect_count,     -- 关联缺陷记录数
       COUNT(DISTINCT r.id)                                                   AS recall_count      -- 作为源头批次供应商引发的召回单数
FROM supplier s
         LEFT JOIN batch b  ON b.supplier_id = s.id AND b.source_type = 'PURCHASE'
         LEFT JOIN inspection_task t ON t.batch_id = b.id AND t.inspect_type = 'IQC' AND t.status = 'COMPLETED'
         LEFT JOIN defect_record d   ON d.batch_id = b.id
         LEFT JOIN recall_order r    ON r.source_batch_id = b.id
GROUP BY s.id, s.supplier_code, s.name;

-- 8.3 缺陷帕累托视图：按缺陷类型统计"缺陷记录条数"及窗口函数累计占比
--     口径要点：帕累托以【记录条数（频次）】为统计口径，而非缺陷品数量——
--     不同物料的数量单位不可通约（台+个+kg 混加无量纲意义）；
--     defect_quantity 列仅供同单位物料的钻取参考，跨类型不可比（注释申报）。
--     累计占比用窗口函数实现（SUM(...) OVER (ORDER BY ...) 累计和 /
--     SUM(...) OVER () 全量和），是 MySQL 8 窗口函数的经典应用场景
CREATE OR REPLACE VIEW v_defect_pareto AS
SELECT d.defect_type,
       COUNT(*)                                          AS record_count,     -- 缺陷记录条数（帕累托主指标）
       SUM(d.quantity)                                   AS defect_quantity,  -- 缺陷品数量合计（仅同单位参考）
       ROUND(COUNT(*) / SUM(COUNT(*)) OVER () * 100, 2)  AS record_ratio,     -- 条数占比%
       ROUND(SUM(COUNT(*)) OVER (ORDER BY COUNT(*) DESC, d.defect_type)
                 / SUM(COUNT(*)) OVER () * 100, 2)       AS cumulative_ratio  -- 累计占比%（帕累托曲线，窗口函数）
FROM defect_record d
GROUP BY d.defect_type
ORDER BY record_count DESC;

-- =====================================================================
-- 9. 触发器：数据库层质量安全网
-- =====================================================================
-- 职责边界：主流程状态流转由应用 Service 层负责（可测试、可回滚）；
-- 触发器兜底"检验不合格必须冻结批次"这一质量安全规则——
-- 即使有人绕过应用层直接写检验结论，冻结依然自动发生。
-- 设计要点：
--   1. UPDATE 与 INSERT 双触发器，堵死两条旁路（仅 UPDATE 会漏掉
--      "直接插入终态不合格单"的路径）；
--   2. 冻结带状态白名单：只有处于检验主干（待检/检验中/合格在库）的
--      批次才会被冻结，不覆写 SCRAPPED/RETURNED/DEPLETED/RECALLED 等
--      更强终态——否则已召回批次会被降级为 FROZEN，经"让步放行"出边
--      重新变回可用库存，安全网自身反而制造漏洞；
--   3. 两层写入同一目标状态，幂等不冲突。

DELIMITER $$

-- 9.1 UPDATE 路径：检验结论被改为不合格时冻结批次
CREATE TRIGGER trg_freeze_batch_on_unqualified
    AFTER UPDATE
    ON inspection_task
    FOR EACH ROW
BEGIN
    -- 条件1：结论"变为"不合格（避免重复更新触发多余写入）
    -- 条件2：检验对象是批次（IPQC 针对工序执行记录，batch_id 为 NULL，不在此触发）
    IF NEW.conclusion = 'UNQUALIFIED'
        AND (OLD.conclusion IS NULL OR OLD.conclusion <> 'UNQUALIFIED')
        AND NEW.batch_id IS NOT NULL THEN
        UPDATE batch
        SET status = 'FROZEN'
        WHERE id = NEW.batch_id
          AND status IN ('PENDING_INSPECT', 'INSPECTING', 'QUALIFIED');  -- 状态白名单，不降级终态
    END IF;
END$$

-- 9.2 INSERT 路径：直接插入终态不合格单同样触发冻结（堵住 INSERT 旁路）
CREATE TRIGGER trg_freeze_batch_on_unqualified_ins
    AFTER INSERT
    ON inspection_task
    FOR EACH ROW
BEGIN
    IF NEW.conclusion = 'UNQUALIFIED'
        AND NEW.batch_id IS NOT NULL THEN
        UPDATE batch
        SET status = 'FROZEN'
        WHERE id = NEW.batch_id
          AND status IN ('PENDING_INSPECT', 'INSPECTING', 'QUALIFIED');  -- 同款白名单
    END IF;
END$$

DELIMITER ;

-- =====================================================================
-- 10. 存储过程：双向追溯的数据库层封装（★递归 CTE，系统灵魂）
-- =====================================================================
-- 追溯链的"边"由两跳构成：
--   产出跳：batch.production_order_id → production_order（成品批次由哪张工单产出）
--   消耗跳：production_order → batch_consumption → batch（该工单消耗了哪些批次）
-- 递归组合两跳即可实现任意层级的上/下游遍历。
-- path 列记录访问路径：既供前端还原追溯树，也是防环保险；
-- level < 10 为防御性深度上限（业务上 BOM 为两级嵌套，远低于上限）。
-- ★口径申报：追溯结果按【路径】枚举而非【节点集合】——若 DAG 存在汇聚
--   （同一上游批次经多条路径到达，如两个半成品批共用同一原料批），该批次
--   会按路径多次出现，每行的 path 不同。这在追溯语义上是正确的：每条路径
--   都是真实的物料流向；需要"去重节点清单"口径时由调用方按 batch_id 聚合。
--   路径枚举的行数受 BOM 层级（两级嵌套）与 level<10 上限约束，规模可控。

DELIMITER $$

-- 10.1 反向追溯：给定批次，逐级向上游展开"它由什么做成"
--     典型场景：成品客诉 → 定位使用的原料批次与供应商
CREATE PROCEDURE sp_trace_upstream(IN p_batch_id BIGINT UNSIGNED)
BEGIN
    WITH RECURSIVE upstream AS (
        -- 锚点：起点批次自身（层级0）
        SELECT b.id,
               b.batch_no,
               b.material_id,
               b.production_order_id,
               CAST(NULL AS DECIMAL(12, 3))   AS consumed_quantity, -- 起点无"被消耗"语义
               0                              AS level,
               CAST(b.id AS CHAR(500))        AS path
        FROM batch b
        WHERE b.id = p_batch_id
        UNION ALL
        -- 递归：当前层批次若产自工单，则该工单投料的全部批次为上一层原料
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
    SELECT u.id                AS batch_id,
           u.batch_no,
           m.material_code,
           m.name              AS material_name,
           m.category          AS material_category,
           s.name              AS supplier_name, -- 上游尽头（采购批次）显示供应商
           po.order_no         AS production_order_no,
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
           )                   AS process_summary,
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
           )                   AS inspection_summary,
           u.consumed_quantity,
           u.level,
           u.path
    FROM upstream u
             JOIN material m      ON m.id = u.material_id
             LEFT JOIN batch b    ON b.id = u.id
             LEFT JOIN supplier s ON s.id = b.supplier_id
             LEFT JOIN production_order po ON po.id = u.production_order_id
    ORDER BY u.level, u.batch_no;
END$$

-- 10.2 正向追溯：给定批次，逐级向下游展开"它被做成了什么"
--     典型场景：原料缺陷 → 圈定受影响成品批次 → 联接出货记录锁定客户（召回影响面）
CREATE PROCEDURE sp_trace_downstream(IN p_batch_id BIGINT UNSIGNED)
BEGIN
    WITH RECURSIVE downstream AS (
        -- 锚点：起点批次自身（层级0）
        SELECT b.id,
               b.batch_no,
               b.material_id,
               0                       AS level,
               CAST(b.id AS CHAR(500)) AS path
        FROM batch b
        WHERE b.id = p_batch_id
        UNION ALL
        -- 递归：消耗了当前层批次的工单，其产出批次为下一层
        -- （两跳合并：batch_consumption 定位工单，再经 batch.production_order_id 找产出）
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
    SELECT d.id           AS batch_id,
           d.batch_no,
           m.material_code,
           m.name         AS material_name,
           m.category     AS material_category,
           b.status       AS batch_status,
           b.remaining_quantity,
           d.level,
           d.path,
           sh.shipment_no,                 -- 下游尽头（已出货部分）显示流向
           c.name         AS customer_name,
           sh.quantity    AS shipped_quantity,
           sh.ship_date
    FROM downstream d
             JOIN material m       ON m.id = d.material_id
             JOIN batch b          ON b.id = d.id
             LEFT JOIN shipment sh ON sh.batch_id = d.id  -- 一批可多次出货，结果按出货记录展开
             LEFT JOIN customer c  ON c.id = sh.customer_id
    ORDER BY d.level, d.batch_no, sh.ship_date;
END$$

DELIMITER ;

-- =====================================================================
-- 11.（可选）最小权限应用账号：应用连接不使用 root
-- =====================================================================
-- 安全原则：应用账号仅授予业务所需的最小权限（DML + 执行存储过程），
-- 禁止 DDL/DROP —— 即使应用被注入攻击，也无法删表删库。
-- 如无需演示可注释掉本段。
CREATE USER IF NOT EXISTS 'quality_app'@'%' IDENTIFIED BY 'QualityApp@2026';
GRANT SELECT, INSERT, UPDATE ON quality_trace.* TO 'quality_app'@'%';
GRANT EXECUTE ON quality_trace.* TO 'quality_app'@'%';
FLUSH PRIVILEGES;

-- ================================ 脚本结束 ================================
