package com.im.netty.common.enums;

import lombok.Getter;

/**
 * IM message type codes.
 */
@Getter
public enum MallImMessageTypeEnum {

    /** "typing" indicator start */
    MSG_START_INPUT(1, "typing start"),

    /** "typing" indicator stop */
    MSG_EXIT_INPUT(2, "typing end"),

    /** Plain text message */
    MSG_TEXT(3, "text"),

    /** Image message (URL in message body) */
    MSG_IMAGE(4, "image"),

    /** Rich card message (product card, order card, etc.) */
    MSG_CARD(5, "card"),

    /** Mixed image + text message */
    MSG_IMAGE_TEXT(6, "image+text");

    private final int code;
    private final String description;

    MallImMessageTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /** Types that don't need persistence — just forward directly */
    public static boolean isForwarding(Integer type) {
        return type != null && (type == MSG_START_INPUT.code || type == MSG_EXIT_INPUT.code);
    }

    /** Card message types */
    public static boolean isCardMessage(Integer type) {
        return type != null && type == MSG_CARD.code;
    }

    /** Recognized message types */
    public static boolean isSupport(Integer type) {
        if (type == null) return false;
        for (MallImMessageTypeEnum t : values()) {
            if (t.code == type) return true;
        }
        return false;
    }

    public static MallImMessageTypeEnum fromCode(int code) {
        for (MallImMessageTypeEnum t : values()) {
            if (t.code == code) return t;
        }
        return null;
    }
}
