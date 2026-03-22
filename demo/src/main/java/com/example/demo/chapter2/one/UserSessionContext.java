package com.example.demo.chapter2.one;

// 模拟的用户安全上下文，用于运行时动态注入以保证数据隔离
public class UserSessionContext {
    private final String userId;
    public UserSessionContext(String userId) {
        this.userId = userId;
    }
    public String getUserId() {
        return userId;
    }
}