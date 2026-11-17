# 采购需求投影与履约关联替换

2026-11-17。用户已批准完整范围：需求事件、投影、采购来源、库存和打包关联、历史回填及命令授权互斥。本记录描述本地代码和隔离验收，不代表虚拟机升级或生产订单路由已切换。

## 最终数据链路

```text
Order：创建/撤回采购需求、确认转单
    → 同事务 Outbox → RabbitMQ → 同事务 Inbox + 订单/商品/需求投影
    → 采购分配 → 国内收货 → 库存分配 → 打包箱商品
```

采购、库存、打包只关联旧后端自己的投影，不跨库 JOIN。旧字段名和公开 DTO 保持兼容，以下外键目标已替换：

| 原字段 | 新关联目标 |
| --- | --- |
| purchase_order_allocations.procurement_request_id | procurement_request_projections.request_id |
| inventory_allocations.sales_order_item_id | order_item_business_projections.order_item_id |
| packing_tasks.sales_order_id | order_business_projections.order_id |
| packing_box_items.sales_order_item_id | order_item_business_projections.order_item_id |

## 事件、版本与身份

`order.created.v2`、`order.updated.v2` 增加 schemaVersion=3，包含安全展示字段、稳定来源身份、规格及 ERP SKU；消费者保留原 v1/v2 兼容。`order.procurement.snapshot.v1` 使用需求 ID 和需求自身版本，不能用订单版本替代需求版本；其订单快照用于处理需求事件先到达的情况。

Inbox 和所有投影在同一事务内更新。重复消息不重复应用；旧需求版本不能覆盖已转单状态；需求 ID 不得换绑商品，商品 ID 不得换绑订单，历史商品不得从快照中隐式删除。新旧订单 ID 冲突且来源身份不匹配时拒绝处理并回滚 Inbox。迁移要求保留原渠道、店铺、订单、商品、需求的 ID 映射，不自动猜测映射关系。

投影只保存必要业务字段。客户电话、完整地址、原始第三方数据、认证密码及令牌均不进入新消息或业务投影；兼容 v1 的 items JSON 也改为白名单重建。

## 执行授权与取消

新模式通过 `app.procurement.remote-enabled=true` 启用。收货、人工分配及打包命令先在本地事务外取得 Order 的持久化执行占用，再运行本地业务事务。普通 JWT 不能调用内部授权或取消回调，内部接口使用至少 32 字节的独立 `ORDER_INTERNAL_SERVICE_TOKEN`。

本地订单投影尚未交接到 ORDER 权威时，命令在取得远程占用前返回 `503 ORDER_PROJECTION_NOT_READY`，不会回退到旧表或执行业务写入。角色校验也先于远程调用，避免无权限请求改变执行占用。

Order 保留 ACTIVE 占用时拒绝取消，不按时间自动放行。旧后端保存本地执行操作、60 秒开始期限和提交状态；业务事务锁定操作记录，提交后释放远程占用。进程中断后的恢复器先核对本地事务结果，再释放；释放墓碑阻止延迟到达的旧授权请求重新生效。执行授权不依赖最终一致投影中的状态判断。

取消在 Order 持久化 PENDING 屏障，再调用旧后端同步释放预留库存。旧后端在一个事务中释放、记录幂等回执并关闭本地履约入口；Order 收到成功回执后才提交 CANCELED 并发布事件。超时保留屏障、使用原键恢复；已消耗库存返回 409 并保持订单未取消。未提供取消请求头时，从账号、订单、版本及原因派生稳定键，兼容现有调用方。

认领和撤回统一先锁订单再锁需求。在真实 MySQL 的 REPEATABLE READ 下，撤回的认领校验改为锁定读取，避免早先的账号查询建立旧快照后看不到已提交认领。采购缺口累计及取消屏障也使用当前锁定读取。

保留单体模式时，旧订单写入通过事务内适配更新本地投影；交接到 ORDER 权威后拒绝旧适配写入。新模式关闭旧订单命令控制器，防止两套权威并行修改。

## 迁移与历史数据

旧后端 V14 扩展订单/商品投影并建立需求投影，包括补齐 V10 后新建的历史记录。V15 是 Flyway Java 迁移：检查身份冲突、检查全部历史引用，检查通过后才更换四类外键；不删除不一致数据。V16 保存执行操作和取消回执。Order 独立库 V11 保存执行占用和取消操作。

MySQL 历史迁移专项覆盖已有采购、收货、库存预留和打包箱记录，核对原 ID、数量及外键目标；故意缺失需求投影时必须在替换任何关联前失败。MySQL 的 DDL 不支持整批事务回滚，因此升级前仍须执行已批准的备份和维护窗口流程。

## 变更文件与原因

