package com.im.netty.server.service;

import com.im.netty.common.util.IPAddressUtil;
import com.im.netty.server.common.NettyInstanceUtils;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Local in-memory registry mapping userId → Netty Channel.
 * <p>
 * Also delegates to {@link ChannelRegistryService} for Redis-based
 * distributed instance tracking (which node holds a user's connection).
 */
public final class UserChannelManager {

    private static final Logger log = LoggerFactory.getLogger(UserChannelManager.class);

    /** Local channel registry: userId → Channel */
    public static final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();

    private static final ChannelRegistryService registryService = new ChannelRegistryService();

    private UserChannelManager() {}

    // ── Local channel operations ──────────────────────────────────

    public static void addChannel(String userId, Channel channel) {
        channels.put(userId, channel);
        log.debug("Channel registered: userId={}, total={}", userId, channels.size());
    }

    public static Channel getChannel(String userId) {
        return channels.get(userId);
    }

    /**
     * Remove a channel from the local registry (called on disconnect).
     */
    public static void removeChannel(String userId) {
        if (userId != null) {
            channels.remove(userId);
            log.debug("Channel removed: userId={}, remaining={}", userId, channels.size());
        }
    }

    // ── Redis instance registry (distributed) ─────────────────────

    /**
     * Register this node as the instance hosting the user's WebSocket connection.
     */
    public static void registryInstance(String userId) {
        registryService.registry(userId);
    }

    /**
     * Remove the user's instance registration from Redis (called on disconnect).
     */
    public static void removeInstance(String userId) {
        registryService.remove(userId);
    }

    /**
     * Query Redis: which node holds the user's connection?
     *
     * @return the target node's MD5 instance ID, or null if not found
     */
    public static String getInstance(String userId) {
        return registryService.getInstance(userId);
    }
}
