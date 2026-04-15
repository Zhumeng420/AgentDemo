package com.example.demo.chapter2.one;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.*;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;



/**
 * 实战 7-1：企业级意图识别 Agent 构现实践
 * 演示通过严格的结构化 Prompt 控制大模型的概率输出边界
 */
public class PromptEngineeringExample {



    public static void main(String args[]) {
        // 1. 安全考量：获取大模型 API Key。在生产环境中应从 Nacos 等安全配置中心动态拉取，严禁硬编码
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("系统启动失败：未检测到合法的 DASHSCOPE_API_KEY 环境变量。");
        }

        // 2. 底层架构：初始化大语言模型连接池与运行配置
        // AgentScope 提供了对多厂商架构的抽象包装。此处以阿里云通义千问大模型为例。
        //  处指出，formatter 负责将 AgentScope 的内部消息格式无缝转换为厂商特定的底层 REST API 负载。
        DashScopeChatModel qwenModel = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max") // 意图识别与复杂格式控制对逻辑推理要求极高，故选用高阶的 max 级模型
                .formatter(new DashScopeChatFormatter())
                .build();

        // 3. 核心资产：获取经过严格工程化调优的结构化 System Prompt
        String optimizedSysPrompt = getOptimizedSystemPrompt();

        // 4. 智能体实例化：构建单一职责的意图路由 ReActAgent
        // [3, 28] ReActAgent 是 AgentScope 提供的一种支持推理与行动循环的高级自治实体。
        ReActAgent intentClassifierAgent = ReActAgent.builder()
                .name("IntentRouterAgent")
                .sysPrompt(optimizedSysPrompt)
                .model(qwenModel)
                // 性能调优：作为一个纯粹的文本解析与分类 Agent，它不需要调用外部工具或进行多轮反思，
                // 因此将 maxIters 严格限制为 1，强行阻断不必要的 ReAct 思考循环，极大降低系统延迟。
                .maxIters(1)
                .build();

        // 5. 边界输入测试：模拟来自 API 网关或上游渠道的复杂用户原始语音转写文本
        String rawUserInput = "你们这什么破手机，根本连不上办公室的WiFi！我已经提交了两次工单了，我的订单是PO-202604A，赶紧让你们主管给我退钱！";

        // 6. 消息封装：使用 Msg.builder 与 TextBlock 构建符合 AgentScope 规范的交互消息 [29, 30]
        Msg userMessage = Msg.builder()
                .role(MsgRole.USER)
                .name("ExternalUser")
                // 将用户输入也包裹在预设的 XML 标签中，防止恶意用户在输入中注入“忽略上述系统指令”的越权攻击
                .content(TextBlock.builder().text("<user_input>\n" + rawUserInput + "\n</user_input>").build())
                .build();

        // 7. 同步计算：触发 Agent 运行生命周期，等待模型完成特征推理并返回结果
        System.out.println("====== Agent 计算引擎已启动，正在解析意图 ======");
        Msg response = intentClassifierAgent.call(userMessage).block();

        // 8. 契约校验与输出：提取大模型的结构化输出
        String jsonOutput = response.getTextContent();
        System.out.println("Agent 响应结果 (严格序列化 JSON):");
        System.out.println(jsonOutput);

        /*
         * 预期输出示例：
         * {
         *   "intent": "Refund",
         *   "order_id": "PO-202604A",
         *   "confidence": 0.98,
         *   "reasoning": "用户表达了强烈的退款诉求，且情绪带有明显的愤怒，同时提供了清晰的订单号。"
         * }
         */

        // 生产环境延伸：在此处可直接将 jsonOutput 喂给 ObjectMapper 进行 POJO 反序列化
        // 随后利用强类型 Java 对象通过 Kafka 或 A2A 协议分发至专业的 退款Agent 进行后续业务处理。
    }

    /**
     * 遵循 RTCF 框架与 XML 结构化设计模式的工程级 Prompt
     */
    private static String getOptimizedSystemPrompt() {
        // 利用 Java Text Blocks 特性，避免混乱的转义符，极大地提升了 Prompt 的后期维护体验
        return """
            <role_definition>
                你是一个隶属于全球顶级电商平台、拥有十年架构经验的智能客服意图路由专家引擎。
                你的唯一核心任务是精准地对用户的输入进行语义解析、意图分类与关键实体提取。
                你必须像一台绝对严谨的 C++ 状态机一样工作，严禁在输出中包含任何自然语言、问候语、解释性文本或冗余字符。
            </role_definition>

            <instructions>
                1. 首先，严格解析被包裹在 <user_input> 标签内的原始用户数据。
                2. 将用户的核心意图映射到 <allowed_intents> 中规定的枚举值。若无法精确匹配，一律回退至 'Human' 状态。
                3. 使用正则逻辑，从文本中提取可能存在的订单号（通常以 ORD, PO 开头，或由纯数字组成）。
                4. 最终响应内容必须是一个完全合法、可被 Jackson 解析的 JSON 对象，严格匹配 <output_format> 约束。
            </instructions>

            <allowed_intents>
                - Refund (退款、退货、拒收等资金逆向链路相关)
                - Tracking (物流进度、发货状态、催单、到达时间相关)
                - TechSupport (电子产品使用指导、故障排查、安装说明)
                - Human (包含严重脏话、愤怒情绪、明确要求投诉或转接人工、或意图极其模糊无法识别)
            </allowed_intents>

            <examples>
                <example>
                    <input>我的包裹ORD-998877卡在分拨中心三天了，怎么回事？</input>
                    <output>
                        {"intent": "Tracking", "order_id": "ORD-998877", "confidence": 0.98}
                    </output>
                </example>
                <example>
                    <input>随便聊聊，今天深圳的天气真不错，适合去爬山。</input>
                    <output>
                        {"intent": "Human", "order_id": null, "confidence": 0.99}
                    </output>
                </example>
            </examples>

            <output_format>
                必须且只能输出包含以下字段的纯 JSON 对象，绝对不要使用 ```json 等 Markdown 标记符号包裹：
                {
                    "intent": "字符串，仅限 allowed_intents 中定义的四种枚举",
                    "order_id": "字符串，提取出的订单号；若彻底未提及则必须返回 null",
                    "confidence": "浮点数，表示识别结果的置信度，范围 0.0 到 1.0"
                }
            </output_format>
            """;
    }

}