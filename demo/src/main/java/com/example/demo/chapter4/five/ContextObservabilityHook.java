package com.example.demo.chapter4.five;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体生命周期上下文切面拦截器
 */
public class ContextObservabilityHook implements Hook {
    private final Map<String, String> requestContextMap = new ConcurrentHashMap<>();
    private final EvaluationService evaluationService;
    private final java.util.concurrent.CountDownLatch latch; // 新增

    public ContextObservabilityHook(EvaluationService evaluationService, java.util.concurrent.CountDownLatch latch) {
        this.evaluationService = evaluationService;
        this.latch = latch;
    }

    @Override
    public int priority() { return 5; }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreCallEvent preEvent) {
            return Mono.deferContextual(ctx -> {
                String traceId = ctx.getOrDefault("X-B3-TraceId", "UNKNOWN_TRACE");
                String inputStr = "";
                Object input = preEvent.getInputMessages();
                if (input instanceof Msg msg) {
                    inputStr = msg.getTextContent();
                } else if (input instanceof List<?> list &&!list.isEmpty() && list.get(0) instanceof Msg msg) {
                    inputStr = msg.getTextContent();
                } else if (input!= null) {
                    inputStr = input.toString();
                }
                requestContextMap.put(traceId, inputStr);
                System.out.println("[Hook-PreCall] 成功拦截输入并绑定 TraceID: " + traceId);
                return Mono.just(event);
            });
        }

        if (event instanceof PostCallEvent postEvent) {
            return Mono.deferContextual(ctx -> {
                String traceId = ctx.getOrDefault("X-B3-TraceId", "UNKNOWN_TRACE");
                String injectedContext = requestContextMap.remove(traceId);

                if (injectedContext!= null &&!injectedContext.isEmpty()) {
                    String agentResponse = postEvent.getFinalMessage()!= null?
                            postEvent.getFinalMessage().getTextContent() : "";

                    System.out.println("[Hook-PostCall] 准备异步触发大模型裁判评估...");
                    evaluationService.evaluateContextUtilization(
                                    postEvent.getAgent().getName(),
                                    injectedContext,
                                    agentResponse
                            )
                            .doFinally(signal -> latch.countDown()) // 无论评估成功还是异常，最后都释放锁
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe();
                } else {
                    latch.countDown(); // 如果上下文未命中，也释放锁防止主线程死锁
                }
                return Mono.just(event);
            });
        }
        return Mono.just(event);
    }
}