package com.example.demo.chapter5.four;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

/**
 * Text2Cypher 服务：利用大模型将自然语言转换为 Neo4j Cypher 语句
 * 生产级进阶版：支持通过 APOC 动态读取图谱 Schema
 */
public class Text2CypherService {

    private final ReActAgent translationAgent;
    private final Driver neo4jDriver;

    // 生产环境中，需将 Neo4j Driver 注入到服务中以执行动态 Schema 查询
    public Text2CypherService(String apiKey, Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;

        // 1. 动态获取当前图数据库的 Schema
        String dynamicSchema = fetchDynamicSchema();
        System.out.println("【已成功加载动态 Schema】\n" + dynamicSchema);

        // 2. 配置大语言模型
        DashScopeChatModel qwenModel = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max")
                .build();

        // 3. 构建翻译智能体，并将动态提取到的 Schema 注入其 System Prompt
        this.translationAgent = ReActAgent.builder()
                .name("CypherGenerator")
                .sysPrompt("你是一个精通Neo4j的专家。请根据提供的动态图数据库Schema，将用户问题转为Cypher查询。\n" +
                        "Schema结构如下：\n" + dynamicSchema + "\n" +
                        "【严格约束】\n" +
                        "直接且仅输出纯Cypher语句，不要包含任何Markdown标记（如```cypher）或多余解释。\n" )
                .model(qwenModel)
                .build();
    }

    /**
     * 核心逻辑：执行 APOC 过程动态抓取数据库拓扑信息
     */
    private String fetchDynamicSchema() {
        StringBuilder schemaBuilder = new StringBuilder();
        try (Session session = neo4jDriver.session()) {
            // 调用 apoc.meta.data() 返回节点、关系、属性及数据类型
            Result result = session.run("CALL apoc.meta.data() YIELD label, property, type, elementType RETURN label, property, type, elementType");

            schemaBuilder.append("Entity and Property Specifications:\n");
            while (result.hasNext()) {
                Record record = result.next();
                String elementType = record.get("elementType").asString(); // node 或 relationship
                String label = record.get("label").asString();             // 节点标签或关系名称
                String property = record.get("property").asString();       // 属性名
                String type = record.get("type").asString();               // 数据类型 (String, Integer等)

                schemaBuilder.append(String.format("- [%s] %s -> Property: '%s' (DataType: %s)\n",
                        elementType.toUpperCase(), label, property, type));
            }
        } catch (Exception e) {
            System.err.println("动态获取 Schema 失败。请确保您的 Neo4j 数据库已安装 APOC 插件！异常信息: " + e.getMessage());
            // 生产环境下的优雅降级：如果获取失败，返回基础的硬编码 Schema
            return "Node Labels: Person, Movie\nRelationship Types: (Person)-->(Movie)";
        }
        return schemaBuilder.toString();
    }

    public String generateCypher(String query) {
        Msg inputMsg = Msg.builder().textContent("请转换为Cypher: " + query).build();
        String res = translationAgent.call(inputMsg).block().getTextContent().trim();
        // 剔除大模型生成的 Markdown 代码块标记（如 ```cypher 和 ```）
        // (?is) 开启多行匹配和忽略大小写，安全的提取代码主体
        return res.replaceAll("(?is)^```[a-z]*\\n?(.*?)\\n?```$", "$1").trim();
    }

    /**
     * 测试入口
     */
    public static void main(String args[]) {
        // 1. 获取大模型 API Key
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("测试失败：未找到 DASHSCOPE_API_KEY 环境变量。");
            return;
        }

        // 2. 连接您的本地 Neo4j 数据库
        String neo4jUri = System.getenv("NEO4J_URL");
        String neo4jUser = System.getenv("NEO4J_USER");
        String neo4jPassword = System.getenv("NEO4J_PASSWORD");

        Driver testDriver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUser, neo4jPassword));

        try {
            System.out.println("正在连接图数据库并提取动态 Schema...");
            // 初始化 Service 时，内部会自动调用 APOC 提取 Schema
            Text2CypherService service = new Text2CypherService(apiKey, testDriver);

            // 模拟自然语言查询
            String userQuery = "对比《The Matrix》和《The Matrix Reloaded》，列出它们各自独有的演员。";
            System.out.println("\n========== 1. 自然语言输入 ==========");
            System.out.println(userQuery);

            System.out.println("\n正在请求大模型生成 Cypher...");
            String generatedCypher = service.generateCypher(userQuery);

            System.out.println("\n========== 2. 基于动态 Schema 生成的 Cypher 语句 ==========");
            System.out.println(generatedCypher);

        } finally {
            // 测试结束后关闭驱动
            testDriver.close();
        }
    }
}