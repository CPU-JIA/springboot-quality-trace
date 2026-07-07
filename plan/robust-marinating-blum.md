# 实现计划：产品质量追踪系统（后端 + 前端 + 联调）

> 本计划经 Plan agent 对抗审计（1 HIGH + 8 MEDIUM + 5 LOW findings 已全部融入）。

## Context

数据库课设"基于 SpringBoot 的产品质量追踪系统"设计阶段已定稿（v1.2，三轮质量关卡）：`docs/01-需求分析.md`、`docs/02-数据库设计.md`、`sql/01-schema.sql`（20 表/3 视图/2 触发器/2 存储过程）、`sql/02-init-data.sql`（召回故事线演示数据）。MySQL 8.4.10 已装于 E 盘（服务 MySQL84，root/123456），`quality_trace` 库已通过全部实证。本计划实现系统本体：SpringBoot 后端 + Vue3 前端 + E2E 联调。**全部代码按课设要求写极详尽中文注释。**

## 技术栈（已调研锁定，禁止漂移）

后端：JDK 21（Temurin 21.0.11）· Spring Boot **3.5.16** · MyBatis-Plus **3.5.16**（`mybatis-plus-spring-boot3-starter` + `mybatis-plus-jsqlparser` **同版本 3.5.16 严格一致**）· Druid **1.2.28**（`druid-spring-boot-3-starter`）· mysql-connector-j 9.7.0（Boot 托管）· jjwt **0.13.0**（api/impl/jackson 三件套）· spring-security-crypto 6.5.11（仅 BCrypt）· springdoc-openapi-starter-webmvc-ui **2.8.17**（显式写版本）· hutool-all 5.8.46 · Lombok 1.18.46 · Maven Wrapper 3.9.16。

前端：Node 24.18.0 · Vue **3.5.39** · Vite **7.3.6** · vue-router 4.6.4 · pinia 3.0.4 · Element Plus 2.14.2 · ECharts 6.1.0 · axios 1.18.1（package.json 去 `^`，提交 lockfile）。

## 工程结构

```
2026/
├── backend/                                    # 单模块 Maven（课设标准形态）
│   ├── pom.xml  mvnw  .mvn/  README.md
│   └── src/main/
│       ├── java/com/xinghui/qualitytrace/
│       │   ├── QualityTraceApplication.java
│       │   ├── common/
│       │   │   ├── result/    Result、PageResult                  # 统一响应 {code,message,data}
│       │   │   ├── exception/ BusinessException、GlobalExceptionHandler
│       │   │   └── enums/     BatchStatus、OrderStatus、ProcessStatus、InspectType、
│       │   │                  Conclusion、DefectType、Severity、HandleMethod、RecallLevel、
│       │   │                  RecallStatus、RecoveryStatus、MaterialCategory、SourceType
│       │   │                  # 状态机枚举内置 canTransitionTo()，与 02文档 §5.6 一致
│       │   ├── config/    MybatisPlusConfig、WebConfig、OpenApiConfig、JacksonConfig
│       │   ├── security/  JwtUtil、JwtInterceptor、RequireRole(注解+拦截)、UserContext(ThreadLocal)
│       │   ├── entity/    20 个实体类（字段注释与 DDL COMMENT 逐一对应）
│       │   ├── mapper/    20 个 Mapper 接口；TraceMapper/StatsMapper 走 XML 手写 SQL
│       │   ├── dto/       按模块分包（req/resp 分离，@Validated 服务端全量校验）
│       │   ├── service/ + impl/
│       │   └── controller/
│       └── resources/
│           ├── application.yml
│           └── mapper/*.xml                    # 递归 CTE 追溯、统计 SQL（逐段注释）
└── frontend/
    └── Vite 工程：src/{api, stores, router, layouts, views, components, utils}
```

## A1 骨架的关键配置清单（审计后补强）

