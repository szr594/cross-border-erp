# 收货模块规格（待确认）

## 1. 目标与边界

收货模块承接已下单采购单的国内包裹，记录包裹到仓、分次验货以及合格、破损、错货结果，并把可用数量交给后续库存模块。

MVP 数据链：

```text
采购单 -> 国内包裹 -> 收货批次 -> 验货明细
   |          |           |-> 合格数量 -> 库存批次（库存模块）
   |          |           |-> 破损/错货 -> 收货异常
   |          |-> 多次收货
   |-> 多个包裹
```

收货模块负责：

- 登记包裹的国内物流单号、承运商和关联采购单。
- 通过物流单号或采购单号定位待收货商品。
- 支持一个包裹分多次收货，并保存每次操作人和时间。
- 记录采购商品的合格、破损数量，以及无法匹配采购商品的错货。
- 在事务内累计采购商品收货数量并推进采购单状态。
- 保存不可覆盖的收货事件，向库存模块提供已确认合格数量。

收货模块不负责：

- 修改采购数量、供应商或采购价格。
- 直接分配销售订单库存。
- 处理国际物流、报关、运费和供应商退款。
- 让前端直接提交采购单状态或库存数量。
- 物理删除已确认的收货事实。

## 2. 与现有项目的衔接

当前实现已经提供以下边界：

- `docs/architecture.md:26` 将国内包裹和收货记录划入 `receiving` 模块。
- `docs/mvp-spec.md:17` 要求记录合格、破损和错货数量。
- `V3__purchase_schema.sql:45-66` 已在采购商品中保存 `purchased_quantity` 和 `received_quantity`。
- `PurchaseOrderStatus.java:6-7` 已定义 `PARTIALLY_RECEIVED` 和 `RECEIVED`。
- `frontend/src/router/index.ts:38` 已注册 `/receiving` 路由。
- `frontend/src/views/receiving/ReceivingView.vue` 当前只是占位页面。

后续实现不得修改 V3；新增表和约束统一放入 Flyway V4。

## 3. 建议确认的业务决策

1. 一张采购单允许对应多个国内包裹。
2. 一个国内包裹只关联一张采购单，避免跨采购单验货和追溯复杂度。
3. 一个包裹允许分多次收货；每次确认生成一张不可直接编辑的收货批次。
4. `purchase_order_items.received_quantity` 表示累计合格数量，不包含破损和错货。
5. 短缺数量不手工录入，按采购数量减累计已验数量动态计算。
6. 只有 `ORDERED`、`PARTIALLY_RECEIVED` 状态的采购单可以登记或确认收货。
7. `OWNER` 和 `WAREHOUSE` 可以执行收货；其他已登录员工仅查看。
8. 确认收货后立即更新采购累计和状态。库存模块上线后，在同一事务创建合格品库存批次。
9. 库存模块上线前，不伪造库存记录；收货接口明确返回 `inventoryPosted: false`。
10. 已确认批次不允许普通编辑。冲销能力在库存批次设计完成后单独审批，不纳入第一版。

## 4. 状态模型

### 4.1 国内包裹状态

| 状态 | 中文名称 | 含义 |
| --- | --- | --- |
| `EXPECTED` | 待到货 | 已登记物流信息，尚未开始收货 |
| `RECEIVING` | 收货中 | 已确认至少一次收货，仍有商品未验完 |
| `RECEIVED` | 已收货 | 采购商品数量已全部验完 |
| `EXCEPTION` | 异常 | 存在破损、错货或超量等待处理事项 |
| `CANCELED` | 已取消 | 物流单登记错误且尚未产生确认收货事实 |

### 4.2 收货批次状态

第一版采用 `CONFIRMED` 单状态。提交接口一次完成校验和确认，不保存半成品草稿，避免重复确认和草稿并发覆盖。

### 4.3 采购单状态联动

采购状态由收货服务根据收货事实推进，客户端不得提交：

```text
累计验货数量 = 累计合格数量 + 累计破损数量
```

- 所有商品累计验货数量为 0：保持 `ORDERED`。
- 任一商品已有验货，但仍有商品未验完：`PARTIALLY_RECEIVED`。
- 所有商品累计验货数量等于采购数量：`RECEIVED`。
- 存在破损或错货时，同时保留异常事件；异常不回退已发生的收货事实。
- `COMPLETED` 由后续库存/采购完结流程设置，不由收货模块直接设置。

