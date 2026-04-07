package com.example.demo.chapter2.four;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;

import java.time.Duration;

public class AmapEnabledCustomerService {

    private static String apikey = "sk-034c7aa31c7f44b18e8f27c0119b72ac";
    //如果报错先安装这个
    //npm install -g @amap/amap-maps-mcp-server --registry=https://registry.npmjs.org
    public static ReActAgent buildAgent() {
        // 1. 创建高德地图MCP客户端 (StdIO传输)
        // 使用npx动态拉取并执行开源包

        // 针对 Windows 系统的兼容性处理
        String npxCommand = System.getProperty("os.name").toLowerCase().contains("win")? "npx.cmd" : "npx";

        System.out.println("正在初始化高德地图MCP子进程...");
        McpClientWrapper amapMcpClient = McpClientBuilder.create("amap-location-service")
                .stdioTransport(npxCommand , "--registry=https://registry.npmjs.org","-y", "-q","@amap/amap-maps-mcp-server")
                .timeout(Duration.ofSeconds(60))
                .buildAsync()
                .block();
        // 2. 注册工具箱
        Toolkit toolkit = new Toolkit();
        // AgentScope底层向Node进程发送 "tools/list" JSON-RPC请求，
        // 将高德地理编码、路径规划等工具的能力描述注入当前智能体的可用工具列表
        toolkit.registerMcpClient(amapMcpClient).block();
        // 3. 实例化客服Agent
        return ReActAgent.builder()
                .name("LocationAwareLogisticsAgent")
                // 系统提示词引导其使用新接入的地理工具
                .sysPrompt("你是专业的物流客服。当用户询问距离、通勤时间或周边设施时，" +
                        "你必须优先使用系统提供的高德地图MCP工具获取最准确的实时物理世界数据，" +
                        "而非凭空猜测或依赖预训练知识。")
                .model(DashScopeChatModel.builder()
                        .apiKey(apikey) // 严格遵守配置分离的安全准则
                        .modelName("qwen-max")
                        .build())
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    public static void main(String args[]) {
        ReActAgent agent = buildAgent();

        String userQuery = "我要去北京市朝阳区望京SOHO的极兔驿站自提，我现在在北京南站，开车过去最快要多久？路况怎么样？";
        System.out.println("用户提问: " + userQuery);

        // 触发智能体的思考与行动闭环
        // 6. 构造包含明确意图的复合用户指令
        Msg userRequest = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder()
                        .text(userQuery)
                        .build())
                .build();

        // 发起调用并订阅结果（仅在非生产主线程的测试入口使用.block()，核心业务链路需维持全异步流 ）
        Msg finalResponse = agent.call(userRequest).block();

        System.out.println("工作流已完成。智能体最终响应：\n" + finalResponse.getTextContent());
    }
}
