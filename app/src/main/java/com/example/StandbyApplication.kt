package com.example

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Created once per process, before any screen. Registered via android:name in the manifest.
 */
class StandbyApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.services.start()

        // ProcessLifecycleOwner is the whole app's visibility, not one screen's: STARTED
        // when any UI is showing, STOPPED when none is, with a debounce so a rotation does
        // not trip it. Exactly the signal weather polling should follow.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.services.onAppForegrounded()
                override fun onStop(owner: LifecycleOwner) = container.services.onAppBackgrounded()
            }
        )
    }
}