超出采购数量的预期商品不允许确认，返回 HTTP 409。多出的实物应作为错货登记。

## 5. 数据库设计

### 5.1 `domestic_packages`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `package_no` | VARCHAR(64) | 系统包裹号，唯一 |
| `purchase_order_id` | BIGINT | 必填，关联采购单 |
| `tracking_number` | VARCHAR(128) | 必填，物流单号 |
| `carrier` | VARCHAR(100) | 可空，承运商 |
| `status` | VARCHAR(32) | 包裹状态 |
| `expected_at` | DATETIME(6) | 可空，预计到仓时间 |
| `first_received_at` | DATETIME(6) | 可空，首次确认收货时间 |
| `completed_at` | DATETIME(6) | 可空，全部验完时间 |
| `created_by` | BIGINT | 登记人 |
| `note` | VARCHAR(500) | 可空 |
| `version` | BIGINT | 乐观锁 |
| `created_at`/`updated_at` | DATETIME(6) | UTC 时间 |

约束：

- `(carrier, tracking_number)` 唯一；承运商未知时使用标准值 `UNKNOWN`，避免 NULL 破坏唯一性。
- 已产生确认收货批次的包裹不得取消或更换采购单。
- 同一采购单可登记多个包裹。

### 5.2 `goods_receipts`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `receipt_no` | VARCHAR(64) | 收货批次号，唯一 |
| `domestic_package_id` | BIGINT | 必填，关联国内包裹 |
| `status` | VARCHAR(32) | 第一版固定 `CONFIRMED` |
| `operator_id` | BIGINT | 收货操作人 |
| `confirmed_at` | DATETIME(6) | 确认时间 |
| `note` | VARCHAR(500) | 可空 |
| `idempotency_key` | VARCHAR(64) | 必填，防止网络重试重复入账 |
| `created_at` | DATETIME(6) | UTC 时间 |

约束：`idempotency_key` 全局唯一。同一个键重复提交相同内容时返回原结果，内容不同时返回 HTTP 409。

### 5.3 `goods_receipt_items`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `goods_receipt_id` | BIGINT | 必填，关联收货批次 |
| `purchase_order_item_id` | BIGINT | 必填，关联采购商品 |
| `accepted_quantity` | INTEGER | 合格数量，默认 0 |
| `damaged_quantity` | INTEGER | 破损数量，默认 0 |
| `note` | VARCHAR(500) | 破损时必填 |

约束：

- 数量均不得小于 0，且两者合计必须大于 0。
- 同一收货批次中一个采购商品只能出现一次。
- 采购商品必须属于包裹所关联的采购单。
- 事务内锁定采购商品并校验累计验货数量不超过采购数量。

### 5.4 `unexpected_receipt_items`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `goods_receipt_id` | BIGINT | 必填，关联收货批次 |
| `description` | VARCHAR(500) | 错货描述 |
| `supplier_sku` | VARCHAR(128) | 可空，实物可识别 SKU |
| `quantity` | INTEGER | 必须大于 0 |
| `note` | VARCHAR(500) | 可空 |

错货不增加采购商品的 `received_quantity`，也不进入可用库存。

### 5.5 `receiving_events`

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `domestic_package_id` | BIGINT | 必填 |
| `goods_receipt_id` | BIGINT | 可空 |
| `event_type` | VARCHAR(64) | 事件类型 |
| `operator_id` | BIGINT | 操作人 |
| `remark` | VARCHAR(500) | 可空 |
| `occurred_at` | DATETIME(6) | UTC 时间 |

事件至少包括：`PACKAGE_REGISTERED`、`RECEIPT_CONFIRMED`、`DAMAGE_RECORDED`、`UNEXPECTED_ITEM_RECORDED`、`PACKAGE_COMPLETED`、`PACKAGE_CANCELED`。

## 6. 核心业务规则

### 6.1 包裹登记

- 只能关联非 `DRAFT`、非 `CANCELED` 的采购单。
- 物流单号去除首尾空格后保存，不改变字母大小写。
- 重复物流单号返回现有包裹摘要，不静默创建第二条记录。
- 包裹登记不改变采购单收货状态。

### 6.2 扫码定位

