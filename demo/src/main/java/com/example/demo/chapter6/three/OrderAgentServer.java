package com.example.demo.chapter6.three;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {Neo4jAutoConfiguration.class,io.agentscope.spring.boot.nacos.AgentscopeNacosPromptAutoConfiguration.class})
public class OrderAgentServer {

    public static void main(String args[]) {
        SpringApplication.run(OrderAgentServer.class, args);
        System.out.println("=== 订单处理智能体 A2A 服务端已随 Tomcat 在 8080 端口启动 ===");
    }

    @Bean
    public ReActAgent orderAgent() {
        // 1. 初始化真实的 LLM（请替换为你真实的阿里云百炼 API-KEY）
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-xxxxxxxxxxxxx")
                .modelName("qwen-plus")
                .stream(true)
                .build();

        // 2. 构建 Agent
        // 注意：这里的 name("order-agent") 必须与客户端查找的名称完全一致，
        // 这样客户端才能通过 Nacos 正确路由到这个实例。
        return ReActAgent.builder()
                .name("order-agent")
                .sysPrompt("你是一个专业的订单处理专家，负责核实系统的订单状态。只允许回复客观的订单数据，不要加入主观情绪。当用户询问订单状态时，必须提供准确的订单信息，包括订单号、状态、物流进度等。支持分布式事务处理，能够与主管智能体和退款智能体协同工作。")
                .model(model)
                .build();
    }
}