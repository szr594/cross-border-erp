# 采购桥接实施进度

2026-11-17，已批准认领→本地采购单→确认、失败释放、超时先查询流程。

当前进展：需求投影、采购来源及库存/打包关联替换已实现，详细范围和最新验证见 [采购投影验收记录](procurement-projection-acceptance.md)。以下各节保留分批实施历史，虚拟机与生产路由尚未切换。

订单服务侧：Flyway V10 建立 procurement_claims 和 procurement_claim_owners，按需求 ID 唯一归属；认领按排序加行锁，校验原数量/版本/状态，幂等键绑定请求摘要。确认重复调用返回原结果，不同采购引用拒绝；已确认不可释放，释放后同键仍返回原释放状态，重新认领需新键。

内部接口（不经网关公开）：

- POST /internal/procurement-requests/claims：idempotencyKey、items（requestId/quantity/version）。
- GET /internal/procurement-requests/claims/{id}：查询认领结果。
- POST /internal/procurement-requests/claims/{id}/confirm：purchaseReference。
- POST /internal/procurement-requests/claims/{id}/release。

独立凭据 ORDER_INTERNAL_SERVICE_TOKEN 至少 32 字节，以 X-Service-Token 传递；无配置或错误凭据均拒绝。凭据不得出现在浏览器、日志或仓库。

已通过默认关闭的桥接开关接入旧采购创建入口，恢复器实现见下文。现有需求撤回、订单取消仍需读取认领状态并统一锁序，当前未完成；禁止生产启用该接口。
库存/打包 JPA 关联尚未替换，本文不表示三模块改造完成。

## 公开幂等契约实施

2026-11-17，用户批准 Idempotency-Key、不同请求 409、未决结果 503 PURCHASE_OPERATION_PENDING。
旧 backend 新增 Flyway V11 purchase_create_operations，保存 actorId、摘要、请求、状态和原响应。携带合法请求头时，控制器调用 IdempotentPurchaseCreation：成功采购单与原响应在同一事务保存，重复键按账号与摘要核对，成功重放不重复建单。
未携带头暂走原路径，兼容旧调用方；这不代表已全面启用强制幂等契约。前端尚未统一传入并保留操作键，跨服务桥接不得启用。
已执行 mvn -q -pl backend -Dtest=PurchaseOrderCommandControllerTest test，5 项通过，包括原响应重放及改变请求 409。测试使用 H2 和本地创建流程，不代表远程超时或进程恢复验收。
V11 记录仅 PENDING/SUCCEEDED；后续 V12 增加阶段、失败原因和恢复租约，不能把所有 PENDING 自动视为跨服务未知成功。

## 调用封装补齐

2026-11-17，frontend/src/api/purchaseOrders.ts 新增 preparePurchaseCreation：在创建操作对象时生成或接收键，固定序列化请求体，submit 的所有重试均传相同 Idempotency-Key。当前工作台只有查询，没有创建表单，因此尚无 UI 调用者，不宣称前端流程已接通。
旧 backend 新增 RemoteProcurementClaims，提供 claim/get/confirm/release HTTP 客户端，使用独立 X-Service-Token、2秒连接/5秒读取超时，未知远程结果映射503；检测到数据库事务时拒绝远程调用。开关 app.procurement.remote-enabled 默认关闭。
RemoteProcurementClaimsTest 与 PurchaseOrderCommandControllerTest 共6项通过；前端类型检查通过。远程开关启用时改由 PurchaseBridgeWorkflow 编排，默认关闭时保留原入口；库存/打包关联替换仍未完成。

## 持久化恢复编排

Flyway V12 增加 operation_phase、actor_roles、lease_owner、lease_until、next_attempt_at 和失败原因。PurchaseBridgeWorkflow 先查询同键认领，再认领、本地创建及确认；远程调用均在本地事务外执行。本地采购单与原响应在同一事务提交，30 秒租约控制并发执行，每 5 秒扫描最多 20 个待恢复操作。

确认响应丢失时保留本地结果，下次查询后重试确认。认领响应丢失时先查询远程结果；本地业务失败时回滚本地事务，记录 RELEASE_PENDING，后续释放认领后进入 FAILED。远程明确拒绝认领直接进入失败终态。同键不得变更请求或账号。

MySQL 8.4 专项命令：`mvn -q -pl backend -Dtest=PurchaseBridgeWorkflowIT test`。6 项通过：成功重放、确认响应丢失、认领响应丢失、事务回滚后释放、同键并发和明确拒绝终态。测试在随机隔离数据库运行 V11/V12；远程客户端与本地命令使用受控测试替身，本地事务写入真实 MySQL。新建编排对象验证持久化恢复，不等同于杀 JVM 或完整远程 HTTP 链路验收。

尚未完成的启用条件：新订单需求投影接入本地采购校验；需求撤回/取消与认领互斥；完整 HTTP、进程中断与并发业务专项；库存与打包实体及查询关联替换；实际前端创建页面调用。因此开关继续关闭，虚拟机保持影子部署，不切生产写路由。

本批回归：`mvn -q -pl backend,services/order-service test` 通过，六份 schema 测试的精确版本断言同步为 V12；`pnpm type-check` 通过；`git diff --check` 通过（有 Windows 换行提示）。用户明确允许构建命令生成 frontend/dist 后，补跑 `pnpm build` 成功，退出码 0；Element Plus 分包约 853 kB，仍有超过 500 kB 的体积提示，不影响本次构建通过。代码未提交、未更新虚拟机。

## 未确认采购单隔离（V13）

本批增加 `purchase_orders.bridge_confirmed`，历史普通采购单默认为 TRUE；`purchase_create_operations.purchase_order_id` 使用唯一约束及本地外键绑定采购单。迁移按 V12 原响应的固定 JSON 序列化格式回填关联，完整匹配首字段数值 ID（例如 12 不匹配 123），将历史 PENDING 桥接采购单置为不可见，已成功确认的保持可用。无法关联的历史桥接响应会触发约束失败，迁移不会静默放行未知记录。

`PurchaseOrderCommandService.createClaimed` 在持久化前将采购单设为未确认；编排器在保存原响应的同一事务保存采购单关联。远程确认成功且采购引用匹配后，另一个短事务锁定操作记录、校验租约和本地关联，同时设置采购单可见与操作 SUCCEEDED。事务失败时两项回滚；不修改最初响应中的业务版本和时间。

采购列表、分页总数排除未确认单；详情和事件返回原有 404。收货服务在登记包裹及写入新收货批次前检查确认标记，未确认时返回 503，不产生包裹、收货或库存副作用。对外 DTO 不增加内部确认标记。

验证结果：

- 先运行新增隔离用例，3 项因缺少确认标记失败，确认当前实现无法满足隔离要求。
- `mvn -q -pl backend -Dtest=PurchaseBridgeWorkflowIT test`：9 项通过。真实 MySQL 8.4 验证 V12→V13 历史回填、超时后仍隐藏、恢复后开放、同键并发、确认引用不匹配，以及通过数据库触发器注入完成状态写入失败后可见性回滚。远程客户端和本地命令仍为受控测试替身；不宣称整套 HTTP 链路已验收。
- `mvn -q -pl backend,services/order-service test`：通过。包含实际 JPA 桥接创建隔离、采购查询隐藏/恢复及收货无副作用测试，六份 schema 测试精确版本断言更新为 V13。
- 前端 `pnpm type-check`、`pnpm build`：通过；仅有既有 Element Plus 大分包提示。

本隔离阶段未提交 Git、未修改虚拟机或生产写路由。后续采购需求投影及旧关联替换已完成本地实现，见文首最新记录。
