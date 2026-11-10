# SecureTrade Access API architecture

```mermaid
flowchart TD
    Client[Trading agent or administrator] --> Security[Spring Security and JWT]
    Security --> Controllers[REST controllers]
    Controllers --> Services[Trade evaluation and admin review]
    Services --> DB[PostgreSQL requests and audit records]
    Liquibase[Liquibase migrations] --> DB
```

Spring Security authenticates requests before they reach the controllers. The service layer applies decision rules and ownership checks. PostgreSQL persists trade requests and compliance audit records.
