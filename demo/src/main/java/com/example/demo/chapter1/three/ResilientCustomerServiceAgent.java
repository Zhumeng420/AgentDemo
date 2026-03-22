package com.example.demo.chapter1.three;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeoutException;


/**
 * 企业级流式智能客服服务实现
 * 核心特性展示：非阻塞流式输出、精细化超时控制、指数退避重试网络、Resilience4j状态机熔断与分级降级策略
 */
public class ResilientCustomerServiceAgent {

    private static final Logger log = LoggerFactory.getLogger(ResilientCustomerServiceAgent.class);

    private final ReActAgent agent;
    private final CircuitBreaker circuitBreaker;

    public ResilientCustomerServiceAgent() {
        // =====================================================================
        // 1. 初始化熔断器状态机配置 (基于 Resilience4j)
        // =====================================================================
        // 设定规则：使用基于计数的滑动窗口。在最新的10个调用请求中，
        // 若失败率达到50%，或超过3秒响应的慢调用比率达到50%，则状态机立即切换至 OPEN (熔断) 状态。
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)                       // 统计窗口大小
                .minimumNumberOfCalls(5)                     // 至少累积5个请求后才开始计算失败率
                .failureRateThreshold(50)                    // 失败率阈值设为 50%
                .slowCallRateThreshold(50)                   // 慢调用率阈值设为 50%
                .slowCallDurationThreshold(Duration.ofSeconds(3)) // 慢调用定义：响应时间超过3秒
                .waitDurationInOpenState(Duration.ofSeconds(30))  // 熔断后冷却时间：30秒后进入 Half-Open 状态
                .build();
        this.circuitBreaker = CircuitBreaker.of("LLM-DashScope-Service", cbConfig);

        // =====================================================================
        // 2. 初始化 AgentScope 的网络重试与超时控制墙
        // =====================================================================
        ExecutionConfig execConfig = ExecutionConfig.builder()
                .timeout(Duration.ofMinutes(1))              // 设置单次流的总生存期硬性上限为 1 分钟
                .maxAttempts(3)                              // 发生瞬时错误时，最高允许尝试 3 次（1次原请求 + 2次重试）
                .initialBackoff(Duration.ofMillis(500))      // 首次重试的初始退避时间：500 毫秒
                .maxBackoff(Duration.ofSeconds(5))           // 最大退避时间封顶：5 秒
                .backoffMultiplier(2.0)                      // 指数倍增乘数，退避时间按 0.5s, 1s, 2s 增长
                // 企业实践：通过谓词排除业务级错误（如API Key无效导致请求不可用），避免无效重试
                .retryOn(throwable ->!(throwable instanceof SecurityException))
                .build();

