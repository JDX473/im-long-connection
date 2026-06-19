package com.im.netty.domain.dto;

/**
 * Client-to-server message DTO.
 * No additional fields beyond {@link BaseIMMessageDTO}.
 * The server enriches msgId, msgTime, and read status on receipt.
 */
public class NettyServerMessageDTO<T> extends BaseIMMessageDTO<T> {
}
