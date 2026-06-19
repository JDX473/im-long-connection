package com.im.netty.server.config.properties;

import com.im.netty.server.common.PropertyLoader;

/**
 * Nacos connection configuration.
 */
public class NacosConfig {

    private static String serverAddr;
    private static String namespace;
    private static String group;
    private static String username;
    private static String password;

    private NacosConfig() {}

    public static void init() {
        serverAddr = PropertyLoader.safeGetProperty("nacos.config.server-addr", String.class);
        namespace = PropertyLoader.safeGetProperty("nacos.config.namespace", String.class);
        group = PropertyLoader.safeGetProperty("nacos.config.group", String.class, "DEFAULT_GROUP");
        username = PropertyLoader.safeGetProperty("nacos.config.username", String.class, "");
        password = PropertyLoader.safeGetProperty("nacos.config.password", String.class, "");
    }

    public static String getServerAddr() { return serverAddr; }
    public static String getNamespace() { return namespace; }
    public static String getGroup() { return group; }
    public static String getUsername() { return username; }
    public static String getPassword() { return password; }
}
