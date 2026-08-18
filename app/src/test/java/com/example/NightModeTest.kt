package com.example

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NightModeTest {

    @Test
    fun testIsNightTimeCrossMidnight() {
        val startHour = 22
        val startMinute = 0
        val endHour = 7
        val endMinute = 0

        // Night time hours (should be true)
        assertTrue(StandbyViewModel.isNightTime(22, 0, startHour, startMinute, endHour, endMinute))
        assertTrue(StandbyViewModel.isNightTime(23, 30, startHour, startMinute, endHour, endMinute))
        assertTrue(StandbyViewModel.isNightTime(0, 0, startHour, startMinute, endHour, endMinute))
        assertTrue(StandbyViewModel.isNightTime(3, 15, startHour, startMinute, endHour, endMinute))
        assertTrue(StandbyViewModel.isNightTime(6, 59, startHour, startMinute, endHour, endMinute))

        // Daytime hours (should be false)
        assertFalse(StandbyViewModel.isNightTime(7, 0, startHour, startMinute, endHour, endMinute))
        assertFalse(StandbyViewModel.isNightTime(12, 0, startHour, startMinute, endHour, endMinute))
        assertFalse(StandbyViewModel.isNightTime(18, 45, startHour, startMinute, endHour, endMinute))
        assertFalse(StandbyViewModel.isNightTime(21, 59, startHour, startMinute, endHour, endMinute))
    }

    @Test
    fun testIsNightTimeSameDay() {
        val startHour = 1
        val startMinute = 30
        val endHour = 5
        val endMinute = 30

        assertFalse(StandbyViewModel.isNightTime(1, 0, startHour, startMinute, endHour, endMinute))
        assertTrue(StandbyViewModel.isNightTime(1, 30, startHour, startMinute, endHour, endMinute))
        assertTrue(StandbyViewModel.isNightTime(3, 0, startHour, startMinute, endHour, endMinute))
        assertFalse(StandbyViewModel.isNightTime(5, 30, startHour, startMinute, endHour, endMinute))
        assertFalse(StandbyViewModel.isNightTime(6, 0, startHour, startMinute, endHour, endMinute))
    }

    @Test
    fun testViewModelNightModePreferences() {
        val viewModel = StandbyViewModel(ApplicationProvider.getApplicationContext())

        // Default state
        assertFalse(viewModel.nightModeEnabled.value)
        assertEquals(22, viewModel.nightStartHour.value)
        assertEquals(0, viewModel.nightStartMinute.value)
        assertEquals(7, viewModel.nightEndHour.value)
        assertEquals(0, viewModel.nightEndMinute.value)
        assertEquals(4, viewModel.nightProtectionRatio.value)
        assertTrue(viewModel.nightBrightnessEnabled.value)
        assertEquals(0.05f, viewModel.nightBrightnessValue.value, 0.001f)

        // Change preferences
        viewModel.setNightModeEnabled(true)
        assertTrue(viewModel.nightModeEnabled.value)

        viewModel.setNightStartTime(23, 15)
        assertEquals(23, viewModel.nightStartHour.value)
        assertEquals(15, viewModel.nightStartMinute.value)

        viewModel.setNightEndTime(8, 30)
        assertEquals(8, viewModel.nightEndHour.value)
        assertEquals(30, viewModel.nightEndMinute.value)

        viewModel.setNightProtectionRatio(5)
        assertEquals(5, viewModel.nightProtectionRatio.value)

        viewModel.setNightBrightnessEnabled(false)
        assertFalse(viewModel.nightBrightnessEnabled.value)

        viewModel.setNightBrightnessValue(0.10f)
        assertEquals(0.10f, viewModel.nightBrightnessValue.value, 0.001f)
    }

    @Test
    fun testNightModeActiveCalculationWithCalendar() {
        val viewModel = StandbyViewModel(ApplicationProvider.getApplicationContext())
        viewModel.setNightModeEnabled(true)
        viewModel.setNightStartTime(22, 0)
        viewModel.setNightEndTime(7, 0)

        // Mock night time calendar
        val nightCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
        }
        viewModel.updateNightModeActiveState(nightCal)
        assertTrue(viewModel.isNightModeActive.value)

        // Mock day time calendar
        val dayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        viewModel.updateNightModeActiveState(dayCal)
        assertFalse(viewModel.isNightModeActive.value)

        // Disable night mode
        viewModel.setNightModeEnabled(false)
        viewModel.updateNightModeActiveState(nightCal)
        assertFalse(viewModel.isNightModeActive.value)
    }
}
