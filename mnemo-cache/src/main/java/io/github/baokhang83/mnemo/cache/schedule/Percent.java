package io.github.baokhang83.mnemo.cache.schedule;

/**
 * A whole-number percentage in {@code [0, 100]}, used to express a fraction of a
 * cache's maximum capacity at a point on the seasonality curve.
 *
 * @param value the percentage, {@code 0..100}
 */
public record Percent(int value) {

    public Percent {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("percent must be within 0..100, got " + value);
        }
    }

    public static Percent of(int value) {
        return new Percent(value);
    }

    /** @return this percentage as a fraction in {@code [0.0, 1.0]} */
    public double fraction() {
        return value / 100.0;
    }
}
