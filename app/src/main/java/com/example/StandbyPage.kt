package com.example

sealed class StandbyPage {
    abstract val pageId: String

    data class FullWidth(
        val plugin: PluginModel,
        override val pageId: String = java.util.UUID.randomUUID().toString()
    ) : StandbyPage()
    
    data class HalfWidth(
        val leftPlugin: PluginModel,
        val rightPlugin: PluginModel,
        override val pageId: String
    ) : StandbyPage()
}
