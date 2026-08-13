package me.alpha432.oxevy.sound

import net.minecraft.resources.ResourceLocation
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

    private fun of(name: String): SoundEvent {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.parse("oxevy:$name"))
    }

    @JvmStatic fun init() {
    }
}
