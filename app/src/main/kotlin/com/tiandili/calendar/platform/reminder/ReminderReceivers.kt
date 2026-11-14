package com.tiandili.calendar.platform.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

const val ALARM_MANAGER_EXACT_PERMISSION_ACTION =
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != REMINDER_ALARM_ACTION) {
            return
        }
        val jobId = intent.getLongExtra(EXTRA_REMINDER_JOB_ID, 0)
        if (jobId <= 0) {
            return
        }
        val pendingResult = goAsync()
        val runtime = context.reminderRuntime()
        if (runtime == null) {
            pendingResult?.finish()
            return
        }
        runtime.dispatch(jobId) {
            pendingResult?.finish()
        }
    }
}

class ReminderRecoveryReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in RecoveryActions) {
            return
        }
        val pendingResult = goAsync()
        val runtime = context.reminderRuntime()
        if (runtime == null) {
            pendingResult?.finish()
            return
        }
        runtime.reconcile(rebuildScheduled = true) {
            pendingResult?.finish()
        }
    }
}

private fun Context.reminderRuntime(): ReminderRuntime? =
    (applicationContext as? ReminderRuntimeOwner)?.reminderRuntime

private val RecoveryActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    ALARM_MANAGER_EXACT_PERMISSION_ACTION,
)
