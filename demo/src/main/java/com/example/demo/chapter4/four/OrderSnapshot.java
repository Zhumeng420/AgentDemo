package com.example.demo.chapter4.four;

import lombok.Data;

//模拟业务系统的实体类
@Data
public class OrderSnapshot {
    String orderId;
    String productName;
    String logisticsCompany;

    public OrderSnapshot(String orderId, String productName, String logisticsCompany) {
        this.orderId = orderId;
        this.productName = productName;
        this.logisticsCompany = logisticsCompany;
    }
}