package com.example.demo.chapter2.two;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import reactor.core.publisher.Mono;

import java.util.List;

public class CustomerServiceWorkflow {

    // 初始化Agent实例。注意：此处仅作单例演示，实际高并发场景见后文优化考量
    private final ReActAgent customerSupportAgent;

    public CustomerServiceWorkflow(String apiToken) {
        // 构建底层DashScope模型配置实例
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiToken)
                .modelName("qwen-max")
                .build();

        // 构建高度复合的系统提示词 (通过Markdown标题结构化融合分类、抽取与生成的业务规则)
        String systemPrompt = """
            你是一个运行在电商核心链路的专业智能售后处理中心。请按照以下三个阶段严格执行任务：
            
            ### 阶段一：意图分类与情绪推理
            深入分析用户输入，判定是否属于"售后退换"分类。如果不是，立刻仅回复"TRANSFER_TO_HUMAN"并终止所有后续步骤。
            请运用逐步推理的过程（Step-by-step）分析用户情绪是否处于愤怒或即将爆发的状态。
            
            ### 阶段二：实体数据抽取
            如果判定通过，请提取文本中的目标订单号和具体的商品瑕疵描述。
            
            ### 阶段三：规范化回复生成
            基于提取的信息，生成一段向用户的安抚性回复。必须声明已经记录了订单号，并承诺在2小时内由专员跟进。语气必须诚恳、专业，禁止使用反问句。
            """;

        this.customerSupportAgent = ReActAgent.builder()
                .name("CoreSupportAgent")
                .model(model)
                .sysPrompt(systemPrompt)
                // 可通过.toolkit(toolkit) 注册订单数据库查询相关的Tool
                .build();
    }

    /**
     * 响应式处理流水线入口
     */
    public Mono<String> handleCustomerComplaint(String userComplaint) {
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(userComplaint).build()))
                .build();

        // 核心原则：在整个调用链中绝对避免使用.block()
        return customerSupportAgent.call(userMsg)
                .map(responseMsg -> {
                    // 在此处可以通过 instanceof 检测并滤除 ThinkingBlock
                    // 仅将 TextBlock 拼接返回给调用方
                    return responseMsg.getTextContent();
                })
                // 生产环境必备的错误熔断与柔性降级机制
                .onErrorResume(exception -> {
                    System.err.println("智能体推理链路异常崩溃: " + exception.getMessage());
                    return Mono.just("当前系统繁忙，正在为您建立人工专属通道，请稍候...");
                });
    }

    public static void main(String args[]) {
        // 1. 从系统环境变量中获取 DashScope (通义千问) 的 API Key
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("测试失败: 请先在系统环境变量中配置 DASHSCOPE_API_KEY");
            System.exit(1);
        }

        // 2. 初始化智能客服工作流实例
        System.out.println("正在初始化智能客服 Agent 引擎...");
        CustomerServiceWorkflow workflow = new CustomerServiceWorkflow(apiKey);

        // 3. 模拟一段具有强烈情绪且包含关键实体（订单号、瑕疵问题）的非结构化用户输入
        String userComplaint = "我昨天在你们这买的那个手机，今天刚收到屏幕就是碎的！你们怎么做品控的？" +
                "我要立刻退货并赔偿我的损失，订单号是 ORD-20260415-8899，" +
                "如果不赶紧处理我就去315投诉你们！";

        System.out.println("\n[收到用户客诉]: " + userComplaint);
        System.out.println("[Agent 正在进行意图推理、实体抽取与回复生成，请稍候...]\n");

        // 4. 发起响应式调用。在 main 测试函数中安全使用.block() 订阅并等待最终结果
        try {
            String finalResponse = workflow.handleCustomerComplaint(userComplaint).block();

            System.out.println("================= Agent 最终回复 =================");
            System.out.println(finalResponse);
            System.out.println("==================================================");
        } catch (Exception e) {
            System.err.println("执行过程中发生异常: " + e.getMessage());
        }
    }
}