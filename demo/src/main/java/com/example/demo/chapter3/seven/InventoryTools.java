package com.example.demo.chapter3.seven;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 库存领域专属工具集。仅在 inventory_management 技能被加载后才会对大模型可见。
 */
public class InventoryTools {

    @Tool(name = "query_live_stock_api", description = "通过 RPC 接口获取特定商品的实时出入库动态库存（比 SQL 更精确）")
    public String queryLiveStockApi(
            @ToolParam(name = "productName", description = "商品精确名称") String productName) {
        // 模拟调用 WMS 仓储系统的实时微服务
        System.out.println(" 正在实时盘点商品: " + productName);
        return "{\"product\": \"" + productName + "\", \"live_stock_count\": 42, \"warehouse\": \"WH-East-01\"}";
    }

    @Tool(name = "create_restock_ticket", description = "如果发现库存低于预警水位，调用此工具向采购部门发起补货工单")
    public String createRestockTicket(
            @ToolParam(name = "productName", description = "缺货的商品名称") String productName,
            @ToolParam(name = "urgency", description = "紧急程度：HIGH 或 NORMAL") String urgency) {
        System.out.println(" 已创建补货工单，商品: " + productName + ", 紧急度: " + urgency);
        return "SUCCESS: Restock ticket RT-77291 created.";
    }
}