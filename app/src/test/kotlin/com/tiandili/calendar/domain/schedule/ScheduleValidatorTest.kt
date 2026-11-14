package com.tiandili.calendar.domain.schedule

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class ScheduleValidatorTest {

    @Test
    fun `blank title is rejected on the title field`() {
        val errors = ScheduleValidator.validate(
            Schedule(
                title = "   ",
                timing = validTimedSchedule(),
            ),
        )

        assertThat(errors).containsExactly(ScheduleValidationError.TITLE_REQUIRED)
        assertThat(errors.single().field).isEqualTo(ScheduleField.TITLE)
    }

    @Test
    fun `title longer than 80 characters is rejected on the title field`() {
        val errors = ScheduleValidator.validate(
            Schedule(
                title = "天".repeat(81),
                timing = validTimedSchedule(),
            ),
        )

        assertThat(errors).hasSize(1)
        assertThat(errors.single().field).isEqualTo(ScheduleField.TITLE)
    }

    @Test
    fun `timed schedule ending before it starts is rejected on the timing field`() {
        val errors = ScheduleValidator.validate(
            Schedule(
                title = "项目评审",
                timing = ScheduleTiming.Timed(
                    startsAt = LocalDateTime.of(2025, 1, 1, 10, 0),
                    endsAt = LocalDateTime.of(2025, 1, 1, 9, 0),
                    zoneId = ZoneId.of("Asia/Shanghai"),
                ),
            ),
        )

        assertThat(errors).hasSize(1)
        assertThat(errors.single().field.name).isEqualTo("TIMING")
    }

    @Test
    fun `all day schedule ending on or before its start is rejected on the timing field`() {
        val startDate = LocalDate.of(2025, 1, 2)

        listOf(startDate, startDate.minusDays(1)).forEach { endDateExclusive ->
            val errors = ScheduleValidator.validate(
                Schedule(
                    title = "全天安排",
                    timing = ScheduleTiming.AllDay(
                        startDate = startDate,
                        endDateExclusive = endDateExclusive,
                    ),
                ),
            )

            assertThat(errors).hasSize(1)
            assertThat(errors.single().field.name).isEqualTo("TIMING")
        }
    }

    @Test
    fun `note longer than 1000 characters is rejected on the note field`() {
        val errors = ScheduleValidator.validate(
            Schedule(
                title = "有备注的安排",
                timing = validTimedSchedule(),
                note = "记".repeat(1001),
            ),
        )

        assertThat(errors).hasSize(1)
        assertThat(errors.single().field.name).isEqualTo("NOTE")
    }

    private fun validTimedSchedule() = ScheduleTiming.Timed(
        startsAt = LocalDateTime.of(2025, 1, 1, 9, 0),
        endsAt = LocalDateTime.of(2025, 1, 1, 10, 0),
        zoneId = ZoneId.of("Asia/Shanghai"),
    )
}
