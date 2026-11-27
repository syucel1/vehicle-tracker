# Vehicle Tracker roadmap

Delivery plan derived from the [system design](vehicle-tracker-design.html) (requirements F1–F11, section 2).
Each phase ends with something that can be demonstrated. Item IDs (R2.1, …) are used in commits and docs.

Status: ✅ done · 🔨 in progress · ⏳ next · ○ planned

Last updated: 27 Nov 2026

## Overview

| Phase | Goal | Requirements | Status |
|---|---|---|---|
| 0 | Design and mockups | – | ✅ |
| 1 | Device ingestion: trackers ⇄ backend ⇄ Kafka | F1, F2, F3 | ✅ written, ⏳ end-to-end run pending |
| 2 | Live backend: positions reach a browser in real time, history is stored | F4, F8 (storage) | 🔨 R2.1–R2.2 written |
| 3 | Core API and sign-in: vehicles, devices, users, tenants | F5, F7 | ○ |
| 4 | Web console MVP: live map from the mockups, on real data | F6, F7 | ○ |
| 5 | Trips, history replay, events and alerts | F8, F9 | ○ |
| 6 | Commands, reports, public API and webhooks | F10, F11 | ○ |
| 7 | Production readiness | NFRs | ○ |

```
Phase 1 ──▶ Phase 2 ──┬──▶ Phase 4 (needs 2 + 3) ──▶ Phase 5 ──▶ Phase 6
                      └──▶ Phase 3 ───────────────┘
Phase 7 runs alongside from Phase 4 onwards
```

---

## Phase 0: Design and mockups ✅

- System design (HTML/PDF), README, DOCS index.
- Clickable console mockups in [`mockup/`](mockup/README.md).

## Phase 1: Device ingestion ✅

| ID | Item | Status |
|---|---|---|
| R1.1 | Traccar ingestion gateway, Kafka forwarding (`deploy/gateway/traccar.xml`) | ✅ |
| R1.2 | Kafka (KRaft) and topics via `docker-compose.yml` | ✅ |
| R1.3 | `ingest-bridge`: canonical `Position`/`DeviceEvent`, validation, allow-list | ✅ |
| R1.4 | `device-simulator`: GT06, Teltonika, H02, OsmAnd | ✅ |
| R1.5 | First end-to-end run on a developer machine (`mvn verify`, `docker compose --profile simulator up`) | ⏳ needs a machine with Maven Central and Docker Hub access |

## Phase 2: Live backend 🔨

Goal: a browser connected over WebSocket sees every simulated vehicle move within about a second, and every position is stored.

| ID | Item | Acceptance | Status |
|---|---|---|---|
| R2.1 | **Live Hub** (`services/live-hub`): WebSocket endpoint, snapshot on connect, live positions and events, per-session coalescing, viewport filter, pluggable auth (dev token now, JWT later), debug page | Debug page shows simulated vehicles updating live; p95 Kafka-to-socket under 500 ms locally | ✅ written, ⏳ end-to-end run pending |
| R2.2 | `positions.latest` compacted topic (one record per device) fed by the bridge; Live Hub rebuilds its snapshot from it on start | Restarting the Live Hub restores all last positions | ✅ written, ⏳ end-to-end run pending |
| R2.3 ⏳ next | **History Writer**: `positions.raw` → TimescaleDB hypertable (batched, idempotent on device + fix time) | 100 simulated devices at 1 msg/s stored with no gaps | ○ |
| R2.4 | **Position Processor**: dedupe buffered replays, outlier jumps, motion state, `historical` flag for late data | Replayed buffer after reconnect does not move markers backwards | ○ |
| R2.5 | Gateway durability: ACK devices only after Kafka confirms (small Traccar extension) | Kafka outage test loses no acknowledged positions | ○ |
| R2.6 | Road-following test routes: Greater London road extract from Geofabrik, used by the simulator and mockups | Simulated vehicles stay on real roads | ○ waiting for the OSM file in `data/` |

## Phase 3: Core API and sign-in ○

| ID | Item | Acceptance |
|---|---|---|
| R3.1 | Keycloak in compose, realm with roles (viewer, dispatcher, admin) | Login with a test user returns a JWT |
| R3.2 | `core-api` (Spring Boot): tenants, users, groups, vehicles, devices, device-vehicle assignment; PostgreSQL; OpenAPI | CRUD covered by integration tests |
| R3.3 | Device registry feeds the bridge allow-list (`device.registry` topic) instead of static config | New device accepted within seconds of registration |
| R3.4 | Live Hub switches to JWT auth and per-user vehicle scope from Core API | A user only receives vehicles in their groups |
| R3.5 | Audit log of admin actions | Every create/update/delete recorded |

## Phase 4: Web console MVP ○

| ID | Item | Acceptance |
|---|---|---|
| R4.1 | React + TypeScript app scaffold, OIDC login (PKCE), map adapter (MapLibre default, Google optional) | Sign in, see empty map |
| R4.2 | Live map from the mockup: list, filters, selected vehicle drawer, driver card, start/destination | Simulated fleet moves live after login |
| R4.3 | Vehicles, drivers and devices admin pages | Add a vehicle and its tracker appears on the map |
| R4.4 | Deployment of the web app (static hosting/CDN) | Reachable over HTTPS |

## Phase 5: Trips, history and alerts ○

| ID | Item |
|---|---|
| R5.1 | Trip detection (ignition/motion based) and trip list API |
| R5.2 | Route history API with simplification; trip replay in the web app |
| R5.3 | Geofences (PostGIS), enter/exit events |
| R5.4 | Alert rules (overspeed, SOS, offline, geofence) and notifications (email first) |

## Phase 6: Commands, reports and integrations ○

| ID | Item |
|---|---|
| R6.1 | Device commands (request position, immobiliser) with confirmation and audit |
| R6.2 | Reports: trips, stops, mileage, driving time (CSV/PDF export) |
| R6.3 | Public REST API keys and webhooks for integrators |

## Phase 7: Production readiness ○

| ID | Item |
|---|---|
| R7.1 | Kubernetes/Helm, managed Kafka/Postgres/Redis, Terraform |
| R7.2 | TLS everywhere, Kafka auth, secrets management, WAF |
| R7.3 | Observability: Prometheus/Grafana dashboards, OpenTelemetry tracing, alerting |
| R7.4 | Load tests: 100k simulated devices, 10k WebSocket clients |
| R7.5 | Backups, retention policies, GDPR export/erasure |
| R7.6 | Certified device list: real hardware tests for the top 10 tracker models |

## Open questions

- Which tracker models will customers use first? (drives R7.6 and protocol testing)
- Cloud multi-tenant service, on-premise installs, or both? (affects Phase 3 and 7)
- Default map provider and who pays for premium map keys.
