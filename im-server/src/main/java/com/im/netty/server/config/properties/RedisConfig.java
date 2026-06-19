package com.im.netty.server.config.properties;

import com.im.netty.server.common.PropertyLoader;

/**
 * Redis connection configuration.
 */
public class RedisConfig {

    private static String host;
    private static int port;
    private static String username;
    private static String password;
    private static int database;
    private static int timeout;
    private static int maxActive;
    private static long maxWait;
    private static int minIdle;

    private RedisConfig() {}

    public static void init() {
        host = PropertyLoader.safeGetProperty("redis.host", String.class);
        port = PropertyLoader.safeGetProperty("redis.port", Integer.class);
        username = PropertyLoader.safeGetProperty("redis.username", String.class, "");
        password = PropertyLoader.safeGetProperty("redis.password", String.class, "");
        database = PropertyLoader.safeGetProperty("redis.database", Integer.class);
        timeout = PropertyLoader.safeGetProperty("redis.jedis.timeout", Integer.class, 5000);
        maxActive = PropertyLoader.safeGetProperty("redis.jedis.pool.max-active", Integer.class, 100);
        maxWait = PropertyLoader.safeGetProperty("redis.jedis.pool.max-wait", Long.class, -1L);
        minIdle = PropertyLoader.safeGetProperty("redis.jedis.pool.min-idle", Integer.class, 10);
    }

    public static String getHost() { return host; }
    public static int getPort() { return port; }
    public static String getUsername() { return username; }
    public static String getPassword() { return password; }
    public static int getDatabase() { return database; }
    public static int getTimeout() { return timeout; }
    public static int getMaxActive() { return maxActive; }
    public static long getMaxWait() { return maxWait; }
    public static int getMinIdle() { return minIdle; }
}
