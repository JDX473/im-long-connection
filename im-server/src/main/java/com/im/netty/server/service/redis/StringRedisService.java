package com.im.netty.server.service.redis;

/**
 * Redis String operations.
 */
public class StringRedisService extends AbstractRedisService {

    /**
     * SET a key with optional expiry.
     */
    public void set(String key, String value, long expireSeconds) {
        executeAndRelease(jedis -> {
            jedis.set(key, value);
            if (expireSeconds > 0) {
                jedis.expire(key, expireSeconds);
            }
        });
    }

    /**
     * GET a key.
     */
    public String get(String key) {
        return executeAndReleaseWithReturn(jedis -> jedis.get(key));
    }
}
