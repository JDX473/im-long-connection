package com.im.netty.server.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * Pipeline initializer for each client WebSocket connection.
 *
 * <pre>
 * HttpServerCodec
 *   → HttpObjectAggregator(320KB)
 *     → NettyWebSocketRequestHandler        (extracts userId, registers connection)
 *       → WebSocketServerProtocolHandler     (WebSocket upgrade per RFC 6455)
 *         → IdleStateHandler(60s read, 0 write, 0 all)  (idle detection)
 *           → HeartbeatHandler              (sends Ping on idle, closes on max failures)
 *             → NettyWebSocketMessageHandler (application messages)
 * </pre>
 */
public class NettyWebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_CONTENT_LENGTH = 327680; // 64KB * 5
    private static final String WEBSOCKET_PATH = "/netty-service/ws";

    /** Send Ping probe if no data from client for 60 seconds. */
    private static final int READER_IDLE_SECONDS = 60;

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
                .addLast(new NettyWebSocketRequestHandler())
                .addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH))
                .addLast(new IdleStateHandler(READER_IDLE_SECONDS, 0, 0, TimeUnit.SECONDS))
                .addLast(new HeartbeatHandler())
                .addLast(new NettyWebSocketMessageHandler());
    }
}
