package com.example.demo.chapter3.three;



import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.user.UserAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.integration.bailian.BailianConfig;
import io.agentscope.core.rag.integration.bailian.BailianKnowledge;
import io.agentscope.core.rag.integration.bailian.RerankConfig;
import io.agentscope.core.rag.integration.bailian.RewriteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 *
 * 此代码基于 AgentScope Java 构建企业级客服 RAG 智能体，
 * 演示了 意图改写 -> 混合检索 -> RRF融合 -> 交叉编码器重排序 全流水线技术。
 */
public class AdvancedCustomerServiceRAGAgent {
    private static final Logger logger = LoggerFactory.getLogger(AdvancedCustomerServiceRAGAgent.class);

    public static void main(String args[]) {
        // 1. 严格的安全密钥环境检查
        String accessKeyId = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET");
        String workspaceId = System.getenv("BAILIAN_WORKSPACE_ID");
        String indexId = System.getenv("BAILIAN_INDEX_ID");
        String dashScopeApiKey = System.getenv("DASHSCOPE_API_KEY");

        if (accessKeyId == null || dashScopeApiKey == null) {
            logger.error("系统环境异常: 缺失核心鉴权配置，请检查云环境凭据变量。");
            System.exit(1);
        }

        // 2. 装配包含全链路优化特性的云端知识库引擎配置
        BailianConfig retrievalConfig = BailianConfig.builder()
                .accessKeyId(accessKeyId)
                .accessKeySecret(accessKeySecret)
                .workspaceId(workspaceId)
                .indexId(indexId)
                // 【核心优化1】：开启混合检索，词法和语义各捕获Top-100特征
                .denseSimilarityTopK(100)
                .sparseSimilarityTopK(100)
                // 【核心优化2】：启用对话上下文意图识别与大模型改写流水线
                .enableRewrite(true)
                .rewriteConfig(RewriteConfig.builder()
                        .modelName("conv-rewrite-qwen-1.8b") // 使用极速小模型进行意图重写
                        .build())
                // 【核心优化3】：挂载交叉编码器重排序，剔除底层混合引擎返回的伪相关碎片
                .enableReranking(true)
                .rerankConfig(RerankConfig.builder()
                        .modelName("gte-rerank-hybrid")
                        .rerankMinScore(0.38f) // 严格的截断基线阈值
                        .rerankTopN(5)         // 提炼最核心的5条记录输送给主代理
                        .build())
                .build();

        BailianKnowledge enterpriseKnowledge = BailianKnowledge.builder()
                .config(retrievalConfig)
                .build();

        // 3. 构建基于 ReAct 范式的高级客服智能体
        ReActAgent serviceAgent = ReActAgent.builder()
                .name("EnterpriseSupportAgent")
                .sysPrompt("您是一名资深客户服务支持专家。必须严格依据内部系统与知识库工具提供的信息来解答用户疑问，对于未检索到的信息，请直接致歉并转接人工，绝对禁止凭空捏造（幻觉）。")
                .model(DashScopeChatModel.builder()
                        .apiKey(dashScopeApiKey)
                        .modelName("qwen-max")
                        // 生产环境建议开启流式输出提升首字节响应时间(TTFB)
                        .stream(true)
                        .build())
                .knowledge(enterpriseKnowledge)
                // 采用 Agentic 工具模式，使模型具备“按需决定”是否要翻阅手册的自由裁量权
                .ragMode(RAGMode.AGENTIC)
                .build();

        UserAgent userAgent = UserAgent.builder().name("ClientTerminal").build();

        logger.info("企业级智能客服系统已初始化完毕，全链路 RAG 检索引擎就绪。");

        // 4. 用户交互循环。注：此处使用.block() 仅作为控制台程序的主线程阻塞保持手段。
        // 在实际的 Spring WebFlux 服务层开发中，应始终返回 Mono<Msg> 并由上层框架订阅消费。
        Msg contextMsg = null;
        while (true) {
            contextMsg = userAgent.call(contextMsg).block();
            if (contextMsg!= null && "exit".equalsIgnoreCase(contextMsg.getTextContent())) {
                logger.info("接收到终端下线信号，会话终止。");
                break;
            }

            // 将会话记录通过响应式链式操作送入 Agent 处理
            contextMsg = serviceAgent.call(contextMsg)
                    // 生产环境必备的响应式异常穿透与容错降级降级机制
                    .onErrorResume(e -> {
                        logger.error("检索与模型推理阶段发生崩溃: {}", e.getMessage());
                        return Mono.just(Msg.builder()
                                .role(io.agentscope.core.message.MsgRole.ASSISTANT)
                                .textContent("抱歉，当前后端系统负载过高或知识库连接超时，请稍后重试。")
                                .build());
                    })
                    .block();
        }
    }
}
