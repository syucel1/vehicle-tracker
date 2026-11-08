# Documentation

| Folder / file | Purpose | Status |
|---|---|---|
| [vehicle-tracker-design.html](vehicle-tracker-design.html) | System design (web version) | v0.1 draft |
| [vehicle-tracker-design.pdf](vehicle-tracker-design.pdf) | System design (print / share version) | v0.1 draft |
| [README.md → Phase 1](README.md#phase-1-device-ingestion) | Phase 1 build/run guide, Kafka topics, message contracts, known limitations | Current |
| [traccar.xml](deploy/gateway/traccar.xml) | Ingestion gateway configuration | Phase 1 |
| [mockup/](mockup/README.md) | Clickable HTML mockups of the management console (Google Maps) | Mockup |
| roadmap/ | Delivery roadmap | Planned |
| adr/ | Architecture Decision Records | Planned |

## Design document contents

1. Executive summary
2. Goals, scope & requirements
3. The 200+ protocol problem (options & decision)
4. High-level architecture
5. Component design
6. Data flow (uplink & downlink)
7. Real-time WebSocket contract
8. REST API surface
9. Data model & storage
10. Management web application ("any map" adapter)
11. Security
12. Scalability & capacity
13. Deployment & operations
14. Technology stack
15. Risks & open questions

## Regenerating the PDF

The HTML is the source of truth. To rebuild the PDF:

```bash
google-chrome --headless=new --no-pdf-header-footer \
  --print-to-pdf=vehicle-tracker-design.pdf vehicle-tracker-design.html
```

## Proposed first ADRs

- ADR-001: Use Traccar protocol decoders as the ingestion gateway
  — *Phase 1 decision:* run the unmodified Traccar image and use its built-in Kafka forwarding;
  revisit with a small gateway extension in Phase 2 to ACK devices only after Kafka confirms.
- ADR-002: Kafka as the event backbone, partitioned by device ID
- ADR-003: PostgreSQL + TimescaleDB + PostGIS as the single initial data store
- ADR-004: MapLibre GL JS with a provider-agnostic map adapter
- ADR-005: Keycloak / OIDC for authentication
