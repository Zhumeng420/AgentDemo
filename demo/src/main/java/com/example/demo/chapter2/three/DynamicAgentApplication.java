package com.example.demo.chapter2.three;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

// 排除 Neo4jAutoConfiguration 自动配置，避免版本冲突导致的启动失败
@SpringBootApplication(exclude = {Neo4jAutoConfiguration.class})
public class DynamicAgentApplication {

    public static void main(String args[]) {
        // 1. 启动 Spring Boot 应用上下文
        ConfigurableApplicationContext context = SpringApplication.run(DynamicAgentApplication.class, args);

        // 2. 从 Spring 容器中获取已完成初始化（并自动拉取了 Nacos 配置）的 Manager 实例
        DynamicCustomerServiceAgentManager manager = context.getBean(DynamicCustomerServiceAgentManager.class);

        // 3. 模拟业务调用并打印执行结果
        System.out.println("\n========== Agent 模拟交互开始 ==========");
        System.out.println("用户提问: 我的双十一订单怎么还没发货？");

        // 传入模拟参数：用户问题、期望的语气、当前的活动通知上下文
        String response = manager.handleCustomerInquiry(
                "我的双十一订单怎么还没发货？",
                "热情且富有同理心",
                "双十一大促物流高峰预警：部分订单预计延迟1-2天"
        );

        System.out.println("\nAgent 执行结果返回:");
        System.out.println(response);
        System.out.println("========== Agent 模拟交互结束 ==========\n");

        // 提示：为了测试 Nacos 动态修改 Prompt 的热更新效果，
        // 这里不调用 context.close()，让应用保持存活状态以便监听长连接变更。
    }
}