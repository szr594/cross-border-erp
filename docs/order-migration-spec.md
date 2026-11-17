# MS-08 订单与 CSV 导入服务迁移规格

日期：2026-11-17。状态：设计草案，未批准跨服务命令语义，未实施代码或数据库变更。

## 目标与现状

Order Service 使用内部端口 8082、独立逻辑库 cross_erp_order、独立账号及 Flyway 历史。RS256 验签沿用 Identity 公钥，公开接口以 api-reference.md、order-api.md 和 order-import-spec.md 为基线。
当前 OrderQueryService 直接依赖 InventoryAllocationRepository 与 PackingTaskRepository；OrderCommandService 取消时调用库存释放；采购、收货和打包仍关联 SalesOrder/SalesOrderItem。不能通过复制实体与连接旧数据库完成独立服务迁移。

## 数据归属

订单服务独占 sales_channels、sales_stores、sales_orders、sales_order_items、order_status_events、order_procurement_requests、order_import_batches、order_import_rows、order_import_errors。
本地增加员工显示投影、履约投影、outbox_events、inbox_events。员工仅保存稳定 ID、显示名、账号状态及业务所需角色投影，不复制密码与刷新会话。
库存、收货、采购单、箱和打包记录继续由旧 backend 所有；订单服务禁止跨库 JOIN 和 Repository 访问。
订单和商品 ID 在切换时保留；旧 backend 使用必要的本地只读订单/商品投影支撑原有业务关联。所有影响业务判断的投影必须携带来源版本。
客户电话和完整地址仅留在订单所有者，不放入 RabbitMQ 事件和非必要的旧后端投影。旧表中敏感字段清理由单独批准的迁移完成。

## 公开 API 与授权

列表、详情、筛选选项、履约进度、事件分页、负责人和异常处理路径及响应保持兼容。
运营暂可查看全部订单；采购看不到客户电话和完整地址；仅老板可取消订单或使用 CSV 导入。裁剪在服务端 DTO 层完成。
CSV 保留 POST /api/order-imports/preview、POST /api/order-imports/{importId}/confirm、GET /api/order-imports/{importId} 和 errors 分页，沿用现有状态码。确认导入只在订单库一个本地事务中创建订单、商品、业务事件和 Outbox；任意行失败整体回滚。
批次归属、文件大小、模板、错误分页及不保留原文件规则继续按现有规格执行。重复确认与并发确认不得重复创建订单。

## 跨服务协作与防双写

切换前旧 backend 是唯一订单写入方，新服务仅作隔离验证。切换后 Order 是唯一权威写入方，旧 backend 关闭订单和导入写入口，只处理其拥有的业务及消息投影。
采购需求的认领、撤回与转单仍由订单所有者串行检查版本，通过受保护的内部幂等命令交互；旧采购模块必须先接入兼容桥接，再开放独立订单写入。
收货、库存分配和打包状态通过事件更新订单履约投影。查询延迟需可识别，命令不能用过期投影替代权威校验。
订单库存覆盖子路径仍由库存所有者提供，通过网关精确路由到旧 backend，不能被 /api/orders/** 的宽泛路由吞掉。
内部接口必须采用独立服务凭据、超时和最小权限，不能直接信任客户端身份头或复用浏览器 Cookie。

## RabbitMQ 可靠性

采用架构文档既定的事件命名和 envelope，包含 eventId、aggregateId、aggregateVersion、schemaVersion、correlationId。
本地业务与 Outbox 同事务提交；收到 publisher confirm 后才标记发布。Inbox 按 consumerName/eventId 唯一约束去重，与业务投影更新同事务。提交后再 ack。
消息重复、乱序、Broker 重启、确认丢失和消费失败均需测试。有限重试后进入死信，必须能人工重放，不能默默跳过消息。RabbitMQ 网络恢复后应可继续发布积压 Outbox。
事件字段白名单不包含客户电话、完整地址、密码、令牌或自由文本敏感信息。

## 订单取消的兼容边界

现有 architecture.md 11.3 规定 Warehouse 迁移前原子取消继续由旧单体负责，本阶段遵守该约定，不直接更改取消接口返回语义。
这意味着不能在取消所需兼容桥接完成前整体迁移订单权威写入，也不能让两个服务同时修改权威订单。第一批只在本地完成独立实现与验证，不对虚拟机切换订单写路由。
2026-11-17 已确认最终方案：保持现有同步接口，库存所有者幂等释放成功后，Order 才提交本地取消并返回成功；超时允许使用同一幂等键重试。不引入 202 或对外异步取消成功状态。库存释放与打包/分配必须串行互斥，释放结果须持久化；Order 提交失败后的重试继续完成取消，不能重复释放。

## 分批实施与文件范围

1. order-service 模块入口、独立数据层、用户显示投影、RS256 与基础接口契约测试。新增服务自己的 Flyway V1，不修改旧迁移。
2. RabbitMQ Compose 配置、Outbox/Inbox、订单/商品与履约投影；旧 backend 增加兼容桥接，分别验证消费者幂等和授权。
3. 订单查询、命令、CSV 接口实现及 MySQL 8.4 并发测试。保持旧前端 API 目录接口签名。
4. 取消/转单兼容方案批准后，再进行写入方统一切换、容器验收和回退演练。
5. 维护窗口迁移既有订单和导入记录，保留主键；核对数量、金额、版本、负责人及旧业务关联，再更新虚拟机。

预计涉及 services/order-service、backend 中订单/采购/库存/打包协作接口、gateway 路由、Compose/RabbitMQ 配置、API 文档和迁移验收脚本。第一批不改前端页面。

## 切换与回退门槛

迁移前停写、备份配置/数据库/旧镜像。核验用户投影、主键、自增值、订单项数量、金额、CSV 批次和关联完整性。确认重复导入拒绝、旧角色裁剪一致。
切换时订单及导入命令必须一起转交唯一写入方；不得只切 CSV 确认而不让采购/库存获知新订单。
回退演练限定维护窗口未恢复写入场景。已恢复业务写入后的回退需额外增量对账方案，不能直接用旧备份覆盖新订单。
验收覆盖现有完整业务闭环、CSV 全有或全无、取消/打包竞争、消息重复与中断恢复、公钥验证、受限账号及页面交互。全部通过前 MS-08 不勾选完成。
