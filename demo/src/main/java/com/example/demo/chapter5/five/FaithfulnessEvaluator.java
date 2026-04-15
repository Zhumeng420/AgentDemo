package com.example.demo.chapter5.five;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.StructuredOutputReminder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FaithfulnessEvaluator {

    private final Model judgeModel;
    private final ObjectMapper mapper = new ObjectMapper();
    // 预定义的严格裁判提示词
    private static final String JUDGE_SYS_PROMPT =
            "你是一个RAG系统事实一致性评估的严苛裁判。请仔细核对生成的回答中的每一个信息点是否均源自检索上下文。" +
                    "严禁回答中出现任何外部知识或编造的幻觉内容。必须且仅输出合法的JSON对象，包含 'reasoning' (字符串) 和 'score' (0.0到1.0的浮点数)。";

    public FaithfulnessEvaluator(Model judgeModel) {
        // 仅保留无状态的 Model 实例引用。不要在这里实例化全局唯一的 Agent。
        this.judgeModel = judgeModel;
    }

    /**
     * 对单条用例进行事实一致性评估
     */
    public EvalResult evaluate(TestCase testCase) {
        // 【关键修正】：AgentScope 的 Agent 是有状态的（内部持有 memory 和运行状态 checkRunning）。
        // 在多线程并发流水线中，如果多个线程调用同一个 Agent 实例的 call() 方法，会引发并发冲突。
        // 因此，必须在 evaluate() 内部为每个请求实例化独立的、线程隔离的裁判分身 。
        ReActAgent judgeAgent = ReActAgent.builder()
                .name("FaithfulnessJudge")
                .sysPrompt(JUDGE_SYS_PROMPT)
                .model(this.judgeModel)
                // 开启结构化输出提醒，强迫模型在系统提示词末尾追加 JSON 约束
                .structuredOutputReminder(StructuredOutputReminder.PROMPT)
                // 评估是一次性的单轮推理任务，直接返回结果，不需要多轮 Action-Observation 循环
                .maxIters(1)
                .build();

        String promptTemplate = "【检索上下文】:\n%s\n\n【系统生成回答】:\n%s\n\n请进行事实一致性评估。";
        String content = String.format(promptTemplate, testCase.getRetrievedContext(), testCase.getGeneratedAnswer());

        Msg requestMsg = Msg.builder()
                .name("CI_Pipeline")
                .textContent(content)
                .build();

        try {
            // 在评估流水线中，通常使用同步阻塞调用获取裁判模型的反馈
            Msg responseMsg = judgeAgent.call(requestMsg).block();

            // 提取并解析大模型返回的JSON结构
            String jsonText = extractJsonBlock(responseMsg.getTextContent());
            return mapper.readValue(jsonText, EvalResult.class);

        } catch (Exception e) {
            log.error("大模型裁判JSON解析失败或调用超时，用例容灾处理。原因: {}", e.getMessage());
            // 解析失败时的悲观降级处理：直接给予0分，迫使人工介入查看
            return new EvalResult("JSON Parsing Failed or Timeout due to LLM unstable output.", 0.0);
        }
    }

    /**
     * 辅助解析方法：提取大模型可能包裹在 Markdown 语法 ```json... ``` 中的文本块
     */
    private String extractJsonBlock(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}