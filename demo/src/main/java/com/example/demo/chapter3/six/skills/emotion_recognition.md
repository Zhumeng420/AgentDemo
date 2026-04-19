# SKILL: 全局情绪追踪与危机干预 (Emotion Recognition)

## 1. 技能目标 (Objective)
在系统后台静默运行，作为状态机旁路拦截并分析用户输入文本中的潜在情感极性与攻击性特征，为对话系统提供底层的共情计算指标。

## 2. 情感极性标准极点 (Emotion Scale)
- `LEVEL_1_EXTREME_ANGER` (极端愤怒): 包含辱骂、威胁（投诉/曝光/起诉）、严重对立情绪。
- `LEVEL_2_SEVERE_ANXIETY` (极度焦虑): 密集追问、大量感叹/问号滥用、表达急迫性损失。
- `LEVEL_3_CALM` (情绪平静): 正常业务诉求，语气中性，无明显波澜。
- `LEVEL_4_HIGH_SATISFACTION` (超预期满意): 表达感谢、夸赞、正向反馈。

## 3. 危机熔断机制 (Crisis Intervention Rules)
- **触发条件**：当识别到 `LEVEL_1_EXTREME_ANGER` 且文本特征命中 [12315, 投诉, 法院, 媒体, 报警] 中的任意语义时。
- **执行动作**：必须在输出情感级别的同时，附加 `<CRISIS_FLAG_TRIGGERED>` 标识符，强制阻断常规业务流，请求最高级人工专家接管。

## 4. 输出契约 (Output Contract)
- 格式：`[情绪级别] | [危机标识(可选)] | [简短判别依据(限20字)]`
- 示例：`LEVEL_1_EXTREME_ANGER | <CRISIS_FLAG_TRIGGERED> | 包含"12315投诉"威胁词汇`