package com.im.netty.gateway.handler;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.im.netty.common.constants.NettySocketConstants;
import com.im.netty.gateway.router.RouterTable;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Core gateway handler — transparent WebSocket proxy and load balancer.
 *
 * <h3>Connection lifecycle</h3>
 * <ol>
 *   <li>Client sends HTTP WebSocket upgrade request → load-balance to a backend netty-server</li>
 *   <li>Forward the 101 response back to client (computing Sec-WebSocket-Accept manually)</li>
 *   <li>Replace HTTP codecs with WebSocket frame codecs on both sides</li>
 *   <li>Bidirectionally forward WebSocket frames without inspecting payloads</li>
 * </ol>
 *
 * <h3>Design decisions</h3>
 * <ul>
 *   <li><b>Transparent proxy:</b> does not decode WebSocket payload — pure frame-level forwarding</li>
 *   <li><b>EventLoop affinity:</b> backend connection shares the client channel's EventLoop,
 *       avoiding thread-switching overhead</li>
 *   <li><b>Single-node design:</b> gateway state is per-connection, in-memory; no distributed state</li>
 * </ul>
 */
public class NettyGatewayRouterHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(NettyGatewayRouterHandler.class);

    private static final String WEBSOCKET_MAGIC_KEY = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private Channel backendChannel;
    private ChannelPromise backendChannelPromise;
    private WebSocketClientHandshaker handshaker;
    private boolean isWebSocketUpgraded;

    // ── Message dispatch ──────────────────────────────────────────

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            doHandleWebSocketRequest(ctx, (FullHttpRequest) msg);
        } else if (isWebSocketUpgraded && msg instanceof WebSocketFrame) {
            doHandleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    // ── HTTP request handling (WebSocket upgrade) ─────────────────

    private void doHandleWebSocketRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        // Check if this is a WebSocket upgrade request
        if (!isWebSocketUpgradeRequest(req)) {
            ReferenceCountUtil.release(req);
            ctx.close();
            return;
        }

        // Double-checked locking: ensure one backend connection per client
        if (backendChannelPromise == null) {
            synchronized (this) {
                if (backendChannelPromise == null) {
                    backendChannelPromise = ctx.newPromise();
                    // Retain for async use in the listener; released in listener callback
                    req.retain();
                    initBackendConnection(ctx, req);
                }
            }
        }

        // When backend connection is ready, trigger the WebSocket handshake
        backendChannelPromise.addListener((ChannelFutureListener) future -> {
            try {
                if (future.isSuccess()) {
                    doWriteToNettyServer(ctx);
                } else {
                    log.error("Backend connection failed", future.cause());
                    ctx.close();
                }
            } finally {
                ReferenceCountUtil.release(req);
            }
        });
    }

    private boolean isWebSocketUpgradeRequest(HttpRequest req) {
        String upgrade = req.headers().get(HttpHeaderNames.UPGRADE);
        return upgrade != null && upgrade.equalsIgnoreCase("websocket");
    }

    private void initBackendConnection(ChannelHandlerContext ctx, FullHttpRequest req) {
        // Pick backend via round-robin
        Instance backendInstance = RouterTable.rt();
        // Preserve query string (e.g. ?userId=alice) from the original request
        String queryString = req.uri().contains("?") ? req.uri().substring(req.uri().indexOf('?')) : "";
        String wsUri = "ws://" + backendInstance.getIp() + ":" + backendInstance.getPort()
                + NettySocketConstants.CHAT_WEBSOCKET_PATH + queryString;
        URI uri = URI.create(wsUri);

        log.info("Routing client {} → backend {} (current table size: {})",
                ctx.channel().remoteAddress(), wsUri, RouterTable.size());

        // Create WebSocket client handshaker (forwarding all original headers)
        handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, false, req.headers());

        // Bootstrap on the SAME EventLoop as the client channel — zero thread switch
        Bootstrap bootstrap = new Bootstrap()
                .group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(327680))
                                .addLast(new BackendHandler(ctx.channel()));
                    }
                });

        bootstrap.connect(backendInstance.getIp(), backendInstance.getPort())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        backendChannel = future.channel();
                        backendChannelPromise.setSuccess();
                    } else {
                        backendChannelPromise.setFailure(future.cause());
                    }
                });
    }

    private void doWriteToNettyServer(ChannelHandlerContext ctx) {
        // Initiate WebSocket handshake toward backend
        handshaker.handshake(backendChannel);
        // Handler cleanup — remove HTTP codecs from client pipeline after handshake completes
        ctx.pipeline().remove(HttpServerCodec.class);
        ctx.pipeline().remove(HttpObjectAggregator.class);
    }

    // ── Backend handshake response handler ────────────────────────

    private class BackendHandler extends ChannelInboundHandlerAdapter {

        private final Channel clientChannel;

        BackendHandler(Channel clientChannel) {
            this.clientChannel = clientChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                if (!handshaker.isHandshakeComplete()) {
                    // finishHandshake validates the backend's 101 response.
                    // Since we forwarded the client's original headers (including Sec-WebSocket-Key)
                    // to the backend via the handshaker, the backend's Sec-WebSocket-Accept
                    // is already valid for the client. Just forward the response as-is.
                    handshaker.finishHandshake(ctx.channel(), response);

                    // Forward the 101 Switching Protocols response to the client
                    HttpResponse clientResponse = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);
                    clientResponse.headers().add(response.headers());

                    clientChannel.writeAndFlush(clientResponse).addListener(future -> {
                        if (future.isSuccess()) {
                            // Replace HTTP codecs with WebSocket frame codecs on client side
                            ChannelPipeline clientPipeline = clientChannel.pipeline();
                            clientPipeline.addFirst(new WebSocket13FrameDecoder(true, true, 65536));
                            clientPipeline.addFirst(new WebSocket13FrameEncoder(false));

                            // Replace backend pipeline the same way
                            ChannelPipeline backendPipeline = ctx.pipeline();
                            backendPipeline.remove(HttpClientCodec.class);
                            backendPipeline.remove(HttpObjectAggregator.class);
                            backendPipeline.remove(this);
                            backendPipeline.addFirst(new WebSocket13FrameDecoder(false, true, 65536));
                            backendPipeline.addFirst(new WebSocket13FrameEncoder(true));
                            backendPipeline.addLast(new WebSocketFrameForwarderHandler(clientChannel));

                            isWebSocketUpgraded = true;
                            log.info("WebSocket upgrade complete: client {} ↔ backend {}",
                                    clientChannel.remoteAddress(), ctx.channel().remoteAddress());
                        }
                    });
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Backend handler error", cause);
            ctx.close();
            clientChannel.close();
        }
    }

    // ── WebSocket frame forwarding ────────────────────────────────

    private void doHandleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (backendChannel != null && backendChannel.isActive()) {
            backendChannel.writeAndFlush(frame.retain());
        } else {
            log.error("Backend channel inactive, closing client connection");
            ReferenceCountUtil.release(frame);
            ctx.close();
        }
    }

    /**
     * Forwards WebSocket frames from backend to client.
     */
    private static class WebSocketFrameForwarderHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

        private final Channel clientChannel;

        WebSocketFrameForwarderHandler(Channel clientChannel) {
            this.clientChannel = clientChannel;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) {
            if (clientChannel != null && clientChannel.isActive()) {
                clientChannel.writeAndFlush(msg.retain());
            } else {
                log.warn("Client channel inactive, dropping frame from backend");
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (clientChannel != null && clientChannel.isActive()) {
                clientChannel.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Frame forwarder error", cause);
            ctx.close();
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeChannels(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Gateway handler error", cause);
        closeChannels(ctx);
    }

    private void closeChannels(ChannelHandlerContext ctx) {
        if (backendChannel != null && backendChannel.isActive()) {
            backendChannel.close();
        }
        backendChannel = null;
        backendChannelPromise = null;
        isWebSocketUpgraded = false;
    }
}
