package com.example.demo.chapter4.five;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;

import java.util.List;
import java.util.UUID;


public class AgentBootstrap {
    public static void main(String args[]) {

        // 新增：创建一个同步锁用于阻塞主线程
        java.util.concurrent.CountDownLatch evalLatch = new java.util.concurrent.CountDownLatch(1);

        // 1. 初始化专门用于打分的独立裁判模型
        // 生产环境绝对要求：必须将裁判模型的 temperature 彻底归零，以切断其生成随机文本的倾向
        Model judgeModel = DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max")
                // AgentScope 1.0 规范：使用 defaultOptions 构建参数，取消了硬编码的 temperature() API
                .defaultOptions(GenerateOptions.builder().temperature(0.0).build())
                .build();

        EvaluationService evalService = new EvaluationService(judgeModel);
        ContextObservabilityHook obsHook = new ContextObservabilityHook(evalService,evalLatch);

        // 2. 初始化服务于实际业务流的生成模型
        Model bizModel = DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .build();

        // 3. 构建装甲强化后的客服智能体
        ReActAgent customerServiceAgent = ReActAgent.builder()
                .name("BankRefundAgent")
                .sysPrompt("你是专业的银行信用卡客服。请始终且仅仅基于前置检索注入的《退款政策文件》回答客户问题。")
                .model(bizModel)
                .hooks(List.of(obsHook)) // 动态挂载上下文分析追踪切面
                .build();

        String simulatedRagContext = "【退款政策文件】：对于海外信用卡争议交易，我行不提供实时到账退款。款项将在30-45个工作日内原路退回。";

        Msg userMsg = Msg.builder()
                .textContent(simulatedRagContext + "\n\n用户提问：我昨天在海外刷卡发生重复扣款，你们的海外争议交易退款是立刻到账吗？")
                .build();

        System.out.println("--- 开始处理用户请求 ---");

        // 4. 发起调用：核心修复！
        // 必须通过 contextWrite 将 TraceID 显式写入响应式流，确保它跨越所有的异步线程切换！
        Msg response = customerServiceAgent.call(userMsg)
                .contextWrite(reactor.util.context.Context.of("X-B3-TraceId", "trace-" + UUID.randomUUID().toString()))
                .block();

        System.out.println("\n[最终响应] 业务智能体回复: " + response.getTextContent());

        try {
            // 最长等待 60 秒，防止因网络问题导致一直卡住
            evalLatch.await(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("--- 流程结束 ---");
        System.exit(0); // 确保所有后台守护线程和 HTTP 客户端彻底关闭
    }
}