package com.im.netty.common.util;

import java.util.UUID;

/**
 * ID generation utility.
 */
public final class IDGenerateUtil {

    private IDGenerateUtil() {}

    /**
     * Generate a prefixed unique ID, e.g. "IM_a1b2c3d4...".
     */
    public static String createId(String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + "_" + uuid();
        }
        return uuid();
    }

    /**
     * Generate a 32-char UUID without dashes.
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
