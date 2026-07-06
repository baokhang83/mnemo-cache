package io.github.baokhang83.mnemo.cache.schedule;

import java.time.LocalTime;

/**
 * A daily, wrapping capacity curve: maps a wall-clock {@link LocalTime} to a
 * fraction in {@code [0.0, 1.0]} of the cache's configured maximum size.
 *
 * <p>Implementations are pure functions of the time of day; the runtime samples
 * this curve on a schedule and adjusts the backend's maximum accordingly.
 */
@FunctionalInterface
public interface CapacitySchedule {

    /**
     * @param time wall-clock time of day (in the schedule's configured zone)
     * @return the target fraction of maximum capacity, in {@code [0.0, 1.0]}
     */
    double fractionAt(LocalTime time);
}
