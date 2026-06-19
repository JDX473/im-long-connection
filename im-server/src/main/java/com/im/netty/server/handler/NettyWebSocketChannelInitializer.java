package com.im.netty.server.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * Pipeline initializer for each client WebSocket connection.
 *
 * <pre>
 * HttpServerCodec
 *   → HttpObjectAggregator(320KB)
 *     → NettyWebSocketRequestHandler    (extracts userId, registers connection)
 *       → WebSocketServerProtocolHandler (performs WebSocket upgrade per RFC 6455)
 *         → NettyWebSocketMessageHandler (processes application messages + heartbeat)
 * </pre>
 */
public class NettyWebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_CONTENT_LENGTH = 327680; // 64KB * 5
    private static final String WEBSOCKET_PATH = "/netty-service/ws";

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
                .addLast(new NettyWebSocketRequestHandler())
                .addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH))
                .addLast(new NettyWebSocketMessageHandler());
    }
}
