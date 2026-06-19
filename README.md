# IM Long Connection

提取自 WebChat IM 系统的长连接模块，独立可运行的 Netty WebSocket 集群方案。零 Spring 依赖，纯 Netty + RocketMQ + Redis + Nacos 实现。

## 架构

```
浏览器 / 客户端
    │
    │  ws://gateway:88/netty-service/ws?userId=xxx
    ▼
┌──────────────────────────────────────────────┐
│  im-gateway  (port 88)                       │
│  ┌──────────────────────────────────────────┐│
│  │ HttpServerCodec → HttpObjectAggregator   ││
│  │   → NettyGatewayRouterHandler            ││
│  │       ├── 新连接：Round-Robin → 后端节点  ││
│  │       ├── 升级后：替换为 WS 帧编解码器    ││
│  │       └── 双向透明转发 WebSocket 帧       ││
│  └──────────────────────────────────────────┘│
│  Nacos 服务发现 ──→ RouterTable 路由表        │
└──────┬──────────────────┬────────────────────┘
       │  ws://           │  ws://
       ▼                  ▼
┌──────────────┐  ┌──────────────┐
│  im-server   │  │  im-server   │   ← 可水平扩展
│   node-1     │  │   node-2     │
│              │  │              │
│  ┌──────────┐│  │  ┌──────────┐│
│  │ Pipeline ││  │  │ Pipeline ││
│  │----------││  │  │----------││
│  │ Codec    ││  │  │ Codec    ││
│  │ RequestH ││  │  │ RequestH ││
│  │ WSProtoH ││  │  │ WSProtoH ││
│  │ IdleState││  │  │ IdleState││
│  │ BeatH    ││  │  │ BeatH    ││
│  │ MsgH     ││  │  │ MsgH     ││
│  └──────────┘│  │  └──────────┘│
│              │  │              │
│  Redis       │  │  Redis       │  ← 用户会话定位
│  RocketMQ    │  │  RocketMQ    │  ← 两阶段消息路由
│  Nacos       │  │  Nacos       │  ← 注册到注册中心
└──────┬───────┘  └──────┬───────┘
       │                 │
       └────────┬────────┘
                │  RocketMQ
                ▼
        ┌──────────────┐
        │   业务服务     │  ← 消息持久化 / 业务处理
        └──────────────┘
```

## 模块

| 模块 | 依赖 | 说明 |
|------|------|------|
| `im-domain` | Lombok | 消息 DTO（`BaseIMMessageDTO`、`NettyServerMessageDTO`、`UgcServerMessageDTO`） |
| `im-common` | im-domain, Jackson, Commons Lang3 | 工具类、枚举、异常 |
| `im-gateway` | im-common, Netty, Nacos SDK | WebSocket 反向代理 + 负载均衡 |
| `im-server` | im-common, Netty, RocketMQ, Jedis, Nacos SDK | IM 消息服务器 |

## 消息路由（两阶段 RocketMQ）

这是整个系统最核心的设计。

### 上行：客户端 → 业务服务

```
Client → Gateway（透明转发）→ Netty Server
  → 解析 JSON，补全 msgId / msgTime / read
  → RocketMQ Topic "netty_server_chat_msg"
  → 业务服务消费，持久化到 MongoDB，业务处理
```

### 下行：业务服务 → 目标客户端

```
业务服务 → RocketMQ Topic "webchat_ugc_messages"
  │
  ├── Netty Server Node-1（Consumer-1 收到）
  ├── Netty Server Node-2（Consumer-1 收到）
  │       │
  │       └── IMMessageHandler.handleMessage()
  │             ├── 查 Redis：目标用户在 node-1？
  │             ├── 是 → 查本地 Channel Map → writeAndFlush → Client
  │             └── 否 → RocketMQ Topic "TOPIC_NODE_IM_SEND_MSG"
  │                         tag = 目标节点 MD5
  │                         │
  │                         └── 只有 node-1 的 Consumer-2 订阅了这个 tag
  │                               → doWriteMessage2Receiver()
  │                               → 查本地 Channel Map → writeAndFlush → Client
```

### 为什么是两阶段而不是全节点广播

全节点广播的问题是每个节点都要处理每条消息，集群规模越大浪费越严重。这里用 RocketMQ 的 **Tag 过滤** 实现精准投递：

- 每个节点启动时计算自己的 MD5 tag
- Consumer-2 只订阅 `TOPIC_NODE_IM_SEND_MSG` 下自己 tag 的消息
- 发送方查 Redis 拿到目标节点的 MD5，发消息时打上对应 tag
- RocketMQ Broker 端按 tag 过滤，只有目标节点收到

### 代理模式

支持买家 → 商家 → 客服坐席的消息代理链路：

```java
// UgcServerMessageDTO
proxyReceiverId  // 买家发消息时，实际接收方是被分配的客服
proxySenderId    // 客服回复时，发件人身份是客服代商家发言
```

## 心跳机制

```
                        服务端                              客户端
                          │                                  │
                  60s 无数据 │                                  │
                          │──── PingWebSocketFrame ──────────→│
                          │                                  │ ← RFC 6455 自动回 Pong
                          │←──────── Pong ───────────────────│
                          │  (重置计数器)                       │
                          │                                  │
                  60s 无数据 │                                  │
                          │──── Ping ────────────✖  (丢包)     │
                  60s 无数据 │                                  │
                          │──── Ping ────────────✖             │
                  60s 无数据 │                                  │
                          │──── Ping ────────────✖             │
                          │                                  │
               连续 3 次无响应 │                                  │
                          │── close ──→ channelInactive ─→ 清理 Redis 会话
```

