package com.im.netty.common.constants;

/**
 * Constants shared between the Netty gateway and server modules.
 */
public final class NettySocketConstants {

    private NettySocketConstants() {}

    /** WebSocket endpoint path */
    public static final String CHAT_WEBSOCKET_PATH = "/netty-service/ws";

    /** Query parameter name for user ID in WebSocket upgrade request */
    public static final String QUERY_USER_ID = "userId";

    /** Query parameter name for business code in WebSocket upgrade request */
    public static final String QUERY_BIZ_CODE = "bizCode";

    /** Nacos service name for netty-server instances */
    public static final String SERVICE_NAME = "webchat-netty-service";

    /** Nacos cluster name */
    public static final String CLUSTER_NAME = "webchat-netty-cluster";
}
