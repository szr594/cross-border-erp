# Cross Border ERP 微服务架构

状态：目标架构已批准，业务代码尚未开始迁移。

批准日期：2026-11-13。

适用范围：认证、订单与 CSV 导入、采购、收货与库存、打包、前端和本地部署。

## 1. 文档目的

本文定义 Cross Border ERP 从现有 Spring Boot 模块化单体迁移到微服务架构时的正式技术边界。后续目录调整、数据库拆分、内部接口、RabbitMQ 事件和测试均以本文为基线。

现有 `backend` 在全部功能完成迁移和验收之前继续作为可运行基线，不在迁移过程中提前删除。公开 HTTP 路径默认保持 `docs/api-reference.md` 中的现有契约；如果命令的同步语义、状态码或响应字段必须变化，需要先更新对应业务规格并单独批准。

## 2. 架构目标

- 按业务所有权拆分可独立构建、运行和迁移的服务。
- 每个服务只写入自己的数据库，不共享 JPA 实体，不跨库连接查询。
- 保留订单、采购、收货、库存和打包的完整履约闭环。
- 服务内继续使用本地事务、行锁、乐观锁和唯一约束保护强一致业务规则。
- 跨服务状态通过明确的内部接口或 RabbitMQ 领域事件协作。
- 使用 Outbox/Inbox 和幂等命令处理至少一次投递，禁止依赖“消息只发送一次”。
- 浏览器只访问网关，不直接访问内部服务和 RabbitMQ。
- 保持客户电话、完整地址和凭据不进入采购、仓储事件及普通日志。

## 3. 非目标

- 第一阶段不引入 Nacos、Kubernetes、Service Mesh 或分布式事务协调器。
- 不使用数据库 XA 或两阶段提交模拟跨服务事务。
- 不因拆分服务扩展当前 MVP 业务范围。
- 不接入真实 Faire、1688、客户、供应商或员工生产数据。
- 不在服务间共享数据库账号、数据库表或可修改的领域对象。
- 不提前建设独立报表服务；跨域报表在核心迁移完成后单独设计。

## 4. 当前基线与目标形态

### 4.1 当前基线

```text
Vue 3 browser application
        |
        | JSON over /api
        v
Spring Boot modular monolith (backend)
        |
        v
MySQL 8.4 / cross_border_erp
```

当前单体中的主要直接耦合包括：

- 订单取消在同一事务中直接释放库存预留。
- 采购单创建直接锁定并修改订单模块的采购需求。
- 确认收货直接更新采购数量并调用库存入库。
- 打包完成直接锁定并消耗库存预留。
- 多个业务实体直接关联 `User`、订单项、采购项或库存分配实体。

这些调用在单体内是正确的强一致实现，但不能原样搬到独立进程。迁移时必须使用本文定义的 seam 和 interface 替换跨包实体引用。

### 4.2 目标拓扑

```text
                         +---------------------+
                         |   Vue 3 Frontend    |
                         +----------+----------+
                                    |
                                    | public HTTP / JSON
                                    v
                         +----------+----------+
                         |    Gateway Service  |
                         |  routing / authn    |
                         +----+----+----+----+-+
                              |    |    |    |
                 +------------+    |    |    +-------------+
                 v                 v    v                  v
        +--------+-------+  +------+----+-----+   +--------+---------+
        | Identity       |  | Order Service   |   | Procurement      |
        | Service        |  | orders / import |   | Service          |
        +--------+-------+  +--------+---------+   +--------+---------+
                 |                   |                      |
                 |                   v                      |
                 |          +--------+---------+            |
                 |          | Warehouse       |<-----------+
                 |          | Service         |
                 |          | receiving / inv |
                 |          +--------+---------+
                 |                   |
                 |                   v
                 |          +--------+---------+
                 +--------->| Fulfillment     |
                            | Service         |
                            | packing         |
                            +--------+---------+
                                     |
          +--------------------------+--------------------------+
          |              RabbitMQ domain events                |
          +-----------------------------------------------------+

Each business service -> its own MySQL database
```

身份箭头表示受保护的服务调用需要身份声明，不表示业务服务可以查询或写入认证数据库。

## 5. 服务职责与深模块接口

