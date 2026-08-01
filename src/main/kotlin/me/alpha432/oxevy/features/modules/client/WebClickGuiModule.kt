package me.alpha432.oxevy.features.modules.client

import me.alpha432.oxevy.Oxevy
import me.alpha432.oxevy.features.commands.Command
import me.alpha432.oxevy.features.modules.Module
import me.alpha432.oxevy.web.ClickGuiWebBridge

class WebClickGuiModule : Module("WebClickGui", "Opens the ClickGui in your browser", Module.Category.CLIENT) {

    private val autoOpen = bool("AutoOpenBrowser", true)

    override fun onEnable() {
        val url = ClickGuiWebBridge.launch(autoOpen.value)
        Command.sendMessage("Web ClickGui started at $url")
    }

    override fun onDisable() {
        ClickGuiWebBridge.stop()
    }
}
