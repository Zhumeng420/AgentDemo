package com.example.demo.chapter4.three;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.reme.ReMeLongTermMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import reactor.core.publisher.Mono;

public class ECommerceMemoryAgentService {

    private final DashScopeChatModel model;

    public ECommerceMemoryAgentService() {
        // 初始化通义千问大模型实例，必须通过环境变量安全注入密钥
        this.model = DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max-latest")
                .build();
    }

    /**
     * 处理用户请求的响应式主干道
     */
    public Mono<String> handleCustomerMessage(String userId, String userInput) {
        // 核心：基于租户/用户ID动态构建或获取专属的长效记忆隔离空间
        ReMeLongTermMemory longTermMemory = ReMeLongTermMemory.builder()
                .userId(userId) // 强隔离标识，保障不同用户的记忆向量不发生交叉污染
                .apiBaseUrl("http://localhost:8002")
                .build();

        // 构建带有长期记忆外挂的ReAct智能体
        ReActAgent agent = ReActAgent.builder()
                .name("ECommerceGenie")
                .model(model)
                .sysPrompt("你是平台的金牌专属客服。请时刻关注上下文中的历史记忆事实。如果在记忆中发现用户的特定习惯（如衣服尺码、不吃辣、特定快递偏好），必须在推荐和解决问题时严格遵守，无需向用户反复确认。")
                .longTermMemory(longTermMemory)
                // 采用静态控制模式：框架将在底层自动执行检索(Retrieve)与记录(Record)，实现业务代码零侵入
                .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
                .build();

        // 构建用户消息
        Msg userMsg = Msg.builder().textContent(userInput).build();

        // 发起非阻塞调用，依靠Reactor框架异步处理推理与记忆落地
        return agent.call(userMsg)
                .map(Msg::getTextContent)
                // 优雅的异常降级处理：若大模型或向量库调用失败，返回保底文本
                .onErrorReturn("抱歉，系统暂时开小差了，请稍后重试。");
    }

    public static void main(String args[]) {
        // 确保环境变量 DASHSCOPE_API_KEY 和 REME_API_ENDPOINT 已正确配置
        ECommerceMemoryAgentService service = new ECommerceMemoryAgentService();
        String userId = "user_ecommerce_1024";

        System.out.println("=== 第一次会话（建立记忆） ===");
        String input1 = "你好，我以后买运动鞋只穿42码的，并且只发顺丰快递，别发其他的。";
        System.out.println("用户: " + input1);

        // 依据AgentScope最佳实践，仅在main方法或测试代码中允许使用.block()来阻塞等待异步结果
        String response1 = service.handleCustomerMessage(userId, input1).block();
        System.out.println("Agent: " + response1);

        System.out.println("\n=== 第二次会话（跨会话记忆召回） ===");
        // 在新的会话中，直接提出需求，智能体应能从长效记忆中提取尺码和快递偏好
        String input2 = "我穿多少码的鞋子？";
        System.out.println("用户: " + input2);

        String response2 = service.handleCustomerMessage(userId, input2).block();
        System.out.println("Agent: " + response2);
    }
}