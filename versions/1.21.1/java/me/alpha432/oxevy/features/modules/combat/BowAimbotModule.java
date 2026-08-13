package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Comparator;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BowAimbotModule extends Module {

    public Setting<Priority> priority = mode("Priority", Priority.ANGLE_DIST);
    public Setting<Float> predictMovement = num("Predict Movement", 0.2f, 0f, 2f);
    public Setting<Boolean> players = bool("Players", true);
    public Setting<Boolean> mobs = bool("Mobs", false);
    public Setting<Boolean> invisibles = bool("Invisibles", false);
    public Setting<Boolean> teams = bool("Teams", false);
    public Setting<Float> range = num("Range", 64f, 10f, 256f);
    public Setting<Color> espColor = color("ESP Color", Color.RED);
    public Setting<Float> lineWidth = num("Line Width", 2f, 0.5f, 4f);

    private Entity target;
    private float velocity;

    public BowAimbotModule() {
        super("BowAimbot", "Automatically aims bows and crossbows at entities", Category.COMBAT);
    }

    @Subscribe
    public void onTick(me.alpha432.oxevy.event.impl.entity.player.TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        ItemStack stack = player.getInventory().getSelected();
        Item item = stack.getItem();

        if (!(item instanceof BowItem || item instanceof CrossbowItem)) {
            target = null;
            return;
        }

        if (item instanceof BowItem && !mc.options.keyUse.isDown() && !player.isUsingItem()) {
            target = null;
            return;
        }

        if (item instanceof CrossbowItem && !CrossbowItem.isCharged(stack)) {
            target = null;
            return;
        }

        if (target == null || !isValidTarget(target))
            target = findTarget();

        if (target == null) return;

        velocity = (72000 - player.getUseItemRemainingTicks()) / 20F;
        velocity = (velocity * velocity + velocity * 2) / 3;
        if (velocity > 1) velocity = 1;

        double d = player.getEyePosition().distanceTo(
                target.getBoundingBox().getCenter()) * predictMovement.getValue();
        double posX = target.getX() + (target.getX() - target.xo) * d - player.getX();
        double posY = target.getY() + (target.getY() - target.yo) * d
                + target.getBbHeight() * 0.5 - player.getY()
                - player.getEyeHeight(player.getPose());
        double posZ = target.getZ() + (target.getZ() - target.zo) * d - player.getZ();

        float neededYaw = (float) Math.toDegrees(Math.atan2(posZ, posX)) - 90;
        mc.player.setYRot(limitAngleChange(mc.player.getYRot(), neededYaw));

        double hDistance = Math.sqrt(posX * posX + posZ * posZ);
        double hDistanceSq = hDistance * hDistance;
        float g = 0.006F;
        float velocitySq = velocity * velocity;
        float velocityPow4 = velocitySq * velocitySq;
        float neededPitch = (float) -Math.toDegrees(Math.atan(
                (velocitySq - Math.sqrt(velocityPow4 - g * (g * hDistanceSq + 2 * posY * velocitySq)))
                        / (g * hDistance)));

        if (Float.isNaN(neededPitch))
            mc.player.setXRot(limitAngleChange(mc.player.getXRot(),
                    getPitchTo(target.getBoundingBox().getCenter())));
        else
            mc.player.setXRot(neededPitch);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (target == null) return;

        Color c = espColor.getValue();

        double extraSize = 0.05;
        AABB box = getLerpedBox(target, event.getDelta())
                .move(0, extraSize, 0).inflate(extraSize);

        Color quadColor = new Color(c.getRed(), c.getGreen(), c.getBlue(),
                (int) (0.5f * velocity * c.getAlpha()));
        Color lineColor = new Color(c.getRed(), c.getGreen(), c.getBlue(),
                (int) (0.25f * velocity * c.getAlpha()));

        RenderUtil.drawBoxFilled(event.getMatrix(), box, quadColor);
        RenderUtil.drawBox(event.getMatrix(), box, lineColor, lineWidth.getValue(), false);
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (target == null) return;

        String message;
        if (velocity < 1)
            message = "Charging: " + (int) (velocity * 100) + "%";
        else
            message = "Target Locked";

        Font font = mc.font;
        int msgWidth = font.width(message);
        int midX = event.getContext().guiWidth() / 2;
        int midY = event.getContext().guiHeight() / 2;

        int msgX1 = midX - msgWidth / 2;
        int msgX2 = msgX1 + msgWidth + 4;
        int msgY1 = midY + 10;
        int msgY2 = msgY1 + 12;

        event.getContext().fill(msgX1, msgY1, msgX2, msgY2, 0x80000000);
        event.getContext().drawString(font, message, msgX1 + 2, msgY1 + 2, 0xFFFFFFFF);
    }

    private Entity findTarget() {
        Stream<Entity> stream = StreamSupport.stream(
                mc.level.entitiesForRendering().spliterator(), true);
        stream = stream.filter(this::isValidTarget);
        return stream.min(priority.getValue().comparator).orElse(null);
    }

    private boolean isValidTarget(Entity e) {
        if (e == mc.player || e.isRemoved()) return false;
        if (!(e instanceof LivingEntity living) || living.getHealth() <= 0) return false;
        double dist = mc.player.distanceTo(e);
        if (dist > range.getValue()) return false;
        if (e.isInvisible() && !invisibles.getValue()) return false;
        if (!players.getValue() && e instanceof net.minecraft.world.entity.player.Player) return false;
        if (!mobs.getValue() && !(e instanceof net.minecraft.world.entity.player.Player)) return false;
        if (teams.getValue() && e instanceof net.minecraft.world.entity.player.Player p
                && Oxevy.friendManager != null && Oxevy.friendManager.isFriend(p)) return false;
        return true;
    }

    private float getPitchTo(Vec3 pos) {
        double diffX = pos.x - mc.player.getX();
        double diffY = pos.y - mc.player.getEyeY();
        double diffZ = pos.z - mc.player.getZ();
        double hDist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        return (float) -Math.toDegrees(Math.atan2(diffY, hDist));
    }

    private AABB getLerpedBox(Entity entity, float delta) {
        double x = entity.xo + (entity.getX() - entity.xo) * delta;
        double y = entity.yo + (entity.getY() - entity.yo) * delta;
        double z = entity.zo + (entity.getZ() - entity.zo) * delta;
        return new AABB(x, y, z, x + entity.getBbWidth(), y + entity.getBbHeight(), z + entity.getBbWidth());
    }

    private float limitAngleChange(float current, float intended) {
        float wrapped = Mth.wrapDegrees(current);
        float intendedWrapped = Mth.wrapDegrees(intended);
        float change = Mth.wrapDegrees(intendedWrapped - wrapped);
        return current + change;
    }

    public enum Priority {
        DISTANCE("Distance", e -> mc.player.distanceToSqr(e)),
        ANGLE("Angle", e -> {
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 look = mc.player.getLookAngle();
            Vec3 toTarget = center.subtract(mc.player.getEyePosition()).normalize();
            return (double) Math.acos(Mth.clamp(look.dot(toTarget), -1, 1));
        }),
        ANGLE_DIST("Angle+Dist", e -> {
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 look = mc.player.getLookAngle();
            Vec3 toTarget = center.subtract(mc.player.getEyePosition()).normalize();
            double angle = Math.acos(Mth.clamp(look.dot(toTarget), -1, 1));
            return angle * angle + mc.player.distanceToSqr(e) / 100;
        }),
        HEALTH("Health", e -> e instanceof LivingEntity living ? (double) living.getHealth() : Double.MAX_VALUE);

        private final String name;
        private final Comparator<Entity> comparator;

        Priority(String name, ToDoubleFunction<Entity> keyExtractor) {
            this.name = name;
            comparator = Comparator.comparingDouble(keyExtractor);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
