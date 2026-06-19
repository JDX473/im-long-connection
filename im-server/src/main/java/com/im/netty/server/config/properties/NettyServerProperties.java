package com.im.netty.server.config.properties;

import com.im.netty.server.common.PropertyLoader;

/**
 * Netty server configuration.
 */
public class NettyServerProperties {

    private static int port;

    private NettyServerProperties() {}

    public static void init() {
        port = PropertyLoader.safeGetProperty("netty.server.port", Integer.class);
    }

    public static int getPort() { return port; }
}
