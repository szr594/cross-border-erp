# 库存模块规格（设计稿）

## 1. 目标与边界

库存模块承接已经确认的收货事实，把合格品形成可追溯库存批次，并依据采购需求来源为销售订单分配库存，使满足数量的订单进入“已齐货”。

MVP 业务链路：

```text
收货批次明细
  |-> 合格数量 -> 可用库存批次 -> 订单库存分配 -> 已齐货 -> 打包
  |-> 破损数量 -> 隔离库存批次 -> 人工调整/报损
  |-> 错货数量 -> 收货异常，不进入库存

采购商品 -> 采购分配 -> 采购申请 -> 销售订单商品
                              ^
                              | 自动分配目标
```

库存模块负责：

- 将每条已确认收货明细幂等转换为可追溯库存批次。
- 分开保存合格库存和破损隔离库存。
- 维护库存流水、当前可用数量和订单分配记录。
- 按采购来源自动分配合格库存，并防止超分配。
- 支持释放尚未出库的订单分配和有理由的库存调整。
- 为订单查询和后续打包模块提供只读库存结果。

库存模块不负责：

- 修改采购数量、收货数量或收货批次。
- 将错货自动识别为正常商品。
- 处理多仓、多库位、调拨、盘点任务、批次成本、保质期或序列号。
- 直接完成打包或发货；实际扣减由后续打包模块调用库存边界完成。
- 允许前端直接修改批次数量、可用数量或订单履约阶段。

## 2. 与现有模块的衔接

现有数据已经提供以下追溯链：

```text
goods_receipt_items.purchase_order_item_id
  -> purchase_order_allocations.purchase_order_item_id
  -> order_procurement_requests.id
  -> order_procurement_requests.order_item_id
  -> sales_order_items.id
```

因此第一版自动分配不依赖字符串 SKU 猜测。商品名称、销售 SKU 和供应商 SKU 只用于展示与检索，采购分配关系才是自动分配依据。

关键约定：

1. `purchase_order_items.received_quantity` 继续只表示累计合格数量。
2. 每条 `goods_receipt_items` 最多生成一个合格批次和一个破损隔离批次。
3. 收货事务与库存入账最终应在同一事务中完成；库存模块上线前已有的收货明细通过幂等补偿任务入账。
4. `sales_order_items` 不新增可人工维护的“已分配数量”字段；有效分配量由库存分配表汇总。
5. 订单履约阶段继续由查询服务动态计算，库存模块不写死阶段字段。

## 3. 已采用的业务决策

1. 合格品确认收货后自动生成可用库存批次。
2. 破损品生成 `QUARANTINED` 隔离批次，不参与订单分配。
3. 错货继续只保留在收货异常中，不自动入库。
4. 合格库存优先按采购来源自动分配；没有明确来源的剩余数量保留为可用库存。
5. 同一采购商品关联多个销售订单时，按要求发货时间、订单 ID、订单商品 ID 顺序分配。
6. 自动分配不得超过采购分配数量、销售订单商品需求或库存批次可用数量。
7. 已取消订单不分配库存；取消订单时释放尚未消耗的分配。
8. 只有有效的 `RESERVED` 分配能使订单进入“已齐货”。
9. 已被打包消耗的分配不能直接释放或调整，必须由后续冲销流程处理。
10. 所有库存变化必须产生不可覆盖的流水，禁止直接修改余额绕过流水。

## 4. 状态与数量模型

### 4.1 库存批次状态

| 状态 | 中文名称 | 可分配 | 含义 |
| --- | --- | --- | --- |
| `AVAILABLE` | 可用 | 是 | 合格品，仍有可用数量 |
| `ALLOCATED` | 已分配 | 否 | 批次全部可用数量已被订单占用 |
| `QUARANTINED` | 隔离 | 否 | 破损品或人工冻结库存 |
| `DEPLETED` | 已耗尽 | 否 | 已全部出库或调整减少 |
| `CLOSED` | 已关闭 | 否 | 数量归零且业务已结清 |