- **MyBatis-Plus**：全局 `id-type: auto`（默认 ASSIGN_ID 雪花会绕过表 AUTO_INCREMENT）；分页插件 `PaginationInnerInterceptor` 注册 + **A1 验收含一条分页断言**（未注册的症状是静默返回全量且 total=0）
- **Jackson**：`Long → String` 序列化（ToStringSerializer，防 JS 2^53 精度丢失）；`LocalDateTime` 统一 `yyyy-MM-dd HH:mm:ss`
- **JWT**：HS256 **密钥 ≥ 32 字节**（否则 jjwt 抛 WeakKeyException）；拦截器只挂 `/api/**`，白名单 `/api/auth/login`；显式放行 `/v3/api-docs/**`、`/swagger-ui/**`
- **Druid**：**只开 stat filter，绝不开 wall**（wall 对递归 CTE 的解析失败会直接拒绝执行 SQL，stat 失败仅影响监控；Druid 历史版本对 WITH RECURSIVE 有解析 issue）；WebStatFilter exclusions 加 swagger 路径；**A1 冒烟必须含一条 WITH RECURSIVE 穿过 Druid 的查询**
- **前后端契约**（A1 定死，防 B 阶段漂移）：分页参数 `current/size`、响应 `Result{code,message,data}`、枚举值全大写蛇形（前端维护中文映射与 tag 配色表）

## 后端接口清单（12 组 · 约 43 个端点，均挂 /api 前缀）

