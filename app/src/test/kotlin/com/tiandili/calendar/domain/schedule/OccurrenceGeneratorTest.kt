package com.tiandili.calendar.domain.schedule

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

class OccurrenceGeneratorTest {

    @Test
    fun `yearly lunar new year follows the lunar month day instead of Gregorian anchor`() {
        val dates = OccurrenceGenerator.generateDates(
            LocalDate.of(2025, 1, 29),
            RecurrenceRule(RecurrenceFrequency.YEARLY, lunarMonth = 1, lunarDay = 1),
            LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31),
        )
        assertThat(dates).containsExactly(LocalDate.of(2026, 2, 17))
    }

    @Test
    fun `lunar day thirty clamps to a short month and leap month falls back to regular month`() {
        val dates = OccurrenceGenerator.generateDates(
            LocalDate.of(2025, 7, 25),
            RecurrenceRule(RecurrenceFrequency.YEARLY, lunarMonth = -6, lunarDay = 30),
            LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31),
        )
        val lunar = com.nlf.calendar.Solar.fromYmd(
            dates.single().year, dates.single().monthValue, dates.single().dayOfMonth).lunar
        assertThat(lunar.month).isEqualTo(6)
        assertThat(lunar.day).isEqualTo(
            com.nlf.calendar.LunarMonth.fromYm(2026, 6).dayCount)
    }

    @Test
    fun `monthly recurrence clamps the anchor day to the end of short months`() {
        val occurrences = OccurrenceGenerator.generateDates(
            startDate = LocalDate.of(2025, 1, 31),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY),
            window = LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 4, 30),
        )

        assertThat(occurrences).containsExactly(
            LocalDate.of(2025, 1, 31),
            LocalDate.of(2025, 2, 28),
            LocalDate.of(2025, 3, 31),
            LocalDate.of(2025, 4, 30),
        ).inOrder()
    }

    @Test
    fun `monthly recurrence respects its interval`() {
        val occurrences = OccurrenceGenerator.generateDates(
            startDate = LocalDate.of(2025, 1, 31),
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 2,
            ),
            window = LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 6, 30),
        )

        assertThat(occurrences).containsExactly(
            LocalDate.of(2025, 1, 31),
            LocalDate.of(2025, 3, 31),
            LocalDate.of(2025, 5, 31),
        ).inOrder()
    }

    @Test
    fun `recurrence interval must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 0,
            )
        }
    }

    @Test
    fun `recurrence count must be positive when present`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                count = 0,
            )
        }
    }

    @Test
    fun `yearly February 29 recurrence clamps to February 28 in common years`() {
        val occurrences = OccurrenceGenerator.generateDates(
            startDate = LocalDate.of(2024, 2, 29),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.YEARLY),
            window = LocalDate.of(2024, 1, 1)..LocalDate.of(2027, 12, 31),
        )

        assertThat(occurrences).containsExactly(
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2025, 2, 28),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2027, 2, 28),
        ).inOrder()
    }

    @Test
    fun `daily recurrence respects its interval`() {
        val occurrences = OccurrenceGenerator.generateDates(
            startDate = LocalDate.of(2025, 1, 1),
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 2,
            ),
            window = LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 1, 7),
        )

        assertThat(occurrences).containsExactly(
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 3),
            LocalDate.of(2025, 1, 5),
            LocalDate.of(2025, 1, 7),
        ).inOrder()
    }

    @Test
    fun `weekly recurrence emits only selected weekdays`() {
        val occurrences = OccurrenceGenerator.generateDates(
            startDate = LocalDate.of(2025, 1, 1),
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            ),
            window = LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 1, 12),
        )

        assertThat(occurrences).containsExactly(
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 6),
            LocalDate.of(2025, 1, 8),
        ).inOrder()
    }

    @Test
    fun `recurrence includes its until date and stops afterward`() {
        val occurrences = OccurrenceGenerator.generateDates(
            startDate = LocalDate.of(2025, 1, 1),
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                until = LocalDate.of(2025, 1, 3),
            ),
            window = LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 1, 10),
        )

        assertThat(occurrences).containsExactly(
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 2),
            LocalDate.of(2025, 1, 3),
        ).inOrder()
    }

    @Test
    fun `recurrence stops after its occurrence count`() {
        val occurrences = OccurrenceGenerator.generateDates(
            startDate = LocalDate.of(2025, 1, 1),
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                count = 3,
            ),
            window = LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 1, 10),
        )

        assertThat(occurrences).containsExactly(
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 2),
            LocalDate.of(2025, 1, 3),
        ).inOrder()
    }

    @Test
    fun `occurrence count applies before window filtering`() {
        val occurrences = OccurrenceGenerator.generateDates(
            startDate = LocalDate.of(2025, 1, 1),
            rule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                count = 3,
            ),
            window = LocalDate.of(2025, 1, 5)..LocalDate.of(2025, 1, 10),
        )

        assertThat(occurrences).isEmpty()
    }
}