| 文件或目录 | 改动原因 |
| --- | --- |
| backend/src/main/resources/db/migration/V14__procurement_demand_projections.sql | 投影扩展及原 ID 回填 |
| backend/src/main/java/db/migration/V15__replace_order_references.java | 预检与跨数据库兼容的外键替换 |
| backend/src/main/resources/db/migration/V16__fulfillment_execution_operations.sql | 执行恢复及取消回执 |
| backend/.../integration/orders/model、三个投影 Repository | 本地 JPA 投影关联 |
| backend/.../integration/orders/OrderEventConsumer、LegacyProjectionListener | 消息版本、去重、身份校验和保留单体适配 |
| backend/.../procurement/model、service | 采购来源切换到需求投影；未就绪返回可重试错误 |
| backend/.../inventory、packing | 分配、查询、取消、打包实体及服务改用本地投影 |
| backend/.../integration/orders/OrderExecutionGate、RemoteExecutionAuthority | 事务外取得权威占用及提交后恢复释放 |
| 三个 CommandFacade 和对应控制器 | 在业务事务开始前获取授权，提前验证角色 |
| backend/.../integration/orders/OrderCancellationCallback*、OrderCallbackSecurity | 内部同步释放库存及独立凭据边界 |
| backend/.../order/model、OrderCommandService、SalesOrderRepository | 旧模式投影适配及一致锁序 |
| backend/.../procurement/config/DemoPurchaseOrderInitializer | 兼容原 JDBC 演示数据初始化 |
| services/order-service/.../events、procurement、fulfillment | 需求发布、创建/撤回/认领互斥、权威执行占用与同步取消 |
| services/order-service/.../db/migration/V11__fulfillment_execution_fences.sql | Order 所有者的持久化屏障 |
| 各 SchemaMigrationTest/MySql*SchemaIT、OrderFoundationTest | 精确版本断言及按聚合类型检查不同事件 |
| ProcurementProjectionTest、ExecutionAuthorityTest、ProjectionFulfillmentIT、HistoricalProjectionMigrationIT | 消息、授权、历史迁移和真实履约链路专项 |

## 验证命令

```powershell
mvn -q -pl backend,services/order-service package
mvn -q -pl backend '-Dtest=ProjectionFulfillmentIT,HistoricalProjectionMigrationIT,PurchaseBridgeWorkflowIT,OrderMessagingIT' test
mvn -q -pl backend -Dtest=ProjectionFulfillmentIT test
# 原有 MySQL 专项：先将 MYSQL_IT_URL/USER/PASSWORD 指向隔离测试库
mvn -q -pl backend '-Dtest=MySql*IT' test
mvn -q -pl backend -Dtest=MySqlOrderImportIT test
# 最后增量验证与后端重新打包
mvn -q -pl backend '-Dtest=ProcurementProjectionTest,PurchaseOrderCommandControllerTest' package
cd frontend
pnpm type-check
pnpm build
```

真实专项使用独立随机 MySQL 测试库、独立 RabbitMQ vhost 和两个实际 Java 进程。测试数据均为虚构数据，不读取虚拟机数据库。采购单与供应商下单的状态置为 ORDERED 是测试前置数据准备，该下单命令本身不属于本次验收。

验证汇总：普通回归共 115 项（旧后端 106、Order 9）；真实 MySQL/RabbitMQ 专项共 45 项，均无跳过。

| 测试组 | 项数 | 覆盖 |
| --- | --- | --- |
| ProcurementProjectionTest（包含在普通回归中） | 6 | 重复、乱序、归属冲突、旧 ID 交接、隐私白名单、交接前执行阻断 |
| ExecutionAuthorityTest（包含在普通回归中） | 4 | 持久化占用、取消超时屏障、认领撤回互斥、消耗阻断 |
| ProjectionFulfillmentIT | 6 | 两个真实 JVM：CSV→需求→采购→收货→自动分配→打包、取消、并发、重启、内部鉴权、人工分配 |
| HistoricalProjectionMigrationIT | 2 | 历史 ID/数量/外键对账及悬空投影预检失败 |
| PurchaseBridgeWorkflowIT | 9 | 真实 MySQL 编排与回滚；远程/本地命令是受控替身 |
| OrderMessagingIT | 6 | 真实 Broker 发布消费、ACK 丢失、Broker 中断和订单进程恢复 |
| 原有 MySql*IT | 22 | 保留单体的采购、收货、库存、CSV、查询、并发及精确迁移版本 |

组合专项首次被人工分配用例缺失“破损说明”的测试数据拦下，补齐前置数据后单独重跑该组；原有 MySQL CSV 用例的精确版本仍为 V8，更新为 V16 后复跑。未删除测试、跳过测试或放松业务断言。其余组保留各自成功运行记录，最终以各组最新 Surefire XML 为准。

`pnpm type-check`、`pnpm build` 均通过。前端构建仍有既有 Element Plus 大分包提示，未修改或隐藏该提示。生产代码和部署包的提交、推送及虚拟机升级未执行。

## 部署边界

当前虚拟机仍使用既有影子部署，未更新新关联版本、未启用桥接、未切生产写路由。生产升级还需要核对真实 ID 映射、完整订单查询及其余命令的迁移范围，并单独安排虚拟机升级验收。

桥接运行还需显式设置 `ORDER_SERVICE_INTERNAL_URL`、`LEGACY_BACKEND_INTERNAL_URL`、`ORDER_INTERNAL_SERVICE_TOKEN` 和两侧消息连接配置；旧后端保持 `APP_AUTH_LEGACY_ENABLED=false` 并加载 Identity 公钥。现有 compose.order.yml 继续作为影子部署配置，不自动启用桥接。
