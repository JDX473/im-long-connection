package com.im.netty.server.common;

import com.im.netty.common.util.IPAddressUtil;
import com.im.netty.common.util.MD5Utils;

/**
 * Utility for identifying this Netty node instance in the cluster.
 * Instance ID format: "ip:port", MD5 used as RocketMQ consumer tag.
 */
public final class NettyInstanceUtils {

    private static String instanceId;
    private static String instanceMd5;

    private NettyInstanceUtils() {}

    public static String getInstanceId() {
        if (instanceId == null) {
            instanceId = IPAddressUtil.getLocalIPv4Address() + ":"
                    + PropertyLoader.safeGetProperty("netty.server.port", Integer.class);
        }
        return instanceId;
    }

    public static String getInstanceMd5() {
        if (instanceMd5 == null) {
            instanceMd5 = MD5Utils.md5(getInstanceId());
        }
        return instanceMd5;
    }
}
