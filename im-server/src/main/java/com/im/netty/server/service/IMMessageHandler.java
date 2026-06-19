package com.im.netty.server.service;

import com.im.netty.common.util.JsonUtil;
import com.im.netty.domain.dto.UgcServerMessageDTO;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Push handler — writes messages directly to WebSocket channels.
 * <p>
 * Consumer receives messages from {@code TOPIC_DOWNSTREAM}
 * (published by im-chat after business processing).
 * Single-node: tag "*", all messages arrive here.
 * Multi-node:  tag = this node's MD5, only messages for this node arrive.
 */
public class IMMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(IMMessageHandler.class);

    /**
     * Write a message to the receiver's WebSocket channel.
     */
    public void doWriteMessage2Receiver(MessageExt msg) {
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            UgcServerMessageDTO<?> message = JsonUtil.fromJson(body, UgcServerMessageDTO.class, Object.class);
            if (message == null) return;

            String receiverId = getEffectiveReceiverId(message);
            if (receiverId == null) return;

            Channel channel = UserChannelManager.getChannel(receiverId);
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(body));
                log.debug("Message pushed to user: {}", receiverId);
            } else {
                log.debug("User offline, message skipped: {}", receiverId);
            }
        } catch (Exception e) {
            log.error("Error pushing message: msgId={}", msg.getMsgId(), e);
        }
    }

    private String getEffectiveReceiverId(UgcServerMessageDTO<?> message) {
        if (message.getProxyReceiverId() != null && !message.getProxyReceiverId().isEmpty()) {
            return message.getProxyReceiverId();
        }
        return message.getReceiverId();
    }
}