批次状态由数量和质量状态推导，不接受前端任意切换。

### 4.2 订单分配状态

| 状态 | 中文名称 | 含义 |
| --- | --- | --- |
| `RESERVED` | 已预留 | 已为订单占用，尚未打包消耗 |
| `CONSUMED` | 已消耗 | 已被打包模块确认出库 |
| `RELEASED` | 已释放 | 订单取消或人工释放，数量已归还批次 |

### 4.3 数量恒等式

```text
批次初始数量 = 当前可用数量 + 有效预留数量 + 隔离数量 + 已消耗数量 + 调整减少数量

订单商品有效分配量 = SUM(RESERVED.quantity)
订单商品已消耗量   = SUM(CONSUMED.quantity)
订单商品待分配量   = MAX(订单数量 - 有效分配量 - 已消耗量, 0)
```

破损隔离批次不参与上述订单分配计算。

## 5. 数据库设计

库存第一阶段表结构已由 Flyway V5 创建，包括库存批次、库存分配和库存流水。后续扩展继续通过新的 Flyway 版本演进，不直接修改 V5。

### 5.1 `inventory_lots`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `lot_no` | VARCHAR(64) | 系统批次号，唯一 |
| `goods_receipt_item_id` | BIGINT | 必填，关联收货明细 |
| `purchase_order_item_id` | BIGINT | 必填，便于追溯和查询 |
| `quality_status` | VARCHAR(32) | `ACCEPTED` 或 `DAMAGED` |
| `status` | VARCHAR(32) | 库存批次状态 |
| `initial_quantity` | INTEGER | 入库初始数量，大于 0 |
| `available_quantity` | INTEGER | 当前可用数量，不小于 0 |
| `reserved_quantity` | INTEGER | 当前预留数量，不小于 0 |
| `quarantined_quantity` | INTEGER | 当前隔离数量，不小于 0 |
| `consumed_quantity` | INTEGER | 累计出库数量，不小于 0 |
| `adjusted_out_quantity` | INTEGER | 累计调整减少数量，不小于 0 |
| `received_at` | DATETIME(6) | 继承收货确认时间 |
| `version` | BIGINT | 乐观锁版本 |
| `created_at`/`updated_at` | DATETIME(6) | UTC 时间 |

约束：

- `(goods_receipt_item_id, quality_status)` 唯一，保证补偿重试不会重复入库。
- 合格批次数量取 `accepted_quantity`，破损批次数量取 `damaged_quantity`。
- `DAMAGED` 批次创建时固定为 `QUARANTINED`，`quarantined_quantity = initial_quantity` 且 `available_quantity = 0`。
- 数量恒等式必须同时由服务校验和数据库 CHECK 约束保护。

### 5.2 `inventory_allocations`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `allocation_no` | VARCHAR(64) | 分配号，唯一 |
| `inventory_lot_id` | BIGINT | 必填，关联库存批次 |
| `sales_order_item_id` | BIGINT | 必填，关联销售订单商品 |
| `purchase_order_allocation_id` | BIGINT | 可空；自动分配时记录采购来源 |
| `quantity` | INTEGER | 大于 0 |
| `status` | VARCHAR(32) | `RESERVED`、`CONSUMED`、`RELEASED` |
| `allocated_by` | BIGINT | 可空；系统自动分配时为空 |
| `allocated_at` | DATETIME(6) | 分配时间 |
| `released_by`/`released_at` | BIGINT/DATETIME(6) | 可空 |
| `release_reason` | VARCHAR(500) | 释放时必填 |
| `version` | BIGINT | 乐观锁版本 |

约束：

- 自动分配记录 `(inventory_lot_id, purchase_order_allocation_id)` 唯一。
- 同一销售订单商品的有效分配与已消耗数量合计不得超过订单数量。
- `CONSUMED` 记录不能变为 `RELEASED`。

