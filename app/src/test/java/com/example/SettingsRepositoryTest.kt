package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsRepositoryTest {

    private fun newRepo() =
        SettingsRepository(ApplicationProvider.getApplicationContext<Context>())

    /**
     * Two instances, which is what MainActivity and the DreamService will each have, have
     * to agree after either one writes. The old design seeded flows in a constructor and
     * never refreshed them, so it fails this.
     */
    @Test
    fun aWriteFromOneInstanceReachesAnother() {
        val a = newRepo()
        val b = newRepo()

        assertFalse(a.nightModeEnabled.value)
        assertFalse(b.nightModeEnabled.value)

        a.setNightModeEnabled(true)

        assertTrue("writer should see its own write", a.nightModeEnabled.value)
        assertTrue("second reader must see it too", b.nightModeEnabled.value)
    }

    @Test
    fun writesReachAnotherInstanceForEveryType() {
        val a = newRepo()
        val b = newRepo()

        a.setProtectionRatio(7)                       // Int
        a.setNightBrightnessValue(0.42f)              // Float
        a.setWeatherLocation("1.5", "2.5", "Hobart")  // String, multi-key write
        a.setAppWidgetsEnabled(false)                 // Boolean

        assertEquals(7, b.protectionRatio.value)
        assertEquals(0.42f, b.nightBrightnessValue.value, 0.0001f)
        assertEquals("1.5", b.weatherLat.value)
        assertEquals("2.5", b.weatherLon.value)
        assertEquals("Hobart", b.weatherCity.value)
        assertFalse(b.appWidgetsEnabled.value)
    }

    /**
     * Holds the line on the default from PR #4. A server that starts itself puts a
     * listening socket on every launch, and that is an easy thing to put back by accident.
     */
    @Test
    fun uploadServerIsOffByDefault() {
        assertFalse(newRepo().serverEnabled.value)
    }

    @Test
    fun defaultsMatchTheOriginalViewModel() {
        val r = newRepo()
        assertTrue(r.burnInProtectionEnabled.value)
        assertFalse(r.delayAfterInteraction.value)
        assertEquals(1, r.protectionRatio.value)
        assertTrue(r.hideControlsOnIdle.value)
        assertFalse(r.lowRefreshRateEnabled.value)
        assertEquals(60, r.lowRefreshRateValue.value)
        assertFalse(r.nightModeEnabled.value)
        assertEquals(22, r.nightStartHour.value)
        assertEquals(0, r.nightStartMinute.value)
        assertEquals(7, r.nightEndHour.value)
        assertEquals(0, r.nightEndMinute.value)
        assertEquals(4, r.nightProtectionRatio.value)
        assertTrue(r.nightBrightnessEnabled.value)
        assertEquals(0.05f, r.nightBrightnessValue.value, 0.0001f)
        assertEquals("52.52", r.weatherLat.value)
        assertEquals("13.41", r.weatherLon.value)
        assertEquals("Berlin", r.weatherCity.value)
        assertFalse(r.weatherUseGps.value)
        assertEquals(0L, r.weatherLastUpdate.value)
        assertTrue(r.confirmImportEnabled.value)
        assertTrue(r.appWidgetsEnabled.value)
    }

    @Test
    fun nightTimesWriteBothHalvesTogether() {
        val a = newRepo()
        val b = newRepo()
        a.setNightStartTime(23, 30)
        a.setNightEndTime(6, 15)
        assertEquals(23, b.nightStartHour.value)
        assertEquals(30, b.nightStartMinute.value)
        assertEquals(6, b.nightEndHour.value)
        assertEquals(15, b.nightEndMinute.value)
    }
}
