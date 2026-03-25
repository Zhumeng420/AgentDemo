package com.example.demo.chapter2.two;

import com.example.demo.chapter2.one.OrderQueryTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;

import java.time.Duration;

public class AgentScopeDemo {

    private static String apikey = "sk-xxxxxxxxxxxxxxxxxxxxxxx";

    public static void main(String args[]) {
        System.out.println("初始化客服智能体系统...");

        // 1.装配 Toolkit
        ToolkitConfig config = ToolkitConfig.builder()
                // 开启并行执行开关：允许大模型一次思考输出多个工具调用指令时（如同时查物流和推荐），系统底层并发执行它们，显著降低长尾延迟
                .parallel(true)
                .build();
        Toolkit toolkit = new Toolkit(config);



        // 2. 注册并映射工具到指定分组
        // 框架会自动运用反射引擎遍历 CustomerServiceTools 内的所有 @Tool 方法并生成标准 JSON Schema
        toolkit.registration()
                .tool(new CustomerServiceTools())
                .apply();

        // 在实际复杂工程中，由于 CustomerServiceTools 类里包含三个不同业务维度的工具
        // 开发规约通常建议按领域切分为独立的 Tool 类，以方便像下面这样精细地挂载到不同组中：
        // 3. 建立按业务领域划分的虚拟工具分组
        // toolkit.createToolGroup("core_after_sales", "核心高频售后服务功能", true); // 设定为默认激活状态
        // toolkit.createToolGroup("advanced_recommendation", "高算力消耗的商品推荐服务", false); // 设定为默认静默关闭状态，不在 LLM 的 System Prompt 中显示
        //toolkit.registration().tool(new OrderQueryTools()).group("core_after_sales").apply();
        // toolkit.registration().tool(new trackLogistics()).group("advanced_recommendation").apply();

        // 4.构建当前会话的安全上下文
        // 假设当前登录的用户ID为 "ORD-U77"，这样查询 "ORD-U778899" 就能通过权限校验
        String currentUserId = "ORD-U77";
        ToolExecutionContext executionContext = ToolExecutionContext.builder()
                .register(new UserContext(currentUserId))
                .build();// 1. 大模型推理层配置：由于仅仅是查询获取Token，可以允许相对宽容的超时策略并支持多次退避重试

        // 3. 工具执行层配置：坚守防御性编程，严格控制长尾阻滞，绝不轻易重试以防不可逆的业务副作用污染
        ExecutionConfig modelConfig = ExecutionConfig.builder()
                .timeout(Duration.ofMinutes(1)) // 设定单次响应最长忍耐1分钟
                .maxAttempts(3)                 // 失败允许进行最多3轮重试
                .build();

        ExecutionConfig toolConfig = ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(15)) // 工具调用极限容忍度降至15秒
                .maxAttempts(1)                  // 不提供任何自动重试机制
                .build();


        // 5.初始化并构建 ReActAgent
        ReActAgent supportAgent = ReActAgent.builder()
                .name("CloudEdge_Support_Agent")
                .modelExecutionConfig(modelConfig)
                .toolExecutionConfig(toolConfig)
                .sysPrompt("您是云边商城的资深高级智能客服专员。处理用户诉求时，请务必秉持以下原则：\n" +
                        "1. 涉及事实型问题（如订单状态、物流轨迹），必须优先使用您配备的系统工具进行精准查询，绝不可凭借模型内部参数随意捏造幻觉。\n" +
                        "2. 回复语言需专业、热情且富有同理心。\n" +
                        "3. 若系统工具返回异常信息，请如实告知用户，并提供安抚话术。")
                .model(DashScopeChatModel.builder()
                        .apiKey(apikey)
                        .modelName("qwen-plus")
                        .build())
                .toolkit(toolkit)
                .toolExecutionContext(executionContext)
                .build();

        // 6.模拟用户提问并执行
        String userQuery = "帮我查一下我的订单 ORD-U778899 的物流卡在哪里了？什么时候派送？这太慢了！";
        System.out.println("\n用户提问: " + userQuery);
        System.out.println("Agent思考及执行中...\n");

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(userQuery).build())
                .build();

        // 7.在主函数（main）或测试用例中允许使用.block() 来阻塞等待异步结果返回
        Msg response = supportAgent.call(userMsg).block();

        System.out.println("\n客服回复: " + response.getTextContent());
    }
}