package com.example.demo.chapter3.five;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import java.util.Arrays;
import java.util.List;

public class RagEvaluationDemo {

    public static void main(String args[]) {
        // 1. 初始化裁判大模型
        // 强烈建议在实际测试中使用参数规模较大的模型作为裁判以保证推理稳定性
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("【警告】未找到环境变量 DASHSCOPE_API_KEY，请配置后再运行测试。");
            return;
        }

        Model judgeModel = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max")
                .build();

        // 2. 实例化评估器与流水线
        FaithfulnessEvaluator evaluator = new FaithfulnessEvaluator(judgeModel);
        RagEvaluationPipeline pipeline = new RagEvaluationPipeline();

        // 3. 构建评估测试集
        List<TestCase> testCases = buildTestCases();

        // 4. 运行流水线
        try {
            pipeline.runPipeline(testCases, evaluator);
        } catch (Exception e) {
            // 当均分低于设定的 0.90 阈值时，流水线会抛出 RuntimeException 阻断执行
            System.err.println("\n" + e.getMessage());
        }
    }

    /**
     * 构建用于模拟测试的用例集
     */
    private static List<TestCase> buildTestCases() {
        // 【测试用例 1：完美的回答】
        // 表现：回答的内容100%源自检索到的上下文，无多余信息。
        // 预期：接近或等于 1.0 的高分。
        TestCase tc1 = new TestCase();
        tc1.setQuestion("云边智能客服的退款规则是什么？");
        tc1.setRetrievedContext("根据最新政策，云边智能客服系统支持用户在购买后7天内无理由全额退款。退款请求提交后，财务部门将在3个工作日内完成审核并原路退回至用户的支付账户。");
        tc1.setGeneratedAnswer("云边智能客服支持购买后7天内的无理由全额退款。您的退款款项会在财务审核通过后的3个工作日内，原路退回到支付账户。");

        // 【测试用例 2：轻微幻觉 / 引入外部知识】
        // 表现：前半句正确，但模型自作主张补充了上下文中根本没提过的“全国包邮”和“延保”信息。
        // 预期：低于 0.5 的低分（根据我们的 Prompt 规则，一处幻觉就应低分）。
        TestCase tc2 = new TestCase();
        tc2.setQuestion("这款企业级路由器的保修期是多久？");
        tc2.setRetrievedContext("企业级路由器 X900 的主机提供1年有限保修。在保修期内，非人为损坏的硬件故障可免费维修。");
        tc2.setGeneratedAnswer("企业级路由器 X900 主机提供1年有限保修，非人为损坏免费维修。此外，您还可以购买200元的延保服务，并且维修期间我们提供全国包邮服务。");

        // 【测试用例 3：严重事实篡改】
        // 表现：回答完全违背了检索到的上下文逻辑，属于极其严重的危险输出。
        // 预期：接近或等于 0.0 分。
        TestCase tc3 = new TestCase();
        tc3.setQuestion("如果我忘记了管理后台的密码怎么办？可以自己找回吗？");
        tc3.setRetrievedContext("系统暂不支持通过注册手机号或邮箱自助重置密码。出于企业安全考量，如果忘记后台密码，必须联系本公司的系统超级管理员，由超级管理员在控制台中手动为您重置。");
        tc3.setGeneratedAnswer("不用担心，如果您忘记了密码，您可以直接点击登录页面的‘忘记密码’按钮，系统会向您的注册手机号发送验证码，填写验证码即可轻松自助找回密码。");

        return Arrays.asList(tc1, tc2, tc3);
    }
}