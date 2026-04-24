package com.example.demo.chapter6.three;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {Neo4jAutoConfiguration.class,io.agentscope.spring.boot.nacos.AgentscopeNacosPromptAutoConfiguration.class})
public class RefundAgentServer {

    public static void main(String args[]) {
        SpringApplication.run(RefundAgentServer.class, args);
        System.out.println("=== 退款处理智能体 A2A 服务端已随 Tomcat 在 8080 端口启动 ===");
    }

    @Bean
    public ReActAgent refundAgent() {
        // 1. 初始化真实的 LLM（请替换为你真实的阿里云百炼 API-KEY）
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-xxxxxxxxxx")
                .modelName("qwen-plus")
                .stream(true)
                .build();

        // 2. 构建 Agent
        // 注意：这里的 name("refund-agent") 必须与客户端查找的名称完全一致，
        // 这样客户端才能通过 Nacos 正确路由到这个实例。
        return ReActAgent.builder()
                .name("refund-agent")
                .sysPrompt("你是财务处理Agent。在退款失败时，你必须根据传入的异常代码，自主决定是重试还是发放等额代金券进行补偿。支持分布式事务处理，能够与主管智能体和订单智能体协同工作，执行Saga事务的补偿步骤。")
                .model(model)
                .build();
    }
}