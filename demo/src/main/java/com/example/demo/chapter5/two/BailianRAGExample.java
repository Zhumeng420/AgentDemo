package com.example.demo.chapter5.two;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.rag.integration.bailian.BailianConfig;
import io.agentscope.core.rag.integration.bailian.BailianKnowledge;
import io.agentscope.core.message.TextBlock;

/**
 * 实战3-2: 基于 AgentScope 接入阿里云百炼知识库，
 * 构建一个具备生产级可用性与强事实依据的智能IT客服智能体。
 */
public class BailianRAGExample {
    public static void main(String args[]){
        // 步骤 1: 生产级环境安全校验
        String accessKeyId = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET");
        String workspaceId = System.getenv("BAILIAN_WORKSPACE_ID");
        String indexId = System.getenv("BAILIAN_INDEX_ID");
        String apiKey = System.getenv("DASHSCOPE_API_KEY");

        if (accessKeyId == null || accessKeySecret == null || workspaceId == null || indexId == null || apiKey == null) {
            System.err.println("[致命错误] 百炼 RAG 运行必须的鉴权或路由环境变量缺失。");
            System.err.println("请确保配置: ACCESS_KEY_ID, ACCESS_KEY_SECRET, WORKSPACE_ID, INDEX_ID, DASHSCOPE_API_KEY");
            System.exit(1);
        }

        System.out.println(">>> [1/3] 正在初始化阿里云百炼知识库安全连接池...");

        // 步骤 2: 配置并实例化 BailianKnowledge 抽象对象
        // AgentScope 的 Knowledge 接口在底层彻底屏蔽了网络握手、协议打包与检索重排逻辑的复杂性
        BailianKnowledge bailianKnowledge = BailianKnowledge.builder()
                .config(BailianConfig.builder()
                        .accessKeyId(accessKeyId)
                        .accessKeySecret(accessKeySecret)
                        .workspaceId(workspaceId)
                        .indexId(indexId)
                        // 生产优化建议: 在此可注入自定义的检索阈值配置 RetrieveConfig
                        .build())
                .build();

        System.out.println(">>> [2/3] 正在挂载千亿级参数的大语言模型推理核心...");

        // 步骤 3: 构建大语言模型驱动核心
        // 选择具有强逻辑链推理能力的 qwen-max 模型作为智能体的大脑中枢
        DashScopeChatModel qwenModel = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max")
                .build();

        System.out.println(">>> [3/3] 正在装配具备ReAct范式与外挂记忆的架构级智能体...");

        // 步骤 4: 组装 ReActAgent (Reasoning and Acting)
        // 亮点: 将预置的百炼知识库实例直接无缝注入 Agent 构建器的 knowledge() 方法中
        ReActAgent supportAgent = ReActAgent.builder()
                .name("EnterpriseSupportAgent")
                .sysPrompt("你是一名资深的跨国企业IT技术客服支持专家。\n"
                        + "规则一：在回答用户关于系统配置、故障排查或公司政策的问题时，必须优先触发知识检索动作，并严格依据外部知识库返回的内容作答。\n"
                        + "规则二：绝不允许主观臆断或依据模型内部记忆编造答案（即“事实幻觉”）。\n"
                        + "规则三：如果检索到的知识库片段中完全未提及相关信息，请直接回答“根据公司当前IT知识库，暂未查找到相关解决方案，建议联系人工L2支持团队”。")
                .model(qwenModel)
                .knowledge(bailianKnowledge) // 关键装配点：挂载云端私域知识组件
                .build();

        // 步骤 5: 执行真实业务场景的交互测试
        String userQuery = "昨晚公司主楼停电后，核心机房的戴尔R740服务器出现闪黄灯报警，重置iDRAC需要哪些安全审批流程？";
        System.out.println("\n[工单接入] 员工提问: " + userQuery + "\n");

        try {
            Msg requestMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(userQuery).build())
                    .build();

            // 触发智能体的深层思考与外部行动协同循环
            Msg responseMsg = supportAgent.call(requestMsg).block();



            System.out.println("[处理结果] Agent分析与回复: \n" + responseMsg.getContent());
        } catch (Exception e) {
            // 生产级异常兜底策略
            System.err.println("[告警] 智能体服务链路异常降级，触发错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}