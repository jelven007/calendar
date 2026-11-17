package com.tiandili.calendar.platform.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.net.toUri
import com.tiandili.calendar.R
import com.tiandili.calendar.TiandiliApplication
import com.tiandili.calendar.platform.reminder.EXTRA_REMINDER_JOB_ID
import com.tiandili.calendar.platform.reminder.EXTRA_SCHEDULE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

const val ACTION_START_ALARM = "com.tiandili.calendar.action.START_ALARM"
const val ACTION_STOP_ALARM = "com.tiandili.calendar.action.STOP_ALARM"
const val ALARM_CHANNEL_ID = "ringing_alarms"
private const val EXTRA_ALARM_TITLE = "alarm_title"
private const val MAX_RING_DURATION_MILLIS = 5 * 60 * 1000L

class AlarmSoundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = AlarmSession()
    private val handler = Handler(Looper.getMainLooper())
    private var validationJob: kotlinx.coroutines.Job? = null
    private var pendingJobId: Long? = null
    private val stopTimeout = Runnable { finishRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getLongExtra(EXTRA_REMINDER_JOB_ID, 0) ?: 0
        if (intent?.action == ACTION_STOP_ALARM) {
            if (jobId == 0L || jobId == pendingJobId) finishRinging()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START_ALARM || jobId <= 0) {
            if (pendingJobId == null) stopSelf()
            return START_NOT_STICKY
        }
        validationJob?.cancel()
        session.stop()
        activeJobId = null
        pendingJobId = jobId
        val app = application as TiandiliApplication
        val title = intent.getStringExtra(EXTRA_ALARM_TITLE).orEmpty().ifBlank { "闹钟" }
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, 0)
        val notification = alarmNotification(this, jobId, scheduleId, title,
            app.userPreferences.value.showScheduleTitleOnLockScreen)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(notificationId(jobId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notificationId(jobId), notification)
        }
        handler.removeCallbacks(stopTimeout)
        handler.postDelayed(stopTimeout, MAX_RING_DURATION_MILLIS)
        validationJob = scope.launch {
            try {
                app.database.scheduleDao().observeRingingJobActive(jobId).collect { authorized ->
                    if (!authorized || pendingJobId != jobId) {
                        finishRinging()
                    } else if (session.jobId != jobId) {
                        val job = app.database.scheduleDao().getReminderJob(jobId)
                        val age = System.currentTimeMillis() - (job?.triggerAtEpochMillis ?: 0)
                        if (age !in -1000..MAX_RING_DURATION_MILLIS) {
                            finishRinging()
                        } else {
                            session.start(jobId, SystemAlarmSound(this@AlarmSoundService) { finishRinging() })
                            activeJobId = jobId
                        }
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                finishRinging()
            }
        }
        return START_NOT_STICKY
    }

    private fun finishRinging() {
        validationJob?.cancel()
        handler.removeCallbacks(stopTimeout)
        session.stop()
        activeJobId = null
        pendingJobId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        finishRinging()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var activeJobId: Long? = null
            private set

        fun startIntent(context: Context, jobId: Long, scheduleId: Long, title: String): Intent =
            Intent(context, AlarmSoundService::class.java).setAction(ACTION_START_ALARM)
                .setData("tiandili://ring/$jobId".toUri())
                .putExtra(EXTRA_REMINDER_JOB_ID, jobId)
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId).putExtra(EXTRA_ALARM_TITLE, title)

        fun stopIntent(context: Context, jobId: Long): Intent =
            Intent(context, AlarmSoundService::class.java).setAction(ACTION_STOP_ALARM)
                .setData("tiandili://stop-ring/$jobId".toUri()).putExtra(EXTRA_REMINDER_JOB_ID, jobId)
    }
}

private fun notificationId(jobId: Long): Int = jobId.hashCode().takeUnless { it == 0 } ?: 1

internal fun alarmNotification(
    context: Context, jobId: Long, scheduleId: Long, title: String, showTitle: Boolean,
): Notification {
    val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
    manager.createNotificationChannel(NotificationChannel(ALARM_CHANNEL_ID, "闹钟响铃",
        NotificationManager.IMPORTANCE_HIGH).apply {
        description = "播放系统闹钟声音，支持从通知停止"
        setSound(null, null)
    })
    val content = PendingIntent.getActivity(context, jobId.hashCode(),
        Intent(context, AlarmRingingActivity::class.java)
            .setData("tiandili://ringing/$jobId".toUri())
            .putExtra(EXTRA_REMINDER_JOB_ID, jobId).putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            .putExtra(EXTRA_ALARM_TITLE, title),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val stop = PendingIntent.getService(context, jobId.hashCode(),
        AlarmSoundService.stopIntent(context, jobId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val public = Notification.Builder(context, ALARM_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("天地历闹钟")
        .setContentText("点击停止响铃").build()
    return Notification.Builder(context, ALARM_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(title)
        .setContentText("闹钟正在响铃 · 点击停止")
        .setContentIntent(content).setOngoing(true).setCategory(Notification.CATEGORY_ALARM)
        .setVisibility(if (showTitle) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE)
        .setPublicVersion(public)
        .addAction(Notification.Action.Builder(null, "停止", stop).build())
        .apply {
            if (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) {
                setFullScreenIntent(content, true)
            }
        }.build()
}

private class SystemAlarmSound(
    private val context: Context,
    private val onFocusLost: () -> Unit,
) : AlarmSound {
    private val manager = requireNotNull(context.getSystemService(AudioManager::class.java))
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS) onFocusLost()
        }.build()
    private var player: MediaPlayer? = null

    override fun play() {
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw IllegalStateException("无法获取闹钟音频焦点")
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val nextPlayer = MediaPlayer()
        player = nextPlayer
        nextPlayer.apply {
            setAudioAttributes(attributes)
            setDataSource(context, uri)
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            isLooping = true
            prepare()
            start()
        }
    }

    override fun stop() {
        player?.release()
        player = null
        manager.abandonAudioFocusRequest(focus)
    }
}
