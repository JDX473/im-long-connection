package com.im.netty.gateway;

import com.im.netty.gateway.common.PropertyLoader;
import com.im.netty.gateway.config.NettyGatewayServer;
import com.im.netty.gateway.config.properties.NacosConfig;
import com.im.netty.gateway.listener.NacosEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Netty WebSocket Gateway.
 * <p>
 * Startup sequence:
 * <ol>
 *   <li>Load configuration from classpath</li>
 *   <li>Initialize Nacos service discovery subscription</li>
 *   <li>Wait for initial instance list from Nacos</li>
 *   <li>Start the Netty server to accept client WebSocket connections</li>
 * </ol>
 */
public class NettyGatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(NettyGatewayApplication.class);

    public static void main(String[] args) {
        // Allow -Denv=dev|prod override
        String env = System.getProperty("env", "dev");
        System.setProperty("env", env);

        NettyGatewayServer gatewayServer = null;
        try {
            // 1. Load properties
            PropertyLoader.init();

            // 2. Initialize Nacos config
            NacosConfig.init();

            // 3. Start Nacos event listener (subscribes to backend instance changes)
            NacosEventListener nacosEventListener = new NacosEventListener();
            nacosEventListener.start();

            // 4. Give Nacos a moment to populate the initial instance list
            Thread.sleep(2000);

            // 5. Start Netty gateway server
            gatewayServer = new NettyGatewayServer();
            gatewayServer.start();

            log.info("========================================");
            log.info("  IM Netty Gateway started successfully");
            log.info("  Port: {}", PropertyLoader.safeGetProperty("netty.server.port", Integer.class));
            log.info("========================================");

            // Block main thread
            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("Gateway startup failed", e);
            if (gatewayServer != null) {
                gatewayServer.stop();
            }
            System.exit(1);
        }
    }
}
