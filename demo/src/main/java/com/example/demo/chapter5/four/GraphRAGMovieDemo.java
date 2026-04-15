package com.example.demo.chapter5.four;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;

public class GraphRAGMovieDemo {
    public static void main(String args[]) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        DashScopeChatModel qwenModel = DashScopeChatModel.builder()
                .apiKey(apiKey).modelName("qwen-max").build();


        String neo4jUri = System.getenv("NEO4J_URL");
        String neo4jUser = System.getenv("NEO4J_USER");
        String neo4jPassword = System.getenv("NEO4J_PASSWORD");
        // 实例化定制开发的图数据库接入层
        Neo4jGraphKnowledge graphKnowledge = new Neo4jGraphKnowledge(
                neo4jUri, neo4jUser, neo4jPassword, apiKey
        );

        ReActAgent movieAssistant = ReActAgent.builder()
                .name("MovieExpertAgent")
                .sysPrompt("您是一位资深的影评人。请依据为您提供的【图谱事实萃取】信息，准确回答关于电影的演职人员异同。严禁捏造未在图谱记录中的人员名字。" )
                .model(qwenModel)
                .knowledge(graphKnowledge)
                .ragMode(RAGMode.GENERIC) // 开启通用RAG拦截模式
                .retrieveConfig(RetrieveConfig.builder().limit(3).build())
                .build();

        Msg complexQuery = Msg.builder()
                .textContent("对比《The Matrix》和《The Matrix Reloaded》，它们有哪些共同的演职人员？又有哪些各自独有的人员？")
                .build();

        // 执行处理流：拦截请求 -> 生成Cypher -> 提取图谱事实 -> Prompt组装 -> Qwen-Max 推理
        Msg finalResponse = movieAssistant.call(complexQuery).block();
        System.out.println("【智能体深度解析响应】\n" + finalResponse.getTextContent());
    }
}