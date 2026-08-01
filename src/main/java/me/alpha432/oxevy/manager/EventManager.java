package me.alpha432.oxevy.manager;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.Stage;
import me.alpha432.oxevy.event.impl.entity.DeathEvent;
import me.alpha432.oxevy.event.impl.entity.player.TickEvent;
import me.alpha432.oxevy.event.impl.entity.player.UpdateWalkingPlayerEvent;
import me.alpha432.oxevy.event.impl.input.KeyInputEvent;
import me.alpha432.oxevy.event.impl.network.ChatEvent;
import me.alpha432.oxevy.event.impl.network.PacketEvent;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.Feature;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.player.Player;

public class EventManager extends Feature {
    public void init() {
        EVENT_BUS.register(this);
    }

    public void onUnload() {
        EVENT_BUS.unregister(this);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck())
            return;
        Oxevy.moduleManager.onTick();
        Oxevy.oxevyUsersManager.onTick();
        for (Player player : mc.level.players()) {
            if (player == null || player.getHealth() > 0.0F)
                continue;
            EVENT_BUS.post(new DeathEvent(player));
        }
    }

    @Subscribe
    public void onUpdateWalkingPlayer(UpdateWalkingPlayerEvent event) {
        if (nullCheck())
            return;
        if (event.getStage() == Stage.PRE) {
            Oxevy.speedManager.update();
            Oxevy.rotationManager.updateRotations();
            Oxevy.positionManager.updatePosition();
        }
        if (event.getStage() == Stage.POST) {
            Oxevy.rotationManager.restoreRotations();
            Oxevy.positionManager.restorePosition();
        }
    }

    @Subscribe
    public void onPacketReceive(PacketEvent.Receive event) {
        Oxevy.serverManager.onPacketReceived();
        if (event.getPacket() instanceof ClientboundSetTimePacket)
            Oxevy.serverManager.update();
        if (event.getPacket() instanceof ClientboundCustomPayloadPacket(CustomPacketPayload payload)
                && payload instanceof BrandPayload(String brand)) {
            Oxevy.serverManager.setServerBrand(brand);
        }
    }

    @Subscribe
    public void onWorldRender(Render3DEvent event) {
        Oxevy.moduleManager.onRender3D(event);
    }

    @Subscribe
    public void onRenderGameOverlayEvent(Render2DEvent event) {
        Oxevy.colorManager.update(event.getDelta());
        Oxevy.moduleManager.onRender2D(event);
    }

    @Subscribe
    public void onKeyInput(KeyInputEvent event) {
        Oxevy.moduleManager.onKeyPressed(event.getKey());
    }

    @Subscribe
    public void onChatSent(ChatEvent event) {
        String message = event.getMessage();
        if (!message.startsWith(Oxevy.commandManager.getCommandPrefix())) {
            return;
        }
        event.cancel();
        Oxevy.commandManager.onChatSent(message);
    }
}