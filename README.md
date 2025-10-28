# A2A Spring Boot 3.5.3 多模块项目

本项目在 `e:\project\GPT\A2A-0.3.0` 下搭建，包含：
- `a2a-server`：Spring Boot 服务端示例，集成 A2A AgentCard 与 AgentExecutor 的基础骨架，并提供 JSON-RPC 端点
- `a2a-client`：Spring Boot 客户端示例，演示 JSON-RPC 调用
- `spec-grpc`：A2A gRPC 规范模块，负责生成 gRPC Java 类

## 版本与依赖
- Spring Boot：`3.5.3`
- Java：`17`
- A2A Java SDK 参考实现依赖版本：`0.3.0.Beta2`（发布信息见 https://github.com/a2aproject/a2a-java/releases ）

> 说明：参考实现模块目前基于 Quarkus，但其中包含 A2A 规范与通用类，方便在 Spring 环境中引用与后续适配。

## 快速开始

1. （可选）复制 A2A 的 gRPC 规范：
   - 从 `https://github.com/a2aproject/A2A/blob/main/specification/grpc/a2a.proto` 获取最新 `a2a.proto`
   - 覆盖到 `spec-grpc/src/main/proto/a2a.proto`
   - 确保在 proto 文件中设置：
     ```
     option java_package = "io.a2a.grpc";
     ```

2. 生成 gRPC 类（如使用 gRPC）：
   - 在项目根目录执行：
     - `mvn clean install -Pproto-compile`

3. 构建项目：
   - 根目录执行：`mvn clean install`

4. 运行服务端：
  - 进入 `a2a-server`：`mvn spring-boot:run`
  - 服务默认端口：`10001`
  - 健康检查：`http://localhost:10001/actuator/health`
  - HarmonyOS Agent JSON-RPC 端点：`POST http://localhost:10001/agent/message`
    - 服务端会根据请求方法返回标准 JSON-RPC 响应或通过 `Content-Type: text/event-stream` 推送 SSE 消息
    - `initialize` / `notifications/initialized`：建立会话并返回 `agentSessionId`
    - `message/stream`：在对话期间返回 `TaskStatusUpdateEvent`、`TaskArtifactUpdateEvent` 事件流
    - `tasks/cancel`、`clearContext`、`authorize`、`deauthorize`：同步 JSON-RPC 响应

5. 运行客户端：
   - 进入 `a2a-client`：`mvn spring-boot:run`
   - 控制台将打印 JSON-RPC 调用返回的文本内容

## 代码结构说明

- 服务端示例：
  - `com.example.a2a.server.agent.WeatherAgent`：示例 Agent，提供伪天气查询
  - `com.example.a2a.server.agent.WeatherAgentConfig`：
    - `@Bean @PublicAgentCard` 暴露 `AgentCard`
    - `@Bean` 暴露 `AgentExecutor`，示例实现从任务输入中提取文本并返回消息
  - `com.example.a2a.server.transport.agent.AgentMessageController`：HarmonyOS Agent 规范 `/agent/message` 控制器
  - `com.example.a2a.server.transport.agent.dto.AgentJsonRpcDtos`：HarmonyOS Agent 规范 JSON-RPC 请求/响应 DTO
  - `com.example.a2a.server.transport.JsonRpcController`：JSON-RPC 控制器
  - `com.example.a2a.server.transport.JsonRpcDtos`：JSON-RPC 请求/响应 DTO

- 客户端示例：
  - `com.example.a2a.client.ClientService`：使用 JSON-RPC 调用服务端 `/jsonrpc` 接口
  - `com.example.a2a.client.jsonrpc.JsonRpcDtos`：客户端 JSON-RPC DTO

- gRPC 规范：
  - `spec-grpc` 模块使用 `protobuf-maven-plugin` 与 `grpc-java` 插件在 Windows 环境下生成 Java 类

## 后续集成建议

- 传输层选择：
  - JSON-RPC：依赖 `a2a-java-sdk-reference-jsonrpc`（当前已为演示提供一个简化的JSON-RPC端点）
  - gRPC：依赖 `a2a-java-sdk-reference-grpc` 并使用 `spec-grpc` 生成类
  - REST：依赖 `a2a-java-sdk-reference-rest`

- 在 Spring 环境中适配 A2A：
  - 适配点：路由与控制器（将 A2A 任务处理流程暴露为 REST/JSON-RPC/gRPC 端点）
  - 使用 `AgentExecutor` 与 `TaskUpdater` 管理任务状态与事件队列（当前 JSON-RPC 示例直接调用示例 Agent）

- HarmonyOS Agent RPC 兼容性说明：
  - `initialize`、`notifications/initialized`：通过 `AgentMessageController` 与 `AgentSessionService` 建立并维护七天有效期会话
  - `message/stream`：`StreamingTaskService` 使用 `SseEmitter` 推送 `TaskStatusUpdateEvent`、`TaskArtifactUpdateEvent`，符合 SSE 格式
  - `tasks/cancel`、`clearContext`：结合 `StreamingTaskService` 与 `ConversationContextService` 管理任务与上下文
  - `authorize`、`deauthorize`：`AuthorizationService` 支持生成/撤销 `agentLoginSessionId`

## 注意事项

- 若构建失败，请先确保：
  - JDK 17+ 已安装且 `JAVA_HOME` 配置正确
  - Maven 已安装并可执行 `mvn -v`
  - `spec-grpc` 已替换为真实的 `a2a.proto` 后再执行 `-Pproto-compile`