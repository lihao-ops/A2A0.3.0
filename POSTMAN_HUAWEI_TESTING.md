# HarmonyOS Agent Postman 调试指南

本文档说明如何在 Postman 中调用项目提供的 HarmonyOS `/agent/message` 端点，方便在本地验证与华为终端的对接流程。

## 前置条件

1. 在项目根目录执行 `mvn clean install`，确保服务端模块成功构建。
2. 进入 `a2a-server` 目录执行 `mvn spring-boot:run`，默认监听 `http://localhost:10001`。
3. Postman 版本需支持 **Server-Sent Events (SSE)**。推荐使用 Postman v11 及以上。

## 公共设置

- URL：`http://localhost:10001/agent/message`
- Method：`POST`
- Headers：
  - `Content-Type: application/json`
  - 除 `initialize` 以外的请求均需包含 `agent-session-id`，值为 `initialize` 响应中的 `agentSessionId`
- Body：选择 `raw`，格式 `JSON`

## 调试流程

### 1. 初始化会话（initialize）

请求体：
```json
{
  "jsonrpc": "2.0",
  "id": "init-1",
  "method": "initialize"
}
```

预期响应：`result.agentSessionId` 返回会话 ID；`result.agentSessionTtl` 表示 TTL（秒）。

### 2. 上报初始化完成（notifications/initialized）

请求体：
```json
{
  "jsonrpc": "2.0",
  "id": "init-notify-1",
  "method": "notifications/initialized"
}
```

Headers 需携带 `agent-session-id`。成功返回空的 `result`。

### 3. 发起流式任务（message/stream）

1. 在 Headers 中使用步骤 1 获取的 `agent-session-id`
2. 请求体示例：
```json
{
  "jsonrpc": "2.0",
  "id": "stream-1",
  "method": "message/stream",
  "params": {
    "id": "task-001",
    "sessionId": "session-001",
    "message": {
      "role": "user",
      "parts": [
        {
          "kind": "text",
          "text": "北京的天气如何"
        }
      ]
    }
  }
}
```
3. 在 Postman 中点击 **Save**，然后切换到 **SSE** 标签（若第一次使用需要从右上角的 `New` > `SSE Request` 创建）。
4. 启动请求后可以看到 `TaskStatusUpdateEvent`、`TaskArtifactUpdateEvent` 的 JSON 文本流。

> 调试技巧：若需要快速验证 SSE 返回内容，也可以在 `StreamingTaskService` 的测试中增加 `StepVerifier.create(flux.doOnNext(System.out::println))` 观察消息结构。

### 4. 取消任务（tasks/cancel）

请求体：
```json
{
  "jsonrpc": "2.0",
  "id": "cancel-1",
  "method": "tasks/cancel",
  "params": {
    "id": "task-001"
  }
}
```

成功响应包含 `result.status.state`，值为 `canceled`。

### 5. 清除上下文（clearContext）

请求体：
```json
{
  "jsonrpc": "2.0",
  "id": "clear-1",
  "method": "clearContext",
  "params": {
    "sessionId": "session-001"
  }
}
```

未指定 `sessionId` 时会清除整个会话上下文。

### 6. 授权登录（authorize / deauthorize）

#### authorize
```json
{
  "jsonrpc": "2.0",
  "id": "auth-1",
  "method": "authorize",
  "params": {
    "message": {
      "parts": [
        {
          "kind": "data",
          "data": {
            "authCode": "demo-auth-code"
          }
        }
      ]
    }
  }
}
```

成功返回 `result.agentLoginSessionId`。

#### deauthorize
```json
{
  "jsonrpc": "2.0",
  "id": "deauth-1",
  "method": "deauthorize",
  "params": {
    "message": {
      "parts": [
        {
          "kind": "data",
          "data": {
            "agentLoginSessionId": "<authorize 返回的 ID>"
          }
        }
      ]
    }
  }
}
```

当 ID 正确时返回空的 `result`。

## 常见问题

- **收到 `-32001 Missing agent-session-id header`**：请确认请求头携带了 `agent-session-id`。
- **Postman 无法展示 SSE**：升级 Postman 至最新版本，或改用命令行 `curl`/`hey` 等工具。
- **Mockito 动态 Agent 警告**：若在 IDEA 运行测试时提示 `java.lang.instrument.IllegalClassFormatException`，可在 Run Configuration 的 VM Options 中加入 `-XX:+EnableDynamicAgentLoading`。

## 进一步验证

执行以下命令生成覆盖率报告并检查尚未覆盖的 JSON-RPC 流程：

```bash
mvn clean test jacoco:report
```

生成的报告位于 `target/site/jacoco/index.html`。
