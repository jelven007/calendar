package com.tiandili.calendar.platform.weather

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidCurrentLocationProviderTest {

    @Test
    fun `permission denial returns no coordinates`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = AndroidCurrentLocationProvider(context)

        assertThat(provider.currentLocation()).isNull()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `newest available system location is returned`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager = application.getSystemService(LocationManager::class.java)
        shadowOf(manager).setLastKnownLocation(
            LocationManager.GPS_PROVIDER,
            location(
                provider = LocationManager.GPS_PROVIDER,
                latitude = 31.2,
                longitude = 121.5,
                time = 1_000,
            ),
        )
        shadowOf(manager).setLastKnownLocation(
            LocationManager.NETWORK_PROVIDER,
            location(
                provider = LocationManager.NETWORK_PROVIDER,
                latitude = 30.2,
                longitude = 120.1,
                time = 2_000,
            ),
        )
        val provider = AndroidCurrentLocationProvider(application)

        val point = provider.currentLocation()

        assertThat(point?.latitude).isWithin(0.001).of(30.2)
        assertThat(point?.longitude).isWithin(0.001).of(120.1)
    }

    private fun location(
        provider: String,
        latitude: Double,
        longitude: Double,
        time: Long,
    ) = Location(provider).apply {
        this.latitude = latitude
        this.longitude = longitude
        this.time = time
    }
}
