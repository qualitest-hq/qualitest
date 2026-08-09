# 造流 Staging 与 AI 体验优化方案

> 来源：全面测试手册 **T1** 冒烟（`smoke-S01` §10.1 等）现场摩擦。  
> 状态：**待落地**（本文只定方案，不改产品代码）。  
> 相关：`docs/全面测试手册.md` §F #11 / #17 / #18 / #19；`docs/鉴权注入与Bearer方案.md`。

---

## 1. 目标

把「AI 造流 → Staging 确认 → 保存 → Run」从**能用**做到**长流可承受**，并减少模型对业务约定的胡猜。

本轮高优四项：

| # | 问题 | 影响 |
|---|------|------|
| H1 | Staging 无按依赖批量确认 | §10.1 一次 ~27 pending，人工点 ✓ 成本高 |
| H2 | pending>0 时保存仅警告、仍落盘并静默过滤 | 未确认节点「保存后消失」，易误以为丢了 |
| H3 | AI 改图意图下仍 explainOnly / 无 Staging | 同会话大 schema 造流常白跑；§F #17 |
| H4 | AI 业务语义胡猜（body 双传、流水金额符号） | 首跑断言/业务码失败；§F #18 / #19 |

---

## 2. H1 · Staging 按拓扑批量确认

### 2.1 现状

- 确认依赖：`stagingDependencyHints.ts` — `addEdge` 须两端 `addNode` 已 confirm；后端 `FlowDesignPatchConfirmService` 对齐。
- 确认编排：`useAiStagingConfirm.ts` — **全局单飞**串行队列（防连点竞态），单单元 `confirm`。
- UI：画布/属性面板逐项 ✓；**无「确认全部可确认项」**。

### 2.2 推荐方案

**Phase A（最小可用）—「确认全部就绪」**

1. 计算 `readyUnits`：`pending` 且 `!isStagingConfirmBlockedByDependencies(...)`。
2. 工具栏/AI 角标旁增加按钮：`确认全部就绪 (N)`。
3. 复用现有 `enqueueConfirmApply` **串行**确认 `readyUnits`（不要并行打 API）。
4. 每一拍后刷新依赖；循环直到 `readyUnits` 为空或本轮 0 成功（避免死循环）。
5. 失败单项：停在该单元（标红 + toast），**不**静默跳过；提供「跳过失败继续」可选。

**Phase B — 「确认本批 patch」**

- 范围限定为某个 `messageId` 的全部 pending（含边）。
- 顺序：先所有可确认 `addNode` / `updateNode` / `assert` 类，再边，再场景类；或按图拓扑 Kahn 排序。
- 适合「从零搭建」一次大 patch。

**Phase C（可选）— 一键确认 = 信任 AI**

- 设置项：`staging.confirmAllRequiresAck` 默认 true；二次确认文案写明「将按依赖顺序确认本批 N 项」。
- 不改变「Staging ✓ = 唯一人审」语义，只压缩点击次数。

### 2.3 涉及文件（预估）

| 层 | 路径 |
|----|------|
| 依赖工具 | `qualitest-ui/.../utils/stagingDependencyHints.ts`（可抽 `listReadyPendingUnitIds`） |
| 编排 | `composables/useAiStagingConfirm.ts` → `confirmAllReady()` / `confirmMessageBatch(messageId)` |
| UI | `FlowCanvasLayout.vue` / Staging 工具条 / AI 助手角标旁 |
| 测试 | 现有 staging confirm 单测旁增加「链式 addNode+edge 批量」用例 |

### 2.4 验收

- [ ] §10.1 级 14 节点 + 边：一次「确认全部就绪」后角标归零（无人工逐点）。
- [ ] 中途服务端校验失败：已成功项保留，失败项可重试；画布不花屏。
- [ ] 连点批量按钮不会并行 confirm（单飞仍有效）。

### 2.5 非目标

- 不取消 Staging；不做「自动确认无 UI」。
- 不在批量路径绕过服务端 `confirmUnit` 校验。

---

## 3. H2 · pending 保存：从弱警告改为可挡可选

### 3.1 现状

`useFlowGraph.saveFlow`：

- `pending > 0` 时 **`ElMessage.warning`**：「尚有 N 项…本次保存不会包含这些内容」。
- 随后仍 `buildPersistFilter()` **排除未确认 add / 回滚未确认 update** 并提交。
- 自动保存确认后走 `skipPendingWarning: true`（合理）。

