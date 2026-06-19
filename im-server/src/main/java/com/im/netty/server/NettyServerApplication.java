package com.im.netty.server;

import com.im.netty.server.common.NettyInstanceUtils;
import com.im.netty.server.common.PropertyLoader;
import com.im.netty.server.config.NettyWebSocketServer;
import com.im.netty.server.config.RedisPool;
import com.im.netty.server.config.RocketMQResource;
import com.im.netty.server.config.properties.*;
import com.im.netty.server.service.NettyNacosRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Netty WebSocket IM Server.
 *
 * <h3>Startup sequence</h3>
 * <ol>
 *   <li>Load configuration from classpath</li>
 *   <li>Initialize Nacos naming service client</li>
 *   <li>Parse server properties (port, etc.)</li>
 *   <li>Initialize Redis connection pool</li>
 *   <li>Start RocketMQ producer + 2 consumers</li>
 *   <li>Start Netty WebSocket server</li>
 *   <li>Register this node with Nacos</li>
 * </ol>
 */
public class NettyServerApplication {

    private static final Logger log = LoggerFactory.getLogger(NettyServerApplication.class);

    public static void main(String[] args) {
        // Allow -Denv=dev|prod override
        String env = System.getProperty("env", "dev");
        System.setProperty("env", env);

        try {
            initProperties();
            startNettyServer();
            registerToNacos();

            log.info("========================================");
            log.info("  IM Netty Server started successfully");
            log.info("  Port: {}", NettyServerProperties.getPort());
            log.info("  Instance: {}", NettyInstanceUtils.getInstanceId());
            log.info("  Instance MD5: {}", NettyInstanceUtils.getInstanceMd5());
            log.info("========================================");

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down...");
                RocketMQResource.shutdown();
                RedisPool.close();
                NettyNacosRegistry.deregisterInstance();
            }));

            // Block main thread
            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("Server startup failed", e);
            System.exit(1);
        }
    }

    private static void initProperties() throws Exception {
        PropertyLoader.init();
        NacosConfig.init();
        NettyNacosRegistry.init();
        NettyServerProperties.init();
        RedisConfig.init();
        RedisPool.init();
        RocketMQConfig.init();
        RocketMQResource.initProducer();
        RocketMQResource.initPushConsumer();
    }

    private static void startNettyServer() {
        NettyWebSocketServer server = new NettyWebSocketServer();
        server.start();
    }

    private static void registerToNacos() {
        try {
            NettyNacosRegistry.registerInstance();
        } catch (Exception e) {
            log.warn("Nacos registration failed. Server running standalone.", e);
        }
    }
}
