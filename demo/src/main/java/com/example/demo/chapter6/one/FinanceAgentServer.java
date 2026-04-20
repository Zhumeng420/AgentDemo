package com.example.demo.chapter6.one;

import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.transport.DeploymentProperties;
import com.alibaba.nacos.api.PropertyKeyConst;
import io.agentscope.core.a2a.server.transport.jsonrpc.JsonRpcTransportWrapper;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;
import io.agentscope.core.nacos.a2a.registry.NacosAgentRegistry;

import java.util.Properties;

public class FinanceAgentServer {
    public static void main(String args[]) throws Exception {
        System.out.println("=== 启动财务风控智能体 A2A 服务端 ===");

        // 1. 初始化 Nacos 客户端建立链接
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        properties.put(PropertyKeyConst.USERNAME, "nacos");
        properties.put(PropertyKeyConst.PASSWORD, "zhumeng420");
        AiService aiService = AiFactory.createAiService(properties);

        // 2. 编排 Nacos A2A 注册中心心跳及端点策略参数
        NacosA2aRegistryProperties registryProperties = NacosA2aRegistryProperties.builder()
                .setAsLatest(true)
                .enabledRegisterEndpoint(true)
                .overwritePreferredTransport("http")
                .build();
        NacosAgentRegistry agentRegistry = NacosAgentRegistry.builder(aiService)
                .nacosA2aProperties(registryProperties)
                .build();

        // 3. 构建核心业务逻辑：本地 ReAct 智能体
        AgentScopeA2aServer server = AgentScopeA2aServer.builder(
                        ReActAgent.builder()
                                .name("finance-agent")
                                .sysPrompt("你是一个专业财务风控智能体。面对用户的退款查询，如果用户没有给出16位系统订单号，请回复要求提供订单号以核查退款。")
                                .description("退款风控智能体")
                                .model(DashScopeChatModel.builder()
                                        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                                        .modelName("qwen-plus")
                                        .stream(false)
                                        .build())
                )
                .deploymentProperties(new DeploymentProperties.Builder()
                        .host("127.0.0.1")
                        .port(8080)
                        .build())
                .withAgentRegistry(agentRegistry)
                .build();


        // 5. 触发端点就绪事件，正式对外提供 HTTP JSON-RPC 接口与 Nacos 心跳发布
        server.postEndpointReady();
        System.out.println("[系统] 财务风控智能体已在 8080 端口启动，并成功发布 Agent Card 至 Nacos (127.0.0.1:8848)。");

        // 阻塞主线程以持续提供服务
        Thread.currentThread().join();
    }
}