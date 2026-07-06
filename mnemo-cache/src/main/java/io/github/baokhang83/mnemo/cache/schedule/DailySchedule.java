package io.github.baokhang83.mnemo.cache.schedule;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A daily {@link CapacitySchedule} built from an ordered set of keyframes.
 *
 * <p>The curve is described as a starting anchor followed by {@code hold} and
 * {@code ramp} segments (see {@link Builder}). Between consecutive keyframes the
 * fraction is linearly interpolated; a {@code hold} is simply a segment whose two
 * endpoints share the same fraction. The last keyframe's value holds across the
 * midnight wrap until the first anchor.
 */
public final class DailySchedule implements CapacitySchedule {

    private record Keyframe(LocalTime time, double fraction) {}

    private final List<Keyframe> keyframes; // sorted by time, at least one element

    private DailySchedule(List<Keyframe> keyframes) {
        this.keyframes = keyframes;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public double fractionAt(LocalTime time) {
        Keyframe first = keyframes.get(0);
        Keyframe last = keyframes.get(keyframes.size() - 1);

        if (time.equals(first.time())) {
            return first.fraction();
        }
        if (inWrapRegion(time, first, last)) {
            return last.fraction(); // hold the last value across midnight until the first anchor
        }
        return interpolateWithin(time, last);
    }

    /** True when {@code time} falls in the gap that wraps past the last keyframe to the first anchor. */
    private static boolean inWrapRegion(LocalTime time, Keyframe first, Keyframe last) {
        return time.isBefore(first.time()) || !time.isBefore(last.time());
    }

    /** {@code time} is strictly inside {@code (first, last)}: interpolate its bracketing segment. */
    private double interpolateWithin(LocalTime time, Keyframe last) {
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe a = keyframes.get(i);
            Keyframe b = keyframes.get(i + 1);
            if (!time.isBefore(a.time()) && time.isBefore(b.time())) {
                return interpolate(a, b, time);
            }
        }
        return last.fraction(); // unreachable given the caller's guards
    }

    private static double interpolate(Keyframe a, Keyframe b, LocalTime t) {
        double span = b.time().toSecondOfDay() - a.time().toSecondOfDay();
        if (span <= 0) {
            return a.fraction();
        }
        double pos = t.toSecondOfDay() - a.time().toSecondOfDay();
        return a.fraction() + (b.fraction() - a.fraction()) * (pos / span);
    }

    /** Fluent builder mirroring the {@code hold}/{@code ramp} meta-language verbs. */
    public static final class Builder {

        private final List<Keyframe> frames = new ArrayList<>();

        /** The required first anchor. Must be called before any segment. */
        public Builder startAt(LocalTime time, Percent percent) {
            if (!frames.isEmpty()) {
                throw new IllegalStateException("startAt(...) must be the first segment");
            }
            frames.add(new Keyframe(time, percent.fraction()));
            return this;
        }

        /** Hold the previous fraction flat until {@code time}. */
        public Builder holdUntil(LocalTime time) {
            Keyframe prev = requireStarted();
            requireForward(prev.time(), time);
            frames.add(new Keyframe(time, prev.fraction()));
            return this;
        }

        /** Linearly ramp from the previous fraction to {@code percent} by {@code time}. */
        public Builder rampUntil(LocalTime time, Percent percent) {
            Keyframe prev = requireStarted();
            requireForward(prev.time(), time);
            frames.add(new Keyframe(time, percent.fraction()));
            return this;
        }

        public DailySchedule build() {
            requireStarted();
            return new DailySchedule(List.copyOf(frames));
        }

        private Keyframe requireStarted() {
            if (frames.isEmpty()) {
                throw new IllegalStateException("schedule must start with startAt(...)");
            }
            return frames.get(frames.size() - 1);
        }

        private static void requireForward(LocalTime from, LocalTime to) {
            if (!to.isAfter(from)) {
                throw new IllegalArgumentException(
                        "times must move forward: " + to + " is not after " + from);
            }
        }
    }
}
