package com.example.demo.chapter2.five;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import reactor.core.publisher.Mono;

/**
 * 自我修正执行器
 * 利用 Project Reactor 的 flatMap 实现非阻塞的异步递归重试。
 */
class SelfRefinementWrapper {
    private final Agent delegateAgent;
    private final CustomerServiceJudgeMetric judge;
    private final int maxAttempts;

    public SelfRefinementWrapper(Agent delegateAgent, Model judgeModel, int maxAttempts) {
        this.delegateAgent = delegateAgent;
        this.judge = new CustomerServiceJudgeMetric(judgeModel);
        this.maxAttempts = maxAttempts;
    }

    public Mono<Msg> execute(Msg input) {
        // 先获取初始回答，然后进入递归审查
        return delegateAgent.call(input)
                .flatMap(initialOutput -> refineRecursively(input, initialOutput, 1));
    }

    private Mono<Msg> refineRecursively(Msg originalInput, Msg currentOutput, int attempt) {
        if (attempt > maxAttempts) {
            System.out.println("[系统] 达到最大重试次数，强制返回当前结果。");
            return Mono.just(currentOutput);
        }

        String groundTruth = "必须包含安抚话术，且回复字数绝对不能超过50字。";

        return judge.evaluate(originalInput.getTextContent(), currentOutput.getTextContent(), groundTruth)
                .flatMap(evalResult -> {
                    if (evalResult.getScore() >= 4) {
                        System.out.printf("[裁判评估] 第 %d 次生成质量达标 (%d分)。允许放行。\n", attempt, evalResult.getScore());
                        return Mono.just(currentOutput);
                    }

                    System.out.printf("[裁判评估] 第 %d 次生成质量不达标 (%d分)，发现 %s 级错误。触发大模型重写...\n",
                            attempt, evalResult.getScore(), evalResult.getErrorCategory());

                    String feedbackPrompt = String.format("你刚才的回复不达标。反馈建议：%s。请吸取教训，严格按照要求重新回答。", evalResult.getReasoning());
                    Msg feedbackMsg = Msg.builder()
                            .role(MsgRole.USER)
                            .content(TextBlock.builder().text(feedbackPrompt).build())
                            .build();

                    // 将带有批评建议的消息再次喂给 Agent 重新生成
                    return delegateAgent.call(feedbackMsg)
                            .flatMap(newOutput -> refineRecursively(originalInput, newOutput, attempt + 1));
                });
    }
}