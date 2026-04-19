## name:
sales_analytics
## description:
Use this skill when analyzing sales, revenue, or customer data. It unlocks execute_sales_sql and generate_revenue_report tools.
## Sales Analytics Domain Knowledge
### 核心目标
利用提供给你的专属工具，精准分析业务营收与高价值客户。
### 数据库 Schema 与工具使用规范
1. 涉及的表结构包含 customers(id, name, registration_date) 和 orders(id, customer_id, total_amount)。
2. 你必须首先编写合规的 SQL，然后调用 execute_sales_sql 工具获取数据。
3. 如果用户需要数据可视化展示，在获取数据后，将数据 JSON 传递给 generate_revenue_report 工具生成图表。