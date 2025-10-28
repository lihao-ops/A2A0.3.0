package com.example.a2a.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A2A 示例服务端入口，启动 Spring Boot 应用。
 */
@SpringBootApplication
public class A2AServerApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(A2AServerApplication.class, args);
    }
}