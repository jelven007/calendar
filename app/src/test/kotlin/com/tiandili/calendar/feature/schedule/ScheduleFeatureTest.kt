package com.tiandili.calendar.feature.schedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.schedule.ScheduleRepository
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleFeatureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun `list completion and alarm switches persist all original record fields`() {
        val repository = InMemoryScheduleRepository()
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.of(2026, 11, 18)
        repository.seedAll(listOf(
            Schedule(id = 1, title = "待办测试", timing = ScheduleTiming.AllDay(date, date.plusDays(1)),
                kind = com.tiandili.calendar.domain.schedule.ScheduleKind.TODO,
                checklist = listOf(com.tiandili.calendar.domain.schedule.ChecklistItem("子事项")), reminderMinutesBefore = 10),
            Schedule(id = 2, title = "闹钟测试", timing = ScheduleTiming.Timed(date.atTime(8, 0), date.atTime(8, 1), zone),
                kind = com.tiandili.calendar.domain.schedule.ScheduleKind.ALARM, reminderMinutesBefore = 0)))
        val list = ScheduleListViewModel(repository)
        val editor = ScheduleEditorViewModel(repository, Clock.fixed(Instant.parse("2026-11-18T01:00:00Z"), zone), zone)
        composeRule.setContent { TiandiliTheme { ScheduleFeature(list, editor) } }
        composeRule.onNodeWithTag("record_complete_1").performClick()
        composeRule.onNodeWithTag("record_enabled_2").performClick()
        composeRule.runOnIdle {
            val todo = list.uiState.value.schedules.first { it.id == 1L }
            val alarm = list.uiState.value.schedules.first { it.id == 2L }
            assertThat(todo.completed).isTrue()
            assertThat(todo.checklist.single().completed).isTrue()
            assertThat(todo.reminderMinutesBefore).isEqualTo(10)
            assertThat(alarm.enabled).isFalse()
            assertThat(alarm.reminderMinutesBefore).isEqualTo(0)
        }
    }

    @Test fun `todo category adds subtasks by keyboard and preserves them after editing`() {
        val repository = InMemoryScheduleRepository()
        val zone = ZoneId.of("Asia/Shanghai")
        val editor = ScheduleEditorViewModel(repository, Clock.fixed(Instant.parse("2026-11-18T01:00:00Z"), zone), zone)
        val list = ScheduleListViewModel(repository)
        composeRule.setContent { TiandiliTheme { ScheduleFeature(list, editor,
            category = com.tiandili.calendar.domain.schedule.ScheduleKind.SCHEDULE) } }
        composeRule.onNodeWithContentDescription("添加日程").performClick()
        composeRule.onNodeWithTag("category_TODO").performScrollTo().performClick()
        composeRule.onNodeWithText("标题").performTextInput("学习")
        composeRule.onNodeWithTag("checklist_input").performScrollTo().performTextInput("读第一章")
        composeRule.onNodeWithTag("checklist_input").performImeAction()
        composeRule.onNodeWithText("读第一章").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("保存待办").performClick()
        composeRule.onNodeWithText("学习").performClick()
        composeRule.onNodeWithText("读第一章").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertThat(editor.uiState.value.checklist.single().title).isEqualTo("读第一章") }
    }

    @Test
    fun `add flow validates title then saves and immediately shows the schedule`() {
        val repository = InMemoryScheduleRepository()
        val zoneId = ZoneId.of("Asia/Shanghai")
        val listViewModel = ScheduleListViewModel(repository)
        val editorViewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2025-02-02T01:00:00Z"), zoneId),
            zoneId = zoneId,
        )
        composeRule.setContent {
            TiandiliTheme {
                ScheduleFeature(
                    listViewModel = listViewModel,
                    editorViewModel = editorViewModel,
                )
            }
        }

        composeRule.onNodeWithText("暂无日程").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加日程").performClick()
        composeRule.onNodeWithText("新建日程").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("保存日程").performClick()
        composeRule.onNodeWithText("请输入日程标题").assertIsDisplayed()

        composeRule.onNodeWithText("标题").performTextInput("看牙")
        composeRule.onNodeWithContentDescription("保存日程").performClick()
        composeRule.onNodeWithText("看牙").assertIsDisplayed()
    }

    @Test
    fun `edit flow loads the schedule and updates its list row`() {
        val repository = InMemoryScheduleRepository()
        repository.seed(
            Schedule(
                id = 8,
                title = "旧标题",
                timing = ScheduleTiming.AllDay(
                    startDate = LocalDate.of(2025, 2, 2),
                    endDateExclusive = LocalDate.of(2025, 2, 3),
                ),
            ),
        )
        val zoneId = ZoneId.of("Asia/Shanghai")
        val listViewModel = ScheduleListViewModel(repository)
        val editorViewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2025-02-02T01:00:00Z"), zoneId),
            zoneId = zoneId,
        )
        composeRule.setContent {
            TiandiliTheme {
                ScheduleFeature(
                    listViewModel = listViewModel,
                    editorViewModel = editorViewModel,
                )
            }
        }

        composeRule.onNodeWithText("旧标题").performClick()
        composeRule.onNodeWithText("编辑日程").assertIsDisplayed()
        composeRule.onNodeWithText("旧标题").performTextReplacement("新标题")
        composeRule.onNodeWithContentDescription("保存日程").performClick()

        composeRule.onNodeWithText("新标题").assertIsDisplayed()
    }

    @Test
    fun `delete flow requires confirmation before removing the schedule`() {
        val repository = InMemoryScheduleRepository()
        repository.seed(
            Schedule(
                id = 9,
                title = "待删除日程",
                timing = ScheduleTiming.AllDay(
                    startDate = LocalDate.of(2025, 2, 2),
                    endDateExclusive = LocalDate.of(2025, 2, 3),
                ),
            ),
        )
        val zoneId = ZoneId.of("Asia/Shanghai")
        val listViewModel = ScheduleListViewModel(repository)
        val editorViewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2025-02-02T01:00:00Z"), zoneId),
            zoneId = zoneId,
        )
        composeRule.setContent {
            TiandiliTheme {
                ScheduleFeature(
                    listViewModel = listViewModel,
                    editorViewModel = editorViewModel,
                )
            }
        }

        composeRule.onNodeWithContentDescription("删除日程：待删除日程").performClick()
        composeRule.onNodeWithText("删除日程？").assertIsDisplayed()
        composeRule.onAllNodesWithText("待删除日程").assertCountEquals(2)
        composeRule.onNodeWithText("删除").performClick()

        composeRule.onNodeWithText("暂无日程").assertIsDisplayed()
    }

    @Test
    fun `saving a schedule with a reminder requests notification permission handling`() {
        val repository = InMemoryScheduleRepository()
        val zoneId = ZoneId.of("Asia/Shanghai")
        val listViewModel = ScheduleListViewModel(repository)
        val editorViewModel = ScheduleEditorViewModel(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2025-02-02T01:00:00Z"), zoneId),
            zoneId = zoneId,
        )
        var reminderSavedCount = 0
        composeRule.setContent {
            TiandiliTheme {
                ScheduleFeature(
                    listViewModel = listViewModel,
                    editorViewModel = editorViewModel,
                    onReminderSaved = { reminderSavedCount++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("添加日程").performClick()
        composeRule.runOnIdle {
            editorViewModel.onTitleChanged("项目回顾")
            editorViewModel.onReminderMinutesChanged(15)
        }
        composeRule.onNodeWithContentDescription("保存日程").performClick()

        composeRule.runOnIdle {
            assertThat(reminderSavedCount).isEqualTo(1)
        }
    }
}

private class InMemoryScheduleRepository : ScheduleRepository {
    private val schedules = MutableStateFlow<List<Schedule>>(emptyList())

    fun seed(schedule: Schedule) {
        schedules.value = listOf(schedule)
    }
    fun seedAll(records: List<Schedule>) { schedules.value = records }

    override suspend fun save(schedule: Schedule): Long {
        val scheduleId = schedule.id.takeIf { it != 0L } ?: 1L
        val stored = schedule.copy(id = scheduleId)
        schedules.value = schedules.value
            .filterNot { it.id == scheduleId } + stored
        return scheduleId
    }

    override suspend fun get(scheduleId: Long): Schedule? =
        schedules.value.firstOrNull { it.id == scheduleId }

    override suspend fun delete(scheduleId: Long) {
        schedules.value = schedules.value.filterNot { it.id == scheduleId }
    }

    override fun observeAll(): Flow<List<Schedule>> = schedules
}