每个可部署服务是一个独立模块。它对外只暴露完成业务所需的较小 interface，把权限、幂等、事务、状态机和错误处理隐藏在 implementation 内。调用方不得通过共享实体、共享 Repository 或跨库 SQL 绕过 interface。

### 5.1 Gateway Service

职责：

- 暴露唯一公共入口，默认端口 `8080`。
- 根据现有 `/api/**` 路径把请求转发到对应服务。
- 处理统一跨域、请求大小、追踪标识和基础访问令牌校验。
- 把可信身份声明转发给下游；下游仍需执行授权规则。
- 为服务健康状态提供聚合入口，但不聚合业务数据。

不负责：

- 不保存业务数据或刷新令牌。
- 不编排订单取消、收货、入库或打包流程。
- 不在网关中实现角色业务判断。

### 5.2 Identity Service

职责：

- 登录、Access Token、Refresh Token 轮换、退出和登录失败锁定。
- 员工、角色和账号状态。
- 开发环境演示老板账号的安全创建。
- 提供令牌验证所需的公开密钥或受保护的身份元数据 interface。

数据所有权：

- `app_users`
- `roles`
- `user_roles`
- `refresh_sessions`
- 认证审计记录
- 本服务的 `outbox_events` 与 `inbox_events`

其他服务只保存 `actorId`、必要的显示名称快照和角色声明，不建立指向认证数据库的外键。

### 5.3 Order Service

职责：

- 销售渠道、店铺、订单、订单项和订单业务事件。
- 订单查询、履约阶段投影、负责人和异常处理。
- 采购需求的提出、撤回和转换状态。
- CSV 订单预检、确认导入和安全错误分页。
- 发起订单取消流程并维护取消状态。
- 客户电话和完整地址的最终数据所有权与角色裁剪。

数据所有权：

- `sales_channels`
- `sales_stores`
- `sales_orders`
- `sales_order_items`
- `order_status_events`
- `order_procurement_requests`
- `order_import_batches`
- `order_import_rows`
- `order_import_errors`
- 本服务的流程状态、`outbox_events` 与 `inbox_events`

订单服务不读取库存、采购或打包数据库。履约进度由本服务保存的事件投影计算；如需要实时明细，通过内部只读 interface 查询所有者服务。

### 5.4 Procurement Service

职责：

- 供应商档案。
- 采购单、采购单项、采购来源分配和采购事件。
- 一条采购需求一次性完整转单的业务规则。
- 采购数量、已收数量和采购状态投影。
- 为仓储提供采购单及采购项的安全只读快照。

数据所有权：

- `suppliers`
- `purchase_orders`
- `purchase_order_items`
- `purchase_order_allocations`
- `purchase_order_events`
- 订单采购需求的只读投影或转换凭证
- 本服务的 `outbox_events` 与 `inbox_events`

采购服务只接收订单号、订单项 ID、SKU、需求数量等采购所需字段，永远不接收客户电话或完整地址。

### 5.5 Warehouse Service

收货与库存保留在同一个服务，因为“确认收货、生成库存批次、记录库存流水和自动分配”必须继续由一个本地数据库事务完成。

职责：

- 国内包裹登记、扫码定位、取消和收货记录。
- 合格、破损、错货数量核对。
- 库存批次、不可变流水、预留、释放、消耗和调整。
- 收货自动入库与历史补偿幂等入库。
- 订单取消释放库存和打包库存消耗的仓储端状态机。
- MySQL 行锁、版本检查和防超收、防超分配约束。

数据所有权：

- `domestic_packages`
- `goods_receipts`
- `goods_receipt_items`
- `unexpected_receipt_items`
- `receiving_events`
- `inventory_lots`
- `inventory_movements`
- `inventory_allocations`
- 采购来源、订单需求和商品标识的必要只读投影
- 本服务的命令幂等记录、`outbox_events` 与 `inbox_events`

Warehouse Service 不保存客户电话或完整地址。采购和订单信息只保存完成入库、分配与追溯所需的稳定标识和数量快照。

### 5.6 Fulfillment Service

职责：

- 待打包任务投影。
- 打包任务、箱子、箱内商品和打包事件。
- 多箱数量校验和打包流程状态机。
- 发起库存消耗流程，并在仓储确认后完成不可变打包记录。

