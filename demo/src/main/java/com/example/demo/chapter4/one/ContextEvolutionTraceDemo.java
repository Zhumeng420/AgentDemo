package com.example.demo.chapter4.one;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.util.Arrays;
import java.util.List;

public class ContextEvolutionTraceDemo {

    // ==========================================
    // 1. 定义业务工具 (模拟外部系统与知识库)
    // ==========================================

    public static class QueryOrderTool {
        @Tool(name = "query_order", description = "根据用户ID查询最近的订单信息")
        public String queryOrder(
                @ToolParam(name = "userId", description = "用户ID") String userId
        ) {
            System.out.println("\n[系统底层执行] 调用 QueryOrderTool, 提取到的用户ID: " + userId);
            // 模拟数据库返回结构化订单数据
            return "{\"orderId\": \"ORD-20261024\", \"product\": \"AX3000无线路由器\", \"status\": \"买家已签收\", \"purchaseTime\": \"昨天\"}";
        }
    }

    public static class KnowledgeBaseRAGTool {
        @Tool(name = "search_knowledge_base", description = "在知识库中检索产品故障排查手册")
        public String searchKnowledgeBase(
                @ToolParam(name = "query", description = "故障表现及设备型号") String query
        ) {
            System.out.println("\n[系统底层执行] 调用 KnowledgeBaseRAGTool, 检索词: " + query);
            // 模拟向量数据库返回的冗长技术文档（这里缩短以便于演示）
            return "【内部排障技术文档-AX3000】如果设备持续闪烁红灯，且物理拔插电源重启无效，极大概率是前端光猫的VLAN配置未正确同步。建议：1. 将入户网线精准接入背部的WAN口。2. 找一根牙签长按Reset孔洞5秒钟进行深度复位。3. 重新配置PPPoE拨号。";
        }
    }

    public static class RefundPolicyTool {
        @Tool(name = "check_refund_policy", description = "查询当前商品的退换货与运费垫付政策")
        public String checkRefundPolicy(
                @ToolParam(name = "product", description = "商品名称") String product,
                @ToolParam(name = "isQualityIssue", description = "是否为确定的质量问题") boolean isQualityIssue
        ) {
            System.out.println("\n[系统底层执行] 调用 RefundPolicyTool, 评估商品: " + product);
            // 模拟规则引擎返回政策
            if (!isQualityIssue) {
                return "政策核算结果：支持七天无理由退货。但当前无工程师硬件损坏鉴定，计入非质量问题退货。退回物流运费需由买家暂时垫付。";
            }
            return "政策核算结果：支持退换货，运费由平台全额承担。";
        }
    }

    // ==========================================
    // 2. 主函数逻辑
    // ==========================================

    public static void main(String args[]) {
        // 初始化模型配置 (使用环境变量中的 API Key)
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max")
                .build();

        // 注册所有业务工具 [3]
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new QueryOrderTool());
        toolkit.registerTool(new KnowledgeBaseRAGTool());
        toolkit.registerTool(new RefundPolicyTool());

        // 显式实例化内存，以便在主函数中窥探上下文状态的演变
        InMemoryMemory memory = new InMemoryMemory();

        // 构建带有工具和记忆的智能体 [4]
        ReActAgent agent = ReActAgent.builder()
                .name("CustomerServiceAgent")
                .sysPrompt("你是一名资深电商客服专家，负责处理用户的技术排障与退换货诉求。请善用工具。当前的隐式业务上下文中，用户的 userId 为 'U10086'。")
                .model(model)
                .toolkit(toolkit)
                .memory(memory)
                .build();

        // 模拟多轮对话（意图跃迁）
        List<String> userInputs = Arrays.asList(
                "我昨天在你们这买的那个路由器一直连不上网，这破玩意儿能帮我看看吗？", // 意图：隐式故障求助
                "一直不停地闪红灯。网上的法子我试了，拔了电源重启过，完全没用。",   // 意图：深入排障
                "搞这么复杂？太麻烦了，我一点都不想弄了，直接给我走退货流程吧。对了，退回来的运费到底谁出？" // 意图骤变
        );

        System.out.println("================ 智能客服上下文演变实战测试开始 ================\n");

        for (int i = 0; i < userInputs.size(); i++) {
            System.out.println("\n\n>>> 👤 用户输入 (第 " + (i + 1) + " 轮): " + userInputs.get(i));

            // 构建用户消息并同步阻塞调用模型推理
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(userInputs.get(i)).build())
                    .build();

            Msg response = agent.call(userMsg).block();

            System.out.println("<<< 🤖 Agent回复: " + response.getTextContent());

            // 核心部分：打印当前的上下文记忆快照，透视"Context Amnesia"是如何被化解的
            printContextTrace(memory, i + 1);
        }
    }

    // ==========================================
    // 3. 辅助追踪方法：观察 Context 的膨胀
    // ==========================================

    private static void printContextTrace(InMemoryMemory memory, int round) {
        List<Msg> msgs = memory.getMessages();
        System.out.println("\n   ");
        System.out.println("    当前上下文窗口积压消息总数: " + msgs.size() + " 条");

        int totalTextLength = 0;
        for (int j = 0; j < msgs.size(); j++) {
            Msg m = msgs.get(j);
            String contentSnippet = m.getTextContent();
            if (contentSnippet == null) contentSnippet = "";
            else if (contentSnippet.length() > 30) contentSnippet = contentSnippet.substring(0, 30) + "...";

            totalTextLength += (m.getTextContent() == null? 0 : m.getTextContent().length());

            System.out.printf(" |-- [%d] Role: %-10s | Preview: %s%n", j, m.getRole(), contentSnippet.replace("\n", ""));
        }
        System.out.println("    -> 纯文本字符负载估算: 约 " + totalTextLength + " 字符 (不包含结构化对象开销)");
        System.out.println("    ------------------------------------------------------");
    }
}