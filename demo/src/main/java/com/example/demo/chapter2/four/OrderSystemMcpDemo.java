package com.example.demo.chapter2.four;


import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;

import java.time.Duration;


@SpringBootApplication
public class OrderSystemMcpDemo {

    private static String apikey = "sk-034c7aa31c7f44b18e8f27c0119b72ac";

    public static void main(String args[]) {
        // 1. 启动 Spring Boot，底层会自动将标注了 @McpTool 的方法暴露为 MCP 服务 [2, 1]
        SpringApplication.run(OrderSystemMcpDemo.class, args);
    }

    // ==========================================
    // 角色 A：企业后端的微服务（MCP Server端）
    // ==========================================
    @Service
    public class OrderBusinessService {
        // 业务研发只需加上这两个注解，无需编写 Controller
        @McpTool(description = "通过精确的订单号查询用户的核心订单状态、承运商名称以及预计的送达时间。当用户询问物流状态时必须调用此工具。")
        public String queryOrderLogisticsStatus(@McpToolParam(description = "企业内部订单流水号，必须包含ORD-前缀，例如 'ORD-2026-991'", required = true) String orderId) {
            System.out.println("\n>>> [企业后端微服务] 收到大模型的请求！正在查询数据库，订单号: " + orderId);

            // 模拟真实的业务数据查询逻辑
            if ("ORD-2026-991".equals(orderId)) {
                return "【系统实时返回】订单号: ORD-2026-991 | 支付状态: 已支付 | 物流承运商: 顺丰速运 | 当前节点: 北京分拨中心 | 预计送达: 明天上午";
            }
            return "数据库中未检索到该订单，请提示用户核实订单号是否拼写错误。";
        }
    }

    // ==========================================
    // 角色 B：AI 研发团队的智能体（MCP Client端）
    // ==========================================
    @Bean
    public CommandLineRunner runAgentScopeClient() {
        return args -> {
            System.out.println("==================================================");
            System.out.println(" 微服务已就绪，正在初始化无状态 HTTP MCP 客户端...");
            try {
                // 1. 发起网络请求，连接到刚才启动的本地 MCP 端点 [3]
                McpClientWrapper orderMcpClient = McpClientBuilder.create("enterprise-order-mcp")
                        .streamableHttpTransport("http://localhost:8080/mcp")
                        .timeout(Duration.ofSeconds(45))
                        .buildAsync()
                        .block();

                // 2. 注册并拉取微服务暴露的全部工具 [3]
                Toolkit toolkit = new Toolkit();
                toolkit.registerMcpClient(orderMcpClient).block();
                System.out.println(" 成功感知到企业微服务工具，当前可用列表：");
                toolkit.getToolNames().forEach(t -> System.out.println(" -> " + t));


                ReActAgent agent = ReActAgent.builder()
                        .name("OrderCustomerService")
                        .sysPrompt("你是专业的企业智能客服。请优先使用提供的MCP工具查询订单信息，并将枯燥的系统返回数据转化为口语化的自然语言回复用户。")
                        .model(DashScopeChatModel.builder()
                                .apiKey(apikey) // 严格遵守配置分离的安全准则
                                .modelName("qwen-max")
                                .build()) // 传入实际配置
                        .toolkit(toolkit)
                        .memory(new InMemoryMemory())

                        .build();
                String userQuery = "帮我查一下昨天买的那个键盘发货没有，单号是ORD-2026-991";
                System.out.println("\n[用户提问] " + userQuery);

                Msg userRequest = Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder()
                                .text(userQuery)
                                .build())
                        .build();

                // 触发 Agent 的观察、思考与行动闭环
                Msg finalResponse = agent.call(userRequest).block();
                System.out.println("\n[Agent 最终回复] " + finalResponse.getTextContent());

            } catch (Exception e) {
                System.err.println("MCP 客户端调用失败: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}
