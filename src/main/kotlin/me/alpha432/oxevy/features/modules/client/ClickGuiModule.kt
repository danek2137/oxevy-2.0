package me.alpha432.oxevy.features.modules.client

import me.alpha432.oxevy.Oxevy
import me.alpha432.oxevy.event.impl.ClientEvent
import me.alpha432.oxevy.event.system.Subscribe
import me.alpha432.oxevy.features.commands.Command
import me.alpha432.oxevy.features.gui.ClickGuiScreen
import me.alpha432.oxevy.features.gui.WebClickGuiScreen
import me.alpha432.oxevy.features.modules.Module
import me.alpha432.oxevy.features.settings.Bind
import me.alpha432.oxevy.features.settings.Setting
import me.alpha432.oxevy.util.traits.Util
import org.lwjgl.glfw.GLFW
import java.awt.Color

class ClickGuiModule : Module("ClickGui", "Opens the ClickGui", Module.Category.CLIENT) {

    @JvmField val prefix: Setting<String> = str("Prefix", ".")
    @JvmField val color: Setting<Color> = color("Color", Color(0, 255, 0, 255))
    @JvmField val colorPreset: Setting<ColorPreset> = mode("ColorPreset", ColorPreset.CUSTOM)
    @JvmField val topColor: Setting<Color> = color("TopColor", Color(0, 150, 0, 255))
    @JvmField val rainbow: Setting<Boolean> = bool("Rainbow", false)
    @JvmField val rainbowHue: Setting<Int> = num("Delay", 240, 0, 600)
    @JvmField val rainbowBrightness: Setting<Float> = num("Brightness", 150.0f, 1.0f, 255.0f)
    @JvmField val rainbowSaturation: Setting<Float> = num("Saturation", 150.0f, 1.0f, 255.0f)
    @JvmField val nameTagOffset: Setting<Float> = num("NameTagOffset", 1.2f, 0.5f, 3.0f)
    @JvmField val healthBarOffset: Setting<Float> = num("HealthBarOffset", 1.0f, 0.5f, 3.0f)
    @JvmField val animationsEnabled: Setting<Boolean> = bool("AnimationsEnabled", true)
    @JvmField val animationSpeed: Setting<Float> = num("AnimationSpeed", 0.15f, 0.05f, 0.5f)
    @JvmField val slideInAnimation: Setting<Boolean> = bool("SlideInAnimation", true)
    @JvmField val scaleAnimation: Setting<Boolean> = bool("ScaleAnimation", true)
    @JvmField val style: Setting<GuiStyle> = mode("Style", GuiStyle.THUNDERHACK)
    @JvmField val theme: Setting<WebTheme> = mode("Theme", WebTheme.GLASS)
    @JvmField val blur: Setting<Int> = num("Blur", 5, 0, 10)
    @JvmField val engine: Setting<Engine> = mode("Mode", Engine.NATIVE)

    init {
        INSTANCE = this
        setBind(GLFW.GLFW_KEY_RIGHT_SHIFT)
        rainbowHue.setVisibility { rainbow.value }
        rainbowBrightness.setVisibility { rainbow.value }
        rainbowSaturation.setVisibility { rainbow.value }
        color.setVisibility { !rainbow.value }
        topColor.setVisibility { !rainbow.value }
        colorPreset.setVisibility { !rainbow.value }
        Util.EVENT_BUS.register(this)
    }

    @Subscribe
    fun onSettingChange(event: ClientEvent) {
        if (event.type == ClientEvent.Type.SETTING_UPDATE && event.setting.feature === this) {
            when (event.setting) {
                prefix -> {
                    Oxevy.commandManager.setCommandPrefix(prefix.plannedValue)
                    Command.sendMessage("Prefix set to {global} %s", Oxevy.commandManager.commandPrefix)
                }
                color -> {
                    Oxevy.colorManager.setColor(color.plannedValue)
                }
                colorPreset -> {
                    val preset = colorPreset.plannedValue
                    if (preset != ColorPreset.CUSTOM) {
                        this.color.setValue(preset.color)
                        Oxevy.colorManager.setColor(preset.color)
                    }
                }
                rainbow -> {
                    if (rainbow.value) {
                        color.setVisibility { false }
                        topColor.setVisibility { false }
                    } else {
                        color.setVisibility { true }
                        topColor.setVisibility { true }
                    }
                }
            }
        }
    }

    override fun onEnable() {
        if (nullCheck()) return
        when (engine.value) {
            Engine.NATIVE -> Util.mc.setScreen(ClickGuiScreen.getInstance())
            Engine.BROWSER -> WebClickGuiScreen.open()
        }
        disable()
    }

    override fun playEnableSound() {
        // GUI_OPEN is played by ClickGuiScreen.getInstance()
    }

    override fun playDisableSound() {
        // ClickGui disables itself right after opening, so no sound here
    }

    override fun onLoad() {
        Oxevy.colorManager.setColor(color.value)
        Oxevy.commandManager.setCommandPrefix(prefix.value)
    }

    companion object {
        private var INSTANCE: ClickGuiModule? = null

        @JvmStatic
        fun getInstance(): ClickGuiModule {
            if (INSTANCE == null) {
                INSTANCE = ClickGuiModule()
            }
            return INSTANCE!!
        }
    }

    enum class ColorPreset(private val presetColor: Color?) {
        CUSTOM(null),
        BLUE(Color(0, 120, 255, 180)),
        RED(Color(255, 60, 60, 180)),
        GREEN(Color(60, 255, 120, 180)),
        PURPLE(Color(180, 0, 255, 180)),
        CYAN(Color(0, 255, 220, 180)),
        ORANGE(Color(255, 140, 0, 180));

        val color: Color get() = presetColor ?: Color(0, 0, 255, 180)
    }

    enum class GuiStyle {
        THUNDERHACK,
        OXEVY,
        METEOR
    }

    enum class Engine {
        NATIVE,
        BROWSER
    }

    /** Web ClickGui theme: bundles a page layout with a colour palette (the
     *  palette itself lives in webclickgui/style.css, keyed by the lowercase
     *  enum name; layout is passed to the page as a data-layout attribute). */
    enum class WebTheme(val layout: String, val displayName: String, val swatch: String) {
        GLASS("top", "Glass", "#00FF66"),
        SIDEBAR("rail", "Sidebar", "#00FF66"),
        MIDNIGHT("top", "Midnight", "#4F8CFF"),
        METEOR("bottom", "Meteor", "#A46BFF"),
        NEON("rail", "Neon", "#FF3DF0"),
        FOCUS("list", "Focus", "#35D0FF"),
        FROST("top", "Frost", "#0A84FF")
    }
}
