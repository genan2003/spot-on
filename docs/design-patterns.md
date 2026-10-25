# Design patterns (exercises 1–2)

This document lists the domain model of the Smart Parking Management System and the five design
patterns the system is built on. For each pattern it explains the concrete problem in this project,
how the pattern solves it, and why it is better than the simpler alternative.

The class names below are the ones used in the class diagram (`diagrams/class/class-diagram.puml`)
and in the proof of concept (`poc/`). If one of the three changes, update the other two.

## Domain model

| Class | Responsibility | Key attributes |
|---|---|---|
| `ParkingLocation` | A car park managed by an administrator | `id`, `name`, `address`, `latitude`, `longitude`, `pricingRule`, `spots` |
| `ParkingSpot` | One bay in a location | `id`, `code` (e.g. `B4`), `level`, `zone`, `type`, `evCharging`, `accessible`, `state`, `version` |
| `SpotType` (enum) | Size category of a spot | `STANDARD`, `COMPACT`, `LARGE`, `MOTORCYCLE` |
| `PricingRule` | The pricing an administrator configured for a location: which strategy, with which rates | `strategyType`, `hourlyRate`, `flatRate`, `firstHourRate`, `laterHourRate`, `dayRate`, `nightWeekendRate` |
| `User` (abstract) | Someone who uses the system | `id`, `name`, `email` |
| `Driver` | Finds, reserves and pays for spots | `phone`, `preferredChannel`, `preferredPaymentMethod` |
| `Admin` | Manages locations, spots and pricing | — |
| `Reservation` | A driver's hold on a spot | `id`, `driver`, `spot`, `status`, `createdAt`, `expiresAt` |
| `ParkingSession` | The time a car actually spends in a spot | `id`, `driver`, `spot`, `reservation` (optional), `entryTime`, `exitTime`, `cost`, `status` |
| `Payment` | Payment for one completed session | `id`, `session`, `amount`, `method`, `status`, `processedAt` |
| `Notification` | A message sent to a driver | `id`, `recipient`, `channelType`, `message`, `sentAt` |

