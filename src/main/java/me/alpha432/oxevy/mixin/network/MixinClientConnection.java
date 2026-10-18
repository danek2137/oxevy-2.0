package me.alpha432.oxevy.mixin.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.bootstrap.AbstractBootstrap;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.network.PacketEvent;
import me.alpha432.oxevy.manager.ProxyManager;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static me.alpha432.oxevy.util.traits.Util.EVENT_BUS;

@Mixin(Connection.class)
public class MixinClientConnection {

    private static final Logger OXEVY_LOG = LoggerFactory.getLogger("Oxevy/Proxy");

    @Shadow
    private Channel channel;
    @Shadow
    @Final
    private PacketFlow receiving;

    @Unique
    private static final ThreadLocal<InetSocketAddress> oxevy$targetAddress = new ThreadLocal<>();

    @Inject(method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;", at = @At("HEAD"))
    private static void oxevy$captureTarget(InetSocketAddress address, net.minecraft.server.network.EventLoopGroupHolder holder, Connection connection, CallbackInfoReturnable<?> cir) {
        oxevy$targetAddress.set(address);
    }

    @Redirect(
        method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
        at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;handler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/AbstractBootstrap;")
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static io.netty.bootstrap.AbstractBootstrap oxevy$wrapHandler(io.netty.bootstrap.Bootstrap bootstrap, io.netty.channel.ChannelHandler handler) {
        ProxyManager.ProxyInfo active = Oxevy.proxyManager.getActiveProxy();
        InetSocketAddress target = oxevy$targetAddress.get();
        oxevy$targetAddress.remove();

        if (active == null || target == null) {
            return bootstrap.handler(handler);
        }

        OXEVY_LOG.info("Routing connection through SOCKS proxy {}:{}", active.ip, active.port);

        ProxyManager.ProxyInfo proxy = active;
        InetSocketAddress targetAddr = target;
        io.netty.channel.ChannelHandler originalHandler = handler;

        return bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast("oxevy-socks", new SocksConnectHandler(proxy, targetAddr));
                ch.pipeline().addLast(originalHandler);
            }
        });
    }

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    public void channelRead0(ChannelHandlerContext chc, Packet<?> packet, CallbackInfo ci) {
        if (this.channel.isOpen() && packet != null) {
            try {
                if (EVENT_BUS.post(new PacketEvent.Receive(packet))) {
                    ci.cancel();
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void sendImmediately(Packet<?> packet, ChannelFutureListener callbacks, boolean flush, CallbackInfo ci) {
        if (this.receiving != PacketFlow.CLIENTBOUND) return;
        try {
            if (EVENT_BUS.post(new PacketEvent.Send(packet))) {
                ci.cancel();
            }
        } catch (Exception ignored) {
        }
    }
}
