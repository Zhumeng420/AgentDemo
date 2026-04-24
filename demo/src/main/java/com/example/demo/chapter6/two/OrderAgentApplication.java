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

import java.util.List;
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
    private static final String NACOS_PASSWORD = "lQacRbWitl7J";

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
     * 
     * 【安全机制】
     * - 验证请求消息中的签名和时间戳
     * - 拒绝过期或无效签名的请求
     */
    @Bean
    public AgentScopeA2aServer orderAgentServer(NacosAgentRegistry nacosAgentRegistry) {

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-dbb3b7a481ef4a30bc3b39f4105bdb86")
                .modelName("qwen-plus")
                .build();
        
        String orderAgentSysPrompt = "你高级订单业务专家，精通通过订单号、手机号等信息精准提取订单的当前状态、物流进度详情。在用户提及物流去向时必须优先调用本专家。\n" +
                "\n" +
                "【安全验证 - 重要】\n" +
                "当收到【安全请求】格式的查询时，你必须：\n" +
                "1. 从消息中提取【签名:xxx】和时间戳\n" +
                "2. 使用共享密钥验证签名有效性\n" +
                "3. 检查时间戳是否在5分钟有效期内\n" +
                "4. 验证通过后才执行查询，否则返回'请求验证失败'\n" +
                "\n" +
                "【签名算法】\n" +
                "- 共享密钥: a2a-secure-key-2024-supervisor\n" +
                "- 签名格式: HMAC-SHA256(订单号:时间戳, 密钥) -> Base64\n" +
                "- 示例: 数据 \"20260421001:1234567890\" 的签名为对应计算结果\n" +
                "\n" +
                "【查询逻辑】\n" +
                "- 如果请求格式正确且验证通过，返回订单状态信息\n" +
                "- 如果是普通请求（非安全请求格式），直接处理查询\n" +
                "- 订单 20260421001 的状态：已发货，物流在途，预计2天后送达\n";
        
        ReActAgent.Builder  builder = ReActAgent.builder()
                .name("order-agent")
                .sysPrompt(orderAgentSysPrompt)
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
     * 
     * 【安全机制】
     * - 验证请求消息中的签名和时间戳
     * - 拒绝过期或无效签名的请求
     */
    @Bean
    public AgentScopeA2aServer afterSalesAgentServer(NacosAgentRegistry nacosAgentRegistry) {

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-dbb3b7a481ef4a30bc3b39f4105bdb86")
                .modelName("qwen-plus")
                .build();
        
        String afterSalesSysPrompt = "高权限售后理赔专家，专注于处理由于退货、丢件等异常状态引发的订单退款及换货申请操作。该专家具备直接发起资金逆向回滚的行动能力。\n" +
                "\n" +
                "【安全验证 - 重要】\n" +
                "当收到【安全请求】格式的工单时，你必须：\n" +
                "1. 从消息中提取【签名:xxx】和时间戳\n" +
                "2. 使用共享密钥验证签名有效性\n" +
                "3. 检查时间戳是否在5分钟有效期内\n" +
                "4. 验证通过后才创建工单，否则返回'请求验证失败，拒绝处理'" + "\n" +
                "\n" +
                "【签名算法】\n" +
                "- 共享密钥: a2a-secure-key-2024-supervisor\n" +
                "- 签名格式: HMAC-SHA256(订单号:原因:时间戳, 密钥) -> Base64\n" +
                "- 示例: 数据 \"20260421001:屏幕坏点:1234567890\" 的签名为对应计算结果\n" +
                "\n" +
                "【工单逻辑】\n" +
                "- 如果请求格式正确且验证通过，创建售后工单\n" +
                "- 如果是普通请求（非安全请求格式），直接处理工单\n" +
                "- 工单格式: WO-订单号-时间戳后6位\n";
        
        ReActAgent.Builder  builder  = ReActAgent.builder()
                .name("after-sales-agent")
                .sysPrompt(afterSalesSysPrompt)
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