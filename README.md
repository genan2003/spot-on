# Spot On — Smart Parking Management System

[![CI/CD](https://github.com/genan2003/spot-on/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/genan2003/spot-on/actions/workflows/ci-cd.yml)

Drivers find available parking spaces, reserve spots, start and end parking sessions, and pay.
Administrators manage parking locations, spaces, prices, and availability.

## Prerequisites

- Docker with Docker Compose v2 (that is all you need to run the system)
- Node.js 22, to run the Postman collection with Newman (and the frontend dev server)
- Java 21, only to build or test a service outside Docker (Maven is not needed; each module ships `./mvnw`)

## Quick start

From the repository root:

```bash
# 1. Build and start everything; returns once every service reports healthy (first build: a few minutes)
docker compose up -d --build --wait

# 2. Run the API tests through the gateway (all requests should pass)
npx newman run postman/smart-parking.postman_collection.json -e postman/local.postman_environment.json

# 3. Stop (add -v to also delete the databases and start from scratch next time)
docker compose down
```

Then open the driver app at http://localhost:3000 (admin dashboard at http://localhost:3000/admin).
A new database has no car parks yet: create one in the admin dashboard (add a location, then its spots).

| Component | URL |
|---|---|
| Frontend (driver app, admin dashboard at `/admin`) | http://localhost:3000 |
| Gateway (single entry point, all `/api/v1/**` calls) | http://localhost:8080 |
| parking-service | http://localhost:8081 |
| reservation-service | http://localhost:8082 |
| payment-service | http://localhost:8083 |
| notification-service | http://localhost:8084 |
| RabbitMQ management UI | http://localhost:15672 (guest / guest) |
| Zipkin (traces) | http://localhost:9411 |
| PostgreSQL | localhost:5432 (smartparking / smartparking) |

Each service exposes Swagger UI at `/swagger-ui.html` and its health at `/actuator/health` on its own port.
`docker compose ps` shows whether every container is healthy; `docker compose logs -f payment-service`
follows one service's log (simulated payments are logged by payment-service, notifications by notification-service).

Troubleshooting:
- **A port is already in use:** stop whatever uses ports 3000, 5432, 5672, 8080–8084, 9411 or 15672
  (often a local PostgreSQL), or change the left side of the port mapping in `docker-compose.yml`.
- **`--wait` fails or a service keeps restarting:** check `docker compose logs <service>`. If the database
  was created by an older version of the project, start clean with `docker compose down -v`.
- **Newman reports failures right after a restart:** the stack must be healthy first; `--wait` takes care
  of that, otherwise rerun once `docker compose ps` shows every service as healthy.

Settings you may want to change for a demo. Set them in the shell when starting, e.g.
`PAYMENT_FAILURE_RATE=1 docker compose up -d payment-service` makes every payment fail:

| Variable | Service | Default | Effect |
|---|---|---|---|
| `RESERVATION_DURATION` | reservation-service | `15m` | How long a reserved spot is held |
| `RESERVATION_REMINDER` | reservation-service | `5m` | How long before the hold ends the driver is reminded (at most half the hold) |
| `PAYMENT_FAILURE_RATE` | payment-service | `0.2` | Share of simulated payments that fail (0 to 1) |
| `NOTIFICATION_CHANNEL` | notification-service | `PUSH` | `EMAIL`, `SMS` or `PUSH` (all simulated in the logs) |
| `RESERVATION_RATE_LIMIT` | gateway | `5` | Reservation attempts per driver in a row; then one every 60 / n seconds (429 with `Retry-After`) |
| `PARKING_CIRCUIT_OPEN_FOR` | reservation-service | `30s` | How long the circuit breaker to parking-service stays open after repeated failures |

The gateway routes only the public API. parking-service's `/api/v1/spots/{id}/reserve|occupy|release`
is internal: reservation-service calls it directly on the Docker network.

## Messaging (RabbitMQ)

Services announce what happened on the topic exchange `parking.events`; the others react on their own
time. Open the management UI at http://localhost:15672 (guest / guest) and watch these queues while
using the app:

| Queue | Consumer | Receives |
|---|---|---|
| `parking.spot-holds` | parking-service | `reservation.cancelled`, `reservation.expired`, `reservation.changed` (a moved reservation), `session.completed` (frees the spot) |
| `reservation.payment-results` | reservation-service | `payment.completed`, `payment.failed` (updates the receipt) |
| `reservation.spot-closures` | reservation-service | `spot.closed` (an admin closed a reserved spot: cancels the reservation) |
| `payment.session-completed` | payment-service | `session.completed` (charges the driver) |
| `notification.events` | notification-service | reservation, session and payment events |
| one temporary queue per notification-service instance | notification-service | `notification.sent`: forwarded to the driver's open live stream (`GET /api/v1/notifications/stream`, Server-Sent Events) |
| `reservation.expiry-delay` | — | one message per reservation (two if it was extended); when its TTL runs out it moves to `reservation.expiry` |
| `reservation.reminder-delay` | — | the same for the "expires soon" reminder: TTL = hold time − 5 min, then `reservation.reminders` → `reservation.expiring` |

| `parking-service.dlq`, `reservation-service.dlq`, `payment-service.dlq`, `notification-service.dlq` | — (kept for people) | messages a service still couldn't handle after 3 attempts, via exchange `parking.dead-letters` |

**Dead letters.** A listener that fails is retried 3 times, 1 second apart. Then the message goes to its
service's `*.dlq` queue instead of being dropped, with headers `x-original-exchange`, `x-original-routingKey`
and `x-exception-message`. To look at one: RabbitMQ UI → Queues → e.g. `notification-service.dlq` → Get
messages. To replay it once the cause is fixed: Exchanges → `parking.events` → Publish message, with the
routing key from `x-original-routingKey` and the same payload (content type `application/json`); then take it
off the dead-letter queue (Get messages with "Automatic ack", or Purge).

To see a queue hold messages, stop a consumer (`docker compose stop notification-service`), reserve and
cancel a spot, and start it again: the waiting events are delivered and the notifications appear.
Set `RESERVATION_DURATION=1m` on reservation-service to watch a reservation expire through the TTL queue; the
reminder then arrives after 30 seconds. Purge `reservation.expiry-delay` and `reservation.reminder-delay` first
(`docker compose exec rabbitmq rabbitmqctl purge_queue <name>`): a delay queue releases messages only from its
head, so new 1-minute timers would wait behind 15-minute ones from earlier runs (the minute sweep catches them late).

## Tracing (Zipkin)

Every request is traced from the gateway through each service it reaches, over REST and over RabbitMQ
(Micrometer Tracing with Brave; the trace id travels in the `traceparent` header of HTTP requests and messages).
Do something in the app, open http://localhost:9411 and press "Run query": "End session", for example, shows
one trace across all five services (gateway → reservation-service → parking-service, then `session.completed` to
payment-, parking- and notification-service, then `payment.completed` back). Every log line carries
`[traceId-spanId]`, so `docker compose logs | grep <traceId>` shows the same request in the logs.
Health checks and timer runs are not traced. Spans live in Zipkin's memory, so a restart clears them.

## Develop one service

```bash
docker compose up -d postgres rabbitmq zipkin   # infrastructure only (Zipkin is optional)
cd parking-service
./mvnw spring-boot:run                   # run against local infrastructure
./mvnw clean verify                      # build and test (Testcontainers needs Docker)
```

## Frontend

React, Vite, TypeScript and Tailwind, in `frontend/`.

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, proxies /api and /ws to the gateway on 8080
npm run lint
npm run build
```

`frontend/.env.development` sets `VITE_USE_MOCKS=true`, so the dev server runs on in-memory demo data
(three car parks in Cluj-Napoca, other drivers coming and going, a 20% simulated payment failure rate).
Demo data resets when the page reloads. Set it to `false` to use the real services through the gateway.
The Docker image always uses the real services.

## API tests (Postman)

`postman/smart-parking.postman_collection.json` covers every public endpoint, one folder per service,
in the order of a driver's day: an admin sets up a car park, a driver reserves, cancels, reserves again,
parks, leaves and pays, then reads their notifications. Every request has test assertions; IDs are passed
between requests through `postman/local.postman_environment.json` (`{{baseUrl}}` is the gateway).
Each run uses a new driver id, so it can be repeated against the same database.

Steps that happen through RabbitMQ (the spot being freed, the payment, notifications) are not instant:
those requests repeat themselves for up to a few seconds until the event has been processed.

```bash
npx newman run postman/smart-parking.postman_collection.json -e postman/local.postman_environment.json
```

In the Postman app: import both files, select the "Smart Parking - local" environment and run the
collection with the Collection Runner (run the folders in order; later requests use earlier IDs).

## CI/CD (GitHub Actions)

`.github/workflows/ci-cd.yml` runs on every push and pull request to `main`:

| Job | Runs on | What it does |
|---|---|---|
| Build and test (one per module) | GitHub-hosted | `./mvnw verify` in each service, the gateway and the PoC: unit tests and Testcontainers integration tests against real PostgreSQL and RabbitMQ. A failed build keeps its test reports as an artifact. |
| Lint and build frontend | GitHub-hosted | `npm ci`, `npm run lint`, `npm run build` |
| Build Docker images | GitHub-hosted | `docker compose build`, once every module passed |
| Deploy to local Docker and smoke test | the team's self-hosted runner | Only for `main` (push, or "Run workflow" in the Actions tab): `docker compose up -d --build --wait` replaces the running stack and waits until every service is healthy, then the Postman collection runs against it with Newman. On failure the job prints `docker compose ps` and the services' logs. |

Pull requests run the first three jobs only, so nothing is deployed before review. Deployments run one at
a time.

### Setting up the self-hosted runner (once, on the machine that hosts the demo)

1. Install Docker (with Compose v2) and make sure the runner's user can use it without `sudo`:
   `sudo usermod -aG docker $USER`, then log out and in again.
2. In the GitHub repository: Settings → Actions → Runners → New self-hosted runner → Linux. Run the
   download and `./config.sh --url ... --token ...` commands shown there and keep the default labels
   (the deploy job asks for `self-hosted`).
3. Install it as a service so it keeps running after a reboot: `sudo ./svc.sh install` and `sudo ./svc.sh start`.
   Settings → Actions → Runners should now show it as Idle.
4. Keep ports 3000, 5432, 5672, 8080–8084 and 15672 free on that machine. A stack started by hand from this
   repository is the same Compose project (`smart-parking`), so a deployment simply replaces it.

Keep the repository private (or require approval for workflows from outside contributors): a
self-hosted runner executes the workflow code it is given on your machine.

## Repository layout

| Path | Contents |
|---|---|
| `frontend/` | Driver app and admin dashboard |
| `gateway/` | Spring Cloud Gateway, routes `/api/v1/**` to the services |
| `parking-service/` | Locations, spots, pricing rules, availability, real-time status |
| `reservation-service/` | Reservations, expiration, parking sessions, cost calculation |
| `payment-service/` | Simulated payment processing |
| `notification-service/` | Consumes events and sends simulated notifications |
| `poc/` | Stage 1 design pattern proof-of-concept |
| `infra/postgres/` | Creates one database per service on first start |
| `postman/` | Postman collection and local environment |
| `diagrams/` | PlantUML sources (class, sequence, architecture) |
| `docs/` | Design pattern justification and architecture comparison |
| `.github/workflows/` | CI/CD pipeline |
