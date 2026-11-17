package com.tiandili.calendar.domain.reminder

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReminderDispatcherTest {

    @Test
    fun `alarm recovery only rings within five minutes while schedules keep twenty four hours`() = runTest {
        val alarm = sampleDelivery(NOW_EPOCH_MILLIS - 6 * 60 * 1000L)
            .copy(kind = com.tiandili.calendar.domain.schedule.ScheduleKind.ALARM)
        val store = FakeReminderDeliveryStore(alarm)
        val notifications = FakeReminderNotificationGateway()
        ReminderDispatcher(store, notifications, fixedClock()).dispatch(alarm.jobId)
        assertThat(store.skipped).containsExactly(alarm.jobId)
        assertThat(notifications.posted).isEmpty()
        val recent = alarm.copy(triggerAtEpochMillis = NOW_EPOCH_MILLIS - 4 * 60 * 1000L)
        ReminderDispatcher(FakeReminderDeliveryStore(recent), notifications, fixedClock()).dispatch(recent.jobId)
        assertThat(notifications.posted.single().kind)
            .isEqualTo(com.tiandili.calendar.domain.schedule.ScheduleKind.ALARM)
    }

    @Test
    fun `dispatch claims a scheduled job and marks it fired after notification`() = runTest {
        val delivery = sampleDelivery(triggerAtEpochMillis = NOW_EPOCH_MILLIS)
        val store = FakeReminderDeliveryStore(delivery)
        val notifications = FakeReminderNotificationGateway()

        ReminderDispatcher(store, notifications, fixedClock()).dispatch(delivery.jobId)

        assertThat(notifications.posted).containsExactly(
            ReminderNotification(
                jobId = delivery.jobId,
                scheduleId = delivery.scheduleId,
                title = "项目回顾",
                occurrenceEpochMillis = delivery.occurrenceEpochMillis,
                missed = false,
            ),
        )
        assertThat(store.fired).containsExactly(delivery.jobId)
    }

    @Test
    fun `dispatch marks a claimed job blocked when notifications are disabled`() = runTest {
        val delivery = sampleDelivery(triggerAtEpochMillis = NOW_EPOCH_MILLIS)
        val store = FakeReminderDeliveryStore(delivery)
        val notifications = FakeReminderNotificationGateway(canPost = false)

        ReminderDispatcher(store, notifications, fixedClock()).dispatch(delivery.jobId)

        assertThat(store.blocked).containsExactly(delivery.jobId)
        assertThat(store.fired).isEmpty()
    }

    @Test
    fun `dispatch labels a trigger missed within the recovery window`() = runTest {
        val delivery = sampleDelivery(
            triggerAtEpochMillis = NOW_EPOCH_MILLIS - 23 * 60 * 60 * 1000L,
        )
        val notifications = FakeReminderNotificationGateway()

        ReminderDispatcher(
            store = FakeReminderDeliveryStore(delivery),
            notifications = notifications,
            clock = fixedClock(),
        ).dispatch(delivery.jobId)

        assertThat(notifications.posted.single().missed).isTrue()
    }

    @Test
    fun `dispatch skips a trigger older than the recovery window`() = runTest {
        val delivery = sampleDelivery(
            triggerAtEpochMillis = NOW_EPOCH_MILLIS - 25 * 60 * 60 * 1000L,
        )
        val store = FakeReminderDeliveryStore(delivery)
        val notifications = FakeReminderNotificationGateway()

        ReminderDispatcher(store, notifications, fixedClock()).dispatch(delivery.jobId)

        assertThat(notifications.posted).isEmpty()
        assertThat(store.skipped).containsExactly(delivery.jobId)
    }

    @Test
    fun `dispatch releases a claimed job when notification publishing fails`() = runTest {
        val delivery = sampleDelivery(triggerAtEpochMillis = NOW_EPOCH_MILLIS)
        val store = FakeReminderDeliveryStore(delivery)
        val notifications = FakeReminderNotificationGateway(
            failure = IllegalStateException("notification service unavailable"),
        )

        ReminderDispatcher(store, notifications, fixedClock()).dispatch(delivery.jobId)

        assertThat(store.released).containsExactly(
            delivery.jobId to "notification service unavailable",
        )
    }

    private fun fixedClock(): Clock =
        Clock.fixed(Instant.ofEpochMilli(NOW_EPOCH_MILLIS), ZoneOffset.UTC)

    private fun sampleDelivery(triggerAtEpochMillis: Long) = ReminderDelivery(
        jobId = 7,
        scheduleId = 3,
        title = "项目回顾",
        occurrenceEpochMillis = NOW_EPOCH_MILLIS + 15 * 60 * 1000L,
        triggerAtEpochMillis = triggerAtEpochMillis,
    )

    private companion object {
        const val NOW_EPOCH_MILLIS = 1_735_689_600_000L
    }
}

private class FakeReminderDeliveryStore(
    private var delivery: ReminderDelivery?,
) : ReminderDeliveryStore {
    val fired = mutableListOf<Long>()
    val blocked = mutableListOf<Long>()
    val skipped = mutableListOf<Long>()
    val released = mutableListOf<Pair<Long, String>>()

    override suspend fun claim(jobId: Long): ReminderDelivery? =
        delivery?.takeIf { it.jobId == jobId }.also {
            if (it != null) {
                delivery = null
            }
        }

    override suspend fun markFired(jobId: Long) {
        fired += jobId
    }

    override suspend fun markBlocked(jobId: Long) {
        blocked += jobId
    }

    override suspend fun markSkipped(jobId: Long) {
        skipped += jobId
    }

    override suspend fun releaseForRetry(
        jobId: Long,
        message: String,
    ) {
        released += jobId to message
    }
}

private class FakeReminderNotificationGateway(
    private val canPost: Boolean = true,
    private val failure: RuntimeException? = null,
) : ReminderNotificationGateway {
    val posted = mutableListOf<ReminderNotification>()

    override fun post(notification: ReminderNotification): Boolean {
        failure?.let { throw it }
        if (!canPost) {
            return false
        }
        posted += notification
        return true
    }
}
