# Stage 1 presentation: run sheet

Slides: the "Spot On — Stage 1" deck (22 slides, speaker notes on each). Diagrams in full
resolution: `docs/images/` (the class diagram also as SVG, for zooming).

Presenters follow the team split in `docs/PLAN.md`: A = patterns doc, class diagram, Strategy,
Facade; B = sequence diagrams, State, double reservations; C = Observer, Factory, architectures.

## Running order (about 17 minutes + questions)

| Part | Slides | Who | Time |
|---|---|---|---|
| What Spot On does | Cover, domain | A | 1 min |
| Five patterns | Overview, Strategy, State, Observer, Factory, Facade | A, B, C | 5 min |
| Class diagram | Class diagram | A | 2 min |
| Sequence diagrams | Reserve, park and pay | B | 2 min |
| Proof of concept | Live demo, no double reservations | A runs, all narrate; B | 3 min |
| Architectures | Monolith, microservices, event-driven (2 slides each) | C | 3 min |
| Decision | Comparison, recommendation | C | 1 min |
| Questions | Questions | All | — |

## Demo checklist

Before the presentation, on the laptop that will run it:

1. `cd poc && ./mvnw -q clean compile` (downloads nothing on the day; no network needed after this).
2. Make the terminal font large and the window wide, so tagged lines don't wrap.
3. Do one full run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` takes about 16 seconds.

On the day: run the same command. Which driver wins the race for A4 changes between runs; that's
expected (real threads). Fallback if anything fails: `./mvnw verify` (49 tests) and walk through the
test names on the "proof of concept" slide.

## Questions to prepare

| Question | Short answer | Who |
|---|---|---|
| Why is Factory here a pattern and not just `new`? | Callers depend only on the interface; the choice depends on a runtime value (method, channel). It is a simple factory, not GoF Factory Method, and we say so. | C |
| What if two drivers reserve the same spot at the same moment? | State rejects a reserved spot; if both read it as free, `@Version` or the unique index fails the second commit and it gets 409. Proven by the 10-thread test. | B |
| Why not just an `if` on the spot status? | The same checks would be repeated across services; one missing check is a bug. State keeps each rule in one class, matching the transition table. | B |
| Why are notifications sent only after the commit? | So a driver is never told about a reservation that was rolled back. | C |
| What happens when a payment fails? | The session still ends and the spot is freed (the car has left); the payment is marked failed and the driver is notified to pay again. | A |
| Does the class diagram match the code? | Yes: a script checked that every class and method in the diagram exists in `poc/`. Only DTOs, configuration and the demo runner are left out, as the diagram's header says. | A |
| Why microservices and not event-driven? | A reservation needs an immediate yes or no; side effects can wait. We use events exactly for those. Event-driven would make every action asynchronous and add sagas for little gain at our scale. | C |
| Why not a monolith, if it's simpler? | For one operator it would be our starting point, and we say so. Microservices let availability scale on its own, isolate failures, and are what stage 2 asks for. | C |
| How do the patterns change in microservices? | State moves to parking-service, Strategy and Facade to reservation-service, Factory to payment and notification services, Observer becomes RabbitMQ. Payment leaves the facade's transaction. | C |
| How is the reservation kept consistent across two databases? | parking-service locks the spot first; if saving the reservation fails, reservation-service releases it (compensation), and an unconfirmed hold is released after the hold time. | C |

---

# Stage 2 presentation: run sheet

Exercises 7–9: microservices (2p), message queue (1p), CI/CD (1p). Everything is shown live on one
laptop that is also the GitHub Actions self-hosted runner. Presenters follow the Stage 2 team split in
`docs/PLAN.md`: A = parking-service, gateway; B = reservation-service, RabbitMQ; C = payment and
notification services, Postman, CI/CD.

## Running order (about 18 minutes + questions)

| Part | What the audience sees | Exercise | Who | Time |
|---|---|---|---|---|
| The system as built | Microservices component diagram (`docs/images/architecture/microservices-component.png`): gateway, four services, one database each, REST for holding a spot, RabbitMQ for everything after | 7 | A | 2 min |
| Start it | `docker compose ps`: eight healthy containers (started with the README's one command) | 7 | A | 1 min |
| Postman | Newman runs the collection through the gateway: 39 requests, about 80 assertions, all green | 7 | C | 2 min |
| Driver journey, live | Driver app (phone width) next to the admin dashboard: reserve, the bay turns hatched on both screens; cancel, it turns free; reserve, start, end; receipt with cost and "Paid"; notifications | 7 | A drives, B narrates | 4 min |
| Message queue | RabbitMQ UI: queues per consumer; stop notification-service, reserve and cancel, messages wait in `notification.events`; start it, they are delivered | 8 | B | 3 min |
| No double reservations | Two guards: spot state + `@Version` in parking-service, partial unique index in reservation-service; the two 10-thread concurrency tests | 7 | B | 2 min |
| Failed payment (optional) | Payments forced to fail: receipt offers "Try payment again" | 7, 8 | C | 1 min |
| CI/CD | Actions tab: the run started at the beginning of the talk; four jobs, deploy on the self-hosted runner, Newman smoke test | 9 | C | 3 min |
| Questions | | | All | — |

## Before the presentation (day before, on the demo laptop)

1. `git pull`, then `docker compose up -d --build --wait` (builds every image; nothing to download on the day).
2. `npx --yes newman@6 --version` once, so Newman is cached.
3. Settings → Actions → Runners shows the self-hosted runner as Idle. Push a commit or use "Run workflow"
   and check the whole pipeline is green.
4. Create the demo car park in the admin dashboard (http://localhost:3000/admin): "Central Garage",
   Strada Memorandumului 21, zone A with 6 spots, zone B with 4 EV-charging spots, tiered pricing 8 / 4 lei.
   Seeing it created is part of the admin story, so it can also be done live.
5. Clear the demo driver: in the driver app, cancel any reservation and end any session for `driver-1`.
6. Open and log in: RabbitMQ UI (http://localhost:15672, guest / guest) on the Queues tab, GitHub Actions tab.
7. Browser windows: driver app at phone width (DevTools device mode) left, admin dashboard right. Zoom 125 %.
8. Terminal: large font, in the repository root.

## On the day, step by step

```bash
# Start the pipeline first, so it has finished when we reach CI/CD: Actions → CI/CD → Run workflow (main).

