package com.im.netty.gateway.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * Pipeline initializer for client-facing channels.
 * <p>
 * Pipeline (before WebSocket upgrade):
 * <pre>
 * HttpServerCodec → HttpObjectAggregator(320KB) → NettyGatewayRouterHandler
 * </pre>
 * After WebSocket upgrade, the HTTP codecs are replaced with WebSocket frame codecs
 * dynamically inside {@link NettyGatewayRouterHandler}.
 */
public class NettyGatewayChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_CONTENT_LENGTH = 327680; // 64KB * 5

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
                .addLast(new NettyGatewayRouterHandler());
    }
}
