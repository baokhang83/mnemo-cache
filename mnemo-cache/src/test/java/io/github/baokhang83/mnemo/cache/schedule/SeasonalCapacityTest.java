package io.github.baokhang83.mnemo.cache.schedule;

import static io.github.baokhang83.mnemo.cache.schedule.SeasonalCapacity.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SeasonalCapacityTest {

    private static final double EPS = 1e-9;

    @Test
    void builderProducesSameSpecAsTheParser() {
        ScheduleSpec built = SeasonalCapacity.named("hotItems")
                .max(100_000).min(500).zone("Europe/Vienna").tick(Duration.ofSeconds(60))
                .startAt("00:00", percent(10))
                .holdUntil("06:00")
                .rampUntil("11:00", percent(85))
                .holdUntil("20:00")
                .rampUntil("23:00", percent(10))
                .build();

        ScheduleSpec parsed = ScheduleParser.parse(
                "zone=Europe/Vienna; max=100000; min=500; tick=60s; "
                        + "curve=00:00@10, hold 06:00, ramp 11:00@85, hold 20:00, ramp 23:00@10");

        assertEquals(parsed.zone(), built.zone());
        assertEquals(parsed.maxEntries(), built.maxEntries());
        assertEquals(parsed.minEntries(), built.minEntries());
        assertEquals(parsed.tick(), built.tick());
        for (int hour = 0; hour < 24; hour++) {
            LocalTime t = LocalTime.of(hour, 0);
            assertEquals(parsed.curve().fractionAt(t), built.curve().fractionAt(t), EPS,
                    "curve mismatch at hour " + hour);
        }
    }

    @Test
    void maxIsRequired() {
        assertThrows(IllegalStateException.class,
                () -> SeasonalCapacity.named("x").zone("UTC").startAt("00:00", percent(10)).build());
    }

    @Test
    void zoneIsRequired() {
        assertThrows(IllegalStateException.class,
                () -> SeasonalCapacity.named("x").max(10).startAt("00:00", percent(10)).build());
    }
}
