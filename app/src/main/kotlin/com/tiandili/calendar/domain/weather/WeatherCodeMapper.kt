package com.tiandili.calendar.domain.weather

object WeatherCodeMapper {

    fun conditionFor(code: Int, isDay: Boolean): WeatherCondition = when (code) {
        0 -> WeatherCondition(
            description = "晴",
            symbol = if (isDay) WeatherSymbol.CLEAR_DAY else WeatherSymbol.CLEAR_NIGHT,
        )
        1 -> WeatherCondition(
            description = "晴间多云",
            symbol = if (isDay) {
                WeatherSymbol.PARTLY_CLOUDY_DAY
            } else {
                WeatherSymbol.PARTLY_CLOUDY_NIGHT
            },
        )
        2 -> WeatherCondition("多云", WeatherSymbol.CLOUDY)
        3 -> WeatherCondition("阴", WeatherSymbol.CLOUDY)
        45, 48 -> WeatherCondition("雾", WeatherSymbol.FOG)
        51 -> WeatherCondition("小毛毛雨", WeatherSymbol.DRIZZLE)
        53 -> WeatherCondition("中毛毛雨", WeatherSymbol.DRIZZLE)
        55 -> WeatherCondition("大毛毛雨", WeatherSymbol.DRIZZLE)
        56, 57 -> WeatherCondition("冻毛毛雨", WeatherSymbol.DRIZZLE)
        61 -> WeatherCondition("小雨", WeatherSymbol.RAIN)
        63 -> WeatherCondition("中雨", WeatherSymbol.RAIN)
        65 -> WeatherCondition("大雨", WeatherSymbol.RAIN)
        66, 67 -> WeatherCondition("冻雨", WeatherSymbol.RAIN)
        71 -> WeatherCondition("小雪", WeatherSymbol.SNOW)
        73 -> WeatherCondition("中雪", WeatherSymbol.SNOW)
        75 -> WeatherCondition("大雪", WeatherSymbol.SNOW)
        77 -> WeatherCondition("米雪", WeatherSymbol.SNOW)
        80 -> WeatherCondition("小阵雨", WeatherSymbol.SHOWERS)
        81 -> WeatherCondition("中阵雨", WeatherSymbol.SHOWERS)
        82 -> WeatherCondition("大阵雨", WeatherSymbol.SHOWERS)
        85, 86 -> WeatherCondition("阵雪", WeatherSymbol.SNOW)
        95 -> WeatherCondition("雷阵雨", WeatherSymbol.THUNDERSTORM)
        96, 99 -> WeatherCondition("雷暴伴冰雹", WeatherSymbol.THUNDERSTORM)
        else -> WeatherCondition("未知天气", WeatherSymbol.UNKNOWN)
    }
}
