# 订单模块 API 规格

基础路径为 `/api`。除登录和刷新外，全部接口要求有效 Access Token。错误结构沿用 `auth-spec.md` 定义的统一 API 错误格式。

## 权限约定

- 所有已登录员工暂时可以查看全部订单。
- 只有 `OWNER` 可以取消订单和修改负责人。
- `PROCUREMENT` 角色的响应 DTO 必须移除电话、详细地址、邮编和原始载荷。
- 权限由后端执行；前端隐藏操作不构成授权。

## 列表与查询

### `GET /api/orders`

查询参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `page` | 1 | 从 1 开始 |
| `size` | 20 | 最大 100 |
| `keyword` | 空 | 订单号、商品名称或 SKU |
| `storeId` | 空 | 店铺 |
| `responsibleUserId` | 空 | 负责人 |
| `externalStatus` | 空 | 外部标准状态 |
| `stage` | `ACTIONABLE` | 履约阶段或合成筛选 |
| `orderedFrom`/`orderedTo` | 空 | ISO-8601 时间 |
| `exceptionOnly` | false | 只看异常 |
| `sort` | `requestedShipAt,asc` | 白名单排序字段 |

响应：

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "summary": {
    "pending": 0,
    "waitingProcurement": 0,
    "readyForPacking": 0,
    "exception": 0
  }
}
```

列表项不包含电话、地址行或 `sourcePayload`。

### `GET /api/orders/{orderId}`

返回订单基本信息、角色裁剪后的客户信息、商品进度、采购/包裹/打包摘要和 `availableActions`。

`availableActions` 由后端根据角色和业务事实生成，例如：

```json
["CHANGE_RESPONSIBLE", "REQUEST_PROCUREMENT", "OPEN_EXCEPTION"]
```

客户端不得根据阶段自行放开写操作。

### `GET /api/orders/{orderId}/fulfillment`

按订单商品返回：

```text
requiredQuantity
inventoryAllocatedQuantity
procurementRequestedQuantity
procurementInProgressQuantity
receivedQuantity
missingQuantity
ready
```

### `GET /api/orders/{orderId}/events`

分页返回业务事件。默认按 `occurredAt desc, id desc` 排序。

### `GET /api/orders/filter-options`

返回当前用户可使用的店铺、负责人、外部状态和履约阶段选项。

## 命令接口

### `PUT /api/orders/{orderId}/responsible-user`

仅老板：

```json
{
  "userId": 12,
  "reason": "店铺运营调整",
  "version": 3
}
```

成功返回更新后的负责人摘要和版本。版本冲突返回 HTTP 409。

### `POST /api/orders/{orderId}/procurement-requests`

```json
{
  "items": [
    { "orderItemId": 101, "quantity": 20 }
  ],
  "note": "库存不足，申请采购",
  "version": 3
}
```

服务端在同一事务重新计算缺口。任一商品无效时整个请求失败，不允许部分成功。

### `GET /api/orders/{orderId}/procurement-requests`

老板、运营和采购可分页查看订单采购申请，默认按申请时间倒序。响应包含订单商品摘要、申请数量、状态、申请/撤回人员、时间、备注和乐观锁版本。

仅 `REQUESTED` 状态返回 `canWithdraw: true`；`CONVERTED` 和 `WITHDRAWN` 不允许撤回。仓库和打包角色返回 HTTP 403。

### `POST /api/orders/{orderId}/procurement-requests/{requestId}/withdraw`

```json
{
  "reason": "改用现有库存",
  "version": 1
}
```

只允许撤回 `REQUESTED`；已转正式采购时返回 HTTP 409。

### `POST /api/orders/{orderId}/exceptions`

```json
{
  "reason": "客户要求变更商品规格",
  "version": 3
}
```

### `POST /api/orders/{orderId}/exceptions/resolve`

```json
{
  "resolution": "已确认继续按原规格发货",
  "version": 4
}
```

### `POST /api/orders/{orderId}/cancel`

仅老板：

```json
{
  "reason": "外部平台订单已取消",
  "version": 3
}
```

若存在需要人工处理的正式采购、已收货或已打包事实，返回 HTTP 409，并在错误详情中返回不含敏感信息的阻塞项代码。

## CSV 导入

### `POST /api/order-imports/preview`

仅老板。以 multipart 字段 `file` 上传 CSV 后解析、校验并返回预检结果，不写订单业务表。限制为 UTF-8、5 MB 和 10000 行，详细模板见 `order-import-spec.md`。

### `POST /api/order-imports/{importId}/confirm`

仅老板。确认24小时内、零错误的预检批次并执行整批事务化导入。重复确认已完成批次不会重复创建订单。

### `GET /api/order-imports/{importId}`

返回导入状态、成功数、失败数和创建时间。

### `GET /api/order-imports/{importId}/errors`

分页返回行号、字段、错误码和安全错误描述。不得回显密码、令牌或超出模板的原始文件内容。

模板 v1、必填列、重复订单策略和错误码已经在 `order-import-spec.md` 中确认并实现。

## 状态码

| 状态码 | 使用场景 |
| --- | --- |
| 200 | 查询或命令成功 |
| 201 | 创建采购申请、异常或导入预检 |
| 400 | 格式、枚举或字段校验失败 |
| 401 | 未认证或 Access Token 无效 |
| 403 | 角色无权操作或查看敏感字段 |
| 404 | 订单、商品或申请不存在 |
| 409 | 版本冲突、缺口变化、已消耗库存阻止取消 |
| 422 | CSV 业务数据不能导入 |

## 安全要求

- 查询使用参数绑定；排序字段使用服务端白名单。
- DTO 显式映射，禁止直接序列化实体或原始 JSON。
- CSV 预检限制文件大小、行数、字符编码和允许列。
- 导出或错误 CSV 中以 `= + - @` 开头的文本需要防止电子表格公式注入。
- 日志不记录完整客户地址、电话、上传文件内容或认证信息。
