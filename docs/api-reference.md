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
| POST | `/api/order-imports/preview` | Validate a CSV order import without writing business data |
| POST | `/api/order-imports/{importId}/confirm` | Confirm a validated CSV import; owner only |
| GET | `/api/order-imports/{importId}` | Return import status and counts |
| GET | `/api/order-imports/{importId}/errors` | Page safe row-level import errors |

The complete contract, privacy rules, concurrency behavior, and status codes are defined in `order-api.md`. Database design and module behavior are defined in `order-database.md` and `order-spec.md`.

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

CSV order import remains the last deferred MVP module. The public project exposes only endpoints required by the approved MVP.
