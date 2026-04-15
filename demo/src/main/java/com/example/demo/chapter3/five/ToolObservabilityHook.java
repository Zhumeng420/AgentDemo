package com.example.demo.chapter3.five;


import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;



// ==========================================
// 2. 实现可观测性 Hook 拦截器
// ==========================================
public class ToolObservabilityHook implements Hook {
    private static final Logger log = LoggerFactory.getLogger(ToolObservabilityHook.class);
    private final ObservationRegistry observationRegistry;

    public ToolObservabilityHook(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 拦截工具执行前置事件 (PreActingEvent)
        if (event instanceof PreActingEvent preActingEvent) {
            // 获取工具名称和具体传参
            String toolName = preActingEvent.getToolUse().getName();
            String rawArgs = preActingEvent.getToolUse().getInput().toString();

            // 打印高优审计日志，观察模型传递的真实参数
            log.info("\n>>> [审计拦截] 智能体正在准备调用工具 [{}]\n>>> 工具参数载荷为: {}",
                    toolName, rawArgs);

            // 生成 Micrometer 监控指标打点
            return Observation.createNotStarted("agent.tool.execution", observationRegistry)
                    .contextualName("Tool-Call: " + toolName)
                    .lowCardinalityKeyValue("tool.name", toolName)
                    .highCardinalityKeyValue("tool.args.raw", rawArgs)
                    .observe(() -> Mono.just(event));
        }
        return Mono.just(event);
    }
}