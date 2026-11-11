# Cross Border ERP

A portfolio-oriented cross-border order fulfillment ERP rebuilt with Java and Vue. The project models the operational flow from sales orders through procurement, domestic receiving, inventory allocation, and packing using fictional demonstration data.

## Technology stack

- Java 17 and Spring Boot 3
- Maven
- Spring Data JPA and MySQL 8.4
- Flyway
- Vue 3, TypeScript, Vite, Vue Router
- Element Plus
- pnpm

## Current stage

The repository includes JWT authentication and working order, procurement, receiving, inventory, and packing modules with development demonstration data. Inventory supports traceable lots, automatic and manual allocation, release, adjustment, quarantine release, and automatic reservation release when an order is canceled. Packing supports ready-order discovery, multi-box item allocation, immutable completion, and atomic consumption of reserved inventory. Vue workbenches are available for orders, procurement, receiving, inventory, and packing. CSV order import remains deferred to the next phase.

With the `dev` profile and `DEMO_OWNER_PASSWORD` configured, startup creates fictional orders, purchase orders, two receiving batches, accepted and damaged inventory lots, reserved and released allocations, a fully reserved order ready for packing, and immutable inventory movements. Repeated startup does not duplicate these demo records.

## Development

Create a local `.env` from `.env.example`. Set `MYSQL_ROOT_PASSWORD` for the MySQL container and set `MYSQL_PASSWORD` for the backend connection. Never commit the local `.env`. Set a random `JWT_SECRET` of at least 32 bytes. Set `DEMO_OWNER_PASSWORD` only when you want the development profile to create the `owner` demonstration account.

Database:

```bash
docker compose up -d mysql
```

MySQL is bound to `127.0.0.1:3306` by default, uses `utf8mb4`, and stores application timestamps in UTC.

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
pnpm install
pnpm dev
```

Default development addresses:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`

Authentication uses a 15-minute JWT held only in frontend memory and a rotating 7-day HttpOnly refresh cookie. See `docs/auth-spec.md`.

## Verification

```bash
cd backend
mvn test

cd ../frontend
pnpm type-check
pnpm build
```

The inventory workbench, lot detail, filtering, traceability links, role-aware commands, order inventory coverage, and packing workbench are covered by frontend type checks and production builds. Browser acceptance is repeated after each end-to-end module milestone.

See `docs/mvp-spec.md`, `docs/architecture.md`, and `docs/route-design.md` before implementing a business module.
