package com.example.demo.chapter4.two;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.autocontext.AutoContextHook;

public class AutoContextMemoryDemo {

    public static void main(String args[]) {
        // 1. 初始化大模型接入层网关
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max")
                .build();

        // 2. 实例化智能上下文核心配置矩阵 (AutoContextConfig)
        AutoContextConfig config = AutoContextConfig.builder()
                // 核心修正：匹配主流模型实际稳定的 30K Token 上限防爆处理
                .maxToken(30000)
                .tokenRatio(0.8)
                // 为了方便演示，我们将触发门槛降低：当消息堆积超过 4 条即刻触发检查机制
                .msgThreshold(4)
                // 关键护栏：仅保护最新 2 条消息不被卸载，确保大文本一旦变老立刻脱离保护区
                .lastKeep(2)
                // 卸载阈值：任何超过 5KB 的单体文本将被系统标记为危险巨无霸
                .largePayloadThreshold(5 * 1024)
                .offloadSinglePreview(300)
                .build();

        // 3. 实例化智能记忆底层系统
        AutoContextMemory memory = new AutoContextMemory(config, model);
        Toolkit toolkit = new Toolkit();

        // 4. 构建并装配具备自动记忆管理能力的 ReActAgent
        ReActAgent agent = ReActAgent.builder()
                .name("TechSupportAssistant")
                .sysPrompt("你是云原生排障专家。当日志被压缩时，利用摘要中的核心类名进行推理。")
                .model(model)
                .memory(memory)
                .toolkit(toolkit)
                .enablePlan()
                // 【绝对核心】没有这个钩子，所有配置如同虚设
                .hook(new AutoContextHook())
                .build();

        System.out.println("====== 生产级上下文卸载演练开始 ======\n");

        // 【第一轮】正常交互（此时上下文内共计 2 条消息）
        System.out.println(">>> 轮次 1：基础沟通");
        Msg userMsg1 = Msg.builder().textContent("你好，我的订单链路崩了。").build();
        System.out.println("Agent 回复: " + agent.call(userMsg1).block().getTextContent());

        // 【第二轮】投递大型载荷：约 12KB (远超 5KB 阈值，但未达模型 30K 上限)
        System.out.println("\n>>> 轮次 2：发送巨大堆栈日志 (约 12KB)");
        StringBuilder massiveLogBuilder = new StringBuilder("ERROR: java.lang.NullPointerException\n");
        for (int i = 0; i < 150; i++) {
            massiveLogBuilder.append("at com.example.PaymentService.process(PaymentService.java:").append(i).append(")\n");
            massiveLogBuilder.append("at org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:218)\n");
        }

        Msg userMsg2 = Msg.builder().textContent("这是完整日志：" + massiveLogBuilder.toString()).build();
        // 此时由于 12KB 日志属于“当前轮次”用户输入，受安全协议保护，系统不会压缩它 。
        // 且它没有超越 30000 Token 物理大限，API 调用顺利成功。 (此时上下文中包含 4 条消息)
        System.out.println("Agent 诊断: " + agent.call(userMsg2).block().getTextContent());

        // 【第三轮】再次发问，促发历史剥离风暴
        System.out.println("\n>>> 轮次 3：追加提问（触发防线）");
        // 当此条消息进入时，总消息数达到 5 条，突破 msgThreshold(4)。
        // 此时，第二轮发送的 12KB 巨型日志正式退化为“历史消息”。
        // 且其索引位置已跌出 lastKeep(2) （只保护最近 1 问 1 答）的保护伞范围。
        Msg userMsg3 = Msg.builder().textContent("能给我提供一段修复这行代码的 Demo 吗？").build();

        // AutoContextHook 瞬间拦截！命中【策略 2】：历史大消息卸载！
        Msg response3 = agent.call(userMsg3).block();
        System.out.println("Agent 修复建议: " + response3.getTextContent());

        // 验证系统内存，观察那坨巨大的日志是否已经被切除替换
        System.out.println("\n>>> 监控探针：");
        System.out.println("当前短时记忆保留的总消息数: " + agent.getMemory().getMessages().size());
    }
}