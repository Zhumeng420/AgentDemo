package com.example.demo.chapter3.five;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 评估结果数据结构体，对应大模型JSON输出
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalResult {
    /** 大模型给出的详细推理与依据 */
    private String reasoning;
    /** 最终量化评分，区间 [0.0, 1.0] */
    private double score;
}
