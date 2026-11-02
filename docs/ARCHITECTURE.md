# Architecture

```mermaid
flowchart LR
  User[Student] --> UI[React 19 + Vite]
  UI -->|HTTP /api| API[Spring Boot 3 REST API]
  API -->|MatcherBridge subprocess| Py[Python matching engine]
  Py --> Data[(Generated O*NET/ESCO data)]
  API -->|Optional account mode| DB[(MySQL 8 + Flyway)]
  API --> Cat[Career/resource CSV and JSON catalogs]
  API --> Auth[JWT access token + rotating HttpOnly refresh cookie]
```

The matching formula remains in `data_processing/matching_engine.py`; Java delegates matching and what-if requests through `MatcherBridge`. Guest profiles stay in browser storage. When account mode is enabled, verified user IDs scope relational persistence. Catalog search and learning resources are read-only and use generated occupation files plus a curated resource catalog. Generated `data/processed/` is intentionally excluded from Git; the clean-clone delivery method remains a deployment decision.