- 扫描物流单号后精确查询包裹。
- 未找到时，仓库人员可选择采购单并登记新包裹。
- 扫描采购单号时展示该采购单全部包裹和剩余待验数量，不自动创建包裹。
- 前端扫码只负责输入；服务端仍执行完整权限和数量校验。

### 6.3 确认收货

确认操作在单个事务中执行：

1. 校验角色、幂等键和请求格式。
2. 锁定国内包裹、采购单和涉及的采购商品。
3. 重新计算每个商品累计合格、破损和剩余数量。
4. 拒绝超量、重复商品、跨采购单商品或不可收货状态。
5. 创建收货批次、正常明细、错货明细和事件。
6. 将本次合格数量累加到 `purchase_order_items.received_quantity`。
7. 重新计算国内包裹和采购单状态。
8. 库存模块存在时，为本次合格数量创建不可重复的库存批次。

事务失败时不得留下部分明细、采购累计或库存记录。

### 6.4 短缺与异常

```text
商品剩余待验 = 采购数量 - 累计合格数量 - 累计破损数量
```

- 破损和错货必须生成异常事件。
- 分次收货期间的剩余数量只表示“尚未验到”，不自动认定供应商短缺。
- 当仓库明确结束包裹但采购单仍有剩余数量时，需等待后续包裹；第一版不提供“强制短缺结案”。
- 供应商补发继续登记为同一采购单的新包裹。

## 7. API 边界

基础路径为 `/api`，所有接口要求认证。

### 7.1 查询接口

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/domestic-packages` | 分页查询包裹，可按物流单号、采购单、供应商、状态筛选 |
| GET | `/api/domestic-packages/{packageId}` | 包裹详情、采购商品剩余数量及收货历史 |
| GET | `/api/domestic-packages/lookup?trackingNumber=` | 扫码精确定位包裹 |
| GET | `/api/domestic-packages/{packageId}/events` | 分页查询收货事件 |
| GET | `/api/goods-receipts/{receiptId}` | 查询单次收货批次详情 |

### 7.2 命令接口

| Method | Path | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/domestic-packages` | OWNER、WAREHOUSE | 登记国内包裹 |
| POST | `/api/domestic-packages/{packageId}/receipts` | OWNER、WAREHOUSE | 确认一次收货 |
| POST | `/api/domestic-packages/{packageId}/cancel` | OWNER、WAREHOUSE | 取消尚未收货的错误登记 |

确认收货请求示例：

```json
{
  "idempotencyKey": "850ad5ca-2c42-4b12-aac5-66a0c72d51c2",
  "packageVersion": 2,
  "items": [
    {
      "purchaseOrderItemId": 101,
      "acceptedQuantity": 8,
      "damagedQuantity": 1,
      "note": "外箱挤压，1 件破损"
    }
  ],
  "unexpectedItems": [
    {
      "supplierSku": "UNKNOWN-RED",
      "description": "红色未知配件",
      "quantity": 2
    }
  ],
  "note": "第一箱验收"
}
```

响应返回收货批次、最新包裹状态、采购单状态、各采购商品累计/剩余数量以及 `inventoryPosted`。

### 7.3 错误码

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| 400 | `RECEIPT_INVALID` | 数量、明细或备注不合法 |
| 403 | `RECEIVING_FORBIDDEN` | 当前角色无权执行收货 |
| 404 | `DOMESTIC_PACKAGE_NOT_FOUND` | 包裹不存在 |
| 404 | `PURCHASE_ORDER_ITEM_NOT_FOUND` | 采购商品不存在 |
| 409 | `TRACKING_NUMBER_EXISTS` | 物流单号已登记 |
| 409 | `RECEIPT_QUANTITY_CONFLICT` | 累计数量超过采购数量 |
| 409 | `RECEIPT_VERSION_CONFLICT` | 包裹已被其他人更新 |
| 409 | `RECEIPT_IDEMPOTENCY_CONFLICT` | 相同幂等键对应不同请求 |
| 409 | `PURCHASE_ORDER_NOT_RECEIVABLE` | 采购单当前不可收货 |

## 8. 页面边界

### 8.1 `/receiving` 收货工作台

