package com.tiandili.calendar.domain.reminder

import java.time.Clock
import kotlinx.coroutines.CancellationException

data class ReminderDelivery(
    val jobId: Long,
    val scheduleId: Long,
    val title: String,
    val occurrenceEpochMillis: Long,
    val triggerAtEpochMillis: Long,
)

data class ReminderNotification(
    val jobId: Long,
    val scheduleId: Long,
    val title: String,
    val occurrenceEpochMillis: Long,
    val missed: Boolean,
)

interface ReminderDeliveryStore {
    suspend fun claim(jobId: Long): ReminderDelivery?

    suspend fun markFired(jobId: Long)

    suspend fun markBlocked(jobId: Long)

    suspend fun markSkipped(jobId: Long)

    suspend fun releaseForRetry(
        jobId: Long,
        message: String,
    )
}

interface ReminderNotificationGateway {
    fun post(notification: ReminderNotification): Boolean
}

class ReminderDispatcher(
    private val store: ReminderDeliveryStore,
    private val notifications: ReminderNotificationGateway,
    private val clock: Clock,
) {

    suspend fun dispatch(jobId: Long) {
        val delivery = store.claim(jobId) ?: return
        try {
            val ageMillis = clock.millis() - delivery.triggerAtEpochMillis
            if (ageMillis > MISSED_REMINDER_WINDOW_MILLIS) {
                store.markSkipped(jobId)
                return
            }
            val posted = notifications.post(
                ReminderNotification(
                    jobId = delivery.jobId,
                    scheduleId = delivery.scheduleId,
                    title = delivery.title,
                    occurrenceEpochMillis = delivery.occurrenceEpochMillis,
                    missed = ageMillis > 0,
                ),
            )
            if (posted) {
                store.markFired(jobId)
            } else {
                store.markBlocked(jobId)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            store.releaseForRetry(
                jobId = jobId,
                message = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private companion object {
        const val MISSED_REMINDER_WINDOW_MILLIS = 24 * 60 * 60 * 1000L
    }
}
