package com.example.demo.chapter0;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.util.Scanner;

@SpringBootApplication
public class WeatherAssistantApplication {

    Hook debugHook = new Hook() {
        @Override
        public <T extends HookEvent> Mono<T> onEvent(T event) {
            if (event instanceof PreReasoningEvent) {
                System.out.println("\n[开始思考]...");
            } else if (event instanceof PostReasoningEvent) {
                System.out.println("[思考结果]：" + ((PostReasoningEvent) event).getReasoningMessage().getTextContent());
            } else if (event instanceof PostActingEvent) {
                System.out.println("[工具执行结果]：" + ((PostActingEvent) event).getToolResult().getOutput());
            } else if (event instanceof PostCallEvent) {
                System.out.println("[最终答案]：" + ((PostCallEvent) event).getFinalMessage().getTextContent());
            }
            return Mono.just(event);
        }
    };


    public static void main(String[] args) {
        SpringApplication.run(WeatherAssistantApplication.class, args);
    }


    @Bean
    public CommandLineRunner demo() {
        return args -> {
            Scanner scanner = new Scanner(System.in);

            // 提示用户输入 API Key
            System.out.println("========================================");
            System.out.println("        天气助手 v1.0");
            System.out.println("========================================");
            System.out.print("请输入 DashScope API Key: ");
            String apiKey = scanner.nextLine().trim();

            // 校验 API Key 不为空
            if (apiKey.isEmpty()) {
                System.err.println("错误：API Key 不能为空");
                System.exit(1);
            }

            System.out.println("\n✓ API Key 已接收");
            System.out.println("正在初始化天气助手...\n");

            // 创建Toolkit并注册工具
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new WeatherTools());

            // 构建ReActAgent
            ReActAgent agent = ReActAgent.builder()
                    .name("WeatherAssistant")
                    .sysPrompt("你是一个天气助手，可以根据用户询问的城市查询天气信息。")
                    .model(DashScopeChatModel.builder()
                            .apiKey(apiKey)
                            .modelName("qwen-plus")
                            .build())
                    .toolkit(toolkit)
                    .hook(debugHook)
                    .build();

            System.out.println("✓ 天气助手已就绪\n");

            // 循环让用户输入天气问题
            while (true) {
                System.out.print("请输入要查询的城市或天气问题 (输入 'exit' 退出): ");
                String question = scanner.nextLine().trim();

                // 退出条件
                if (question.equalsIgnoreCase("exit") || question.isEmpty()) {
                    System.out.println("\n感谢使用天气助手，再见！");
                    break;
                }

                System.out.println("\n正在查询中...");

                // 调用Agent
                Msg response = agent.call(
                        Msg.builder()
                                .role(MsgRole.USER)
                                .content(TextBlock.builder()
                                        .text(question)
                                        .build())
                                .build()
                ).block();

                System.out.println("\n回答：" + response.getTextContent());
                System.out.println("========================================\n");
            }
            // 关闭 Scanner
            scanner.close();
        };
    }
}