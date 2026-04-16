package com.example.demo.chapter2.five;

import lombok.Data;

@Data
public class EvaluationResult {
    private int score;
    private String reasoning;
    private String errorCategory;

    public EvaluationResult(int i, String s, String small) {
        this.score = i;
        this.reasoning = s;
        this.errorCategory = small;
    }
}