现场问题：警告一闪而过；人以为「保存了整张 Staging 图」，刷新后节点变少。

### 3.2 推荐方案

**默认改为「挡」+ 显式选择（破坏性小、语义清）**

```
pending > 0 且非 skipPendingWarning
  → MessageBox / 抽屉：
      · 标题：还有 N 项 AI 变更未确认
      · 列表：前 5 个单元名 +「还有 M 项」
      · 按钮：
          [去确认]     → 关闭并 focus 首个 pending
          [仅保存已确认] → 继续当前过滤保存（今日行为）
          [取消]
```

可选设置（默认关）：`save.blockWhenStagingPending=true` 时隐藏「仅保存已确认」，强制先清零（团队开源演示可开）。

**保存按钮态**

- pending>0：按钮旁小角标 `N`，tooltip 与 MessageBox 同文案。
- 保存成功 toast：`已保存（已排除 N 项未确认 Staging）`，避免「保存成功」假安全感。

### 3.3 涉及文件

- `composables/useFlowGraph.ts` — `saveFlow` 分支
- 可选：`utils/aiDesignPreferences.ts` — 用户偏好
- i18n / 文案

### 3.4 验收

- [ ] pending>0 点保存：未选「仅保存已确认」前不发 update API。
- [ ] 选「仅保存已确认」后行为与今日一致；toast 写明排除数。
- [ ] 「确认后自动保存」仍 `skipPendingWarning`，不弹 Box。

---

## 4. H3 · 改图意图下强制产出 Staging（治 explainOnly）

### 4.1 现状

- `TestFlowDesignAgent`：未调用 `submit_flow_design_patch` ⇒ `explainOnly=true`，仍返回「成功」摘要。
- 前端已提示「本轮未提交 Staging」（§F #11）；**根因未除**（§F #17）：
  - 同会话多轮 + 反复 `get_api_detail`（订单等大 schema）耗尽步数；
  - 模型写自然语言方案不调工具；
  - 偶发摘要像已改图但 `patch` 为空。
- 已有：`DEFAULT_MAX_AGENT_STEPS` 8→20；`FlowDesignApiSummarizer` 叶数截断；`terminalSuccessProbe` 在已 submit 时提前结束。

### 4.2 推荐方案（分层）

**A. 意图分流（产品）**

| 模板 / 模式 | 期望 | Agent 行为 |
|-------------|------|------------|
| 从零搭建 / 末尾追加 / Run 失败修复 / … | **必须** patch | 见 B |
| 仅答疑-* | 允许 explainOnly | 保持现状 |

前端已有快捷模板：造流类请求带 `designIntent=mutate`；答疑带 `designIntent=explain`。

**B. 服务端：mutate 轮次硬约束**

1. `designIntent=mutate` 且结束时未 submit：
   - **不要**当成功 explainOnly；
   - 返回明确错误或 `needsRetry`：`改图未提交 Staging：未调用 submit_flow_design_patch`；
   - 可选：自动 **补跑 1 次**「只允许 submit / get 摘要工具」的收束轮（上限 1，防烧钱）。
2. 步数将尽（如剩余 ≤3）且未 submit：注入系统提醒 *「下一工具必须 submit_flow_design_patch，禁止再 get_api_detail」*。
3. `get_api_detail`：对已摘要过的 `apiId` **短路缓存**（同 session）；响应默认只回 summarizer 结果，`includeFullSchema=true` 才给全量。

**C. 工具侧减负**

- 造流优先 `search_apis` + 摘要字段（method/path/必填 body/鉴权），禁止为「看一眼」拉全订单 schema。
- system prompt 增加靶场/项目短约定（与 H4 共用），减少试探性 detail。

**D. 前端**

- mutate 且 explainOnly：助手气泡红色「本轮未改图」+ 一键「请继续提交 Staging」预填发送。
- 若 `patchPending` 元数据异常（摘要说有 patch、体没有）：同样当失败展示，勿显示「已生成修改建议」。

### 4.3 涉及文件（预估）

