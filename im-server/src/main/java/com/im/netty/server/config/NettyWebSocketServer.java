package com.im.netty.server.config;

import com.im.netty.server.config.properties.NettyServerProperties;
import com.im.netty.server.handler.NettyWebSocketChannelInitializer;
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
 * Netty WebSocket server bootstrap for IM message processing.
 */
public class NettyWebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(NettyWebSocketServer.class);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private io.netty.channel.Channel serverChannel;

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(10);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new NettyWebSocketChannelInitializer())
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            int port = NettyServerProperties.getPort();
            serverChannel = bootstrap.bind(port).sync().channel();
            log.info("Netty WebSocket server listening on port {}", port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Server bind interrupted", e);
        }
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("Netty WebSocket server stopped");
    }
}
