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

The repository includes the application skeleton, JWT authentication, the order-domain data layer, order query APIs, development demonstration orders, and the first Vue order workbench and detail page. CSV import and order command workflows remain deferred to later approved phases.

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

See `docs/mvp-spec.md`, `docs/architecture.md`, and `docs/route-design.md` before implementing a business module.
