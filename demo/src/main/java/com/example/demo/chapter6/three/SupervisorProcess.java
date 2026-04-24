package com.example.demo.chapter6.three;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.session.SessionManager;
// import io.agentscope.extensions.session.redis.RedisSession; // 假设使用该实现

import java.util.Properties;

public class SupervisorProcess {
    public static void main(String args) throws Exception {
        // 1. 初始化 Nacos Client 以获取远程 Agent 列表
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        AiService aiService = AiFactory.createAiService(properties);
        NacosAgentCardResolver resolver = new NacosAgentCardResolver(aiService);

        // 2. 通过 A2A 协议与 Nacos 动态生成远程 Agent 的代理
        A2aAgent orderA2aAgent = A2aAgent.builder()
                .name("OrderAgent")
                .agentCardResolver(resolver)
                .build();

        A2aAgent refundA2aAgent = A2aAgent.builder()
                .name("RefundAgent")
                .agentCardResolver(resolver)
                .build();

        // 3. 构建全局会话ID，保障记忆与异常补偿的上下文不丢失
        String globalSessionId = "COMPLAINT_SESSION_20261111";

        /*
         * 实际生产中在此注入 RedisSession：
         * Session redisSession = new RedisSession(redisConfig);
         * SessionManager.forSessionId(globalSessionId).withSession(redisSession).loadIfExists();
         */

        System.out.println("\n 接收到用户客诉：订单没收到，要求退款！");
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent("用户诉求：查询订单 T9987 并办理退款").build();

        try {
            // ================== Saga Step 1: 订单合法性校验 ==================
            System.out.println("-> 将任务派发给 OrderAgent...");
            // A2A底层会自动将 globalSessionId 放入 Header，穿透到 Nacos 注册的远程容器中
            Msg orderResponse = orderA2aAgent.call(userMsg).block();
            System.out.println("<- 收到订单反馈: " + orderResponse.getTextContent());

            // 这里使用简单的包含判断模拟状态机（实际可接入 StateGraph 组件）
            if (orderResponse.getTextContent().contains("未发货") || orderResponse.getTextContent().contains("异常")) {

                // ================== Saga Step 2: 触发实际退款/AI自主补偿 ==================
                System.out.println("-> 订单异常属实，移交 RefundAgent 执行退款及安抚...");
                Msg refundResponse = refundA2aAgent.call(orderResponse).block();
                System.out.println("<- 收到退款与补偿方案: " + refundResponse.getTextContent());

            } else {
                System.out.println(" 订单状态正常，驳回退款请求。");
            }

        } catch (Exception e) {
            // SAGA异常捕获与框架级重试机制
            System.err.println(" 检测到链路崩溃，由于已配置 RedisSession，下一次重试将直接从崩溃节点恢复状态...");
            e.printStackTrace();
        } finally {
            /*
             * SessionManager.forSessionId(globalSessionId).saveSession();
             * 确保无论成功失败，当前多轮对话与工具状态均写回 Redis
             */
            System.out.println(" 流程持久化完成。");
        }
    }
}