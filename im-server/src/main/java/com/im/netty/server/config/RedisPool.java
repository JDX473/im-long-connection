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
        config.setMinIdle(0);  // Don't keep idle connections when Redis is unstable

        String user = RedisConfig.getUsername();
        String pass = RedisConfig.getPassword();
        jedisPool = new JedisPool(config,
                RedisConfig.getHost(),
                RedisConfig.getPort(),
                RedisConfig.getTimeout(),
                (user != null && !user.isEmpty()) ? user : null,
                (pass != null && !pass.isEmpty()) ? pass : null,
                RedisConfig.getDatabase());

        // Validate connection (non-fatal — server can run without Redis)
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            log.info("Redis connected: {}:{}/{}",
                    RedisConfig.getHost(), RedisConfig.getPort(), RedisConfig.getDatabase());
        } catch (Exception e) {
            log.warn("Redis unavailable: {} — server will start but push will be degraded", e.getMessage());
            jedisPool.close();
            jedisPool = null;
        }
    }

    public static Jedis getResource() {
        if (jedisPool == null) {
            return null;  // Redis is down, caller handles null
        }
        try {
            return jedisPool.getResource();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return jedisPool != null && !jedisPool.isClosed();
    }

    public static void close() {
        if (jedisPool != null) {
            jedisPool.close();
            log.info("Redis pool closed");
        }
    }
}
