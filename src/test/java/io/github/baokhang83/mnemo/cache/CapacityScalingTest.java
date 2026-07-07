package io.github.baokhang83.mnemo.cache;

import static io.github.baokhang83.mnemo.cache.CacheFixtures.clockAt;
import static io.github.baokhang83.mnemo.cache.CacheFixtures.fullDay100k;
import static io.github.baokhang83.mnemo.cache.schedule.SeasonalCapacity.percent;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.baokhang83.mnemo.cache.schedule.ScheduleSpec;
import io.github.baokhang83.mnemo.cache.schedule.SeasonalCapacity;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CapacityScalingTest {

    private static final double EPS = 1e-9;

    @Test
    void scalesMaximumByCurveFraction() {
        ScheduleSpec spec = fullDay100k();
        assertEquals(10_000, SeasonalCache.targetMaximum(spec, LocalTime.of(3, 0)));  // 10%
        assertEquals(85_000, SeasonalCache.targetMaximum(spec, LocalTime.of(15, 0))); // 85%
    }

    @Test
    void appliesTheAbsoluteFloor() {
        ScheduleSpec spec = SeasonalCapacity.named("t").max(1000).min(200).zone("UTC")
                .startAt("00:00", percent(10))   // 100 entries -> below the 200 floor
                .rampUntil("12:00", percent(80))
                .build();
        assertEquals(200, SeasonalCache.targetMaximum(spec, LocalTime.of(0, 0)));  // floored
        assertEquals(800, SeasonalCache.targetMaximum(spec, LocalTime.of(12, 0))); // 80%
    }

    @Test
    void appliesInitialCapacityOnConstructionForCurrentTime() {
        SeasonalCache<String, String> cache =
                SeasonalCache.withoutScheduler(fullDay100k(), clockAt(LocalTime.of(3, 0)));
        assertEquals(10_000, cache.state().currentMaxEntries());
        assertEquals(0.10, cache.state().currentFraction(), EPS);
    }
}
