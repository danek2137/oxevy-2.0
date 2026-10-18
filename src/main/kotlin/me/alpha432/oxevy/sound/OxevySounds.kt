package me.alpha432.oxevy.sound

import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent

object OxevySounds {

    @JvmField val ENABLE = of("enable")
    @JvmField val DISABLE = of("disable")
    @JvmField val BOOLEAN = of("boolean")
    @JvmField val KEYPRESS = of("keypress")
    @JvmField val KEYRELEASE = of("keyrelease")
    @JvmField val SCROLL = of("scroll")
    @JvmField val SWIPEIN = of("swipein")
    @JvmField val SWIPEOUT = of("swipeout")
    @JvmField val PMSOUND = of("pmsound")
    @JvmField val SKEET = of("skeet")
    @JvmField val ORTHODOX = of("orthodox")
    @JvmField val UWU = of("uwu")
    @JvmField val MOAN1 = of("moan1")
    @JvmField val MOAN2 = of("moan2")
    @JvmField val MOAN3 = of("moan3")
    @JvmField val MOAN4 = of("moan4")
    @JvmField val RUST_HEADSHOT = of("rust_headshot")
    @JvmField val GUI_OPEN = of("gui_open")
    @JvmField val GUI_CLOSE = of("gui_close")
    @JvmField val TURN_ON_CLICK = of("turn_on_click")
    @JvmField val TURN_OFF_CLICK = of("turn_off_click")

    private fun of(name: String): SoundEvent {
        return SoundEvent.createVariableRangeEvent(Identifier.parse("oxevy:$name"))
    }

    @JvmStatic fun init() {
    }
}
