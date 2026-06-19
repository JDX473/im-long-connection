package com.im.netty.gateway.connector;

import com.alibaba.nacos.api.naming.pojo.Instance;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raw TCP connector to backend nodes (alternative/legacy path).
 * <p>
 * NOTE: This class is preserved for potential non-WebSocket use cases.
 * The primary WebSocket proxying path in {@code NettyGatewayRouterHandler}
 * does NOT use this connector — it creates its own Bootstrap per connection.
 * <p>
 * Protocol: length-field framed TCP with UTF-8 string codec.
 */
public class NodeConnector {

    private static final Logger log = LoggerFactory.getLogger(NodeConnector.class);

    /** Cached backend channels keyed by "ip:port" */
    private static final ConcurrentHashMap<String, Channel> channelCache = new ConcurrentHashMap<>();

    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1MB

    private NodeConnector() {}

    public static void send(Instance instance, String message) {
        String key = instance.getIp() + ":" + instance.getPort();
        Channel channel = channelCache.get(key);

        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
            return;
        }

        // Create new connection
        connect(instance, message);
    }

    private static void connect(Instance instance, String message) {
        EventLoopGroup group = new NioEventLoopGroup(1);
        String key = instance.getIp() + ":" + instance.getPort();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4))
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast(new StringDecoder(StandardCharsets.UTF_8))
                                    .addLast(new StringEncoder(StandardCharsets.UTF_8))
                                    .addLast(new SimpleChannelInboundHandler<String>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                            log.debug("Received from backend {}: {}", key, msg);
                                        }

                                        @Override
                                        public void channelInactive(ChannelHandlerContext ctx) {
                                            channelCache.remove(key);
                                            ctx.close();
                                        }
                                    });
                        }
                    });

            ChannelFuture future = bootstrap.connect(instance.getIp(), instance.getPort()).sync();
            Channel ch = future.channel();
            channelCache.put(key, ch);
            ch.writeAndFlush(message);

        } catch (Exception e) {
            log.error("Failed to connect to backend {}", key, e);
        }
    }
}
