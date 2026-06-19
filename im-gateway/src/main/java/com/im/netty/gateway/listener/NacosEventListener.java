package com.im.netty.gateway.listener;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.im.netty.common.constants.NettySocketConstants;
import com.im.netty.gateway.config.properties.NacosConfig;
import com.im.netty.gateway.router.RouterTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Properties;

/**
 * Subscribes to Nacos for backend netty-server instance changes.
 * Each NamingEvent triggers a full refresh of the RouterTable.
 */
public class NacosEventListener {

    private static final Logger log = LoggerFactory.getLogger(NacosEventListener.class);

    private static NamingService namingService;
    private static RouterTable routerTable;

    public NacosEventListener() {
        routerTable = new RouterTable();
    }

    public static void init() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", NacosConfig.getServerAddr());
        properties.setProperty("namespace", NacosConfig.getNamespace());
        namingService = NacosFactory.createNamingService(properties);
    }

    public void start() throws NacosException {
        init();
        namingService.subscribe(
                NettySocketConstants.SERVICE_NAME,
                NacosConfig.getGroup(),
                Collections.<String>emptyList(),
                new EventListener() {
                    @Override
                    public void onEvent(Event event) {
                        if (event instanceof NamingEvent) {
                            NamingEvent ne = (NamingEvent) event;
                            log.info("Nacos event: {} instances changed", ne.getInstances().size());
                            RouterTable.doUpdate(ne.getInstances());
                        }
                    }
                }
        );
        log.info("Subscribed to Nacos service: {}", NettySocketConstants.SERVICE_NAME);
    }
}
