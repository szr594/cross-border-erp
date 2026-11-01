# MVP Specification

## Goal

Deliver a portfolio-ready operational loop using fictional data:

```text
Login -> sales order -> inventory decision -> procurement -> domestic receiving
-> inventory allocation -> order ready -> packing complete
```

## MVP modules

1. JWT-based employee authentication and role permissions.
2. Order list, detail, filtering, procurement request, inventory allocation, fictional demo data, and CSV import.
3. Procurement demand, cart grouping, batch submission, and domestic purchase order anchors.
4. Tracking-number receiving with accepted, damaged, and wrong-item quantities.
5. SKU inventory, traceable lots, adjustments, reservations, and over-allocation protection.
6. Ready-to-pack queue, multiple packages, completion, and exception handling.

## Deferred

- Real Faire or 1688 credentials and APIs.
- International shipping, labels, customs, and shipment expense calculation.
- Financial settlement, cost, and profit.
- Multi-warehouse transfers.
- Production customer, order, supplier, or employee data.
- Real Faire order synchronization; the MVP uses fictional demo data and an approved CSV template.

## Acceptance rules

- Each module receives an approved specification before implementation.
- The frontend calls only documented backend APIs.
- Write operations are transactional.
- Inventory cannot be allocated below zero.
- Only ready orders can enter packing.
- Automated tests cover critical state transitions.
- All authenticated employees can initially view all orders, but response fields remain role-filtered.
- Only the owner role can cancel an order.
- Procurement users never receive customer phone numbers or complete addresses.

The approved order scope, stage calculation, privacy boundary, and acceptance criteria are defined in `order-spec.md`.
