package com.example.demo.chapter2.five;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import reactor.test.StepVerifier;

class PromptRegressionTest {

    private static ReActAgent testAgent;
    private static CustomerServiceJudgeMetric evaluator;

    @BeforeAll
    static void setup() {
        // 生产安全规范：从系统环境变量或密钥管理服务中加载，绝对禁止硬编码
        String dashScopeKey = System.getenv("DASHSCOPE_API_KEY");

        // 实例化业务智能体所使用的生成模型
        DashScopeChatModel generator = DashScopeChatModel.builder()
                .apiKey(dashScopeKey)
                .modelName("qwen-plus")
                // 提供对多厂商架构的抽象包装格式化器
                .formatter(new DashScopeChatFormatter())
                .build();

        GenerateOptions generateOptions  = GenerateOptions.builder().temperature(0.0).build();
        // 实例化专门用于裁判的模型（通常选择能力更强的模型），必须设置temperature=0.0确保确定性
        DashScopeChatModel judgeModel = DashScopeChatModel.builder()
                .apiKey(dashScopeKey)
                .modelName("qwen-max")
                .defaultOptions(generateOptions )
                .formatter(new DashScopeChatFormatter())
                .build();

        // 注入当前正在研发/优化中的系统提示词
        testAgent = ReActAgent.builder()
                .name("CustomerService")
                .sysPrompt("你是一个智能电商助手。请用不超过50字的简明语言解决问题。")
                .model(generator)
                // 作为专门进行测试的客服Agent场景，若不需要多轮工具循环，限制maxIters提高效率
                .maxIters(1)
                .build();

        evaluator = new CustomerServiceJudgeMetric(judgeModel);
    }

    /**
     * 参数化测试：利用JUnit从CSV读取真实的基准测试数据集，执行批量并发评估
     */
    @ParameterizedTest(name = "测试用例 {0}: {1}")
    @CsvFileSource(resources = "/testcases/customer_service_eval_dataset.csv", numLinesToSkip = 1)
    void testPromptQualityWithoutRegression(String caseId, String query, String expectedGt) {

        // 封装标准请求结构
        Msg userMessage = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(query).build())
                .build();

        // 构建响应式处理流：执行智能体 -> 截获最终结果 -> 发送给裁判进行评分分类
        var evaluationMono = testAgent.call(userMessage)
                .flatMap(agentResponse ->
                        evaluator.evaluate(query,  agentResponse.getTextContent(), expectedGt)
                );

        // 使用Reactor提供的StepVerifier优雅地验证异步响应式流的终态，避免阻塞线程
        StepVerifier.create(evaluationMono)
                .assertNext(result -> {
                    System.out.printf("用例 %s 评估完成。得分: %d, 分类: %s, 推理过程: %s%n",
                            caseId, result.getScore(), result.getErrorCategory(), result.getReasoning());

                    // 质量门禁1：评分低于4分则认为提示词出现严重退化，测试失败
                    assertTrue(result.getScore() >= 4,
                            "检测到提示词质量退化！分数降至 " + result.getScore() +
                                    "。请根据裁判建议修复: " + result.getReasoning());

                    // 质量门禁2：绝对不能出现致命级别的严重错误（如幻觉、敏感信息泄漏）
                    assertNotEquals("Fatal", result.getErrorCategory(),
                            "提示词评估触发了致命(Fatal)错误分类！");
                })
                .verifyComplete();
    }
}