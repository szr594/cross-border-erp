# Cross Border ERP Development Rules

1. Frontend package management uses pnpm only. Do not use npm or yarn.
2. Do not add dependencies without explicit approval.
3. Do not edit generated output such as `frontend/dist`, `frontend/*.tsbuildinfo`, or `backend/target`.
4. Do not modify unrelated files or format the whole project as part of a scoped task.
5. Do not use `any`, `@ts-ignore`, `eslint-disable`, skipped tests, or weaker assertions to hide failures.
6. Frontend HTTP calls belong in `frontend/src/api`; Vue components must not call `fetch` directly.
7. Every new page must be registered in the router and include an access rule when authentication is active.
8. Backend controllers handle transport concerns only. Business rules and transactions belong in application services.
9. Database schema changes must use Flyway migrations. Never change a production schema manually.
10. Never commit real customer, order, supplier, credential, cookie, token, or database backup data.
11. Preserve the API contract documented in `docs/api-reference.md` unless a specification explicitly changes it.
12. Before implementation, restate the request, list expected files, disclose uncertainties, and wait for confirmation.
13. After implementation, report changed files, actual verification commands and results, and remaining risks.
14. Run the relevant checks before delivery. The full verification target is backend tests plus frontend type-check and build.
15. Database, authentication, authorization, destructive operations, and bulk scripts require an approved design before implementation.

