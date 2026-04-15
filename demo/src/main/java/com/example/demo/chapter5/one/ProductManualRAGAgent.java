package com.example.demo.chapter5.one;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.model.DashScopeChatModel;
import io. agentscope. core. embedding. EmbeddingModel;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

// RAG 核心组件导入
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.exception.VectorStoreException;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ProductManualRAGAgent {
    private static String apiKey = "sk-xxxxxxxxxxxx";

    public static void main(String args[]) throws VectorStoreException, IOException {
        // ==========================================
        // 步骤一：配置底层的推理大模型与API鉴权
        // 本案例采用阿里云通义千问模型作为认知底座
        // ==========================================
        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                // 生产环境安全注意：切忌将API Key硬编码，务必使用环境变量或凭据管理系统
                .apiKey(apiKey)
                .modelName("qwen-max") // 选择具备较强逻辑推理与总结能力的大模型版本
                .build();

        // ==========================================
        // 步骤二：构建 RAG 系统的核心数据管道组件
        // 包括 Embedding模型、Vector Store（向量库）、Reader（读取器）和 Knowledge（知识中枢）
        // ==========================================
        System.out.println("正在初始化底层知识库管道并解析产品手册...");

        // 1. 实例化向量化模型 (Embedding Model)：负责把文本转换为高维向量
        EmbeddingModel embeddingModel = DashScopeTextEmbedding.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-v4")
                .build();

        // 2. 实例化向量数据库
        // 本例使用支持持久化的 QdrantStore，将其连接至本地或远程集群
//        VDBStoreBase vectorStore = QdrantStore.builder()
//                .location("127.0.0.1:6334")
//                .collectionName("my_collection")
//                .dimensions(1024)
//                .checkCompatibility(false)
//                .build();

        // 3. 构建 Knowledge 对象，完成向量模型与存储器的装配绑定
        // SimpleKnowledge 提供了本地文档全生命周期的管理能力
        Knowledge knowledge = SimpleKnowledge.builder()
                .embeddingModel(embeddingModel) // 将 Embedding 模型绑定至知识库
                .embeddingStore(InMemoryStore.builder().dimensions(1024).build())    // 将 VectorStore 绑定至知识库
                //.embeddingStore(vectorStore)
                .build();

        // 4. 实例化 Reader 负责将硬盘上的产品手册长文本加载并进行逻辑分块
        TextReader manualReader = new TextReader(512, SplitStrategy.PARAGRAPH, 50);
        String manualPath = Paths.get("E:\\javaProject\\demo\\demo\\src\\main\\java\\com\\example\\demo\\chapter3\\one\\MeshPro_V3_Manual.txt").toString();

        // 读取文档内容为字符串，并使用 ReaderInput.fromString() 包装。
        // 注意：因为 AgentScope 底层使用 Reactor 异步架构，read 返回的是 Mono<List<Document>>，
        // 在同步的 main 方法中，必须调用.block() 阻塞等待分块完成
        String manualContent = Files.readString(Paths.get(manualPath));
        List<Document> documents = manualReader.read(ReaderInput.fromString(manualContent)).block();
        knowledge.addDocuments(documents).block();
        System.out.println("产品手册向量化索引构建与写入完成！");

        // ==========================================
        // 步骤三：创建挂载了 RAG 认知能力的 ReActAgent
        // ==========================================
        // 配置检索策略：每次召回最相关的3个文本块（Chunks），设定相似度阈值过滤噪音
        RetrieveConfig retrieveConfig = RetrieveConfig.builder()
                .limit(3)                 // 限制返回文档的数量
                .scoreThreshold(0.75)     // 相似度阈值（通常为0.0-1.0之间）
                .build();

        ReActAgent agent = ReActAgent.builder()
                .name("RouterSupportAgent")
                .sysPrompt("你是一名资深的SmartHome Inc.高级技术支持工程师。请耐心、专业地回答用户的问题。" +
                        "务必严格基于知识库提供的内容回答。如果用户的提问超出了产品手册的范围，" +
                        "请诚实地表示你不知道，切勿捏造任何技术参数或操作步骤。")
                .model(chatModel)
                // 关键绑定：将封装好的私域知识库注入智能体
                .knowledge(knowledge)
                // 本节演示环境采用 GENERIC 模式，框架会自动在底层执行检索并组装上下文
                .ragMode(RAGMode.GENERIC)
                .retrieveConfig(retrieveConfig)
                .build();

        // ==========================================
        // 步骤四：发起业务请求测试，验证 RAG 架构的抗幻觉能力
        // ==========================================
        String userQuestion = "你好，我刚买的MeshPro V3路由器，它的系统指示灯一直高频闪烁红灯是什么意思？该如何排查解决？";
        System.out.println("\n用户提问: " + userQuestion);

        // 构建标准化的消息载体发送给 Agent
        Msg requestMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(userQuestion)
                .build();

        // 调用 Agent 执行推理链路并阻塞等待响应流完成
        Msg response = agent.call(requestMsg).block();

        System.out.println("\nAgent 专家回复:");
        System.out.println("==================================================");
        System.out.println(response.getTextContent());
        System.out.println("==================================================");

        // 优雅停机：在 AgentScope-Java v1.0.11 中引入了完善的停机机制，防止状态丢失
        agent.interrupt();
    }
}