package com.tiandili.calendar.data.weather

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.local.TiandiliDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WeatherDaoTest {

    private lateinit var database: TiandiliDatabase
    private lateinit var dao: WeatherDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TiandiliDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.weatherDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `selecting a city clears the previous selection`() = runTest {
        dao.selectCity(sampleCity("first", "杭州"), selectedAtEpochMillis = 100)
        dao.selectCity(sampleCity("second", "上海"), selectedAtEpochMillis = 200)

        assertThat(dao.observeSelectedCity().first()?.id).isEqualTo("second")
        assertThat(dao.getSavedCities().filter(WeatherCityEntity::isSelected))
            .containsExactly(sampleCity("second", "上海").copy(
                isSelected = true,
                lastUsedAtEpochMillis = 200,
            ))
    }

    @Test
    fun `saved city history is capped at ten including the current city`() = runTest {
        repeat(11) { index ->
            dao.selectCity(
                sampleCity(id = "city-$index", name = "城市 $index"),
                selectedAtEpochMillis = index.toLong(),
            )
        }

        assertThat(dao.getSavedCities()).hasSize(10)
        assertThat(dao.getSavedCities().map(WeatherCityEntity::id))
            .doesNotContain("city-0")
        assertThat(dao.observeSelectedCity().first()?.id).isEqualTo("city-10")
    }

    @Test
    fun `forecast segments replace together without removing air quality`() = runTest {
        dao.selectCity(sampleCity("hangzhou", "杭州"), selectedAtEpochMillis = 100)
        dao.replaceSegments(
            listOf(
                sampleSegment(WeatherSegmentTypeEntity.CURRENT, """{"temperature":8}"""),
                sampleSegment(WeatherSegmentTypeEntity.HOURLY, """{"hours":[8,9]}"""),
                sampleSegment(WeatherSegmentTypeEntity.DAILY, """{"days":[10,8]}"""),
                sampleSegment(WeatherSegmentTypeEntity.AIR_QUALITY, """{"usAqi":42}"""),
            ),
        )

        dao.replaceSegments(
            listOf(
                sampleSegment(WeatherSegmentTypeEntity.CURRENT, """{"temperature":9}"""),
                sampleSegment(WeatherSegmentTypeEntity.HOURLY, """{"hours":[9,10]}"""),
                sampleSegment(WeatherSegmentTypeEntity.DAILY, """{"days":[11,9]}"""),
            ),
        )

        val segments = dao.observeSegments("hangzhou").first()
            .associateBy(WeatherCacheSegmentEntity::segmentType)
        assertThat(segments).hasSize(4)
        assertThat(segments.getValue(WeatherSegmentTypeEntity.CURRENT).payloadJson)
            .contains("\"temperature\":9")
        assertThat(segments).containsKey(WeatherSegmentTypeEntity.AIR_QUALITY)
    }

    private fun sampleCity(
        id: String,
        name: String,
    ) = WeatherCityEntity(
        id = id,
        name = name,
        admin1 = "浙江",
        country = "中国",
        latitude = 30.2,
        longitude = 120.1,
        zoneId = "Asia/Shanghai",
        isSelected = false,
        lastUsedAtEpochMillis = 0,
    )

    private fun sampleSegment(
        type: WeatherSegmentTypeEntity,
        payload: String,
    ) = WeatherCacheSegmentEntity(
        cityId = "hangzhou",
        segmentType = type,
        payloadJson = payload,
        fetchedAtEpochMillis = 1_000,
        expiresAtEpochMillis = 2_000,
        status = WeatherCacheStatusEntity.SUCCESS,
    )
}
