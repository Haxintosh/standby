package com.example

import java.util.UUID

sealed class StandbyPage {
    abstract val pageId: String

    data class FullWidth(
        val item: StandbyItem,
        override val pageId: String = UUID.randomUUID().toString()
    ) : StandbyPage() {
        constructor(plugin: PluginModel, pageId: String = UUID.randomUUID().toString()) :
            this(StandbyItem.Plugin(plugin), pageId)

        val plugin: PluginModel? get() = (item as? StandbyItem.Plugin)?.plugin
    }

    data class HalfWidth(
        val leftItem: StandbyItem,
        val rightItem: StandbyItem,
        override val pageId: String = UUID.randomUUID().toString()
    ) : StandbyPage() {
        constructor(leftPlugin: PluginModel, rightPlugin: PluginModel, pageId: String = UUID.randomUUID().toString()) :
            this(StandbyItem.Plugin(leftPlugin), StandbyItem.Plugin(rightPlugin), pageId)

        val leftPlugin: PluginModel? get() = (leftItem as? StandbyItem.Plugin)?.plugin
        val rightPlugin: PluginModel? get() = (rightItem as? StandbyItem.Plugin)?.plugin
    }
}
