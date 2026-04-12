package com.example.demo.chapter3.five;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class RagEvaluationPipeline {

    // 使用自定义线程池控制外部并发请求，避免打满本地线程资源
    private final ExecutorService evalExecutor = Executors.newFixedThreadPool(10);

    public void runPipeline(List<TestCase> testCases, FaithfulnessEvaluator evaluator) {
        System.out.println("开始执行RAG离线评估流水线，当前测试用例总数: " + testCases.size());
        long startTime = System.currentTimeMillis();

        // 使用 CompletableFuture 并发执行大模型评估调用，显著降低流水线总体耗时
        List<CompletableFuture<EvalResult>> futures = testCases.stream()
                .map(tc -> CompletableFuture.supplyAsync(() -> evaluator.evaluate(tc), evalExecutor))
                .collect(Collectors.toList());

        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(CompletableFuture[]::new)
        );

        // 阻塞等待所有并发评测任务完成
        allOf.join();

        double totalScore = 0.0;
        int validCount = 0;

        for (CompletableFuture<EvalResult> future : futures) {
            try {
                EvalResult result = future.get();
                totalScore += result.getScore();
                validCount++;
                // 生产环境中应将结果写入数据库或评估看板
                System.out.printf("[评估轨迹] 事实一致性得分: %.2f, 裁判理由: %s\n",
                        result.getScore(), result.getReasoning());
            } catch (Exception e) {
                System.err.println("单个评估用例并发执行抛出异常: " + e.getMessage());
            }
        }

        double avgScore = validCount > 0? totalScore / validCount : 0.0;
        long costTime = System.currentTimeMillis() - startTime;

        System.out.println("============== 最终评估报告 ==============");
        System.out.printf("事实一致性 (Faithfulness) 均分: %.3f\n", avgScore);
        System.out.printf("评估耗时: %d 毫秒\n", costTime);

        // CI/CD 强硬门禁断言
        if (avgScore < 0.90) {
            throw new RuntimeException(
                    String.format("【流水线阻断】严重质量衰退：当前系统事实一致性均分(%.2f)低于红线基准 0.90", avgScore)
            );
        }
        System.out.println("CI/CD 检查通过，允许合并至主干。");
        evalExecutor.shutdown();
    }
}