package com.im.netty.common.constants;

/**
 * Shared constants for the IM long-connection system.
 */
public final class ImConstant {

    private ImConstant() {}

    /** Redis key prefix */
    public static final String REDIS_KEY_PREFIX = "IM_LC_";

    /** MD5 salt */
    public static final String MD5_SALT = "SAH(S&0218328jdhaj(**";

    /** UTF-8 encoding */
    public static final String ENCODING_UTF8 = "UTF-8";
}
