package com.example.demo.chapter3.seven;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


/**
 * 企业级实战 4-3：智能数据分析与专属工具的渐进式暴露
 */
public class SqlAssistantWorkflow {
    private static final Logger log = LoggerFactory.getLogger(SqlAssistantWorkflow.class);

    public static void main(String args[]) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("Missing DASHSCOPE_API_KEY environment variable.");
            return;
        }

        StudioManager.init()
                .studioUrl("http://localhost:3000") // Studio 的后端接收端口
                .project("智能数据分析")
                .runName("SKILL调用测试")
                .initialize()
                .block();

        Model model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-max")
                .build();

        Toolkit toolkit = new Toolkit();
        SkillBox skillBox = new SkillBox(toolkit);

        try {
            // 1. 读取独立的 Markdown 技能文件内容
            // 为了在本地快速测试运行，这里我们采用相对于项目根目录的文件系统路径（Paths.get()）直接进行读取 。
            String salesContent = "";
            String inventoryContent = "";
            try{
                InputStream salesStream = SqlAssistantWorkflow.class.getClassLoader().getResourceAsStream("skills/sales_analytics.md");
                InputStream inventoryStream = SqlAssistantWorkflow.class.getClassLoader().getResourceAsStream("skills/inventory_management.md");
                // 读取文件内容
                salesContent = new String(salesStream.readAllBytes(), StandardCharsets.UTF_8);
                inventoryContent = new String(inventoryStream.readAllBytes(), StandardCharsets.UTF_8);
            }catch(Exception e){
                log.error("load skills error ",e);
            }

            AgentSkill salesSkill = AgentSkill.builder()
                    .name("sales_analytics")
                    .description("Use this skill when analyzing sales, revenue, or customer data.")
                    .skillContent(salesContent)
                    .build();

            AgentSkill inventorySkill = AgentSkill.builder()
                    .name("inventory_management")
                    .description("Use this skill to handle inventory checks, warehouse logic, and restock alerts.")
                    .skillContent(inventoryContent)
                    .build();

            // 2. 渐进式工具暴露的核心：联合注册 (Progressive Disclosure of Tools)
            // 将技能与该领域专属的本地 Java 工具强制绑定。
            // 初始时这些工具对大模型不可见，极大降低幻觉和上下文污染；激活对应技能后才会按需透出 。
            skillBox.registration()
                    .skill(salesSkill)
                    .tool(new SalesTools()) // 绑定销售域的多个专属工具
                    .apply();

            skillBox.registration()
                    .skill(inventorySkill)
                    .tool(new InventoryTools()) // 绑定库存域的多个专属工具
                    .apply();

            log.info("Domain skills and their exclusive multi-tools successfully registered.");
        } catch (Exception e) {
            log.error("Failed to mount knowledge base and tools: ", e);
            return;
        }

        // 3. 构建响应式 ReAct 智能体
        ReActAgent agent = ReActAgent.builder()
                .name("EnterpriseDataSpecialist")
                .sysPrompt("你是一名首席企业数据架构师。你需要根据用户的查询需求，加载相关技能以解锁底层工具，并最终给出完整的业务报告。")
                .model(model)
                .toolkit(toolkit)
                .skillBox(skillBox)
                .memory(new InMemoryMemory())
                .hook(new StudioMessageHook(StudioManager.getClient()))
                .build();

        // 4. 发起需要跨越多个领域，且依赖特定工具组合链条的复杂请求
        Msg userQuery = Msg.builder()
                .name("DataAnalyst_User")
                .textContent("帮我查一下销售表里高价值客户买的最多的那款核心商品，然后去系统里查一下它的实时库存，如果不够的话记得发工单采购.")
                .build();

        log.info("Processing cross-domain tasks with progressive tools...");

        agent.call(userQuery)
                .map(Msg::getTextContent)
                .doOnNext(finalOutput -> {
                    log.info("\n==================================\n" +
                            "Final Report:\n{}\n" +
                            "==================================", finalOutput);
                })
                .onErrorResume(throwable -> {
                    log.error("Fatal error during orchestration: ", throwable);
                    return Mono.just("抱歉，数据流转引擎发生异常。");
                })
                .block(); // 仅在 main 线程允许使用 block() 等待程序执行完毕
    }
}