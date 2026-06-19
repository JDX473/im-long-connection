package com.im.netty.common.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;

/**
 * JSON serialization utility wrapping Jackson ObjectMapper.
 */
public final class JsonUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);

    private static final ObjectMapper om = new ObjectMapper();
    private static final JsonFactory factory = new JsonFactory();

    static {
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
    }

    private JsonUtil() {}

    public static <T> T fromJson(String jsonStr, Class<T> clazz) {
        try {
            return om.readValue(jsonStr, clazz);
        } catch (Exception e) {
            log.error("fromJson error, jsonStr: {}, class: {}", jsonStr, clazz.getName(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String jsonStr, TypeReference<?> valueTypeRef) {
        try {
            return (T) om.readValue(jsonStr, valueTypeRef);
        } catch (Exception e) {
            log.error("fromJson error, typeRef: {}", valueTypeRef.getType(), e);
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJson(String jsonStr, Class<T> clazz, Class<?> parametricType) {
        JavaType javaType = om.getTypeFactory().constructParametricType(clazz, parametricType);
        try {
            return om.readValue(jsonStr, javaType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJsonString(Object object) {
        try {
            return om.writeValueAsString(object);
        } catch (Exception e) {
            log.error("toJsonString error, obj: {}", object.getClass().getName(), e);
            throw new RuntimeException(e);
        }
    }

    public static String toJson(Object obj, boolean prettyPrinter) {
        if (obj == null) return null;
        StringWriter sw = new StringWriter();
        try {
            JsonGenerator jg = factory.createGenerator(sw);
            if (prettyPrinter) {
                jg.useDefaultPrettyPrinter();
            }
            om.writeValue(jg, obj);
        } catch (IOException e) {
            log.error("toJson error, obj: {}", obj.getClass().getName(), e);
            throw new RuntimeException(e);
        }
        return sw.toString();
    }
}
