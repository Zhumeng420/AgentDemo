package com.example.demo.chapter3.one;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.DashScopeChatModel;
import java.time.Duration;

public class CustomerServiceAgentFactory {

    /**
     * 工厂方法：基于当前用户会话构建具备订单查询能力的专属客服Agent
     */
    public static ReActAgent createOrderQueryAgent(UserSessionContext currentSession, Model llmModel) {

        // pre初始化连接
        StudioManager.init()
                .studioUrl("http://localhost:3000") // Studio 的后端接收端口
                .project("客服系统调试")
                .runName("工具调用测试")
                .initialize()
                .block();

        // 1. 初始化Toolkit容器并注册我们编写的查询工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new OrderQueryTools());

        // 2. 注册动态上下文（Context Injection）
        // 框架底层的调度器在反射调用工具方法时，会自动从该容器中寻找匹配类型的实例注入
        ToolExecutionContext context = ToolExecutionContext.builder()
                .register(currentSession)
                .build();

        // 3. 配置工具调用的运行时执行策略（容错与重试）
        ExecutionConfig toolConfig = ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(15))
                .maxAttempts(2) // 允许发生特定网络异常时最多尝试2次
                .initialBackoff(Duration.ofSeconds(1))
                .build();

        // 4. 构建并返回完备的ReActAgent
        return ReActAgent.builder()
                .name("PremiumOrderAssistant")
                .sysPrompt("你是一家高端电子品牌的客服代表，富有同理心且高效。你的主要目标是利用提供的工具协助用户解决订单查询问题。请始终保持简明礼貌。如果工具调用失败，绝对禁止编造或猜测任何订单数据。")
                .model(llmModel)
                .toolkit(toolkit)
                .toolExecutionContext(context)
                .toolExecutionConfig(toolConfig)
                .hook(new StudioMessageHook(StudioManager.getClient()))
                .maxIters(5) // 生产环境安全底线：严格限制循环次数，阻断死循环风险[15, 28]
                .build();
    }


    // 提供本地直接调试验证的 main 方法
    public static void main(String args[]) {
        // A. 准备模型配置 (运行时请确保已配置环境变量 DASHSCOPE_API_KEY)
        String apiKey = "xxxxxxxxxxxx";
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("WARN: 请配置 DASHSCOPE_API_KEY 环境变量后重试！");
            return;
        }


        Model model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max")
                .build();

        // B. 模拟当前系统的登录用户状态
        UserSessionContext session = new UserSessionContext("USER-10086");

        // C. 构建并初始化Agent
        ReActAgent agent = createOrderQueryAgent(session, model);

        // D. 模拟用户消息并触发Agent调用
        System.out.println("====== 开始会话 ======");
        System.out.println("用户: 帮我查一下昨天买的那个手机发货了没，订单号是 ORD-98765432\n");

        Msg userMsg = Msg.builder()
                .textContent("帮我查一下昨天买的那个手机发货了没，订单号是 ORD-98765432")
                .build();

        // ⚠️ 最佳实践警示：在真实的服务层(Controller/Service)中，应该利用 flatMap 延续响应式流。
        //.block() 仅作为本地 main 方法测试或特定同步过渡环境中的妥协用法[25, 29]。
        Msg response = agent.call(userMsg).block();

        System.out.println("\nAgent回复: \n" + response.getTextContent());
        System.out.println("====== 会话结束 ======");
    }
}