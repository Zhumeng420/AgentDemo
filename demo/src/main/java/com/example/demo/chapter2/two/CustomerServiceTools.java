package com.example.demo.chapter2.two;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import reactor.core.publisher.Mono;
import java.time.Duration;

class CustomerServiceTools {

    @Tool(name = "query_order_status", description = "根据订单号查询当前订单的基础状态信息")
    public String queryOrderStatus(
            @ToolParam(name = "orderId", description = "需要查询的合法订单ID，格式应为 ORD- 开头") String orderId,
            UserContext userContext) { // 隐式参数自动注入

        // 强校验机制：验证订单号前缀是否匹配当前用户ID
        if(!orderId.startsWith(userContext.getUserId())) {
            System.out.println("[工具日志] 拦截到越权访问，操作用户：" + userContext.getUserId() + "，目标订单：" + orderId);
            return "【系统警告】权限校验失败：您无权查询不属于当前账号的订单状态。";
        }
        return "订单 " + orderId + " 的状态为：[已发货]，预计在明晚20:00前派送。";
    }

    @Tool(name = "track_external_logistics", description = "当用户明确询问物流轨迹时，调用此工具查询指定订单的详细运输节点")
    public Mono<String> trackLogistics(
            @ToolParam(name = "orderId", description = "订单ID") String orderId, UserContext userContext) {
        // 强校验机制：验证订单号前缀是否匹配当前用户ID
        if(!orderId.startsWith(userContext.getUserId())) {
            System.out.println("[工具日志] 拦截到越权访问，操作用户：" + userContext.getUserId() + "，目标订单：" + orderId);
            return Mono.delay(Duration.ofMillis(500))
                    .map(v -> "【系统警告】权限校验失败：您无权查询不属于当前账号的订单状态。")
                    .timeout(Duration.ofSeconds(3))
                    .onErrorResume(e -> Mono.just("抱歉，外部物流网络系统当前繁忙，请稍后重试查询。"));
        }
        // 模拟异步外部网络调用，使用 Mono.delay 替代 Thread.sleep()
        return Mono.delay(Duration.ofMillis(500))
                .map(v -> "物流系统返回：包裹已到达【杭州转运中心】，正在发往下一站。")
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> Mono.just("抱歉，外部物流网络系统当前繁忙，请稍后重试查询。"));
    }

}