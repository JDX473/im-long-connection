package com.im.netty.common.enums;

import lombok.Getter;

/**
 * Common API error codes.
 */
@Getter
public enum APIErrorCommonEnum {

    UN_AUTH(101400, "No permission"),
    USER_UN_LOGIN(101401, "Not logged in"),
    USER_NOT_FOUND(101404, "User not found"),
    VALID_EXCEPTION(100001, "Parameter validation failed"),
    API_UN_AUTH(101406, "API call not authorized"),
    UN_SAFE_EVENT(999999, "Unsafe event detected");

    private final int code;
    private final String message;

    APIErrorCommonEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
