package io.github.baokhang83.mnemo.cache.schedule;

import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Resolves the effective {@link ScheduleSpec} for a named cache, applying the precedence
 * <strong>system property &gt; environment variable &gt; programmatic default</strong>.
 *
 * <p>External config (property/env) is the meta-language string parsed by
 * {@link ScheduleParser}; a malformed value fails fast rather than falling back.
 * The config sources are injected as lookup functions so resolution is fully testable
 * without touching real system state.
 */
public final class ScheduleResolver {

    private ScheduleResolver() {}

    /** {@code mnemo.cache.<name>.schedule} */
    public static String propertyKey(String name) {
        return "mnemo.cache." + name + ".schedule";
    }

    /** {@code MNEMO_CACHE_<NAME>_SCHEDULE} (name upper-cased, non-alphanumerics to {@code _}). */
    public static String envKey(String name) {
        String normalized = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
        return "MNEMO_CACHE_" + normalized + "_SCHEDULE";
    }

    /** Resolve against the live JVM (system properties and environment). */
    public static ScheduleSpec resolve(String name, ScheduleSpec programmaticDefault) {
        return resolve(name, programmaticDefault, System::getProperty, System::getenv);
    }

    /**
     * Resolve against injected config sources.
     *
     * @param properties key &rarr; value lookup for system properties ({@code null} if absent)
     * @param env        key &rarr; value lookup for environment variables ({@code null} if absent)
     */
    public static ScheduleSpec resolve(String name, ScheduleSpec programmaticDefault,
            UnaryOperator<String> properties, UnaryOperator<String> env) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(programmaticDefault, "programmaticDefault");

        String fromProperty = properties.apply(propertyKey(name));
        if (isPresent(fromProperty)) {
            return ScheduleParser.parse(fromProperty);
        }
        String fromEnv = env.apply(envKey(name));
        if (isPresent(fromEnv)) {
            return ScheduleParser.parse(fromEnv);
        }
        return programmaticDefault;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
