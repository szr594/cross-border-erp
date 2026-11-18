# 旧业务投影替换实施进度

2026-11-17。采购需求投影、采购来源及库存/打包关联替换已在本地实现，真实链路与历史迁移验收见 [最新记录](procurement-projection-acceptance.md)。虚拟机尚未升级此版本，生产路由未切换。

## 初始基础层历史记录

- Flyway V10 新建 order_business_projections 与 order_item_business_projections，并从旧订单表回填既有记录，保留原主键和来源版本。
- 新增 OrderBusinessProjectionReader，提供类型化订单/商品快照；投影不存在返回 ORDER_PROJECTION_NOT_READY，不回退为读取其他服务数据库。
- 迁移版本测试保持精确断言，更新预期 V10；mvn -q -pl backend test 通过。

尚未完成：业务匹配键扩展、v2 消息更新新投影、JPA 库存/打包关联替换、采购权威命令桥接、同步取消与分配/打包互斥、真实 MySQL 的既有数据回填对账、消费者虚拟机升级。读入口存在不等于业务已接入。
V10 不在当前虚拟机执行，生产订单写路由维持旧 backend。回归使用 H2，不能代替 MySQL 专项。

## v2 消息链路补验

2026-11-17，CSV 发布升级为 order.created.v2，携带白名单业务字段：订单号、外部状态、异常标志、负责人 ID、商品 ID/名称/SKU/数量/履约状态；不带客户电话、地址或原始 JSON。
消费者兼容 v1，并支持 order.created.v2/order.updated.v2，在同一事务内提交 Inbox、订单及商品业务投影。按订单行锁串行更新，拒绝旧版本覆盖、历史商品 ID 隐式删除及商品跨订单归属变更；拒绝消息会回滚 Inbox，等待重试或死信排查。
真实 MySQL/RabbitMQ OrderMessagingIT 共 6 项通过，包含 v2 业务快照、重复与旧版本、历史商品保护和 CSV→业务投影全链路、Broker/订单进程恢复、ACK 丢失重投。V10 已在隔离测试库实际执行。
实际命令：mvn -q -pl services/order-service -DskipTests package；mvn -q -pl backend -Dtest=OrderMessagingIT test。后端与订单模块普通回归也通过。

剩余：ERP SKU 等采购匹配键、库存/打包 JPA 关联替换与并发保护、采购命令桥接、消费者虚拟机升级。当前读取入口尚未替代旧实体，不能据此切换生产订单写入。

## 完整关联替换

V14～V16 已补齐业务匹配字段、需求投影、原 ID 回填、四类外键替换和执行恢复记录。采购单创建和来源详情、自动/人工库存分配、库存查询、打包查询及命令均已使用本地投影。保留单体的 JDBC 演示初始化及采购入口也显式同步投影，避免旧来源产生悬空引用。

Order 的需求创建/撤回/确认转单按需求自身版本发布 Outbox；同事务 Inbox 与投影消费防重复及乱序。执行占用、取消屏障和同步库存释放已接入，并在真实 MySQL 修复认领与撤回的旧快照竞态。新模式关闭旧订单命令入口，禁止双权威写入。

仍待独立安排：完整订单查询及其余命令迁移、真实 ID 对账、部署新容器版本、虚拟机验收及生产路由切换。上述“剩余”段落保留为此前阶段的历史记录，以本节及最新验收记录为准。

## 2026-11-18 独立订单查询与真实履约链路验证

已实现独立订单列表、详情、筛选项、履约进度、事件与采购需求分页，以及负责人调整、异常开启和解除命令。库存和打包事实由受服务令牌保护的 Backend 内部接口读取；失败返回 503，不伪装成零库存。网关订单路由通过 ORDER_SERVICE_URL 显式启用，库存区 inventory-allocations 继续转发 Backend。

实际执行：

```powershell
mvn -pl backend,services/order-service,services/identity-service,services/gateway-service -am test -q
mvn -pl backend,services/order-service -am package -DskipTests -q
mvn -f backend/pom.xml '-Dtest=ProjectionFulfillmentIT' test -q
git diff --check
```

四模块常规回归与打包通过。2026-11-18 启动 Docker 后重跑 ProjectionFulfillmentIT：6 项通过，失败 0、错误 0、跳过 0。真实 MySQL/RabbitMQ 链路包含 CSV→采购→收货→库存→打包、重复请求、取消释放、并发认领/撤回、执行恢复，以及新增订单详情“已齐货”和打包后履约接口“已打包”断言。

尚未完成：安全用户目录同步专项测试、历史数据迁移工具专项测试、网关切换专项测试、正式切换编排与回退验收。维护期迁移工具目前为初稿，禁止直接用于虚拟机。虚拟机仍运行 4562667，本次新增代码未提交，生产路由未切换。本节仅表示本地已列明测试通过，不代表整体迁移验收完成。

## 2026-11-18 历史数据迁移和恢复专项

新增 OrderDataMigrationIT，在隔离 MySQL 8.4 库执行三项专项：保留订单/商品/需求 ID、版本和关联并生成 Outbox，拒绝非空目标重复迁移；通过真实 MySQL 触发器在商品复制阶段注入故障，检查目录、店铺、订单和 Outbox 全部回滚；使用容器内 mysqldump 实际导出测试源库，再向独立恢复库导入，验证订单版本、需求数量和迁移前 Outbox 状态。

测试发现并修复合法列名 address_line1 被错误拒绝的问题，列名规则调整为允许后续数字。故障测试明确断言触发器错误信息，防止提前校验失败造成假通过。

命令：mvn -pl services/order-service '-Dtest=OrderDataMigrationIT' test -q。

