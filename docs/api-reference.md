# API Reference

## Authentication

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/auth/login` | Authenticate with login name and password |
| POST | `/api/auth/refresh` | Rotate the refresh session and return a new access token |
| GET | `/api/auth/me` | Return the current authenticated employee |
| POST | `/api/auth/logout` | Revoke the refresh session and clear its cookie |

Authentication details and error behavior are defined in `auth-spec.md`.

## Orders

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/orders` | Page and filter the order workspace |
| GET | `/api/orders/filter-options` | Return allowed order filter options |
| GET | `/api/orders/{orderId}` | Return role-filtered order detail |
| GET | `/api/orders/{orderId}/fulfillment` | Return item-level fulfillment progress |
| GET | `/api/orders/{orderId}/events` | Page order business events |
| PUT | `/api/orders/{orderId}/responsible-user` | Change responsible employee; owner only |
| POST | `/api/orders/{orderId}/procurement-requests` | Request procurement for selected order-item shortages |
| POST | `/api/orders/{orderId}/procurement-requests/{requestId}/withdraw` | Withdraw an unconverted request |
| POST | `/api/orders/{orderId}/exceptions` | Open an order exception |
| POST | `/api/orders/{orderId}/exceptions/resolve` | Resolve the current order exception |
| POST | `/api/orders/{orderId}/cancel` | Cancel an order and release reserved inventory; owner only; consumed inventory blocks cancellation |
| POST | `/api/order-imports/preview` | Owner-only multipart CSV preview without writing business orders |
| POST | `/api/order-imports/{importId}/confirm` | Confirm a validated CSV import; owner only |
| GET | `/api/order-imports/{importId}` | Return import status and counts |
| GET | `/api/order-imports/{importId}/errors` | Page safe row-level import errors |

The complete contract, privacy rules, concurrency behavior, and status codes are defined in `order-api.md`. The implemented CSV template, limits, idempotency rules, and persistence boundary are defined in `order-import-spec.md`.

独立 Order 已实现采购需求创建、撤回和取消命令，但生产路由尚未切换。新模式取消必须先完成库存释放；超时返回 `503 ORDER_CANCEL_PENDING`，重复原请求和原键继续恢复。可显式传入 `Idempotency-Key`，省略时由账号、订单、版本和原因派生稳定键。存在未释放的履约执行占用时同样返回可重试状态；已消耗库存仍返回 `409 ORDER_CANCEL_BLOCKED_BY_CONSUMED_INVENTORY`。

## Suppliers

All authenticated employees can view supplier contact details. Only `OWNER` and `PROCUREMENT` can mutate supplier records.

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/suppliers` | Page suppliers by keyword and status |
| GET | `/api/suppliers/{supplierId}` | Return supplier details including contact fields |
| POST | `/api/suppliers` | Create a supplier; owner or procurement only |
| PUT | `/api/suppliers/{supplierId}` | Update a supplier with optimistic version checking |
| POST | `/api/suppliers/{supplierId}/enable` | Enable a supplier; owner or procurement only |
| POST | `/api/suppliers/{supplierId}/disable` | Disable a supplier; owner or procurement only |

## Purchase Orders

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/purchase-orders` | Create a draft purchase order and fully convert allocated procurement requests; owner or procurement only |
| GET | `/api/purchase-orders` | Page and filter purchase orders |
| GET | `/api/purchase-orders/{purchaseOrderId}` | Return purchase-order detail and safe sales-order sources |
| GET | `/api/purchase-orders/{purchaseOrderId}/events` | Page purchase-order events |

采购桥接开启时，创建请求必须携带 `Idempotency-Key`。认领或确认结果未决时返回 `503 PURCHASE_OPERATION_PENDING`，使用原键及原请求重试；改变账号或请求内容返回 `409 PURCHASE_IDEMPOTENCY_CONFLICT`。
远程认领确认前，本地采购单不计入列表及分页总数；直接访问详情或事件返回 `404 PURCHASE_ORDER_NOT_FOUND`。确认成功后，可见标记与操作成功状态在同一事务提交，重试返回最初的创建响应。普通采购单及已完成确认的历史采购单继续可用。桥接开关当前仍默认关闭。

新模式下，采购来源、库存和打包关联本地订单/商品/采购需求投影。需求投影未就绪或未完成权威交接时返回 `503 PROCUREMENT_PROJECTION_NOT_READY`，保留认领并使用原键重试，不将其作为业务失败自动释放。分配、收货及打包在事务外取得 Order 的持久化执行占用；授权结果未知返回 `503 ORDER_EXECUTION_PENDING`，订单已取消或有取消屏障返回 `409 ORDER_EXECUTION_FORBIDDEN`。已成功命令的原幂等重放仍返回原结果。

履约命令所需的订单投影尚未交接给 ORDER 权威时返回 `503 ORDER_PROJECTION_NOT_READY`，不执行远程占用或本地业务写入。

## Receiving

