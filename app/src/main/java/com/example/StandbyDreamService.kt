package com.example

import android.app.Application
import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.MyApplicationTheme

/**
 * Standby as an Android screen saver.
 *
 * The system starts this on its own when the screen saver conditions are met, which for
 * most people means docked or charging, and it draws over the lock screen without the
 * user unlocking. That is the whole reason for it: MainActivity has to be launched by
 * hand, which is not how a docked phone should behave.
 *
 * ## Why this class has three interfaces bolted on
 *
 * DreamService extends Service but owns a Window, which makes it an awkward host for
 * Compose. Compose is a recomposition loop, not a view renderer, and it needs three
 * things from the view tree that an Activity supplies for free and a Service supplies not
 * at all:
 *
 *  - [LifecycleOwner] decides when to recompose and, more importantly, **cancels
 *    coroutines**. Every LaunchedEffect in StandbyDisplay is scoped to this. Without it
 *    the idle timers and refresh-rate effects never stop.
 *  - [ViewModelStoreOwner] is where `viewModel()` looks. Ours is separate from
 *    MainActivity's, so this gets its own StandbyViewModel. That is fine: the settings
 *    and the upload server live in AppContainer, so two ViewModels share one of each.
 *  - [SavedStateRegistryOwner] backs rememberSaveable. A dream gets no saved-state Bundle
 *    from the system, so this is mostly ceremony, but Compose requires the owner to exist.
 *
 * The registries are driven by hand from the dream callbacks below, then hung on the
 * ComposeView so everything underneath can find them.
 */
class StandbyDreamService : DreamService(), LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        // No Bundle to restore from. The system does not hand a dream saved state the way
        // it does an Activity, but the controller still has to be restored before the
        // lifecycle moves past CREATED or reads throw.
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // Interactive, because iOS StandBy is a persistent ambient display rather than a
        // screen saver that dies on touch, and because the UI is a swipeable pager. The
        // usual exits still work: power button, unlock, and undocking.
        isInteractive = true
        isFullscreen = true
        isScreenBright = true

        val viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application as Application),
        )[StandbyViewModel::class.java]

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@StandbyDreamService)
            setViewTreeViewModelStoreOwner(this@StandbyDreamService)
            setViewTreeSavedStateRegistryOwner(this@StandbyDreamService)

            // Default strategy assumes Activity or Fragment semantics. Tie disposal to our
            // lifecycle instead, or the composition outlives the window and keeps the whole
            // tree alive.
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@StandbyDreamService)
            )

            setContent {
                MyApplicationTheme(darkTheme = true) {
                    // window is non-null from onAttachedToWindow onward. No long-press
                    // handlers: the dream has nowhere to show a plugin info dialog, and
                    // both PluginWebView and AppWidgetView no-op a null handler.
                    StandbyDisplay(window = window, viewModel = viewModel)
                }
            }
        }

        setContentView(composeView)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        // Ours alone. The settings and services in AppContainer outlive this by design.
        viewModelStore.clear()
        super.onDestroy()
    }
}