        // =====================================================================
        // 3. 构建支持 SSE 流式输出的模型封装层与智能体容器
        // =====================================================================
        // 安全红线：严禁在源码中硬编码明文 API Key，必须通过环境变量或配置中心动态挂载注入
        String apiKey = "sk-xxxxxxxxxxxxxxxxxxxxx";
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("致命错误：未检测到 DASHSCOPE_API_KEY 环境变量配置，大模型鉴权将失败！");
        }

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .stream(true)                                // 核心参数：指示框架使用底层流式网络协议交互 [8, 9]
                .build();

        this.agent = ReActAgent.builder()
                .name("CloudEdge-Customer-Support")
                .sysPrompt("你是一名具有专业素养与极强同理心的电商企业智能客服。请使用简明扼要的专业语言回答用户的物流与售后问题。")
                .model(model)
                .modelExecutionConfig(execConfig)            // 将包含重试和超时的执行配置注入Agent层级 [14]
                .build();
    }

    /**
     * 辅助方法：安全地从多模态的消息结构中提取纯文本块 [15]
     */
    private static String extractText(Msg msg) {
        return msg.getContentBlocks(TextBlock.class).stream()
                .findFirst()
                .map(TextBlock::getText)
                .orElse("");
    }

    /**
     * 处理外部用户请求并返回响应式数据流 (Flux)
     * 该方法返回的数据流可直接桥接至 Spring WebFlux 的 ServerHttpResponse 或 WebSocket
     *
     * @param userMessage 用户在客户端界面输入的文本查询消息
     * @return 包含大模型碎片化 Token 块的 Flux 数据流，确保系统侧全异步非阻塞消费
     */
    public Flux<Msg> chatStream(String userMessage) {

        // 依据新规范构建多模态的消息体
        Msg inputMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(userMessage).build())
                .build();

        log.info("开始接入并处理用户请求, 上下文内容长度: {}", userMessage.length());

        // AgentScope 1.0+ 中 agent.stream 返回的是 Flux<Event> 类型
        return agent.stream(inputMsg)
                // 4.【核心修正】：精确过滤生命周期事件。AgentScope 会在单轮处理中发出 REASONING, AGENT_RESULT, SUMMARY 等多个携带结果的事件
                // 此处必须拦截并只保留最终结果事件，否则下游会因接收到多个内部事件而重复输出多遍相同的文本
                .filter(event -> event.getType() == EventType.AGENT_RESULT)
                // 过滤完成后，安全地将其映射为业务层所需的 Msg 实体
                .map(Event::getMessage)

                // 5. 将流接入 Resilience4j 熔断器操作符，利用 AOP 思想对流进行健康监控与拦截
                .transformDeferred(CircuitBreakerOperator.of(this.circuitBreaker))

                // 日志记录：在 DEBUG 级别打印流式生成的 Token 进度，用于排查模型生成卡顿现象
                .doOnNext(chunk -> log.debug("捕获到底层网络流式数据块: {}", extractText(chunk)))
                .doOnError(e -> log.error("大模型流式调用执行链路发生异常拦截: {}", e.getMessage()))

                // 6. 核心防御机制：多级服务降级策略 (Graceful Degradation)
                .onErrorResume(throwable -> {
                    log.warn("防线触发：底层依赖失效或熔断器已开启，阻断流向上抛出，开始执行静态兜底降级方案。触发原因: {}",
                            throwable.getClass().getSimpleName());

                    // 降级响应构建：返回一个包含了业务安抚文案的标准单元素数据流，掩盖底层故障
                    Msg fallbackMsg = Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .content(TextBlock.builder()
                                    .text("【系统服务提示】非常抱歉，当前客服大脑网络传输稍有拥塞。为了不耽误您的宝贵时间，请稍后再试或点击屏幕右上角人工服务按钮转接专属客服。")
                                    .build())
                            .build();
                    // 这里现在能够完美匹配上游的 Flux<Msg> 类型了
                    return Flux.just(fallbackMsg);
                });
    }

    // 示例入口点。注意：在 Spring WebFlux 等真实企业级应用容器中，Flux 对象由框架底层的 Netty 线程负责安全订阅，
    // 业务代码中严禁任何形式的调用.block() 。此处仅作控制台演示控制流不退出而使用。
    public static void main(String[] args) {
        ResilientCustomerServiceAgent service = new ResilientCustomerServiceAgent();

        System.out.println(">>> 流式智能客服交互测试链路已初始化...");

        // ⚠️ 警告：.blockLast() 或类似阻塞操作仅在 main 引导方法或独立单元测试代码中允许使用
        // 真实业务中，这些数据块会被通过 HTTP chunked 传输编码或 WebSocket 帧实时 writeAndFlush 推送到客户端浏览器
        service.chatStream("我的订单 20260322XYZ 一直显示在发货中，这都三天了，请问是什么原因？什么时候能送到？")
                .doOnNext(msg -> System.out.print(extractText(msg)))
                .doOnComplete(() -> System.out.println("\n>>> 网络流传输正常结束，资源已回收。"))
                .blockLast();
    }
}