# Spot On: design pattern proof of concept (exercise 5)

One Spring Boot application that implements the six patterns from `docs/design-patterns.md`,
faithful to the class diagram in `diagrams/class/class-diagram.puml`. Data lives in an in-memory
H2 database created by Flyway on every start, with demo data from `V2__demo_data.sql`.

## Run it

```bash
cd poc
./mvnw verify                                             # build and run all 49 tests
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo    # play the demo scenario, then exit
./mvnw spring-boot:run                                    # REST API on http://localhost:8090
```

The demo prints each step tagged with the pattern it shows. It uses a clock it can move forward
("2 h 10 min later") and a fixed random seed, so the first payment succeeds and the second fails
every time. The race for spot A4 is real: which driver wins changes from run to run.

## Where each pattern is

| Pattern | Role | Class |
|---|---|---|
| Strategy | Strategy | `pricing.PricingStrategy` |
| | Concrete strategies | `HourlyPricingStrategy`, `FlatRatePricingStrategy`, `TieredPricingStrategy`, `NightWeekendPricingStrategy` |
| | Context | `location.ParkingLocation` (built from its `pricing.PricingRule`) |
| | Client | `session.ParkingSessionFacade` |
| Decorator | Component | `pricing.PricingStrategy` (the same interface) |
| | Decorator | `pricing.PricingDecorator` (wraps an `inner` strategy) |
| | Concrete decorators | `BillingIncrementDecorator`, `BaySurchargeDecorator`, `DailyCapDecorator`, `GracePeriodDecorator` |
| | Builds the chain | `pricing.PricingRule.toStrategy(spotType, evCharging)`, inside out: blocks, surcharge, daily cap, grace period |
| State | State | `spot.SpotState` (default methods reject; each state overrides what it allows) |
| | Concrete states | `AvailableState`, `ReservedState`, `OccupiedState`, `OutOfServiceState` |
| | Context | `spot.ParkingSpot` |
| | Error | `spot.IllegalSpotStateTransitionException` → 409 Conflict |
| Observer | Subject | Spring's `ApplicationEventPublisher` |
| | Publishers | `reservation.ReservationService`, `session.ParkingSessionFacade` |
| | Events | `event.DomainEvent` and its six subclasses |
| | Observer | `notification.NotificationEventListener` (runs after the transaction commits) |
| Factory | Factories | `payment.PaymentProcessorFactory`, `notification.NotificationChannelFactory` |
| | Products | `PaymentProcessor` (`Card…`, `Wallet…`), `NotificationChannel` (`Email…`, `Sms…`, `Push…`) |
| Facade | Facade | `session.ParkingSessionFacade` (`startSession`, `endSession`) |
| | Client | `session.ParkingSessionController` |

Every pattern class has a Javadoc line starting with the pattern and its role.

## No double reservations

Two guards, both covered by `ReservationConcurrencyTest` (10 drivers, one spot, one winner):

- `ParkingSpot.version` (`@Version`): when two transactions change the same spot, the second
  commit fails with an optimistic locking error.
- A unique index allowing one ACTIVE reservation per spot (`V1__init.sql`). PostgreSQL would use a
  partial index; H2 has none, so a generated column holds `spot_id` only while the reservation
  is ACTIVE and the unique index is on that column.

Both errors, and an illegal spot transition, become `409 Conflict` with an RFC 7807 body
(`common.GlobalExceptionHandler`).

## Tests

| Test | Covers |
|---|---|
| `pricing.PricingStrategyTest` | Every strategy, every decorator, the order `PricingRule.toStrategy()` wraps them in |
| `spot.SpotStateTest` | All 20 cells of the state × action transition table (a reserved spot can now be closed) |
| `payment.PaymentProcessorFactoryTest`, `notification.NotificationChannelFactoryTest` | Factory output; simulated success and failure |
| `session.ParkingSessionFacadeTest` | Facade orchestration with every subsystem mocked, including a failed payment |
| `reservation.ReservationConcurrencyTest` | Parallel reservations for one spot: exactly one succeeds |
| `reservation.ReservationFlowTest` | Observer end to end: reserve, cancel and expire send notifications |
| `location.ApiErrorTest` | 409, 404 and 400 responses as problem details |

## REST API

Demo IDs: drivers Ana `…0011` and Bogdan `…0012`, Central Garage `…0101`, spots A1–A4 `…1001`–`…1004`,
B1 `…1005`, B2 `…1006` (out of service). Each ID is `00000000-0000-0000-0000-` followed by 12
digits, e.g. `00000000-0000-0000-0000-000000001001`.

| Method and path | Does |
|---|---|
| `POST /api/v1/parking-locations` | Create a location with a pricing rule |
| `POST /api/v1/parking-locations/{id}/spots` | Add a row of spots to a zone |
| `PUT /api/v1/parking-locations/{id}/pricing-rule` | Change the pricing rule (swaps the strategy) |
| `PATCH /api/v1/parking-locations/{id}/spots/{spotId}` | `{"outOfService": true}` closes a spot, `false` reopens it |
| `GET /api/v1/parking-locations/{id}/availability` | Count of spots per status |
| `POST /api/v1/reservations` | `{"driverId", "spotId"}` reserves a spot |
| `DELETE /api/v1/reservations/{id}` | Cancel a reservation |
| `POST /api/v1/parking-sessions` | `{"driverId", "spotId"}` starts a session |
| `POST /api/v1/parking-sessions/{id}/end` | End the session: cost, payment, spot freed, notifications |

```bash
curl -s -X POST localhost:8090/api/v1/reservations -H 'Content-Type: application/json' \
  -d '{"driverId":"00000000-0000-0000-0000-000000000011","spotId":"00000000-0000-0000-0000-000000001001"}'
```

These endpoints belong to the PoC only. The Postman collection covers the microservices
(stage 2) through the gateway.

## Simplifications

- No authentication: requests name the driver directly.
- DTOs are Java records with a static `from()` instead of MapStruct; with nine small DTOs the
  mapping is clearer written out.
- Payments and notification channels are simulated and write to the log.
