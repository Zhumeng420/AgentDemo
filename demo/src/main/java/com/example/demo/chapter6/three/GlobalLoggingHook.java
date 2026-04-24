package com.example.demo.chapter6.three;

import io.agentscope.core.hook.*;

import reactor.core.publisher.Mono;

public class GlobalLoggingHook implements Hook {
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent) {
            System.out.println("[" + event.getAgent().getName() + "] 正在分析请求上下文，开始思考...");
        } else if (event instanceof PreActingEvent preActing) {
            System.out.println("[" + event.getAgent().getName() + "] 触发动作，准备调用内部工具: " + preActing.getToolUse().getName());
        } else if (event instanceof PostCallEvent postCall) {
            System.out.println("[" + event.getAgent().getName() + "] 执行完毕，输出结果: " + postCall.getFinalMessage().getTextContent());
        } else if (event instanceof ErrorEvent errorEvent) {
            System.err.println("[" + event.getAgent().getName() + "] 发生异常熔断: " + errorEvent.getError().getMessage());
        }
        return Mono.just(event);
    }
}