package io.github.baokhang83.mnemo.cache.schedule;

import static io.github.baokhang83.mnemo.cache.schedule.Percent.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class DailyScheduleTest {

    private static final double EPS = 1e-9;

    @Test
    void singleAnchorIsConstantAllDay() {
        CapacitySchedule s = DailySchedule.builder()
                .startAt(LocalTime.of(0, 0), of(25))
                .build();

        assertEquals(0.25, s.fractionAt(LocalTime.of(0, 0)), EPS);
        assertEquals(0.25, s.fractionAt(LocalTime.of(13, 37)), EPS);
        assertEquals(0.25, s.fractionAt(LocalTime.of(23, 59)), EPS);
    }

    @Test
    void holdKeepsPreviousFractionFlat() {
        CapacitySchedule s = DailySchedule.builder()
                .startAt(LocalTime.of(0, 0), of(10))
                .holdUntil(LocalTime.of(6, 0))
                .build();

        assertEquals(0.10, s.fractionAt(LocalTime.of(3, 0)), EPS);
        assertEquals(0.10, s.fractionAt(LocalTime.of(6, 0)), EPS);
    }

    @Test
    void rampInterpolatesLinearly() {
        CapacitySchedule s = DailySchedule.builder()
                .startAt(LocalTime.of(6, 0), of(10))
                .rampUntil(LocalTime.of(11, 0), of(85))
                .build();

        assertEquals(0.10, s.fractionAt(LocalTime.of(6, 0)), EPS);
        assertEquals(0.475, s.fractionAt(LocalTime.of(8, 30)), EPS); // midpoint of 10 -> 85
        assertEquals(0.85, s.fractionAt(LocalTime.of(11, 0)), EPS);
    }

    @Test
    void lastValueHoldsAcrossTheMidnightWrap() {
        CapacitySchedule s = fullDayCurve();
        // After the last anchor (23:00@10) until midnight -> hold 10%.
        assertEquals(0.10, s.fractionAt(LocalTime.of(23, 30)), EPS);

        // When the first anchor is not midnight, the pre-anchor gap also holds the last value.
        CapacitySchedule late = DailySchedule.builder()
                .startAt(LocalTime.of(2, 0), of(40))
                .rampUntil(LocalTime.of(20, 0), of(90))
                .build();
        assertEquals(0.90, late.fractionAt(LocalTime.of(1, 0)), EPS);
    }

    @Test
    void realisticReclaimCurve() {
        CapacitySchedule s = fullDayCurve();

        assertEquals(0.10, s.fractionAt(LocalTime.of(3, 0)), EPS);  // overnight plateau
        assertEquals(0.85, s.fractionAt(LocalTime.of(15, 0)), EPS); // daytime plateau

        double morning = s.fractionAt(LocalTime.of(9, 0));          // mid morning ramp-up
        assertTrue(morning > 0.10 && morning < 0.85, "expected ramp, got " + morning);
    }

    private static CapacitySchedule fullDayCurve() {
        return DailySchedule.builder()
                .startAt(LocalTime.of(0, 0), of(10))
                .holdUntil(LocalTime.of(6, 0))
                .rampUntil(LocalTime.of(11, 0), of(85))
                .holdUntil(LocalTime.of(20, 0))
                .rampUntil(LocalTime.of(23, 0), of(10))
                .build();
    }
}
