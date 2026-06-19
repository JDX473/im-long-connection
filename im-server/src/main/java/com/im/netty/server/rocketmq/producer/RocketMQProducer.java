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

    private static final int DEFAULT_QUEUE_ID = 2;

    private final DefaultMQProducer producer;

    public RocketMQProducer() {
        this.producer = RocketMQResource.getProducer();
    }

    /**
     * Send a message with a tag.
     *
     * @param topic   RocketMQ topic
     * @param tag     message tag (used for consumer-side filtering)
     * @param message JSON message body
     */
    public void send(String topic, String tag, String message) {
        try {
            Message msg = new Message(topic, tag, message.getBytes(StandardCharsets.UTF_8));
            producer.send(msg, (MessageQueueSelector) (mqs, m, arg) -> {
                int queueId = (int) arg;
                return mqs.get(queueId % mqs.size());
            }, DEFAULT_QUEUE_ID);
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
