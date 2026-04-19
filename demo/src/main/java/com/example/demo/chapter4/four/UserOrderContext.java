package com.example.demo.chapter4.four;

import lombok.Data;

//定义业务上下文对象 (承载真实的业务数据)
@Data
public class UserOrderContext {
    private String realOrderId;

    public UserOrderContext(String realOrderId) {
        this.realOrderId = realOrderId;
    }

    public String getRealOrderId() {
        return realOrderId;
    }
}