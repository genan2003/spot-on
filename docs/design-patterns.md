# Design patterns (exercises 1–2)

This document lists the domain model of the Smart Parking Management System and the six design
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
| `PricingRule` | The pricing an administrator configured for a location: which strategy, with which rates, plus extras for any strategy | `strategyType`, `hourlyRate`, `flatRate`, `bands`, `dayRate`, `nightWeekendRate`, `nightStart`, `nightEnd`, `billingIncrementMinutes`, `gracePeriodMinutes`, `dailyCap`, `surcharges` |
| `PriceBand` | One band of a tiered rule: from hour N of the stay on, each hour costs this much | `fromHour`, `hourlyRate` |
| `BaySurcharges` | Percent added (or taken off) per spot type and for EV charging | `compact`, `large`, `motorcycle`, `evCharging` |
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
| Decorator | Structural | Pricing extras | Optional extras (billing blocks, grace period, daily maximum, surcharges) combine freely with every pricing rule |
| State | Behavioural | Spot lifecycle | Which actions are allowed depends on the spot's current status |
| Observer | Behavioural | Domain events | Many parts of the system react to reservations, sessions and payments |
| Factory | Creational | Payments, notifications | Picking the right processor or channel for a payment method or driver preference |
| Facade | Structural | Ending a session | Ending a session touches five subsystems in a fixed order |

---

## Strategy: pricing rules

### Problem

Every location sets its own pricing, and the admin can change it at any time. The system supports
four rules:

- **Hourly:** every hour costs the same.
- **Flat rate:** one price per visit, however long.
- **Tiered:** the hourly price changes as the stay gets longer, in bands (for example 8 lei for the
  first hour, 4 lei up to hour 4, then 2 lei).
- **Night and weekend:** an hourly day rate, and a different hourly rate at night (hours set by the
  admin, 20:00–08:00 by default) and on weekends. Each part of the stay is charged at the rate for
  the time it falls in.

