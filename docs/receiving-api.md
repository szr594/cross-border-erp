# 收货模块 API 规格

基础路径为 `/api`。全部接口要求有效 Access Token，错误响应沿用统一 API 错误格式。

## 权限

- 所有已登录员工可以查看包裹和收货历史。
- 只有 `OWNER` 和 `WAREHOUSE` 可以登记包裹、确认收货或取消未收货包裹。
- 收货 DTO 不返回客户电话、完整地址或采购来源中的原始载荷。
- 权限由后端执行，前端隐藏按钮不构成授权。

## 查询接口

### `GET /api/domestic-packages`

查询参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `page` | 1 | 从 1 开始 |
| `size` | 20 | 最大 100 |
| `keyword` | 空 | 物流单号或采购单号 |
| `supplierId` | 空 | 供应商 ID |
| `status` | 空 | 包裹状态 |
| `sort` | `createdAt,desc` | 服务端白名单排序 |

列表项返回包裹号、物流单号、承运商、采购单、供应商、状态、累计验货进度和收货时间摘要。

### `GET /api/domestic-packages/lookup`

使用 `trackingNumber` 精确定位包裹。不存在时返回 HTTP 404，不自动创建包裹；若不同承运商存在相同单号则返回 HTTP 409，要求用户从列表选择。

### `GET /api/domestic-packages/{packageId}`

返回包裹摘要、采购单摘要、采购商品的采购/合格/破损/剩余数量、收货批次摘要和 `availableActions`。

### `GET /api/domestic-packages/{packageId}/events`

分页返回收货事件，默认按 `occurredAt desc, id desc` 排序。

### `GET /api/goods-receipts/{receiptId}`

返回单次已确认收货批次及其合格、破损、错货明细。响应只读。

## 命令接口

### `POST /api/domestic-packages`

仅 `OWNER`、`WAREHOUSE`：

```json
{
  "purchaseOrderId": 21,
  "trackingNumber": "SF1234567890",
  "carrier": "SF_EXPRESS",
  "expectedAt": "2026-11-05T08:00:00Z",
  "note": "供应商第一箱"
}
```

只能关联非草稿、非取消采购单。同一 `(carrier, trackingNumber)` 不得重复。

### `POST /api/domestic-packages/{packageId}/receipts`

仅 `OWNER`、`WAREHOUSE`：

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
      "quantity": 2,
      "note": "无法匹配采购商品"
    }
  ],
  "note": "第一箱验收"
}
```

服务端在同一事务锁定包裹和采购商品、重新计算剩余数量、写入收货事实、累计合格数量并推进采购状态。任一明细失败时整个请求失败。

相同幂等键和相同内容重复提交时返回原结果；相同键但内容不同返回 HTTP 409。

响应返回收货批次、最新包裹状态、采购单状态、商品累计/剩余数量以及 `inventoryPosted`。确认收货会在同一事务将合格品写入可用库存批次、将破损品写入隔离库存批次；全部成功时该字段为 `true`。

### `POST /api/domestic-packages/{packageId}/cancel`

仅 `OWNER`、`WAREHOUSE`：

```json
{
  "reason": "物流单登记错误",
  "version": 0
}
```

仅未产生确认收货事实的包裹可以取消。取消保留包裹和事件，不物理删除。

## 状态与数量

- 包裹状态：`EXPECTED`、`RECEIVING`、`RECEIVED`、`EXCEPTION`、`CANCELED`。
- 收货批次状态：`CONFIRMED`。
- `purchase_order_items.received_quantity` 只累计合格数量。
- 采购单是否全部验完按合格数量加破损数量判断。
- 错货不增加采购商品合格数量，也不进入库存。
- 短缺是动态结果，客户端不能直接提交。

## 状态码与错误码

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| 400 | `RECEIPT_INVALID` | 请求格式、数量或备注不合法 |
| 401 | `AUTHENTICATION_REQUIRED` | 未认证或令牌无效 |
| 403 | `RECEIVING_FORBIDDEN` | 当前角色无写权限 |
| 404 | `DOMESTIC_PACKAGE_NOT_FOUND` | 包裹不存在 |
| 404 | `PURCHASE_ORDER_ITEM_NOT_FOUND` | 采购商品不存在 |
| 409 | `TRACKING_NUMBER_EXISTS` | 物流单号已经登记 |
| 409 | `TRACKING_NUMBER_AMBIGUOUS` | 不同承运商存在相同物流单号 |
| 409 | `RECEIPT_QUANTITY_CONFLICT` | 累计验货数量超过采购数量 |
| 409 | `RECEIPT_VERSION_CONFLICT` | 包裹被其他操作更新 |
| 409 | `RECEIPT_IDEMPOTENCY_CONFLICT` | 幂等键对应不同请求 |
| 409 | `PURCHASE_ORDER_NOT_RECEIVABLE` | 采购单状态不允许收货 |

## 并发与安全

- 包裹使用乐观锁，采购商品确认收货时使用数据库行锁。
- MySQL 8.4 必须验证两个操作同时确认同一商品时不会超收。
- DTO 使用显式字段映射，禁止直接序列化实体。
- 日志不得记录认证信息、客户隐私或完整请求载荷。
- 已确认收货批次不提供编辑、删除或冲销接口。
