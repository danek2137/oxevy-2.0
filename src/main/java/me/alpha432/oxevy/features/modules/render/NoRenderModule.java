package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;

import java.awt.Color;

public class NoRenderModule extends Module {

    public Setting<Boolean> noPortalOverlay = bool("PortalOverlay", false);
    public Setting<Boolean> noSpyglassOverlay = bool("SpyglassOverlay", false);
    public Setting<Boolean> noNausea = bool("Nausea", false);
    public Setting<Boolean> noPumpkinOverlay = bool("PumpkinOverlay", false);
    public Setting<Boolean> noPowderedSnowOverlay = bool("PowderedSnowOverlay", false);
    public Setting<Boolean> noFireOverlay = bool("FireOverlay", false);
    public Setting<Boolean> noLiquidOverlay = bool("LiquidOverlay", false);
    public Setting<Boolean> noInWallOverlay = bool("InWallOverlay", false);
    public Setting<Boolean> noVignette = bool("Vignette", false);
    public Setting<Boolean> noGuiBackground = bool("GuiBackground (W.I.P)", false);
    public Setting<Boolean> noTotemAnimation = bool("TotemAnimation (W.I.P)", false);
    public Setting<Boolean> noEatParticles = bool("EatParticles (W.I.P)", false);
    public Setting<Boolean> noEnchantGlint = bool("EnchantGlint", false);

    public Setting<Boolean> noBossBar = bool("BossBar", false);
    public Setting<Boolean> noScoreboard = bool("Scoreboard", false);
    public Setting<Boolean> noCrosshair = bool("Crosshair", false);
    public Setting<Boolean> noTitle = bool("Title", false);
    public Setting<Boolean> noHeldItemName = bool("HeldItemName", false);
    public Setting<Boolean> noObfuscation = bool("Obfuscation (W.I.P)", false);
    public Setting<Boolean> noPotionIcons = bool("PotionIcons", false);
    public Setting<Boolean> noMessageSignature = bool("MessageSignature (W.I.P)", false);

    public Setting<Boolean> noWeather = bool("Weather", false);
    public Setting<Boolean> noWorldBorder = bool("WorldBorder", false);
    public Setting<Boolean> noBlindness = bool("Blindness (W.I.P)", false);
    public Setting<Boolean> noDarkness = bool("Darkness (W.I.P)", false);
    public Setting<Boolean> noFog = bool("Fog (W.I.P)", false);
    public Setting<Boolean> noEnchTableBook = bool("EnchTableBook", false);
    public Setting<Boolean> noSignText = bool("SignText", false);
    public Setting<Boolean> noBlockBreakParticles = bool("BlockBreakParticles (W.I.P)", false);
    public Setting<Boolean> noBlockBreakOverlay = bool("BlockBreakOverlay (W.I.P)", false);
    public Setting<Boolean> noBeaconBeams = bool("BeaconBeams", false);
    public Setting<Boolean> noFallingBlocks = bool("FallingBlocks", false);
    public Setting<Boolean> noCaveCulling = bool("CaveCulling (W.I.P)", false);
    public Setting<Boolean> noMapMarkers = bool("MapMarkers (W.I.P)", false);
    public Setting<Boolean> noMapContents = bool("MapContents (W.I.P)", false);
    public Setting<Boolean> noFireworkExplosions = bool("FireworkExplosions", false);
    public Setting<Boolean> noBarrierInvis = bool("BarrierInvis (W.I.P)", false);

    public Setting<Boolean> noArmor = bool("Armor", false);
    public Setting<Boolean> noInvisibility = bool("Invisibility (W.I.P)", false);
    public Setting<Boolean> noGlowing = bool("Glowing (W.I.P)", false);
    public Setting<Boolean> noMobInSpawner = bool("MobInSpawner", false);
    public Setting<Boolean> noDeadEntities = bool("DeadEntities (W.I.P)", false);
    public Setting<Boolean> noNametags = bool("Nametags (W.I.P)", false);

    public NoRenderModule() {
        super("NoRender", "Disables certain animations or overlays", Category.RENDER);
    }

