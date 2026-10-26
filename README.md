# Spot On — Smart Parking Management System

Drivers find available parking spaces, reserve spots, start and end parking sessions, and pay.
Administrators manage parking locations, spaces, prices, and availability.

## Prerequisites

- Java 21 (Maven is not needed; each module ships the Maven wrapper `./mvnw`)
- Docker with Docker Compose
- Node.js 22 (for the frontend dev server and to run the Postman collection with Newman)

## Run everything

```bash
docker compose up --build
```

| Component | URL |
|---|---|
| Frontend (driver app, admin dashboard at `/admin`) | http://localhost:3000 |
| Gateway (single entry point) | http://localhost:8080 |
| parking-service | http://localhost:8081 |
| reservation-service | http://localhost:8082 |
| payment-service | http://localhost:8083 |
| notification-service | http://localhost:8084 |
| RabbitMQ management UI | http://localhost:15672 (guest / guest) |
| PostgreSQL | localhost:5432 (smartparking / smartparking) |

Each service exposes Swagger UI at `/swagger-ui.html` and its health at `/actuator/health` on its own port.

Stop with `docker compose down`; add `-v` to also wipe the database.

Settings you may want to change for a demo (environment variables in `docker-compose.yml`):

| Variable | Service | Default | Effect |
|---|---|---|---|
| `RESERVATION_DURATION` | reservation-service | `15m` | How long a reserved spot is held |
| `PAYMENT_FAILURE_RATE` | payment-service | `0.2` | Share of simulated payments that fail (0 to 1) |
| `NOTIFICATION_CHANNEL` | notification-service | `PUSH` | `EMAIL`, `SMS` or `PUSH` (all simulated in the logs) |

The gateway routes only the public API. parking-service's `/api/v1/spots/{id}/reserve|occupy|release`
is internal: reservation-service calls it directly on the Docker network.

## Messaging (RabbitMQ)

Services announce what happened on the topic exchange `parking.events`; the others react on their own
time. Open the management UI at http://localhost:15672 (guest / guest) and watch these queues while
using the app:

| Queue | Consumer | Receives |
|---|---|---|
| `parking.spot-holds` | parking-service | `reservation.cancelled`, `reservation.expired`, `session.completed` (frees the spot) |
| `reservation.payment-results` | reservation-service | `payment.completed`, `payment.failed` (updates the receipt) |
| `payment.session-completed` | payment-service | `session.completed` (charges the driver) |
| `notification.events` | notification-service | reservation, session and payment events |
| `reservation.expiry-delay` | — | one message per reservation; when its TTL runs out it moves to `reservation.expiry` |

To see a queue hold messages, stop a consumer (`docker compose stop notification-service`), reserve and
cancel a spot, and start it again: the waiting events are delivered and the notifications appear.
Set `RESERVATION_DURATION=1m` on reservation-service to watch a reservation expire through the TTL queue.

## Develop one service

```bash
docker compose up -d postgres rabbitmq   # infrastructure only
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

## API tests

```bash
npx newman run postman/smart-parking.postman_collection.json -e postman/local.postman_environment.json
```

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
