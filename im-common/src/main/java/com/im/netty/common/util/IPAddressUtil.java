package com.im.netty.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Local IP address resolution utility.
 * Only the local-IP methods are kept; IP geolocation was stripped
 * since it's unused by the netty modules.
 */
public final class IPAddressUtil {

    private static final Logger log = LoggerFactory.getLogger(IPAddressUtil.class);

    private static volatile String localIP;

    private IPAddressUtil() {}

    /**
     * Get the local machine's IPv4 address.
     * Falls back to enumerating network interfaces if the simple method returns loopback.
     */
    public static String getLocalIPv4Address() {
        if (localIP != null) {
            return localIP;
        }
        String ip = getLocalIp();
        if (ip == null || "127.0.0.1".equals(ip)) {
            log.warn("Local IP is loopback, scanning network interfaces...");
            ip = getExternalIPv4Address();
        }
        localIP = ip;
        return ip;
    }

    private static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.error("Failed to get local IP via InetAddress", e);
            return null;
        }
    }

    /**
     * Scan all network interfaces for the first non-loopback IPv4 address.
     */
    public static String getExternalIPv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to scan network interfaces", e);
        }
        return null;
    }
}
