package me.alpha432.oxevy.features.modules.client;

import com.mojang.authlib.GameProfile;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class fakeplayer extends Module {

    public final Setting<String> name = str("Name", "FakePlayer");

    private RemotePlayer fakePlayer;

    public fakeplayer() {
        super("FakePlayer", "spawns a player with no ai", Category.CLIENT);
    }

    @Override
    public void onEnable() {
        Player player = mc.player;
        ClientLevel world = mc.level;
        if (player == null || world == null) return;

        String playerName = name.getValue();
        if (playerName == null || playerName.trim().isEmpty()) playerName = "FakePlayer";

        fakePlayer = new RemotePlayer(world, new GameProfile(UUID.nameUUIDFromBytes(playerName.getBytes()), playerName));
        fakePlayer.copyPosition(player);
        fakePlayer.setYRot(player.getYRot());
        fakePlayer.setXRot(player.getXRot());
        fakePlayer.setYHeadRot(player.getYHeadRot());

        fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem());
        fakePlayer.setItemSlot(EquipmentSlot.OFFHAND, player.getOffhandItem());
        fakePlayer.setItemSlot(EquipmentSlot.HEAD, player.getItemBySlot(EquipmentSlot.HEAD));
        fakePlayer.setItemSlot(EquipmentSlot.CHEST, player.getItemBySlot(EquipmentSlot.CHEST));
        fakePlayer.setItemSlot(EquipmentSlot.LEGS, player.getItemBySlot(EquipmentSlot.LEGS));
        fakePlayer.setItemSlot(EquipmentSlot.FEET, player.getItemBySlot(EquipmentSlot.FEET));

        world.addEntity(fakePlayer);
    }

    @Override
    public void onDisable() {
        if (fakePlayer != null) {
            mc.level.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            fakePlayer = null;
        }
    }

    @Override
    public void onTick() {
    }
}
