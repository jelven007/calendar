package com.tiandili.calendar.domain.schedule

enum class ScheduleField {
    TITLE,
    TIMING,
    NOTE,
}

enum class ScheduleValidationError(
    val field: ScheduleField,
) {
    TITLE_REQUIRED(ScheduleField.TITLE),
    TITLE_TOO_LONG(ScheduleField.TITLE),
    INVALID_TIME_RANGE(ScheduleField.TIMING),
    NOTE_TOO_LONG(ScheduleField.NOTE),
}

object ScheduleValidator {

    fun validate(schedule: Schedule): List<ScheduleValidationError> {
        val titleError = when {
            schedule.title.isBlank() -> ScheduleValidationError.TITLE_REQUIRED
            schedule.title.length > 80 -> ScheduleValidationError.TITLE_TOO_LONG
            else -> null
        }
        val timingError = when (val timing = schedule.timing) {
            is ScheduleTiming.AllDay ->
                ScheduleValidationError.INVALID_TIME_RANGE.takeIf {
                    timing.endDateExclusive <= timing.startDate
                }
            is ScheduleTiming.Timed ->
                ScheduleValidationError.INVALID_TIME_RANGE.takeIf {
                    timing.endsAt < timing.startsAt
                }
        }
        val noteError = ScheduleValidationError.NOTE_TOO_LONG.takeIf {
            schedule.note.length > 1000
        }
        return listOfNotNull(titleError, timingError, noteError)
    }
}
