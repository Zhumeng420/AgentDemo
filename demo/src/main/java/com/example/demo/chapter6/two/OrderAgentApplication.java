package com.example.demo.chapter6.two;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.transport.DeploymentProperties;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;
import io.agentscope.core.nacos.a2a.registry.NacosAgentRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Properties;

/**
 * 订单与售后智能体应用启动类
 *
 * 本应用同时注册两个 Agent 到 Nacos A2A Registry：
 * 1. order-agent：订单业务专家，负责查询订单状态和物流信息
 * 2. after-sales-agent：售后理赔专家，负责处理退款、换货等售后请求
 *
 * 由于 AgentscopeA2aAutoConfiguration 只支持单个 Agent 注册，
 * 因此需要禁用自动配置，手动创建 AgentScopeA2aServer 来注册多个 Agent。
 */
@SpringBootApplication(exclude = {
        Neo4jAutoConfiguration.class,
        io.agentscope.spring.boot.nacos.AgentscopeNacosPromptAutoConfiguration.class,
        io.agentscope.spring.boot.a2a.AgentscopeA2aAutoConfiguration.class , // 禁用自动配置以支持多 Agent
        io.agentscope.spring.boot.nacos.AgentscopeNacosReActAgentAutoConfiguration.class
})
public class OrderAgentApplication {

    private static final String NACOS_SERVER_ADDR = "127.0.0.1:8848";
    private static final String NACOS_USERNAME = "nacos";
    private static final String NACOS_PASSWORD = "woTZnqgBs1V8";

    public static void main(String args[]) throws InterruptedException {
        SpringApplication.run(OrderAgentApplication.class, args);
        System.out.println("====== 智能体已成功启动并注册至 Nacos A2A Registry ======");
        System.out.println("====== 主线程将持续运行以保持服务可用 ======");
        // 阻塞主线程，防止应用退出
        Thread.currentThread().join();
    }

    /**
     * 创建 Nacos A2A 注册表配置
     * 用于后续构建 AgentRegistry 以支持多个 Agent 的注册
     */
    @Bean
    public NacosAgentRegistry nacosMutiAgentRegistry() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, NACOS_SERVER_ADDR);
        properties.put(PropertyKeyConst.USERNAME, NACOS_USERNAME);
        properties.put(PropertyKeyConst.PASSWORD, NACOS_PASSWORD);

        AiService aiService = AiFactory.createAiService(properties);

        NacosA2aRegistryProperties registryProperties = NacosA2aRegistryProperties.builder()
                .setAsLatest(true)
                .enabledRegisterEndpoint(true)
                .overwritePreferredTransport("http")
                .build();

        return NacosAgentRegistry.builder(aiService)
                .nacosA2aProperties(registryProperties)
                .build();
    }


    /**
     * 订单专家的 A2A 服务器
     * 将 order-agent 注册到 Nacos A2A Registry，使其可以被远程调用
     */
    @Bean
    public AgentScopeA2aServer orderAgentServer(NacosAgentRegistry nacosAgentRegistry) {

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-xxxxxxxxxxxxxxx")
                .modelName("qwen-plus")
                .build();
        ReActAgent.Builder  builder = ReActAgent.builder()
                .name("order-agent")  // 修正：应该是 order-agent，不是 after-sales-agent
                .sysPrompt("你高级订单业务专家，精通通过订单号、手机号等信息精准提取订单的当前状态、物流进度详情。在用户提及物流去向时必须优先调用本专家。" )
                .model(model);

        return AgentScopeA2aServer.builder( builder)
                .deploymentProperties(new DeploymentProperties.Builder()
                        .host("127.0.0.1")
                        .port(8081)
                        .build())
                .withAgentRegistry(nacosAgentRegistry)
                .build();
    }

    /**
     * 售后专家的 A2A 服务器
     * 将 after-sales-agent 注册到 Nacos A2A Registry，使其可以被远程调用
     */
    @Bean
    public AgentScopeA2aServer afterSalesAgentServer(NacosAgentRegistry nacosAgentRegistry) {

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-xxxxxxxxxxxxxxx")
                .modelName("qwen-plus")
                .build();
        ReActAgent.Builder  builder  = ReActAgent.builder()
                .name("after-sales-agent")  // 修正：应该是 after-sales-agent，不是 order-agent
                .sysPrompt("高权限售后理赔专家，专注于处理由于退货、丢件等异常状态引发的订单退款及换货申请操作。该专家具备直接发起资金逆向回滚的行动能力。" )
                .model(model);

        return AgentScopeA2aServer.builder(builder)
                .deploymentProperties(new DeploymentProperties.Builder()
                        .host("127.0.0.1")
                        .port(8082)
                        .build())
                .withAgentRegistry(nacosAgentRegistry)
                .build();
    }

    /**
     * 启动订单专家 A2A 服务器
     */
    @Bean
    public CommandLineRunner orderAgentServerStarter(AgentScopeA2aServer orderAgentServer) {
        return args -> {
            orderAgentServer.postEndpointReady();
            System.out.println("[order-agent] A2A 服务器已在 8081 端口启动");
        };
    }

    /**
     * 启动售后专家 A2A 服务器
     */
    @Bean
    public CommandLineRunner afterSalesAgentServerStarter(AgentScopeA2aServer afterSalesAgentServer) {
        return args -> {
            afterSalesAgentServer.postEndpointReady();
            System.out.println("[after-sales-agent] A2A 服务器已在 8082 端口启动");
        };
    }
}