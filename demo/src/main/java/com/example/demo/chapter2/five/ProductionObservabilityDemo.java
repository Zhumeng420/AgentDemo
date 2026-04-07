package com.example.demo.chapter2.five;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import io.micrometer.observation.ObservationRegistry;


import java.util.List;

public class ProductionObservabilityDemo {

    // ==========================================
    // 3. 应用入口 (Main函数)
    // ==========================================
    public static void main(String args[]) {
        // a. 初始化 Micrometer 观察注册表
        ObservationRegistry observationRegistry = ObservationRegistry.create();

        // b. 配置模型 (请确保运行时已配置系统环境变量 DASHSCOPE_API_KEY)
        String apiKey = "sk-xxxxxxxxxxxxxxxxxxxxx";
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请先配置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max")
                .build();

        // c. 初始化工具箱并注册工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new OrderQueryTool());

        // d. 构建 ReActAgent 并使用 hooks(List.of(...)) 挂载 Hook
        ReActAgent customerServiceAgent = ReActAgent.builder()
                .name("VIP_CustomerService_Agent")
                .sysPrompt("你是一个专业的电商客服。用户提问时，你必须使用工具查询真实信息后再回答。")
                .model(model)
                .toolkit(toolkit)
                .hooks(List.of(new ToolObservabilityHook(observationRegistry))) // 挂载拦截器
                .build();

        System.out.println("====== Agent 初始化完成，开始处理用户请求 ======");

        // e. 发起调用并阻塞等待结果
        String userQuery = "我昨天买的手机，订单号是 889900，帮我查查到哪了？";
        Msg userRequest = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder()
                        .text(userQuery)
                        .build())
                .build();
        Msg response = customerServiceAgent.call(userRequest).block();
        System.out.println("\n====== 大模型最终回复 ======");
        // 输出最终文本结果
        System.out.println(response.getTextContent());
    }
}