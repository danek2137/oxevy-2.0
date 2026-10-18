package me.alpha432.oxevy.sound;

import net.minecraft.sounds.SoundEvent;

public enum SoundMode {
    SKEET(OxevySounds.SKEET),
    BOOLEAN(OxevySounds.BOOLEAN),
    ENABLE(OxevySounds.ENABLE),
    DISABLE(OxevySounds.DISABLE),
    KEYPRESS(OxevySounds.KEYPRESS),
    KEYRELEASE(OxevySounds.KEYRELEASE),
    SCROLL(OxevySounds.SCROLL),
    SWIPEIN(OxevySounds.SWIPEIN),
    SWIPEOUT(OxevySounds.SWIPEOUT),
    PMSOUND(OxevySounds.PMSOUND),
    ORTHODOX(OxevySounds.ORTHODOX),
    UWU(OxevySounds.UWU),
    MOAN1(OxevySounds.MOAN1),
    MOAN2(OxevySounds.MOAN2),
    MOAN3(OxevySounds.MOAN3),
    MOAN4(OxevySounds.MOAN4),
    RUST_HEADSHOT(OxevySounds.RUST_HEADSHOT),
    GUI_OPEN(OxevySounds.GUI_OPEN),
    GUI_CLOSE(OxevySounds.GUI_CLOSE),
    TURN_ON_CLICK(OxevySounds.TURN_ON_CLICK),
    TURN_OFF_CLICK(OxevySounds.TURN_OFF_CLICK);

    private final SoundEvent sound;

    SoundMode(SoundEvent sound) {
        this.sound = sound;
    }

    public SoundEvent getSound() {
        return sound;
    }
}