### 5.3 `inventory_movements`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `movement_no` | VARCHAR(64) | 流水号，唯一 |
| `inventory_lot_id` | BIGINT | 必填 |
| `allocation_id` | BIGINT | 可空 |
| `movement_type` | VARCHAR(32) | 流水类型 |
| `quantity` | INTEGER | 大于 0；方向由类型决定 |
| `available_after` | INTEGER | 操作后可用数量快照 |
| `reserved_after` | INTEGER | 操作后预留数量快照 |
| `operator_id` | BIGINT | 可空；系统操作为空 |
| `reason` | VARCHAR(500) | 调整、释放时必填 |
| `idempotency_key` | VARCHAR(64) | 命令重试键，唯一 |
| `occurred_at` | DATETIME(6) | UTC 时间 |

流水类型至少包括：

- `RECEIPT_ACCEPTED`
- `RECEIPT_DAMAGED`
- `ORDER_ALLOCATED`
- `ALLOCATION_RELEASED`
- `PACKING_CONSUMED`
- `ADJUSTMENT_IN`
- `ADJUSTMENT_OUT`
- `QUARANTINE_RELEASED`

流水只允许追加，不允许更新或删除。

### 5.4 不新增独立 SKU 主数据表的原因

当前系统已经用采购分配关系明确记录“采购商品为哪个销售订单商品采购”。第一版直接沿该关系分配，可以避免用销售 SKU、供应商 SKU 或商品名称做不可靠匹配。

正式建设商品中心、通用现货分配或跨订单复用库存时，再单独设计标准商品、销售 SKU 与供应商 SKU 映射。本次不把该能力塞进库存 MVP。

## 6. 核心业务流程

### 6.1 收货入库

确认收货的同一事务中：

1. 创建收货批次和收货明细。
2. 合格数量大于 0 时，幂等创建 `ACCEPTED` 库存批次和入库流水。
3. 破损数量大于 0 时，幂等创建 `DAMAGED` 隔离批次和流水。
4. 根据该采购商品的采购分配来源计算自动分配目标。
5. 锁定库存批次、采购分配和销售订单商品，按固定顺序创建订单分配。
6. 更新批次余额并追加分配流水。
7. 返回 `inventoryPosted: true`、库存批次号和本次自动分配结果。

任一步失败时，收货、采购累计、库存批次和订单分配必须整体回滚。

### 6.2 历史收货补偿

V5 上线前已经确认且 `inventoryPosted = false` 的收货明细，由受控补偿服务处理：

- 按收货时间、收货明细 ID 顺序分页扫描。
- 依靠 `(goods_receipt_item_id, quality_status)` 唯一约束保证幂等。
- 单条失败不伪造成功标记，记录错误供人工重试。
- 补偿服务不改变原收货数量。

第一版不提供前端批量按钮；仅 `OWNER` 可通过单条重试接口处理失败明细。

### 6.3 自动分配

自动分配候选仅来自当前采购商品的 `purchase_order_allocations`，并过滤：

- 采购申请状态为 `ORDERED`。
- 销售订单未取消。
- 销售订单商品未取消。
- 该采购分配仍有未覆盖数量。
- 销售订单商品仍有待分配数量。

排序规则：

1. 销售订单 `requested_ship_at` 升序，空值最后。
2. 销售订单 ID 升序。
3. 销售订单商品 ID 升序。
4. 采购分配 ID 升序。

每次分配数量取以下最小值：

```text
批次剩余可用数量
采购分配尚未覆盖数量
销售订单商品尚未分配数量
```

没有明确采购来源的剩余库存保留在批次中，不跨订单猜测分配。

### 6.4 释放分配

- 订单取消时，在订单取消事务中锁定并释放全部 `RESERVED` 分配。
- 人工释放仅限 `OWNER`、`WAREHOUSE`，必须填写原因。
- 释放后数量归还原库存批次，并写 `ALLOCATION_RELEASED` 流水。
- 已消耗分配不能释放。
- 释放后订单履约阶段重新动态计算，可能从“已齐货”回退。

