package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class TrajectoriesModule extends Module {

    public Setting<Color> missColor = color("Miss Color", Color.GRAY);
    public Setting<Color> entityHitColor = color("Entity Hit Color", Color.RED);
    public Setting<Color> blockHitColor = color("Block Hit Color", Color.GREEN);

    public TrajectoriesModule() {
        super("Trajectories", "Shows the trajectory of throwable items", Category.RENDER);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        Trajectory trajectory = getTrajectory(event.getDelta());
        if (trajectory.path.isEmpty()) return;

        Color c = switch (trajectory.type) {
            case MISS -> missColor.getValue();
            case ENTITY -> entityHitColor.getValue();
            case BLOCK -> blockHitColor.getValue();
        };

        Color quadColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() / 2);

        AABB endBox = trajectory.getEndBox();
        RenderUtil.drawBoxFilled(event.getMatrix(), endBox, quadColor);
        RenderUtil.drawBox(event.getMatrix(), endBox, c, 1.5f, false);
        RenderUtil.drawCurvedLine(event.getMatrix(), trajectory.path, c.getRGB(), 2f, false);
    }

    private Trajectory getTrajectory(float delta) {
        LocalPlayer player = mc.player;
        if (player == null) return new Trajectory(new ArrayList<>(), HitResult.Type.MISS);

        List<Vec3> path = new ArrayList<>();
        HitResult.Type type = HitResult.Type.MISS;

        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getMainHandItem();
        if (!isThrowable(stack)) {
            hand = InteractionHand.OFF_HAND;
            stack = player.getOffhandItem();
            if (!isThrowable(stack))
                return new Trajectory(path, type);
        }

        Item item = stack.getItem();
        double throwPower = getThrowPower(item);
        double gravity = getProjectileGravity(item);
        ClipContext.Fluid fluidHandling = getFluidHandling(item);

        double yaw = Math.toRadians(player.getYRot());
        double pitch = Math.toRadians(player.getXRot());

        Vec3 arrowPos = getLerpedPos(player, delta)
                .add(getHandOffset(hand, yaw));

        Vec3 arrowMotion = getStartingMotion(yaw, pitch, throwPower);

        for (int i = 0; i < 1000; i++) {
            path.add(arrowPos);

            arrowPos = arrowPos.add(arrowMotion.scale(0.1));
            arrowMotion = arrowMotion.scale(0.999);
            arrowMotion = arrowMotion.add(0, -gravity * 0.1, 0);

            Vec3 lastPos = path.size() > 1 ? path.get(path.size() - 2)
                    : player.getEyePosition(delta);

            BlockHitResult bResult = mc.level.clip(new ClipContext(
                    lastPos, arrowPos,
                    ClipContext.Block.COLLIDER, fluidHandling, player));
            if (bResult.getType() != HitResult.Type.MISS) {
                type = HitResult.Type.BLOCK;
                path.set(path.size() - 1, bResult.getLocation());
                break;
            }

            AABB box = new AABB(lastPos, arrowPos);
            Predicate<Entity> predicate = e -> !e.isSpectator() && e.isPickable();
            EntityHitResult eResult = ProjectileUtil.getEntityHitResult(
                    player, lastPos, arrowPos, box, predicate, 64 * 64);
            if (eResult != null && eResult.getType() != HitResult.Type.MISS) {
                type = HitResult.Type.ENTITY;
                path.set(path.size() - 1, eResult.getLocation());
                break;
            }
        }

        return new Trajectory(path, type);
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ProjectileWeaponItem
                || item instanceof SnowballItem || item instanceof EggItem
                || item instanceof EnderpearlItem
                || item instanceof ThrowablePotionItem
                || item instanceof FishingRodItem || item instanceof TridentItem;
    }

    private double getThrowPower(Item item) {
        if (!(item instanceof ProjectileWeaponItem))
            return 1.5;

        float bowPower = (72000 - mc.player.getUseItemRemainingTicks()) / 20F;
        bowPower = bowPower * bowPower + bowPower * 2F;

        if (bowPower > 3 || bowPower <= 0.3F)
            bowPower = 3;

        return bowPower;
    }

    private double getProjectileGravity(Item item) {
        if (item instanceof ProjectileWeaponItem) return 0.05;
        if (item instanceof ThrowablePotionItem) return 0.4;
        if (item instanceof FishingRodItem) return 0.15;
        if (item instanceof TridentItem) return 0.015;
        return 0.03;
    }

    private ClipContext.Fluid getFluidHandling(Item item) {
        if (item instanceof FishingRodItem) return ClipContext.Fluid.ANY;
        return ClipContext.Fluid.NONE;
    }

    private Vec3 getLerpedPos(LocalPlayer player, float delta) {
        double x = player.xo + (player.getX() - player.xo) * delta;
        double y = player.yo + (player.getY() - player.yo) * delta;
        double z = player.zo + (player.getZ() - player.zo) * delta;
        return new Vec3(x, y, z);
    }

    private Vec3 getHandOffset(InteractionHand hand, double yaw) {
        HumanoidArm mainArm = mc.options.mainHand().get();
        boolean rightSide = mainArm == HumanoidArm.RIGHT && hand == InteractionHand.MAIN_HAND
                || mainArm == HumanoidArm.LEFT && hand == InteractionHand.OFF_HAND;
        double sideMultiplier = rightSide ? -1 : 1;
        double handOffsetX = Math.cos(yaw) * 0.16 * sideMultiplier;
        double handOffsetY = mc.player.getEyeHeight() - 0.1;
        double handOffsetZ = Math.sin(yaw) * 0.16 * sideMultiplier;
        return new Vec3(handOffsetX, handOffsetY, handOffsetZ);
    }

    private Vec3 getStartingMotion(double yaw, double pitch, double throwPower) {
        double cosOfPitch = Math.cos(pitch);
        double mx = -Math.sin(yaw) * cosOfPitch;
        double my = -Math.sin(pitch);
        double mz = Math.cos(yaw) * cosOfPitch;
        return new Vec3(mx, my, mz).normalize().scale(throwPower);
    }

    private record Trajectory(List<Vec3> path, HitResult.Type type) {
        public AABB getEndBox() {
            Vec3 end = path.get(path.size() - 1);
            return new AABB(end.subtract(0.5, 0.5, 0.5), end.add(0.5, 0.5, 0.5));
        }
    }
}
