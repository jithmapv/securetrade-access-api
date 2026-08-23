# SecureTrade Access API

SecureTrade Access API is a Spring Boot REST API for simulated trading agents. It authenticates users, evaluates trade access requests, protects request ownership, supports admin review, and records compliance audit history.

The project is complete through Phase 12 and includes end-to-end, database, security, idempotency, and concurrency tests.

## Main Features

- Stateless JWT authentication
- `ADMIN` and `TRADING_AGENT` role authorization
- BCrypt password hashing with cost factor 12
- Trading agent registration and status management
- Trade decisions: `APPROVED`, `REJECTED`, or `MANUAL_REVIEW`
- Agent-scoped idempotency with concurrent request protection
- Request ownership checks
- Admin overrides for manual-review requests
- Compliance audit records
- RFC 7807-style error responses
- Swagger UI and OpenAPI documentation
- Liquibase database migrations
- Paginated request and audit history

## Technology Stack

| Technology | Version or use |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.5.16 |
| Spring Security | 6.x |
| Spring Data JPA | Hibernate ORM |
| PostgreSQL | 17 |
| Liquibase | Database migrations |
| JJWT | 0.11.5 |
| springdoc-openapi | 2.8.5 |
| Maven | Wrapper included |
| Testing | JUnit 5, Mockito, MockMvc |

## Decision Rules

Rules run in this order. The first matching rule decides the result.

| Priority | Condition | Outcome | Reason code |
| --- | --- | --- | --- |
| 1 | Agent status is not `ACTIVE` | `REJECTED` | `ERR_AGENT_SUSPENDED` |
| 2 | Volume is greater than the agent limit | `REJECTED` | `ERR_EXCEEDS_AGENT_LIMIT` |
| 3 | Volume is greater than 10,000,000 or risk is greater than 0.80 | `REJECTED` | `ERR_EXCEEDS_HARD_LIMIT` |
| 4 | Volume is greater than 1,000,000 or risk is greater than 0.30 | `MANUAL_REVIEW` | `FLAG_HIGH_VOL_RISK` |
| 5 | No earlier rule matched | `APPROVED` | `EXEC_PASS_STANDARD` |

The comparisons are strict. A volume of exactly 1,000,000 and a risk score of exactly 0.30 do not trigger manual review.

## Requirements

- JDK 17
- Docker Desktop or another Docker Compose environment
- PowerShell on Windows, or a POSIX shell on Linux and macOS

The integration tests use the configured PostgreSQL database. They do not use H2 or Testcontainers. Use a local development database, not a production database.

## Configuration

The default settings are for local development.

| Environment variable | Default value | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5433/securetrade_access` | PostgreSQL connection URL |
| `DB_USERNAME` | `securetrade` | Database username |
| `DB_PASSWORD` | `securetrade_dev_password` | Database password |
| `JWT_SECRET` | Development fallback in `application.properties` | JWT signing secret |
| `JWT_EXPIRATION_MS` | `86400000` | Token life in milliseconds (24 hours) |

Always set a strong `JWT_SECRET` and secure database credentials outside local development.

`JWT_SECRET` must be Base64 text that decodes to at least 32 bytes.

PowerShell example:

```powershell
$jwtBytes = New-Object byte[] 32
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$random.Dispose()
$env:JWT_EXPIRATION_MS = "3600000"
```

Linux or macOS example:

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
export JWT_EXPIRATION_MS="3600000"
```

## Quick Start

### 1. Start PostgreSQL

```powershell
docker compose up -d postgres
docker compose ps
```

Docker starts PostgreSQL with these development values:

- Database: `securetrade_access`
- Username: `securetrade`
- Password: `securetrade_dev_password`
- Host port: `5433`

### 2. Run the application

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux or macOS:

```bash
sh ./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`.

Liquibase runs automatically. Hibernate validates the schema and does not create it.

### 3. Check the API documentation

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

These documentation routes are public.

## Initial Admin Account

The application creates this admin account on first startup when it does not already exist:

- Username: `admin_user`
- Password: `AdminPassword123!`
- Role: `ADMIN`
- Status: `ACTIVE`

The password is stored as a BCrypt hash. Later application starts do not replace the existing account or reset its password.

These credentials are for local development only. Change or disable this bootstrap account before using the application in another environment.

## Authentication

Login is the only public business API endpoint. Swagger and OpenAPI routes are also public.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin_user",
    "password": "AdminPassword123!"
  }'
```

Example response:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "username": "admin_user",
  "role": "ADMIN"
}
```

Send the token on protected requests:

```text
Authorization: Bearer <token>
```

The API reloads the current user for each JWT request. An `INACTIVE` or `SUSPENDED` account cannot continue using an old token.

## API Endpoints

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | Public | Login and receive a JWT |
| `POST` | `/api/v1/admin/agents` | Admin | Register an active trading agent |
| `GET` | `/api/v1/agents/me` | Agent or admin | Get the current agent profile |
| `GET` | `/api/v1/admin/agents/{id}` | Admin | Get an agent by ID |
| `PATCH` | `/api/v1/admin/agents/{id}/status` | Admin | Set `ACTIVE`, `INACTIVE`, or `SUSPENDED` |
| `POST` | `/api/v1/access/evaluate` | Agent or admin | Evaluate and save a trade request |
| `GET` | `/api/v1/access/requests/me?page=0&size=10` | Agent or admin | Get the current agent request history |
| `GET` | `/api/v1/access/requests/{id}` | Owner or admin | Get one saved request |
| `POST` | `/api/v1/admin/requests/{id}/override` | Admin | Finalize a manual-review request |
| `GET` | `/api/v1/admin/audit-logs?page=0&size=10` | Admin | Get compliance audit history |

