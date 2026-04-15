package com.example.demo.chapter5.three;

import io.agentscope.core.rag.integration.bailian.BailianConfig;

import io.agentscope.core.rag.integration.dify.DifyRAGConfig;
import io.agentscope.core.rag.integration.dify.RetrievalMode;
import io.agentscope.core.rag.integration.bailian.RerankConfig;
import io.agentscope.core.rag.integration.dify.MetadataFilter;
import io.agentscope.core.rag.integration.dify.MetadataFilterCondition;


public class ConfigDemo {

    BailianConfig bailianConfig = BailianConfig.builder()
            .accessKeyId(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"))
            .accessKeySecret(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"))
            .workspaceId("enterprise-workspace-id")
            .indexId("core-knowledge-index")
            // 第一路：配置语义检索(Dense)召回特征数量上限
            .denseSimilarityTopK(80)
            // 第二路：配置关键词检索(Sparse)召回特征数量上限
            .sparseSimilarityTopK(80)
            .build();

    DifyRAGConfig difyConfig = DifyRAGConfig.builder()
            .apiKey(System.getenv("DIFY_RAG_API_KEY"))
            .datasetId("technical-docs-dataset")
            .retrievalMode(RetrievalMode.HYBRID_SEARCH) // 强制要求使用混合检索模式
            .weights(0.65) // 设定：语义权重占65%，关键词权重自动调整为35%
            .topK(15)      // 融合后输出的总候选集数量
            .scoreThreshold(0.45)
            .build();

    BailianConfig enhancedBailianConfig = BailianConfig.builder()
            .accessKeyId("your-ak")
            .accessKeySecret("your-sk")
            .workspaceId("workspace-id")
            .indexId("index-id")
            // 第一阶段：双路高召回率配置
            .denseSimilarityTopK(100)
            .sparseSimilarityTopK(100)
            // 第二阶段：重排序引擎激活与阈值把控
            .enableReranking(true)
            .rerankConfig(RerankConfig.builder()
                    .modelName("gte-rerank-hybrid") // 指定后端部署的交叉编码器模型名称
                    .rerankMinScore(0.38f) // 设定绝对相关性阈值底线，剔除所有低于该分数的冗余片段
                    .rerankTopN(5) // 最终仅提取得分最高的前5个文档传递给大模型代理
                    .build())
            .build();



    // 动态构建运行时的元数据拦截过滤层
    MetadataFilter runtimeFilters = MetadataFilter.builder()
            .logicalOperator("AND") // 定义底层逻辑连接符
            .addCondition(MetadataFilterCondition.builder()
                    .name("department_scope")
                    .comparisonOperator("=") // 支持 =, ≠, >, <, contains 等多态运算
                    .value("customer_success_tier2")
                    .build())
            .addCondition(MetadataFilterCondition.builder()
                    .name("update_timestamp")
                    .comparisonOperator(">=")
                    .value("2026-01-01")
                    .build())
            .build();


}
