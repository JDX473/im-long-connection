package com.im.netty.server.rocketmq.producer;

import com.im.netty.server.config.RocketMQResource;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ message sender.
 * Uses a fixed queue selector for ordered message delivery within a queue.
 */
public class RocketMQProducer {

    private static final Logger log = LoggerFactory.getLogger(RocketMQProducer.class);

    private final DefaultMQProducer producer;

    public RocketMQProducer() {
        this.producer = RocketMQResource.getProducer();
        if (this.producer == null) {
            throw new IllegalStateException("RocketMQ producer not initialized");
        }
    }

    /**
     * Send a message with a tag.
     */
    public void send(String topic, String tag, String message) {
        try {
            Message msg = new Message(topic, tag, message.getBytes(StandardCharsets.UTF_8));
            producer.send(msg);
        } catch (Exception e) {
            log.error("Failed to send RocketMQ message: topic={}, tag={}", topic, tag, e);
        }
    }

    /**
     * Send a message without a tag.
     */
    public void send(String topic, String message) {
        send(topic, "", message);
    }
}
