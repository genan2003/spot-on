package com.smartparking.poc.demo;

import com.smartparking.poc.location.ParkingLocation;
import com.smartparking.poc.location.ParkingLocationRepository;
import com.smartparking.poc.location.ParkingLocationService;
import com.smartparking.poc.payment.PaymentMethod;
import com.smartparking.poc.payment.PaymentProcessorFactory;
import com.smartparking.poc.pricing.PricingRule;
import com.smartparking.poc.reservation.Reservation;
import com.smartparking.poc.reservation.ReservationExpirationScheduler;
import com.smartparking.poc.reservation.ReservationService;
import com.smartparking.poc.session.ParkingSession;
import com.smartparking.poc.session.ParkingSessionFacade;
import com.smartparking.poc.spot.ParkingSpotRepository;
import com.smartparking.poc.spot.SpotStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Plays one scenario that shows all five patterns working together, using the demo data from
 * {@code V2__demo_data.sql}. Run with: {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo}.
 * Indented lines come from the application itself (payment processors and notification channels).
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoRunner implements CommandLineRunner {

    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID BOGDAN = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID CENTRAL_GARAGE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID A1 = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID A2 = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID A3 = UUID.fromString("00000000-0000-0000-0000-000000001003");
    private static final UUID A4 = UUID.fromString("00000000-0000-0000-0000-000000001004");

    private final ParkingLocationService locationService;
    private final ParkingLocationRepository locationRepository;
    private final ParkingSpotRepository spotRepository;
    private final ReservationService reservationService;
    private final ReservationExpirationScheduler expirationScheduler;
    private final ParkingSessionFacade sessionFacade;
    private final PaymentProcessorFactory paymentProcessorFactory;
    private final DemoClock clock;

    @Override
    public void run(String... args) throws Exception {
        title("Spot On proof of concept: five design patterns working together");
        say("Central Garage charges with %s.", strategyName(), "Strategy");

        step("1. Ana reserves spot A1");
        say("After the commit, ReservationCreatedEvent reaches the listener, which gets Ana's channel (email) from the factory:",
                "Observer + Factory");
        Reservation reservation = reservationService.reserve(ANA, A1);
        say("A1 went from AVAILABLE to %s. The hold ends at %s.", status(A1), localTime(reservation.getExpiresAt()), "State");

        step("2. Bogdan tries to reserve the same spot");
        try {
            reservationService.reserve(BOGDAN, A1);
        } catch (RuntimeException e) {
            say("Rejected by ReservedState: \"%s\" (409 Conflict over REST)", e.getMessage(), "State");
        }

        step("3. Ana and Bogdan both try to reserve A4 at the same moment");
        raceForSpot(A4);

        step("4. Ana arrives at A1 and starts a session");
        ParkingSession anaSession = sessionFacade.startSession(ANA, A1);
        say("One facade call: the reservation is completed and A1 is now %s.", status(A1), "Facade + State");

        clock.advance(Duration.ofMinutes(130));
        step("5. 2 h 10 min later, Ana leaves and ends the session");
        say("ParkingSessionFacade.endSession() runs the five steps in one transaction:", "Facade");
        ParkingSession ended = sessionFacade.endSession(anaSession.getId());
        say("%s, 3 started hours: %s lei", strategyName(), ended.getCost(), "Strategy");
        say("Paid with %s, chosen for Ana's card.", processorName(PaymentMethod.CARD), "Factory");
        say("A1 is %s again.", status(A1), "State");

        step("6. The admin switches Central Garage to tiered pricing (8 lei first hour, then 4 lei)");
        locationService.updatePricingRule(CENTRAL_GARAGE, PricingRule.tiered(new BigDecimal("8.00"), new BigDecimal("4.00")));
        say("Central Garage now charges with %s. No other code changed.", strategyName(), "Strategy");

        step("7. Bogdan parks on A2 for 2 h 10 min");
        ParkingSession bogdanSession = sessionFacade.startSession(BOGDAN, A2);
        clock.advance(Duration.ofMinutes(130));
        ParkingSession bogdanEnded = sessionFacade.endSession(bogdanSession.getId());
        say("%s: 8 + 2 × 4 = %s lei", strategyName(), bogdanEnded.getCost(), "Strategy");
        say("Paid with %s (Bogdan's wallet); this one fails, and the spot is still freed.",
                processorName(PaymentMethod.WALLET), "Factory + Observer");

        step("8. Bogdan reserves A3 but never arrives");
        reservationService.reserve(BOGDAN, A3);
        clock.advance(Duration.ofMinutes(16));
        say("16 minutes later the expiry check runs. A4 from step 3 was never used either:", "Observer");
        expirationScheduler.expireReservations();
        say("A3 is %s again.", status(A3), "State");

        step("Spots at Central Garage now");
        System.out.println("   " + locationService.getAvailability(CENTRAL_GARAGE));
        System.out.println();
    }

    /** Two threads reserve the same spot at once; the database lets exactly one commit. */
    private void raceForSpot(UUID spotId) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> results = new ArrayList<>();
        List<String> names = List.of("Ana", "Bogdan");
        for (UUID driver : List.of(ANA, BOGDAN)) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    reservationService.reserve(driver, spotId);
                    return "reserved";
                } catch (RuntimeException e) {
                    return "conflict (" + e.getClass().getSimpleName() + ")";
                }
            }));
        }
        start.countDown();
        for (int i = 0; i < results.size(); i++) {
            say("%s: %s", names.get(i), results.get(i).get(), "Concurrency guard");
        }
        say("Exactly one reservation committed; the other hit the spot's @Version check or the unique index.", "Concurrency guard");
        pool.shutdown();
    }

    private static String localTime(java.time.Instant time) {
        return time.atZone(java.time.ZoneId.of("Europe/Bucharest")).toLocalTime().toString();
    }

    private String strategyName() {
        ParkingLocation location = locationRepository.findById(CENTRAL_GARAGE).orElseThrow();
        return location.getPricingStrategy().getClass().getSimpleName();
    }

    private String processorName(PaymentMethod method) {
        return paymentProcessorFactory.create(method).getClass().getSimpleName();
    }

    private SpotStatus status(UUID spotId) {
        return spotRepository.findById(spotId).orElseThrow().getStatus();
    }

    private static void title(String text) {
        System.out.println();
        System.out.println(text);
        System.out.println("=".repeat(text.length()));
    }

    private static void step(String text) {
        System.out.println();
        System.out.println(text);
    }

    /** Prints a line tagged with the pattern(s) it demonstrates; the last argument is the tag. */
    private static void say(String format, Object... argsAndTag) {
        Object[] args = java.util.Arrays.copyOf(argsAndTag, argsAndTag.length - 1);
        String tag = (String) argsAndTag[argsAndTag.length - 1];
        System.out.printf("   [%s] %s%n", tag, format.formatted(args));
    }
}
