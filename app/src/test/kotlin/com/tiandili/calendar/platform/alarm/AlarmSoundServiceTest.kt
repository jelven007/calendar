package com.tiandili.calendar.platform.alarm

import android.content.Intent
import android.app.Service
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class AlarmSoundServiceTest {
    @Test
    fun `alarm start creates media foreground notification with working stop action`() {
        val controller = Robolectric.buildService(AlarmSoundService::class.java).create()
        val service = controller.get()
        service.onStartCommand(AlarmSoundService.startIntent(service, 7, 3, "起床"), 0, 1)
        val notification = shadowOf(service).lastForegroundNotification
        assertThat(notification.category).isEqualTo(android.app.Notification.CATEGORY_ALARM)
        assertThat(notification.actions.single().title.toString()).isEqualTo("停止")
        service.onStartCommand(AlarmSoundService.stopIntent(service, 7), 0, 2)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(AlarmSoundService.activeJobId).isNull()
        controller.destroy()
    }

    @Test
    fun `empty service start cannot ring and stop action removes foreground state`() {
        val controller = Robolectric.buildService(AlarmSoundService::class.java).create()
        val service = controller.get()
        assertThat(service.onStartCommand(Intent(), 0, 1)).isEqualTo(Service.START_NOT_STICKY)
        assertThat(AlarmSoundService.activeJobId).isNull()
        service.onStartCommand(Intent().setAction(ACTION_STOP_ALARM), 0, 2)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        controller.destroy()
    }
}
