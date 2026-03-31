package com.example.demo.chapter2.three;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.plan.PlanNotebook;
import java.time.Duration;

public class ComplexTransactionAgentBuilder {
    private static String apikey = "sk-xxxxxxxxxxxxx";

    public static void main(String args[]) {
        // 1. 初始化Toolkit并向其挂载商业功能工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new CommerceTools());

        // 2. 初始化ToolExecutionContext，构建强安全边界，确保用户上下文不外泄
        UserContext loggedInUser = new UserContext("USR-9981", "VIP");
        ToolExecutionContext toolCtx = ToolExecutionContext.builder()
                .register(loggedInUser)
                .build();

        // 3. 构建高可用执行配置，切断底层自动重试，保护交易接口幂等性
        ExecutionConfig toolConfig = ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(15)) // 设定合理的网络I/O宽容度
                .maxAttempts(1)                  // 【生产底线】禁止自动重试写入操作 [20, 29]
                .build();

        // 4. 激活PlanNotebook组件以确保多阶段任务的严密追踪
        PlanNotebook notebook = PlanNotebook.builder()
                // 框架在底层默认使用InMemoryPlanStorage和DefaultPlanToHint，无需显式引入
                .maxSubtasks(5) // 控制计划切分粒度，防止过度拆解导致上下文Token耗尽
                .build();

        // 5. 编排并实例化最终的ReActAgent系统
        ReActAgent salesAgent = ReActAgent.builder()
                .name("SalesCopilot")
                .sysPrompt("你是一名精英购物助手。对于对比任务，请务必先严格搜索所有请求的商品，在内部比较价格，最后执行用户的最终指令。请循序渐进地思考，并严格更新你的任务笔记以跟踪进度。")
                .model(DashScopeChatModel.builder()
                        .apiKey(apikey) // 严格遵守配置分离的安全准则
                        .modelName("qwen-max")
                        .build())
                .toolkit(toolkit)
                .toolExecutionContext(toolCtx)
                .toolExecutionConfig(toolConfig)
                .planNotebook(notebook)
                .maxIters(8) // 预留充裕的Reasoning-Acting重试轮次以应对多工具调用与自我修正
                .build();

        // 6. 构造包含明确意图的复合用户指令
        Msg userRequest = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder()
                        .text("请帮我对比iPhone 15和Galaxy S24的当前价格，并直接为我下单售价较低的那款。")
                        .build())
                .build();

        System.out.println("正在启动自主智能体工作流...");

        // 发起调用并订阅结果（仅在非生产主线程的测试入口使用.block()，核心业务链路需维持全异步流 ）
        Msg finalResponse = salesAgent.call(userRequest).block();

        System.out.println("工作流已完成。智能体最终响应：\n" + finalResponse.getTextContent());
    }
}