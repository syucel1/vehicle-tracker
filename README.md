# Vehicle Tracker Platform

Real-time vehicle tracking: GPS tracker devices connect to a Java backend, which decodes their reports (200+ GPS protocols), stores them, and streams live positions over WebSocket to an authenticated web management application that shows the fleet on a map.

> **Status:** Design phase (v0.1 draft, 6 Nov 2026). Roadmap is next.

## Documentation

| Document | Format | Description |
|---|---|---|
| System design | [HTML](vehicle-tracker-design.html) · [PDF](vehicle-tracker-design.pdf) | Architecture, components, data flow, WebSocket/REST contracts, data model, security, scaling, risks |
| Roadmap | _coming next_ | Phases, milestones, team, estimates |

See [DOCS.md](DOCS.md) for the documentation index.

## Architecture at a glance

```
Tracker devices ──TCP/UDP──▶ L4 LB ──▶ Ingestion Gateway (Netty + Traccar decoders)
                                              │ normalised Position
                                              ▼
                                            Kafka ──▶ Position Processor (validate, dedupe, enrich, events)
                                              │
                         ┌────────────────────┼────────────────────┐
                         ▼                    ▼                    ▼
                  History Writer          Live Hub ◀──▶ Redis   Core API (REST) ◀──▶ Keycloak (OIDC)
                         │                    │ WebSocket          │
                         ▼                    ▼                    │
              PostgreSQL + TimescaleDB   Management Web App ◀──────┘
                    + PostGIS            (React + MapLibre / any map)
```

### Key decisions

- **200+ protocols:** reuse the protocol decoders of the open-source [Traccar](https://github.com/traccar/traccar) server (Java, Apache 2.0) as a thin, stateless ingestion gateway. Everything downstream is our own code.
- **Event-driven core:** Kafka (keyed by device ID) decouples device ingestion from processing, storage and live delivery; all consumers are idempotent.
- **Live updates:** dedicated Live Hub service pushes positions over `wss://` with snapshot-on-connect, server-side authorisation, viewport filtering and back-pressure.
- **Any map:** the web app uses a `MapAdapter` interface; MapLibre GL JS by default (OSM, MapTiler, Mapbox, Esri, HERE tiles), Google Maps adapter optional.
- **Security:** OIDC (Keycloak) with JWT, tenant isolation on every row, registered-device allow-list, audited commands.

## Planned technology stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Netty, Traccar (protocol decoding) |
| Messaging | Apache Kafka (Avro/Protobuf schemas) |
| Storage | PostgreSQL + TimescaleDB + PostGIS, Redis |
| Identity | Keycloak (OIDC / OAuth2) |
| Frontend | React, TypeScript, Vite, MapLibre GL JS |
| Infrastructure | Docker, Kubernetes, Helm, Terraform, GitHub Actions |
| Observability | Prometheus, Grafana, OpenTelemetry, Loki |

## Planned repository layout

```
vehicle-tracker/
├── docs/                    # design, roadmap, ADRs
├── services/
│   ├── ingestion-gateway/   # Traccar-based protocol gateway
│   ├── position-processor/
│   ├── history-writer/
│   ├── live-hub/            # WebSocket service
│   └── core-api/            # REST API, device registry
├── web/                     # management web application
├── libs/                    # shared schemas (position, events)
├── deploy/                  # Helm charts, Docker Compose, Terraform
└── tools/                   # device simulators, packet replay, load tests
```

## Licence notes

Traccar is licensed under Apache 2.0. Any use or modification must retain its licence and NOTICE files and mark modified files.
