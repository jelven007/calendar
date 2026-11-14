package com.tiandili.calendar.domain.schedule

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class TimedOccurrenceGeneratorTest {

    @Test
    fun `nonexistent local time advances by the daylight saving gap`() {
        val occurrences = OccurrenceGenerator.generateTimed(
            timing = ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 3, 9, 2, 30),
                endsAt = LocalDateTime.of(2025, 3, 9, 3, 30),
                zoneId = ZoneId.of("America/New_York"),
            ),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY),
            window = LocalDate.of(2025, 3, 9)..LocalDate.of(2025, 3, 9),
        )

        assertThat(occurrences.single().startsAt.toLocalDateTime())
            .isEqualTo(LocalDateTime.of(2025, 3, 9, 3, 30))
        assertThat(occurrences.single().startsAt.offset)
            .isEqualTo(ZoneOffset.ofHours(-4))
    }

    @Test
    fun `ambiguous local time uses the earlier offset`() {
        val occurrences = OccurrenceGenerator.generateTimed(
            timing = ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 11, 2, 1, 30),
                endsAt = LocalDateTime.of(2025, 11, 2, 2, 30),
                zoneId = ZoneId.of("America/New_York"),
            ),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY),
            window = LocalDate.of(2025, 11, 2)..LocalDate.of(2025, 11, 2),
        )

        assertThat(occurrences.single().startsAt.offset)
            .isEqualTo(ZoneOffset.ofHours(-4))
    }
}
