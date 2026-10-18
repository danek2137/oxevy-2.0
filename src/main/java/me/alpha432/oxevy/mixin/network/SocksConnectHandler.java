package me.alpha432.oxevy.mixin.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import me.alpha432.oxevy.manager.ProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class SocksConnectHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger("Oxevy/Proxy");

    private final ProxyManager.ProxyInfo proxy;
    private final InetSocketAddress target;
    private ChannelPromise handshakePromise;
    private int state = 0;

    public SocksConnectHandler(ProxyManager.ProxyInfo proxy, InetSocketAddress target) {
        this.proxy = proxy;
        this.target = target;
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        this.handshakePromise = promise;

        InetSocketAddress proxyAddr = new InetSocketAddress(proxy.ip, proxy.port);
        ctx.connect(proxyAddr, null, ctx.newPromise().addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                sendGreeting(ctx);
            } else {
                promise.setFailure(f.cause());
            }
        }));
    }

    private void sendGreeting(ChannelHandlerContext ctx) {
        ByteBuf buf = ctx.alloc().buffer(3);
        buf.writeByte(0x05);
        buf.writeByte(0x01);
        buf.writeByte(0x00);
        ctx.writeAndFlush(buf);
        state = 1;
    }

    private void sendConnectRequest(ChannelHandlerContext ctx) {
        InetAddress addr = target.getAddress();
        byte[] addrBytes = addr.getAddress();

        ByteBuf buf = ctx.alloc().buffer(10);
        buf.writeByte(0x05);
        buf.writeByte(0x01);
        buf.writeByte(0x00);
        buf.writeByte(0x01);
        buf.writeBytes(addrBytes);
        buf.writeShort(target.getPort());
        ctx.writeAndFlush(buf);
        state = 2;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf buf)) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            if (state == 1) {
                if (buf.readableBytes() < 2) return;
                buf.readByte();
                byte method = buf.readByte();
                if (method != 0x00) {
                    LOG.warn("SOCKS5 proxy rejected auth method: {}", method);
                    handshakePromise.setFailure(new RuntimeException("SOCKS5 auth rejected"));
                    ctx.close();
                    return;
                }
                sendConnectRequest(ctx);
            } else if (state == 2) {
                if (buf.readableBytes() < 4) return;
                buf.readByte();
                byte status = buf.readByte();
                buf.readByte();
                byte addrType = buf.readByte();

                if (status != 0x00) {
                    LOG.warn("SOCKS5 connect failed with status: {}", status);
                    handshakePromise.setFailure(new RuntimeException("SOCKS5 connect failed: " + status));
                    ctx.close();
                    return;
                }

                int addrLen = switch (addrType) {
                    case 0x01 -> 4;
                    case 0x03 -> buf.readByte() & 0xFF;
                    case 0x04 -> 16;
                    default -> 0;
                };
                if (addrLen > 0) buf.skipBytes(addrLen);
                buf.skipBytes(2);

                state = 3;
                ctx.pipeline().remove(this);
                handshakePromise.setSuccess();
                LOG.info("SOCKS5 tunnel established to {}:{}", target.getHostString(), target.getPort());
            } else {
                super.channelRead(ctx, msg);
            }
        } finally {
            buf.release();
        }
    }
}
