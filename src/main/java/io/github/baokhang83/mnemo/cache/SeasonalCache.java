package io.github.baokhang83.mnemo.cache;

import io.github.baokhang83.mnemo.cache.schedule.ScheduleSpec;
import java.time.Clock;
import java.time.LocalTime;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A cache whose maximum capacity flexes on a daily seasonality curve, to reclaim memory
 * off-peak. A single background thread samples the curve every {@link ScheduleSpec#tick()}
 * and adjusts the backend's maximum; eviction itself is delegated to the backend.
 *
 * <p>Construct with {@link #start(ScheduleSpec)}. Call {@link #shutdown()} to stop the
 * background scheduler when the cache is no longer needed.
 */
public final class SeasonalCache<K, V> {

    private final ScheduleSpec spec;
    private final Clock clock;
    private final CapacityBackend<K, V> backend;
    private volatile ScheduledExecutorService scheduler;

    SeasonalCache(ScheduleSpec spec, Clock clock, CapacityBackend<K, V> backend) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.backend = Objects.requireNonNull(backend, "backend");
        refreshCapacity(); // apply the curve's capacity for "now" up front
    }

    /** Start a Caffeine-backed seasonal cache driven by the system clock. */
    public static <K, V> SeasonalCache<K, V> start(ScheduleSpec spec) {
        return start(spec, Clock.systemUTC());
    }

    /** Start a Caffeine-backed seasonal cache driven by the given clock. */
    public static <K, V> SeasonalCache<K, V> start(ScheduleSpec spec, Clock clock) {
        SeasonalCache<K, V> cache =
                new SeasonalCache<>(spec, clock, new CaffeineBackend<>(spec.maxEntries()));
        cache.startScheduler();
        return cache;
    }

    /** Test seam: build a cache with initial capacity applied but no background scheduler. */
    static <K, V> SeasonalCache<K, V> withoutScheduler(ScheduleSpec spec, Clock clock) {
        return new SeasonalCache<>(spec, clock, new CaffeineBackend<>(spec.maxEntries()));
    }

    public V getIfPresent(K key) {
        return backend.getIfPresent(key);
    }

    public void put(K key, V value) {
        backend.put(key, value);
    }

    public void invalidate(K key) {
        backend.invalidate(key);
    }

    /** Return the value for {@code key}, computing and caching it via {@code loader} if absent. */
    public V get(K key, Function<K, V> loader) {
        return backend.get(key, loader);
    }

    public SeasonalCacheState state() {
        return new SeasonalCacheState(
                currentFraction(),
                backend.currentMaximum(),
                backend.estimatedSize(),
                backend.evictionCount());
    }

    /** Stop the background scheduler. Idempotent; safe to call more than once. */
    public void shutdown() {
        ScheduledExecutorService current = scheduler;
        if (current != null) {
            current.shutdownNow();
            scheduler = null;
        }
    }

    private void startScheduler() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mnemo-seasonal-cache");
            thread.setDaemon(true);
            return thread;
        });
        long periodMillis = Math.max(1L, spec.tick().toMillis());
        service.scheduleAtFixedRate(
                this::refreshCapacityQuietly, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
        this.scheduler = service;
    }

    private void refreshCapacityQuietly() {
        try {
            refreshCapacity();
        } catch (RuntimeException ignored) {
            // A transient failure must not kill the scheduler; the next tick retries.
        }
    }

    void refreshCapacity() {
        backend.setMaximum(targetMaximum(spec, nowLocal()));
    }

    private LocalTime nowLocal() {
        return clock.instant().atZone(spec.zone()).toLocalTime();
    }

    private double currentFraction() {
        return spec.curve().fractionAt(nowLocal());
    }

    /** Pure capacity policy: the curve fraction scaled by max, clamped to the absolute floor. */
    static long targetMaximum(ScheduleSpec spec, LocalTime now) {
        double fraction = spec.curve().fractionAt(now);
        long scaled = Math.round(spec.maxEntries() * fraction);
        return Math.max(spec.minEntries(), scaled);
    }
}
