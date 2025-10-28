package com.example.a2a.server.core;

import com.example.a2a.server.agent.WeatherAgent;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 基于内存的数据结构实现的轻量任务编排器，复用示例 {@link WeatherAgent} 来模拟任务生命周期。
 * 主要用于 JSON-RPC 演示，和 HarmonyOS 流式实现相互独立，便于替换为真实的异步任务引擎。
 */
@Service
public class TaskService {

    /**
     * 任务状态数据结构，保存当前执行信息。
     */
    public static class TaskData {
        public String taskId;
        public volatile String state; // 可取值：SUBMITTED、RUNNING、COMPLETED、FAILED、CANCELED
        public String inputText;
        public String resultText; // 简化为文本，返回时再包装为 JSON-RPC Message/Part 结构
        public volatile boolean cancelRequested;
    }

    private final Map<String, TaskData> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final WeatherAgent weatherAgent;

    /**
     * 注入示例天气 Agent。
     *
     * @param weatherAgent 天气查询实现
     */
    public TaskService(WeatherAgent weatherAgent) {
        this.weatherAgent = weatherAgent;
    }

    /**
     * 接收到 JSON-RPC 的 `task_submit` 请求后进入此流程：创建任务、异步执行并持续更新
     * 任务状态。真实业务可在此触发华为侧工作流或自定义线程池。
     */
    public TaskData submit(String text) {
        TaskData data = new TaskData();
        data.taskId = UUID.randomUUID().toString();
        data.inputText = text;
        data.state = "SUBMITTED";
        tasks.put(data.taskId, data);

        executor.submit(() -> runTask(data));
        return data;
    }

    /**
     * 后台线程执行任务：模拟耗时、检查取消标记并生成结果。
     *
     * @param data 当前任务数据
     */
    private void runTask(TaskData data) {
        if (data.cancelRequested) {
            data.state = "CANCELED";
            return;
        }
        data.state = "RUNNING";
        try {
            // 模拟处理时间
            Thread.sleep(300);
            if (data.cancelRequested) {
                data.state = "CANCELED";
                return;
            }
            String result = weatherAgent.search(data.inputText);
            data.resultText = result;
            data.state = "COMPLETED";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            data.state = "FAILED";
        } catch (Exception e) {
            data.state = "FAILED";
        }
    }

    /**
     * 根据任务标识获取最新的任务状态。
     *
     * @param taskId 任务标识
     * @return 任务数据，未找到返回 {@code null}
     */
    public TaskData get(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 支持通过 `task_cancel` 请求标记任务取消。HarmonyOS 流程不会调用该接口，可视实际
     * 场景决定是否保留。
     */
    public boolean cancel(String taskId) {
        TaskData data = tasks.get(taskId);
        if (data == null) return false;
        if ("COMPLETED".equals(data.state) || "FAILED".equals(data.state)) return false;
        data.cancelRequested = true;
        return true;
    }
}