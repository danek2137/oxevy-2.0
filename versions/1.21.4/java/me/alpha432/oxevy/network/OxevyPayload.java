package me.alpha432.oxevy.network;

import me.alpha432.oxevy.util.BuildConfig;
import me.alpha432.oxevy.util.traits.Util;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class OxevyPayload implements CustomPacketPayload, Util {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("oxevy", "handshake");
    public static final Type<OxevyPayload> TYPE = new Type<>(ID);

    private final String version;

    public OxevyPayload() {
        this.version = BuildConfig.VERSION;
    }

    public OxevyPayload(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public static void sendHandshake() {
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new OxevyPayload()));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