- **服务端主动探测**：`IdleStateHandler` + `HeartbeatHandler`，60 秒无数据发 Ping，3 次无响应断连
- **客户端零代码**：浏览器和标准 WebSocket 库自动回 Pong（RFC 6455）
- **断连自动清理**：`channelInactive()` 中移除本地 Channel、删除 Redis 会话映射、Nacos 注销

## 连接生命周期

```
连接建立
  │  HTTP GET ?userId=xxx → NettyWebSocketRequestHandler
  │  ├── 提取 userId，存为 Channel 属性
  │  ├── UserChannelManager.addChannel(userId, channel)    ← 本地内存
  │  ├── ChannelRegistryService.registry(userId)            ← Redis Hash
  │  └── URI 改写为 /netty-service/ws，交给协议处理器升级
  ▼
WebSocket 升级
  │  WebSocketServerProtocolHandler → 101 Switching Protocols
  ▼
运行中
  │  IdleStateHandler(60s) → HeartbeatHandler → Ping/Pong
  │  NettyWebSocketMessageHandler → JSON 解析 → RocketMQ → 业务服务
  ▼
连接断开
  │  channelInactive()
  │  ├── UserChannelManager.removeChannel(userId)          ← 清理本地
  │  └── UserChannelManager.removeInstance(userId)          ← 清理 Redis
  │  (shutdown hook → Nacos deregister)
```

## 管道设计

### Gateway（客户端入口）

```
HttpServerCodec → HttpObjectAggregator(320KB) → NettyGatewayRouterHandler
                                                      │
                                      升级前：转发 HTTP 升级请求到后端
                                      升级后：替换为 WS13FrameDecoder/Encoder
                                             双向透明转发 WebSocket 帧
```

### Server（消息服务器）

```
HttpServerCodec
  → HttpObjectAggregator(320KB)
    → NettyWebSocketRequestHandler        # 提取 userId，注册连接
      → WebSocketServerProtocolHandler    # RFC 6455 升级握手
        → IdleStateHandler(60s)           # 读空闲检测
          → HeartbeatHandler              # 发 Ping，3 次无响应断连
            → NettyWebSocketMessageHandler # JSON 反序列化 → 写入 RocketMQ
```

## RocketMQ Topic 拓扑

| Topic | 方向 | Tag | Consumer |
|-------|------|-----|----------|
| `netty_server_chat_msg` | Server → 业务 | `im_chat` | 业务服务 |
| `webchat_ugc_messages` | 业务 → Server | `*` | 所有节点（Consumer-1） |
| `TOPIC_NODE_IM_SEND_MSG` | Server → Server | 目标节点 MD5 | 目标节点（Consumer-2） |

## Redis 数据结构

| Key | 类型 | 字段 | 值 | 过期 |
|-----|------|------|-----|------|
| `IM_LC_USER_NETTY_CHANNEL_INSTANCE_HASH_CACHE` | Hash | userId | 节点 MD5 | 永不过期（断连时主动删除） |

## 消息类型

| Code | 类型 | 说明 |
|------|------|------|
| 1 | `MSG_START_INPUT` | Typing 开始（不持久化，直接转发） |
| 2 | `MSG_EXIT_INPUT` | Typing 结束（不持久化，直接转发） |
| 3 | `MSG_TEXT` | 文本消息 |
| 4 | `MSG_IMAGE` | 图片消息（body 为图片 URL） |
| 5 | `MSG_CARD` | 卡片消息（商品卡片/订单卡片） |
| 6 | `MSG_IMAGE_TEXT` | 图文混排（body 为 `[{type, content}]` 数组） |

## 消息格式

```json
{
    "msgId": "IM_a1b2c3d4e5f6...",
    "messageType": 3,
    "senderId": "user_001",
    "receiverId": "user_002",
    "message": "Hello IM!",
    "msgTime": 1718765432000,
    "read": false,
    "aiGenerate": false,
    "proxyReceiverId": null,
    "proxySenderId": null
}
```

## 快速开始

### 环境要求

- JDK 8+
- Maven 3.8+
- Nacos（服务发现）
- Redis（会话定位）
- RocketMQ（消息路由，NameSrv + Broker）

### 构建

```bash
cd im-long-connection
mvn clean package -DskipTests
```

### 启动 Server（可多节点）

```bash
# 节点 1（默认 9999）
java -Denv=dev -jar im-server/target/im-server-1.0.0.jar

# 节点 2
java -Denv=dev -Dnetty.server.port=9998 -jar im-server/target/im-server-1.0.0.jar
```

### 启动 Gateway

```bash
java -Denv=dev -jar im-gateway/target/im-gateway-1.0.0.jar
```

### 浏览器连接测试

```javascript
const ws = new WebSocket('ws://localhost:88/netty-service/ws?userId=test_user');

ws.onopen = () => {
    console.log('Connected');
    ws.send(JSON.stringify({
        messageType: 3,
        senderId: 'test_user',
        receiverId: 'target_user',
        message: 'Hello IM!'
    }));
};

ws.onmessage = (e) => console.log('Received:', JSON.parse(e.data));
```

## 设计决策

| 决策 | 原因 |
|------|------|
| WebSocket 而非自定义 TCP | 浏览器原生支持，零客户端开发成本；IM 消息体短，JSON 序列化开销可忽略 |
| 网关透明代理 | 不解析业务内容，纯帧级转发，性能高且与业务解耦 |
| 两阶段 MQ 路由 | Tag 过滤避免全节点广播；业务服务只关心消息处理，推送独立演进 |
| EventLoop 亲和 | 网关的后端连接复用客户端 EventLoop，零线程切换 |
| 零 Spring 依赖 | Netty 模块本身不需要 Spring；去掉后 fat jar 从 80MB 降到 15MB，启动即用 |
| 服务端心跳探测 | 30 秒级发现死连接并清理 Redis 会话，避免消息投递到幽灵连接 |
