package com.example.demo.chapter5.five;

import lombok.Data;

/**
 * 评估用例实体类
 */
@Data
public class TestCase {
    private String question;
    private String retrievedContext;
    private String generatedAnswer;
    private String groundTruth; // 某些评估指标需要标准答案参考
}