All authenticated employees can view domestic packages and goods receipts. Only `OWNER` and `WAREHOUSE` can register packages, confirm receipts, or cancel a package that has no confirmed receipt.

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/domestic-packages` | Page and filter domestic packages |
| GET | `/api/domestic-packages/lookup` | Locate a package by exact tracking number |
| GET | `/api/domestic-packages/{packageId}` | Return package, purchase-item progress, and receipt summaries |
| GET | `/api/domestic-packages/{packageId}/events` | Page receiving events |
| POST | `/api/domestic-packages` | Register a domestic package; owner or warehouse only |
| POST | `/api/domestic-packages/{packageId}/receipts` | Confirm one immutable receipt batch; owner or warehouse only |
| POST | `/api/domestic-packages/{packageId}/cancel` | Cancel an unreceived package; owner or warehouse only |
| GET | `/api/goods-receipts/{receiptId}` | Return one immutable goods-receipt detail |

The complete receiving contract, quantity semantics, idempotency behavior, and error codes are defined in `receiving-api.md`. The approved module boundary and database design are defined in `receiving-spec.md`.

登记包裹或确认新收货批次时，如果对应采购单的远程认领尚未确认，返回 `503 PURCHASE_OPERATION_PENDING`，不写入包裹、收货、库存批次或库存流水。

## Inventory

Confirmed receipts create accepted and damaged inventory lots in the same transaction. Accepted inventory is automatically reserved for sales-order items only through explicit purchase-order allocation sources. Historical receipts created before inventory posting was available can be posted idempotently by an owner, and retries do not duplicate lots, allocations, or movements.

All authenticated employees can query inventory lots, movements, allocations, and order coverage. Inventory responses expose operational order and product identifiers only and never include customer phone numbers or full addresses.

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/inventory/lots` | Page lots by product or lot keyword, purchase order, status, and quality |
| GET | `/api/inventory/lots/{lotId}` | Return lot quantities, receipt and purchase traceability, and allocations |
| GET | `/api/inventory/lots/{lotId}/movements` | Page immutable movements for one lot |
| GET | `/api/inventory/allocations` | Page allocations by order number, lot number, and status |
| GET | `/api/orders/{orderId}/inventory-allocations` | Return item-level required, reserved, consumed, and shortage quantities |
| POST | `/api/inventory/receipts/{receiptId}/post` | Idempotently post one historical receipt to inventory; owner only |
| POST | `/api/inventory/lots/{lotId}/allocate` | Manually reserve inventory against a matching purchase source; owner or warehouse only |
| POST | `/api/inventory/allocations/{allocationId}/release` | Release a reserved allocation; owner or warehouse only |
| POST | `/api/inventory/lots/{lotId}/adjustments` | Adjust available inventory in or out; owner or warehouse only |
| POST | `/api/inventory/lots/{lotId}/release-quarantine` | Move quarantined quantity into usable inventory; owner or warehouse only |

Inventory commands require a non-empty reason, an idempotency key, and the current entity version. Reusing a key for the same command returns the prior result; reusing it for different command data returns `INVENTORY_IDEMPOTENCY_CONFLICT`. Inventory movements are append-only. Manual allocation cannot exceed the lot's available balance, the matching purchase source remainder, or the sales-order item shortage. Adjustment-out never consumes reserved inventory. Quarantine release preserves the lot's `DAMAGED` source marker while making the released quantity available.

The approved inventory boundary is defined in `inventory-spec.md`. Automatic and manual source-based allocation, the command service, read-only query layer, inventory workbench, lot detail, and order coverage panel are implemented.

## Packing

All authenticated employees can view the packing workbench and task detail. Only `OWNER` and `PACKING` can start or mutate a packing task. An order can start packing only after every normal order item is fully covered by reserved inventory.

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/packing/tasks` | List ready orders and existing packing tasks by order keyword and status |
| GET | `/api/packing/orders/{orderId}` | Return packing detail, inventory allocations, boxes, items, events, and available actions |
| POST | `/api/packing/orders/{orderId}/start` | Start the single packing task for a fully reserved order |
| POST | `/api/packing/tasks/{taskId}/boxes` | Add one box with allocation-level item quantities |
| PUT | `/api/packing/tasks/{taskId}/boxes/{boxId}` | Replace box measurements, note, and allocation quantities |
| DELETE | `/api/packing/tasks/{taskId}/boxes/{boxId}` | Delete a box from a draft task using the current task version |
| POST | `/api/packing/tasks/{taskId}/complete` | Validate complete allocation coverage and atomically consume reserved inventory |
| POST | `/api/packing/tasks/{taskId}/cancel` | Cancel a draft packing task without consuming inventory |

Packing completion requires a unique idempotency key and the current task version. Every order quantity and every active inventory allocation must be represented exactly once across its boxes. Completion changes allocations from `RESERVED` to `CONSUMED`, updates lot balances, and appends one `PACKING_CONSUMED` movement per allocation in the same transaction. Completed tasks are immutable and are not reversible in the first packing phase. See `packing-spec.md`.

CSV order import completes the approved MVP module set. The public project exposes only endpoints required by that MVP.

### 演示店铺初始化（保留单体模式）

`POST /api/stores/demo/initialize`，无请求体。需要有效 JWT 且 roles 包含 OWNER。返回 200：channelId、storeId、code、name、status。创建启用的 DEMO 渠道与 ACTIVE 的 DEMO-STORE 店铺；重复调用保留原 ID，已停用的对应演示店铺重新启用。不会创建订单或修改其他店铺。同编码店铺若渠道或外部标识不同，返回 409 DEMO_STORE_CONFLICT；非老板返回 403，匿名返回 401。启用远程采购/订单权威模式时不注册该入口，避免两库分别创建店铺。迁移时保留渠道和店铺原 ID。

### 采购草稿确认下单

POST /api/purchase-orders/{id}/confirm-order，请求 {"version":0}。仅 OWNER、PROCUREMENT 可调用。校验有效本地用户、采购认领已确认、采购单版本和 DRAFT 状态，在采购单行锁内转为 ORDERED，记录 ordered_by、ordered_at 以及 ORDERED 事件。返回 id、status、version、orderedAt。同一操作人以原版本重复提交，在采购单仍为 ORDERED 且版本恰为原版本加一时返回成功，不重复写事件；状态继续变化、其他操作人或旧版本返回冲突。该操作仅登记已向供应商下单的事实，不调用第三方支付或采购平台。不新增数据表。
