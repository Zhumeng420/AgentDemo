package com.example.demo.chapter2.five;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.message.Msg;

import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;


/**
 * 智能客服专属的自定义大语言模型裁判指标，基于G-Eval范式实现慢思考架构
 */
public class CustomerServiceJudgeMetric {
    private static final Logger log = LoggerFactory.getLogger(CustomerServiceJudgeMetric.class);
    private final Model judgeModel;

    // 采用"先分析，后总结"的慢思考提示词设计
    private static final String JUDGE_PROMPT_TEMPLATE = """
        你是一名公正的资深质量保证（QA）评估专家，负责评估电商AI智能客服的回复质量。
        
        请基于提供的对话历史和预期标准结果，评估智能体的回复。
        
        1. 事实正确性 (1-5分): 智能体是否根据公司政策准确解决了用户问题且没有产生幻觉？
        2. 语调 (1-5分): 回复是否高度专业、简洁（不拖泥带水）且富有同理心？
        3. 工具执行 (1-5分): 智能体是否正确理解了上下文并拉取或更新了订单状态？
        
        用户查询：%s
        待评估的智能体回复：%s
        真实基准参考：%s
        
        [执行步骤]
        1. 识别用户查询的核心意图。
        2. 将智能体回复与真实基准进行对比，检查是否存在事实偏差。
        3. 分析冗余度。如果回复过长，请扣分。
        4. 将发现的任何错误分类为以下分类之一：可忽略(Negligible)、轻微(Small)、严重(Major)、致命(Fatal)。完美无瑕则使用无(None)。
        5. 将观察结果综合为一个最终的整数得分（1到5分）。
        
        [输出格式约束]
        你必须只返回一个严格符合以下模式的有效JSON对象，不要包含任何Markdown格式块：
        {
          "score": <1到5的整数>,
          "reasoning": "<最多100字的逐步分析推理过程>",
          "errorCategory": "<Negligible|Small|Major|Fatal|None>"
        }
        """;

    public CustomerServiceJudgeMetric(Model judgeModel) {
        this.judgeModel = judgeModel;
    }

    /**
     * 响应式评估方法，返回Mono以支持高并发非阻塞调用
     */
    public Mono<EvaluationResult> evaluate(String query, String agentResponse, String groundTruth) {
        // 组装评估提示词
        String prompt = String.format(JUDGE_PROMPT_TEMPLATE, query,  agentResponse, groundTruth);

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(prompt).build())
                .build();

        ReActAgent agent = ReActAgent.builder()
                .name("CustomerServiceJudgeMetric")
                .sysPrompt(prompt)
                .model(judgeModel)
                .maxIters(1)
                .build();

        return agent.call(userMsg).map(response -> response.getTextContent())
                .doOnNext(content -> log.debug("裁判原始JSON输出: "+ content))
                .flatMap(this::parseJsonResult)
                // 强大的错误处理：防止由于JSON解析失败导致整个流崩溃
                .onErrorResume(e -> {
                    log.error("解析裁判输出失败。模型可能幻觉了格式: {}", e.getMessage());
                    // 返回一个致命错误结果以熔断测试
                    return Mono.just(new EvaluationResult(0, "因违反格式规范导致解析失败。", "Fatal"));
                });
    }

    /**
     * 将大语言模型生成的JSON字符串反序列化为Java对象
     */
    private Mono<EvaluationResult> parseJsonResult(String jsonContent) {
        try {
            // 在实际生产代码中，应注入Jackson ObjectMapper实例
            // 这里为了演示逻辑，假设将其映射到记录类
            // return Mono.just(objectMapper.readValue(jsonContent, EvaluationResult.class));

            // 伪代码替代：
            return Mono.just(new EvaluationResult(4, "智能体准确解决了问题，但略显冗长。", "Small"));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("JSON解析异常", e));
        }
    }

    public static void main(String[] args) {
        // 1. 创建一个模拟的 DashScopeChatModel（避免真实调用 API）
        DashScopeChatModel mockModel = DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max") // 意图识别与复杂格式控制对逻辑推理要求极高，故选用高阶的 max 级模型
                .formatter(new DashScopeChatFormatter())
                .build();

        // 2. 实例化裁判类
        CustomerServiceJudgeMetric judgeMetric = new CustomerServiceJudgeMetric(mockModel);

        // 3. 构造测试数据
        String query = "我的订单为什么还没发货？";
        String agentResponse = "您的订单正在仓库拣货，预计今天下午6点前发货，请您耐心等待。";
        String groundTruth = "订单已拣货完毕，将于今日18:00前发出，物流单号将短信通知。";

        // 4. 调用评估方法并阻塞等待结果（演示用）
        EvaluationResult result = judgeMetric.evaluate(query,agentResponse, groundTruth)
                .block();  // 在 main 线程中同步等待

        // 5. 打印评估结果
        System.out.println("===== 客服回复评估结果 =====");
        System.out.println("综合得分: " + result.getScore());
        System.out.println("错误等级: " + result.getErrorCategory());
        System.out.println("推理过程: " + result.getReasoning());
    }

}