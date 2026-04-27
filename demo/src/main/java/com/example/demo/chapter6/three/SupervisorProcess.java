package com.example.demo.chapter6.three;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.SessionManager;
import io.agentscope.core.session.redis.RedisSession;
import redis.clients.jedis.JedisPooled;

import java.util.Properties;

/**
 * Supervisor 协调进程 —— 分布式多 Agent Saga 编排器
 *
 * 核心能力：
 * 1. 通过 Nacos 服务发现定位远程 OrderAgent / RefundAgent
 * 2. 使用 Redis 作为分布式 Session 后端，实现跨 Agent 的上下文共享
 * 3. 基于 Saga 模式进行异常补偿，并在崩溃后通过 Redis 恢复状态
 *
 * 分布式上下文传递流程：
 *   Supervisor 创建 globalSessionId → 写入 Redis
 *       ↓ A2A call（sessionId 随 Header 穿透）
 *   OrderAgent 接收 sessionId → 从 Redis 加载上下文 → 处理 → 回写 Redis
 *       ↓ A2A call
 *   RefundAgent 接收 sessionId → 从 Redis 加载上下文（含 OrderAgent 写入的数据） → 处理 → 回写 Redis
 *       ↓
 *   Supervisor 保存最终上下文到 Redis（包含完整链路快照）
 */
public class SupervisorProcess {

    // Redis 连接配置（生产环境应从配置中心读取）
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;

    public static void main(String[] args) throws Exception {

        // ==================== 1. 初始化 Redis 分布式会话后端 ====================
        // 使用 Jedis 连接池连接本地 Redis，作为跨 Agent 上下文共享的存储介质
        JedisPooled jedisClient = new JedisPooled(REDIS_HOST, REDIS_PORT);
        Session redisSession = RedisSession.builder()
                .jedisClient(jedisClient)       // 传入 Jedis 客户端实例
                .keyPrefix("agentscope:saga:")  // 统一的 Key 前缀，便于在 Redis 中分区管理
                .build();
        System.out.println("[Redis] 分布式会话后端初始化完成: " + REDIS_HOST + ":" + REDIS_PORT);

        // ==================== 2. 初始化 Nacos Client 以获取远程 Agent 列表 ====================
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        AiService aiService = AiFactory.createAiService(properties);
        NacosAgentCardResolver resolver = new NacosAgentCardResolver(aiService);

        // ==================== 3. 通过 A2A 协议与 Nacos 动态生成远程 Agent 的代理 ====================
        A2aAgent orderA2aAgent = A2aAgent.builder()
                .name("OrderAgent")
                .agentCardResolver(resolver)
                .build();

        A2aAgent refundA2aAgent = A2aAgent.builder()
                .name("RefundAgent")
                .agentCardResolver(resolver)
                .build();

        // ==================== 4. 构建全局会话ID，保障分布式上下文在所有 Agent 间共享 ====================
        // 该 sessionId 将随 A2A 协议 Header 自动穿透到远程 Agent 容器中，
        // 各 Agent 通过同一 sessionId 从 Redis 读写共享上下文，实现跨进程状态同步
        String globalSessionId = "COMPLAINT_SESSION_20261111";

        // 初始化 SessionManager，绑定 Redis 后端并尝试加载已有的会话数据
        // 如果是首次执行，loadIfExists() 不会报错，只是不加载任何历史状态
        // 如果是崩溃重启，loadIfExists() 将自动恢复上次中断点的完整上下文
        SessionManager sessionManager = SessionManager.forSessionId(globalSessionId)
                .withSession(redisSession);
        sessionManager.loadIfExists();
        System.out.println("[Session] 分布式会话已初始化, sessionId=" + globalSessionId);

        System.out.println("\n 接收到用户客诉：订单没收到，要求退款！");
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent("用户诉求：查询订单 T9987 并办理退款")
                .build();

        try {
            // ================== Saga Step 1: 订单合法性校验 ==================
            System.out.println("-> [Saga Step 1] 将任务派发给 OrderAgent...");
            // A2A 底层会自动将 globalSessionId 放入请求 Header，
            // 远程 OrderAgent 通过该 sessionId 从 Redis 加载/保存共享上下文
            Msg orderResponse = orderA2aAgent.call(userMsg).block();
            System.out.println("<- [Saga Step 1] 收到订单反馈: " + orderResponse.getTextContent());

            // 将 OrderAgent 的处理结果同步保存到 Redis，确保后续 Agent 可见
            sessionManager.saveSession();
            System.out.println("[Session] Saga Step 1 上下文已持久化到 Redis");

            // 基于订单状态决策是否触发退款流程（简单的包含判断模拟状态机，实际可接入 StateGraph 组件）
            if (orderResponse.getTextContent().contains("未发货") || orderResponse.getTextContent().contains("异常")) {

                // ================== Saga Step 2: 触发实际退款/AI自主补偿 ==================
                System.out.println("-> [Saga Step 2] 订单异常属实，移交 RefundAgent 执行退款及安抚...");
                // RefundAgent 通过同一 sessionId 从 Redis 读取 OrderAgent 写入的上下文信息，
                // 实现跨 Agent 的上下文共享（如订单详情、异常原因等）
                Msg refundResponse = refundA2aAgent.call(orderResponse).block();
                System.out.println("<- [Saga Step 2] 收到退款与补偿方案: " + refundResponse.getTextContent());

                // 将 RefundAgent 的处理结果也同步到 Redis
                sessionManager.saveSession();
                System.out.println("[Session] Saga Step 2 上下文已持久化到 Redis");

            } else {
                System.out.println(" 订单状态正常，驳回退款请求。");
            }

        } catch (Exception e) {
            // ================== SAGA 异常捕获与框架级重试机制 ==================
            // 由于每个 Saga Step 完成后都已将上下文写入 Redis，
            // 下次重启时 loadIfExists() 将自动恢复到崩溃前的最后一个成功节点
            System.err.println(" 检测到链路崩溃，由于已配置 RedisSession，下一次重试将直接从崩溃节点恢复状态...");
            e.printStackTrace();
        } finally {
            // 最终兜底：无论成功还是失败，都将当前多轮对话与工具状态写回 Redis
            sessionManager.saveSession();
            System.out.println(" 流程持久化完成，所有分布式上下文已写入 Redis。");

            // 释放 Redis 连接资源
            jedisClient.close();
        }
    }
}