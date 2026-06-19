package com.im.netty.gateway.config.properties;

import com.im.netty.gateway.common.PropertyLoader;

/**
 * Nacos connection configuration.
 */
public class NacosConfig {

    private static String serverAddr;
    private static String namespace;
    private static String group;

    private NacosConfig() {}

    public static void init() {
        serverAddr = PropertyLoader.safeGetProperty("nacos.config.server-addr", String.class);
        namespace = PropertyLoader.safeGetProperty("nacos.config.namespace", String.class);
        group = PropertyLoader.safeGetProperty("nacos.config.group", String.class, "DEFAULT_GROUP");
    }

    public static String getServerAddr() { return serverAddr; }
    public static String getNamespace() { return namespace; }
    public static String getGroup() { return group; }
}