| 模块                                                             | 端点要点                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | 权限                        |
| ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------- |
| auth                                                             | POST login（BCrypt→JWT）、GET profile；拦截器每次请求校验账号仍启用并加载数据库最新角色。登出=客户端令牌废弃（无服务端黑名单，报告申报口径）                                                                                                                                                                                                                                                                                                                                                                                     | 匿名/所有                   |
| users                                                            | CRUD + 启停 + 重置密码 + 角色分配（roles 仅 GET）                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | ADMIN                       |
| materials / suppliers / customers / processes / inspection-items | 标准 CRUD + 分页筛选；物料停用校验无在库批次                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | ADMIN                       |
| boms                                                             | GET 构成树（递归展开）、POST/DELETE 行（父项=启用半成品/成品、子项=启用原材料/半成品、重复父子项友好提示、应用层 DFS 防环）                                                                                                                                                                                                                                                                                                                                                                                                       | ADMIN                       |
| process-routes                                                   | GET 按物料查、PUT 整条替换（启用半成品/成品才可维护；校验 step_no 连续；同一路线工序不得重复；**校验末道工序 need_ipqc=0**——末道设 IPQC 点会使完工入库先于结论，系统级禁止并写入报告）                                                                                                                                                                                                                                                                             | ADMIN                       |
| batches                                                          | POST 入库（★事务：校验原材料已配置 IQC 标准 + 单号生成 + 建批次 + 自动 IQC 单）、GET 台账（v_batch_overview 分页多条件）、GET 详情（检验历史/缺陷/消耗与出货去向聚合）                                                                                                                                                                                                                                                                                                                                                             | WAREHOUSE                   |
| production-orders                                                | POST 创建（★事务：校验生产物料启用、BOM+路线存在、BOM 直接子项启用、FQC 标准存在、路线内 IPQC 工序标准存在 → 按路线快照 process_record）、POST 领料（★核心事务，校验批次物料 ∈ 工单 BOM 子项）、POST 工序开工（**★HIGH 门禁：前道 COMPLETED 且若前道 need_ipqc=1 则其最新 IPQC 结论 ∈ {QUALIFIED, CONCESSION}**）、POST 工序完工（复查 IPQC 标准后生成 IPQC 单）、POST 完工入库（★事务：校验全部工序 COMPLETED **且末道无未决 IPQC**、复查 FQC 标准 → 请求体带 actualQuantity（默认=计划量，>0，超计划量需申报口径）→ 工单 COMPLETED + 生成成品批次 `quantity=remaining=actualQuantity` + 自动 FQC 单）、GET 列表/详情 | PRODUCTION                  |
| inspection-tasks                                                 | GET 任务池、POST 领取、POST 提交（★事务，三重完整性校验：① 该对象适用的**全部标准项均有明细**——IQC/FQC 按 material_id+inspect_type 取项，**IPQC 额外按工序执行记录的 process_def_id 过滤**；② 标准项为空集时拒绝提交（配置缺失显式报错，防校验真空通过）；③ 结论=QUALIFIED 时禁止存在 is_pass=0 明细，结论=UNQUALIFIED/CONCESSION 时至少一项明细不通过。结论联动：QUALIFIED/CONCESSION→批次 QUALIFIED；UNQUALIFIED→IQC/FQC 冻结批次、IPQC 工序退回 IN_PROGRESS；CONCESSION 强制同步登记 MINOR 缺陷，UNQUALIFIED 强制同步登记待处置缺陷） | INSPECTOR                   |
| defects                                                          | POST 登记（双载体互斥、数量不超过载体；待检/检验中批次必须走检验任务；合格在库批次登记后同事务冻结隔离；手工过程缺陷仅允许登记在生产中工单的进行中工序，已产出批次后改走批次缺陷）、GET 列表、POST 处置（★事务：REWORK→批次回 PENDING_INSPECT + 自动生成同类型重检单；SCRAP/RETURN/CONCESSION→状态流转；过程缺陷仅允许返工/让步关闭）                                                                                                                                                                                                                  | INSPECTOR / QUALITY_MANAGER |
| shipments                                                        | POST 出货（★事务：与领料同构的原子扣减——`UPDATE batch SET status=CASE WHEN remaining_quantity=? THEN 'DEPLETED' ELSE status END, remaining_quantity=remaining_quantity-? WHERE id=? AND remaining_quantity>=? AND status='QUALIFIED'` + 物料 category='PRODUCT' 校验进事务，堵"预检后被并发置 RECALLED"的 TOCTOU 窗口；减至 0 置 DEPLETED）、GET 列表                                                                                                                                                                           | WAREHOUSE                   |
| trace                                                            | GET upstream/{batchId}、downstream/{batchId}、by-no/{batchNo}：Mapper 递归 CTE，path 口径枚举真实追溯路径并阻断环路                                                                                                                                                                                                                                                                                                                                                                                                              | 登录即可                    |
| recalls                                                          | POST 发起（★事务：正向追溯→按 batch_id 去重聚合→生成明细（出货逐笔挂 shipment、在库 NULL）→受影响批次+源头批次置 RECALLED；**发起即 IN_PROGRESS**（CREATED 仅为字典完备态，报告申报口径））、PUT 明细回收状态（已出货明细必须 PENDING→NOTIFIED→终态，在库隔离明细允许 PENDING→终态，无法回收必须备注，终态不可回退）、POST 完成（校验明细全部终态）、GET 列表/详情                                                                                                                                                                                        | QUALITY_MANAGER             |
| stats                                                            | GET dashboard、pass-rate-trend、defect-pareto（视图）、supplier-quality（视图）                                                                                                                                                                                                                                                                                                                                                                                                                                                  | 登录即可                    |

## 核心实现要点（防踩坑清单，审计后补强）

