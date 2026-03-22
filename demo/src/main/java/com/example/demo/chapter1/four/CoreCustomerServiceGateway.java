package com.example.demo.chapter1.four;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.model.Model;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import java.time.Duration;

public class CoreCustomerServiceGateway {
    private static final Logger gatewayLog = LoggerFactory.getLogger(CoreCustomerServiceGateway.class);

    private static String qwen = "xxxxxxxxxxxxxxxxxxxx";
    private static String gemini = "xxxxxxxxxxxxxxxxxxxxxxxxxx";


    // 将单纯的模型提升为具备身份设定的独立 Agent，规范输入输出标准
    private final ReActAgent intentClassifierAgent;
    private final ReActAgent simpleQuerySpecialist;
    private final ReActAgent emotionalCareSpecialist;

    // 分别定义主备技术排障专家与熔断器
    private final ReActAgent primaryTechSpecialist;
    private final ReActAgent fallbackTechSpecialist;
    private final CircuitBreaker failoverBreaker;

    public CoreCustomerServiceGateway() {
        // 构建廉价且高速的轻量级底层模型
        Model lightweightModel =DashScopeChatModel.builder()
                .apiKey(qwen )
                .modelName("qwen-plus")
                .build();


        // 实例化业务组件零：专门负责解析意图的路由分配代理
        this.intentClassifierAgent = ReActAgent.builder()
                .name("ClassifierAgent")
                .sysPrompt("你是一个精准的意图分类器。请认真分析用户的请求，并仅输出路由标签。不要输出任何解释性文本。")
                .model(lightweightModel)
                .build();

        // 实例化业务组件一：面向海量低门槛查询的急速应答专家
        this.simpleQuerySpecialist = ReActAgent.builder()
                .name("QuerySpecialist")
                .sysPrompt("您是高效的物流与账户状态查询助手。工作原则：语言极简、只输出关键事实，切忌长篇大论。")
                .model(lightweightModel)
                .build();

        // 实例化业务组件二：具备情绪干预体系的客诉专家
        this.emotionalCareSpecialist = ReActAgent.builder()
                .name("EmotionalCareSpecialist")
                .sysPrompt("您是资深的争议解决专家。当面临用户的愤怒或沮丧情绪时，请优先表达理解与歉意，并提供一套合理且具有安抚性质的补偿方案草案。")
                .model(lightweightModel)
                .build();

        // 构建主选前沿推理模型
        Model primaryComputeEngine =  OpenAIChatModel.builder()
                .apiKey(gemini)
                .modelName("gemini-3.1-pro-preview")
                // 将请求路由到 Google 官方的 OpenAI 兼容网关
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
                .build();

        // 构建部署于同一内网或高可用区域的备用容灾模型
        Model fallbackComputeEngine = DashScopeChatModel.builder()
                .apiKey(qwen)
                .modelName("qwen3-max")
                .build();

        // 实例化业务组件三（主节点）：接管高难度技术栈排障
        this.primaryTechSpecialist = ReActAgent.builder()
                .name("PrimaryTechSpecialist")
                .sysPrompt("您是 L3 级高级技术支持工程师。请以极度严谨的逻辑分析硬件或软件错误代码。")
                .model(primaryComputeEngine)
                .build();

        // 实例化业务组件三（备节点）：用于在主节点宕机时兜底
        this.fallbackTechSpecialist = ReActAgent.builder()
                .name("FallbackTechSpecialist")
                .sysPrompt("您是 L3 级高级技术支持工程师。请以极度严谨的逻辑分析硬件或软件错误代码。")
                .model(fallbackComputeEngine)
                .build();

        // 编排断路器的敏感度矩阵：设定当请求失败率越过 50% 红线时执行物理熔断，
        // 随后在全封闭状态下强制冷却 30 秒，并仅允许 3 个微量探针请求进入半开状态测试恢复度
        CircuitBreakerConfig circuitStrategy = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        this.failoverBreaker = CircuitBreaker.of("TechOperationsFailover", circuitStrategy);
    }

