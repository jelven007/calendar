package com.tiandili.calendar.feature.mine

import com.tiandili.calendar.domain.schedule.*
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun Schedule.recordDate(): LocalDate = when (val value = timing) {
    is ScheduleTiming.AllDay -> value.startDate
    is ScheduleTiming.Timed -> value.startsAt.toLocalDate()
}

fun nextRecordDate(
    record: Schedule,
    today: LocalDate,
    cancelledStarts: Set<LocalDate> = emptySet(),
): LocalDate? {
    if (!record.enabled || record.completed || today.year !in 1901..2099) return null
    val limit = LocalDate.of(2099, 12, 31)
    if (record.recurrence == null && record.recordDate() < today) {
        val end = when (val timing = record.timing) {
            is ScheduleTiming.AllDay -> timing.endDateExclusive.minusDays(1)
            is ScheduleTiming.Timed -> timing.endsAt.toLocalDate()
        }
        if (end < today) return null
    }
    var start = today
    while (start <= limit) {
        val end = minOf(start.plusYears(2), limit)
        record.occurrenceDates(start..end, cancelledStarts).minOrNull()?.let { return it }
        if (end == limit) break
        start = end.plusDays(1)
    }
    return null
}

fun upcomingRecords(
    records: List<Schedule>,
    kind: ScheduleKind,
    clock: Clock,
    cancelled: Map<Long, Set<LocalDate>> = emptyMap(),
): List<Pair<Schedule, LocalDate>> = records.filter { it.kind == kind }.mapNotNull { record ->
    val zone = (record.timing as? ScheduleTiming.Timed)?.zoneId ?: clock.zone
    val now = clock.instant().atZone(zone)
    val today = now.toLocalDate()
    var date = nextRecordDate(record, today, cancelled[record.id].orEmpty())
    val timing = record.timing as? ScheduleTiming.Timed
    if (date == today && timing != null && kind in setOf(ScheduleKind.SCHEDULE, ScheduleKind.ALARM) &&
        timing.endsAt.toLocalTime() <= now.toLocalTime() &&
        timing.startsAt.toLocalDate() == timing.endsAt.toLocalDate()) {
        date = nextRecordDate(record, today.plusDays(1), cancelled[record.id].orEmpty())
    }
    date?.let { record to it }
}.sortedWith(compareBy<Pair<Schedule, LocalDate>> { it.second }
    .thenBy { (it.first.timing as? ScheduleTiming.Timed)?.startsAt?.toLocalTime() })

fun searchRecords(records: List<Schedule>, query: String): List<Schedule> {
    val text = query.trim()
    if (text.isEmpty()) return emptyList()
    return records.filter { it.title.contains(text, ignoreCase = true) ||
        it.note.contains(text, ignoreCase = true) ||
        it.checklist.any { item -> item.title.contains(text, ignoreCase = true) } }
}

fun countdownText(today: LocalDate, date: LocalDate): String =
    when (val days = ChronoUnit.DAYS.between(today, date)) {
        0L -> "就是今天"
        else -> "还有 $days 天"
    }
