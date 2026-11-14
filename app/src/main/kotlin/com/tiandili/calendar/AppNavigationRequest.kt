package com.tiandili.calendar

data class AppNavigationRequest(
    val destination: String,
    val scheduleId: Long? = null,
    val sequence: Long = 0,
)

const val EXTRA_DESTINATION = "com.tiandili.calendar.extra.DESTINATION"