    /**
     * 接收并处理前端用户发送的原始异构文本数据
     * 该方法必须保证全链条非阻塞运行，以支撑企业网关的高并发吞吐要求
     */
    public Mono<Msg> routeAndProcess(String rawUserInput) {
        String classificationPrompt = String.format(
                "深度解析以下客服对话场景的用户核心诉求，并强制且仅能输出一个路由标签。可选的路由策略标签为：" +
                        "HIGH_RISK_COMPLAINT（高危客诉）, DEEP_TECH_ISSUE（深度技术排障）, SIMPLE_QUERY（基础状态查询）。\n用户原始语料：%s",
                rawUserInput
        );
        Msg classificationMsg = Msg.builder().role(MsgRole.USER).textContent(classificationPrompt).build();

        // 发起异步意图分类探测，使用标准的 Agent.call 进行调用以确保 API 稳定性
        return intentClassifierAgent.call(classificationMsg)
                .flatMap(modelResponse -> {
                    // Agent 返回的结果是 Msg，使用 getTextContent() 方法获取核心文本信息
                    String routingIntent = modelResponse.getTextContent().trim().toUpperCase();
                    gatewayLog.info("流量拦截与解析完毕，判定业务域意图为: [{}]", routingIntent);

                    Msg normalizedTarget = Msg.builder().role(MsgRole.USER).textContent(rawUserInput).build();

                    // 根据语义意图将计算流重定向至具体的专家沙箱
                    if (routingIntent.contains("HIGH_RISK_COMPLAINT")) {
                        return emotionalCareSpecialist.call(normalizedTarget);
                    } else if (routingIntent.contains("DEEP_TECH_ISSUE")) {
                        return primaryTechSpecialist.call(normalizedTarget)
                                .transformDeferred(CircuitBreakerOperator.of(failoverBreaker)) // 植入断路器屏障
                                // 显式声明 Throwable 类型，引导 Java 编译器执行正确的类型推断，避免日志模块重载冲突
                                .onErrorResume((Throwable exception) -> {
                                    gatewayLog.warn("主节点服务异常或已被隔离，异常根因: {}。触发平滑降级至备用专家节点...", exception.getMessage());
                                    return fallbackTechSpecialist.call(normalizedTarget);
                                });
                    } else {
                        // 对于任何无法匹配甚至模型解析偏差的情况，实施安全收敛策略，统一降维给普通查询专家处理
                        return simpleQuerySpecialist.call(normalizedTarget);
                    }
                })
                // 部署架构级的硬兜底防线：一旦整个智能体路由与执行网络彻底瘫痪，必须保证 API 不抛出 500
                .onErrorResume((Throwable fatalException) -> {
                    gatewayLog.error("核心网关运转过程中遭遇未捕获的致命故障，系统正在启动被动防御机制: ", fatalException);
                    return Mono.just(Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .textContent("亲爱的用户您好，当前接待中心线路过于繁忙。为了保障您的体验，我们已为您分配了专属人工客服，请稍作等待。")
                            .build());
                });
    }

    /**
     * 用于本地执行与逻辑验证的 Main 方法
     */
    public static void main(String args[]) {
        // 【注意】在基于 Project Reactor 的系统中，.block() 方法绝对禁止出现在生产环境的 Controller 或业务 Service 中！
        // 只有在此类 main 方法测试、或是编写单元测试时，为了防止控制台主线程提前终止，才可以调用.block() 阻塞等待异步结果。
        CoreCustomerServiceGateway gateway = new CoreCustomerServiceGateway();

        System.out.println("========== 正在启动网关本地验证 ==========\n");

        System.out.println(">>> 场景一：基础状态查询");
        String query1 = "帮我查一下昨天买的手机发货了没有？";
        System.out.println("User: " + query1);
        Msg res1 = gateway.routeAndProcess(query1).block();
        System.out.println("Agent: " + res1.getTextContent() + "\n");

        System.out.println(">>> 场景二：深度技术排障（触发主大模型/若环境未配 Key 将自动熔断至备用模型）");
        String query2 = "我的智能路由器一直红灯闪烁，系统后台日志一直提示鉴权拒绝错误代码 1002，请排查原因";
        System.out.println("User: " + query2);
        Msg res2 = gateway.routeAndProcess(query2).block();
        System.out.println("Agent: " + res2.getTextContent() + "\n");

        System.out.println(">>> 场景三：客诉冲突与情绪安抚");
        String query3 = "你们平台卖的生鲜完全是坏的！品控太差劲了，如果今天不给我全额退款我立刻去投诉！";
        System.out.println("User: " + query3);
        Msg res3 = gateway.routeAndProcess(query3).block();
        System.out.println("Agent: " + res3.getTextContent() + "\n");

        System.out.println("========== 本地验证结束 ==========");
    }
}