    public boolean noPortalOverlay() { return isEnabled() && noPortalOverlay.getValue(); }
    public boolean noSpyglassOverlay() { return isEnabled() && noSpyglassOverlay.getValue(); }
    public boolean noNausea() { return isEnabled() && noNausea.getValue(); }
    public boolean noPumpkinOverlay() { return isEnabled() && noPumpkinOverlay.getValue(); }
    public boolean noPowderedSnowOverlay() { return isEnabled() && noPowderedSnowOverlay.getValue(); }
    public boolean noFireOverlay() { return isEnabled() && noFireOverlay.getValue(); }
    public boolean noLiquidOverlay() { return isEnabled() && noLiquidOverlay.getValue(); }
    public boolean noInWallOverlay() { return isEnabled() && noInWallOverlay.getValue(); }
    public boolean noVignette() { return isEnabled() && noVignette.getValue(); }
    public boolean noGuiBackground() { return isEnabled() && noGuiBackground.getValue(); }
    public boolean noTotemAnimation() { return isEnabled() && noTotemAnimation.getValue(); }
    public boolean noEatParticles() { return isEnabled() && noEatParticles.getValue(); }
    public boolean noEnchantGlint() { return isEnabled() && noEnchantGlint.getValue(); }

    public boolean noBossBar() { return isEnabled() && noBossBar.getValue(); }
    public boolean noScoreboard() { return isEnabled() && noScoreboard.getValue(); }
    public boolean noCrosshair() { return isEnabled() && noCrosshair.getValue(); }
    public boolean noTitle() { return isEnabled() && noTitle.getValue(); }
    public boolean noHeldItemName() { return isEnabled() && noHeldItemName.getValue(); }
    public boolean noObfuscation() { return isEnabled() && noObfuscation.getValue(); }
    public boolean noPotionIcons() { return isEnabled() && noPotionIcons.getValue(); }
    public boolean noMessageSignature() { return isEnabled() && noMessageSignature.getValue(); }

    public boolean noWeather() { return isEnabled() && noWeather.getValue(); }
    public boolean noWorldBorder() { return isEnabled() && noWorldBorder.getValue(); }
    public boolean noBlindness() { return isEnabled() && noBlindness.getValue(); }
    public boolean noDarkness() { return isEnabled() && noDarkness.getValue(); }
    public boolean noFog() { return isEnabled() && noFog.getValue(); }
    public boolean noEnchTableBook() { return isEnabled() && noEnchTableBook.getValue(); }
    public boolean noSignText() { return isEnabled() && noSignText.getValue(); }
    public boolean noBlockBreakParticles() { return isEnabled() && noBlockBreakParticles.getValue(); }
    public boolean noBlockBreakOverlay() { return isEnabled() && noBlockBreakOverlay.getValue(); }
    public boolean noBeaconBeams() { return isEnabled() && noBeaconBeams.getValue(); }
    public boolean noFallingBlocks() { return isEnabled() && noFallingBlocks.getValue(); }
    public boolean noCaveCulling() { return isEnabled() && noCaveCulling.getValue(); }
    public boolean noMapMarkers() { return isEnabled() && noMapMarkers.getValue(); }
    public boolean noMapContents() { return isEnabled() && noMapContents.getValue(); }
    public boolean noFireworkExplosions() { return isEnabled() && noFireworkExplosions.getValue(); }
    public boolean noBarrierInvis() { return isEnabled() && noBarrierInvis.getValue(); }

    public boolean noArmor() { return isEnabled() && noArmor.getValue(); }
    public boolean noInvisibility() { return isEnabled() && noInvisibility.getValue(); }
    public boolean noGlowing() { return isEnabled() && noGlowing.getValue(); }
    public boolean noMobInSpawner() { return isEnabled() && noMobInSpawner.getValue(); }
    public boolean noDeadEntities() { return isEnabled() && noDeadEntities.getValue(); }
    public boolean noNametags() { return isEnabled() && noNametags.getValue(); }
}
