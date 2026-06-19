package com.im.netty.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side heartbeat handler.
 * <p>
 * Works with {@link io.netty.handler.timeout.IdleStateHandler} placed earlier in the pipeline:
 * <ul>
 *   <li><b>Reader idle:</b> no data from client for N seconds → send a WebSocket Ping frame.
 *        If the client is alive, it will auto-reply with a Pong (RFC 6455).</li>
 *   <li><b>Writer idle:</b> no data sent to client for N seconds → send a Ping to keep the
 *        connection warm (optional, mainly for intermediate proxies).</li>
 *   <li><b>All idle:</b> no data in either direction → the connection is considered dead,
 *        close it and let {@code channelInactive} clean up session state.</li>
 * </ul>
 * <p>
 * The client does NOT need to implement application-level ping/pong.
 * Browsers and standard WebSocket libraries handle Ping/Pong frames automatically per RFC 6455.
 */
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatHandler.class);

    /** Max consecutive read-idle events before forcing disconnect. */
    private static final int MAX_READ_IDLE_COUNT = 3;

    private int readIdleCount;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            if (event.state() == IdleState.READER_IDLE) {
                // No data received from client for the configured timeout.
                readIdleCount++;
                if (readIdleCount >= MAX_READ_IDLE_COUNT) {
                    // Client hasn't responded to multiple Pings — connection is dead.
                    log.warn("Client {} idle after {} read-idle events, closing connection",
                            ctx.channel().remoteAddress(), readIdleCount);
                    ctx.close();
                } else {
                    // Send a WebSocket Ping to probe the client.
                    // A healthy client responds with a Pong, which resets the idle timer.
                    log.debug("Reader idle ({}/{}), sending Ping to {}",
                            readIdleCount, MAX_READ_IDLE_COUNT, ctx.channel().remoteAddress());
                    ctx.writeAndFlush(new PingWebSocketFrame());
                }
            }

            if (event.state() == IdleState.WRITER_IDLE) {
                // Nothing sent to client for the configured timeout.
                // Send a Ping to keep intermediate proxies from dropping the connection.
                log.debug("Writer idle, sending keep-alive Ping to {}",
                        ctx.channel().remoteAddress());
                ctx.writeAndFlush(new PingWebSocketFrame());
            }

            if (event.state() == IdleState.ALL_IDLE) {
                // No data in either direction — connection is definitely dead.
                log.warn("Client {} all-idle, closing connection", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Any inbound data resets the read-idle counter.
        readIdleCount = 0;
        ctx.fireChannelRead(msg);
    }
}
