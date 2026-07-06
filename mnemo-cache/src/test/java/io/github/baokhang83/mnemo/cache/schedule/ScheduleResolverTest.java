package io.github.baokhang83.mnemo.cache.schedule;

import static io.github.baokhang83.mnemo.cache.schedule.SeasonalCapacity.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class ScheduleResolverTest {

    private static final double EPS = 1e-9;
    private static final UnaryOperator<String> NONE = key -> null;

    private static final ScheduleSpec CODE_DEFAULT = SeasonalCapacity.named("hot")
            .max(1000).zone("UTC").startAt("00:00", percent(20)).build();

    @Test
    void keysFollowTheNamingConvention() {
        assertEquals("mnemo.cache.hot.schedule", ScheduleResolver.propertyKey("hot"));
        assertEquals("MNEMO_CACHE_HOT_SCHEDULE", ScheduleResolver.envKey("hot"));
    }

    @Test
    void usesProgrammaticDefaultWhenNoExternalConfig() {
        ScheduleSpec spec = ScheduleResolver.resolve("hot", CODE_DEFAULT, NONE, NONE);
        assertEquals(1000, spec.maxEntries());
        assertEquals(0.20, spec.curve().fractionAt(LocalTime.NOON), EPS);
    }

    @Test
    void envOverridesProgrammatic() {
        UnaryOperator<String> env = key ->
                key.equals("MNEMO_CACHE_HOT_SCHEDULE") ? "zone=UTC; max=5000; curve=00:00@70" : null;

        ScheduleSpec spec = ScheduleResolver.resolve("hot", CODE_DEFAULT, NONE, env);

        assertEquals(5000, spec.maxEntries());
        assertEquals(0.70, spec.curve().fractionAt(LocalTime.NOON), EPS);
    }

    @Test
    void propertyOverridesEnvAndProgrammatic() {
        UnaryOperator<String> props = key ->
                key.equals("mnemo.cache.hot.schedule") ? "zone=UTC; max=9000; curve=00:00@90" : null;
        UnaryOperator<String> env = key ->
                key.equals("MNEMO_CACHE_HOT_SCHEDULE") ? "zone=UTC; max=5000; curve=00:00@70" : null;

        ScheduleSpec spec = ScheduleResolver.resolve("hot", CODE_DEFAULT, props, env);

        assertEquals(9000, spec.maxEntries());
    }

    @Test
    void malformedExternalConfigFailsFast() {
        UnaryOperator<String> props = key -> "totally invalid";
        assertThrows(ScheduleParseException.class,
                () -> ScheduleResolver.resolve("hot", CODE_DEFAULT, props, NONE));
    }
}
