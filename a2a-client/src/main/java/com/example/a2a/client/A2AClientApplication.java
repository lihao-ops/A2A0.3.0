package com.example.a2a.client;

import com.example.a2a.client.jsonrpc.JsonRpcDtos.AgentCardDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * A2A 示例客户端入口，通过命令行任务模拟完整调用流程。
 */
@SpringBootApplication
public class A2AClientApplication {

    private static final Logger logger = LoggerFactory.getLogger(A2AClientApplication.class);

    /**
     * 启动命令行客户端。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        logger.info("启动 A2A 客户端应用程序");
        org.springframework.context.ConfigurableApplicationContext ctx =
                new org.springframework.boot.builder.SpringApplicationBuilder(A2AClientApplication.class)
                        .web(org.springframework.boot.WebApplicationType.NONE)
                        .run(args);
        logger.info("A2A 客户端应用程序已退出");
        SpringApplication.exit(ctx);
    }

    /**
     * 注册命令行执行入口，依次调用各个测试流程。
     */
    @Bean
    CommandLineRunner runner(ClientService clientService) {
        return args -> {
            logger.info("=== 开始执行 A2A 完整测试流程 ===");
            
            // 测试场景 1: 获取 AgentCard
            testGetAgentCard(clientService);
            
            // 测试场景 2: 天气查询任务
            testWeatherQuery(clientService, "北京");
            
            // 测试场景 3: 另一个城市的天气查询
            testWeatherQuery(clientService, "上海");
            
            // 测试场景 4: 英文城市查询
            testWeatherQuery(clientService, "New York, NY");
            
            // 测试场景 5: 测试错误处理（无效任务ID）
            testErrorHandling(clientService);
            
            logger.info("=== A2A 完整测试流程执行完毕 ===");
        };
    }
    
    /**
     * 测试获取 AgentCard 功能
     * 
     * 预期行为说明:
     * 1. 发送 get_agent_card 请求 (无参数)
     *    - 正常返回: JSON-RPC 成功响应，包含 AgentCard 对象
     *    - 响应内容: name, description, version, protocolVersion, skills 等字段
     *    - 客户端行为: 成功解析并显示 AgentCard 信息
     */
    private void testGetAgentCard(ClientService clientService) {
        logger.info("--- 测试场景 1: 获取 AgentCard ---");
        logger.info("发送 get_agent_card 请求 - 预期返回: AgentCard 对象信息");
        try {
            AgentCardDto card = clientService.getAgentCard();
            if (card != null) {
                logger.info("✓ AgentCard 获取成功:");
                logger.info("  - 名称: {}", card.name);
                logger.info("  - 描述: {}", card.description);
                logger.info("  - 版本: {}", card.version);
                logger.info("  - 协议版本: {}", card.protocolVersion);
                if (card.skills != null && !card.skills.isEmpty()) {
                    logger.info("  - 技能数量: {}", card.skills.size());
                    card.skills.forEach(skill -> 
                        logger.info("    * {}: {}", skill.name, skill.description)
                    );
                }
            } else {
                logger.error("✗ AgentCard 获取失败");
            }
        } catch (Exception e) {
            logger.error("✗ AgentCard 获取过程中发生异常", e);
        }
        logger.info("");
    }
    
    /**
     * 测试天气查询任务
     * 
     * 预期行为说明:
     * 1. 发送 task_submit 请求，参数为查询文本 (如 "北京天气")
     *    - 正常返回: JSON-RPC 成功响应，包含 taskId
     *    - 客户端行为: 获取 taskId 并开始轮询
     * 
     * 2. 轮询 task_status 请求，参数为 taskId
     *    - 正常返回: JSON-RPC 成功响应，包含任务状态 (RUNNING -> COMPLETED)
     *    - 客户端行为: 持续轮询直到状态变为 COMPLETED
     * 
     * 3. 发送 task_result 请求，参数为 taskId
     *    - 正常返回: JSON-RPC 成功响应，包含天气查询结果
     *    - 响应内容: 包含天气信息的文本 (如 "Weather in Beijing: Sunny 25°C")
     *    - 客户端行为: 显示天气查询结果
     */
    private void testWeatherQuery(ClientService clientService, String location) {
        logger.info("--- 测试场景: 天气查询 - {} ---", location);
        try {
            // 1. 提交任务 - 预期返回: taskId
            logger.info("发送 task_submit 请求，查询内容: {} - 预期返回: taskId", location);
            String taskId = clientService.submitTask(location);
            if (taskId == null) {
                logger.error("✗ 任务提交失败，位置: {}", location);
                return;
            }
            logger.info("✓ 任务提交成功，taskId: {}", taskId);
            
            // 2. 轮询任务状态 - 预期: RUNNING -> COMPLETED
            logger.info("开始轮询任务状态 - 预期状态变化: RUNNING -> COMPLETED");
            String state = pollTaskStatus(clientService, taskId, 30, 500);
            if (state == null) {
                logger.error("✗ 任务状态查询失败或超时，taskId: {}", taskId);
                return;
            }
            
            // 3. 获取任务结果 - 预期返回: 天气信息文本
            if ("COMPLETED".equals(state)) {
                logger.info("发送 task_result 请求 - 预期返回: 天气查询结果文本");
                String result = clientService.getTaskResult(taskId);
                if (result != null) {
                    logger.info("✓ 任务执行成功，结果: {}", result);
                } else {
                    logger.error("✗ 任务结果获取失败，taskId: {}", taskId);
                }
            } else if ("FAILED".equals(state)) {
                logger.error("✗ 任务执行失败，taskId: {}", taskId);
                // 尝试获取错误信息
                String errorResult = clientService.getTaskResult(taskId);
                if (errorResult != null) {
                    logger.error("  错误详情: {}", errorResult);
                }
            } else {
                logger.warn("? 任务状态未知: {}，taskId: {}", state, taskId);
            }
            
        } catch (Exception e) {
            logger.error("✗ 天气查询测试过程中发生异常，位置: {}", location, e);
        }
        logger.info("");
    }
    
