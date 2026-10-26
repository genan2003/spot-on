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
