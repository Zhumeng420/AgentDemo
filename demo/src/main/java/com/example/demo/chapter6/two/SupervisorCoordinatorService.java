package com.example.demo.chapter6.two;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/**
 * 客户投诉协调服务 - 总控调度器（Supervisor）
 *
 * 该服务扮演两大核心角色：
 * 1. 消费者（Client）：接收用户的复杂投诉请求
 * 2. 宏观调度者（Orchestrator）：通过 AgentScope 的"Agent as Tool"模式，将远程子 Agent 动态装配为本地工具，
 *    并由大模型自主决定调用顺序，实现复杂的跨节点协调工作流。
 *
 * 核心设计理念：
 * - 利用 Nacos 服务发现机制，实现远程 Agent 的动态路由
 * - 通过 Toolkit 将远程 Agent 转换为本地可调用的 Tool，实现"大一统"的工具调用范式
 * - Supervisor Agent 具备全局视野，根据子 Agent 的返回结果自主决策下一步操作
 */
public class SupervisorCoordinatorService {

    /** Nacos AI 服务实例，用于连接 Nacos 注册中心并获取服务元数据 */
    private AiService aiService;

    /**
     * AgentCard 动态解析器
     * 核心职责：根据 Agent 名称向 Nacos 注册中心查询对应的 Agent Card（包含网络地址、端口、能力描述等元数据）
     * 实现原理：底层封装了 Nacos 的服务发现机制，支持动态感知远程 Agent 的变更
     */
    private NacosAgentCardResolver resolver;

    /** 远程"订单 Agent"的本地代理句柄，用于发起 A2A（Agent-to-Agent）协议调用 */
    private A2aAgent remoteOrderAgent;

    /** 远程"售后 Agent"的本地代理句柄，用于发起 A2A（Agent-to-Agent）协议调用 */
    private A2aAgent remoteAfterSalesAgent;

    /**
     * 工具包（Toolkit）
     * 核心设计：将远程 Agent 转换为本地可用的 Tool 资源池
     * "Agent as Tool"模式：底层框架会自动将 subAgent 的调用签名封装为标准的大模型 Function Calling 接口
     * 这样Supervisor Agent 就能像调用本地工具一样调用远程 Agent，无需关心底层网络通信细节
     */
    private Toolkit toolkit;

    /**
     * 总控大脑（Supervisor Agent）
     * 核心职责：作为调度中枢，根据用户诉求自主规划任务分解与执行顺序
     * 工作流程：先调用订单专家获取权威状态，再根据结果决定是否调用售后专家发起工单
     */
    private ReActAgent supervisorAgent;

