package com.example.demo.chapter1;

import lombok.Data;

// 定义工单数据结构
@Data
public  class TicketInfo {
    private String title;
    private String customerId;
    private TicketPriority priority;
    private TicketCategory category;
    private String description;
    private String createdTime; // 建议时间暂用 String 接收，以防大模型生成非标准格式

    public enum TicketPriority { HIGH, MEDIUM, LOW }
    public enum TicketCategory { TECHNICAL, BILLING, ACCOUNT, PRODUCT, OTHER }
}
