package com.example.demo.chapter2.four;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.message.Msg;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

public class IntentRouterService {

    private static final Logger log = LoggerFactory.getLogger(IntentRouterService.class);
    private final Model lightweightModel; // 推荐使用响应极快的轻量级模型进行分解
    private final ObjectMapper mapper = new ObjectMapper();

    public IntentRouterService(Model lightweightModel) {
        this.lightweightModel = lightweightModel;
    }

    /**
     * 将复杂原始输入转换为结构化任务规划
     */
    public List<Map<String, Object>> decomposeUserQuery(String originalQuery) {
        log.info("Initiating Query Decomposition for input length: {}", originalQuery.length());

        // 步骤1：Prompt渲染
        String prompt = DecompositionPrompt.DECOMPOSE_TEMPLATE.replace("{original_query}", originalQuery);

        try {
            // 步骤2：调用大模型执行Prompt链的第一环
            ReActAgent intentClassifierAgent = ReActAgent.builder()
                    .name("lightweightModel")
                    .sysPrompt(prompt)
                    .model(lightweightModel)
                    // 性能调优：作为一个纯粹的文本解析与分类 Agent，它不需要调用外部工具或进行多轮反思，
                    // 因此将 maxIters 严格限制为 1，强行阻断不必要的 ReAct 思考循环，极大降低系统延迟。
                    .maxIters(1)
                    .build();

            Msg responseMsg =  intentClassifierAgent.call(
                    Msg.builder().textContent(prompt).build()
            ).block();

            // 步骤3：容错清理，剔除可能附带的Markdown包裹符 ```json
            String cleanJson = responseMsg.getTextContent().replaceAll("```json|```", "").trim();

            // 步骤4：反序列化为Java结构
            List<Map<String, Object>> tasks = mapper.readValue(
                    cleanJson, new TypeReference<List<Map<String, Object>>>() {}
            );

            log.info("Successfully decomposed query into {} atomic tasks.", tasks.size());
            return tasks;

        } catch (Exception e) {
            log.error("Query decomposition failed.", e);
            throw new RuntimeException("意图拆解引擎异常", e);
        }
    }
}