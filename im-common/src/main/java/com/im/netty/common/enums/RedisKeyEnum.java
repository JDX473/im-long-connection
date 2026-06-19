package com.im.netty.common.enums;

import com.im.netty.common.constants.ImConstant;
import lombok.Getter;

/**
 * Redis key definitions used by the netty modules.
 * Key format: {@code IM_LC_<enum-name>[_<suffix>...]}
 */
@Getter
public enum RedisKeyEnum {

    /**
     * Netty channel instance registry.
     * Hash: userId → nodeInstanceMd5.
     * Used for distributed message routing — find which node holds a user's WebSocket connection.
     * No expiry (persistent until user disconnects and is cleaned up).
     */
    USER_NETTY_CHANNEL_INSTANCE_HASH_CACHE(-1L),

    /**
     * IM message history cache.
     * Hash structure, used in merchant customer-service chat scenarios.
     */
    MALL_IM_MESSAGE_HISTORY_CACHE(24 * 60 * 60L),

    /**
     * Chat session read status.
     * Hash: sessionId → readStatus, used for unread message tracking.
     */
    MALL_CHAT_SESSION_READ_STATUS_HASH(24 * 60 * 60L);

    /** Expire time in seconds. -1 means no expiry. */
    private final long expireTime;

    RedisKeyEnum(long expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * Build the full Redis key: prefix + enum name + optional suffixes.
     */
    public String getKey(String... suffix) {
        StringBuilder sb = new StringBuilder(ImConstant.REDIS_KEY_PREFIX).append(this.name());
        if (suffix != null) {
            for (String s : suffix) {
                if (s != null && !s.isEmpty()) {
                    sb.append("_").append(s);
                }
            }
        }
        return sb.toString();
    }
}
