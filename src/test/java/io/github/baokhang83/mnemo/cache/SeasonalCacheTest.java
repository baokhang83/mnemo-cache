package io.github.baokhang83.mnemo.cache;

import static io.github.baokhang83.mnemo.cache.CacheFixtures.clockAt;
import static io.github.baokhang83.mnemo.cache.CacheFixtures.fullDay100k;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SeasonalCacheTest {

    private static final double EPS = 1e-9;

    @Test
    void storesRetrievesAndInvalidates() {
        SeasonalCache<String, String> cache = noonCache();
        cache.put("a", "1");
        assertEquals("1", cache.getIfPresent("a"));
        assertNull(cache.getIfPresent("missing"));
        cache.invalidate("a");
        assertNull(cache.getIfPresent("a"));
    }

    @Test
    void getComputesAndCachesViaLoader() {
        SeasonalCache<String, String> cache = noonCache();
        assertEquals("computed", cache.get("k", key -> "computed"));
        assertEquals("computed", cache.getIfPresent("k")); // cached; loader not re-run
    }

    @Test
    void stateReportsCurrentFraction() {
        SeasonalCache<String, String> cache = noonCache();
        assertEquals(0.85, cache.state().currentFraction(), EPS); // daytime plateau at noon
    }

    @Test
    void startProducesAWorkingCacheAndShutdownIsIdempotent() {
        SeasonalCache<String, String> cache = SeasonalCache.start(fullDay100k());
        try {
            cache.put("a", "1");
            assertEquals("1", cache.getIfPresent("a"));
        } finally {
            cache.shutdown();
            cache.shutdown(); // idempotent
        }
    }

    private static SeasonalCache<String, String> noonCache() {
        return SeasonalCache.withoutScheduler(fullDay100k(), clockAt(LocalTime.NOON));
    }
}