docker compose up -d --build --wait      # usually instant: already running and up to date
docker compose ps                        # 8 containers, the Java ones "healthy"

npx --yes newman@6 run postman/smart-parking.postman_collection.json -e postman/local.postman_environment.json
```

Driver journey in the browser: open "Central Garage", pick A1, "Reserve spot" (admin plan hatches A1
at the same moment), "Cancel reservation" (A1 free again on both screens), reserve A2, "Start session",
"End session". The receipt shows "Processing payment…" for a moment, then "Paid". Open the bell: reserved,
cancelled, reserved, session ended, paid.

Message queue:

```bash
docker compose stop notification-service
# in the driver app: reserve a spot, cancel it
# RabbitMQ UI → Queues → notification.events: "Ready" shows the waiting messages
docker compose start notification-service
# Ready drops to 0; the bell shows the two new notifications
```

Optional, failed payment (put it back afterwards):

```bash
PAYMENT_FAILURE_RATE=1 docker compose up -d --wait payment-service
# park and leave once: the receipt says the card was declined and offers "Try payment again"
docker compose up -d --wait payment-service          # back to the 20 % default
```

Optional, reminder and expiry through the TTL queues (only reservations made after the restart use the new time).
Empty the two delay queues first: RabbitMQ only releases a delay queue's messages from its head, so 1-minute
timers would wait behind the 15-minute ones left by earlier runs (the minute sweep would still catch them, late):

```bash
docker compose exec rabbitmq rabbitmqctl purge_queue reservation.expiry-delay
docker compose exec rabbitmq rabbitmqctl purge_queue reservation.reminder-delay
RESERVATION_DURATION=1m docker compose up -d --wait reservation-service
# reserve a spot, show reservation.expiry-delay and reservation.reminder-delay holding one message each;
# after 30 s the bell shows "held for you only until …", after a minute the spot frees itself
docker compose up -d --wait reservation-service      # back to 15 minutes
```

No double reservations: show `SpotConcurrencyTests` (parking-service) and `ReservationConcurrencyTests`
(reservation-service), and their green results in the latest CI run (build-and-test jobs). Running one
live takes about 30 seconds because Testcontainers starts PostgreSQL and RabbitMQ:
`cd parking-service && ./mvnw -q test -Dtest=SpotConcurrencyTests`.

## If something goes wrong

| Problem | Fallback |
|---|---|
| A container isn't healthy | `docker compose logs <service>`; `docker compose up -d --wait <service>` |
| Strange data from rehearsals | `docker compose down -v && docker compose up -d --build --wait`, then create the car park again (2 minutes) |
| The runner is offline or the run is still going | Show the last green run; run the deploy job's two commands by hand (`docker compose up -d --build --wait`, then Newman) |
| The browser loses live updates | Reload the page (the STOMP client also reconnects by itself after 5 seconds) |
| No network | Everything runs locally; only the map tiles and the GitHub tab need the internet |

## Questions to prepare

| Question | Short answer | Who |
|---|---|---|
| Why REST for reserving but events for everything else? | The driver needs a yes or no now, so holding the spot is synchronous. Freeing the spot, payment and notifications can happen a moment later and must survive a service being down, so they are events. | B |
| How can two drivers never get the same spot across two databases? | parking-service: the spot's state rejects a held spot, and `@Version` fails the slower of two simultaneous requests. reservation-service: a partial unique index on open reservations per spot. If saving the reservation fails after the spot was held, a compensating call releases it. | B |
| What if parking-service is down when someone reserves? | `RestClient` has timeouts; the driver gets a clear 503 "try again" instead of a hanging request. Nothing is saved. | A |
| What if notification-service is down? | Nothing breaks. Its durable queue keeps the events and it catches up when it starts (shown in the demo). | B |
| What if RabbitMQ delivers an event twice? | Each consumer stores the `eventId` in `processed_event` in the same transaction as the effect; a duplicate is skipped. payment-service also charges each session only once (unique index). | C |
| What if an old event arrives late, e.g. "reservation expired" after the driver parked? | The spot remembers which reservation or session holds it. An event about an older hold changes nothing. | A |
| How does expiry work? | Each reservation sends a message to a queue nobody reads, with a TTL equal to the hold time. When it expires RabbitMQ dead-letters it to `reservation.expiry`, and reservation-service expires the reservation if it is still active. A sweep every minute is the fallback. | B |
| Can an event get lost? | Events are sent after the database commit. A crash exactly between commit and send would lose that event; a production system would add a transactional outbox. Expiry has the sweep as a safety net. | B |
| How does the spot plan update live? | parking-service publishes `spot.status-changed`; each parking-service instance reads it from its own temporary queue and pushes it to its browsers over STOMP, through the gateway and nginx. | A |
| Why a gateway? | One entry point for the frontend; it routes `/api/v1/**` and `/ws`, and keeps the internal spot endpoints unreachable from outside. | A |
| Why no shared library for the events? | Every service is an independent Maven project and Docker build. Each consumer reads only the fields it needs into its own record. | C |
| What does the pipeline actually test? | `./mvnw verify` per module: unit tests for every pattern and integration tests against real PostgreSQL and RabbitMQ (Testcontainers); frontend lint and build; image build; then deploy and a Newman smoke test against the running stack. | C |
| Why a self-hosted runner? | The assignment asks for deployment to local Docker; the runner is the machine that hosts the stack. Pull requests never deploy. | C |
| Where are the Stage 1 patterns now? | State in parking-service; Strategy and Facade in reservation-service; Factory in payment- and notification-service; Observer is RabbitMQ (plus in-process events before the commit). | All |

## Deliverables against the assignment (Project 3, exercises 7–9)

| Requirement | Where | Status |
|---|---|---|
| 7: at least two independent services with clear responsibilities | parking, reservation, payment, notification services + gateway; one database each | Done |
| 7: inter-service communication | REST (`ParkingClient`, hold and pricing) and RabbitMQ events | Done |
| 7: Postman collection covering all features | `postman/` (locations, spots, pricing, availability, reservations, sessions and cost, payments and retry, notifications) | Done; live spot updates and expiry are shown in the UI, since Newman can't wait 15 minutes or open a WebSocket |
| 7: Docker packaging, README with run instructions | Multi-stage Dockerfiles, `docker-compose.yml`, README "Quick start" (checked on a fresh copy) | Done |
| 8: message queue between at least two services | RabbitMQ: 8 routing keys, 4 durable consumer queues, TTL + dead-letter expiry | Done |
| 9: CI/CD that builds, tests and deploys to local Docker | `.github/workflows/ci-cd.yml` | Done in code; needs the self-hosted runner registered and one green run on `main` |
| Functionalities 1–5 (locations, availability, reservations, sessions, payments and notifications) | Services + frontend | All covered |