Supporting enums: `PricingStrategyType` (`HOURLY`, `FLAT_RATE`, `TIERED`, `NIGHT_WEEKEND`),
`ReservationStatus` (`ACTIVE`, `CANCELLED`, `EXPIRED`, `COMPLETED`),
`SessionStatus` (`ACTIVE`, `COMPLETED`), `PaymentStatus` (`PENDING`, `COMPLETED`, `FAILED`),
`PaymentMethod` (`CARD`, `WALLET`), `ChannelType` (`EMAIL`, `SMS`, `PUSH`),
`SpotStatus` (`AVAILABLE`, `RESERVED`, `OCCUPIED`, `OUT_OF_SERVICE`, stored in the database to
restore the spot's state object).

Money is `BigDecimal`, timestamps are `Instant`, lengths of time are `Duration`.

Application layer (also in the class diagram):

| Class | Responsibility |
|---|---|
| `ParkingLocationController`, `ReservationController`, `ParkingSessionController` | Thin REST controllers |
| `ParkingLocationService` | Create locations, add spots, change pricing rules and spot availability |
| `ReservationService` | Reserve and cancel spots, expire overdue reservations |
| `ReservationExpirationScheduler` | Runs `ReservationService.expireOverdue()` on a `@Scheduled` timer |
| `ParkingSessionFacade` | Starts and ends parking sessions (Facade, see below) |
| `GlobalExceptionHandler` | `@RestControllerAdvice`: turns conflicts (illegal spot transition, lost concurrent reservation) into `409 Conflict` with a `ProblemDetail` |
| `DriverRepository`, `ParkingSpotRepository`, `ReservationRepository`, `ParkingSessionRepository`, `PaymentRepository`, `NotificationRepository` | Spring Data JPA repositories |

## Overview

| Pattern | Kind | Where | Problem it solves |
|---|---|---|---|
| Strategy | Behavioural | Pricing | Each location charges differently, and the rule can change at runtime |
| State | Behavioural | Spot lifecycle | Which actions are allowed depends on the spot's current status |
| Observer | Behavioural | Domain events | Many parts of the system react to reservations, sessions and payments |
| Factory | Creational | Payments, notifications | Picking the right processor or channel for a payment method or driver preference |
| Facade | Structural | Ending a session | Ending a session touches five subsystems in a fixed order |

---

## Strategy: pricing rules

### Problem

Every location sets its own pricing, and the admin can change it at any time. The system supports
four rules:

- **Hourly:** every started hour costs the same.
- **Flat rate:** one price per visit, however long.
- **Tiered:** the first hour has its own price, then every later started hour costs a second price.
- **Night and weekend:** an hourly day rate, and a different hourly rate when the session starts
  between 20:00 and 08:00 or on a weekend.

The cost of a session must be computed by the rule of its location, and new rules (for example an
event-day rate) should not require changing the code that ends a session.

### Solution

| Role | Class |
|---|---|
| Strategy (interface) | `PricingStrategy` with `BigDecimal calculateCost(Instant entryTime, Instant exitTime)` |
| Concrete strategies | `HourlyPricingStrategy`, `FlatRatePricingStrategy`, `TieredPricingStrategy`, `NightWeekendPricingStrategy` |
| Context | `ParkingLocation`, which exposes `getPricingStrategy()` built from its `PricingRule` |
| Client | `ParkingSessionFacade`, which asks the location's strategy for the cost when a session ends |

Each concrete strategy keeps its own rates as fields and contains only its own formula.
`PricingRule.toStrategy()` turns the stored configuration (strategy type + rates) into the matching
strategy object, so the database stores plain values while the code works with behaviour.

```java
BigDecimal cost = location.getPricingStrategy().calculateCost(session.getEntryTime(), exitTime);
```

### Why not a simpler alternative

The simple version is one `calculateCost` method with a `switch` on a `pricingType` enum.

- **Adding a rule:** with a `switch`, it means editing that shared method and retesting all rules.
  With Strategy it is one new class, and existing classes stay untouched (open/closed principle).
- **Testing:** each strategy is a small class that can be tested on its own with fixed timestamps.
  A `switch` mixes every formula in one method.
- **Clutter:** the rates each rule needs (one rate for hourly, two for tiered) live inside their own
  strategy. They don't pile up as nullable fields that every branch has to check.
- **Switching rules:** an admin changing a location from hourly to tiered just swaps the strategy
  object; no caller changes.

---

## State: parking spot lifecycle

### Problem

A spot is always in exactly one of four states: available, reserved, occupied or out of service.
What may happen to it depends on that state:

| From \ action | `reserve()` | `occupy()` | `release()` | `markOutOfService()` | `returnToService()` |
|---|---|---|---|---|---|
| Available | → Reserved | → Occupied | not allowed | → Out of service | not allowed |
| Reserved | not allowed | → Occupied | → Available | not allowed | not allowed |
| Occupied | not allowed | not allowed | → Available | not allowed | not allowed |
| Out of service | not allowed | not allowed | not allowed | not allowed | → Available |

`release()` covers a cancelled or expired reservation (Reserved → Available) and a car leaving
(Occupied → Available). The most important rule in the project depends on this table:
**a spot that is reserved or occupied must never be reserved again.**

### Solution

| Role | Class |
|---|---|
| State (interface) | `SpotState` with `reserve`, `occupy`, `release`, `markOutOfService`, `returnToService`, each taking the `ParkingSpot` |
| Concrete states | `AvailableState`, `ReservedState`, `OccupiedState`, `OutOfServiceState` |
| Context | `ParkingSpot`, which holds its current `SpotState` and delegates each action to it |
| Error | `IllegalSpotStateTransitionException`, thrown for every "not allowed" cell |

```java
public void reserve() { state.reserve(this); }   // in ParkingSpot

// in AvailableState
public void reserve(ParkingSpot spot) { spot.changeState(new ReservedState()); }

// in OccupiedState
public void reserve(ParkingSpot spot) {
    throw new IllegalSpotStateTransitionException(spot, "reserve");
}
```

The spot's status is stored as the `SpotStatus` enum column, and the state object is rebuilt from it
when the spot is loaded. Whether the driver starting a session is the one who reserved the spot is
checked by the service, because it needs the reservation, which the spot doesn't know about.

State guards the rules inside the application. Two drivers reserving the same spot at the same
moment is handled at the database level: optimistic locking (`@Version` on `ParkingSpot`) and a
partial unique index on active reservations, with the losing request getting `409 Conflict`.

### Why not a simpler alternative

The simple version is a `status` enum on the spot and `if` checks in each service method
(`if (spot.getStatus() != AVAILABLE) throw ...`).

- **Scattered checks:** the same checks get repeated in the reservation, session and admin code,
  and forgetting one creates a real bug, such as reserving an occupied spot. With State, each rule
  is written once, in the class for that state.
- **Readable rules:** the transition table above maps one-to-one onto the four classes, so it can be
  read and tested state by state.
- **Adding a state:** a new state (for example "being cleaned") is a new class, not a change to
  every `if` in the codebase.

---

## Observer: domain events

### Problem

When something important happens, several other parts of the system must react:

| Event | Who reacts |
|---|---|
| Reservation created | The driver is notified |
| Reservation cancelled | The driver is notified; the spot is released |
| Reservation expired | The driver is notified; the spot is released |
| Session completed | Payment is started; the driver is notified |
| Payment completed or failed | The driver is notified; the session records the payment result |

The table describes the microservices. The proof of concept runs in one process, so it keeps the
steps that must succeed together inside one transaction: the reservation service releases the spot
itself, and `ParkingSessionFacade` starts the payment directly. There, only notifications are
observers.

If the code that creates a reservation also had to call every one of these reactions, it would
depend on all of them, and adding a new reaction (for example an audit log) would mean editing it.

### Solution

| Role | In the proof of concept | In the microservices |
|---|---|---|
| Subject (publisher) | Services publish through Spring's `ApplicationEventPublisher` | Services publish to the RabbitMQ topic exchange `parking.events` |
| Events | `ReservationCreatedEvent`, `ReservationCancelledEvent`, `ReservationExpiredEvent`, `SessionCompletedEvent`, `PaymentCompletedEvent`, `PaymentFailedEvent` | The same events as JSON messages, with routing keys like `reservation.created` |
| Observers (listeners) | `NotificationEventListener` (`@EventListener` methods) | One queue per consuming service, with a `@RabbitListener` |

Publishers only announce what happened. They don't know who listens or how many listeners there are.

### Why not a simpler alternative

The simple version is direct calls, for example `ReservationService` calling
`notificationService.send(...)` after saving a reservation.

- **Adding a reaction:** with direct calls, the publisher depends on every consumer and must change
  for each new one. With Observer, a new reaction is a new listener, and the publisher stays as it is.
- **Failures and outages:** in the microservices, a direct call would make reserving a spot fail
  when the notification service is down. With events in a queue, the reservation succeeds and the
  notification is delivered when the service is back (exercise 8).
- **Testing:** each listener can be tested by handing it an event, without running the code that
  produced it.

---

## Factory: payment processors and notification channels

### Problem

A payment is processed differently for a card and for a wallet, and a notification is sent
differently by email, SMS and push (all simulated in this project). The code that pays for a
session, or notifies a driver, should not have to know how each one is built or which one to pick.

### Solution

| Role | Payments | Notifications |
|---|---|---|
| Factory | `PaymentProcessorFactory.create(PaymentMethod)` | `NotificationChannelFactory.create(ChannelType)` |
| Product (interface) | `PaymentProcessor` with `PaymentResult process(Payment)` | `NotificationChannel` with `void send(Notification)` |
| Concrete products | `CardPaymentProcessor`, `WalletPaymentProcessor` | `EmailNotificationChannel`, `SmsNotificationChannel`, `PushNotificationChannel` |
| Client | `ParkingSessionFacade` (PoC), payment-service (microservices) | `NotificationEventListener` |

The driver's `preferredPaymentMethod` and `preferredChannel` decide which product the factory
returns. The payment processors simulate success or failure with a configurable failure rate, so the
"payment failed" path can be shown in the demo.

This is the *simple factory* form: one factory class with a method that returns the right product
for a parameter. It is not GoF *Factory Method*, which uses a subclass per creator. A simple factory
is enough here because the choice depends on a runtime value (the method or channel), not on which
subclass of creator is running.

### Why not a simpler alternative

The simple version is `new CardPaymentProcessor()` or a `switch` wherever a payment is made.

- **Adding a type:** creation logic gets duplicated in every caller, and every caller must change
  when a payment method is added. With a factory, it changes in one place.
- **Dependencies:** callers depend only on the `PaymentProcessor` and `NotificationChannel`
  interfaces, never on concrete classes.
- **Testing:** in tests the factory can be replaced with one that returns a processor that always
  succeeds or always fails.

---

## Facade: ending a parking session

### Problem

Ending a session is the most involved operation in the system. In one transaction, in this order:

1. Record the exit time on the `ParkingSession`.
2. Calculate the cost with the location's `PricingStrategy` (Strategy).
3. Create the `Payment` and process it with the processor from `PaymentProcessorFactory` (Factory).
4. Release the `ParkingSpot`, Occupied → Available (State).
5. Publish `SessionCompletedEvent` so the driver is notified (Observer).

If each caller (a REST controller, a scheduled job, a test) did these steps itself, the order and the
transaction boundary would be repeated, and easy to get wrong.

### Solution

| Role | Class |
|---|---|
| Facade | `ParkingSessionFacade` with `startSession(driverId, spotId)` and `endSession(sessionId)` |
| Subsystems | `ParkingSessionRepository`, `ParkingSpotRepository`, `PricingStrategy` (via `ParkingLocation`), `PaymentProcessorFactory`, `ApplicationEventPublisher` |
| Client | `ParkingSessionController` |

The controller makes a single call, `parkingSessionFacade.endSession(id)`, and gets back the
completed session with its cost and payment status.

In the microservices the same facade lives in reservation-service. The cost uses the pricing rule
fetched from parking-service, and step 3 becomes asynchronous: the facade publishes
`session.completed`, and payment-service processes the payment when it receives the event.

### Why not a simpler alternative

The simple version is a controller that calls each repository and service itself.

- **Business logic in controllers:** it breaks the project rule that controllers are thin, and
  duplicates the sequence anywhere else a session is ended.
- **One transaction:** the facade owns the transaction, so a failure in any step leaves no half-ended
  session (for example a freed spot with no cost recorded).
- **Easy to test and present:** the facade can be tested with every subsystem mocked, and it is the
  one place to point at when explaining how a session ends.

---

## How the patterns work together

Ending a session shows all five patterns in one call:

```
ParkingSessionController
  └─ ParkingSessionFacade.endSession(sessionId)                Facade
       ├─ location.getPricingStrategy().calculateCost(...)      Strategy
       ├─ paymentProcessorFactory.create(method).process(...)   Factory
       ├─ spot.release()  → OccupiedState → AvailableState      State
       └─ publish(SessionCompletedEvent)                        Observer
            └─ NotificationEventListener
                 └─ notificationChannelFactory.create(channel).send(...)   Factory
```

Reserving a spot uses State (Available → Reserved, or an exception if the spot is taken) and
Observer (`ReservationCreatedEvent` → notification).

## Where each pattern lives

| Pattern | Proof of concept (`poc/`, package `com.smartparking.poc`) | Microservices |
|---|---|---|
| Strategy | `pricing` | Rules configured in parking-service, cost calculated in reservation-service |
| State | `spot` | parking-service |
| Observer | `event`, `notification` | RabbitMQ exchange `parking.events`; listeners in every consuming service |
| Factory | `payment`, `notification` | payment-service (processors), notification-service (channels) |
| Facade | `session` | reservation-service |

The class diagram (Phase 2) and the PoC code (Phase 4) are not written yet. When they are, each
pattern there carries a UML note or Javadoc naming the pattern and the role of each class, using the
names in this document.
