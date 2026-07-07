# 产品质量追踪系统后端

后端是数据库课设项目的业务核心，基于 Spring Boot 提供批次追溯、生产流转、三级检验、缺陷处置、出货召回和统计报表接口。接口数据全部来自 MySQL `quality_trace`，前端通过真实接口联调。

## 环境要求

- JDK 21+（本机 JDK 25 可通过 `release=21` 编译）
- Maven 3.9+
- MySQL 8.4
- 默认数据库：`quality_trace`
- 默认账号：`root / 123456`

## 初始化数据库

在项目根目录执行：

```powershell
mysql -uroot -p < sql/01-schema.sql
mysql -uroot -p < sql/02-init-data.sql
```

或直接重置演示库：

```powershell
.\scripts\reset-db.ps1
```

## 启动

```powershell
cd backend
mvn spring-boot:run
```

启动成功后：

| 项目 | 地址 |
| --- | --- |
| 接口前缀 | `http://localhost:8081/api` |
| Swagger UI | `http://localhost:8081/swagger-ui/index.html` |
| OpenAPI 快照 | `backend/openapi.json` |
| Druid 监控 | `http://localhost:8081/druid`，账号 `admin / admin123` |

## 接口模块

后端 Controller 覆盖 17 组业务接口：

```text
认证、用户、物料、供应商、客户、工序、工艺路线、BOM、检验标准
批次、生产工单、检验任务、缺陷、出货、追溯、召回、统计报表
```

关键事务包括：

- 原材料入库：生成批次并自动创建 IQC 任务。
- 生产领料：按 BOM 校验物料，原子扣减批次余量，写入消耗关系。
- 工序流转：校验前道工序和 IPQC 结论，禁止跳步。
- 完工入库：校验工序完成状态，生成成品/半成品批次和 FQC 任务。
- 检验提交：校验标准项完整性，联动批次、工序和缺陷状态。
- 缺陷处置：支持返工、报废、让步、退货并按状态机拦截非法操作。
- 出货登记：仅允许合格成品批次出货，扣减至 0 自动置为已耗尽。
- 召回发起：基于下游追溯自动生成影响面和召回明细。

## 演示账号

所有账号密码均为 `123456`。

| 账号 | 角色 | 用途 |
| --- | --- | --- |
| `admin` | 系统管理员 | 用户、主数据、BOM、工艺路线、检验标准维护 |
| `zhangsan` | 仓库人员 | 原材料入库、批次台账、成品出货 |
| `lisi` | 生产人员 | 工单创建、领料、工序流转、完工入库 |
| `wangwu` | 质检员 | IQC/IPQC/FQC 检验执行、缺陷登记 |
| `zhaoliu` | 质量主管 | 缺陷处置、召回管理、统计报表、追溯查询 |

## 验证命令

编译验证：

```powershell
cd backend
mvn -q -DskipTests compile
```

后端运行中时，在项目根目录执行接口验证：

```powershell
.\scripts\verify-readonly.ps1
.\scripts\verify-e2e-flow.ps1
.\scripts\verify-serial-concurrency.ps1
```

`verify-readonly.ps1` 验证登录、分页、追溯、统计、关键查询口径等只读能力。

`verify-e2e-flow.ps1` 会创建真实两级生产链并验证检验、缺陷、出货、追溯、召回、审计字段、库存扣减和业务拒绝场景，默认结束后自动重置数据库。

`verify-serial-concurrency.ps1` 并发分配同一单号前缀，验证 `serial_number` 流水表不会重复或断号。

## 前端联调

前端位于 `../frontend`，开发端口为 `5174`，通过 Vite 代理访问后端 `8081`。

```powershell
cd ..\frontend
npm install
npm run dev
```

前端运行后，可在项目根目录执行：

```powershell
.\scripts\verify-frontend-routes.ps1
.\scripts\verify-frontend-permissions.ps1
.\scripts\verify-frontend-interactions.ps1
.\scripts\capture-report-screenshots.ps1
```