- 顶部扫码/物流单号输入框。
- 按物流单号、采购单号、供应商和包裹状态筛选。
- 表格展示物流单号、采购单、供应商、状态、累计进度、首次收货时间。
- `OWNER`、`WAREHOUSE` 显示“登记包裹”；其他角色只读。
- 扫码成功后进入包裹详情，不在列表页直接写入数量。

### 8.2 `/receiving/packages/:packageId` 包裹详情

- 包裹和采购单摘要。
- 采购商品的采购、累计合格、累计破损、剩余数量。
- 本次收货录入区，默认数量为 0，不自动填满剩余数量。
- 错货明细录入区。
- 收货历史和事件时间线。
- 提交前二次确认合格、破损和错货合计。

### 8.3 `/receiving/receipts/:receiptId` 收货批次详情

- 只读展示单次收货内容、操作人和时间。
- 明确区分合格、破损与错货。
- 第一版不提供编辑或删除按钮。

## 9. 权限矩阵

| 操作 | OWNER | OPERATIONS | PROCUREMENT | WAREHOUSE | PACKING |
| --- | --- | --- | --- | --- | --- |
| 查看包裹和收货历史 | 是 | 是 | 是 | 是 | 是 |
| 登记包裹 | 是 | 否 | 否 | 是 | 否 |
| 确认收货 | 是 | 否 | 否 | 是 | 否 |
| 取消未收货包裹 | 是 | 否 | 否 | 是 | 否 |
| 查看供应商联系方式 | 是 | 是 | 是 | 是 | 否 |

收货响应不需要客户电话或完整地址，DTO 不得通过采购来源嵌套返回这些字段。

## 10. 并发和一致性

- 包裹实体使用乐观锁；确认收货同时对涉及的采购商品执行数据库行锁。
- 两个仓库人员同时确认同一商品时，后提交者必须重新校验剩余数量。
- 幂等键防止按钮连点、浏览器重试和网络超时造成重复入账。
- `received_quantity` 是查询优化字段，必须与已确认收货明细的合格数量总和一致。
- 采购状态、采购累计、收货事实和库存入账必须处于同一事务。
- MySQL 8.4 单独验证并发收货，不能只依赖 H2 测试。

## 11. 验收标准

1. 一张采购单可登记多个包裹，一个包裹不能跨采购单。
2. 同一物流单号不能重复登记。
3. 包裹可分多次收货，累计数量不能超过采购数量。
4. 合格、破损和错货分别保存，错货不增加采购商品合格数量。
5. 短缺数量由事实计算，不能由客户端直接覆盖。
6. 确认收货后采购商品累计和采购单状态正确推进。
7. 只有老板和仓库角色可以执行收货命令。
8. 重复幂等请求不会重复增加数量。
9. 收货失败不会留下部分数据。
10. 并发确认不会超收，冲突返回 HTTP 409。
11. 所有确认操作留下操作人、时间和事件。
12. 页面支持扫码定位、分次录入、详情追溯和只读历史。

## 12. 建议实施顺序

| 步骤 | 产出 | 验证 |
| --- | --- | --- |
| 1. 确认本规格 | 冻结关联关系、状态和权限 | 人工评审 |
| 2. 数据层 | Flyway V4、实体、Repository | H2 迁移测试、MySQL 8.4 迁移测试 |
| 3. 查询层 | 包裹列表、详情、扫码定位、事件分页 | 查询和权限测试 |
| 4. 命令层 | 登记包裹、确认收货、取消错误登记 | 事务、幂等和角色测试 |
| 5. 并发验证 | MySQL 行锁和超收冲突 | MySQL 8.4 集成测试 |
| 6. 演示数据 | 虚构包裹和分次收货数据 | 重复启动幂等测试 |
| 7. 前端 | 收货工作台、包裹详情、批次详情 | 类型检查、构建、浏览器点击验收 |
| 8. 库存联动 | 合格品库存批次 | 库存规格批准后实施 |

## 13. 实现前必须确认

1. 是否确认“一个包裹只对应一张采购单；一张采购单可有多个包裹”？
2. 是否确认采购单 `RECEIVED` 按“合格 + 破损均已验完”判断，而 `received_quantity` 只累计合格品？
3. 是否确认第一版不做已确认收货的冲销/改数，待库存模块设计后再增加？
4. 是否确认收货写权限仅限 `OWNER` 和 `WAREHOUSE`？
5. 是否确认库存模块完成前只更新采购收货事实，不临时伪造库存？

