package me.alpha432.oxevy.mixin.input;

import me.alpha432.oxevy.event.impl.input.MouseInputEvent;
import me.alpha432.oxevy.event.impl.input.MouseMoveEvent;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.alpha432.oxevy.util.traits.Util.EVENT_BUS;

@Mixin(MouseHandler.class)
public class MixinMouse {
    private static double lastMoveX = Double.NaN;
    private static double lastMoveY = Double.NaN;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (EVENT_BUS.post(new MouseInputEvent(input.button(), action))) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void onMouseMove(long window, double x, double y, CallbackInfo ci) {
        if (!Minecraft.getInstance().isWindowActive()) {
            lastMoveX = Double.NaN;
            lastMoveY = Double.NaN;
            return;
        }

        double dx = 0;
        double dy = 0;
        if (!Double.isNaN(lastMoveX)) {
            dx = x - lastMoveX;
            dy = y - lastMoveY;
            if (Math.abs(dx) > 1000.0 || Math.abs(dy) > 1000.0) {
                dx = 0;
                dy = 0;
            }
        }
        lastMoveX = x;
        lastMoveY = y;

        if (dx != 0.0 || dy != 0.0) {
            EVENT_BUS.post(new MouseMoveEvent(dx, dy, System.nanoTime()));
        }
    }
}

