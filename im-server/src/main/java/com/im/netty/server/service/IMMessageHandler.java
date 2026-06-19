package com.im.netty.server.service;

import com.im.netty.common.util.JsonUtil;
import com.im.netty.common.util.MD5Utils;
import com.im.netty.domain.dto.UgcServerMessageDTO;
import com.im.netty.server.config.RocketMQResource;
import com.im.netty.server.rocketmq.producer.RocketMQProducer;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Core distributed IM message routing handler.
 *
 * <h3>Two-stage RocketMQ routing</h3>
 * <pre>
 * Stage 1 — handleMessage():
 *   Business server publishes processed message to "webchat_ugc_messages".
 *   ALL netty nodes receive it.
 *   → Look up Redis: which node holds the receiver's connection?
 *   → Re-publish to "TOPIC_NODE_IM_SEND_MSG" with tag = target-node-MD5.
 *
 * Stage 2 — doWriteMessage2Receiver():
 *   Only the target node receives (subscribed to its own MD5 tag).
 *   → Look up local ConcurrentHashMap for the WebSocket channel.
 *   → writeAndFlush the message directly to the client.
 * </pre>
 *
 * <h3>Agent/Proxy mode</h3>
 * If {@code proxyReceiverId} or {@code proxySenderId} is set,
 * the message is routed via the proxy (customer-service agent) instead.
 */
public class IMMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(IMMessageHandler.class);

    private final RocketMQProducer producer = new RocketMQProducer();

    /**
     * Stage 1: Received a processed message from the business server.
     * Determine the target node and re-publish for directed delivery.
     */
    public void handleMessage(MessageExt msg) {
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            UgcServerMessageDTO<?> message = JsonUtil.fromJson(body, UgcServerMessageDTO.class, Object.class);
            if (message == null) {
                log.warn("Failed to parse message: msgId={}", msg.getMsgId());
                return;
            }

            // Determine the effective receiver (agent/proxy mode)
            String realReceiverId = getEffectiveReceiverId(message);
            if (realReceiverId == null) {
                log.warn("No receiver for message: msgId={}", msg.getMsgId());
                return;
            }

            // Check if target is on this node (local delivery)
            Channel localChannel = UserChannelManager.getChannel(realReceiverId);
            if (localChannel != null && localChannel.isActive()) {
                doLocalWrite(localChannel, body);
                return;
            }

            // Remote delivery: query Redis → re-publish to target node's tag
            String targetInstanceMd5 = UserChannelManager.getInstance(realReceiverId);
            if (targetInstanceMd5 == null) {
                log.warn("Target user not online: receiverId={}", realReceiverId);
                return;
            }

            // Don't re-route to self
            String myMd5 = MD5Utils.md5(
                    com.im.netty.server.common.NettyInstanceUtils.getInstanceId());
            if (targetInstanceMd5.equals(myMd5)) {
                log.debug("Target is on this node but channel not found (probably disconnected)");
                return;
            }

            // Publish to target node via tag-filtered topic
            String json = JsonUtil.toJsonString(message);
            producer.send(RocketMQResource.TOPIC_NODE_IM_SEND_MSG, targetInstanceMd5, json);
            log.debug("Routed message to node: tag={}", targetInstanceMd5);

        } catch (Exception e) {
            log.error("Error in handleMessage: msgId={}", msg.getMsgId(), e);
        }
    }

    /**
     * Stage 2: Final delivery — write to the WebSocket channel.
     * Only called on the node that holds the receiver's connection.
     */
    public void doWriteMessage2Receiver(MessageExt msg) {
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            UgcServerMessageDTO<?> message = JsonUtil.fromJson(body, UgcServerMessageDTO.class, Object.class);
            if (message == null) return;

            String realReceiverId = getEffectiveReceiverId(message);
            if (realReceiverId == null) return;

            Channel channel = UserChannelManager.getChannel(realReceiverId);
            if (channel != null && channel.isActive()) {
                doLocalWrite(channel, body);
            } else {
                log.warn("Receiver channel not active: userId={}", realReceiverId);
            }
        } catch (Exception e) {
            log.error("Error in doWriteMessage2Receiver: msgId={}", msg.getMsgId(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String getEffectiveReceiverId(UgcServerMessageDTO<?> message) {
        // Agent proxy: use proxyReceiverId if set
        if (message.getProxyReceiverId() != null && !message.getProxyReceiverId().isEmpty()) {
            return message.getProxyReceiverId();
        }
        return message.getReceiverId();
    }

    private void doLocalWrite(Channel channel, String json) {
        channel.writeAndFlush(new TextWebSocketFrame(json));
        log.debug("Message delivered to channel: remote={}", channel.remoteAddress());
    }
}
