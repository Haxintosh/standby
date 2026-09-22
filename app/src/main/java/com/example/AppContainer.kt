package com.example

import android.content.Context

/**
 * Builds the long-lived objects once, at process start.
 *
 * Manual dependency injection. Anything needing the settings or the services gets handed
 * them from here instead of building its own, which is what makes "one upload server" a
 * fact about the design rather than a side effect of there only being one screen.
 */
class AppContainer(context: Context) {

    val settings = SettingsRepository(context)

    val services = StandbyServices(context.applicationContext, settings)
}
