package com.example.demo.chapter5.four;


import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 企业级 GraphRAG 自定义知识库实现
 * 继承自 AgentScope 框架的 Knowledge 接口，实现与 Neo4j 电影图数据库的深度整合
 */
public class Neo4jGraphKnowledge implements Knowledge {

    private final Driver neo4jDriver;
    // 假设引入了一个专门负责将自然语言转化为Cypher逻辑的内部服务/Agent
    private final Text2CypherService cypherService;

    public Neo4jGraphKnowledge(String uri, String user, String password, String apiKey) {
        // 初始化 Neo4j 高性能连接池驱动
        this.neo4jDriver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        this.cypherService = new Text2CypherService(apiKey, neo4jDriver);
    }

    /**
     * 核心实现方法：接受自然语言查询，执行图数据库召回，并封装为响应式 Document 流
     */
    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        return Mono.fromCallable(() -> {
            List<Document> resultDocuments = new ArrayList<>();
            String cypherQuery = cypherService.generateCypher(query);
            System.out.println("\n========== 基于动态 Schema 生成的 Cypher 语句 ==========");
            System.out.println( cypherQuery);

            try (Session session = neo4jDriver.session()) {
                Result result = session.run(cypherQuery);

                while (result.hasNext()) {
                    Record record = result.next();
                    String contextSegment = formatRecordToText(record);
                    System.out.println("========== GraphRAG 检索结果 ==========");
                    System.out.println(contextSegment);

                    ContentBlock textBlock = TextBlock.builder().text(contextSegment).build();
                    DocumentMetadata metadata = DocumentMetadata.builder()
                            .content(textBlock)
                            .docId("neo4j_doc_" + UUID.randomUUID().toString())
                            .chunkId("neo4j_chunk_0")
                            .payload(Map.of(
                                    "source_system", "Neo4j_Enterprise_Graph",
                                    "query_mode", "Topology_Difference_Analysis"
                            ))
                            .build();

                    Document doc = new Document(metadata);
                    resultDocuments.add(doc);
                }
            } catch (Exception e) {
                // 打印完整的堆栈信息，避免底层数据库语法错误被掩盖
                System.err.println("Graph traversal failed. Error type: " + e.getClass().getSimpleName());
                System.err.println("Error message: " + e.getMessage());
                e.printStackTrace();
            }

            return resultDocuments;
        });
    }

    /**
     * 将图数据库的键值记录转化为适合大模型阅读的语义化结构
     */
    private String formatRecordToText(Record record) {
        StringBuilder sb = new StringBuilder();
        sb.append("【电影图谱事实萃取】\n");
        Map<String, Object> map = record.asMap();
        map.forEach((key, value) -> {
            sb.append("- ").append(key).append(": ").append(value.toString()).append("\n");
        });
        return sb.toString();
    }



    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        return Mono.error(new UnsupportedOperationException("Graph indexing should be processed via offline pipelines."));
    }

    /**
     * =======================
     * 测试运行入口 (Movie 数据集场景)
     * =======================
     */
    public static void main(String args[]) {
        // 1. 获取大模型 API Key 环境变量
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("测试失败：未找到 DASHSCOPE_API_KEY 环境变量，请配置系统环境变量。");
            return;
        }

        // 2. 模拟您的 Neo4j 数据库连接信息（请确保本地 Neo4j 已启动并导入了原生的 Movie 数据集）
        String neo4jUri = System.getenv("NEO4J_URL");
        String neo4jUser = System.getenv("NEO4J_USER");
        String neo4jPassword = System.getenv("NEO4J_PASSWORD");

        System.out.println("正在初始化 Neo4j GraphRAG 系统 (Movie 电影数据集)...");
        Neo4jGraphKnowledge knowledge = new Neo4jGraphKnowledge(neo4jUri, neo4jUser, neo4jPassword, apiKey);

        // 3. 模拟用户发起的复杂电影关系对比查询
        String testQuery = "对比《The Matrix》和《The Matrix Reloaded》，它们有哪些共同的演职人员？又有哪些各自独有的人员？";
        System.out.println("\n发起自然语言图谱检索：" + testQuery + "\n");
        System.out.println("正在将自然语言转化为 Cypher 并执行图数据库子图召回，请稍候...\n");

        try {

            // 在 main 测试函数中，使用.block() 安全地阻塞提取响应式流 (Mono) 的执行结果
            List<Document> retrievedDocs = knowledge.retrieve(testQuery, null).block();

            System.out.println("========== GraphRAG 检索结果 ==========");
            if (retrievedDocs!= null &&!retrievedDocs.isEmpty()) {
                for (int i = 0; i < retrievedDocs.size(); i++) {
                    System.out.println("--- 匹配子图萃取结果 " + (i + 1) + " ---");
                    Document doc = retrievedDocs.get(i);
                    DocumentMetadata meta = doc.getMetadata();

                    if (meta!= null) {
                        // 提取封装在 TextBlock 中的业务属性
                        System.out.println("内容: \n" + meta.getContentText());
                        System.out.println("文档ID: " + meta.getDocId());
                        System.out.println("元数据溯源: " + meta.getPayload() + "\n");
                    } else {
                        System.out.println("内容: \n" + doc.toString() + "\n");
                    }
                }
            } else {
                System.out.println("检索为空。可能的原因：");
                System.out.println("1. Text2CypherAgent 未能生成合法的 Cypher 查询；");
                System.out.println("2. Neo4j 库中暂无 The Matrix 相关的电影测试数据。");
            }
        } catch (Exception e) {
            System.err.println("测试运行出现异常：" + e.getMessage());
            e.printStackTrace();
        }
    }
}