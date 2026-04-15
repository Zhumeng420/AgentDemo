package com.example.demo.chapter3.one;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;

public class OrderQueryTools {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryTools.class);

    /**
     * 工具描述严格遵循"动作+场景+约束"的最佳实践，确保LLM的路由精准度
     */
    @Tool(
            name = "queryOrderStatus",
            description = "查询订单实时履约与物流状态的工具。仅当用户明确询问特定订单的发货、配送或总体状态时使用。如果用户未提供订单号，切勿使用此工具；请先要求用户提供订单号。"
    )
    public Mono<String> getOrderStatus(
            @ToolParam(
                    name = "orderId",
                    description = "以 'ORD-' 开头的唯一字母数字订单标识符，例如 'ORD-12345678'。必须严格等于12个字符。"
            ) String orderId,
            // 隐式参数：由ToolExecutionContext在运行时自动匹配并注入，实现租户级别的数据隔离
            UserSessionContext sessionContext
    ) {
        // 【防卫式编程】
        // 尽管我们在Prompt Schema中给出了正则级别的约束，但在Java执行层进行二次业务硬校验依然不可或缺。
        if (orderId == null ||!orderId.startsWith("ORD-") || orderId.length()!= 12) {
            // 返回包含明确指导信息的Mono，框架将其作为Observation反馈给模型，触发模型向用户询问正确格式。
            return Mono.just("错误：识别到无效的订单号格式。订单号必须是12个字符并以 'ORD-' 开头。请礼貌地要求用户核对并提供正确的12位订单号。");
        }

        // 使用 Mono.delay 模拟响应式、非阻塞的底层微服务调用
        return Mono.delay(Duration.ofMillis(500))
                .flatMap(v -> {
                    log.info("执行模拟API请求查询订单: {}, 请求用户ID: {}", orderId, sessionContext.getUserId());

                    if (orderId.equals("ORD-98765432")) {
                        return Mono.just("{\"status\": \"已发货\", \"carrier\": \"顺丰速运\", \"waybill\": \"SF9988776655\", \"estimatedDelivery\": \"明天上午 10:00\"}");
                    }
                    return Mono.just("{\"status\": \"未找到\"}");
                })
                // 生产级考量：设置API调用的响应式硬超时，防止下游服务故障拖垮Agent进程[15, 39]
                .timeout(Duration.ofSeconds(5))
                // 【面向LLM的异常兜底与话术转化】
                .onErrorResume(throwable -> {
                    log.error("订单API调用失败，订单号: {}", orderId, throwable);
                    return Mono.just("系统异常：后端订单数据库暂时无响应。请向用户真诚致歉，并委婉建议他们15分钟后再试。");
                });
    }
}