package com.im.netty.server.service;

import com.im.netty.common.enums.RedisKeyEnum;
import com.im.netty.server.common.NettyInstanceUtils;
import com.im.netty.server.service.redis.HashRedisService;

/**
 * Redis-based distributed channel registry.
 * <p>
 * Maps userId → node instance MD5 so that any node in the cluster
 * can look up which node holds a user's WebSocket connection.
 */
public class ChannelRegistryService {

    private static final HashRedisService hashRedisService = new HashRedisService();

    /**
     * Redis Hash key: {@code IM_LC_USER_NETTY_CHANNEL_INSTANCE_HASH_CACHE}
     */
    private static String getRedisKey() {
        return RedisKeyEnum.USER_NETTY_CHANNEL_INSTANCE_HASH_CACHE.getKey();
    }

    /**
     * Register: userId → this node's MD5.
     */
    public void registry(String userId) {
        String instanceMd5 = NettyInstanceUtils.getInstanceMd5();
        // 30 minute TTL — prevents zombie entries if server crashes
        hashRedisService.set(getRedisKey(), userId, instanceMd5, 1800);
    }

    /**
     * Remove registration for a userId.
     */
    public void remove(String userId) {
        hashRedisService.del(getRedisKey(), userId);
    }

    /**
     * Look up which node instance holds a user's connection.
     *
     * @return node MD5, or null if not found
     */
    public String getInstance(String userId) {
        return hashRedisService.get(getRedisKey(), userId);
    }
}
