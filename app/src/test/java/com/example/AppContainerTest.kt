package com.example

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppContainerTest {

    private fun app() = ApplicationProvider.getApplicationContext<StandbyApplication>()

    /**
     * The point of the container. Two ViewModels, which is what MainActivity and the
     * DreamService will each create, have to end up looking at the same server, not one
     * each on different ports with different PINs.
     */
    @Test
    fun twoViewModelsShareOneSetOfServices() {
        val a = StandbyViewModel(app())
        val b = StandbyViewModel(app())

        assertSame(a.isServerRunning, b.isServerRunning)
        assertSame(a.serverPin, b.serverPin)
        assertSame(a.serverPort, b.serverPort)
        assertSame(a.serverIp, b.serverIp)
    }

    @Test
    fun twoViewModelsShareOneSettingsRepository() {
        val a = StandbyViewModel(app())
        val b = StandbyViewModel(app())
        assertSame(a.nightModeEnabled, b.nightModeEnabled)
    }

    /**
     * The reason any of this was worth doing. Building a screen's ViewModel used to start
     * an HTTP server from its constructor, so adding a DreamService would have meant
     * docking the phone opened a listening socket on the lock screen.
     */
    @Test
    fun constructingAViewModelDoesNotStartTheServer() {
        val vm = StandbyViewModel(app())

        assertFalse(vm.isServerRunning.value)
        assertEquals(0, vm.serverPort.value)
        assertEquals("", vm.serverPin.value)
    }
}
