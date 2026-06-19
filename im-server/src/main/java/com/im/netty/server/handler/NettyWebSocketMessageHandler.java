package com.im.netty.server.handler;

import com.im.netty.common.constants.NettySocketConstants;
import com.im.netty.common.util.IDGenerateUtil;
import com.im.netty.common.util.JsonUtil;
import com.im.netty.domain.dto.NettyServerMessageDTO;
import com.im.netty.server.config.RocketMQResource;
import com.im.netty.server.rocketmq.producer.RocketMQProducer;
import com.im.netty.server.service.UserChannelManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terminal handler — processes post-upgrade WebSocket text frames.
 *
 * <h3>Message flow</h3>
 * <ol>
 *   <li>Deserialize JSON to {@link NettyServerMessageDTO}</li>
 *   <li>Enrich: assign msgId, msgTime, read=false</li>
 *   <li>Re-serialize and publish to RocketMQ for business processing</li>
 * </ol>
 *
 * <h3>Heartbeat</h3>
 * Client sends {@code "ping"} (plain text, not JSON), server responds {@code "ok"}.
 */
public class NettyWebSocketMessageHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(NettyWebSocketMessageHandler.class);

    private final RocketMQProducer rocketMQProducer = new RocketMQProducer();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();

        // Application-level heartbeat
        if ("ping".equals(text)) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame("ok"));
            return;
        }

        try {
            // Deserialize
            NettyServerMessageDTO<String> msg = JsonUtil.fromJson(text, NettyServerMessageDTO.class, String.class);
            if (msg == null) {
                log.warn("Failed to parse message: {}", text);
                return;
            }

            // Enrich
            msg.setMsgId(IDGenerateUtil.createId("IM"));
            msg.setMsgTime(System.currentTimeMillis());
            msg.setRead(false);

            // Publish to RocketMQ for business processing
            String json = JsonUtil.toJsonString(msg);
            rocketMQProducer.send(
                    RocketMQResource.TOPIC_CLIENT_MSG,
                    "im_chat",
                    json);

        } catch (Exception e) {
            log.error("Error processing message: {}", text, e);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Channel active: remote={}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // FIXED: Clean up connection state on disconnect
        String userId = getConnectionUserId(ctx);
        if (userId != null) {
            UserChannelManager.removeChannel(userId);
            UserChannelManager.removeInstance(userId);
            log.info("Channel inactive — cleaned up userId={}, remote={}",
                    userId, ctx.channel().remoteAddress());
        } else {
            log.info("Channel inactive: remote={}", ctx.channel().remoteAddress());
        }
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Handler error: remote={}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    private String getConnectionUserId(ChannelHandlerContext ctx) {
        try {
            return (String) ctx.channel().attr(AttributeKey.valueOf("userId")).get();
        } catch (Exception e) {
            return null;
        }
    }
}
