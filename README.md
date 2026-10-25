# Spot On — Smart Parking Management System

Drivers find available parking spaces, reserve spots, start and end parking sessions, and pay.
Administrators manage parking locations, spaces, prices, and availability.

## Prerequisites

- Java 21 (Maven is not needed; each module ships the Maven wrapper `./mvnw`)
- Docker with Docker Compose
- Node.js (only to run the Postman collection with Newman)

## Run everything

```bash
docker compose up --build
```

| Component | URL |
|---|---|
| Gateway (single entry point) | http://localhost:8080 |
| parking-service | http://localhost:8081 |
| reservation-service | http://localhost:8082 |
| payment-service | http://localhost:8083 |
| notification-service | http://localhost:8084 |
| RabbitMQ management UI | http://localhost:15672 (guest / guest) |
| PostgreSQL | localhost:5432 (smartparking / smartparking) |

Each service exposes Swagger UI at `/swagger-ui.html` and its health at `/actuator/health` on its own port.

Stop with `docker compose down`; add `-v` to also wipe the database.

## Develop one service

```bash
docker compose up -d postgres rabbitmq   # infrastructure only
cd parking-service
./mvnw spring-boot:run                   # run against local infrastructure
./mvnw clean verify                      # build and test (Testcontainers needs Docker)
```

## API tests

```bash
npx newman run postman/smart-parking.postman_collection.json -e postman/local.postman_environment.json
```

## Repository layout

| Path | Contents |
|---|---|
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