    /**
     * 轮询任务状态直到完成或超时
     * 
     * 预期行为说明:
     * 1. 循环发送 task_status 请求，参数为 taskId
     *    - 正常返回: JSON-RPC 成功响应，包含任务状态字符串
     *    - 可能的状态: "RUNNING", "COMPLETED", "FAILED"
     *    - 客户端行为: 持续轮询直到状态为 "COMPLETED" 或 "FAILED"
     * 
     * 2. 轮询间隔控制
     *    - 每次轮询间隔 intervalMs 毫秒
     *    - 最大轮询次数 maxPolls 次，防止无限等待
     *    - 达到最终状态时立即停止轮询
     */
    private String pollTaskStatus(ClientService clientService, String taskId, int maxPolls, long intervalMs) {
        logger.info("开始轮询任务状态，最大轮询次数: {}, 间隔: {}ms", maxPolls, intervalMs);
        
        for (int i = 0; i < maxPolls; i++) {
            try {
                logger.info("发送第 {} 次 task_status 请求 - 预期返回: 任务状态字符串", i + 1);
                String state = clientService.getTaskStatus(taskId);
                if (state == null) {
                    logger.warn("第 {} 次轮询失败，taskId: {}", i + 1, taskId);
                    continue;
                }
                
                logger.info("第 {} 次轮询结果: {}", i + 1, state);
                
                if ("COMPLETED".equals(state) || "FAILED".equals(state)) {
                    logger.info("任务状态已确定: {}，停止轮询", state);
                    return state;
                }
                
                if (i < maxPolls - 1) { // 不是最后一次轮询
                    Thread.sleep(intervalMs);
                }
                
            } catch (InterruptedException e) {
                logger.warn("轮询过程被中断", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("第 {} 次轮询发生异常，taskId: {}", i + 1, taskId, e);
            }
        }
        
        logger.warn("轮询超时或失败，taskId: {}", taskId);
        return null;
    }
    
    /**
     * 测试错误处理
     * 
     * 预期行为说明:
     * 1. 发送无效 taskId 的状态查询请求
     *    - 正常返回: JSON-RPC 错误响应，错误码 -32004，错误信息 "Task not found"
     *    - 客户端行为: 抛出 RuntimeException，包含错误码和错误信息
     * 
     * 2. 发送无效 taskId 的结果查询请求  
     *    - 正常返回: JSON-RPC 错误响应，错误码 -32004，错误信息 "Task not found"
     *    - 客户端行为: 抛出 RuntimeException，包含错误码和错误信息
     * 
     * 注意: 这些异常是预期的正常行为，用于验证错误处理机制的正确性
     */
    private void testErrorHandling(ClientService clientService) {
        logger.info("--- 测试场景: 错误处理 ---");
        try {
            // 测试无效的 taskId - 预期会收到 JSON-RPC 错误响应
            String invalidTaskId = "invalid-task-id-12345";
            logger.info("测试无效 taskId: {} (预期: 服务器返回错误码 -32004)", invalidTaskId);
            
            logger.info("发送状态查询请求 - 预期返回: 错误响应 'Task not found'");
            String status = clientService.getTaskStatus(invalidTaskId);
            logger.info("无效 taskId 状态查询结果: {}", status);
            
            logger.info("发送结果查询请求 - 预期返回: 错误响应 'Task not found'");
            String result = clientService.getTaskResult(invalidTaskId);
            logger.info("无效 taskId 结果查询结果: {}", result);
            
        } catch (Exception e) {
            logger.error("✗ 错误处理测试过程中发生异常", e);
        }
        logger.info("");
    }
}