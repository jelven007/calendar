package com.tiandili.calendar.platform.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

interface WidgetRefreshOwner {
    fun requestWidgetRefresh(resetMidnight: Boolean = false)
}

class WidgetRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            (context.applicationContext as? WidgetRefreshOwner)?.requestWidgetRefresh(resetMidnight = true)
        }
    }
}
