package com.im.netty.domain.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Server-to-client message DTO with proxy/agent support.
 * <p>
 * Used in merchant customer-service scenarios:
 * <ul>
 *   <li>Buyer → Shop: proxyReceiverId is the assigned customer-service agent</li>
 *   <li>Agent → Buyer: proxySenderId is the agent speaking on behalf of the shop</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UgcServerMessageDTO<T> extends BaseIMMessageDTO<T> {

    /**
     * Proxy receiver ID — the actual person who receives the message
     * (e.g. customer-service agent assigned to handle a shop's messages).
     */
    private String proxyReceiverId;

    /**
     * Proxy sender ID — the actual person who sent the message
     * (e.g. agent replying on behalf of a shop).
     */
    private String proxySenderId;
}
