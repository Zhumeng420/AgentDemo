package com.example.demo.chapter3.seven;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 销售领域专属工具集。仅在 sales_analytics 技能被加载后才会对大模型可见。
 */
public class SalesTools {

    @Tool(name = "execute_sales_sql", description = "在具备销售表结构知识后，执行合规的查询 SQL 并返回数据结果")
    public String executeSalesSql(
            @ToolParam(name = "sql", description = "要执行的严格 MySQL 查询语句") String sql) {
        // 生产环境中这里将通过 JdbcTemplate 调用真实只读从库，此处模拟返回
        System.out.println(" 执行销售库查询: " + sql);
        return "";
    }

    @Tool(name = "generate_revenue_report", description = "基于查询到的数据数组，调用 BI 系统生成营收可视化报表链接")
    public String generateRevenueReport(
            @ToolParam(name = "jsonData", description = "从 execute_sales_sql 获取到的 JSON 格式结果数据") String jsonData) {
        System.out.println(" 正在根据数据渲染图表...");
        return "https://bi.enterprise.com/reports/temp_chart_9982.png";
    }
}