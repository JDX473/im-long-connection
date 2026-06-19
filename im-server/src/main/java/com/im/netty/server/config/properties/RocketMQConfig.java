package com.im.netty.server.config.properties;

import com.im.netty.server.common.PropertyLoader;

/**
 * RocketMQ configuration.
 */
public class RocketMQConfig {

    private static String nameServer;
    private static String consumerGroupNode;
    private static String consumerGroup;
    private static String producerGroup;
    private static int producerSendMsgTimeout;
    private static int consumerThreadMin;
    private static int consumerThreadMax;

    private RocketMQConfig() {}

    public static void init() {
        nameServer = PropertyLoader.safeGetProperty("rocketmq.name-server", String.class);
        consumerGroupNode = PropertyLoader.safeGetProperty("rocketmq.consumer.group.node", String.class);
        consumerGroup = PropertyLoader.safeGetProperty("rocketmq.consumer.group", String.class);
        producerGroup = PropertyLoader.safeGetProperty("rocketmq.producer.group", String.class);
        producerSendMsgTimeout = PropertyLoader.safeGetProperty("rocketmq.producer.send-message-timeout", Integer.class, 3000);
        consumerThreadMin = PropertyLoader.safeGetProperty("rocketmq.consumer.thread.min", Integer.class, 20);
        consumerThreadMax = PropertyLoader.safeGetProperty("rocketmq.consumer.thread.max", Integer.class, 50);
    }

    public static String getNameServer() { return nameServer; }
    public static String getConsumerGroupNode() { return consumerGroupNode; }
    public static String getConsumerGroup() { return consumerGroup; }
    public static String getProducerGroup() { return producerGroup; }
    public static int getProducerSendMsgTimeout() { return producerSendMsgTimeout; }
    public static int getConsumerThreadMin() { return consumerThreadMin; }
    public static int getConsumerThreadMax() { return consumerThreadMax; }
}