1. **领料事务**：原子扣减 SQL（余量+status 双条件，影响行数 0 即回滚）+ 消耗行 `INSERT ... ON DUPLICATE KEY UPDATE quantity = quantity + ?` + 减至 0 置 DEPLETED + **批次物料必须 ∈ 工单物料的 BOM 子项**（防误领弄脏追溯链）。耗尽判断使用扣减前 `remaining_quantity = quantity`，避免 MySQL `UPDATE` 左到右赋值导致先扣余量后状态判断失效。
2. **单号生成**：已改为 `serial_number(serial_key, current_value)` 单号流水表集中分配。事务内执行 `INSERT ... ON DUPLICATE KEY UPDATE current_value = LAST_INSERT_ID(current_value + 1)`，再读取同一连接 `LAST_INSERT_ID()` 组成单号；当日前缀首单和后续递增都落在同一主键行/唯一键路径上串行化，业务表 `UNIQUE` 单号约束保留为最终兜底。答辩口径："单号池行锁串行化 + 业务唯一约束兜底"，不再讲按业务表最大号递增。
3. **IPQC 门禁（HIGH 修复）**：工序开工与完工入库两处校验，见接口清单；工艺路线维护禁止末道设 IPQC 点，且同一路线内工序不得重复。
4. **检验标准前置校验**：自动生成 IQC/IPQC/FQC 任务前必须确认标准项存在；入库、工单创建、工序报完工、完工入库均有配置缺失拦截，避免产生无法提交的空检验任务。
5. **状态机**：枚举 `canTransitionTo()` 统一校验，与数据库 CHECK/触发器构成三层防线。
6. **主数据状态完整性**：物料/供应商/客户新增时服务端默认启用；前端新增弹窗不展示状态开关且不提交 `status`，避免误导用户；编辑时必须显式提交 `status`（0/1），漏传直接返回中文业务错误，不把 `NOT NULL` 数据库异常暴露给用户；前端 API helper 仅为真实存在的用户/物料状态接口暴露 `changeStatus`。
7. **BOM 在制工单漂移保护**：工单领料/完工按当前 BOM 计算；父项物料存在未关闭工单时，BOM 新增/删除均拒绝，并通过父项物料行锁串行化工单创建与 BOM 调整。
8. **检验标准在途任务漂移保护**：检验任务提交按当前标准拉取应检项目与判限；同一物料/检验类型/IPQC工序存在待检或检验中任务时，标准新增/编辑/删除均拒绝，并通过物料行锁串行化任务生成与标准维护。
9. **检验任务领取人约束**：检验任务提交必须由当前领取人完成，后端按 `inspector_id = UserContext.currentUserId()` 拦截非领取人提交；前端执行入口同样按领取人过滤，避免他人误提交检验结论。
10. **手工过程缺陷生命周期保护**：IPQC 不合格由检验提交事务自动登记过程缺陷；人工入口只允许挂到生产中工单的进行中工序。未开工工序、已完工工序、已产出批次/已关闭工单均拒绝，避免把历史生产链重新打回进行中。
11. **缺陷处置可操作性**：缺陷列表接口返回批次状态与来源，前端处置弹窗按状态机过滤有效处置方式：冻结批次可返工/报废/让步，采购来源额外可退货；合格批次仅可让步留痕；召回批次仅可报废或采购退货；历史耗尽等线索保留但不展示无效处置入口。
12. **追溯树组装**：递归 CTE 返回路径枚举行，Service 按 path 构建 `TraceNode{batchInfo, children[]}`；召回影响面用同一 Mapper 按 batch_id 去重（两种口径对应 02 文档 §6.1 申报）。
13. **召回明细状态与并发控制**：已出货明细必须先通知客户，再进入已回收/无法回收终态；在库隔离明细因无客户通知动作，可从待通知直接进入终态。明细更新与召回关闭都按"召回单 → 召回明细"顺序加 `FOR UPDATE` 行锁；关闭前锁定全部明细复核终态，关闭后禁止再改明细，防止并发状态漂移。
14. **审计字段**：MyBatis-Plus `MetaObjectHandler` 自动填充 `created_by`（取 `UserContext`），采购入库与生产完工生成批次时由 `verify-e2e-flow.ps1` 断言登记人等于当前登录用户。
15. **统一异常**：BusinessException / 参数校验异常 / 兜底 500，全部落 Result；前端统一 toast。
16. **主布局响应式**：侧边导航以同一个 `effectiveCollapsed` 状态驱动品牌、Element 菜单与壳宽；窄屏固定为图标导航并保留 `aria-label/title`，避免文字被硬裁剪或折叠状态不一致。
17. **列表搜索真实性**：前端筛选框提示的业务字段必须由后端真实支持。出货列表关键字已通过 JOIN 覆盖出货单号、备注、批次号、客户编码/名称；召回列表关键字已通过 JOIN 覆盖召回单号、原因、源头批次号，并由 `verify-readonly.ps1` 断言。
18. **前端构建优化**：Element Plus 改为按需组件入口注册，保留中文 locale；Vite 手工分包按真实依赖边界拆为 `element`、`element-vendor`、`echarts`、`zrender`、`vue`、`vendor`，避免过细拆分造成 circular chunk；VueUse 上游 PURE 注释噪音在 `onwarn` 中精准过滤。当前 `npm run build` 无大 chunk / VueUse 注释 / circular chunk warning，浏览器路由巡检 11 个页面无 console error/warning。
19. **统计窗口口径一致性**：合格率趋势后端按“本月在内的最近 N 个自然月”聚合，前端月份轴使用同一自然月窗口；`verify-readonly.ps1` 已断言 `months=1` 不返回非本月数据，避免滚动天数窗口与月度报表轴错位。
20. **过程缺陷选项真实分页**：缺陷登记里的“所属工序”不再前端拉取全部工单详情再拼装选项，后端新增 `/api/defects/process-targets`，按“生产中工单 + 进行中工序 + 尚未产出批次 + 后续工序未开始”的同一生命周期口径分页返回；前端使用远程搜索选择器，`verify-readonly.ps1` 覆盖分页大小、关键字段与进行中状态，避免大数据量下的假可用下拉框。
21. **生产领料选项真实分页**：生产工单列表接口改为返回 `ProductionOrderResponse`，直接带生产物料编码/名称，前端不再为列表展示全量加载物料表；批次台账视图补出 `material_id` 并支持 `materialId` 精确过滤，生产领料弹窗按工单 BOM 子项物料分页查询合格批次。`verify-readonly.ps1` 已断言工单列表物料字段与批次 `materialId` 过滤，E2E 重置后继续通过。
22. **出货选项真实分页**：出货管理不再打开页面全量加载成品批次和客户，筛选栏与出货弹窗均改为远程分页搜索；成品批次按 `status=QUALIFIED + materialCategory=PRODUCT + keyword` 查询，客户按 `keyword` 查询后仅展示启用客户。前端构建、路由巡检、权限巡检和只读接口验收均已通过。
23. **追溯图与主数据前端可用性修复**：追溯查询页保留 ECharts tree 的拖动/滚轮缩放能力，并新增缩小、放大、适配按钮；默认缩放与边距改为更保守，双列状态下避免节点贴边裁剪。主数据页补齐 `mergeById`，修复工艺路线保存、BOM 新增/保存、检验项目编辑回显中的远程选择器数据源引用。`npm run build`、`verify-frontend-routes.ps1`、`verify-frontend-permissions.ps1` 均通过，并已用浏览器验证 `/trace` 双列图表与 `/master` BOM 弹窗无运行时错误。
24. **前端关键交互可重复验收**：新增 `scripts/verify-frontend-interactions.ps1`，使用 Edge CDP 以管理员身份巡检 10 组非写入交互：用户弹窗、主数据物料/工序/BOM/检验标准弹窗、批次详情、工单详情、检验明细、缺陷登记/处置、出货登记、追溯图缩放/适配、召回发起/详情、统计区间切换。脚本会拦截控制台 error/warning、网络 4xx/5xx 与运行时异常，避免“页面能打开但关键按钮一点击就报错”的假可用状态。
25. **答辩截图素材**：新增 `scripts/capture-report-screenshots.ps1`，自动登录管理员并保存质量看板、主数据、批次台账、生产工单、检验任务、缺陷管理、追溯查询、召回管理、统计报表 9 张 PNG 到 `docs/screenshots/`，截图过程同样拦截控制台与网络错误，避免报告素材来自异常页面。

