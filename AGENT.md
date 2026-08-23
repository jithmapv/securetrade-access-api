# AGENT.md — Master Guidelines for SecureTrade Access API

## 1. Project Overview & Architecture

The **SecureTrade Access API** is a high-security Spring Boot REST API that authenticates simulated trading agents and evaluates incoming financial trade requests into three auditable outcomes: `APPROVED`, `REJECTED`, and `MANUAL_REVIEW`.

### Architectural Principles

* **Layered Architecture**: Controller -> Service -> Repository -> PostgreSQL Database.
* **Database Migration Owner**: Liquibase owns all database schema changes. Hibernate MUST remain set to `spring.jpa.hibernate.ddl-auto=validate`.
* **Stateless Security**: Spring Security 6 with JWT Bearer Token authentication and RBAC (`ROLE_ADMIN`, `ROLE_TRADING_AGENT`).
* **Clean Code**: SOLID principles, constructor injection, explicit DTO mappings, and immutable patterns where applicable.

---

## 2. Universal Code Commenting Protocol

**CRITICAL RULE**: ALL inline, class, and method comments MUST be written in **very simple, beginner-friendly English**.

### Formatting Rules

* Use short, direct sentences and simple vocabulary.
* Explain **what** the code does or **why** it is needed.
* Avoid academic jargon, complex grammar, or repeating the code line literally.

| Type | Do NOT Write | Write This Instead |
| :--- | :--- | :--- |
| **Database** | `// Persist entity state via repository abstraction layer` | `// Save user to database` |
| **Validation** | `// Enforce non-null constraint validation on payload` | `// Check required fields` |
| **Exceptions** | `// Instantiate and propagate domain runtime exception` | `// Item not found error` |
| **Security** | `// Extract bearer token from authorization header context` | `// Get token from header` |

---

## 3. Environment & Security Rules

### Database & Environment Standard

* **Development Port**: Default PostgreSQL port is `5433` (Database: `securetrade_access`).
* **Environment Overrides**: Database connection parameters MUST support environment variable overrides:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5433/securetrade_access}
spring.datasource.username=${DB_USERNAME:securetrade}
spring.datasource.password=${DB_PASSWORD:securetrade_dev_password}
