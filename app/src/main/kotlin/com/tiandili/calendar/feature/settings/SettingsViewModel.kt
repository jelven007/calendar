package com.tiandili.calendar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiandili.calendar.data.backup.BackupSnapshot
import com.tiandili.calendar.data.backup.ImportMode
import com.tiandili.calendar.data.backup.LocalDataManager
import com.tiandili.calendar.data.backup.MAX_BACKUP_BYTES
import com.tiandili.calendar.data.settings.SettingsRepository
import com.tiandili.calendar.data.settings.ThemeMode
import com.tiandili.calendar.data.settings.UserPreferences
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val busy: Boolean = false,
    val preview: BackupSnapshot? = null,
    val message: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val dataManager: LocalDataManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val preferences = repository.preferences.stateIn(viewModelScope,
        SharingStarted.Eagerly, UserPreferences())
    private val state = MutableStateFlow(SettingsUiState())
    val uiState = state.asStateFlow()

    fun setWeekStart(day: DayOfWeek) = perform { repository.setFirstDayOfWeek(day) }
    fun setTheme(mode: ThemeMode) = perform { repository.setThemeMode(mode) }
    fun setDefaultReminder(minutes: Int?) = perform { repository.setDefaultReminderMinutes(minutes) }
    fun setLockScreenTitle(show: Boolean) = perform { repository.setShowScheduleTitleOnLockScreen(show) }
    fun setWidgetSchedule(show: Boolean) = perform { repository.setShowNextScheduleInWidget(show) }

    fun readImport(open: () -> InputStream?) = perform {
        state.update { it.copy(preview = null) }
        val json = requireNotNull(open()) { "无法打开备份文件" }.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BACKUP_BYTES) { "备份文件超过 5 MB" }
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }
        val preview = dataManager.preview(json)
        state.update { it.copy(preview = preview) }
    }

    fun cancelPreview() { state.update { it.copy(preview = null) } }

    fun confirmImport(mode: ImportMode) {
        val preview = state.value.preview ?: return
        perform {
            val result = dataManager.importBackup(preview, mode)
            state.update { it.copy(preview = null,
                message = "已导入 ${result.importedCount} 个日程，跳过 ${result.skippedCount} 个重复项") }
        }
    }

    fun export(open: () -> OutputStream?) = perform {
        val json = dataManager.exportJson()
        requireNotNull(open()) { "无法保存备份文件" }.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        state.update { it.copy(message = "备份已保存") }
    }

    fun clearWeatherCache() = perform {
        dataManager.clearWeatherCache()
        state.update { it.copy(message = "天气缓存已清除") }
    }

    fun clearAll() = perform {
        dataManager.clearAll()
        state.update { it.copy(preview = null, message = "本地数据已清除，旧提醒正在取消") }
    }

    private fun perform(block: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    dataManager.recoverPendingPreferences()
                    block()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update { it.copy(message = "操作未完成：${error.message ?: "请稍后重试"}") }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}
