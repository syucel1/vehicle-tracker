# Vehicle Tracker Platform

Real-time vehicle tracking: GPS tracker devices connect to a Java backend, which decodes their reports (200+ GPS protocols), stores them, and streams live positions over WebSocket to an authenticated web management application that shows the fleet on a map.

> **Status:** Phase 1 in progress — device ingestion and the Kafka backbone (6 Nov 2026).

## Documentation

| Document | Format | Description |
|---|---|---|
| System design | [HTML](vehicle-tracker-design.html) · [PDF](vehicle-tracker-design.pdf) | Architecture, components, data flow, WebSocket/REST contracts, data model, security, scaling, risks |
| Console mockups | [mockup/](mockup/README.md) | Clickable HTML mockups of the web app (live map with multiple vehicles, selected vehicle with driver and trip start/end, trip history, fleet admin) on Google Maps |
| Roadmap | _coming next_ | Phases, milestones, team, estimates |

See [DOCS.md](DOCS.md) for the documentation index.

---

## Phase 1: device ingestion

Phase 1 covers only: **tracker device ⇄ backend communication, protocol decoding, Kafka, and the Java service that turns gateway output into canonical messages.** No database, API, auth or UI yet.

```
 GPS trackers ──TCP/UDP 5001-5268──▶ gateway (Traccar)  ──▶ Kafka: traccar.positions / traccar.events
                                     decodes 200+ protocols         │
                                     ACKs devices                   ▼
                                                            ingest-bridge (Spring Boot)
                                                            validate · map · allow-list
                                                                    │
                          ┌─────────────────┬──────────────────────┼───────────────────┐
                          ▼                 ▼                      ▼                   ▼
                    positions.raw     device.events         device.unknown    positions.rejected
                    (canonical)       (online, ignition…)   (unregistered)    (bad data, kept for debugging)
```

| Component | Path | What it does |
|---|---|---|
| **Ingestion gateway** | [`deploy/gateway/traccar.xml`](deploy/gateway/traccar.xml) | Traccar (Apache 2.0) runs as a stateless protocol gateway: listens on one port per protocol, decodes, ACKs devices, forwards every position and event to Kafka. |
| **Kafka** | [`docker-compose.yml`](docker-compose.yml) | Single-node KRaft broker; topics created by `kafka-init`, 12 partitions each, keyed by device. |
| **ingest-bridge** | [`services/ingest-bridge`](services/ingest-bridge) | Consumes gateway topics in batches, converts to the canonical schema, validates, applies the device allow-list, re-keys by IMEI, publishes. At-least-once: offsets are committed only after Kafka acknowledges the output. |
| **tracker-model** | [`libs/tracker-model`](libs/tracker-model) | Shared canonical message contracts (`Position`, `DeviceEvent`, `UnknownDevice`, `RejectedMessage`) and topic names. |
| **device-simulator** | [`tools/device-simulator`](tools/device-simulator) | Simulated trackers speaking the real wire protocols (GT06, Teltonika Codec 8, H02, OsmAnd), driving around London. JDK only, no dependencies. |

### Quick start (Docker)

Requirements: Docker Desktop (or Docker Engine + Compose v2).

```bash
docker compose up -d --build                        # Kafka + gateway + ingest-bridge
docker compose --profile simulator up -d --build    # add 8 simulated trackers

# watch canonical positions arrive (key = IMEI)
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic positions.raw --property print.key=true

docker compose logs -f ingest-bridge simulator
curl http://localhost:8081/actuator/health
curl -s http://localhost:8081/actuator/prometheus | grep tracker_bridge
```

