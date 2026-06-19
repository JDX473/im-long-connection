package com.im.netty.server.service.redis;

/**
 * Redis Hash operations.
 */
public class HashRedisService extends AbstractRedisService {

    /**
     * HSET a field in a hash with optional expiry.
     */
    public void set(String key, String field, String value, long expireSeconds) {
        executeAndRelease(jedis -> {
            jedis.hset(key, field, value);
            if (expireSeconds > 0) {
                jedis.expire(key, expireSeconds);
            }
        });
    }

    /**
     * HGET a field from a hash.
     */
    public String get(String key, String field) {
        return executeAndReleaseWithReturn(jedis -> jedis.hget(key, field));
    }

    /**
     * HDEL a field from a hash.
     */
    public void del(String key, String field) {
        executeAndRelease(jedis -> jedis.hdel(key, field));
    }
}
