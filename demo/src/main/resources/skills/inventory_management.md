## name:
inventory_management
## description:
Use this skill to handle inventory checks, warehouse logic, and restock alerts. It unlocks query_live_stock_api and create_restock_ticket tools.
## Inventory Management Domain Knowledge
### 操作规范 (SOP)
当你需要处理库存相关问题时：
1. 不要使用普通的 SQL，必须直接调用专属 RPC 接口工具 query_live_stock_api 查询实时数据。
2. 评估返回的 live_stock_count。如果发现库存低于 50 件，你必须主动调用 create_restock_ticket 触发 HIGH 级别的采购补货流程。