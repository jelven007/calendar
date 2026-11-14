package com.tiandili.calendar

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.platform.reminder.EXTRA_SCHEDULE_ID
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityIntentNavigationTest {
    @get:Rule val composeRule = createEmptyComposeRule()
    private lateinit var controller: ActivityController<MainActivity>

    @Before fun launchActivity() { controller = Robolectric.buildActivity(MainActivity::class.java).setup() }
    @After fun destroyActivity() { controller.pause().stop().destroy() }

    @Test fun `warm activity opens notification schedule detail`() {
        val app = controller.get().application as TiandiliApplication
        val id = runBlocking {
            val today = LocalDate.now()
            app.scheduleRepository.save(Schedule(title = "通知中的日程",
                timing = ScheduleTiming.AllDay(today, today.plusDays(1))))
        }
        composeRule.runOnIdle {
            deliverIntent(Intent().putExtra(EXTRA_SCHEDULE_ID, id))
        }
        composeRule.onNodeWithText("日程详情").assertIsDisplayed()
        composeRule.onNodeWithText("通知中的日程").assertIsDisplayed()
    }

    @Test fun `missing notification schedule opens list with unavailable message`() {
        composeRule.runOnIdle {
            deliverIntent(Intent().putExtra(EXTRA_SCHEDULE_ID, 999_999L))
        }
        composeRule.onNodeWithText("日程已删除或不可用").assertIsDisplayed()
    }

    @Test fun `weather widget action navigates without recreating activity`() {
        composeRule.runOnIdle {
            deliverIntent(Intent()
                .putExtra("com.tiandili.calendar.extra.DESTINATION", "weather"))
        }
        composeRule.onNodeWithText("选择城市").assertIsDisplayed()
    }

    private fun deliverIntent(intent: Intent) {
        controller.newIntent(intent)
    }
}
