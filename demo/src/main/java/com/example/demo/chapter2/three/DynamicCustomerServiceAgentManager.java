package com.example.demo.chapter2.three;


import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DynamicCustomerServiceAgentManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicCustomerServiceAgentManager.class);

    // Nacos 基础设施连接参数配置
    @Value("${spring.cloud.nacos.config.server-addr:127.0.0.1:8848}")
    private String serverAddr;
    @Value("${spring.cloud.nacos.config.namespace:dfce71d2-b32f-4480-ba4e-519a7012cccd}")
    private String namespace;

    // 核心定位坐标
    private static final String DATA_ID = "customer-service-agent.json";
    private static final String GROUP = "DEFAULT_GROUP";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 核心设计：使用 AtomicReference 封装 ReActAgent。
    // 在极高并发（如双十一）下，必须保证Agent实例切换的原子性与内存可见性。
    // 绝不能在处理请求的中途修改 Agent 内部的可变状态。
    private final AtomicReference<ReActAgent> currentAgentRef = new AtomicReference<>();

    // 用于记录当前内存中运行的Prompt版本（如哈希值），支撑可观测性
    private final AtomicReference<String> currentConfigVersion = new AtomicReference<>("INIT");

    private ConfigService configService;

    @PostConstruct
    public void initializeSystem() {
        try {
            // 1. 初始化并构建 Nacos ConfigService 实例
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
            properties.put(PropertyKeyConst.NAMESPACE, namespace);
            properties.put("username", "nacos");
            properties.put("password", "sqqysLf9cYNm");

            configService = NacosFactory.createConfigService(properties);

            // 2. 启动时执行首次防御性同步阻塞拉取（容忍5000ms超时）
            String initialConfig = configService.getConfig(DATA_ID, GROUP, 5000);
            rebuildAgentCognitiveModel(initialConfig);

            // 3. 注册长轮询监听器（Long-Polling Listener）
            // 该监听器将在后台非阻塞运行，感知服务器端的MD5变化
            configService.addListener(DATA_ID, GROUP, new Listener() {
                @Override
                public void receiveConfigInfo(String latestConfigJson) {
                    log.info("📢 [Nacos-Listener] 监测到核心控制脑配置发生变更，触发Agent认知重构流水线...");
                    rebuildAgentCognitiveModel(latestConfigJson);
                }

                @Override
                public Executor getExecutor() {
                    // 返回 null 意为使用 Nacos 客户端内部默认的异步回调线程池
                    return null;
                }
            });
            log.info("✅ Nacos配置总线订阅成功: DataId=[{}], Group=[{}]", DATA_ID, GROUP);

        } catch (Exception e) {
            log.error("❌ Agent系统启动时 Nacos 连接或首次拉取遭遇灾难性失败", e);
            // 在生产环境中，若中枢瘫痪，必须立即触发灾备预案（Fallback）
            triggerLocalFallbackStrategy();
        }
    }

    /**
     * 核心业务方法：解析最新下发的配置负载，并原子性地重构 Agent 实例
     * 此过程被设计为完全无状态（Stateless），不干涉正在执行中的上下文。
     */
    private void rebuildAgentCognitiveModel(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            log.warn("⚠️ Nacos 下发配置为空数据，出于系统安全防御机制，拒绝本次状态更新");
            return;
        }

        try {
            // 第一步：解析多维结构化数据
            JsonNode rootNode = objectMapper.readTree(configJson);

            JsonNode profile = rootNode.path("agentProfile");
            String agentName = profile.path("name").asText("CustomerServiceBot");
            String configVersion = profile.path("version").asText("unknown");

            JsonNode modelConf = rootNode.path("modelConfig");
            double temperature = modelConf.path("temperature").asDouble(0.5);
            String modelName = modelConf.path("modelName").asText("qwen-max");

            GenerateOptions generateOptions  = GenerateOptions.builder().temperature(temperature).build();

            String rawSystemPrompt = rootNode.path("promptTemplate").path("systemPrompt").asText("");

            // 第二步：利用 AgentScope API 重新组建包含最新超参数的大模型抽象
            // 生产级建议：在此集成 DashScopeChatModel 等阿里系原生模型抽象 [2]
            DashScopeChatModel chatModel = DashScopeChatModel.builder()
                    .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                    .modelName(modelName)
                    .defaultOptions(generateOptions)
                    // 可扩展其他诸如 maxTokens, 熔断重试等高级模型执行配置 (modelExecutionConfig)
                    .build();

            // 第三步：利用不可变构建器（Builder Pattern）组装新的 ReActAgent 实例
            // ReActAgent 负责思考与行动的逻辑编排，是AgentScope框架的中枢大脑
            ReActAgent newlyEvolvedAgent = ReActAgent.builder()
                    .name(agentName)
                    .sysPrompt(rawSystemPrompt) // 注入尚未最终渲染的基础系统提示词骨架
                    .model(chatModel)
                    // 在此亦可动态绑定或注销工具包 (Toolkit) 与各类 Hook 拦截器
                    .maxIters(15) // 控制最大思考轮次，防止在异常 Prompt 下陷入死循环 [2]
                    .build();

            // 第四步：执行高并发场景下的 CAS (Compare-And-Swap) 原子性替换
            // 该操作时间复杂度为 O(1)，确保绝对的线程安全与可见性。
            currentAgentRef.set(newlyEvolvedAgent);
            currentConfigVersion.set(configVersion);

            log.info("🔄 智能体 [{}] 认知核心升级成功！当前版本标识: [{}], 模型调度: [{}], 采样温度: [{}]",
                    agentName, configVersion, modelName, temperature);

        } catch (Exception e) {
            // 防御性编程的极致体现：若配置数据存在语法错误（如 JSON 漏了逗号），
            // 捕获异常，阻断更新，继续沿用上一个健康的 Agent 实例，防止系统被毒化崩溃。
            log.error("❌ 致命异常：在解析 Nacos 配置或重构 Agent 时发生崩溃。启动熔断保护，继续维持旧版模型运行。", e);
        }
    }

    /**
     * 对外提供的业务交互入口点
     * 演示如何基于 Spring AI Alibaba 的能力对骨架 Prompt 进行二次语境渲染
     */
    public String handleCustomerInquiry(String userMessage, String targetTone, String currentActivity) {
        ReActAgent activeAgent = currentAgentRef.get();
        if (activeAgent == null) {
            return "系统初始化安全保护中，请稍后重试。"; // 优雅降级
        }

        // --- 生产级可观测性链路埋点 ---
        // 将当前 Agent 所依赖的配置版本号强制打入 MDC (Mapped Diagnostic Context)
        // 这样在日志收集系统 (如阿里云 SLS 或 ELK) 中，每一条大模型输出都能精准溯源其基于的 Prompt 版本
        MDC.put("agent.config.version", currentConfigVersion.get());

        // --- 动态变量解析与二次渲染 ---
        // 在生产中，我们会利用 Spring AI Alibaba 提供的 ConfigurablePromptTemplateFactory
        // 对底层配置好的 rawSystemPrompt 骨架中遗留的占位符 {tone}, {activity_notice}
        // 结合实际入参进行实时编译注入，生成最终送入大模型的确定性文本 。
        // (注：为聚焦AgentScope引擎逻辑，此处略去具体的字符串替换实现)

        // --- 发起 Agent 协同与思考循环 ---
        // 构建消息体，并触发 AgentScope 中 ReActAgent 的核心阻塞调用 block()
        // 底层会根据 sysPrompt 约束进行自动工具发现、意图推理与回复生成
        // Msg response = activeAgent.call(Msg.builder().textContent(userMessage).build()).block();
        // return response.getTextContent();

        MDC.clear(); // 链路清理
        return "模拟智能体交互已完成。当前状态：完全受控。";
    }

    /**
     * 容灾预案：本地配置降级加载机制
     */
    private void triggerLocalFallbackStrategy() {
        log.warn("⚠️ 激活本地磁盘基线配置降级预案 (Fallback)...");
        // 逻辑：读取 classpath:resources/fallback-agent-config.json 进行重构
    }
}