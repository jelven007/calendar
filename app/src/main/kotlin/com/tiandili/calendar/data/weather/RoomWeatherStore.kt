package com.tiandili.calendar.data.weather

import com.squareup.moshi.Moshi
import com.tiandili.calendar.domain.weather.AirQuality
import com.tiandili.calendar.domain.weather.CachedWeatherSegment
import com.tiandili.calendar.domain.weather.City
import com.tiandili.calendar.domain.weather.ForecastBundle
import com.tiandili.calendar.domain.weather.WeatherCachePolicy
import com.tiandili.calendar.domain.weather.WeatherSegmentType
import com.tiandili.calendar.domain.weather.WeatherSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomWeatherStore(
    private val dao: WeatherDao,
    moshi: Moshi,
) : WeatherStore {
    private val codec = WeatherCacheCodec(moshi)

    override fun observeWeather(): Flow<WeatherSnapshot?> =
        dao.observeSelectedCity().flatMapLatest { cityEntity ->
            val city = cityEntity?.toDomainOrNull()
            if (city == null) {
                flowOf(null)
            } else {
                dao.observeSegments(city.id).map { segments ->
                    buildSnapshot(city, segments)
                }
            }
        }

    override suspend fun selectedCity(): City? =
        dao.getSelectedCity()?.toDomainOrNull()

    override suspend fun currentSnapshot(cityId: String): WeatherSnapshot? {
        val city = dao.getSavedCities()
            .firstOrNull { it.id == cityId }
            ?.toDomainOrNull()
            ?: return null
        return buildSnapshot(city, dao.getSegments(cityId))
    }

    override suspend fun selectCity(city: City, selectedAt: Instant) {
        dao.selectCity(
            city = city.toEntity(),
            selectedAtEpochMillis = selectedAt.toEpochMilli(),
        )
    }

    override suspend fun savedCities(): List<City> =
        dao.getSavedCities().mapNotNull(WeatherCityEntity::toDomainOrNull)

    override suspend fun saveForecast(
        city: City,
        forecast: ForecastBundle,
        fetchedAt: Instant,
    ) {
        dao.replaceSegments(
            listOf(
                segmentEntity(
                    cityId = city.id,
                    type = WeatherSegmentType.CURRENT,
                    payload = codec.encodeCurrent(forecast.current),
                    fetchedAt = fetchedAt,
                ),
                segmentEntity(
                    cityId = city.id,
                    type = WeatherSegmentType.HOURLY,
                    payload = codec.encodeHourly(forecast.hourly),
                    fetchedAt = fetchedAt,
                ),
                segmentEntity(
                    cityId = city.id,
                    type = WeatherSegmentType.DAILY,
                    payload = codec.encodeDaily(forecast.daily),
                    fetchedAt = fetchedAt,
                ),
            ),
        )
    }

    override suspend fun saveAirQuality(
        city: City,
        airQuality: AirQuality,
        fetchedAt: Instant,
    ) {
        dao.replaceSegments(
            listOf(
                segmentEntity(
                    cityId = city.id,
                    type = WeatherSegmentType.AIR_QUALITY,
                    payload = codec.encodeAirQuality(airQuality),
                    fetchedAt = fetchedAt,
                ),
            ),
        )
    }

    private fun buildSnapshot(
        city: City,
        segments: List<WeatherCacheSegmentEntity>,
    ): WeatherSnapshot {
        val byType = segments.associateBy(WeatherCacheSegmentEntity::segmentType)
        return WeatherSnapshot(
            city = city,
            current = byType[WeatherSegmentTypeEntity.CURRENT]?.decode {
                codec.decodeCurrent(it, city.zoneId)
            },
            hourly = byType[WeatherSegmentTypeEntity.HOURLY]?.decode {
                codec.decodeHourly(it, city.zoneId)
            },
            daily = byType[WeatherSegmentTypeEntity.DAILY]?.decode {
                codec.decodeDaily(it, city.zoneId)
            },
            airQuality = byType[WeatherSegmentTypeEntity.AIR_QUALITY]?.decode {
                codec.decodeAirQuality(it, city.zoneId)
            },
        )
    }

    private fun segmentEntity(
        cityId: String,
        type: WeatherSegmentType,
        payload: String,
        fetchedAt: Instant,
    ) = WeatherCacheSegmentEntity(
        cityId = cityId,
        segmentType = type.toEntity(),
        payloadJson = payload,
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
        expiresAtEpochMillis = fetchedAt
            .plus(WeatherCachePolicy.freshnessDuration(type))
            .toEpochMilli(),
        status = WeatherCacheStatusEntity.SUCCESS,
    )

    private fun <T> WeatherCacheSegmentEntity.decode(
        decodePayload: (String) -> T,
    ): CachedWeatherSegment<T>? = runCatching {
        CachedWeatherSegment(
            value = decodePayload(payloadJson),
            fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis),
            expiresAt = Instant.ofEpochMilli(expiresAtEpochMillis),
        )
    }.getOrNull()
}

private fun City.toEntity() = WeatherCityEntity(
    id = id,
    name = name,
    admin1 = admin1,
    country = country,
    latitude = latitude,
    longitude = longitude,
    zoneId = zoneId.id,
    isSelected = false,
    lastUsedAtEpochMillis = 0,
)

private fun WeatherCityEntity.toDomainOrNull(): City? {
    val parsedZoneId = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return null
    return City(
        id = id,
        name = name,
        admin1 = admin1,
        country = country,
        latitude = latitude,
        longitude = longitude,
        zoneId = parsedZoneId,
    )
}

private fun WeatherSegmentType.toEntity(): WeatherSegmentTypeEntity = when (this) {
    WeatherSegmentType.CURRENT -> WeatherSegmentTypeEntity.CURRENT
    WeatherSegmentType.HOURLY -> WeatherSegmentTypeEntity.HOURLY
    WeatherSegmentType.DAILY -> WeatherSegmentTypeEntity.DAILY
    WeatherSegmentType.AIR_QUALITY -> WeatherSegmentTypeEntity.AIR_QUALITY
}
