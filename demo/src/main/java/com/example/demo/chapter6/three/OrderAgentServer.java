package com.example.demo.chapter6.three;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.redis.RedisSession;
import io.agentscope.core.state.StatePersistence;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.JedisPooled;

/**
 * 订单处理智能体 A2A 服务端
 *
 * 该服务通过 Spring Boot 启动并注册到 Nacos A2A Registry，
 * 同时通过 Redis 实现分布式上下文管理：
 * - 接收 Supervisor 传递的 sessionId（通过 A2A Header 自动穿透）
 * - 通过 SessionManager + RedisSession 从 Redis 加载/保存 Agent 的对话记忆和工具状态
 * - 处理结果会被持久化到 Redis，供后续的 RefundAgent 读取共享上下文
 */
@SpringBootApplication(exclude = {Neo4jAutoConfiguration.class, io.agentscope.spring.boot.nacos.AgentscopeNacosPromptAutoConfiguration.class})
public class OrderAgentServer {

    public static void main(String[] args) {
        SpringApplication.run(OrderAgentServer.class, args);
        System.out.println("=== 订单处理智能体 A2A 服务端已随 Tomcat 在 8080 端口启动 ===");
    }

    /**
     * 创建 Redis 分布式会话 Bean
     * 所有 Agent 共享同一 Redis 实例和 Key 前缀，确保跨 Agent 上下文可见
     */
    @Bean
    public Session redisSession() {
        JedisPooled jedisClient = new JedisPooled("127.0.0.1", 6379);
        return RedisSession.builder()
                .jedisClient(jedisClient)
                .keyPrefix("agentscope:saga:")  // 与 Supervisor 保持一致的 Key 前缀
                .build();
    }

    @Bean
    public ReActAgent orderAgent(Session redisSession) {
        // 1. 初始化真实的 LLM（请替换为你真实的阿里云百炼 API-KEY）
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-xxxxxxxxxxxxx")
                .modelName("qwen-plus")
                .stream(true)
                .build();

        // 2. 构建 Agent
        // 注意：这里的 name("order-agent") 必须与客户端查找的名称完全一致，
        // 这样客户端才能通过 Nacos 正确路由到这个实例。
        //
        // statePersistence(StatePersistence.all())：启用全量状态持久化
        // 框架会在 A2A 请求到达时，自动通过 Header 中的 sessionId 从 Redis 加载上下文，
        // 并在请求处理完成后将 memory、toolkit、planNotebook 等状态写回 Redis。
        return ReActAgent.builder()
                .name("order-agent")
                .sysPrompt("你是一个专业的订单处理专家，负责核实系统的订单状态。只允许回复客观的订单数据，不要加入主观情绪。" +
                        "当用户询问订单状态时，必须提供准确的订单信息，包括订单号、状态、物流进度等。" +
                        "支持分布式事务处理，能够与主管智能体和退款智能体协同工作。" +
                        "你的对话记忆和处理状态会通过 Redis 实现跨 Agent 共享，后续的退款 Agent 可以读取你写入的上下文。")
                .model(model)
                .statePersistence(StatePersistence.all()) // 开启全量状态持久化到 Redis
                .hook(new GlobalLoggingHook())            // 挂载全局日志监控
                .build();
    }
}