数据所有权：

- `packing_tasks`
- `packing_boxes`
- `packing_box_items`
- `packing_events`
- 订单和库存预留的必要只读投影
- 本服务的流程状态、`outbox_events` 与 `inbox_events`

打包服务不直接修改库存表。箱内商品记录保存 `orderItemId`、`allocationId` 和数量快照，不建立跨数据库外键。

## 6. 数据所有权规则

本地开发使用一个 MySQL 8.4 实例，但建立五个独立逻辑数据库和独立账号：

| 服务 | 数据库 | 默认本地端口 |
| --- | --- | ---: |
| Identity Service | `cross_erp_identity` | 8081 |
| Order Service | `cross_erp_order` | 8082 |
| Procurement Service | `cross_erp_procurement` | 8083 |
| Warehouse Service | `cross_erp_warehouse` | 8084 |
| Fulfillment Service | `cross_erp_fulfillment` | 8085 |

约束：

1. 每个数据库账号只拥有对应数据库权限。
2. 每个服务维护自己的 Flyway 历史和迁移版本。
3. 禁止跨数据库外键、`JOIN`、JPA 关联和 Repository 引用。
4. 跨服务引用使用所有者生成的稳定 ID，并保存必要快照。
5. 服务不得直接修改其他服务的投影；投影只能由对应事件消费者更新。
6. 演示数据由各服务独立初始化，使用稳定业务键保证重复启动不重复创建。

## 7. 公共 HTTP 路由

公开路径继续由 Gateway Service 暴露。路径所有者如下：

| 路径前缀 | 所有者 |
| --- | --- |
| `/api/auth/**` | Identity Service |
| `/api/orders/**` | Order Service；其中库存覆盖子路径由网关精确路由到 Warehouse Service |
| `/api/order-imports/**` | Order Service |
| `/api/suppliers/**` | Procurement Service |
| `/api/purchase-orders/**` | Procurement Service |
| `/api/domestic-packages/**` | Warehouse Service |
| `/api/goods-receipts/**` | Warehouse Service |
| `/api/inventory/**` | Warehouse Service |
| `/api/packing/**` | Fulfillment Service |

`docs/api-reference.md` 仍是公共 interface 的来源。网关只做路由，不把多个服务的业务响应拼成一个隐式聚合接口。

## 8. 内部同步接口

同步接口用于调用方必须在当前请求内获得结果的只读查询或幂等命令。所有内部路径使用 `/internal/**`，不通过公网暴露。

| 提供方 | interface | 主要调用方 | 用途 |
| --- | --- | --- | --- |
| Identity | `GET /internal/users/{id}` | 各业务服务 | 获取员工安全显示摘要 |
| Order | `GET /internal/orders/{id}/snapshot` | Warehouse、Fulfillment | 获取订单项、数量和当前状态快照 |
| Order | `POST /internal/procurement-requests/claims` | Procurement | 原子认领一组完整采购需求 |
| Order | `POST /internal/procurement-requests/claims/{id}/release` | Procurement | 采购单创建失败时释放认领 |
| Procurement | `GET /internal/purchase-orders/{id}/receiving-snapshot` | Warehouse | 获取允许收货的采购项和剩余数量 |
| Warehouse | `GET /internal/orders/{id}/inventory-coverage` | Order、Fulfillment | 获取预留、消耗和缺口摘要 |
| Warehouse | `POST /internal/orders/{id}/release-reservations` | Order | 幂等释放未消耗库存，已消耗时拒绝 |
| Warehouse | `POST /internal/packing-consumptions` | Fulfillment | 幂等请求消耗指定库存预留 |

内部命令必须具有：

- 调用方生成的 `idempotencyKey`。
- `correlationId` 和 `traceId`。
- 明确的版本或期望状态。
- 可区分的业务错误码。
- 有限超时，不执行无限自动重试。

服务间身份使用独立服务凭据，不把浏览器 Refresh Token 或 Cookie 转发到内部网络。具体密钥和令牌格式在认证服务实施前更新 `docs/auth-spec.md` 并单独批准。

## 9. RabbitMQ 领域事件

### 9.1 拓扑

