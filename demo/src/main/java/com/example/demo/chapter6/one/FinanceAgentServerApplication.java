package com.example.demo.chapter6.one;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {Neo4jAutoConfiguration.class, io.agentscope.spring.boot.nacos.AgentscopeNacosPromptAutoConfiguration.class})
public class FinanceAgentServerApplication {

    public static void main(String args[]) {
        SpringApplication.run(FinanceAgentServerApplication.class, args);
        System.out.println("=== 财务风控智能体 A2A 服务端已随 Tomcat 在 8080 端口启动 ===");
    }

    @Bean
    public ReActAgent financeAgent() {
        // 1. 初始化真实的 LLM（请替换为你真实的阿里云百炼 API-KEY）
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-1xxxxx")
                .modelName("qwen-plus")
                .build();

        // 2. 构建 Agent
        // 注意：这里的 name("finance-agent") 必须与客户端查找的名称完全一致，
        // 这样客户端才能通过 Nacos 正确路由到这个实例。
        return ReActAgent.builder()
                .name("finance-agent")
                .sysPrompt("你是一个专业财务风控智能体。面对用户的退款查询，如果用户没有给出16位系统订单号，请回复要求提供订单号以核查退款。")
                .model(model)
                .build();
    }
}