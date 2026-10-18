package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.ColorUtil;
import me.alpha432.oxevy.util.InteractionUtil;
import me.alpha432.oxevy.util.MathUtil;
import me.alpha432.oxevy.util.inventory.InventoryUtil;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CrystalAuraModule extends Module {

    private final Setting<Double> range = num("Range", 5.0, 1.0, 8.0);
    private final Setting<Double> placeRange = num("PlaceRange", 4.5, 1.0, 6.0);
    private final Setting<Double> crystalBlastRange = num("CrystalRange", 6.0, 1.0, 12.0);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Boolean> smooth = bool("Smooth", true);
    private final Setting<Float> smoothSpeed = num("SmoothSpeed", 360.0f, 10.0f, 720.0f);
    private final Setting<Boolean> antiSelf = bool("AntiSelf", true);
    private final Setting<Double> selfDamage = num("SelfDmg", 8.0, 0.0, 20.0);
    private final Setting<Boolean> autoSwitch = bool("AutoSwitch", true);
    private final Setting<Integer> placeDelay = num("PlaceDelay", 0, 0, 10);
    private final Setting<Integer> breakDelay = num("BreakDelay", 0, 0, 10);
    private final Setting<Boolean> showRadius = bool("ShowRadius", true);
    private final Setting<Color> color = color("Color", 255, 50, 50, 180);
    private final Setting<Boolean> rainbow = bool("Rainbow", false);

    private int tickCounter = 0;
    private int lastPlaceTick = 0;
    private int lastBreakTick = 0;
    private Player target = null;
    private int previousSlot = -1;

    private float smoothYaw;
    private float smoothPitch;
    private boolean smoothingInit = false;
    private long lastRotationUpdateNs = 0;

    public CrystalAuraModule() {
        super("CrystalAura", "Automatically places obsidian and end crystals for crystal PvP", Category.COMBAT);
        smoothSpeed.setVisibility(v -> smooth.getValue());
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        lastPlaceTick = 0;
        lastBreakTick = 0;
        target = null;
        previousSlot = -1;
        smoothingInit = false;
        lastRotationUpdateNs = 0;
    }

    @Override
    public void onDisable() {
        target = null;
        restoreSlot();
        smoothingInit = false;
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        tickCounter++;

        target = findTarget();
        if (target == null) {
            smoothingInit = false;
            return;
        }

        if (rotate.getValue()) {
            rotateTo(target);
        }

        if (tickCounter - lastBreakTick >= breakDelay.getValue() + 1) {
            EndCrystal crystal = findBestCrystal();
            if (crystal != null) {
                if (attackCrystal(crystal)) {
                    lastBreakTick = tickCounter;
                    return;
                }
            }
        }

        if (tickCounter - lastPlaceTick >= placeDelay.getValue() + 1) {
            BlockPos obsPos = findObsidianPlacement();
            if (obsPos != null) {
                if (placeSetup(obsPos)) {
                    lastPlaceTick = tickCounter;
                }
            }
        }
    }

    private void rotateTo(Entity entity) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 targetPos = entity.getEyePosition();
        float[] angle = MathUtil.calcAngle(eye, targetPos);

        if (smooth.getValue()) {
            if (!smoothingInit) {
                smoothYaw = mc.player.getYRot();
                smoothPitch = mc.player.getXRot();
                lastRotationUpdateNs = System.nanoTime();
                smoothingInit = true;
            }

            long now = System.nanoTime();
            float elapsed = Math.min((now - lastRotationUpdateNs) / 1_000_000_000f, 0.05f);
            lastRotationUpdateNs = now;

            float rate = smoothSpeed.getValue() / 20f;
            float factor = 1f - (float) Math.exp(-rate * elapsed);

            float yawDelta = MathUtil.wrapDegrees(angle[0] - smoothYaw);
            float pitchDelta = angle[1] - smoothPitch;

            double hDist = Math.sqrt(Math.pow(targetPos.x - eye.x, 2) + Math.pow(targetPos.z - eye.z, 2));
            if (hDist < 0.05) {
                yawDelta = 0f;
            }

            smoothYaw = Mth.wrapDegrees(smoothYaw + yawDelta * factor);
            smoothPitch = Mth.clamp(smoothPitch + pitchDelta * factor, -90f, 90f);

            Oxevy.rotationManager.setPlayerRotations(smoothYaw, smoothPitch);
        } else {
            smoothingInit = false;
            Oxevy.rotationManager.setPlayerRotations(angle[0], angle[1]);
        }
    }

    private Player findTarget() {
        Player closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (me.alpha432.oxevy.util.entity.GhostPlayerEntity.isGhost(player)) continue;
            if (Oxevy.friendManager.isFriend(player)) continue;
            if (player.isRemoved() || !player.isAlive()) continue;

            double dist = mc.player.distanceTo(player);
            if (dist <= range.getValue() && dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }
        return closest;
    }

    private EndCrystal findBestCrystal() {
        EndCrystal best = null;
        double bestScore = -1;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (crystal.isRemoved() || !crystal.isAlive()) continue;

            double crystalDist = mc.player.distanceTo(crystal);
            if (crystalDist > crystalBlastRange.getValue()) continue;

            double targetDist = target.distanceTo(crystal);
            if (targetDist > 6.0) continue;

            if (antiSelf.getValue()) {
                double selfDist = mc.player.distanceTo(crystal);
                if (selfDist < 3.0) continue;
            }

            double score = (6.0 - targetDist) * 2.0 - crystalDist * 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        return best;
    }

    private BlockPos findObsidianPlacement() {
        BlockPos targetPos = target.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos pos = targetPos.offset(x, y, z);

                    Vec3 posCenter = Vec3.atCenterOf(pos);
                    double playerDist = mc.player.position().distanceTo(posCenter);
                    if (playerDist > placeRange.getValue()) continue;

                    if (!InteractionUtil.isPlaceable(pos, false)) continue;

                    BlockPos below = pos.below();
                    if (mc.level.getBlockState(below).getBlock() == Blocks.OBSIDIAN ||
                        mc.level.getBlockState(below).getBlock() == Blocks.CRYING_OBSIDIAN ||
                        mc.level.getBlockState(below).getBlock() == Blocks.BEDROCK) {
                        continue;
                    }

                    boolean canPlace = false;
                    for (Direction dir : Direction.values()) {
                        if (dir == Direction.UP) continue;
                        if (!mc.level.getBlockState(below.relative(dir)).canBeReplaced()) {
                            canPlace = true;
                            break;
                        }
                    }
                    if (!canPlace) continue;

                    candidates.add(pos);
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> {
            Vec3 posCenter = Vec3.atCenterOf(pos);
            double tDist = target.position().distanceTo(posCenter);
            double pDist = mc.player.position().distanceTo(posCenter);

            double crystalDmg = estimateCrystalDamage(target, pos);
            double sDmg = antiSelf.getValue() ? estimateCrystalDamage(mc.player, pos) : 0;

            if (antiSelf.getValue() && sDmg > selfDamage.getValue()) return Double.MAX_VALUE;
            if (crystalDmg <= 0) return Double.MAX_VALUE;

            return -crystalDmg + tDist * 0.3 + pDist * 0.5;
        }));

        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean placeSetup(BlockPos pos) {
        int obsSlot = findInHotbar(Items.OBSIDIAN);
        if (obsSlot == -1) return false;

        int crystalSlot = findInHotbar(Items.END_CRYSTAL);
        if (crystalSlot == -1) return false;

        if (autoSwitch.getValue()) {
            if (previousSlot == -1) previousSlot = InventoryUtil.selected();
            InventoryUtil.swap(obsSlot);
        } else if (InventoryUtil.selected() != obsSlot) {
            return false;
        }

        if (!InteractionUtil.place(pos, false, InteractionHand.MAIN_HAND)) {
            restoreSlot();
            return false;
        }

        if (autoSwitch.getValue()) {
            InventoryUtil.swap(crystalSlot);
        } else if (InventoryUtil.selected() != crystalSlot) {
            restoreSlot();
            return false;
        }

        InteractionUtil.useItem(pos, InteractionHand.MAIN_HAND);

        restoreSlot();
        return true;
    }

    private boolean attackCrystal(EndCrystal crystal) {
        int swordSlot = findBestWeapon();
        if (swordSlot == -1) return false;

        if (autoSwitch.getValue()) {
            if (previousSlot == -1) previousSlot = InventoryUtil.selected();
            InventoryUtil.swap(swordSlot);
        } else if (InventoryUtil.selected() != swordSlot) {
            return false;
        }

        mc.gameMode.attack(mc.player, crystal);
        mc.player.swing(InteractionHand.MAIN_HAND);

        restoreSlot();
        return true;
    }

    private void restoreSlot() {
        if (previousSlot >= 0 && previousSlot < 9) {
            InventoryUtil.swap(previousSlot);
        }
        previousSlot = -1;
    }

    private int findInHotbar(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    private int findBestWeapon() {
        int bestSlot = -1;
        int bestPriority = -1;

        net.minecraft.world.item.Item[] weaponPriority = {
            Items.NETHERITE_SWORD, Items.NETHERITE_AXE,
            Items.DIAMOND_SWORD, Items.DIAMOND_AXE,
            Items.IRON_SWORD, Items.IRON_AXE,
            Items.STONE_SWORD, Items.STONE_AXE,
            Items.GOLDEN_SWORD, Items.GOLDEN_AXE,
            Items.WOODEN_SWORD, Items.WOODEN_AXE
        };

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) continue;

            for (int p = 0; p < weaponPriority.length; p++) {
                if (mc.player.getInventory().getItem(i).getItem() == weaponPriority[p]) {
                    if (p > bestPriority) {
                        bestPriority = p;
                        bestSlot = i;
                    }
                    break;
                }
            }
        }

        return bestSlot;
    }

    private double estimateCrystalDamage(Player victim, BlockPos crystalPos) {
        Vec3 victimPos = victim.position().add(0, victim.getBbHeight() / 2.0, 0);
        Vec3 crystalCenter = Vec3.atCenterOf(crystalPos);
        double dist = victimPos.distanceTo(crystalCenter);
        double blastPower = 6.0;

        double impact = (blastPower * 2.0) * (1.0 - dist / (blastPower * 2.0));
        return Math.max(0, impact);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null || target == null) return;

        if (showRadius.getValue()) {
            Color c = color.getValue();
            int colorInt;
            if (rainbow.getValue()) {
                float hue = (float) ((System.currentTimeMillis() * 0.001) % 1.0);
                colorInt = Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0x00FFFFFF | ((c.getAlpha() & 0xFF) << 24);
            } else {
                colorInt = ColorUtil.toRGBA(c);
            }

            double x = Mth.lerp(event.getDelta(), target.xo, target.getX());
            double y = Mth.lerp(event.getDelta(), target.yo, target.getY());
            double z = Mth.lerp(event.getDelta(), target.zo, target.getZ());
            Vec3 pos = new Vec3(x, y + target.getBbHeight() / 2.0, z);
            RenderUtil.drawCircle(event.getMatrix(), pos, 6.0f, 32, c, 1.5f, true);
        }
    }
}
