package com.example.demo.chapter2.four;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;


public class ComplexSupportAgentDemo {

    /**
     * 1. 定义模拟工具集
     * AgentScope 会通过 @Tool 和 @ToolParam 注解自动将这些能力暴露给大模型[2]。
     */
    public static class MockSupportTools {
        @Tool(name = "query_order", description = "查询订单详情，获取订单状态。")
        public String queryOrder(@ToolParam(name = "order_id", description = "订单编号") String orderId) {
            System.out.println(">>> [工具执行]: 正在查询订单 " + orderId);
            return "订单 " + orderId + " 状态：已付款，昨日生效。";
        }

        @Tool(name = "apply_refund", description = "为指定的订单申请全额退款。")
        public String applyRefund(@ToolParam(name = "order_id", description = "订单编号") String orderId) {
            System.out.println(">>> [工具执行]: 正在提交退款申请 " + orderId);
            return "订单 " + orderId + " 退款申请已提交，系统已自动拦截计费，预计1-3个工作日到账。";
        }

        @Tool(name = "network_diagnostics", description = "诊断网络或服务器故障，并可指派技术专家。")
        public String networkDiagnostics(@ToolParam(name = "region", description = "服务器所在区域，如北京二区") String region) {
            System.out.println(">>> [工具执行]: 正在诊断区域网络 " + region);
            return region + " 网络日志已调取：发现核心交换机存在拥塞，已自动派发高级故障排查工单给驻场技术专家处理。";
        }
    }

    /**
     * 2. 主函数入口
     */
    public static void main(String args[]) {
        // 获取配置的 API Key
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误：请先设置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        System.out.println("正在初始化智能体与工具箱...");

        // 分层模型配置：路由器使用响应快、成本低的轻量模型 (如 qwen-turbo)
        Model routerModel = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-turbo")
                .build();

        // 核心执行 Agent 使用推理能力强、指令遵循度高的大模型 (如 qwen-max)
        Model coreModel = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max")
                .build();

        // 注册工具箱
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new MockSupportTools());

        // 初始化我们此前编写的意图拆解服务
        IntentRouterService intentRouter = new IntentRouterService(routerModel);

        // 实例化基于 ReAct 模式的核心执行智能体
        ReActAgent coreAgent = ReActAgent.builder()
                .name("Support-Executor")
                .sysPrompt("你是一个专业的高级技术支持Agent。请严格按照用户提示词中包含的【任务规划列表】的顺序，调用合适的工具来完成任务，切勿遗漏。")
                .model(coreModel)
                .toolkit(toolkit)
                .build();

        // 实例化业务流
        SupportWorkflow workflow = new SupportWorkflow();

        // 模拟一个极其复杂的客户投诉输入
        String rawCustomerInput = "你们这个ECS服务器根本没法用！我昨天刚续费，今天下午网络一直丢包，导致我数据库全挂了。你现在立刻帮我查一下昨天那笔订单（编号：ORD-99812）能不能全额退款？还有，赶紧派个技术专家查一下你们北京二区的网络到底是怎么回事，给我个事故报告！";

        System.out.println("\n【收到用户原始复杂输入】:\n" + rawCustomerInput + "\n");
        System.out.println("================= 开始执行 Prompt Chaining 工作流 =================");

        // 执行处理，期间会先经过 router 拆解，再交由 coreAgent 调度工具
        workflow.processCustomerIssue(rawCustomerInput, intentRouter, coreAgent);

        System.out.println("================= 工作流执行完毕 =================");
    }
}