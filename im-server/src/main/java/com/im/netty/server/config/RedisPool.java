package com.im.netty.server.config;

import com.im.netty.server.config.properties.RedisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Jedis connection pool singleton.
 */
public class RedisPool {

    private static final Logger log = LoggerFactory.getLogger(RedisPool.class);

    private static JedisPool jedisPool;

    private RedisPool() {}

    public static void init() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(RedisConfig.getMaxActive());
        config.setMaxWaitMillis(RedisConfig.getMaxWait());
        config.setMinIdle(RedisConfig.getMinIdle());
        config.setTestOnBorrow(true);

        jedisPool = new JedisPool(config,
                RedisConfig.getHost(),
                RedisConfig.getPort(),
                RedisConfig.getTimeout(),
                null,
                RedisConfig.getDatabase());

        // Validate connection
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            log.info("Redis connected: {}:{}/{}",
                    RedisConfig.getHost(), RedisConfig.getPort(), RedisConfig.getDatabase());
        }
    }

    public static Jedis getResource() {
        if (jedisPool == null) {
            throw new IllegalStateException("RedisPool not initialized");
        }
        Jedis jedis = jedisPool.getResource();
        if (jedis == null) {
            throw new RuntimeException("Failed to acquire Jedis resource from pool");
        }
        return jedis;
    }

    public static void close() {
        if (jedisPool != null) {
            jedisPool.close();
            log.info("Redis pool closed");
        }
    }
}
