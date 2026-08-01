package me.alpha432.oxevy.network;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.Feature;
import me.alpha432.oxevy.event.impl.network.PacketEvent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class OxevyNetworkManager extends Feature {
    public static final StreamCodec<RegistryFriendlyByteBuf, OxevyPayload> CODEC = StreamCodec.of(
        OxevyNetworkManager::encode,
        OxevyNetworkManager::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, OxevyPayload payload) {
        buf.writeUtf(payload.getVersion());
    }

    private static OxevyPayload decode(RegistryFriendlyByteBuf buf) {
        return new OxevyPayload(buf.readUtf());
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(OxevyPayload.TYPE, CODEC);
        PayloadTypeRegistry.playS2C().register(OxevyPayload.TYPE, CODEC);

        ClientPlayNetworking.registerGlobalReceiver(OxevyPayload.TYPE, (payload, context) -> {
            var sender = context.player();
            if (sender != null) {
                OxevyUserManager.addOxevyUser(sender.getUUID());
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            OxevyUserManager.onWorldJoin();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Oxevy.oxevyUsersManager.markOffline();
            OxevyUserManager.clear();
        });
    }
}
