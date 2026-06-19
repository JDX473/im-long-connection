package com.im.netty.gateway.router;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.im.netty.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory routing table holding healthy backend netty-server instances.
 * Updated by NacosEventListener whenever the instance list changes.
 * <p>
 * Two load-balancing strategies:
 * <ul>
 *   <li>{@link #rt()} — round-robin (default, for new connections)</li>
 *   <li>{@link #ipHash(String)} — client-IP hash (for sticky sessions, if needed)</li>
 * </ul>
 */
public class RouterTable {

    private static final Logger log = LoggerFactory.getLogger(RouterTable.class);

    private static volatile List<Instance> INSTANCES = new ArrayList<>();
    private static final AtomicInteger rtCount = new AtomicInteger(0);

    /**
     * Atomic replacement of the entire backend instance list.
     */
    public static void doUpdate(List<Instance> instances) {
        INSTANCES = new ArrayList<>(instances);
        log.info("RouterTable updated: {} backend instances", INSTANCES.size());
        for (Instance inst : instances) {
            log.info("  -> {}:{}", inst.getIp(), inst.getPort());
        }
    }

    /**
     * Round-robin selection.
     */
    public static Instance rt() {
        List<Instance> list = INSTANCES;
        if (list.isEmpty()) {
            throw new BusinessException("No available Netty backend instances");
        }
        int idx = Math.abs(rtCount.incrementAndGet()) % list.size();
        return list.get(idx);
    }

    /**
     * IP-hash sticky selection. Falls back to round-robin if clientIp is empty.
     */
    public static Instance ipHash(String clientIp) {
        List<Instance> list = INSTANCES;
        if (list.isEmpty()) {
            throw new BusinessException("No available Netty backend instances");
        }
        if (clientIp == null || clientIp.isEmpty()) {
            return rt();
        }
        int idx = Math.abs(clientIp.hashCode()) % list.size();
        return list.get(idx);
    }

    public static Instance getInstance(int index) {
        List<Instance> list = INSTANCES;
        if (list.isEmpty()) {
            throw new BusinessException("No available Netty backend instances");
        }
        return list.get(Math.abs(index) % list.size());
    }

    public static int size() {
        return INSTANCES.size();
    }
}
