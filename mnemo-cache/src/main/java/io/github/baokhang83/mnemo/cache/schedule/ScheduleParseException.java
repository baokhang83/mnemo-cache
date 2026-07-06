package io.github.baokhang83.mnemo.cache.schedule;

/**
 * Thrown when a schedule meta-language string is malformed. The message names the
 * offending fragment so operators can fix it fast. Configuration parsing fails loudly
 * rather than silently falling back to "no seasonality".
 */
public class ScheduleParseException extends IllegalArgumentException {

    public ScheduleParseException(String message) {
        super(message);
    }
}