Point a real tracker at `<your-host>:<protocol port>` (e.g. GT06 → 5023, Teltonika → 5027, Queclink → 5004, OsmAnd/Traccar Client → 5055). The full port list is on [traccar.org/devices](https://www.traccar.org/devices/).

> Publishing 536 ports (5001-5268 TCP + UDP) can take a while on Docker Desktop. If you only need a few protocols, narrow the `ports:` of the `gateway` service.

### Build and test without Docker

Requirements: **Java 21+** and **Maven 3.9+**.

```bash
mvn verify                    # builds all modules, runs unit tests + embedded-Kafka integration test
```

Run the bridge against the Docker Kafka (`localhost:29092`) and the simulator against the Docker gateway:

```bash
java -jar services/ingest-bridge/target/ingest-bridge.jar
java -jar tools/device-simulator/target/device-simulator.jar --devices 20 --interval 2
java -jar tools/device-simulator/target/device-simulator.jar --help
```

### Kafka topics

| Topic | Key | Value | Producer |
|---|---|---|---|
| `traccar.positions` | gateway device id | Traccar `PositionData` JSON | gateway |
| `traccar.events` | gateway device id | Traccar `EventData` JSON | gateway |
| `positions.raw` | IMEI | [`Position`](libs/tracker-model/src/main/java/com/vehicletracker/model/Position.java) v1 | ingest-bridge |
| `device.events` | IMEI | [`DeviceEvent`](libs/tracker-model/src/main/java/com/vehicletracker/model/DeviceEvent.java) v1, e.g. `DEVICE_ONLINE`, `IGNITION_ON`, `ALARM` | ingest-bridge |
| `device.unknown` | IMEI | [`UnknownDevice`](libs/tracker-model/src/main/java/com/vehicletracker/model/UnknownDevice.java), at most once per device per 10 min | ingest-bridge |
| `positions.rejected` | IMEI or source key | [`RejectedMessage`](libs/tracker-model/src/main/java/com/vehicletracker/model/RejectedMessage.java) with reason and original payload | ingest-bridge |

Example `positions.raw` value:

```json
{"schemaVersion":1,"deviceUid":"359000000000015","protocol":"gt06",
 "fixTime":"2026-11-06T18:27:04Z","serverTime":"2026-11-06T18:27:05.112Z","receivedAt":"2026-11-06T18:27:05.140Z",
 "valid":true,"latitude":51.50735,"longitude":-0.12776,"altitudeM":0.0,"speedKph":42.0,"course":118.0,
 "satellites":9,"ignition":true,"motion":true,"odometerM":4200000.0,
 "attributes":{"rssi":4,"distance":12.37,"totalDistance":81234.5},"gatewayDeviceId":7}
```

### Configuration (ingest-bridge)

| Environment variable | Default | Meaning |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Kafka brokers |
| `TRACKER_DEVICES_MODE` | `OPEN` | `OPEN` accepts every device; `ALLOW_LIST` accepts only `TRACKER_DEVICES_ALLOWED` and reports others on `device.unknown` |
| `TRACKER_DEVICES_ALLOWED` | _(empty)_ | Comma-separated IMEIs |
| `SERVER_PORT` | `8081` | Health/metrics port |

All other settings are in [`application.yml`](services/ingest-bridge/src/main/resources/application.yml) (`tracker.*`).

### Validation rules (bridge)

Rejected to `positions.rejected` with a reason: unparseable JSON, missing device ID, missing or out-of-range coordinates, `0,0` ("null island"), unparseable timestamps, fix time more than 5 minutes in the future. Fixes reported as invalid by the device (`valid=false`) are passed through flagged; deduplication and outlier filtering belong to the downstream position processor (Phase 2).

### Known limitations of Phase 1

- **Durability gap at the gateway.** Traccar ACKs the device when it decodes a packet and then hands the position to its Kafka producer (`acks=all`, with client-side retries) without waiting for the broker. A gateway crash during a long Kafka outage could lose buffered positions. Closing this gap (ACK only after Kafka confirms) needs a small gateway extension; planned for Phase 2.
- Gateway state is in an embedded H2 database inside the gateway container (device sessions and auto-registered devices only — no business data).
- Single Kafka broker, no TLS/auth on Kafka. Fine for development, not for production.
- Device registry is a static allow-list until the Core API exists.

---

## Repository layout

```
vehicle-tracker/
├── pom.xml                       # Maven parent (Java 21, Spring Boot 4.1)
├── docker-compose.yml            # Kafka, gateway, ingest-bridge, simulator profile
├── deploy/gateway/traccar.xml    # gateway configuration
├── libs/tracker-model/           # canonical message contracts
├── services/ingest-bridge/       # Spring Boot service
├── tools/device-simulator/       # protocol simulator
├── README.md · DOCS.md
└── vehicle-tracker-design.html / .pdf
```

## Technology stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring for Apache Kafka |
| Protocol decoding | Traccar 6.x as ingestion gateway (Apache 2.0) |
| Messaging | Apache Kafka 4.1 (KRaft) |
| Planned (later phases) | PostgreSQL + TimescaleDB + PostGIS, Redis, Keycloak, React + MapLibre, Kubernetes |
| Observability | Spring Boot Actuator, Micrometer → Prometheus |

## Licence notes

Traccar is licensed under Apache 2.0. Phase 1 uses the unmodified official Docker image, configured through `traccar.xml`. Any future modification must retain its licence and NOTICE files and mark modified files.
