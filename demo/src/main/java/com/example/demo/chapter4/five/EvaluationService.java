package com.example.demo.chapter4.five;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Mono;

public class EvaluationService {

    // 专职担任裁判职责的大模型实例，使用 AgentScope 的通用 Model 接口
    // 建议选用能力强且无自私偏见的模型（如 Qwen-Max）
    private final Model judgeModel;

    public EvaluationService(Model judgeModel) {
        this.judgeModel = judgeModel;
    }

    /**
     * 异步评估上下文的利用率（核心忠实度测试）
     * @param agentName 正在受测的业务Agent名称
     * @param injectedContext RAG系统注入的真实业务文档片段
     * @param agentResponse 业务Agent生成的最终回复
     * @return 包含评估逻辑的非阻塞响应式管道
     */
    public Mono<Void> evaluateContextUtilization(String agentName, String injectedContext, String agentResponse) {
        // 构建严格遵循 LLM-as-a-judge 最佳实践的结构化提示词
        // 强制模型采用 Chain-of-Thought 和二进制评分 [13, 15]
        String evalPromptTemplate = """
            你是一个秉公执法的企业级质量合规审查裁判。你的唯一任务是评估底层智能体生成的回复是否严格、毫无保留地基于提供的银行背景上下文。
            绝对禁止依赖你的预训练知识进行补全。
            
            【被检索到的事实背景上下文】
            %s
            
            【底层智能体生成的回复】
            %s
            
            【强制性评估步骤】
            1. 深度分析（Analysis）：提取底层智能体回复中的所有核心实体声明、数据和承诺，逐一在上述背景上下文中交叉比对，寻找确凿的支撑证据。
            2. 最终总结（Summarization）：如果在上下文中找到了所有声明的确切支撑，评分输出 1；只要发现任何一处智能体捏造了上下文中不存在的条款、步骤或承诺（即发生幻觉），评分必须输出 0。
            
            请严格遵循上述步骤，并且仅仅输出如下格式的合法JSON字符串，禁止包含任何多余的Markdown标记或解释性前缀文字：
            {
                "analysis": "你的逐步交叉比对分析过程",
                "faithfulness_score": 1 或 0
            }
            """;

        String finalPrompt = String.format(evalPromptTemplate, injectedContext, agentResponse);

        Msg evalMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(finalPrompt).build())
                .build();

        // 构建带有业务 Hook 的 ReActAgent
        ReActAgent agent = ReActAgent.builder()
                .name("CustomerService")
                .model(judgeModel)
                .sysPrompt(finalPrompt)
                .build();

        // 发起对裁判模型的请求
        return agent.call(evalMsg)
                .doOnNext(response -> {
                    String jsonResult = response.getTextContent();
                    // 提取评分并发送到可观测性基础设施
                    reportToMetricsBackend(agentName, jsonResult);
                })
                // 将可能的评估异常隔离，绝不能因为监控工具挂掉而导致主业务流崩溃
                .onErrorResume(e -> {
                    System.err.println("[Eval Error] 评估模型离线或超时: " + e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private void reportToMetricsBackend(String agentName, String jsonResult) {
        // 在实际企业应用中，这里应使用 Micrometer 或 OpenTelemetry API
        // 将 faithfulness_score 聚合至 Prometheus 或 Datadog 时序数据库
        System.out.println("\n[监控大屏] Agent: " + agentName + " | 裁判评估结果:\n" + jsonResult);
    }
}