package com.example.demo.chapter4.four;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import reactor.core.publisher.Mono;
import java.util.Map;

public class DynamicContextInjectionExample {
    public static final String CONTEXT_KEY = "CURRENT_ORDER_SNAPSHOT";

    // 实现动态业务上下文注入的 Hook
    static class DynamicContextInjectionHook implements Hook {
        @Override
        public int priority() {
            return 10; // 设置高优先级，确保在系统核心逻辑前执行
        }

        @Override
        public <T extends HookEvent> Mono<T> onEvent(T event) {
            // 在大模型推理（Reasoning）开始前的拦截点
            if (event instanceof PreReasoningEvent preReasoning) {
                // 使用 Mono.deferContextual 从 Reactor 的异步执行流中安全提取上下文
                return Mono.deferContextual(ctxView -> {
                    if (ctxView.hasKey(CONTEXT_KEY)) {
                        OrderSnapshot snapshot = ctxView.get(CONTEXT_KEY);

                        // 构造一条对终端用户不可见的 System 级别背景提示
                        String systemNarrative = String.format(
                                "【系统旁白：请注意，业务系统检测到该用户在3分钟前下了一笔订单。订单号：%s，商品名称：%s，当前分配的物流公司：%s。如果用户的提问模糊，请直接默认他在询问此订单。】",
                                snapshot.orderId, snapshot.productName, snapshot.logisticsCompany
                        );

                        // 实例化上下文 Msg，并将强类型数据放入 metadata，这些字段支持后续 Tool 的参数自动提取
                        Msg contextMsg = Msg.builder()
                                .name("SystemContextEngine")
                                .role(MsgRole.SYSTEM)
                                .textContent(systemNarrative)
                                .metadata(Map.of(
                                        "order_id", snapshot.orderId,
                                        "product_name", snapshot.productName
                                ))
                                .build();

                        // 将此旁白插入到即将发往 LLM 的历史对话流的末尾，即当前用户提问的前面
                        int insertIndex = Math.max(0, preReasoning.getInputMessages().size() - 1);
                        preReasoning.getInputMessages().add(insertIndex, contextMsg);

                        System.out.println("\n[系统日志] -> Hook触发：已成功将动态订单数据(订单号: " + snapshot.orderId + ") 注入到上下文！\n");
                    }
                    return Mono.just(event);
                });
            }
            return Mono.just(event);
        }
    }

    // 3. Main 执行入口
    public static void main(String args[]) {
        // 从环境变量获取通义千问 API Key
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误：请先设置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        System.out.println("正在初始化客服 Agent...");

        // 构建带有业务 Hook 的 ReActAgent
        ReActAgent agent = ReActAgent.builder()
                .name("CustomerService")
                .model(DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName("qwen-max") // 采用 qwen-max 模型
                        .build())
                .sysPrompt("你是云边智能的售后客服。请结合系统提供的旁白信息，直接解答用户的疑问，语气要自然且专业。")
                .hook(new DynamicContextInjectionHook()) // 挂载动态注入钩子
                .build();

        // 模拟用户发出了一句没有明确指代、上下文极度缺失的提问
        Msg userMsg = Msg.builder()
                .name("User")
                .role(MsgRole.USER)
                .textContent("我刚才买的那个鼠标，到底发什么快递？")
                .build();

        System.out.println("\nUser: " + userMsg.getTextContent());
        System.out.println("Agent 思考中...");

        // 模拟后端业务系统从 DB 或 Redis 拉取到了该用户当前的最新活跃订单
        OrderSnapshot currentSnapshot = new OrderSnapshot("ORD_8848_2026", "罗技 MX Master 3S 无线鼠标", "顺丰速运");

        // 执行调用：使用 contextWrite 模拟 WebFlux 环境下的拦截器行为，将状态安全压入执行链
        Msg response = agent.call(userMsg)
                .contextWrite(ctx -> ctx.put(CONTEXT_KEY, currentSnapshot))
                .block();

        // 打印大模型最终整合了旁白信息后的回复
        System.out.println("Agent: " + response.getTextContent());
    }
}