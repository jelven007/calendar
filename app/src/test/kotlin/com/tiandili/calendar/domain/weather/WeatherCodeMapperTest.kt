package com.tiandili.calendar.domain.weather

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeatherCodeMapperTest {

    @Test
    fun `clear weather uses different day and night symbols`() {
        assertThat(WeatherCodeMapper.conditionFor(code = 0, isDay = true))
            .isEqualTo(WeatherCondition("晴", WeatherSymbol.CLEAR_DAY))
        assertThat(WeatherCodeMapper.conditionFor(code = 0, isDay = false))
            .isEqualTo(WeatherCondition("晴", WeatherSymbol.CLEAR_NIGHT))
    }

    @Test
    fun `wmo precipitation and storm codes map to Chinese conditions`() {
        assertThat(WeatherCodeMapper.conditionFor(63, isDay = true).description)
            .isEqualTo("中雨")
        assertThat(WeatherCodeMapper.conditionFor(75, isDay = true).description)
            .isEqualTo("大雪")
        assertThat(WeatherCodeMapper.conditionFor(96, isDay = true))
            .isEqualTo(WeatherCondition("雷暴伴冰雹", WeatherSymbol.THUNDERSTORM))
    }

    @Test
    fun `unknown weather code remains visibly unknown`() {
        assertThat(WeatherCodeMapper.conditionFor(999, isDay = true))
            .isEqualTo(WeatherCondition("未知天气", WeatherSymbol.UNKNOWN))
    }
}
