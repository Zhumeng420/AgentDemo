package com.example.demo.chapter4.four;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;

public class ImplicitToolExtractionExample {

    // 定义工具：注意参数列表！大模型不需要传入 orderId
    static class LogisticsTool {

        // 我们在 @Tool 描述中明确告诉大模型：不需要传入任何参数
        @Tool(name = "get_current_logistics", description = "查询用户当前活跃订单的物流状态。大模型调用时不需要传递任何参数，系统会自动读取。")
        public String getLogistics(UserOrderContext ctx) {
            // 这个 ctx 参数是大模型不可见的，它由 AgentScope 框架在底层隐式自动注入
            String orderId = ctx.getRealOrderId();

            System.out.println("\n>>> [后台日志] 工具被触发！Java 底层已隐式提取到真实的单号：" + orderId + "，正在请求数据库...");

            // 模拟数据库查询返回
            return "订单 (" + orderId + ") 的物流状态：已由顺丰速运揽收，当前正在运输途中。";
        }
    }

    public static void main(String args[]) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误：请先设置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        // A. 假设我们从登录网关获取到了当前用户的复杂订单号
        String complexOrderId = "ORD-5F2A-89BC-XYZ-2026";

        // B. 构建工具执行上下文，将业务对象注册进去
        // 这就相当于把元数据（Metadata）挂载到了框架的环境中
        ToolExecutionContext toolCtx = ToolExecutionContext.builder()
                .register(new UserOrderContext(complexOrderId))
                .build();

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new LogisticsTool());

        // C. 构建 Agent
        ReActAgent agent = ReActAgent.builder()
                .name("CustomerService")
                .model(DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName("qwen-max")
                        .build())
                // 提示词里只需要一笔带过，不需要把复杂的单号全拼进去
                .sysPrompt("你是云边智能客服。当用户询问物流时，请直接调用 get_current_logistics 工具。")
                .toolkit(toolkit)
                // 关键点：将包含业务数据的上下文挂载到 Agent 上
                .toolExecutionContext(toolCtx)
                .build();

        // D. 模拟用户提问
        Msg userMsg = Msg.builder()
                .name("User")
                .role(MsgRole.USER)
                .textContent("帮我查一下我刚才买的东西发货没？")
                .build();

        System.out.println("User: " + userMsg.getTextContent());
        System.out.println("Agent 思考与执行中...");

        // E. 执行调用
        Msg response = agent.call(userMsg).block();

        System.out.println("\nAgent: " + response.getTextContent());
    }
}