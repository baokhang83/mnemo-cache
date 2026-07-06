package io.github.baokhang83.mnemo.cache.schedule;

import java.time.Duration;
import java.time.ZoneId;

/**
 * A fully-resolved seasonal cache specification, as produced by
 * {@link ScheduleParser} from the meta-language, or assembled programmatically.
 *
 * @param zone       wall-clock zone the curve is evaluated against (explicit; never defaulted)
 * @param maxEntries the cache's absolute maximum entry count (100% of the curve)
 * @param minEntries an absolute floor so low percentages cannot collapse the cache
 * @param tick       how often the runtime samples the curve and adjusts capacity
 * @param curve      the seasonality curve
 */
public record ScheduleSpec(
        ZoneId zone,
        long maxEntries,
        long minEntries,
        Duration tick,
        CapacitySchedule curve) {}