Each strategy charges for the exact time it is given. Rounding up to whole blocks ("every started
hour") and the other extras are decorators around it (see Decorator).

The cost of a session must be computed by the rule of its location, and new rules (for example an
event-day rate) should not require changing the code that ends a session.

### Solution

| Role | Class |
|---|---|
| Strategy (interface) | `PricingStrategy` with `BigDecimal calculateCost(Instant entryTime, Instant exitTime)` |
| Concrete strategies | `HourlyPricingStrategy`, `FlatRatePricingStrategy`, `TieredPricingStrategy`, `NightWeekendPricingStrategy` |
| Context | `ParkingLocation`, which exposes `getPricingStrategy(spot)` built from its `PricingRule` |
| Client | `ParkingSessionFacade`, which asks the location's strategy for the cost when a session ends |

Each concrete strategy keeps its own rates as fields and contains only its own formula.
`PricingRule.toStrategy()` turns the stored configuration (strategy type + rates) into the matching
strategy object, so the database stores plain values while the code works with behaviour.

```java
BigDecimal cost = location.getPricingStrategy(spot).calculateCost(session.getEntryTime(), exitTime);
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

## Decorator: pricing extras

### Problem

On top of its rule, a car park can switch on extras, and they apply to every rule:

- **Billing blocks:** the stay is charged in whole blocks of 15, 30 or 60 minutes (60 by default,
  so every started hour counts).
- **Bay surcharge:** a percentage added for a kind of bay (for example +20% for a large bay, +15%
  for EV charging), or taken off when negative (a cheaper motorcycle bay).
- **Daily maximum:** a driver never pays more than this for each started 24 hours.
- **Grace period:** a driver who leaves within N minutes pays nothing.

Any combination is possible, with any of the four strategies, and the admin can change it at any time.

### Solution

| Role | Class |
|---|---|
| Component (interface) | `PricingStrategy`, the same interface the strategies implement |
| Concrete components | The four pricing strategies |
| Decorator | `PricingDecorator` (abstract): implements `PricingStrategy` and holds the `inner` strategy it wraps |
| Concrete decorators | `BillingIncrementDecorator`, `BaySurchargeDecorator`, `DailyCapDecorator`, `GracePeriodDecorator` |
| Builder of the chain | `PricingRule.toStrategy(spotType, evCharging)` |

Each decorator does one thing before or after calling the strategy it wraps: `BillingIncrementDecorator`
rounds the exit time up before calling it, `BaySurchargeDecorator` multiplies its result,
`DailyCapDecorator` caps it, `GracePeriodDecorator` returns 0 without calling it at all for a short stay.
`PricingRule.toStrategy` always wraps them in the same order, from the inside out: billing blocks,
surcharge, daily maximum, grace period. The daily maximum includes the surcharge, and a short enough
stay is free whatever the rest says.

```java
PricingStrategy pricing = new GracePeriodDecorator(
        new DailyCapDecorator(
            new BaySurchargeDecorator(
                new BillingIncrementDecorator(new TieredPricingStrategy(bands), Duration.ofMinutes(15)),
                20),
            new BigDecimal("30.00")),
        Duration.ofMinutes(10));
```

The facade still sees one `PricingStrategy` and calls `calculateCost`; it can't tell a plain strategy
from a decorated one.

### Why not a simpler alternative

The simple version adds `if` blocks for each extra to every strategy, or a subclass per combination.

- **Combinations:** four extras that can each be on or off give 16 combinations per strategy, 64 in
  all. Subclasses for each would explode; decorators are four classes that stack.
- **No duplication:** with `if` blocks, every strategy repeats the rounding, cap and grace-period
  code. Each decorator has it once, and the strategies keep only their own formula.
- **Order is explicit:** "cap after surcharge, grace period over everything" is the order of the
  wrapping in one method, not hidden in the order of `if` statements in four places.
- **Testing:** each decorator is tested on top of a fixed hourly strategy, and one test checks the
  order of the chain.

Strategy and Decorator work together: Strategy picks *how* time turns into money, Decorator adds
*extras* around whichever strategy was picked.

---

## State: parking spot lifecycle

### Problem

A spot is always in exactly one of four states: available, reserved, occupied or out of service.
What may happen to it depends on that state:

| From \ action | `reserve()` | `occupy()` | `release()` | `markOutOfService()` | `returnToService()` |
|---|---|---|---|---|---|
| Available | → Reserved | → Occupied | not allowed | → Out of service | not allowed |
| Reserved | not allowed | → Occupied | → Available | → Out of service | not allowed |
| Occupied | not allowed | not allowed | → Available | not allowed | not allowed |
| Out of service | not allowed | not allowed | not allowed | not allowed | → Available |

`release()` covers a cancelled or expired reservation (Reserved → Available) and a car leaving
(Occupied → Available). An admin can close a reserved spot, since there is no car in it yet; the
reservation holding it is cancelled and the driver is told (in the PoC in the same transaction, in the
microservices through the `spot.closed` event). An occupied spot can't be closed.
The most important rule in the project depends on this table:
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
| Observers (listeners) | `NotificationEventListener` (`@TransactionalEventListener` methods, run after the commit) | One queue per consuming service, with a `@RabbitListener` |

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

Ending a session shows all six patterns in one call:

```
ParkingSessionController
  └─ ParkingSessionFacade.endSession(sessionId)                Facade
       ├─ location.getPricingStrategy(spot).calculateCost(...)  Strategy + Decorator
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
| Decorator | `pricing` | Extras stored with the rule in parking-service, decorators built and applied in reservation-service |
| State | `spot` | parking-service |
| Observer | `event`, `notification` | RabbitMQ exchange `parking.events`; listeners in every consuming service |
| Factory | `payment`, `notification` | payment-service (processors), notification-service (channels) |
| Facade | `session` | reservation-service |

Each pattern is marked in the class diagram by a UML note, and in the PoC code by a Javadoc line
naming the pattern and the class's role. `poc/README.md` maps every class to its pattern and role,
and `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` plays a scenario that shows all six
patterns working together.
