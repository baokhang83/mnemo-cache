package io.github.baokhang83.mnemo.cache.schedule;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ScheduleValidationTest {

    @Test
    void percentRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> Percent.of(-1));
        assertThrows(IllegalArgumentException.class, () -> Percent.of(101));
    }

    @Test
    void builderRequiresStartAtFirst() {
        assertThrows(IllegalStateException.class,
                () -> DailySchedule.builder().holdUntil(LocalTime.of(6, 0)));
    }

    @Test
    void timesMustMoveForward() {
        assertThrows(IllegalArgumentException.class,
                () -> DailySchedule.builder()
                        .startAt(LocalTime.of(10, 0), Percent.of(10))
                        .rampUntil(LocalTime.of(9, 0), Percent.of(20)));
    }

    @Test
    void buildRequiresAtLeastAnAnchor() {
        assertThrows(IllegalStateException.class, () -> DailySchedule.builder().build());
    }
}
