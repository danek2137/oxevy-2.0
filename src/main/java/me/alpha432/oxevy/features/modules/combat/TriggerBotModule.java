package me.alpha432.oxevy.features.modules.combat;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public class TriggerBotModule extends Module {

    public Setting<Float> range = num("Range", 4.25f, 1f, 6f);
    public Setting<Integer> cps = num("CPS", 12, 1, 20);
    public Setting<Boolean> players = bool("Players", true);
    public Setting<Boolean> mobs = bool("Mobs", false);
    public Setting<Boolean> invisibles = bool("Invisibles", false);
    public Setting<Boolean> teams = bool("Teams", false);
    public Setting<Boolean> attackWhileBlocking = bool("Attack While Blocking", false);

    private int tickCounter;
    private int attackCooldown;

    public TriggerBotModule() {
        super("TriggerBot", "Automatically attacks entities when looking at them", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        attackCooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        if (!attackWhileBlocking.getValue() && mc.player.isUsingItem()) return;

        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) return;

        attackCooldown = Math.max(0, attackCooldown - 1);

        if (mc.hitResult == null || !(mc.hitResult instanceof EntityHitResult eResult)) return;

        Entity target = eResult.getEntity();
        if (!isValidTarget(target)) return;

        if (mc.player.distanceToSqr(target) > range.getValue() * range.getValue()) return;

        if (attackCooldown > 0) return;

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        attackCooldown = Math.max(1, 20 / cps.getValue());
    }

    private boolean isValidTarget(Entity e) {
        if (me.alpha432.oxevy.util.entity.GhostPlayerEntity.isGhost(e)) return false;
        if (!(e instanceof LivingEntity living) || living.getHealth() <= 0) return false;
        if (e.isRemoved()) return false;
        if (e.isInvisible() && !invisibles.getValue()) return false;
        if (!players.getValue() && e instanceof net.minecraft.world.entity.player.Player) return false;
        if (!mobs.getValue() && !(e instanceof net.minecraft.world.entity.player.Player)) return false;
        if (teams.getValue() && e instanceof net.minecraft.world.entity.player.Player p
                && Oxevy.friendManager != null && Oxevy.friendManager.isFriend(p)) return false;
        return true;
    }
}