## 前端页面清单（19 视图）

登录、主布局（侧边菜单按角色码过滤 + **退出登录**）、Dashboard（4 指标卡 + 合格率趋势 + 帕累托组合图）、**用户管理（含角色勾选/启停/重置密码——F1-2/F1-3 承载）**、主数据 ×6（表格+弹窗模式化；**工艺路线编辑挂工序管理页的"路线"标签**）、批次台账（筛选+状态 tag+入库弹窗+详情抽屉）、工单管理（列表+详情：工序时间线 Steps/领料对话框/报工按钮——开工按钮按 IPQC 门禁禁用态）、检验任务池+执行页（按项目动态表单，定量项即时判限）、缺陷管理（登记/处置）、出货管理、**追溯查询**（ECharts tree 双向追溯树，节点按状态着色——答辩高光页）、召回管理（发起向导：选源头批→预览影响面→确认；明细回收跟踪）、统计报表页。

## 实现顺序

- **A1 后端骨架**：pom（版本冻结）+ yml + common/config/security + 登录——验收：可启动、可登录、swagger 可访问、/druid 可访问、**分页断言通过、WITH RECURSIVE 穿 Druid 通过**
- **A2 主数据**：6 组 CRUD + BOM 防环/重复/启用状态校验（父项已有未关闭工单时拒绝调整 BOM，防在制工单用料口径漂移）+ 工艺路线（含重复工序与末道 IPQC 禁令；工序 `need_ipqc` 变更时若仍被未关闭工单引用则拒绝，防在制工单门禁口径漂移）+ 检验标准在途任务保护（同对象待检/检验中任务存在时拒绝改标准，防检验口径漂移）
- **A3 批次与入库**：入库事务 + IQC 自动生成 + 台账/详情
- **A4 生产链**：工单创建/领料/工序流转（含 HIGH 门禁）/完工入库（actualQuantity 链）
- **A5 质检闭环**：检验提交三重校验 + 结论联动 + 缺陷双载体 + 处置返工重检
- **A6 追溯与召回**：递归 CTE XML + 树组装 + 出货 + 召回事务
- **A7 统计 + 交付物**：4 统计接口 + **导出 openapi.json 快照**（报告附录 + B 阶段契约冻结）+ **backend/README.md**（环境版本、启动步骤、5 个演示账号、端口、**数据重置命令**：重跑 sql/01+02 两条 mysql 命令——E2E/手测后必须重置，防污染故事线数据与答辩截图）
- **B 前端**：脚手架→axios/pinia/router/布局→按 A2-A7 对应页面→追溯树/统计图
- **C E2E 联调**：verify-app（Playwright）走**两段式**故事线（半成品库存约束：批 8 已耗尽、内胆仅余 20）——先建壶体组件工单（≤20 个）走完领料/工序/FQC，再建成品工单消耗新 SF 批+温控器批 1，完工 FQC 后出货；反向追溯新 FP 批、登记售后缺陷、发起召回核对影响面；另建 1 台小批量链路验证领料/出货扣减至 0 后置 `DEPLETED` 且禁止再次出货；**跑完重置数据库**

## 验证方式

- 每阶段：`mvnw spring-boot:run` + swagger-ui 手测 + 关键事务 curl 正反用例（如余量不足领料应 400 且无脏数据）
- 数据库侧：领料/召回后直查 batch/batch_consumption/recall_detail 核对数量守恒与状态
- 前端浏览器验收：`verify-frontend-routes.ps1`、`verify-frontend-permissions.ps1`、`verify-frontend-interactions.ps1`
- 报告截图素材：`capture-report-screenshots.ps1` 输出到 `docs/screenshots/`
- 追溯黄金用例（重置后）：批 9 上游 7 节点、批 6 下游含批 9/10 及 3 客户且不含批 11
- 最终 E2E：Playwright 全流程 + 界面截图（课设报告素材）

## 明确不做（YAGNI）

单元测试全覆盖（以 E2E + 接口手测替代）、Redis、消息队列、Docker、按钮级权限、文件上传、国际化、JWT 服务端黑名单（但每次请求会校验账号状态和最新角色）。
