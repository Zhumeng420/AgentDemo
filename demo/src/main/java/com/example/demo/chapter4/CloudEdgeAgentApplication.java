package com.example.demo.chapter4;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.AgentSkill;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 云边智能客服系统 - 核心 Agent 技能架构验证入口
 * 运行前请确保已配置环境变量：DASHSCOPE_API_KEY
 */
public class CloudEdgeAgentApplication {
    private static final Logger log = LoggerFactory.getLogger(CloudEdgeAgentApplication.class);

    public static void main(String[] args) {
        log.info(">>> 正在启动云边智能客服架构验证环境...");

        CloudEdgeAgentApplication app = new CloudEdgeAgentApplication();

        // 1. 初始化构建高并发响应式 Agent 实例
        ReActAgent enterpriseAgent = app.buildEnterpriseAgentAsync().block(); // 仅在 main 启动线程允许 block

        if (enterpriseAgent == null) {
            log.error("Agent 引擎初始化失败，程序退出。");
            return;
        }

        // 2. 模拟真实高压客诉场景
        String userComplaint = "你们这是什么破系统！我前天买的路由器到今天都没发货，再不处理我马上打 12315 投诉你们！立刻退款！";

        log.info(">>> 接收到用户输入: [{}]", userComplaint);
        log.info(">>> 正在通过响应式流转入 Agent 技能链路处理...");

        // 3. 执行消息处理流
        Msg responseMsg = app.processCustomerIncomingMessage(enterpriseAgent, userComplaint).block();

        // 4. 打印最终输出结果
        log.info("=========================================");
        log.info(">>> 核心中枢引擎最终对外输出结果:");
        log.info("角色: {}", responseMsg.getRole());
        log.info("内容:\n{}", responseMsg.getTextContent());
        log.info("=========================================");
    }

    /**
     * 异步构建核心企业级客服 Agent
     */
    public Mono<ReActAgent> buildEnterpriseAgentAsync() {
        return Mono.defer(() -> {
            Toolkit toolkit = new Toolkit();
            SkillBox skillBox = new SkillBox(toolkit);

            // 注册三大高内聚低耦合的核心技能
            skillBox.registerSkill(buildIntentClassificationSkill());
            skillBox.registerSkill(buildEmotionTrackingSkill());
            skillBox.registerSkill(buildBrandResponseSkill());

            // 安全红线：从环境变量获取凭证
            String apiKey = System.getenv("DASHSCOPE_API_KEY");
            if (apiKey == null || apiKey.trim().isEmpty()) {
                // 为了本地测试方便，如果没有配置环境变量，这里提供一个降级提示。真实环境应直接 return Mono.error
                log.warn("未检测到 DASHSCOPE_API_KEY 环境变量！请配置后获取真实模型响应。");
                return Mono.error(new IllegalStateException("极其严重的配置异常：未能从环境中读取到 DASHSCOPE_API_KEY 凭证！"));
            }

            ReActAgent enterpriseAgent = ReActAgent.builder()
                    .name("CloudEdge_Supervisor_CS_Agent_V2")
                    .sysPrompt("你是一名通过图灵测试的顶尖企业级高级资深客服专家。在每次应对复杂的客户交互时，请始终调用并严格遵循你自身装备的专属技能箱 (SkillBox) 内部规范，以精准分析深层情绪、高效识别潜在意图，并最终运用标准规范生成无可挑剔的对外回复。请严格按顺序执行：1.意图分类 2.情绪识别 3.话术生成。")
                    .model(DashScopeChatModel.builder()
                            .apiKey(apiKey)
                            .modelName("qwen-max")
                            .build())
                    .toolkit(toolkit)
                    .skillBox(skillBox)
                    .build();

            return Mono.just(enterpriseAgent);
        });
    }

    /**
     * 响应式非阻塞调用流转入口
     */
    public Mono<Msg> processCustomerIncomingMessage(ReActAgent agent, String rawUserInput) {
        Msg incomingUserMsg = Msg.builder()
                .name("External_Customer_Entity")
                .role(MsgRole.USER)
                .textContent(rawUserInput)
                .build();

        return agent.call(incomingUserMsg)
                .onErrorResume(throwableError -> {
                    log.error("核心 Agent 引擎在处理依赖技能体系的消息流时遭遇致命异常: ", throwableError);
                    // 触发底层的服务优雅降级流（Graceful Degradation）
                    return Mono.just(Msg.builder()
                            .name("System_Auto_Recovery")
                            .role(MsgRole.ASSISTANT)
                            .textContent("十分抱歉，我们观察到服务底层认知分析中枢当前处于负载巅峰状态。系统已自动为您排队，或建议您致电 VIP 专线寻求人工专家帮助。")
                            .build());
                });
    }

    // ==========================================
    // 技能模块实例化 (内嵌 Markdown 标准化规范)
    // ==========================================