    /**
     * 初始化方法（Spring 生命周期钩子）
     * @throws NacosException 如果无法连接到 Nacos 注册中心
     */
    public void init() throws NacosException {
        // ==================== 步骤1：Nacos 连接配置 ====================
        // 构建 Nacos 客户端连接属性，包含注册中心地址和认证凭据
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        properties.put(PropertyKeyConst.USERNAME, "nacos");
        properties.put(PropertyKeyConst.PASSWORD, "woTZnqgBs1V8");

        // 创建 Nacos AI 服务抽象层实例，这是与 Nacos 注册中心交互的入口
        aiService = AiFactory.createAiService(properties);

        // ==================== 步骤2：构建 AgentCard 动态解析器 ====================
        resolver = new NacosAgentCardResolver(aiService);

        // ==================== 步骤3：构建远程 Agent 的本地代理 ====================
        remoteOrderAgent = A2aAgent.builder()
                .name("order-agent")  // 必须与 Nacos 注册中心注册的 Agent 名称完全一致
                .agentCardResolver(resolver)
                .build();

        remoteAfterSalesAgent = A2aAgent.builder()
                .name("after-sales-agent")
                .agentCardResolver(resolver)
                .build();

        // ==================== 步骤4：创建封装远程 Agent 调用的本地 Tool ====================
        // 使用 @Tool 注解创建工具类，让框架自动处理方法签名
        RemoteOrderAgentTool orderTool = new RemoteOrderAgentTool(remoteOrderAgent);
        RemoteAfterSalesAgentTool afterSalesTool = new RemoteAfterSalesAgentTool(remoteAfterSalesAgent);

        toolkit = new Toolkit();
        toolkit.registration()
                .tool(orderTool)
                .tool(afterSalesTool)
                .apply();

        // ==================== 步骤5：配置模型执行参数 ====================
        ExecutionConfig execConfig = ExecutionConfig.builder()
                .timeout(Duration.ofMinutes(3))
                .maxAttempts(1)
                .build();

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))  // 从环境变量读取 API Key，避免硬编码
                .modelName("qwen-max")
                .build();

        // ==================== 步骤6：构建总控大脑 Agent ====================
        // Supervisor Agent 是整个系统的决策中枢，负责：
        // 1. 接收用户的复杂投诉请求
        // 2. 解析用户诉求，决定调用哪些子 Agent
        // 3. 根据子 Agent 的返回结果进行下一步决策
        // 4. 最终汇总所有信息，向用户返回完整的处理结果

        // 创建自定义 Hook 用于监控远程 Agent 调用
        AgentCallMonitorHook monitorHook = new AgentCallMonitorHook();

        supervisorAgent = ReActAgent.builder()
                .name("CustomerServiceSupervisor")
                .model(model)
                .toolkit(toolkit)  // 注入工具包，使 Supervisor 能够调用订单专家和售后专家
                .modelExecutionConfig(execConfig)  // 注入执行配置
                .hooks(List.of(monitorHook))  // 挂载监控钩子
                // 系统提示词（System Prompt）：制定严苛的 SOP（标准作业程序）约束
                .sysPrompt("你是一名资深的客户体验主管，负责调度内部系统。你拥有两个能力强悍的助手：订单专家（order-agent）和售后专家（after-sales-agent）。\n" +
                        "\n" +
                        "【核心规则 - 必须遵守】\n" +
                        "当用户询问订单状态时，你【必须】调用 order-agent 工具。\n" +
                        "当需要发起退款或赔偿时，你【必须】调用 after-sales-agent 工具。\n" +
                        "你【禁止】在回复中只是口头描述要做什么，必须【实际调用】工具。\n" +
                        "\n" +
                        "【执行流程】\n" +
                        "1. 调用 order-agent 查询订单 20260421001 的详细状态\n" +
                        "2. 读取返回的订单状态信息\n" +
                        "3. 如果确认存在质量问题，调用 after-sales-agent 发起工单\n" +
                        "4. 汇总所有信息，向用户提供最终回复")
                .build();
    }

    /**
     * 处理复杂客户投诉请求（对外暴露的业务接口）
     *
     * 工作流程：
     * 1. 接收用户输入的投诉文本
     * 2. 构造消息体，发送给 Supervisor Agent
     * 3. Supervisor Agent 自主决策：
     *    a. 首先调用订单专家查询订单状态
     *    b. 根据订单状态判断是否需要退款/赔偿
     *    c. 如需退款/赔偿，则调用售后专家发起工单
     *    d. 汇总所有子 Agent 的返回结果，生成最终回复
     * 4. 将最终处理结果返回给调用方
     *
     * @param userRequest 用户输入的投诉文本，包含订单号、商品信息、诉求描述等
     * @return 最终的处理结果文本，包含订单状态、处理建议、后续流程说明等完整信息
     */
    public String processComplexCustomerRequest(String userRequest) {
        // 构造用户消息体，使用标准的 Msg 格式
        // Msg 是 AgentScope 框架中的核心消息结构，包含角色、名称、内容等字段
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)  // 指定消息发送者为用户角色
                .name("Customer")   // 用户实体名称
                .textContent(userRequest)  // 用户输入的投诉文本
                .build();

        System.out.println("====== Supervisor 接收到用户请求，开始规划及分发任务 ======");

        // 触发 Supervisor Agent 执行任务
        // 使用 block() 阻塞等待结果，超时时间设为 5 分钟（略大于配置的 3 分钟执行超时）
        // 注意：在真实的 Spring WebFlux 环境中，应返回 Mono<Msg> 由框架层异步处理
        Msg response = supervisorAgent.call(userMsg).block(Duration.ofMinutes(5));

        // 防御性编程：检查返回结果是否为空，避免空指针异常
        return response != null ? response.getTextContent() : "处理超时，请稍后重试。";
    }

    /**
     * Main 方法：用于独立运行和测试 SupervisorCoordinatorService
     *
     * 注意：由于该服务依赖 Spring 容器管理（如 @PostConstruct 初始化），
     * 因此在 main 方法中需要手动构建并初始化 Spring 上下文。
     *
     * 运行前请确保：
     * 1. Nacos 注册中心（127.0.0.1:8848）已启动
     * 2. order-agent 和 after-sales-agent 已注册到 Nacos
     * 3. 环境变量 DASHSCOPE_API_KEY 已配置
     */
    public static void main(String[] args) throws NacosException {
        System.out.println("====== 初始化 SupervisorCoordinatorService ======");

        SupervisorCoordinatorService supervisorCoordinatorService = new SupervisorCoordinatorService();
        supervisorCoordinatorService.init();

        // 构造测试用例：模拟用户投诉场景
        String userComplaint = "我上周购买的 iPhone 15 Pro Max 订单号 20260421001，收到后发现屏幕有坏点，" +
                "而且物流磕碰导致边框有划痕。我要求全额退款并重新发货。";

        System.out.println("====== 用户投诉内容 ======");
        System.out.println(userComplaint);
        System.out.println("=========================\n");

        // 调用服务处理投诉
        long startTime = System.currentTimeMillis();
        String result = supervisorCoordinatorService.processComplexCustomerRequest(userComplaint);
        long endTime = System.currentTimeMillis();

        // 输出处理结果
        System.out.println("\n====== Supervisor 处理结果 ======");
        System.out.println(result);
        System.out.println("\n====== 执行耗时: " + (endTime - startTime) / 1000.0 + " 秒 ======");
    }

    /**
     * Agent 调用监控钩子
     *
     * 用于监控以下关键事件：
     * 1. 远程 Agent 的工具调用（PreActingEvent）- 监控何时调用了 order-agent 或 after-sales-agent
     * 2. Agent 响应（PostCallEvent）- 监控远程 Agent 返回的内容
     *
     * 通过这个 Hook，可以观察到 Supervisor 是否真的调用了远程 Agent，以及远程 Agent 的输出内容
     */
    public static class AgentCallMonitorHook implements Hook {

        @Override
        public int priority() {
            return 1;  // 高优先级，确保在其他 Hook 之前执行
        }

        @Override
        public <T extends HookEvent> Mono<T> onEvent(T event) {
            // 拦截工具执行前事件，监控是否在调用远程 Agent
            if (event instanceof PreActingEvent preActingEvent) {
                String toolName = preActingEvent.getToolUse().getName();
                String toolInput = preActingEvent.getToolUse().getInput().toString();

                System.out.println("\n========== [Hook-监控] 检测到工具调用 ==========");
                System.out.println(">>> 工具名称: " + toolName);
                System.out.println(">>> 调用参数: " + toolInput);

                // 判断是否是远程 Agent（order-agent 或 after-sales-agent）
                if ("order-agent".equals(toolName) || "after-sales-agent".equals(toolName)) {
                    System.out.println(">>> [重要] 检测到远程 Agent 调用！正在通过 A2A 协议委派任务...");
                }
                System.out.println("==============================================\n");
            }

            // 拦截 Agent 调用完成事件，监控返回结果
            if (event instanceof PostCallEvent postCallEvent) {
                Msg response = postCallEvent.getFinalMessage();
                if (response != null) {
                    String agentName = postCallEvent.getAgent() != null ? postCallEvent.getAgent().getName() : "Unknown";
                    String responseContent = response.getTextContent();

                    System.out.println("\n========== [Hook-监控] Agent 响应回调 ==========");
                    System.out.println(">>> Agent 名称: " + agentName);
                    System.out.println(">>> 响应内容: " + (responseContent.length() > 200
                            ? responseContent.substring(0, 200) + "..."
                            : responseContent));
                    System.out.println("==============================================\n");
                }
            }

            return Mono.just(event);
        }
    }

    /**
     * 远程订单 Agent 的本地工具封装类
     * 使用 @Tool 注解让框架自动提取方法签名并生成 Function Calling 接口
     */
    public static class RemoteOrderAgentTool {
        private final A2aAgent agent;

        public RemoteOrderAgentTool(A2aAgent agent) {
            this.agent = agent;
        }

        @Tool(description = "查询订单状态和物流信息。当用户提供订单号时，必须使用此工具查询订单的当前状态。")
        public String queryOrderStatus(
                @ToolParam(name = "orderId", description = "需要查询的订单号") String orderId
        ) {
            try {
                Msg requestMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .textContent("请查询订单 " + orderId + " 的状态")
                        .build();
                Msg response = agent.call(requestMsg).block(Duration.ofSeconds(30));
                return response != null ? response.getTextContent() : "查询超时";
            } catch (Exception e) {
                return "查询失败: " + e.getMessage();
            }
        }
    }

    /**
     * 远程售后 Agent 的本地工具封装类
     * 使用 @Tool 注解让框架自动提取方法签名并生成 Function Calling 接口
     */
    public static class RemoteAfterSalesAgentTool {
        private final A2aAgent agent;

        public RemoteAfterSalesAgentTool(A2aAgent agent) {
            this.agent = agent;
        }

        @Tool(description = "发起售后工单，包括退款、换货等。当确认需要处理质量问题时，使用此工具发起工单。")
        public String createAfterSalesTicket(
                @ToolParam(name = "orderId", description = "订单号") String orderId,
                @ToolParam(name = "reason", description = "售后原因") String reason
        ) {
            try {
                long timestamp = System.currentTimeMillis();
                String dataToSign = orderId + ":" + reason + ":" + timestamp;
                String signature = SecurityUtils.sign(dataToSign);
                
                Msg requestMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .textContent("【安全请求】订单:" + orderId + ", 原因:" + reason + ", 时间戳:" + timestamp + ", 签名:" + signature + "【请验证签名后处理】")
                        .build();
                Msg response = agent.call(requestMsg).block(Duration.ofSeconds(30));
                return response != null ? response.getTextContent() : "工单提交超时";
            } catch (Exception e) {
                return "工单提交失败: " + e.getMessage();
            }
        }
    }

    /**
     * 安全校验工具类
     * 
     * 实现基于 HMAC-SHA256 签名的消息认证机制：
     * 1. 客户端（Supervisor）生成签名，附加在请求消息中
     * 2. 服务端（OrderAgent/AfterSalesAgent）验证签名有效性
     * 
     * 签名格式：HMAC-SHA256(data, secretKey) -> Base64
     */
    public static class SecurityUtils {
        // 共享密钥（生产环境应从安全存储获取，如 Nacos 配置中心或密钥管理系统）
        private static final String SHARED_SECRET_KEY = "a2a-secure-key-2024-supervisor";
        
        // 签名有效期（毫秒），超过此时间戳的请求被视为无效
        private static final long SIGNATURE_VALIDITY_MS = 5 * 60 * 1000; // 5分钟

        /**
         * 生成消息签名
         * @param data 需要签名的数据（通常包含：订单号 + 操作类型 + 时间戳）
         * @return Base64 编码的 HMAC-SHA256 签名
         */
        public static String sign(String data) {
            try {
                Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKeySpec = new SecretKeySpec(
                        SHARED_SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                mac.init(secretKeySpec);
                byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(hmacBytes);
            } catch (Exception e) {
                throw new RuntimeException("签名生成失败", e);
            }
        }

        /**
         * 验证消息签名
         * @param data 原始数据
         * @param signature 待验证的签名
         * @return 签名是否有效
         */
        public static boolean verify(String data, String signature) {
            String expectedSignature = sign(data);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 验证请求有效性（签名 + 时间戳）
         * @param data 原始数据（包含时间戳信息）
         * @param signature 签名
         * @param timestamp 请求时间戳
         * @return 请求是否有效
         */
        public static boolean verifyRequest(String data, String signature, long timestamp) {
            // 1. 验证时间戳有效性（防止重放攻击）
            long currentTime = System.currentTimeMillis();
            if (Math.abs(currentTime - timestamp) > SIGNATURE_VALIDITY_MS) {
                System.out.println("[安全校验] 请求已过期，时间戳:" + timestamp + ", 当前时间:" + currentTime);
                return false;
            }
            
            // 2. 验证签名
            if (!verify(data, signature)) {
                System.out.println("[安全校验] 签名验证失败");
                return false;
            }
            
            return true;
        }
    }
}