### 6.5 库存调整

- `ADJUSTMENT_OUT` 不得超过批次可用数量，不能占用已预留数量。
- `ADJUSTMENT_IN` 只能增加现有批次，不创建无来源批次。
- 隔离库存解除时使用专门的 `QUARANTINE_RELEASED` 命令，不能伪装成普通调整。
- 每次调整必须有幂等键、原因、操作人和时间。
- 第一版不提供删除或覆盖流水能力。

## 7. API 边界设计

历史收货幂等入库补偿、只读查询、人工分配、释放、调整和解除隔离接口均已实现并写入 `api-reference.md`。命令接口通过角色权限、乐观版本和幂等键保护写操作。

### 7.1 查询接口

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/inventory/lots` | 分页查询批次，按商品、采购单、状态、质量筛选 |
| GET | `/api/inventory/lots/{lotId}` | 批次详情、来源、数量和分配摘要 |
| GET | `/api/inventory/lots/{lotId}/movements` | 分页查询库存流水 |
| GET | `/api/inventory/allocations` | 按订单、批次、状态查询分配记录 |
| GET | `/api/orders/{orderId}/inventory-allocations` | 查询订单商品的库存覆盖情况 |

所有已登录员工可以查询，但响应不得包含客户电话或完整地址。

### 7.2 命令接口

| Method | Path | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/inventory/receipts/{receiptId}/post` | OWNER | 对历史收货执行幂等入库补偿 |
| POST | `/api/inventory/lots/{lotId}/allocate` | OWNER、WAREHOUSE | 对明确订单商品执行人工分配 |
| POST | `/api/inventory/allocations/{allocationId}/release` | OWNER、WAREHOUSE | 释放未消耗分配 |
| POST | `/api/inventory/lots/{lotId}/adjustments` | OWNER、WAREHOUSE | 增减可用库存 |
| POST | `/api/inventory/lots/{lotId}/release-quarantine` | OWNER、WAREHOUSE | 将核验合格的隔离数量转为可用 |

收货确认触发的自动入库和自动分配属于内部应用服务调用，不通过前端额外请求。

### 7.3 主要错误码

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| 400 | `INVENTORY_QUERY_INVALID` | 分页、筛选枚举或查询文本不合法 |
| 400 | `INVENTORY_COMMAND_INVALID` | 数量、原因或幂等键不合法 |
| 403 | `INVENTORY_FORBIDDEN` | 当前角色无写权限 |
| 404 | `INVENTORY_LOT_NOT_FOUND` | 库存批次不存在 |
| 404 | `INVENTORY_ALLOCATION_NOT_FOUND` | 分配记录不存在 |
| 409 | `INVENTORY_QUANTITY_CONFLICT` | 可用数量不足或订单已足量 |
| 409 | `INVENTORY_SOURCE_CONFLICT` | 收货明细已入库但请求内容不一致 |
| 409 | `INVENTORY_ALLOCATION_CONSUMED` | 已消耗分配不可释放 |
| 409 | `INVENTORY_IDEMPOTENCY_CONFLICT` | 相同幂等键对应不同请求 |
| 409 | `INVENTORY_VERSION_CONFLICT` | 批次已被其他事务修改 |

## 8. 页面边界

### 8.1 `/inventory` 库存工作台

- 顶部展示可用、已预留、隔离和待处理批次数量。
- 支持按商品关键词、采购单号、批次号、状态和质量筛选。
- 表格展示批次号、商品、采购来源、初始、可用、预留、隔离数量和入库时间。
- 点击整行进入批次详情。
- 第一版不在列表内直接编辑数量。

### 8.2 `/inventory/:lotId` 库存批次详情

