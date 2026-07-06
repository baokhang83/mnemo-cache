package io.github.baokhang83.mnemo.cache.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ScheduleParserTest {

    private static final double EPS = 1e-9;

    @Test
    void parsesFullSpec() {
        ScheduleSpec spec = ScheduleParser.parse(
                "zone=Europe/Vienna; max=100000; min=500; tick=60s; "
                        + "curve=00:00@10, hold 06:00, ramp 11:00@85, hold 20:00, ramp 23:00@10");

        assertEquals(ZoneId.of("Europe/Vienna"), spec.zone());
        assertEquals(100_000, spec.maxEntries());
        assertEquals(500, spec.minEntries());
        assertEquals(Duration.ofSeconds(60), spec.tick());
        assertEquals(0.10, spec.curve().fractionAt(LocalTime.of(3, 0)), EPS);
        assertEquals(0.85, spec.curve().fractionAt(LocalTime.of(15, 0)), EPS);
    }

    @Test
    void minAndTickAreOptional() {
        ScheduleSpec spec = ScheduleParser.parse("zone=UTC; max=1000; curve=00:00@50");

        assertEquals(0, spec.minEntries());
        assertEquals(Duration.ofSeconds(60), spec.tick()); // default tick
        assertEquals(0.50, spec.curve().fractionAt(LocalTime.NOON), EPS);
    }

    @Test
    void tickAcceptsMinutesUnit() {
        ScheduleSpec spec = ScheduleParser.parse("zone=UTC; max=1000; tick=5m; curve=00:00@50");
        assertEquals(Duration.ofMinutes(5), spec.tick());
    }

    @Test
    void zoneIsRequired_failFast() {
        ScheduleParseException ex = assertThrows(ScheduleParseException.class,
                () -> ScheduleParser.parse("max=1000; curve=00:00@50"));
        assertTrue(ex.getMessage().toLowerCase().contains("zone"), ex.getMessage());
    }

    @Test
    void malformedSegment_failFastNamingTheFragment() {
        ScheduleParseException ex = assertThrows(ScheduleParseException.class,
                () -> ScheduleParser.parse(
                        "zone=UTC; max=1000; curve=00:00@10, ramp 11:00")); // ramp missing @PERCENT
        assertTrue(ex.getMessage().contains("ramp 11:00"), ex.getMessage());
    }

    @Test
    void unknownKey_failFast() {
        assertThrows(ScheduleParseException.class,
                () -> ScheduleParser.parse("zone=UTC; max=1000; foo=bar; curve=00:00@50"));
    }
}
