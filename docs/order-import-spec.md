# CSV Order Import Specification

## Scope

The MVP accepts the project-owned CSV template and fictional data only. It does not connect to Faire, create stores or employees, update existing orders, or permit partial success. Every endpoint and the frontend route require `OWNER`.

## Workflow

1. `POST /api/order-imports/preview` accepts one UTF-8 CSV file as multipart field `file`.
2. The server validates the exact v1 header, field types, references, order grouping, duplicates, and existing orders without writing business orders.
3. `GET /api/order-imports/{importId}` returns the preview status and counts. `GET /api/order-imports/{importId}/errors` pages safe errors.
4. A zero-error `VALIDATED` preview can be confirmed within 24 hours through `POST /api/order-imports/{importId}/confirm`.
5. Confirmation creates all new orders, items, and `ORDER_IMPORTED` events in one transaction. Repeating confirmation returns the completed result without duplicating data.

## Limits and format

- UTF-8, optionally with BOM; comma delimiter; RFC-style quoted fields.
- Maximum 5 MB and 10,000 data rows.
- ISO-8601 instants include an offset, for example `2026-11-11T02:30:00Z`.
- Currency is three letters; country code is two letters; quantity is positive; minor-unit price is non-negative.
- Each row represents one order item. Order-level values must match across rows with the same `store_code + external_order_id`.

Header v1:

```text
store_code,external_order_id,display_order_no,external_status,ordered_at,requested_ship_at,currency,responsible_login,customer_id,customer_name,recipient_name,recipient_phone,country_code,province,city,district,address_line1,address_line2,postal_code,external_item_id,external_product_id,external_variant_id,external_sku,product_name,variant_name,image_url,quantity,unit_price_minor,note
```

Required columns are `store_code`, `external_order_id`, `external_status`, `ordered_at`, `currency`, `external_item_id`, `product_name`, and `quantity`.

## Idempotency and conflicts

The business key is `store + external_order_id`. A missing order is creatable. An existing order with the same order snapshot and item snapshots is skipped. Any difference returns `EXISTING_ORDER_CONFLICT`; CSV never updates existing orders or downstream procurement, receiving, inventory, or packing facts. Database unique constraints remain the final concurrency guard.

## Persistence and privacy

Flyway V8 adds `order_import_batches`, `order_import_rows`, and `order_import_errors`. The original file is not retained. Normalized preview rows are stored only for confirmation, and error responses contain row number, field, code, and a safe description. Logs and API responses must not echo complete raw rows, phone numbers, or addresses.

## Statuses

`VALIDATING`, `VALIDATED`, `INVALID`, `IMPORTING`, `COMPLETED`, `FAILED`, and `EXPIRED` are reserved. The current synchronous MVP normally returns `VALIDATED`, `INVALID`, or `COMPLETED`.

## Verification

- Flyway and JPA schema validation on H2 MySQL mode.
- MockMvc coverage for owner authorization, preview, error pagination, confirmation, and repeat confirmation.
- Full backend regression plus frontend type-check and production build.
- MySQL 8.4 integration coverage verifies V8 migration, CSV preview, confirmation, and idempotent repeat confirmation.
- Browser acceptance covers owner login, preview, confirmation, imported-order lookup, and mobile layout without horizontal overflow.
