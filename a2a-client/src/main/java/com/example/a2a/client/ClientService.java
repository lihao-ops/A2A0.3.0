package com.example.a2a.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.example.a2a.client.jsonrpc.JsonRpcDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

/**
 * 封装调用示例 JSON-RPC 接口的逻辑，模拟第三方系统在未接入 HarmonyOS 之前的调试方式。
 * 日志中大量输出请求/响应细节，便于排查和比对华为终端侧的期望格式。
 */
@Service
public class ClientService {

    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);
    private final RestClient restClient = RestClient.create();
    private static final String URL = "http://localhost:10001/jsonrpc";

    /**
     * 调用 `agent_card` 接口获取基础元信息，常用于 Postman 或本地脚本校验配置。
     */
    public AgentCardDto getAgentCard() {
        logger.info("开始获取 AgentCard 信息，请求 URL: {}", URL);
        
        JsonRpcRequest<Object> req = new JsonRpcRequest<>();
        req.method = "agent_card";
        req.params = null;
        req.id = "card";
        
        // 使用fastjson格式化打印请求参数，禁用Unicode转义确保中文正常显示
        logger.info("发送 JSON-RPC 请求:");
        logger.info("  - Method: {}", req.method);
        logger.info("  - Params: {}", req.params == null ? "null" : JSON.toJSONString(req.params, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
        logger.info("  - ID: {}", req.id);
        logger.info("完整请求JSON: \n{}", JSON.toJSONString(req, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
        
        try {
            JsonRpcResponse<AgentCardDto> resp = restClient.post()
                    .uri(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(new ParameterizedTypeReference<JsonRpcResponse<AgentCardDto>>(){});
                    
            // 使用fastjson格式化打印响应内容，禁用Unicode转义确保中文正常显示
            logger.info("收到 JSON-RPC 响应:");
            if (resp != null) {
                logger.info("  - Response ID: {}", resp.id);
                
                if (resp.result != null) {
                    logger.info("  - Result (AgentCard): \n{}", JSON.toJSONString(resp.result, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
                    logger.info("成功获取 AgentCard: name={}, version={}", resp.result.name, resp.result.version);
                    return resp.result;
                } else if (resp.error != null) {
                    logger.error("  - Error: 错误码{}, 错误信息={}", resp.error.code, resp.error.message);
                    logger.error("获取 AgentCard 失败: Error({}): {}", resp.error.code, resp.error.message);
                    throw new RuntimeException("Error(" + resp.error.code + "): " + resp.error.message);
                }
            }
            
            logger.error("获取 AgentCard 失败: 响应为空");
            throw new RuntimeException("Empty response");
            
        } catch (Exception e) {
            logger.error("获取 AgentCard 异常: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get agent card", e);
        }
    }

    /**
     * 对应 JSON-RPC 的 `task_submit`，主要用于演示异步任务链路。
     */
    public String submitTask(String text) {
        logger.info("开始提交任务，任务内容: {}", text);
        
        JsonRpcRequest<TaskSubmitParams> req = new JsonRpcRequest<>();
        req.method = "task_submit";
        req.params = new TaskSubmitParams();
        req.params.text = text;
        req.id = "submit";
        
        // 使用fastjson格式化打印请求参数，禁用Unicode转义确保中文正常显示
        logger.info("发送 JSON-RPC 请求:");
        logger.info("  - Method: {}", req.method);
        logger.info("  - Params: \n{}", JSON.toJSONString(req.params, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
        logger.info("  - ID: {}", req.id);
        logger.info("完整请求JSON: \n{}", JSON.toJSONString(req, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
        
        try {
            JsonRpcResponse<TaskSubmitResult> resp = restClient.post()
                    .uri(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(new ParameterizedTypeReference<JsonRpcResponse<TaskSubmitResult>>(){});
                    
            // 使用fastjson格式化打印响应内容，禁用Unicode转义确保中文正常显示
            logger.info("收到 JSON-RPC 响应:");
            if (resp != null) {
                logger.info("  - Response ID: {}", resp.id);
                
                if (resp.result != null) {
                    logger.info("  - Result (TaskSubmit): \n{}", JSON.toJSONString(resp.result, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
                    logger.info("任务提交成功，获得 taskId: {}", resp.result.taskId);
                    return resp.result.taskId;
                } else if (resp.error != null) {
                    logger.error("  - Error: 错误码{}, 错误信息={}", resp.error.code, resp.error.message);
                    logger.error("提交任务失败: Error({}): {}", resp.error.code, resp.error.message);
                    throw new RuntimeException("Error(" + resp.error.code + "): " + resp.error.message);
                }
            }
            
            logger.error("提交任务失败: 响应为空");
            throw new RuntimeException("Empty response");
            
        } catch (Exception e) {
            logger.error("提交任务异常: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to submit task", e);
        }
    }

    /**
     * 轮询任务状态。返回 null 表示请求失败或服务端返回错误。
     */
    public String getTaskStatus(String taskId) {
        logger.info("开始查询任务状态，taskId: {}", taskId);
        
        JsonRpcRequest<TaskIdParams> req = new JsonRpcRequest<>();
        req.method = "task_status";
        req.params = new TaskIdParams();
        req.params.taskId = taskId;
        req.id = "status";
        
        // 使用fastjson格式化打印请求参数，禁用Unicode转义确保中文正常显示
        logger.info("发送 JSON-RPC 请求:");
        logger.info("  - Method: {}", req.method);
        logger.info("  - Params: \n{}", JSON.toJSONString(req.params, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
        logger.info("  - ID: {}", req.id);
        logger.info("完整请求JSON: \n{}", JSON.toJSONString(req, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
        
        try {
            JsonRpcResponse<TaskStatusResult> resp = restClient.post()
                    .uri(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(new ParameterizedTypeReference<JsonRpcResponse<TaskStatusResult>>(){});
                    
            // 使用fastjson格式化打印响应内容，禁用Unicode转义确保中文正常显示
            logger.info("收到 JSON-RPC 响应:");
            if (resp != null) {
                logger.info("  - Response ID: {}", resp.id);
                
                if (resp.result != null) {
                    logger.info("  - Result (TaskStatus): \n{}", JSON.toJSONString(resp.result, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
                    logger.info("任务状态查询成功，taskId: {}, state: {}", taskId, resp.result.state);
                    return resp.result.state;
                } else if (resp.error != null) {
                    logger.error("  - Error: 错误码{}, 错误信息={}", resp.error.code, resp.error.message);
                    logger.error("查询任务状态失败: 错误码{}, 错误信息={}, taskId: {}", resp.error.code, resp.error.message, taskId);
                    return null;
                }
            }
            
            logger.error("查询任务状态失败: 响应为空, taskId: {}", taskId);
            return null;
            
        } catch (Exception e) {
            logger.error("查询任务状态异常: {}, taskId: {}", e.getMessage(), taskId, e);
            return null;
        }
    }

    /**
     * 查询任务最终结果。当服务端返回错误码时抛出异常，提醒外部流程及时处理。
     */
    public String getTaskResult(String taskId) {
        logger.info("开始获取任务结果，taskId: {}", taskId);
        
        JsonRpcRequest<TaskIdParams> req = new JsonRpcRequest<>();
        req.method = "task_result";
        req.params = new TaskIdParams();
        req.params.taskId = taskId;
        req.id = "result";
        
        // 使用fastjson格式化打印请求参数，禁用Unicode转义确保中文正常显示
        logger.info("发送 JSON-RPC 请求:");
        logger.info("  - Method: {}", req.method);
        logger.info("  - Params: \n{}", JSON.toJSONString(req.params, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
        logger.info("  - ID: {}", req.id);
        logger.info("完整请求JSON: \n{}", JSON.toJSONString(req, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
        
        try {
            JsonRpcResponse<TaskResult> resp = restClient.post()
                    .uri(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(new ParameterizedTypeReference<JsonRpcResponse<TaskResult>>(){});
                    
            // 使用fastjson格式化打印响应内容，禁用Unicode转义确保中文正常显示
            logger.info("收到 JSON-RPC 响应:");
            if (resp != null) {
                logger.info("  - Response ID: {}", resp.id);
                
                if (resp.result != null) {
                    logger.info("  - Result (TaskResult): \n{}", JSON.toJSONString(resp.result, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNonStringKeyAsString));
                    String resultText = resp.result.message.parts.get(0).text;
                    logger.info("成功获取任务结果，taskId: {}, 结果长度: {} 字符", taskId, resultText.length());
                    return resultText;
                } else if (resp.error != null) {
                    logger.error("  - Error: 错误码{}, 错误信息={}", resp.error.code, resp.error.message);
                    logger.error("获取任务结果失败: Error({}): {}, taskId: {}", resp.error.code, resp.error.message, taskId);
                    throw new RuntimeException("Error(" + resp.error.code + "): " + resp.error.message);
                }
            }
            
            logger.error("获取任务结果失败: 响应为空, taskId: {}", taskId);
            throw new RuntimeException("Empty response");
            
        } catch (Exception e) {
            logger.error("获取任务结果异常: {}, taskId: {}", e.getMessage(), taskId, e);
            throw new RuntimeException("Failed to get task result", e);
        }
    }
}