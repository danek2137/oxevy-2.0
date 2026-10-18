package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;

public class TimeChangerModule extends Module {

    public enum TimeMode {
        DAY, NOON, SUNSET, NIGHT, MIDNIGHT, CUSTOM
    }

    public final Setting<TimeMode> mode = mode("Time", TimeMode.DAY);
    public final Setting<Integer> customTime = num("CustomTime", 12000, 0, 23999);

    private static final int DAY_TIME = 1000;
    private static final int NOON_TIME = 6000;
    private static final int SUNSET_TIME = 12500;
    private static final int NIGHT_TIME = 18000;
    private static final int MIDNIGHT_TIME = 0;

    public TimeChangerModule() {
        super("TimeChanger", "Changes the client-side world time", Category.RENDER);
        customTime.setVisibility(v -> mode.getValue() == TimeMode.CUSTOM);
    }

    @Override
    public void onTick() {
        if (nullCheck() || mc.level == null) return;

        long target = switch (mode.getValue()) {
            case DAY -> DAY_TIME;
            case NOON -> NOON_TIME;
            case SUNSET -> SUNSET_TIME;
            case NIGHT -> NIGHT_TIME;
            case MIDNIGHT -> MIDNIGHT_TIME;
            case CUSTOM -> customTime.getValue();
        };

        long current = mc.level.getDayTime() % 24000L;
        long distanceForward = Math.floorMod(target - current, 24000L);
        if (distanceForward > 100 && distanceForward < 23900) {
            long newDayTime = mc.level.getDayTime() + Math.min(distanceForward, 500);
            if (mc.level.getLevelData() instanceof net.minecraft.client.multiplayer.ClientLevel.ClientLevelData data) {
                data.setDayTime(newDayTime);
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue().name();
    }
}
