package com.im.netty.server.service.redis;

import com.im.netty.server.config.RedisPool;
import redis.clients.jedis.Jedis;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Template-method base class for Redis operations.
 * Manages Jedis resource lifecycle via try-with-resources.
 */
public abstract class AbstractRedisService {

    protected void executeAndRelease(Consumer<Jedis> action) {
        Jedis jedis = RedisPool.getResource();
        if (jedis == null) return;  // Redis down, skip
        try { action.accept(jedis); } finally { jedis.close(); }
    }

    protected <T> T executeAndReleaseWithReturn(Function<Jedis, T> action) {
        Jedis jedis = RedisPool.getResource();
        if (jedis == null) return null;  // Redis down
        try { return action.apply(jedis); } finally { jedis.close(); }
    }

    protected void del(String key) {
        executeAndRelease(jedis -> jedis.del(key));
    }

    protected void expire(String key, long seconds) {
        if (seconds > 0) {
            executeAndRelease(jedis -> jedis.expire(key, seconds));
        }
    }
}
