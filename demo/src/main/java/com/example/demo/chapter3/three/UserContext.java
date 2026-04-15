package com.example.demo.chapter3.three;

import lombok.Data;

@Data
public class UserContext {
    private String userId;
    private String userTier;

    public UserContext(String s, String vip) {
        this.userId = s;
        this.userTier = vip;
    }
}
