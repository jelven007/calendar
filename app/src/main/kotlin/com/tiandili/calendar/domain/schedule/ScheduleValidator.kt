package com.tiandili.calendar.domain.schedule

enum class ScheduleField {
    TITLE,
    TIMING,
    NOTE,
    CHECKLIST,
    MEDIA,
}

enum class ScheduleValidationError(
    val field: ScheduleField,
) {
    TITLE_REQUIRED(ScheduleField.TITLE),
    TITLE_TOO_LONG(ScheduleField.TITLE),
    INVALID_TIME_RANGE(ScheduleField.TIMING),
    NOTE_TOO_LONG(ScheduleField.NOTE),
    INVALID_CHECKLIST(ScheduleField.CHECKLIST),
    INVALID_MEDIA(ScheduleField.MEDIA),
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
            schedule.note.length > if (schedule.kind in setOf(ScheduleKind.NOTE, ScheduleKind.ARTICLE))
                20000 else 1000
        }
        val checklistError = ScheduleValidationError.INVALID_CHECKLIST.takeIf {
            schedule.checklist.size > 50 ||
                schedule.checklist.any { it.title.isBlank() || it.title.length > 200 }
        }
        val mediaError = ScheduleValidationError.INVALID_MEDIA.takeIf {
            schedule.mediaUris.size > 9 || schedule.mediaUris.any { value ->
                value.length > 1024 || runCatching {
                    val uri = java.net.URI(value)
                    uri.scheme != "content" || uri.authority.isNullOrBlank()
                }.getOrDefault(true)
            }
        }
        return listOfNotNull(titleError, timingError, noteError, checklistError, mediaError)
    }
}
