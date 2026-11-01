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
| POST | `/api/orders/{orderId}/cancel` | Cancel an order; owner only |
| POST | `/api/order-imports/preview` | Validate a CSV order import without writing business data |
| POST | `/api/order-imports/{importId}/confirm` | Confirm a validated CSV import; owner only |
| GET | `/api/order-imports/{importId}` | Return import status and counts |
| GET | `/api/order-imports/{importId}/errors` | Page safe row-level import errors |

The complete contract, privacy rules, concurrency behavior, and status codes are defined in `order-api.md`. Database design and module behavior are defined in `order-database.md` and `order-spec.md`.

Remaining modules will be specified before implementation. The public project exposes only endpoints required by the approved MVP.