| 层 | 路径 |
|----|------|
| Agent | `TestFlowDesignAgent.java`、`AiLlmConfigService`（maxSteps） |
| Tools | `GetApiDetail` / `FlowDesignApiSummarizer` / session 级 detail 缓存 |
| API DTO | design 请求增加 `designIntent`（或复用已有 scenario/template 字段） |
| UI | `useAiDesign.ts`、AiChat 气泡、快捷模板发请求时带 intent |

### 4.4 验收

- [ ] 「从零搭建」短提示：同会话第二轮仍能出 Staging（或明确失败可重试），不再静默 explainOnly。
- [ ] 答疑模板仍可无 patch。
- [ ] 大 schema 接口：默认 detail 不超过 summarizer 上限；全量需显式参数。
- [ ] 手册 §F #17 可改为「已关闭」或降级为观测项。

---

## 5. H4 · 业务语义约束（少让模型猜）

### 5.1 现场案例

| 现象 | 正确约定 |
|------|----------|
| 结算/下单 body 同时含 `cartIds` + `items` | **二选一**；购物车结算只用 `cartIds` + `addressId` |
| 余额流水断言 `changeAmount==-195` | 金额为**正数**；方向看 `changeType`（如 2=支出）+ `recordType` |

代码侧已有部分兜底：`normalizeOverridesShape`、资产默认 body 修正（§F #13/#18）。断言符号仍靠模型（§F #19）。

### 5.2 推荐方案

**A. 项目级「造流约定」短文（只读注入）**

- 存在项目设置或 `test_project` 扩展字段 / 文档资产：`aiFlowConventions`（≤2KB 纯文本）。
- Demo 种子写入商城约定摘要（结算二选一、流水符号、客户端 Bearer 用 `{{flow.token}}` 等）。
- `TestFlowDesignAgent` system 或首条 tool result 注入该约定。

**B. 接口资产注释 / OpenAPI extension（更准）**

- 在易错 API（结算预览、创建订单、余额流水）的 description 或 `x-qualitest-hints` 写 1～3 条：
  - `body: cartIds XOR items`
  - `changeAmount is absolute; sign via changeType`
- `get_api_detail` 摘要**必须带上** hints。

**C. 设计期轻量校验（提交 Staging 前）**

- Normalizer / `submit_flow_design_patch` 校验：
  - 若 body 同时含非空 `cartIds` 与 `items` → **warning 或 error**（建议 warning + 自动删 `items` 若意图为 cart 结算，需产品拍板）。
- 不在引擎 Run 期才首次发现。

**D. 提示集同步**

- `qualitest-demo/docs/ai-test-flow-prompts.md` §10.1 等正文补一句流水约定，避免下轮冒烟再踩。

### 5.3 验收

- [ ] 不写特殊提示时，从零搭 S01 购物流：结算/下单 body 无双字段；余额断言用正数金额。
- [ ] 故意双传时，submit 阶段有明确 warning/error。
- [ ] §F #18/#19 保持关闭；约定变更时只改种子/文档。

---

## 6. 建议落地顺序

```text
1. H2 保存挡板          （前端半天级，风险低，立刻少踩坑）
2. H1 确认全部就绪      （前端 1～2 天，依赖现有串行 confirm）
3. H4 约定注入 + detail hints + 双字段 warning（前后端各半，收益稳）
4. H3 mutate 硬约束 + detail 缓存/收束轮（后端为主，需回归造流）
```

鉴权托管头自动补全（Bearer）见 `鉴权注入与Bearer方案.md`，与 H4 正交，可并行，**不阻塞**上表。

---

## 7. 手工回归清单（落地后）

1. 空画布 → 「从零搭建」§10.1 短提示 → 出 Staging → **确认全部就绪** → 角标 0 → 保存无排除 toast。
2. 故意留 1 个 pending → 点保存 → 出现三选一；取消则未请求 API。
3. 同会话第二轮「再补一个断言」→ 有 Staging 或明确失败，禁止假成功 explainOnly。
4. 重载 S01 → Run → 购物流终态通过（含余额流水正数断言）。
5. 答疑模板问「这个节点干什么」→ 无 patch、无报错。

---

## 8. 决议记录（回头填）

| 项 | 决议 | 日期 |
|----|------|------|
| H2 默认挡还是默认「仅保存已确认」 | | |
| H1 是否要 Phase C 一键信任 | | |
| H3 未 submit 是硬失败还是自动收束 1 轮 | | |
| H4 双字段是 warning 还是自动删 items | | |
