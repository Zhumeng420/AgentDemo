package com.example.demo.chapter2.four;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import java.util.List;
import java.util.Map;

public class SupportWorkflow {

    public void processCustomerIssue(String rawInput, IntentRouterService router, ReActAgent coreAgent) {
        // 第一阶段：通过提示词链完成查询分解
        List<Map<String, Object>> taskPlan = router.decomposeUserQuery(rawInput);

        // 构造注入了分解规划的新Prompt
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("【系统提示】：用户提交了一份复杂的工单请求。为了提高执行效率，系统前置的架构师Agent已将该诉求拆解为以下规范的结构化任务列表：\n");
        contextBuilder.append(taskPlan.toString()).append("\n\n");
        contextBuilder.append("请你作为执行Agent，严格按照上述Task ID的顺序，逐一调用相关工具来解决这些问题。处理完毕后，给用户输出一份综合的反馈报告。");

        Msg formattedMsg = Msg.builder()
                .role(io.agentscope.core.message.MsgRole.USER)
                .textContent(contextBuilder.toString())
                .build();

        // 第二阶段：核心Agent接管，基于清晰的计划执行Tool Calling
        Msg finalResponse = coreAgent.call(formattedMsg).block();

        System.out.println("最终回复：" + finalResponse.getTextContent());
    }
}