- 展示收货批次、国内包裹、采购单和供应商追溯链。
- 展示数量恒等式和当前批次状态。
- 展示订单分配记录及关联订单号，不展示客户敏感信息。
- 分页展示不可覆盖的库存流水。
- `OWNER`、`WAREHOUSE` 根据状态显示调整、释放分配、解除隔离按钮。
- 所有写操作使用独立确认弹窗，要求原因和幂等键。

### 8.3 订单详情库存区

- 每个订单商品展示需求数量、有效预留、已消耗和待分配数量。
- 可以跳转到相关库存批次。
- 库存不足时展示缺口，但不在订单页面直接修改库存。

## 9. 权限矩阵

| 操作 | OWNER | OPERATIONS | PROCUREMENT | WAREHOUSE | PACKING |
| --- | --- | --- | --- | --- | --- |
| 查看库存、流水和分配 | 是 | 是 | 是 | 是 | 是 |
| 历史收货补偿入库 | 是 | 否 | 否 | 否 | 否 |
| 人工分配/释放 | 是 | 否 | 否 | 是 | 否 |
| 库存调整 | 是 | 否 | 否 | 是 | 否 |
| 解除隔离 | 是 | 否 | 否 | 是 | 否 |
| 消耗打包库存 | 内部调用 | 否 | 否 | 否 | PACKING 服务调用 |

后端权限校验是唯一可信边界；前端隐藏按钮只改善体验。

## 10. 并发与一致性

- 所有写命令在事务中按 `inventory_lot.id`、`sales_order_item.id` 升序获取行锁，避免死锁。
- 自动分配必须重新计算批次可用量和订单待分配量，不能信任前端传入余额。
- 两个收货事务同时为同一订单分配时，最多只能分配到订单需求数量。
- 两个调整或分配事务同时操作同一批次时，最多只能使用当前可用数量。
- 幂等键与请求摘要共同防止按钮连点和网络重试产生重复流水。
- 余额字段属于查询优化数据，必须与流水及有效分配汇总一致。
- MySQL 8.4 必须单独验证并发分配、并发调整与收货幂等；H2 测试不能替代这些验证。

## 11. 验收标准

1. 同一收货明细重复入库不会产生第二个同质量批次或重复流水。
2. 合格 8 件、破损 1 件时，生成 8 件可用批次和 1 件隔离批次。
3. 错货不生成库存批次。
4. 自动分配严格遵守采购来源和确定的排序规则。
5. 多个并发请求不能让批次可用量为负数，也不能让订单分配量超过需求。
6. 取消订单会释放未消耗分配并恢复批次可用量。
7. 已消耗分配无法普通释放。
8. 每次入库、分配、释放、调整和消耗都有可追溯流水。
9. 有效分配覆盖全部订单商品后，订单履约阶段变为 `READY_FOR_PACKING`。
10. 前端不显示无权限操作，也不包含客户电话或完整地址。

## 12. 实现进度与后续顺序

已完成：

- Flyway/JPA 数据层、收货入库桥接和幂等历史补偿。
- 自动与人工分配、释放、调整、隔离库存解除及订单取消释放。
- MySQL 8.4 并发验证和覆盖批次、流水、预留、释放、隔离场景的演示数据。
- 库存工作台、批次详情、角色感知的命令弹窗和采购/收货来源追溯。
- 订单详情库存覆盖区，展示需求、有效预留、已消耗、缺口及关联批次。
- Chromium 桌面端和移动端筛选、详情跳转、权限入口、控制台及页面溢出验收。

打包集成：

1. 打包完成通过库存内部服务将 `RESERVED` 分配转为 `CONSUMED`，并逐条记录 `PACKING_CONSUMED` 流水。
2. 消耗事务先按批次 ID、再按分配 ID 升序加锁，批次余额、分配状态、打包任务状态和流水同事务提交。
3. 第一阶段不支持完成后的撤销或冲销；普通库存调整不能修改已消耗分配。
4. 打包模块端到端浏览器验收完成后，进入 CSV 订单导入阶段。
