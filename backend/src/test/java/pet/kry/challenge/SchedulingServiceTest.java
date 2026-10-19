package pet.kry.challenge;

import pet.kry.challenge.exception.SlotUnavailableException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingServiceTest {
    private final SchedulingService scheduling = new SchedulingService(
        Clock.fixed(Instant.parse("2026-10-12T06:00:00Z"), ZoneId.of("Europe/Stockholm")));

    @Test void hasCapacityAtOpening() {
        assertEquals(1, scheduling.activeClinicians(LocalTime.of(8, 0)));
    }

    @Test void hasSlotsInTheFuture() {
        assertFalse(scheduling.availableSlots().isEmpty());
    }

    @Test void fullSlotIsRefusedAndNoLongerOffered() {
        // The original bug: book() never checked capacity. Fill the slot, then it must disappear and refuse more.
        OffsetDateTime slot = OffsetDateTime.parse("2026-10-12T08:30:00+02:00");
        int capacity = scheduling.activeClinicians(LocalTime.of(8, 30));
        for (int i = 0; i < capacity; i++) assertNotNull(scheduling.book(slot));

        assertFalse(scheduling.availableSlots().contains(slot));
        assertThrows(SlotUnavailableException.class, () -> scheduling.book(slot));
    }

    @Test void sameMomentInAnotherTimezoneCountsAsSameSlot() {
        // 06:00Z is the same moment as 08:00 Stockholm; bookings made in UTC must fill the Stockholm slot
        OffsetDateTime utc = OffsetDateTime.parse("2026-10-12T06:00:00Z");
        OffsetDateTime stockholm = OffsetDateTime.parse("2026-10-12T08:00:00+02:00");
        int capacity = scheduling.activeClinicians(LocalTime.of(8, 0));
        for (int i = 0; i < capacity; i++) assertNotNull(scheduling.book(utc));

        assertThrows(SlotUnavailableException.class, () -> scheduling.book(stockholm));
    }

    @Test void clinicianIsNotBookableAtShiftEnd() {
        // 08:00 clinician works 08:00-12:00 and 13:00-17:00, 08:30 clinician until 17:30
        assertEquals(4, scheduling.activeClinicians(LocalTime.of(16, 45)));
        assertEquals(3, scheduling.activeClinicians(LocalTime.of(17, 0)));
        assertEquals(2, scheduling.activeClinicians(LocalTime.of(17, 30)));

        OffsetDateTime slot = OffsetDateTime.parse("2026-10-12T17:00:00+02:00");
        for (int i = 0; i < 3; i++) assertNotNull(scheduling.book(slot));
        assertThrows(SlotUnavailableException.class, () -> scheduling.book(slot));
    }

    @Test void concurrentBookingsNeverExceedCapacity() throws Exception {
        // Regression guard for the lock in book(): a race is not guaranteed to show up in one round, so repeat it.
        OffsetDateTime slot = OffsetDateTime.parse("2026-10-12T08:30:00+02:00");
        int capacity = scheduling.activeClinicians(LocalTime.of(8, 30)); // from the schedule, currently 2
        int threads = 20;
        assertTrue(capacity < threads, "need more threads than places to create a race");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 50; round++) {
                SchedulingService service = new SchedulingService(
                    Clock.fixed(Instant.parse("2026-10-12T06:00:00Z"), ZoneId.of("Europe/Stockholm")));
                CountDownLatch start = new CountDownLatch(1);
                AtomicInteger booked = new AtomicInteger();
                List<Future<?>> results = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        try { service.book(slot); booked.incrementAndGet(); }
                        catch (SlotUnavailableException expected) { }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> result : results) result.get(5, TimeUnit.SECONDS);
                assertEquals(capacity, booked.get(), "round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
