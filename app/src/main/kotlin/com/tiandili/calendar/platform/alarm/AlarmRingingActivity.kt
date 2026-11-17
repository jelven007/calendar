package com.tiandili.calendar.platform.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiandili.calendar.TiandiliApplication
import com.tiandili.calendar.platform.reminder.EXTRA_REMINDER_JOB_ID
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmRingingActivity : ComponentActivity() {
    private var ringingIntent by mutableStateOf<Intent?>(null)
    private fun stopAlarm() {
        val id = intent.getLongExtra(EXTRA_REMINDER_JOB_ID, 0)
        if (id > 0) startService(AlarmSoundService.stopIntent(this, id))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ringingIntent = intent
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val preferences by (application as TiandiliApplication).userPreferences.collectAsStateWithLifecycle()
            val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            TiandiliTheme(themeMode = preferences.themeMode) {
                BackHandler(onBack = ::stopAlarm)
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("天地历 · 闹钟", style = MaterialTheme.typography.titleLarge)
                    Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 64.sp)
                    Text(if (locked && !preferences.showScheduleTitleOnLockScreen) "闹钟正在响铃"
                        else ringingIntent?.getStringExtra("alarm_title") ?: "闹钟",
                        style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = ::stopAlarm, modifier = Modifier.padding(16.dp)) { Text("停止响铃") }
                    Text("最长响铃 5 分钟", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ringingIntent = intent
    }
}