- Topic exchange：`cross-erp.domain.v1`。
- 每个消费者拥有独立持久队列，禁止多个业务语义共用一个队列。
- 生产者使用 publisher confirm，消息标记为持久化。
- 每个队列配置有限次数的延迟重试和死信队列。
- 死信只由人工确认后重放，禁止无限循环消费。

### 9.2 事件信封

所有事件使用统一信封，业务 payload 由事件版本定义：

```json
{
  "eventId": "UUID",
  "eventType": "warehouse.goods-receipt-confirmed.v1",
  "occurredAt": "2026-11-13T08:00:00Z",
  "producer": "warehouse-service",
  "aggregateType": "goods-receipt",
  "aggregateId": "123",
  "aggregateVersion": 2,
  "correlationId": "UUID",
  "causationId": "UUID",
  "payload": {}
}
```

事件 payload 不包含客户电话、完整地址、密码、令牌、Cookie、数据库凭据或自由文本中的敏感信息。

### 9.3 第一阶段事件目录

| Routing key / eventType | 生产者 | 主要消费者 | 作用 |
| --- | --- | --- | --- |
| `order.created.v1` | Order | Warehouse、Fulfillment | 建立订单需求只读投影 |
| `order.updated.v1` | Order | Warehouse、Fulfillment | 更新允许共享的数量和状态投影 |
| `order.canceled.v1` | Order | Procurement、Warehouse、Fulfillment | 终止后续处理并保留审计 |
| `procurement.purchase-order-created.v1` | Procurement | Order、Warehouse | 完成采购需求转换并建立采购来源投影 |
| `procurement.purchase-order-updated.v1` | Procurement | Warehouse | 更新可收货状态和采购数量 |
| `warehouse.goods-receipt-confirmed.v1` | Warehouse | Procurement、Order | 累计到货并刷新履约投影 |
| `warehouse.inventory-allocation-changed.v1` | Warehouse | Order、Fulfillment | 更新订单库存覆盖和待打包资格 |
| `fulfillment.packing-completed.v1` | Fulfillment | Order、Warehouse | 更新订单履约状态并确认消费链路 |

请求与结果型长流程使用不同事件，不能把命令请求伪装成已经完成的事实事件。例如打包库存消耗分为 `fulfillment.packing-consumption-requested.v1` 和 `warehouse.packing-consumption-completed.v1`。

## 10. Outbox 与 Inbox

### 10.1 Outbox

业务状态和待发布事件在同一个本地事务中写入 `outbox_events`。后台发布器按批次锁定待发送记录，发布成功并收到 RabbitMQ confirm 后标记为已发布。

最低字段：

- `event_id`，全局唯一。
- `aggregate_type`、`aggregate_id`、`aggregate_version`。
- `event_type` 和 JSON payload。
- `occurred_at`、`published_at`、`attempt_count`、`next_attempt_at`。
- `correlation_id`、`causation_id`。

发布器失败可重试。数据库事务回滚时，对应事件也必须回滚。

### 10.2 Inbox

消费者在处理消息时先尝试写入 `inbox_events`，唯一约束为 `(consumer_name, event_id)`。已经成功处理的事件再次投递时直接确认，不重复修改业务数据。

消费者的投影更新、业务状态修改、产生的新 Outbox 事件和 Inbox 完成状态必须位于同一个本地事务。

### 10.3 顺序与版本

- 不依赖不同聚合之间的全局顺序。
- 同一聚合使用 `aggregateVersion` 拒绝旧事件或暂存缺失版本。
- 事件 schema 只做向后兼容新增；破坏性变化创建新的事件版本。
- 消费者不得用数据库自增 ID 判断跨服务事件顺序。

## 11. 关键业务流程

### 11.1 创建采购单

```text
Procurement -> Order: claim complete procurement requests (idempotent)
Order: lock requests and create time-limited claim
Procurement: create purchase order in local transaction + outbox
Procurement -> RabbitMQ: purchase-order-created.v1
Order consumes event: mark requests CONVERTED
Warehouse consumes event: create purchase-source projection
```

若采购单本地事务失败，Procurement 调用 Order 释放 claim。claim 超时后可由 Order 安全释放。一个采购需求只能被一个有效 claim 持有，且采购数量必须等于分配数量合计。

