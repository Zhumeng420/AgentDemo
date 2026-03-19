package com.example.demo.chapter1.two;

import com.example.demo.chapter1.one.TicketInfo;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;


public class FormatterDemo {

    private static String apikey = "sk-xxxxxxxxxxxxxxxxxxxx";



    public static void main(String[] args) {

        // 1. 配置阿里云通义千问模型 (AgentScope 原生支持 DashScope)
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apikey)
                .modelName("qwen-plus")
                .build();

        // 2. 构建ActAgent
        ReActAgent agent = ReActAgent.builder()
                .name("TicketAssistant")
                .sysPrompt("你是一个专业的工单处理助手，负责从用户对话中提取工单信息，并严格按照要求的数据结构返回 JSON。如果是账号登录问题归为 ACCOUNT，表达了严重影响或多次失败归为 HIGH 优先级。")
                .model(model)
                .build();

        // 3. 组装用户消息
        String userMessage = """
            我的账户登录不上，已经试了好几次了。账号是user123，
            从昨天开始就这样。这已经是这周第三次了，严重影响我工作。
            请尽快处理！
            """;

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(userMessage)
                .build();

        System.out.println("正在呼叫 AgentScope 进行自纠错结构化提取...\n");

        // 4. 【核心魔法】直接在 call() 方法的第二个参数传入目标 Class！
        // 框架会在底层自动处理 JSON Schema 注入、解析以及失败重试
        Msg response = agent.call(userMsg, TicketInfo.class).block();

        // 5. 安全、优雅地获取强类型 POJO
        if (response != null && response.getStructuredData(TicketInfo.class) != null) {
            TicketInfo ticket = response.getStructuredData(TicketInfo.class);

            System.out.println("====== AgentScope 提取结果 ======");
            System.out.println("工单标题：" + ticket.getTitle());
            System.out.println("客户 ID：" + ticket.getCustomerId());
            System.out.println("优先级：" + ticket.getPriority());
            System.out.println("分类：" + ticket.getCategory());
            System.out.println("===============================");

            // 业务路由触发逻辑
            if (ticket.getPriority() == TicketInfo.TicketPriority.HIGH) {
                System.out.println("\n[系统动作] 检测到高优工单，立即唤醒 On-Call 团队！");
                // alertOnCallTeam(ticket);
            }
        } else {
            System.out.println("提取失败。模型原始回复：" +
                    (response != null ? response.getTextContent() : "空"));
        }
    }

}
