package com.tiandili.calendar.domain.schedule

import com.squareup.moshi.JsonClass

enum class ScheduleKind(val label: String) {
    NOTE("记事"), SCHEDULE("日程"), BIRTHDAY("生日"), ANNIVERSARY("纪念日"),
    ALARM("闹钟"), TODO("待办"), ALBUM("影集"), ARTICLE("图文"),
}

@JsonClass(generateAdapter = true)
data class ChecklistItem(val title: String, val completed: Boolean = false)
