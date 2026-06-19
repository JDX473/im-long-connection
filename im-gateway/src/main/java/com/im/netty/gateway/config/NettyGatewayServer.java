package com.im.netty.gateway.config;

import com.im.netty.gateway.common.PropertyLoader;
import com.im.netty.gateway.handler.NettyGatewayChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty server bootstrap for the gateway.
 * Accepts client WebSocket connections and proxies them to backend netty-server nodes.
 */
public class NettyGatewayServer {

    private static final Logger log = LoggerFactory.getLogger(NettyGatewayServer.class);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(10);

        int port = PropertyLoader.safeGetProperty("netty.server.port", Integer.class, 88);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new NettyGatewayChannelInitializer())
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            bootstrap.bind(port).sync();
            log.info("Netty Gateway listening on port {}", port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gateway bind interrupted", e);
        }
    }

    public void stop() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("Netty Gateway stopped");
    }
}
