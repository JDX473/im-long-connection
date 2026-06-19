# IM Long Connection

提取自 WebChat IM 系统的长连接模块，独立可运行的 Netty WebSocket 集群方案。

## 架构概览

```
浏览器/客户端
    │  WebSocket (ws://gateway:88/netty-service/ws?userId=xxx)
    ▼
┌─────────────────────────────┐
│  im-gateway  (端口 88)       │  ← WebSocket 反向代理 + 负载均衡
│  - Nacos 发现后端节点         │
│  - Round-Robin 连接分发       │
│  - 透明帧转发 (不解析内容)    │
└──────────┬──────────────────┘
           │  ws://
    ┌──────┴──────┐
    ▼             ▼
┌──────────┐ ┌──────────┐
│im-server │ │im-server │  ← 高性能 IM 消息服务器
│  节点 1  │ │  节点 2  │
│          │ │          │
│ Redis    │ │ Redis    │  ← 用户会话定位
│ RocketMQ │ │ RocketMQ │  ← 两阶段消息路由
└────┬─────┘ └────┬─────┘
     │            │
     └─────┬──────┘
           │  RocketMQ
           ▼
      业务服务 (持久化/处理)
```

## 模块说明

| 模块 | 说明 |
|------|------|
| `im-domain` | 消息 DTO，无外部依赖 |
| `im-common` | 工具类、枚举、异常，仅依赖 Jackson + Commons Lang |
| `im-gateway` | Netty WebSocket 反向代理，不解析业务内容 |
| `im-server` | Netty WebSocket 消息服务器，分布式会话 + 消息路由 |

## 消息路由（核心设计）

```
客户端消息上行:
  Client → Gateway → Netty Server → RocketMQ("netty_server_chat_msg") → 业务服务

服务端消息下行:
  业务服务 → RocketMQ("webchat_ugc_messages")
    → 每个 Netty 节点 Consumer-1 收到
    → 查 Redis: 目标用户在哪个节点?
    → 发到 RocketMQ("TOPIC_NODE_IM_SEND_MSG", tag=目标节点MD5)
    → 只有目标节点的 Consumer-2 订阅了自己的 tag
    → 查本地 Channel Map → writeAndFlush → 客户端
```

## 快速开始

### 前置依赖
- JDK 17+
- Maven 3.8+
- Nacos (服务发现)
- Redis (会话定位)
- RocketMQ (消息路由)

### 构建

```bash
cd im-long-connection
mvn clean package -DskipTests
```

### 启动 Server（可多节点）

```bash
# 节点 1（默认端口 9999）
java -Denv=dev -jar im-server/target/im-server-1.0.0.jar

# 节点 2（需要复制配置文件修改端口）
java -Denv=dev -Dnetty.server.port=9998 -jar im-server/target/im-server-1.0.0.jar
```

### 启动 Gateway

```bash
java -Denv=dev -jar im-gateway/target/im-gateway-1.0.0.jar
```

### 连接测试

```javascript
// 浏览器控制台
const ws = new WebSocket('ws://localhost:88/netty-service/ws?userId=test_user');
ws.onopen = () => {
    ws.send(JSON.stringify({
        messageType: 3,
        senderId: 'test_user',
        receiverId: 'target_user',
        message: 'Hello IM!'
    }));
};
ws.onmessage = (e) => console.log('收到:', e.data);
```

## 相对原项目的改进

1. **修复连接断开不清理** — `channelInactive()` 中自动调用 `removeChannel()` + `removeInstance()`
2. **修复关机不注销** — 注册 JVM shutdown hook 调用 `deregisterInstance()`
3. **移除硬编码 env** — 允许 `-Denv=prod` 覆盖
4. **精简依赖** — 移除 Spring Boot、Spring Cloud、MinIO、Redisson 等无关依赖
5. **独立构建** — 每个模块可独立打包为 fat jar
