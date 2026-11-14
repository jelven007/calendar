package com.tiandili.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivitySettingsTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `settings destination offers immediate week start selection`() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("周起始日").performClick()

        composeRule.onNodeWithText("周日").performClick()
        composeRule.waitUntil(5000) {
            (composeRule.activity.application as TiandiliApplication)
                .userPreferences.value.firstDayOfWeek == java.time.DayOfWeek.SUNDAY
        }

        composeRule.onNodeWithText("周日").assertIsDisplayed()
        composeRule.onNodeWithText("月历").performClick()
        composeRule.onNodeWithTag("weekday_0").assertTextEquals("日")
    }

    @Test
    fun `export requires an explicit privacy acknowledgement`() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithTag("settings_content").performScrollToNode(hasText("导出 JSON 备份"))
        composeRule.onNodeWithText("导出 JSON 备份").performClick()

        composeRule.onNodeWithText("备份文件包含个人日程，请保存到可信位置。").assertIsDisplayed()
        composeRule.onNodeWithText("选择保存位置").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
    }

    @Test
    fun `clearing all data requires a second confirmation`() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithTag("settings_content").performScrollToNode(hasText("清除全部本地数据"))
        composeRule.onNodeWithText("清除全部本地数据").performClick()

        composeRule.onNodeWithText("确认清除全部数据？").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
    }
}
