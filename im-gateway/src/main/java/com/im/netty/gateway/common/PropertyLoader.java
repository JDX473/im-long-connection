package com.im.netty.gateway.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads configuration from classpath: {@code config/application-{env}.properties}.
 */
public final class PropertyLoader {

    private static final Logger log = LoggerFactory.getLogger(PropertyLoader.class);

    public static final ConcurrentHashMap<String, Object> PROPERTIES = new ConcurrentHashMap<>();

    private PropertyLoader() {}

    public static void init() {
        String env = System.getProperty("env", "dev");
        String path = "config/application-" + env + ".properties";
        log.info("Loading properties from: {}", path);

        try (InputStream is = PropertyLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Configuration file not found: " + path);
            }
            Properties props = new Properties();
            props.load(is);
            props.forEach((k, v) -> PROPERTIES.put((String) k, v));
            log.info("Loaded {} properties", PROPERTIES.size());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load properties: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T safeGetProperty(String key, Class<T> type, T... defaultValue) {
        Object value = PROPERTIES.get(key);
        if (value == null) {
            if (defaultValue != null && defaultValue.length > 0) {
                return defaultValue[0];
            }
            throw new RuntimeException("Required property not found: " + key);
        }
        String strVal = String.valueOf(value);
        if (type == Integer.class) return (T) Integer.valueOf(strVal);
        if (type == Long.class) return (T) Long.valueOf(strVal);
        if (type == Float.class) return (T) Float.valueOf(strVal);
        if (type == Double.class) return (T) Double.valueOf(strVal);
        if (type == Boolean.class) return (T) Boolean.valueOf(strVal);
        return (T) strVal;
    }
}
