# LedgerLine

LedgerLine is a Java Spring Boot and PostgreSQL financial management application for organizing personal accounts, recording double-entry money movement, tracking transactions, and reviewing ledger-backed activity through dashboards and reports. It is being developed for the Snovasys Fresher Hiring Challenge 2026.

## Features

- Registration and sign-in with BCrypt password hashing, session-based authentication, CSRF protection, and USER/ADMIN/AUDITOR roles.
- User-owned bank, wallet, and cash accounts with ledger-derived balances; account names can be updated and accounts can be closed.
- Deposits, withdrawals, and transfers represented as double-entry journal transactions, with idempotency, owner-scoped history, transactional posting, and balance checks.
- Dashboard with per-currency balances, account summaries, recent activity, and links to money movement.
- Reports for date-filtered income, expense, deposit, withdrawal, and transfer activity; totals remain separated by currency.
- Admin console with user/account counts, bounded user search/listing, user enable/disable management, and recent audit events.
- Auditor-only, read-only workspace for recent audit events and posted transaction statement lines.
- Audit records for implemented identity, account, financial, and administrator actions.

Settings, budgets, password recovery, MFA, email verification, and external bank connectivity are not implemented. The account and transaction workflows record application data; they do not move funds through a real bank or payment network.

## Technology

- Java 21
- Spring Boot 4.1.2, Spring MVC, Spring Security, Spring Data JPA, and Thymeleaf
- PostgreSQL (application database); H2 in PostgreSQL compatibility mode for automated tests
- Flyway versioned migrations
- Maven
- Custom responsive CSS and vanilla JavaScript; no React, Next.js, or Node.js application

## Architecture

Code is organized by feature under `com.ledgerline` and separated into application, domain, infrastructure, and web responsibilities where appropriate. Web controllers bind validated request forms and resolve the authenticated identity; application services enforce ownership and business rules; repositories and infrastructure adapters persist data. Thymeleaf renders pages and uses Spring Security's session and CSRF support.

`FinancialAccount` is the user-facing account. It links to an internal `LedgerAccount`; journal entries reference ledger accounts. A transaction posts debit and credit amounts, with PostgreSQL `NUMERIC(19,4)` storage and explicit three-letter currency codes. Monetary Java values use `BigDecimal`. Account balances and report values are derived from posted ledger activity; the UI does not accept balance edits. Posted journal data is protected from updates/deletes by database triggers. The schema has a reversal reference, but a reversal workflow is not currently implemented.

Flyway owns schema changes. The application uses Hibernate `ddl-auto=validate`; it does not ask Hibernate to create or update the production schema. V5, V7, and V10 are selected from vendor-specific migration directories for PostgreSQL or H2. Never edit an applied migration to change a live schema; add a new versioned migration after review.

## Prerequisites

- JDK 21
- PostgreSQL 18 recommended for the configured application database
- A PostgreSQL database and a database role with permission to apply Flyway migrations and run the application
- PowerShell on Windows (commands below); use `./mvnw` in a POSIX shell

## Configure PostgreSQL

Create an empty database, for example `ledgerline`, and configure the application's environment in IntelliJ's Run Configuration or in the process environment. Do not put real credentials in source files or commit them.

Activate the development profile explicitly with `SPRING_PROFILES_ACTIVE=dev` in IntelliJ's Run Configuration. The `dev` profile uses `jdbc:postgresql://localhost:5432/ledgerline` and username `postgres` when `DB_URL` and `DB_USERNAME` are omitted. `DB_PASSWORD` has no default and must be provided. You can set all three explicitly:

```text
DB_URL=jdbc:postgresql://localhost:5432/ledgerline
DB_USERNAME=<your-postgresql-role>
DB_PASSWORD=<your-local-secret>
```

