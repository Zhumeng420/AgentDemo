package com.example.demo.chapter6.one;

import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.message.Msg;
import com.alibaba.nacos.api.PropertyKeyConst;

import java.time.Duration;
import java.util.Properties;

public class CloudEdgeSupervisorClient {
    public static void main(String args[]) {
        try {
            System.out.println("=== 启动云边智能客服总控系统 (Supervisor 客户端) ===");

            // 1. 初始化 Nacos 客户端，建立服务发现链路
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
            properties.put(PropertyKeyConst.USERNAME, "nacos");
            properties.put(PropertyKeyConst.PASSWORD, "woTZnqgBs1V8");
            AiService aiService = AiFactory.createAiService(properties);
            System.out.println("[系统] 成功连接至 Nacos 注册中心.");

            // 2. 通过 Nacos 解析器构建远程退款风控智能体的本地代理
            // 底层将自动向 Nacos 集群订阅并拉取 finance-agent 节点的 Agent Card 元数据进行路由
            A2aAgent financeAgent = A2aAgent.builder()
                    .name("finance-agent")
                    .agentCardResolver(new NacosAgentCardResolver(aiService))
                    .build();


            AgentCard card = aiService.getAgentCard("finance-agent"); // 需确认是否有此 API
            System.out.println("Resolved Agent Card URL: " + card.getUrl());

            // 3. 模拟大促期间用户的多轮复杂业务对话
            String userQuery = "我的iPhone 15退款进度如何？怎么还没到账？";
            System.out.println("\n[用户输入] : " + userQuery);
            System.out.println("[总控智能体] 意图判定为：[售后退款]。开始通过 A2A 协议委派跨节点 Task 给 Finance Agent...");

            // 4. 发起真实的网络层 A2A 协议调用，使用响应式编程实现流式输出
            Msg requestMsg = Msg.builder().role(MsgRole.USER).textContent(userQuery).build();
            
            // 5. 使用响应式编程处理流式数据
            System.out.print("\n[退款风控智能体] 返回结果: ");
            financeAgent.call(requestMsg)
                .doOnNext(response -> System.out.print(response.getTextContent()))
                .doOnTerminate(() -> System.out.println("\n\n=== A2A 多智能体交互流程结束 ==="))
                .block(Duration.ofSeconds(120));

        } catch (Exception e) {
            System.err.println("系统异常，请检查 Nacos (127.0.0.1:8848) 以及服务端的 FinanceAgentServer 是否均已成功启动。");
            e.printStackTrace();
        }
    }
}