package com.example.demo.chapter3.two;

// 定义安全上下文类 (UserContext)
// 框架会在执行时自动提取并注入该对象，大模型对此毫无感知
class UserContext {
    private final String userId;

    public UserContext(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}