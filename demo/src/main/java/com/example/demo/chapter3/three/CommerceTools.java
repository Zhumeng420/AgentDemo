package com.example.demo.chapter3.three;


import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class CommerceTools {
    private static final Logger log = LoggerFactory.getLogger(CommerceTools.class);

    // 工具1：商品检索（返回响应式流结构 Mono<String>）
    @Tool(description = "搜索特定商品以获取其当前价格和库存状态。必须提供精确的商品名称。")
    public Mono<String> searchProduct(
            @ToolParam(name = "productName", description = "商品的精确名称，例如 'iPhone 15' 或 'Galaxy S24'") String productName) {

        log.info("工具已调用：正在为 {} 执行库存检索", productName);

        // 模拟调用底层微服务的异步非阻塞逻辑
        return Mono.defer(() -> {
            if (productName.contains("iPhone")) {
                return Mono.just("{\"product\":\"iPhone 15\", \"price\":799.00, \"stock\":true}");
            } else if (productName.contains("Galaxy")) {
                return Mono.just("{\"product\":\"Galaxy S24\", \"price\":749.00, \"stock\":true}");
            } else {
                return Mono.just("{\"error\":\"未找到完全匹配的商品。建议提供替代方案。\"}");
            }
        });
    }

    // 工具2：安全交易下单（隐式接收UserContext，LLM对该对象完全无感知）
    @Tool(description = "为给定商品下最终订单。仅在成功查询商品、确认价格并完成所需对比后，才能执行此工具。")
    public Mono<String> placeOrder(
            @ToolParam(name = "productName", description = "要订购的选定商品") String productName,
            @ToolParam(name = "priceConfirmed", description = "为此购买交易确认的最终价格") Double priceConfirmed,
            UserContext ctx // AgentScope框架将自动从拦截器中提取并注入此参数 [8, 20]
    ) {
        log.info("工具已调用：正在为认证用户 {} 请求的商品 {} 下单", ctx.getUserId(), productName);

        // 业务层面的数据防篡改校验
        if (priceConfirmed == null || priceConfirmed <= 0) {
            // 触发Evaluator-Optimizer自校正循环 [32, 34]
            return Mono.just("订单被拒绝：检测到价格无效或为零。请在下单前使用 searchProduct 工具验证准确价格。");
        }

        // 模拟核心交易链路的成功处理
        String transactionId = "TXN-" + System.currentTimeMillis();
        return Mono.just(String.format("交易成功：已为 %s 下单，确认价格为 $%.2f。关联交易 ID：%s",
                productName, priceConfirmed, transactionId));
    }
}