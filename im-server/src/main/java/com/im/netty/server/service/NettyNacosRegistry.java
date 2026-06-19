package com.im.netty.server.service;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.im.netty.common.constants.NettySocketConstants;
import com.im.netty.common.util.IPAddressUtil;
import com.im.netty.server.config.properties.NacosConfig;
import com.im.netty.server.config.properties.NettyServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Registers this Netty node with Nacos so the gateway can discover it.
 */
public class NettyNacosRegistry {

    private static final Logger log = LoggerFactory.getLogger(NettyNacosRegistry.class);

    private static NamingService namingService;

    private NettyNacosRegistry() {}

    public static void init() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", NacosConfig.getServerAddr());
        properties.setProperty("namespace", NacosConfig.getNamespace());
        String user = NacosConfig.getUsername();
        String pass = NacosConfig.getPassword();
        if (user != null && !user.isEmpty()) properties.setProperty("username", user);
        if (pass != null && !pass.isEmpty()) properties.setProperty("password", pass);
        namingService = NacosFactory.createNamingService(properties);
        log.info("Nacos naming service initialized: {}", NacosConfig.getServerAddr());
    }

    /**
     * Register this node as a service instance.
     */
    public static void registerInstance() throws NacosException {
        String ip = IPAddressUtil.getLocalIPv4Address();
        int port = NettyServerProperties.getPort();
        namingService.registerInstance(
                NettySocketConstants.SERVICE_NAME,
                NacosConfig.getGroup(),
                ip, port,
                NettySocketConstants.CLUSTER_NAME);
        log.info("Registered to Nacos: {}:{}@{}", ip, port, NettySocketConstants.SERVICE_NAME);
    }

    /**
     * Deregister on shutdown (FIXED: now actually called via shutdown hook).
     */
    public static void deregisterInstance() {
        try {
            String ip = IPAddressUtil.getLocalIPv4Address();
            int port = NettyServerProperties.getPort();
            namingService.deregisterInstance(
                    NettySocketConstants.SERVICE_NAME,
                    NacosConfig.getGroup(),
                    ip, port,
                    NettySocketConstants.CLUSTER_NAME);
            log.info("Deregistered from Nacos");
        } catch (NacosException e) {
            log.error("Failed to deregister from Nacos", e);
        }
    }
}