For production, set `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the deployment environment or secret manager. The production profile has no datasource fallbacks and explicitly sets Secure, HttpOnly, and SameSite=Lax session-cookie attributes. The application has no default development profile; do not activate `dev` in production. The base configuration also requires datasource environment variables and retains secure cookie settings when no profile is active.

## Run the application

From the repository root, after setting the development environment variables above and `SPRING_PROFILES_ACTIVE=dev`:

```powershell
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`. Register a user, sign in, create one or more accounts, and then record deposits, withdrawals, or transfers. The dashboard, accounts, transaction history, and reports use the signed-in user's stored records. A successful operation is persisted as a balanced journal transaction. Transfers move value between the user's accounts and are not income or expenses.

## Deploy on Render with Docker

1. Push the project to a GitHub repository and create a **Web Service** in Render connected to it. Select **Docker** as the runtime and use the repository root as the Docker build context.
2. Attach a Render PostgreSQL database or use an approved PostgreSQL service. In the web service's environment settings, configure `DB_URL` as a JDBC PostgreSQL URL (`jdbc:postgresql://<host>:<port>/<database>`), `DB_USERNAME`, and `DB_PASSWORD`. Enter credentials only in Render's protected environment settings; never put them in the Dockerfile or repository.
3. Keep `SPRING_PROFILES_ACTIVE=prod` (the image already sets it). Render supplies `PORT`; the container passes it to Spring Boot and falls back to `8080` for local runs. Do not set `PORT` manually unless required by the service configuration.
4. The production profile requires PostgreSQL TLS with `sslmode=verify-full`. Add the trusted database CA certificate as a Render Secret File (for example, `postgres-ca.pem`) and set `SPRING_DATASOURCE_HIKARI_DATA_SOURCE_PROPERTIES_SSLROOTCERT` to its mounted path (for example, `/etc/secrets/postgres-ca.pem`). Ensure the hostname in `DB_URL` matches the certificate. Do not weaken TLS verification to make a connection succeed.
5. Deploy and check the service logs for successful Flyway migration validation/application and Hibernate schema validation. Flyway runs on startup, so the configured database role needs migration privileges. Back up the database before deployment and use a least-privilege role appropriate to the app's runtime and migration model.

The Docker build runs `./mvnw package`, including the Maven test suite, then copies the executable Spring Boot JAR into a Java 21 JRE image. The runtime process uses a non-root user and exposes port 8080. No database credentials are included in the image.

## Database migrations

Flyway runs on application startup and validates/applies the common migrations in `src/main/resources/db/migration` plus the matching vendor migrations under `db/migration-vendor/{vendor}`. V1–V10 establish the current schema, with V5, V7, and V10 implemented separately for PostgreSQL and H2. V9 adds the AUDITOR value to the existing role constraint. V10 permits the `LEGACY` transaction type and restores parent-row locking in PostgreSQL's journal-entry trigger function; H2's Java trigger already locks the parent row. Hibernate validates entity mappings after migration. Back up the database before deploying schema changes and inspect Flyway output for failures; do not drop or recreate a database containing application data.

**PostgreSQL verification (2026-11-10):** The validated recovery backup passed `pg_restore --list` and its archive listing contained LedgerLine schema and table-data entries; no restore drill was performed. After read-only prechecks, Flyway repair reconciled the V4/V5 checksums and migration applied V6–V10. PostgreSQL history shows V1–V10 successful. The V10 constraint is validated and permits DEPOSIT, WITHDRAWAL, TRANSFER, and LEGACY; all four types had zero rows at verification. The journal-entry guard function contains the three expected `FOR UPDATE` lookups and both journal immutability triggers are enabled and attached. A servlet startup against PostgreSQL validated all ten migrations and successfully initialized Hibernate with `ddl-auto=validate`. This verifies migration and ORM schema compatibility, not PostgreSQL financial-operation concurrency or full application workflows.

## Tests

Run the complete suite:

```powershell
.\mvnw.cmd test
```

Tests activate the `test` profile, use an in-memory H2 database in PostgreSQL compatibility mode, and run Flyway with Hibernate validation. The suite covers identity and access controls, account ownership, financial posting/idempotency/concurrency behavior, audit behavior, dashboard pages, admin management, and reports. H2 results are not PostgreSQL integration-test results. PostgreSQL-specific triggers, locking, and migrations should also be verified against a disposable or backed-up PostgreSQL instance before release.

