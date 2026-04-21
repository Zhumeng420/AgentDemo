package com.example.demo.chapter6.two;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
@Service
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
     *
     * 采用 @PostConstruct 的原因：
     * 1. Nacos 连接、Agent 代理构建、Toolkit 注册等操作属于一次性初始化开销
     * 2. 如果放在业务方法中每次调用都会重复执行，严重影响性能
     * 3. Spring 会在 Bean 初始化完成后自动调用此方法，确保服务启动时完成所有准备工作
     *
     * @throws NacosException 如果无法连接到 Nacos 注册中心
     */
    @PostConstruct
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

        // ==================== 步骤4：将远程 Agent 转换为本地 Tool ====================
        toolkit = new Toolkit();
        toolkit.registerTool(remoteOrderAgent);
        toolkit.registerTool(remoteAfterSalesAgent);

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
        supervisorAgent = ReActAgent.builder()
                .name("CustomerServiceSupervisor")
                .model(model)
                .toolkit(toolkit)  // 注入工具包，使 Supervisor 能够调用订单专家和售后专家
                .modelExecutionConfig(execConfig)  // 注入执行配置
                // 系统提示词（System Prompt）：制定严苛的 SOP（标准作业程序）约束
                // 核心规则：必须先查订单状态，再根据事实依据决定是否发起售后工单
                .sysPrompt("你是一名资深的客户体验主管，负责调度内部系统。你拥有两个能力强悍的助手：订单专家和售后专家。\n" +
                        "核心原则：面对用户的多重诉求，你不能瞎猜状态。必须【首先】调用订单专家获取权威的订单流转状态。\n" +
                        "【随后】，如果事实依据表明需要退款或赔偿，你才被允许调用售后专家发起工单。\n" +
                        "最后，由你汇总双方的信息，以专业、亲切的语气向用户解释全流程处理结果。")
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
    public static void main(String[] args) {
        System.out.println("====== 初始化 Spring 容器 ======");

        // 手动构建 Spring 应用上下文（AnnotationConfigApplicationContext）
        // 由于 SupervisorCoordinatorService 是一个 @Service 注解的类，
        // 我们需要使用 AnnotationConfigApplicationContext 来加载它
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(SupervisorCoordinatorService.class)) {

            System.out.println("====== Spring 容器初始化完成 ======");

            // 从容器中获取 SupervisorCoordinatorService Bean
            SupervisorCoordinatorService service = context.getBean(SupervisorCoordinatorService.class);

            // 构造测试用例：模拟用户投诉场景
            String userComplaint = "我上周购买的 iPhone 15 Pro Max 订单号 20260421001，收到后发现屏幕有坏点，" +
                    "而且物流磕碰导致边框有划痕。我要求全额退款并重新发货。";

            System.out.println("====== 用户投诉内容 ======");
            System.out.println(userComplaint);
            System.out.println("=========================\n");

            // 调用服务处理投诉
            long startTime = System.currentTimeMillis();
            String result = service.processComplexCustomerRequest(userComplaint);
            long endTime = System.currentTimeMillis();

            // 输出处理结果
            System.out.println("\n====== Supervisor 处理结果 ======");
            System.out.println(result);
            System.out.println("\n====== 执行耗时: " + (endTime - startTime) / 1000.0 + " 秒 ======");

        } catch (Exception e) {
            System.err.println("====== 运行时发生异常 ======");
            e.printStackTrace();
            System.err.println("=========================");
            System.err.println("请检查以下条件是否满足：");
            System.err.println("1. Nacos 注册中心（127.0.0.1:8848）是否已启动");
            System.err.println("2. order-agent 和 after-sales-agent 是否已注册到 Nacos");
            System.err.println("3. 环境变量 DASHSCOPE_API_KEY 是否已配置");
        }
    }
}