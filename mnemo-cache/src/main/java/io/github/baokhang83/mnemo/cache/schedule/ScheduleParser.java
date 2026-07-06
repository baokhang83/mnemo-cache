package io.github.baokhang83.mnemo.cache.schedule;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Parses the seasonal-cache meta-language into a {@link ScheduleSpec}.
 *
 * <p>Grammar (hand-written, zero parser dependencies):
 * <pre>
 * schedule := ('zone=' ZONE ';')? 'max=' INT ';'
 *             ('min=' INT ';')? ('tick=' DURATION ';')? 'curve=' curve
 * curve    := anchor (',' segment)*
 * anchor   := TIME '@' PERCENT
 * segment  := 'hold' TIME | 'ramp' TIME '@' PERCENT
 * </pre>
 *
 * <p>{@code zone} is syntactically optional in the grammar but semantically
 * <em>required</em>: the zone is never silently defaulted, so a missing zone fails fast.
 */
public final class ScheduleParser {

    private ScheduleParser() {}

    public static ScheduleSpec parse(String text) {
        if (text == null || text.isBlank()) {
            throw new ScheduleParseException("empty schedule");
        }

        ZoneId zone = null;
        Long max = null;
        long min = 0L;
        Duration tick = Duration.ofSeconds(60); // default
        String curveText = null;

        for (String rawPart : text.split(";")) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq < 0) {
                throw new ScheduleParseException("expected key=value, got: \"" + part + "\"");
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            switch (key) {
                case "zone" -> zone = parseZone(value);
                case "max" -> max = parseLong("max", value);
                case "min" -> min = parseLong("min", value);
                case "tick" -> tick = parseDuration(value);
                case "curve" -> curveText = value;
                default -> throw new ScheduleParseException("unknown key: \"" + key + "\"");
            }
        }

        if (zone == null) {
            throw new ScheduleParseException("zone is required");
        }
        if (max == null) {
            throw new ScheduleParseException("max is required");
        }
        if (curveText == null) {
            throw new ScheduleParseException("curve is required");
        }

        return new ScheduleSpec(zone, max, min, tick, parseCurve(curveText));
    }

    private static CapacitySchedule parseCurve(String curveText) {
        String[] tokens = curveText.split(",");
        DailySchedule.Builder builder = DailySchedule.builder();

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (i == 0) {
                Anchor anchor = parseAnchor(token, token);
                builder.startAt(anchor.time(), anchor.percent());
            } else if (token.startsWith("hold")) {
                builder.holdUntil(parseTime(token.substring(4).trim(), token));
            } else if (token.startsWith("ramp")) {
                Anchor anchor = parseAnchor(token.substring(4).trim(), token);
                builder.rampUntil(anchor.time(), anchor.percent());
            } else {
                throw new ScheduleParseException(
                        "expected 'hold' or 'ramp' segment, got: \"" + token + "\"");
            }
        }
        return builder.build();
    }

    private record Anchor(LocalTime time, Percent percent) {}

    private static Anchor parseAnchor(String body, String fragment) {
        int at = body.indexOf('@');
        if (at < 0) {
            throw new ScheduleParseException("expected TIME@PERCENT, got: \"" + fragment + "\"");
        }
        LocalTime time = parseTime(body.substring(0, at).trim(), fragment);
        Percent percent = parsePercent(body.substring(at + 1).trim(), fragment);
        return new Anchor(time, percent);
    }

    private static LocalTime parseTime(String s, String fragment) {
        try {
            return LocalTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new ScheduleParseException("invalid time \"" + s + "\" in: \"" + fragment + "\"");
        }
    }

    private static Percent parsePercent(String s, String fragment) {
        try {
            return Percent.of(Integer.parseInt(s));
        } catch (IllegalArgumentException e) { // NumberFormatException or Percent range check
            throw new ScheduleParseException(
                    "invalid percent \"" + s + "\" in: \"" + fragment + "\"");
        }
    }

    private static ZoneId parseZone(String s) {
        try {
            return ZoneId.of(s);
        } catch (RuntimeException e) {
            throw new ScheduleParseException("invalid zone: \"" + s + "\"");
        }
    }

    private static long parseLong(String key, String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ScheduleParseException("invalid " + key + ": \"" + s + "\"");
        }
    }

    private static Duration parseDuration(String s) {
        if (s.length() < 2) {
            throw new ScheduleParseException("invalid tick: \"" + s + "\"");
        }
        char unit = s.charAt(s.length() - 1);
        String num = s.substring(0, s.length() - 1);
        try {
            long value = Long.parseLong(num);
            return switch (unit) {
                case 's' -> Duration.ofSeconds(value);
                case 'm' -> Duration.ofMinutes(value);
                default -> throw new ScheduleParseException(
                        "invalid tick unit (expected 's' or 'm'): \"" + s + "\"");
            };
        } catch (NumberFormatException e) {
            throw new ScheduleParseException("invalid tick: \"" + s + "\"");
        }
    }
}