    private AgentSkill buildIntentClassificationSkill() {
        return AgentSkill.builder()
                .name("intent_classification_engine")
                .description("精准解析用户的自然语言输入，将其映射至系统预定义的标准业务意图集合。")
                .skillContent("""
                        ## 1. 技能目标 (Objective)
                        作为智能路由网关的首席语义分析器，精准解析用户的自然语言输入，将其映射至系统预定义的标准业务意图集合，为下游服务的硬分发提供绝对可靠的路由凭据。
                        
                        ## 2. 核心枚举值 (Valid Intents)
                        - `ORDER_INQUIRY`: 订单状态查询、物流追踪、发货催促。
                        - `REFUND_REQUEST`: 退款申请、退货换货、售后纠纷。
                        - `TECHNICAL_SUPPORT`: 账号异常、系统报错、操作指导。
                        - `UNKNOWN_INTENT`: 无法收敛的闲聊、超出服务范围的诉求。
                        
                        ## 3. 严格执行约束 (Constraints)
                        - **绝对纯净输出**：只允许输出上述枚举值中的【一项】，严禁生成任何标点符号、前置说明或解释性废话。
                        - **降级收敛**：若经过推理引擎判断，置信度低于 85% 或无法精准匹配，必须立刻输出 `UNKNOWN_INTENT` 以触发人工或回退逻辑。
                        - **免责剥离**：不要在分类阶段尝试解决用户问题，你的唯一职责是“贴标签”。
                        
                        ## 4. 少样本学习参考 (Few-Shot Examples)
                        - User: "我前天买的键盘怎么还没发货？" -> Output: `ORDER_INQUIRY`
                        - User: "你们这破软件一直闪退，密码也登不上！" -> Output: `TECHNICAL_SUPPORT`
                        - User: "质量太差了，我要求退钱！" -> Output: `REFUND_REQUEST`        
                """)
                .build();
    }

    private AgentSkill buildEmotionTrackingSkill() {
        return AgentSkill.builder()
                .name("emotion_tracking_and_crisis_intervention")
                .description("持续分析用户输入的情感状态突变，识别危机特征，动态调整下游处理优先级。")
                .skillContent("""
                        ## 1. 技能目标 (Objective)
                        在系统后台静默运行，作为状态机旁路拦截并分析用户输入文本中的潜在情感极性与攻击性特征，为对话系统提供底层的共情计算指标。
                        
                        ## 2. 情感极性标准极点 (Emotion Scale)
                        - `LEVEL_1_EXTREME_ANGER` (极端愤怒): 包含辱骂、威胁（投诉/曝光/起诉）、严重对立情绪。
                        - `LEVEL_2_SEVERE_ANXIETY` (极度焦虑): 密集追问、大量感叹/问号滥用、表达急迫性损失。
                        - `LEVEL_3_CALM` (情绪平静): 正常业务诉求，语气中性，无明显波澜。
                        - `LEVEL_4_HIGH_SATISFACTION` (超预期满意): 表达感谢、夸赞、正向反馈。
                        
                        ## 3. 危机熔断机制 (Crisis Intervention Rules)
                        - **触发条件**：当识别到 `LEVEL_1_EXTREME_ANGER` 且文本特征命中 [12315, 投诉, 法院, 媒体, 报警] 中的任意语义时。
                        - **执行动作**：必须在输出情感级别的同时，附加 `<CRISIS_FLAG_TRIGGERED>` 标识符，强制阻断常规业务流，请求最高级人工专家接管。
                        
                        ## 4. 输出契约 (Output Contract)
                        - 格式：`[情绪级别] | [危机标识(可选)] | [简短判别依据(限20字)]`
                        - 示例：`LEVEL_1_EXTREME_ANGER | <CRISIS_FLAG_TRIGGERED> | 包含"12315投诉"威胁词汇`
                """)
                .build();
    }

    private AgentSkill buildBrandResponseSkill() {
        return AgentSkill.builder()
                .name("enterprise_brand_response_generation")
                .description("根据获取到的意图极性与情绪级别，遵循企业红线生成最终对外话术。")
                .skillContent("""
                        ## 1. 技能目标 (Objective)
                        承接上游【意图分析】与【情绪指标】，遵循企业公关与法务红线，动态多态地生成最终面客的富文本回复。
                        
                        ## 2. 动态语气与合规策略 (Dynamic Persona & Compliance)
                        - **极性区间 [EXTREME_ANGER / SEVERE_ANXIETY]**：
                            - 策略：极简、务实、高同理心。
                            - 约束：**严禁**使用任何表情符号或颜文字；**严禁**进行任何形式的产品推销；必须直接给出解决方案或明确的处理时效。
                        - **极性区间 [CALM / HIGH_SATISFACTION]**：
                            - 策略：专业、热情、品牌化。
                            - 约束：允许使用品牌吉祥物口吻；在解决核心问题后，允许植入极简的周边产品推荐（交叉销售）。
                        
                        ## 3. 全局硬性红线 (Global Redlines)
                        - **财务隔离**：绝对禁止在未获授权的情况下，通过自然语言承诺任何具体金额的现金赔偿或优惠券发放。
                        - **标准收尾**：所有的回复必须以 "【云边智能客服，随时为您候命】" 结尾。
                        
                        ## 4. 输入上下文装载规范
                        请读取当前环境中的变量：`{{CURRENT_INTENT}}`, `{{CURRENT_EMOTION_LEVEL}}`，并结合用户原始输入进行融合生成。
                """)
                .build();
    }
}