### 11.2 确认收货并自动入库

```text
Warehouse -> Procurement: read receiving snapshot
Warehouse local transaction:
  lock package and projected purchase source
  validate accepted / damaged / wrong quantities
  append immutable receipt
  create inventory lots and movements
  allocate eligible accepted inventory
  write outbox goods-receipt-confirmed.v1
Warehouse -> RabbitMQ
Procurement consumes event: update received quantity and purchase status
Order consumes event: update fulfillment projection
```

收货记录和库存入库仍由一个 Warehouse 本地事务保证。历史补偿继续使用 receipt ID 和 posting key 保证重复调用不重复入库。

### 11.3 订单取消与释放库存

公开取消路径保持 `/api/orders/{orderId}/cancel`。迁移完成前，原子取消逻辑继续留在旧单体；Order 与 Warehouse 均迁移后才切换为以下幂等编排：

```text
Order -> Warehouse: release-reservations(idempotencyKey, orderId, itemIds)
Warehouse:
  reject when any allocation is CONSUMED
  otherwise release RESERVED allocations in one local transaction
  return released allocation summary
Order local transaction:
  withdraw unconverted procurement requests
  cancel items and order
  append order event and order.canceled.v1 outbox
```

如果 Warehouse 已释放而 Order 提交失败，同一个幂等键重试会返回相同释放结果，Order 可继续完成取消。该流程不使用跨库事务。

### 11.4 完成打包与消耗库存

```text
Fulfillment local transaction:
  validate boxes and allocation coverage
  move packing task DRAFT -> COMPLETING
  write packing-consumption-requested.v1 outbox
Warehouse consumes request:
  lock allocations and lots
  idempotently consume RESERVED allocations
  append inventory movements
  emit packing-consumption-completed.v1 or rejected.v1
Fulfillment consumes result:
  COMPLETING -> COMPLETED or back to DRAFT with failure reason
  emit packing-completed.v1
Order consumes completed event: update fulfillment projection
```

`COMPLETED` 打包记录仍不可修改。用户重复提交完成命令只返回同一个流程结果，不创建第二次库存消耗。

## 12. 一致性与故障处理

| 场景 | 处理方式 |
| --- | --- |
| RabbitMQ 暂时不可用 | 业务本地事务可提交；Outbox 保留事件并重试发布 |
| 消息重复投递 | Inbox 唯一约束与业务幂等键阻止重复处理 |
| 消费者处理失败 | 有限重试，超过阈值进入死信队列并告警 |
| 下游同步查询超时 | 返回明确的暂时不可用错误，不回退为跨库查询 |
| 事件乱序 | 使用聚合版本拒绝旧事件或等待缺失版本 |
| 投影暂时落后 | 页面标识更新时间；命令仍由数据所有者执行最终校验 |
| 服务重启 | 未发布 Outbox、处理中 Inbox 和流程状态可继续恢复 |
| 部分服务迁移 | 网关按路径把未迁移接口继续路由到 legacy `backend` |

所有重试必须有次数、退避和可观测状态。不得捕获异常后假装成功，也不得通过删除消息跳过业务冲突。

## 13. 安全与隐私

- 浏览器只访问 Gateway Service。
- Access Token 保持短时有效；Refresh Token Cookie 只发送到认证路径。
- 目标认证方案使用 Identity 私钥签名、其他服务使用公钥校验，避免共享签名私钥。
- Gateway 完成认证后，下游仍根据角色和资源状态授权，不能只信任路由。
- 内部接口使用服务身份、网络隔离和最小权限。
- Procurement、Warehouse、Fulfillment 的接口、事件、日志和数据库投影均不包含客户电话与完整地址。
- 日志不记录密码、JWT、Refresh Token、Cookie、数据库密码或真实业务数据。
- RabbitMQ 管理端口仅绑定本机开发地址，生产环境不公开。
- 各 MySQL 用户只拥有自己的数据库权限。

认证拆分实施前必须更新并重新批准 `docs/auth-spec.md`，包括签名算法、密钥轮换、内部服务身份和网关失败行为。

## 14. 可观测性

每个入口请求生成或接受 `traceId`，跨同步调用和事件传播。事件同时携带 `correlationId` 与 `causationId`，用于还原一次订单履约操作的完整链路。

