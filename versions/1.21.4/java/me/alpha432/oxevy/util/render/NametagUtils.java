package me.alpha432.oxevy.util.render;

import me.alpha432.oxevy.util.traits.Util;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

public class NametagUtils implements Util {
    private static final Vector4f vec4 = new Vector4f();
    private static final Vector3d camera = new Vector3d();
    private static final Vector3d cameraNegated = new Vector3d();
    private static final Matrix4f view = new Matrix4f();
    private static final Matrix4f projection = new Matrix4f();
    private static double windowScale;

    public static double scale;

    private NametagUtils() {
    }

    public static void onRender(Matrix4f proj, Matrix4f viewMatrix) {
        view.set(viewMatrix);
        projection.set(proj);

        var camPos = mc.gameRenderer.getMainCamera().getPosition();
        camera.set(camPos.x, camPos.y, camPos.z);
        cameraNegated.set(camera);
        cameraNegated.negate();

        windowScale = mc.getWindow().getGuiScale();
    }

    public static boolean to2D(Vector3d pos, double scale) {
        NametagUtils.scale = scale;

        vec4.set((float) (pos.x - camera.x), (float) (pos.y - camera.y), (float) (pos.z - camera.z), 1.0f);

        view.transform(vec4);
        projection.transform(vec4);

        boolean behind = vec4.w <= 0.0f;
        if (behind) return false;

        toScreen(vec4);
        double x = vec4.x * mc.getWindow().getWidth();
        double y = vec4.y * mc.getWindow().getHeight();

        if (Double.isInfinite(x) || Double.isInfinite(y)) return false;

        pos.set(x / windowScale, (mc.getWindow().getHeight() - y) / windowScale, vec4.z);
        return true;
    }

    public static void begin(Vector3d pos, GuiGraphics drawContext) {
        drawContext.pose().pushPose();
        drawContext.pose().translate((float) pos.x, (float) pos.y, 0.0F);
        drawContext.pose().scale((float) scale, (float) scale, 1.0F);
    }

    public static void end(GuiGraphics drawContext) {
        drawContext.pose().popPose();
    }

    private static void toScreen(Vector4f vec) {
        float newW = 1.0f / vec.w * 0.5f;
        vec.x = vec.x * newW + 0.5f;
        vec.y = vec.y * newW + 0.5f;
        vec.z = vec.z * newW + 0.5f;
        vec.w = newW;
    }
}
