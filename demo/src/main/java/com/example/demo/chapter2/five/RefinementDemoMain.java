package com.example.demo.chapter2.five;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;

public class RefinementDemoMain {

    public static void main(String args[]) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("运行失败：请先配置 DASHSCOPE_API_KEY 环境变量！");
            return;
        }

        // 1. 初始化模型：严格使用 io.agentscope.core.model.Model 接口
        Model generator = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .formatter(new DashScopeChatFormatter())
                .build();

        GenerateOptions generateOptions  = GenerateOptions.builder().temperature(0.0).build();
        Model judge = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max")
                .defaultOptions(generateOptions) // 裁判必须保持确定性
                .formatter(new DashScopeChatFormatter())
                .build();

        // 2. 实例化基础客服 Agent
        Agent agent = ReActAgent.builder()
                .name("CustomerService")
                .sysPrompt("你是一个智能电商助手。请务必用不超过50字的简明语言安抚客户。")
                .model(generator)
                .maxIters(1)
                .build();

        // 3. 实例化重构后的自我修正外挂包装器 (最大重试2次)
        SelfRefinementWrapper refinementWrapper = new SelfRefinementWrapper(agent, judge, 2);

        // 4. 封装符合 AgentScope 规范的输入消息
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text("我买的手机都三天了还没发货！你们是怎么做生意的？").build())
                .build();

        System.out.println("================= 接收到用户客诉 =================");
        System.out.println("用户: " + userMsg.getTextContent());
        System.out.println("\n[后台] Agent开始处理，触发自动化裁判与修正机制...\n");

        // 5. 触发响应式执行流。规范要求：仅在 main 或测试方法中允许使用.block()
        Msg finalResponse = refinementWrapper.execute(userMsg).block();

        System.out.println("\n================= 最终输出给用户的回复 =================");
        System.out.println("客服: " + finalResponse.getTextContent());
    }
}