An admin needs a linked trading-agent profile to use the agent-specific `/me` and evaluation operations.

## Common Request Examples

### Register an agent

```bash
curl -X POST http://localhost:8080/api/v1/admin/agents \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "agent.one",
    "password": "StrongPassword123!",
    "agentCode": "AGENT-001",
    "name": "Momentum Agent",
    "strategyType": "MOMENTUM",
    "maxAllowedVolume": 5000000.00
  }'
```

### Evaluate a trade

```bash
curl -X POST http://localhost:8080/api/v1/access/evaluate \
  -H "Authorization: Bearer <agent-token>" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: trade-2026-0001" \
  -d '{
    "symbol": "AAPL",
    "tradeType": "BUY",
    "requestedVolume": 500000.00,
    "riskScore": 0.15
  }'
```

Trade input rules:

- `symbol`: 1 to 12 upper-case letters, numbers, `.`, or `-`
- `tradeType`: `BUY` or `SELL`
- `requestedVolume`: positive, with up to 13 whole digits and 2 decimal digits
- `riskScore`: from `0.00` to `1.00`

### Override a manual-review request

```bash
curl -X POST http://localhost:8080/api/v1/admin/requests/REQUEST_ID/override \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "outcome": "APPROVED",
    "reasonCode": "OVERRIDE_ADMIN_APPROVED",
    "adminNotes": "Compliance review is complete."
  }'
```

Only a request in `MANUAL_REVIEW` can be changed. The final outcome must be `APPROVED` or `REJECTED`.

## Idempotency

`POST /api/v1/access/evaluate` accepts an optional `X-Idempotency-Key` header.

- The key is trimmed and may contain at most 64 characters.
- A blank key is treated as missing.
- The same agent and key return the original saved response.
- Concurrent requests with the same agent and key create one database record.
- The same key may be used by different agents.
- Requests without a key create separate records.

## Audit History

The application records these actions:

- `AGENT_REGISTRATION`
- `TRADE_EVALUATION`
- `ADMIN_OVERRIDE`
- `AGENT_STATUS_CHANGE`

Admins can filter audit history by exact actor username:

```text
GET /api/v1/admin/audit-logs?page=0&size=10&actorUsername=admin
```

Audit page size must be between 1 and 100.

## Error Responses

Errors use `application/problem+json` and an RFC 7807-style structure.

```json
{
  "type": "https://api.securetrade.com/errors/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Agent profile not found",
  "instance": "/api/v1/admin/agents/00000000-0000-0000-0000-000000000000",
  "errorCode": "RESOURCE_NOT_FOUND",
  "timestamp": "2026-08-23T10:15:30Z"
}
```

Validation responses also contain a `validationErrors` map.

Application error codes:

- `RESOURCE_NOT_FOUND`
- `DUPLICATE_RESOURCE`
- `INVALID_REQUEST`
- `ACCESS_DENIED`
- `INTERNAL_ERROR`

## Database Migrations

Liquibase loads migrations from `src/main/resources/db/changelog`.

| Migration | Change |
| --- | --- |
| `001-create-users-table.yaml` | User accounts and unique usernames |
| `002-create-agents-table.yaml` | Trading-agent profiles and unique agent codes |
| `003-create-access-requests-table.yaml` | Trade access requests and agent lookup index |
| `004-add-idempotency-constraint.yaml` | Unique agent and idempotency-key pair |
| `005-create-audit-logs-table.yaml` | Audit history and descending timestamp index |

The test suite checks the required database indexes directly against PostgreSQL.

## Test and Build

Start the local PostgreSQL container before running tests:

```powershell
docker compose up -d postgres
.\mvnw.cmd clean test
.\mvnw.cmd clean package
```

Linux or macOS:

```bash
docker compose up -d postgres
sh ./mvnw clean test
sh ./mvnw clean package
```

Latest verified result:

- 115 tests
- 0 failures
- 0 errors
- 0 skipped
- Full end-to-end lifecycle passed
- 20-thread idempotency stress test passed
- 20-thread pessimistic-lock override test passed
- N+1 query check passed
- PostgreSQL index verification passed

The executable JAR is created at:

```text
target/securetrade-access-api-0.0.1-SNAPSHOT.jar
```

Run the packaged application:

```powershell
java -jar target\securetrade-access-api-0.0.1-SNAPSHOT.jar
```

## Project Structure

```text
src/main/java/com/securetrade/accessapi/
|-- common/       Enums and exception handling
|-- config/       OpenAPI configuration
|-- controller/   REST endpoints
|-- decision/     Trade decision rules
|-- dto/          Request and response models
|-- entity/       JPA entities
|-- repository/   Database queries
|-- security/     JWT, roles, and ownership checks
`-- service/      Application workflows

src/main/resources/
|-- application.properties
`-- db/changelog/ Liquibase migrations
```

## Stop the Development Database

```powershell
docker compose down
```

The named PostgreSQL volume keeps the database data. Use `docker compose down -v` only when you intentionally want to remove local database data.
