package com.example.demo.chapter3.five;


import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

// ==========================================
// 1. 定义测试使用的订单查询工具
// ==========================================
public class OrderQueryTool {
    @Tool(description = "根据订单号查询订单物流状态")
    public String queryOrderStatus(
            @ToolParam(name = "orderId", description = "需要查询的订单号") String orderId
    ) {
        // 模拟业务侧的耗时操作与返回
        return "订单 " + orderId + " 已发货，当前到达北京市朝阳区分拨中心。";
    }
}