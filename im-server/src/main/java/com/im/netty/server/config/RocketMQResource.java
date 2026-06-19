package com.im.netty.server.config;

import com.im.netty.server.common.NettyInstanceUtils;
import com.im.netty.server.config.properties.RocketMQConfig;
import com.im.netty.server.service.IMMessageHandler;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocketMQ resources.
 *
 * <h3>Topic topology</h3>
 * <pre>
 * Producer:
 *   → client2server    (client → business, tag=im_chat)
 *
 * Consumer (tag-based push):
 *   server2client     (business → push to client, tag=node MD5)
 *   Single node: subscribes with "*", receives all.
 *   Multi node:  subscribes with own MD5, only receives messages for self.
 * </pre>
 */
public class RocketMQResource {

    private static final Logger log = LoggerFactory.getLogger(RocketMQResource.class);

    private static DefaultMQProducer producer;
    private static DefaultMQPushConsumer pushConsumer;

    public static final String TOPIC_UPSTREAM = "client2server";
    public static final String TOPIC_DOWNSTREAM = "server2client";

    private static final IMMessageHandler messageHandler = new IMMessageHandler();

    private RocketMQResource() {}

    // ── Producer: client message → business ───────────────────────

    public static void initProducer() throws MQClientException {
        producer = new DefaultMQProducer(RocketMQConfig.getProducerGroup());
        producer.setNamesrvAddr(RocketMQConfig.getNameServer());
        producer.setSendMsgTimeout(RocketMQConfig.getProducerSendMsgTimeout());
        producer.setRetryTimesWhenSendFailed(0);
        producer.setRetryAnotherBrokerWhenNotStoreOK(false);
        producer.setDefaultTopicQueueNums(8);
        producer.start();
        log.info("RocketMQ producer started: {}", RocketMQConfig.getProducerGroup());
    }

    public static DefaultMQProducer getProducer() {
        return producer;
    }

    // ── Consumer: push to client ──────────────────────────────────

    public static void initPushConsumer() throws MQClientException {
        // Subscribe to own MD5 tag — only messages directed to this node arrive
        String myTag = NettyInstanceUtils.getInstanceMd5();
        pushConsumer = new DefaultMQPushConsumer(RocketMQConfig.getConsumerGroupNode());
        pushConsumer.setNamesrvAddr(RocketMQConfig.getNameServer());
        pushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        pushConsumer.subscribe(TOPIC_DOWNSTREAM, myTag);
        pushConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    messageHandler.doWriteMessage2Receiver(msg);
                } catch (Exception e) {
                    log.error("Error pushing message: msgId={}", msg.getMsgId(), e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        pushConsumer.start();
        log.info("RocketMQ push consumer started, group={}, topic={}, tag={}",
                RocketMQConfig.getConsumerGroupNode(), TOPIC_DOWNSTREAM, myTag);
    }

    // ── Shutdown ──────────────────────────────────────────────────

    public static void shutdown() {
        if (producer != null) producer.shutdown();
        if (pushConsumer != null) pushConsumer.shutdown();
        log.info("RocketMQ resources shut down");
    }
}
