package com.im.netty.server.config;

import com.im.netty.common.util.JsonUtil;
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

import java.util.concurrent.*;

/**
 * Manages RocketMQ producers and consumers.
 *
 * <h3>Topic topology</h3>
 * <pre>
 * Producer:
 *   → netty_server_chat_msg    (client messages → business server)
 *   → TOPIC_NODE_IM_SEND_MSG   (inter-node directed delivery, tag = node MD5)
 *
 * Consumer 1 (business):
 *   webchat_ugc_messages       (processed messages from business server → netty nodes)
 *
 * Consumer 2 (inter-node):
 *   TOPIC_NODE_IM_SEND_MSG     (only messages tagged with THIS node's MD5)
 * </pre>
 */
public class RocketMQResource {

    private static final Logger log = LoggerFactory.getLogger(RocketMQResource.class);

    private static DefaultMQProducer producer;
    private static DefaultMQPushConsumer businessConsumer;
    private static DefaultMQPushConsumer nodeConsumer;

    // Topic names
    public static final String TOPIC_CLIENT_MSG = "netty_server_chat_msg";
    public static final String TOPIC_BUSINESS_MSG = "webchat_ugc_messages";
    public static final String TOPIC_NODE_IM_SEND_MSG = "TOPIC_NODE_IM_SEND_MSG";

    private static final IMMessageHandler messageHandler = new IMMessageHandler();

    private static final ExecutorService consumerExecutor = new ThreadPoolExecutor(
            RocketMQConfig.getConsumerThreadMin(),
            RocketMQConfig.getConsumerThreadMax(),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> new Thread(r, "rmq-consumer"));

    private RocketMQResource() {}

    // ── Producer ──────────────────────────────────────────────────

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

    // ── Consumer 1: Business → Netty ──────────────────────────────

    public static void initConsumer() throws MQClientException {
        businessConsumer = new DefaultMQPushConsumer(RocketMQConfig.getConsumerGroup());
        businessConsumer.setNamesrvAddr(RocketMQConfig.getNameServer());
        businessConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        businessConsumer.subscribe(TOPIC_BUSINESS_MSG, "*");
        businessConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    messageHandler.handleMessage(msg);
                } catch (Exception e) {
                    log.error("Error handling business message: msgId={}", msg.getMsgId(), e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        businessConsumer.start();
        log.info("RocketMQ business consumer started, group={}, topic={}",
                RocketMQConfig.getConsumerGroup(), TOPIC_BUSINESS_MSG);
    }

    // ── Consumer 2: Inter-node delivery (tag = my MD5) ────────────

    public static void initIMMessageSendConsumer() throws MQClientException {
        String myTag = NettyInstanceUtils.getInstanceMd5();
        nodeConsumer = new DefaultMQPushConsumer(RocketMQConfig.getConsumerGroupNode());
        nodeConsumer.setNamesrvAddr(RocketMQConfig.getNameServer());
        nodeConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        // Only receive messages tagged for THIS node
        nodeConsumer.subscribe(TOPIC_NODE_IM_SEND_MSG, myTag);
        nodeConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    messageHandler.doWriteMessage2Receiver(msg);
                } catch (Exception e) {
                    log.error("Error delivering message to receiver: msgId={}", msg.getMsgId(), e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        nodeConsumer.start();
        log.info("RocketMQ node consumer started, group={}, topic={}, tag={}",
                RocketMQConfig.getConsumerGroupNode(), TOPIC_NODE_IM_SEND_MSG, myTag);
    }

    // ── Shutdown ──────────────────────────────────────────────────

    public static void shutdown() {
        if (producer != null) producer.shutdown();
        if (businessConsumer != null) businessConsumer.shutdown();
        if (nodeConsumer != null) nodeConsumer.shutdown();
        consumerExecutor.shutdown();
        log.info("RocketMQ resources shut down");
    }
}
