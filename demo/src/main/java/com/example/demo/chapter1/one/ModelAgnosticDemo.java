package com.example.demo.chapter1.one;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GeminiChatModel;
import io.agentscope.core.model.OpenAIChatModel;

/**
 * 实战 1-1：模型无关架构下的多大脑无缝切换
 */
public class ModelAgnosticDemo {

    private static String qwen = "sk-xxxxxxxxxxxxxxxxxx";
    private static String gemini = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    public static void main(String[] args) {

        // ==========================================
        // 步骤 1：初始化不同的底层算力提供商
        // ==========================================

        // 1. 初始化阿里云通义千问（国内业务主力）
        DashScopeChatModel qwenModel = DashScopeChatModel.builder()
                .apiKey(qwen)
                .modelName("qwen-plus")
                .build();

        // 2. 初始化 Google Gemini（海外出海业务主力）
        OpenAIChatModel openaiModel = OpenAIChatModel.builder()
                .apiKey(gemini)
                .modelName("gemini-3.1-pro-preview")
                // 将请求路由到 Google 官方的 OpenAI 兼容网关
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
                .build();
        // ==========================================
        // 步骤 2：定义统一的 Agent 业务规则与消息协议
        // ==========================================

        // 业务层将人设和用户输入彻底解耦，不与任何厂商模型绑定
        String commonSysPrompt = "你是一个精通中国历史的学者。请用不超过50个字简短且深刻地回答问题。";
        // 统一的消息协议，屏蔽了底层所有的 JSON 拼接差异
        Msg userMsg = Msg.builder().textContent("唐朝是怎么走向灭亡的？").build();

        // ==========================================
        // 步骤 3：多态注入，展示“换脑”魔法
        // ==========================================

        System.out.println("====== 正在路由至 [阿里云] 算力节点 ======");
        ReActAgent cnAgent = ReActAgent.builder()
                .name("HistoryScholar_CN")
                .sysPrompt(commonSysPrompt)
                .model(qwenModel) // 核心：注入 Qwen 模型实例
                .build();

        System.out.println("【Qwen 回复】: " + cnAgent.call(userMsg).block().getTextContent());


        System.out.println("\n====== 正在路由至 [Google] 算力节点 ======");
        // 注意看：Agent 的构建逻辑与上面完全一致，仅仅换了 model 实例
        ReActAgent globalAgent = ReActAgent.builder()
                .name("HistoryScholar_Global")
                .sysPrompt(commonSysPrompt)
                .model(openaiModel) // 核心：无缝切换为 Gemini 模型实例
                .build();

        System.out.println("【Gemini 回复】: " + globalAgent.call(userMsg).block().getTextContent());
    }
}