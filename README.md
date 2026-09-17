# Connected Car Backend

[![CI](https://github.com/AllaithAsaad/connected-car-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/AllaithAsaad/connected-car-backend/actions/workflows/ci.yml)

A Java backend that simulates a small connected-vehicle fleet. Three fictional cars near Gothenburg send battery, speed, temperature and GPS readings over HTTP every **five seconds**. The server validates each sample, stores it in **PostgreSQL**, and exposes the fleet and its telemetry through a **REST API**.

**Java 21 · Spring Boot 4.1 · Spring Data JPA · PostgreSQL 17 · Flyway · Docker Compose · Maven**

[Svensk snabbstart](README.sv.md) · [API reference](docs/API.md) · [Architecture](docs/ARCHITECTURE.md)

## Start the entire system

Install Docker with the Compose plugin (for example Docker Desktop), then:

```bash
git clone https://github.com/AllaithAsaad/connected-car-backend.git
cd connected-car-backend
cp .env.example .env
docker compose up --build -d --wait
```

On Windows PowerShell, use `Copy-Item .env.example .env` for the copy step.

No local Java, Maven or PostgreSQL installation is needed for Docker. The first build downloads images and dependencies. Flyway creates the schema and registers `VOLVO-001`, `VOLVO-002` and `VOLVO-003`. The simulator starts after the API and database become healthy.

```bash
curl http://localhost:8080/api/vehicles
curl http://localhost:8080/api/vehicles/VOLVO-001
curl 'http://localhost:8080/api/vehicles/VOLVO-001/telemetry?page=0&size=10'
docker compose logs -f simulator
```

Example vehicle response (values and timestamps change):

```json
{
  "id": "VOLVO-001",
  "status": "ONLINE",
  "lastSeenAt": "2026-09-17T12:00:00Z",
  "latestTelemetry": {
    "id": 1,
    "vehicleId": "VOLVO-001",
    "battery": 72.0,
    "speed": 84.0,
    "temperature": 18.0,
    "latitude": 57.7089,
    "longitude": 11.9746,
    "recordedAt": "2026-09-17T11:59:59Z",
    "receivedAt": "2026-09-17T12:00:00Z"
  }
}
```

The initial state is `OFFLINE` with `latestTelemetry: null`. After the first sample a vehicle becomes `ONLINE`; after 20 seconds without contact it becomes `OFFLINE`. Its historical readings remain available.

## API

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/api/vehicles` | All three vehicles with status and latest measurement |
| GET | `/api/vehicles/{id}` | One vehicle with status and latest measurement |
| GET | `/api/vehicles/{id}/telemetry?page=0&size=20` | Paginated history, newest measurement first |
| POST | `/api/vehicles/{id}/telemetry` | Validate and store one measurement; returns 201 |
| GET | `/actuator/health` | Application and database health |

See [API.md](docs/API.md) for request bodies, units, constraints, pagination and errors.

## Run Java locally

Requires JDK 21 or newer and a PostgreSQL instance. Maven is provided by the wrapper.

```bash
cp .env.example .env
docker compose up -d db
./mvnw spring-boot:run
```

In a second terminal:

```bash
java simulator/VehicleSimulator.java
```

The Java defaults match `.env.example`. If you change database credentials or ports, export the matching `DATABASE_URL`, `POSTGRES_USER` and `POSTGRES_PASSWORD` before starting Java; Spring does not automatically load `.env`. Use `mvnw.cmd` on Windows.

## Tests

```bash
./mvnw verify
```

The tests launch a **real PostgreSQL 17 process** with Zonky Embedded Postgres and exercise the HTTP API, Flyway migration, validation, pagination, out-of-order telemetry, timestamp ties, offline/reconnect behavior, simultaneous ingestion and the actual Java simulator. No Docker is required for these tests. Run as a regular user; PostgreSQL refuses to initialize as root. First use downloads PostgreSQL binaries from Maven Central. Apple Silicon uses a native ARM binary.

GitHub Actions runs the Java tests and separately builds the Docker images, starts all services, checks continuous telemetry, restarts the backend to verify persisted history, and checks that stopped cars become offline.

To smoke-test an already running Docker stack locally (Python 3):

```bash
python3 scripts/smoke_test.py
```

## Configuration and lifecycle

| Variable | Default | Used by |
| --- | --- | --- |
| `POSTGRES_DB` | `connected_car` | Docker database |
| `POSTGRES_USER` | `connected_car` | Database and backend |
| `POSTGRES_PASSWORD` | Local demo value in `.env.example` | Database and backend |
| `API_PORT` | `8080` | Docker host API port |
| `DB_PORT` | `5432` | Docker host database port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/connected_car` | Local Java backend |
| `SERVER_PORT` | `8080` | Local Java backend |
| `VEHICLE_OFFLINE_AFTER` | `20s` | Backend contact timeout |
| `API_BASE_URL` | `http://localhost:8080` | Standalone simulator / smoke test |
| `SIMULATOR_INTERVAL_SECONDS` | `5` | Simulator; also configurable in `.env` for Docker |
| `SIMULATOR_MAX_TICKS` | `0` (run continuously) | Standalone simulator; useful for short runs |

```bash
docker compose stop simulator  # Stop sending; cars become OFFLINE after 20s
docker compose start simulator # Resume sending
docker compose down            # Stop services; keep the PostgreSQL volume
docker compose down -v         # Delete the demo database and all telemetry
```

Ports are bound to `127.0.0.1`. If a port is occupied, change `API_PORT` or `DB_PORT` in `.env`. View startup errors with `docker compose logs backend db`. Changing `POSTGRES_PASSWORD` does not change the password inside an existing database volume; update the database password explicitly or recreate the disposable volume.

## Scope

This is a portfolio/demo backend using fictional telemetry, with no connection to real cars or Volvo systems. The simulator uses synthetic circular routes rather than a road network. The fleet is seeded by a migration; adding vehicles currently requires a database migration. Every valid POST creates a new record (there is no idempotency key). Simulator failures are logged and the next fresh sample is attempted; unsent samples are not buffered.

The API has no authentication and is intended for local use. A production service would need vehicle identity and authorization, TLS, rate limits, idempotency, retention/partitioning and observability. Telemetry is retained until explicitly removed; the fleet-list endpoint is deliberately sized for this three-car demo. A React dashboard can consume the existing API later.

## References

- [Spring Boot documentation and supported Java versions](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/)
- [PostgreSQL documentation](https://www.postgresql.org/docs/17/)
- [Zonky Embedded Postgres](https://github.com/zonkyio/embedded-postgres)