恢复演练仅使用合成数据和隔离数据库；没有执行虚拟机恢复、生产流量切换后回切或覆盖真实数据库。迁移要求停止两侧写入且目标业务表/Outbox 为空；目标已有独立订单时拒绝自动合并。准备虚拟机切换前仍需生成正式编排、备份与维护窗口操作清单；切换后出现新写入不能直接覆盖旧备份。

## 本地优先验收（2026-11-18）

用户决定暂停上传虚拟机。新增受版本控制的纯演示 CSV 测试夹具 backend/src/test/resources/fixtures/orders-demo.csv；DemoStoreControllerTest 实际完成老板初始化 DEMO-STORE、CSV 预检、确认及重复确认，断言仅生成 3 单、4 商品行、合计 10 件。权限测试覆盖匿名和其他岗位拒绝；初始化重复调用及重新启用保持店铺 ID。

实际命令：mvn -pl backend,services/order-service,services/identity-service,services/gateway-service -am test -q；pnpm type-check；pnpm build；mvn -pl backend,services/order-service -am package -DskipTests -q；mvn -f backend/pom.xml '-Dtest=ProjectionFulfillmentIT' test -q；git diff --check。全部通过。ProjectionFulfillmentIT 6 项、0 失败、0 错误、0 跳过，在独立 MySQL 与真实 RabbitMQ 中验证订单→采购→收货→库存→打包及并发、取消和恢复。前端保留既有 Element Plus 大分包提示。

范围说明：店铺/实际 CSV 用 MockMvc 集成验证，跨服务履约使用专用合成数据，尚未把同一份 CSV 的 3 个订单全部做浏览器逐项点击验收。虚拟机无改动，新增代码未提交。

## MS-08 正式任务复核（2026-11-18）

部署单位以 HTML 看板正式任务为准，停止将采购小功能作为单独发布单位。本次继续 MS-08，不开始 MS-09。

ProjectionFulfillmentIT 已移除直接 UPDATE purchase_orders 设置 ORDERED 的测试前置条件，改为真实确认下单接口，并保留重试响应严格相等断言。首次真实运行发现纳秒/微秒精度导致重试响应不相等，PurchaseOrderingService 将确认下单时间统一为微秒后修复。

实际执行：mvn -pl backend,services/order-service -am package -DskipTests -q；mvn -f backend/pom.xml '-Dtest=ProjectionFulfillmentIT' test -q；mvn -pl services/order-service,services/identity-service,services/gateway-service -am test -q；git diff --check。全部通过。完整真实接口闭环6项通过、失败0、错误0、跳过0；不再靠手工改采购状态走通收货。

MS-08 仍不勾选正式完成：独立订单服务经网关的带数据浏览器验收、最终统一部署包和虚拟机正式保留新路由尚未完成。此前本地单体浏览器验收与虚拟机空库切换回退不能代替这些验收。

## MS-08 独立服务经真实网关的浏览器验收（2026-11-18）

隔离 Identity、Order、旧 Backend、Gateway 四个真实 JVM、独立 MySQL 库及 RabbitMQ vhost，浏览器通过同源真实 HTTP 代理到 Gateway，无浏览器请求拦截。实际点击 Identity 登录、CSV 预检及确认、订单列表和详情，并访问库存覆盖与事件请求以及旧业务工作台。API均成功且包含X-Request-Id，无pageerror。

数据归属核对：Order库3单4项，Backend旧sales_orders为0，Backend消息投影3单。证据 output/ms08-gateway-browser/result.json 和截图（忽略目录），测试脚本 tmp/ms08-gateway-browser-fixed.mjs。

验收首次暴露用户目录同步 insert ignore + update 的并发锁升级死锁。已改为单语句条件UPSERT，按用户ID排序，并保留源版本检查。真实MySQL四线程20次同步通过，旧版本不覆盖停用状态；订单模块回归通过。Windows运行JAR文件锁导致一次repackage失败，停止旧测试订单进程后重新构建通过，修复版浏览器链路全部通过。

MS-08本地验收通过，虚拟机正式部署及带数据核对仍待执行。按用户规则，部署验收后才将正式任务勾选完成，不因子功能另行部署。当前虚拟机仍为此前回退版本。

## MS-08 正式部署最终验收（2026-11-18）

操作者确认五个组件运行 ms08-20261118，10个容器healthy，目录与履约内部链路成功、前端HTTP200。迁移前私有备份位于 /opt/cross-border-erp/backups/ms08-20261118-193515；演示店铺创建后另存 business-after-store.sql。原用户 szr ID=1 在旧库与Identity映射一致。

维护迁移保留渠道ID1、店铺ID1 DEMO-STORE ACTIVE，以及原INVALID导入批次1个、导入行4条、错误4条。启用正式路由后，操作者在浏览器导入演示CSV，显示3单、4商品行、10件，待处理标签正常；随后明确确认订单详情、事件及其他业务页面均正常。

操作者实际SQL输出：旧sales_orders=0、sales_order_items=0；独立Order库sales_orders=3、sales_order_items=4；Backend消息投影order_business_projections=3、order_item_business_projections=4。正式MS-08部署及约定页面验收通过，HTML看板标记完成。保留旧Backend与源表，不删除历史数据，不代表采购/仓库/打包服务已独立迁移。

后续运维使用 compose.production.yml + compose.identity.yml + compose.order.yml + ms08-release/compose.ms08-candidate.yml + ms08-release/compose.runtime.yml + ms08-release/compose.ms08-cutover.yml；不得再叠加旧rollback文件或只用旧基础配置重建容器。已有新业务写入，回退需先停写和对账，不允许直接覆盖旧备份。
