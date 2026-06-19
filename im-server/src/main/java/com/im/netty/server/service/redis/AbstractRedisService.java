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
        try (Jedis jedis = RedisPool.getResource()) {
            action.accept(jedis);
        }
    }

    protected <T> T executeAndReleaseWithReturn(Function<Jedis, T> action) {
        try (Jedis jedis = RedisPool.getResource()) {
            return action.apply(jedis);
        }
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