The current full suite passes **61 tests** with no failures, errors, or skips. This includes startup integration coverage proving that configuring only `LEDGERLINE_AUDITOR_BOOTSTRAP_EMAIL` provisions an auditor, plus runner tests for independent admin/auditor bootstrap configuration. The startup test uses an isolated H2 database; public registration remains unable to assign AUDITOR.

## Secure first-admin bootstrap

There is no public admin registration route. First, register the intended administrator as a normal user and confirm that the account is enabled. For one operator-controlled application startup, set:

```text
LEDGERLINE_ADMIN_BOOTSTRAP_EMAIL=<the-registered-user-email>
```

The startup runner promotes the configured existing account only when it is eligible and no first administrator has already been assigned; the action is audited. Then remove `LEDGERLINE_ADMIN_BOOTSTRAP_EMAIL` from the runtime environment and restart normally. Do not use this bootstrap variable as a standing public configuration or include an email address in a committed file.

## Secure first-auditor bootstrap

There is no public auditor registration or role-selection field. Register the intended auditor as a normal enabled user first. For a controlled startup, set:

```text
LEDGERLINE_AUDITOR_BOOTSTRAP_EMAIL=<the-registered-user-email>
```

The startup runner promotes the configured existing USER only if no auditor has already been bootstrapped, and records an `AUDITOR_BOOTSTRAPPED` audit event. Remove the variable after the bootstrap startup. The auditor role is separate from USER and ADMIN: it can read the bounded auditor workspace but cannot use customer account, transaction, or administrator routes. The workspace includes customer-identifying transaction statement data, so grant this role only to an authorized reviewer.

## Main workflows

1. **Identity:** Register with an email and password, then sign in. Passwords are stored as BCrypt hashes. Sign out through the CSRF-protected form.
2. **Accounts:** Create BANK, WALLET, or CASH accounts with a display name and currency. Review the derived balance, change the display name, or close the account. Closed accounts remain in history and cannot be used for new operations.
3. **Transactions:** Choose deposit, withdrawal, or transfer; select owned active accounts; enter a positive amount and currency; review and submit. The posting service handles idempotency and transaction boundaries. Posted activity cannot be edited or deleted in the UI.
4. **Dashboard and reports:** Review balances grouped by currency and recent activity. Reports filter posted activity by date range and keep currencies separate; displayed account balances are current ledger balances.
5. **Administration:** A ROLE_ADMIN can search a bounded user list, enable/disable accounts, and review recent audit events. Administrative routes are not available to ordinary users.
6. **Audit review:** A ROLE_AUDITOR can view the latest 100 audit events and journal lines for the latest 100 posted transactions. The interface and backend expose no auditor write operations.

## Deployment requirements and known limitations

- Supply database credentials using a secret manager or protected runtime environment, enforce TLS to PostgreSQL, and use HTTPS for the application.
- The `prod` profile enforces PostgreSQL `sslmode=verify-full` and Secure, HttpOnly, SameSite=Lax session cookies. Configure a trusted PostgreSQL root CA and a database hostname matching its certificate; terminate HTTPS at the application or a correctly configured trusted proxy.
- Admin and auditor bootstrap settings are independent; configure neither as a public registration mechanism. `.idea` workspace files and common local environment, credential, and key files are excluded by `.gitignore`.
- Flyway runs at application startup using the datasource credentials, so that database role needs schema-migration privileges. For production, tightly control those credentials and startup/deployment access; use least-privilege database roles and tested backups.
- PostgreSQL V1–V10 migration validation, journal trigger state, and Hibernate schema validation were verified. Automated functional and concurrency tests run against H2; repeat those against PostgreSQL before production.
- The auditor startup bootstrap and V9 role behavior have H2 integration coverage; production auditor workflow is not separately verified against PostgreSQL.
- Rate limiting, MFA, account recovery, email verification, and external payment integrations are absent. Apply operational controls appropriate to the deployment before exposing the service publicly.
- Budgets and Settings are not implemented. Some UI navigation is intentionally marked as unavailable.

## Project notes

`PROMPTS.md` records AI-assisted project work and its evidence. `THREATS.md` tracks threats, mitigations, and verification status. These documents distinguish implemented behavior from planned or unverified controls.
