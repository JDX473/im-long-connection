package com.im.netty.server.handler;

import com.im.netty.common.constants.NettySocketConstants;
import com.im.netty.server.service.UserChannelManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Intercepts the HTTP upgrade request BEFORE the WebSocket handshake.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Extract {@code userId} from query parameter</li>
 *   <li>Store userId as a channel attribute for downstream handlers</li>
 *   <li>Register user→channel mapping in {@link UserChannelManager}</li>
 *   <li>Register this node as the user's instance in Redis</li>
 *   <li>Rewrite the request URI to the WebSocket path</li>
 * </ul>
 */
public class NettyWebSocketRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(NettyWebSocketRequestHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        List<String> userIds = decoder.parameters().get(NettySocketConstants.QUERY_USER_ID);

        if (userIds == null || userIds.isEmpty()) {
            log.warn("WebSocket upgrade without userId — closing connection");
            ctx.close();
            return;
        }

        String userId = userIds.get(0);
        log.info("WebSocket connection: userId={}, remote={}", userId, ctx.channel().remoteAddress());

        // Store userId as a channel attribute
        ctx.channel().attr(AttributeKey.valueOf("userId")).set(userId);

        // Register local channel + Redis instance mapping
        UserChannelManager.addChannel(userId, ctx.channel());
        UserChannelManager.registryInstance(userId);

        // Rewrite URI to WebSocket path for the protocol handler
        request.setUri(NettySocketConstants.CHAT_WEBSOCKET_PATH);

        // Pass downstream
        ctx.fireChannelRead(request.retain());
    }
}
