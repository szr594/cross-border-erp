# Architecture

## System shape

The application is a modular monolith with two deployable parts:

```text
Vue 3 browser application
        |
        | JSON over /api
        v
Spring Boot modular monolith
        |
        v
MySQL 8.4
```

## Backend modules

- `auth`: access tokens, refresh sessions, login and logout.
- `user`: employees, roles, and permission grants.
- `store`: sales-platform stores and responsible employees.
- `product`: company physical SKUs and external product mappings.
- `order`: external sales orders and order lines.
- `procurement`: procurement demand, carts, batches, and domestic order anchors.
- `receiving`: domestic packages and goods receipts.
- `inventory`: lots, movements, and order reservations.
- `packing`: packing records and packages.
- `reporting`: read-only business projections added after the MVP.
- `audit`: security and business operation records.

Within each module, dependencies flow from controller to application service to repository/entity. Controllers do not own business rules.

## Authentication decision

- Short-lived access token: held in frontend memory.
- Refresh token: stored in a Secure, HttpOnly, SameSite cookie.
- Refresh sessions: revocable server-side records.
- The exact endpoints, expiry policy, rotation, and reuse detection require a separate approved authentication specification.

## Data decision

The Java version uses a clean MySQL 8.4 schema managed by Flyway. It does not reuse the former Python database or real company data. Database sessions and application timestamps use UTC.