最低观测项：

- HTTP 请求数量、延迟和错误码。
- 数据库连接与事务失败。
- Outbox 待发布数量、最老事件年龄和失败次数。
- RabbitMQ 队列深度、重试和死信数量。
- Inbox 重复消息数量和消费者延迟。
- 订单取消、收货入库、库存分配和打包完成的业务结果计数。

日志使用结构化字段，敏感字段在进入日志前裁剪。健康检查区分存活和就绪；RabbitMQ 或数据库不可用时服务不能报告为就绪。

## 15. 目标仓库结构

```text
cross-border-erp/
├── pom.xml
├── backend/                         # 迁移期间保留的 legacy 单体
├── services/
│   ├── gateway-service/
│   ├── identity-service/
│   ├── order-service/
│   ├── procurement-service/
│   ├── warehouse-service/
│   └── fulfillment-service/
├── contracts/
│   ├── events/                      # JSON Schema 与事件示例
│   └── internal-http/               # 内部 interface 文档
├── frontend/
├── infrastructure/
│   ├── mysql/
│   └── rabbitmq/
├── docs/
└── docker-compose.yml
```

根 Maven 工程只统一 Java、Spring Boot、Spring Cloud 和插件版本。业务领域类不得放进共享 Maven 模块。事件消费者使用本服务拥有的 DTO，`contracts` 作为版本化契约而不是共享可修改实体库。

## 16. 渐进迁移顺序

| 阶段 | 交付内容 | 切换门槛 |
| --- | --- | --- |
| 1 | 根 Maven 多模块、Gateway、服务健康检查 | 所有骨架独立构建，legacy 仍可完整运行 |
| 2 | Identity Service | 认证契约测试、登录锁定和令牌轮换测试通过 |
| 3 | Order Service 与 CSV 导入 | 订单 API、隐私裁剪、导入和取消前置逻辑通过 |
| 4 | Procurement Service | 完整转单、并发认领和事件投影测试通过 |
| 5 | Warehouse Service | 收货自动入库、补偿幂等和 MySQL 并发测试通过 |
| 6 | Fulfillment Service | 多箱校验、异步库存消耗和重复完成测试通过 |
| 7 | 前端与网关完整切换 | 浏览器关键路径验收通过 |
| 8 | 清理 legacy backend | 全部公共契约、事件和数据迁移验证完成 |

每一阶段采用 Strangler 路由：已迁移路径由 Gateway 转发到新服务，未迁移路径继续访问 `backend`。禁止一次性复制全部代码后同时切换。

## 17. 测试与验收策略

每个服务至少包含：

- 应用 interface 测试：控制器、权限、错误码和 DTO。
- 数据库迁移测试：服务自己的 Flyway 脚本可以从空库执行。
- MySQL 8.4 集成测试：行锁、唯一约束和并发冲突。
- 事件契约测试：事件信封、版本、必填字段和敏感字段禁入。
- Outbox/Inbox 测试：重复投递、发布失败、消费失败和恢复。
- 跨服务流程测试：订单到采购、收货、入库、分配和打包。

项目级交付检查：

```text
Maven 全模块测试
MySQL 8.4 集成验证
RabbitMQ 重复与重试验证
前端 pnpm type-check
前端 pnpm build
浏览器人工点击验收
```

## 18. 已批准决策与待实施设计

已批准：

- 六个部署服务：Gateway、Identity、Order、Procurement、Warehouse、Fulfillment。
- 收货与库存属于同一个 Warehouse Service。
- 引入 RabbitMQ。
- 跨服务使用本地事务、Outbox/Inbox 和幂等处理，不使用分布式事务。
- legacy `backend` 保留到全部迁移验收完成。
- 本地使用 Docker Compose 服务名，不引入 Nacos。

进入对应实现前仍需逐项批准：

- Identity 的非对称签名、密钥轮换和服务身份细节。
- 每个服务的 Flyway V1 数据表和历史数据迁移方式。
- 采购需求 claim 的超时时间与恢复命令。
- 订单取消和打包完成流程新增中间状态的公共响应语义。
- RabbitMQ 重试次数、退避间隔、死信告警和人工重放流程。
