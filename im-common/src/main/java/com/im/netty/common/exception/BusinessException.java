package com.im.netty.common.exception;

import com.im.netty.common.enums.APIErrorCommonEnum;

/**
 * Business-level runtime exception.
 */
public class BusinessException extends RuntimeException {

    private int code = 500;

    public BusinessException() {
        super();
    }

    public BusinessException(String msg) {
        super(msg);
    }

    public BusinessException(APIErrorCommonEnum errorEnum) {
        super(errorEnum.getMessage());
        this.code = errorEnum.getCode();
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public int getCode() {
        return code;
    }
}
