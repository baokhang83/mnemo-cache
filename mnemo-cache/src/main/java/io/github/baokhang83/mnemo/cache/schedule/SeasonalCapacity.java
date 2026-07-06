package io.github.baokhang83.mnemo.cache.schedule;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The programmatic (Style A) front-end for building a {@link ScheduleSpec}.
 *
 * <p>Its verbs mirror the meta-language parsed by {@link ScheduleParser}
 * ({@code hold}/{@code ramp}, {@code @}/{@link #percent(int)}), so both front-ends
 * read as one language and compile to the same {@link ScheduleSpec}.
 *
 * <pre>{@code
 * ScheduleSpec spec = SeasonalCapacity.named("hotItems")
 *     .max(100_000).min(500).zone("Europe/Vienna").tick(Duration.ofSeconds(60))
 *     .startAt("00:00", percent(10))
 *     .holdUntil("06:00")
 *     .rampUntil("11:00", percent(85))
 *     .holdUntil("20:00")
 *     .rampUntil("23:00", percent(10))
 *     .build();
 * }</pre>
 */
public final class SeasonalCapacity {

    private SeasonalCapacity() {}

    /** Mirrors the {@code @PERCENT} token of the meta-language. */
    public static Percent percent(int value) {
        return Percent.of(value);
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    public static final class Builder {

        private final String name;
        private final DailySchedule.Builder curve = DailySchedule.builder();
        private Long max;
        private long min = 0L;
        private ZoneId zone;
        private Duration tick = Duration.ofSeconds(60);

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public String name() {
            return name;
        }

        public Builder max(long maxEntries) {
            this.max = maxEntries;
            return this;
        }

        public Builder min(long minEntries) {
            this.min = minEntries;
            return this;
        }

        public Builder zone(String zoneId) {
            return zone(ZoneId.of(zoneId));
        }

        public Builder zone(ZoneId zoneId) {
            this.zone = Objects.requireNonNull(zoneId, "zone");
            return this;
        }

        public Builder tick(Duration tick) {
            this.tick = Objects.requireNonNull(tick, "tick");
            return this;
        }

        public Builder startAt(String time, Percent percent) {
            curve.startAt(LocalTime.parse(time), percent);
            return this;
        }

        public Builder holdUntil(String time) {
            curve.holdUntil(LocalTime.parse(time));
            return this;
        }

        public Builder rampUntil(String time, Percent percent) {
            curve.rampUntil(LocalTime.parse(time), percent);
            return this;
        }

        public ScheduleSpec build() {
            if (max == null) {
                throw new IllegalStateException("max is required");
            }
            if (zone == null) {
                throw new IllegalStateException("zone is required");
            }
            return new ScheduleSpec(zone, max, min, tick, curve.build());
        }
    }
}
