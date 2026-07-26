# AI 测试流画布 Staging 合并改造方案

> **状态**：已实施（Step 1～9 + Phase 1～8）+ **Bug 修复轮次（2026-07-07）** + **UX 优化轮次（2026-07-07）**；§10.2 自动化已通过，**§10.4～10.6 浏览器手工验收仍待执行**  
> **范围**：`qualitest-ui` 测试流 AI 设计 + `qualitest-system` patch 确认后端  
> **原则**：一次性改全、不留双轨代码；上线前完成，禁止「新旧并存」  
> **不在范围**：`testProject` 模块的 API 脚本 AI（`useApiScriptAi`），后续可参照本方案独立改造

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [现状与问题](#2-现状与问题)
3. [目标交互（产品定义）](#3-目标交互产品定义)
4. [架构设计](#4-架构设计)
5. [数据模型](#5-数据模型)
6. [前端改造清单](#6-前端改造清单)
7. [后端改造清单](#7-后端改造清单)
8. [删除清单（必须彻底移除）](#8-删除清单必须彻底移除)
9. [实施步骤（单 PR 交付）](#9-实施步骤单-pr-交付)
10. [测试与验收](#10-测试与验收)
11. [风险与回滚](#11-风险与回滚)
12. [附录：与 Cursor 代码合并的对照](#12-附录与-cursor-代码合并的对照)
13. [变更记录](#变更记录)

---

## 1. 背景与目标

当前 AI 测试流设计采用 **「会话 Diff 勾选 + 服务端批量试算 + 画布只读幽灵预览 + 一键合并」** 模式。该模式在结构校验上较稳健，但交互与真实编辑场景脱节：用户需在侧栏勾选、等待试算、再批量合并，无法在画布上直接审阅和修改 AI 生成内容。

本改造将交互升级为 **「画布 Staging + 单元就地确认/取消」**，接近 Cursor inline merge 心智模型，同时保留服务端图结构校验能力（触发时机从「勾选试算」改为「单元确认时硬校验」）。

### 1.1 核心目标


| #   | 目标                                                                          |
| --- | --------------------------------------------------------------------------- |
| G1  | AI 会话只展示 **变更摘要**（新增/修改/删除计数 + 可点击定位），不再承载勾选、试算、合并按钮                        |
| G2  | 新增节点/边 **直接出现在画布**（Staging 态），可点击打开属性并编辑                                    |
| G3  | 修改节点/边在原对象上标记，属性面板展示 **原值 vs 现值**，仅可编辑现值                                    |
| G4  | 运行场景变更在 **运行场景面板** 就地确认/取消，不再进入会话 Diff                                      |
| G5  | 每个变更单元在 **对象旁** 提供确认/取消（类似 Cursor），合并不在会话中进行                                |
| G6  | 确认时做 **硬校验**（error 阻断该单元确认，可改 draft **重试**）；浏览/编辑阶段仅 **软提示**（warning 不阻断编辑） |
| G7  | **删除全部旧链路代码**，禁止 feature flag 双轨、禁止遗留 dead code                             |


---

## 2. 现状与问题

### 2.1 现有链路（改造前）

```
AI SSE 返回 patch
  → AiDesignChatPanel 渲染 Diff 勾选列表
  → 勾选变化 → debounce 300ms 调用 POST /patch/preview（批量试算）
  → computeAiPatchVisualDiff → AiPatchDiffOverlay 幽灵预览（pointer-events: none）
  → 点击「合并到画布」→ 整图替换 store（fromGraphJson mergedGraphJson）
```

### 2.2 关键文件（现状）


| 层级    | 文件                                  | 职责                      |
| ----- | ----------------------------------- | ----------------------- |
| UI    | `AiDesignChatPanel.vue`             | Diff 勾选、试算结果、合并按钮       |
| UI    | `AiPatchDiffOverlay.vue`            | 只读幽灵节点/边/删标记            |
| 逻辑    | `useAiDesign.ts`                    | 勾选态、试算缓存、批量合并、reopen/回滚 |
| 逻辑    | `computeAiPatchVisualDiff.ts`       | 勾选 → overlay 状态         |
| 逻辑    | `previewPartialPatch.ts`            | 批量 preview API 客户端      |
| 逻辑    | `partialPreviewValidation.ts`       | 合并门禁文案                  |
| 逻辑    | `diffItemDependencies.ts`           | 勾选拓扑联动                  |
| 逻辑    | `buildDiffItems.ts`                 | patch → Diff 列表项        |
| 逻辑    | `mergeSnapshot.ts`                  | 批量合并前快照                 |
| Store | `flowCanvasStore.aiPatchVisualDiff` | overlay 状态              |
| 后端    | `FlowDesignPatchPreviewService`     | 按 acceptedIds 批量试算      |


### 2.3 主要痛点

1. **预览不可交互**：幽灵层无法点选、无法改属性
2. **决策与对象分离**：用户在侧栏勾选，在画布上看幽灵，认知成本高
3. **场景变更不可见**：`scenarioPatch` 只在 Diff 文本列表里，运行场景面板无感知
4. **批量合并粒度过粗**：部分合法、部分非法时需反复勾选试算
5. **代码路径复杂**：previewToken、baseGraphHash、messageAcceptedMap、mergeSnapshot 等多层状态交织

---

## 3. 目标交互（产品定义）

### 3.1 AI 会话（AiDesignChatPanel）

**保留**：模型选择、会话历史、流式 summary/thinking、Composer、重新生成、自动保存偏好。

**移除**：Diff 勾选列表、全选按钮、试算区、合并按钮、reopen/回滚入口、Ctrl+Enter 合并快捷键。

**新增 — 变更摘要卡片**（每条含 patch 的 assistant 消息下方）：

```
┌─────────────────────────────────────┐
│ 本次建议变更                         │
│  · 新增节点 2 · 修改节点 1 · 新增边 1 │
│  · 修改场景 1                        │
│ [定位到画布]  [定位到运行场景]        │
│ 待确认 3 · 已确认 1 · 已取消 0       │
└─────────────────────────────────────┘
```

- 点击「定位到画布」：视口 fit 到第一个 `pending` 的 graph 单元  
- 点击「定位到运行场景」：打开左栏 runConfig + 高亮第一个 pending 场景单元  
- 摘要数据来自 Staging Store，非 Diff 勾选态

### 3.2 画布 — 新增节点/边（Staging Add）

- AI 返回后，**立即**将 `addNodes` / `addEdges` 注入 vue-flow（非 overlay）  
- 节点/边带 Staging 视觉样式（虚线边框 + 「AI 待确认」角标）  
- **可正常选中、拖拽、打开属性面板编辑**  
- 节点右上角（或边中点浮层）显示 **[✓ 确认] [✕ 取消]**  
- 确认：调用服务端单元校验 → 通过则去掉 Staging 标记，写入正式图层，pushHistory  
- 取消：从画布移除该 Staging 对象；若有关联 Staging 边，一并取消或提示

### 3.3 画布 — 修改节点/边（Staging Update）

- 原节点/边保留在画布，增加 **修改标记**（蓝色脉冲边框，替代现有 `is-ai-patch-update`）  
- 选中后在右侧属性面板顶部展示 **对照区**：

```
┌─ AI 建议修改 ─────────────────────┐
│ 原值          │ 现值（可编辑）      │
│ name: 登录    │ [登录V2        ]   │
│ url: /api/v1  │ [/api/v2/login ]   │
│              [✓ 确认] [✕ 取消]     │
└───────────────────────────────────┘
```

- **仅「现值」列可编辑**；确认时将 draft 合并进 node.data / edge  
- 取消：清除该单元的 Staging 状态，节点恢复原始属性

### 3.4 画布 — 删除节点/边（Staging Delete）

- 原对象加 **删除待确认标记**（红色虚线框 + 角标 ×）  
- 点击后在属性面板或节点浮层展示影响说明（关联边数量）  
- 确认：执行删除（含级联边，逻辑复用现有 `suggestDeleteEdgesForNode` 规则，但改为确认时自动处理）  
- 取消：去掉删除标记

### 3.5 运行场景（RunConfigPanel / ScenarioConfigPanel）

- `scenarioPatch` **不再进入会话 Diff**  
- 在 `RunConfigPanel` 场景列表中：
  - **新场景**：带 Staging 样式卡片，可点击进入 `ScenarioConfigPanel` 编辑，卡片上 [确认] [取消]  
  - **改场景**：卡片标记「待确认」，右栏 ScenarioConfigPanel 顶部展示原/现对照（同节点属性面板模式）  
  - **删场景 / 切换 activeScenario**：卡片/单选上就地确认
- 确认时调用服务端 `scenario` 单元校验（见 §7）

### 3.6 校验策略


| 阶段          | 行为                                                                       |
| ----------- | ------------------------------------------------------------------------ |
| Staging 注入后 | 仅本地软校验（可选 warning 角标），**不阻断**浏览和编辑                                       |
| 点击「确认」      | 调用 `POST /patch/confirmUnit`，服务端 normalize + 单单元合并试算 + 全图 validate       |
| 确认失败        | 写入该单元 `lastValidation`，展示 errors；**保持 pending**，不污染正式图；其他 pending 单元不受影响 |
| 单元重试        | 用户修改 draft（或先 confirm 依赖单元）后，**再次点击同一单元的「确认」**；不限次数，直至成功或 reject         |
| 确认成功        | 清除 `lastValidation`，去掉 Staging 标记；若开启「确认后自动保存」则触发 saveFlow               |


**不再**做勾选变化的 debounce 批量 preview。

#### 3.6.1 单元重试（与旧批量合并的区别）


| 维度   | 旧批量合并                     | Staging 单元重试                                      |
| ---- | ------------------------- | ------------------------------------------------- |
| 粒度   | 整批勾选项一次试算                 | 单 `unitId` 独立 confirm                             |
| 失败后  | 合并按钮禁用，需改勾选组合             | 仅该单元展示 error，可改 draft 后重试                         |
| 并行   | 一项 error 挡整批              | 单元 A 失败时，单元 B 仍可 confirm                          |
| 典型路径 | 勾 addEdge 失败 → 补勾 addNode | addEdge 失败 → confirm addNode → **再点对 addEdge 确认** |


**重试无效的情况**（需在 UI 标明来源，避免用户反复点同一单元）：

- error 来自**画布既有**结构问题（缺开始节点等）→ 需先修画布，非改 draft 能解决  
- error 来自**依赖未满足**（如 addEdge 缺 addNode）→ 先 confirm 依赖单元，再重试本单元  
- `baseGraphHash` 与试算时不一致 → 提示画布已变更，自动用最新图重试或刷新

confirm 请求进行中：该单元按钮 `loading` 禁用，防止重复提交；**不阻塞**其他单元操作。

---

## 4. 架构设计

### 4.1 总体结构

```
┌──────────────────┐     SSE patch      ┌─────────────────────┐
│ TestFlowDesign   │ ─────────────────► │ useAiDesign         │
│ Agent (后端)     │                    │  · 流式/会话         │
└──────────────────┘                    │  · patch 到达        │
                                        └──────────┬──────────┘
                                                   │ hydrateStaging(patch)
                                                   ▼
                                        ┌─────────────────────┐
                                        │ aiStagingStore      │
                                        │  · units[]          │
                                        │  · byMessageId      │
                                        └──────────┬──────────┘
                    ┌──────────────────────────────┼──────────────────────────┐
                    ▼                              ▼                          ▼
           flowCanvasStore              RunConfigPanel                 AiDesignChatPanel
           (nodes/edges +               (scenario units)               (summary only)
            staging markers)
                    │
                    │ confirmUnit(unitId)
                    ▼
           POST /patch/confirmUnit  ──► FlowDesignPatchConfirmService
                                         (reuse Merger + Validator)
```

### 4.2 模块职责划分


| 模块                                                          | 改造后职责                                                                                                     |
| ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `useAiDesign.ts`                                            | SSE、会话；`createAiStagingHydration` 灌入；`buildFlowGraphInput`；**不再** preview/merge |
| `aiStagingStore.ts`                                         | Staging 单元、`buildStagingParallelMap`、三分拆 persistFilter                          |
| `useAiStagingCanvas.ts` / `useAiStagingScenario.ts`         | 经 `useStagingSync` 同步；layer 专属 build/apply/reject                              |
| `useAiStagingConfirm.ts`                                    | 编排层；`stagingConfirmDialog` + `stagingConfirmRequest`                             |
| `AiStagingChrome.vue` / `AiStagingActionButtons.vue`        | 统一节点/边 Chrome 与共享按钮                                                         |
| `AiDesignChatPanel.vue`                                     | 纯会话 + 变更摘要卡片                                                                                              |
| `NodePropertyPanel.vue` / `EdgePropertyPanel.vue`           | 检测选中对象 Staging 态 → 渲染原/现对照 + confirm/reject                                                               |
| `FlowDesignPatchConfirmService`（**后端新建**，替代 PreviewService） | 单单元 confirm 校验与合并                                                                                         |


### 4.3 与撤销（History）的关系

- **每次 confirm 成功** push 一次 history（与现有手动编辑一致）  
- **reject** 不 push history（Staging 注入本身不 push，避免污染撤销栈）  
- 若用户 confirm 多个单元后 undo：逐步撤销每次 confirm（保持现有 history 语义）

### 4.4 多轮 patch 与会话

- 每条 assistant 消息的 patch 独立一组 Staging units（`messageId` 关联）  
- 同一 nodeId 上不应同时存在两个 pending update；新 patch 到达时若冲突，后到的覆盖并 toast 提示  
- 会话摘要按 message 分组展示；全局 Staging 计数在工具栏可选展示（如「AI 待确认 3」）

---

## 5. 数据模型

### 5.1 Staging 单元 ID

**沿用**现有 Diff 键名规则（与后端 Merger 一致），避免重写合并逻辑：

```
addNode:{id} | updateNode:{id} | addEdge:{id} | updateEdge:{id}
deleteNode:{id} | deleteEdge:{id}
scenario:activeScenarioId | addScenario:{id} | updateScenario:{id} | deleteScenario:{id}
```

> `setActiveScenario` 类单元在实现中统一为 `scenario:activeScenarioId`（前后端一致）。

### 5.2 前端类型（`types/aiStagingTypes.ts`，新建）

```typescript
export type AiStagingStatus = 'pending' | 'confirmed' | 'rejected';

export type AiStagingKind = /* 同 DiffItemKind */;

/** 单个待确认变更单元 */
export interface AiStagingUnit {
  unitId: string;           // 如 addNode:9001
  messageId: string;
  kind: AiStagingKind;
  status: AiStagingStatus;
  label: string;            // 摘要用短文案
  /** update/delete/add 的 patch 原始片段（JSON） */
  patchSlice: unknown;
  /** update 类：确认前的画布基线快照 */
  baseline?: Record<string, unknown>;
  /** add/update 类：用户可编辑的 draft（默认来自 patch） */
  draft?: Record<string, unknown>;
  /** 最近一次 confirm 尝试的服务端校验结果；失败时供重试 UI 展示，成功 confirm 后清除 */
  lastValidation?: DesignValidationResult;
  /** 该单元 confirm 请求进行中，防止重复提交 */
  confirmInFlight?: boolean;
  confirmedAt?: number;
}

/** 按 message 聚合，供会话摘要卡片使用 */
export interface AiStagingMessageSummary {
  messageId: string;
  pending: number;
  confirmed: number;
  rejected: number;
  addNodeCount: number;
  updateNodeCount: number;
  /* ... */
}
```

### 5.3 画布节点 Staging 标记

在 `node.data` 或 parallel map（**推荐 parallel map，不污染持久化 graph_json**）：

```typescript
// flowCanvasStore 或 aiStagingStore
stagingByNodeId: Record<string, { unitId: string; messageId: string; mode: 'add' | 'update' | 'delete' }>
stagingByEdgeId: Record<string, { unitId: string; messageId: string; mode: 'add' | 'update' | 'delete' }>
```

> **重要**：`toGraphJson` / `fromGraphJson` / 保存接口 **不得**序列化 Staging 标记；Staging 仅内存态，刷新后丢失（与现 mergeSnapshot 一致，可接受）。

### 5.4 消息模型简化（`aiDesignTypes.ts`）

从 `AiDesignMessageView` **移除**：

- `merged?: boolean`
- `mergeGeneration?: number`
- `diffItems?: DesignDiffItem[]`

**新增**：

- `stagingSummary?: AiStagingMessageSummary`（computed，非持久化）

`parseAssistantFromServer` 不变；patch 仍来自 `resultMetaJson.patchJson`。

### 5.5 后端 Confirm 请求/响应（替代 Preview）

```java
// FlowDesignPatchConfirmRequest
Long testProjectId;
GraphJson graphJson;          // 当前画布（含已 confirm 内容 + 内存态 staging 已注入部分需前端预先合并进请求体）
FlowDesignPatch patch;        // 原始完整 patch
String unitId;                // 单单元，如 updateNode:1001
Object draftOverride;         // 可选，前端编辑后的 draft（add/update 类）
```

```java
// FlowDesignPatchConfirmResult
boolean ok;
List<String> errors;
List<String> warnings;
GraphJson graphJson;          // 确认该单元后的全图（前端用于落盘）
String baseGraphHash;
List<String> dependencyHints; // 如「确认 addEdge 需先确认 addNode:xxx」
```

---

## 6. 前端改造清单

### 6.1 新建文件


| 文件                                      | 说明                                                         |
| --------------------------------------- | ---------------------------------------------------------- |
| `stores/aiStagingStore.ts`              | Staging 单元 CRUD、摘要、confirm/reject                          |
| `types/aiStagingTypes.ts`               | 类型定义                                                       |
| `composables/useAiStagingCanvas.ts`     | 画布注入/移除 Staging 节点边                                        |
| `composables/useAiStagingScenario.ts`   | 场景 Staging                                                 |
| `composables/useAiStagingConfirm.ts`    | 编排 confirm/reject；dialog + request 拆分                                       |
| `composables/stagingConfirmDialog.ts` | delete 类 ElMessageBox（**结构优化新增**）                                        |
| `composables/stagingConfirmRequest.ts`| confirmUnit API、hash 守卫、落盘（**结构优化新增**）                              |
| `composables/stagingDraftSync.ts`     | node/edge/scenario draft 回写（**结构优化新增，替代 useAiStagingDraftSync**）    |
| `composables/usePendingStagingUnit.ts`| 属性面板 staging lookup（**结构优化新增**）                                     |
| `composables/useStagingNavigation.ts` | 摘要定位、fitView 高亮（**结构优化新增**）                                      |
| `composables/useStagingSync.ts`     | canvas/scenario 同步引擎（**结构优化新增**）                                    |
| `composables/useAiStagingHydration.ts`| patch 灌入（**结构优化新增，从 useAiDesign 剥离**）                               |
| `utils/stagingUnitIds.ts`             | unitId 解析唯一真相源（**结构优化新增**）                                       |
| `utils/stagingGraphInput.ts`          | toGraphJson 输入组装（**结构优化新增**）                                        |
| `utils/stagingCanvasRevert.ts`        | 单单元画布/runConfig 回滚（reject / 冲突时复用）                               |
| `utils/stagingCleanup.ts`             | 会话级 Staging 清理、截断检测（**UX 优化新增**）                                |
| `utils/stagingLabels.ts`              | 标题、删除提示、摘要 breakdown（**结构优化新增**）                              |
| `utils/stagingEdgeStyle.ts`           | 边 stroke 共用（**结构优化新增**）                                              |
| `utils/stagingFieldMerge.ts`          | mergeNodeFields / mergeEdgeFields（**结构优化新增**）                           |
| `constants/stagingTheme.ts`           | Staging 颜色 token（**结构优化新增**）                                          |
| `styles/aiStaging.scss`             | 错误面板、按钮 mixin（**结构优化新增**）                                        |
| `components/AiStagingChrome.vue`      | 合并节点角标 + 边中点浮层（**结构优化新增，替代 Node/Edge Chrome**）            |
| `components/AiStagingActionButtons.vue` | 共享确认/取消按钮（**结构优化新增**）                                         |
| `utils/buildStagingUnits.ts`            | patch → AiStagingUnit[]（替代 buildDiffItems）                 |
| `utils/stagingDependencyHints.ts`       | 确认前本地依赖提示（替代 diffItemDependencies 的勾选联动）                   |
| `components/AiStagingNodeChrome.vue`    | ~~节点上 confirm/reject 按钮~~ → 已合并为 `AiStagingChrome.vue` |
| `components/AiStagingEdgeChrome.vue`    | ~~边上 confirm/reject 浮层~~ → 已合并为 `AiStagingChrome.vue` |
| `components/AiStagingChangeSummary.vue` | 会话内变更摘要卡片                                                  |
| `components/AiStagingFieldDiff.vue`     | 原/现字段对照编辑器（节点/边/场景共用）                                      |
| `panels/AiStagingScenarioBanner.vue`    | 场景列表/配置面板 Staging 条（组件路径：`components/AiStagingScenarioBanner.vue`） |
| `api/project/testFlowAi.ts`             | 新增 `confirmFlowDesignUnit`，**删除** `previewFlowDesignPatch` |


### 6.2 修改文件


| 文件                                     | 改动要点                                                                                                                                                                                                    |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `composables/useAiDesign.ts`           | 删除 messageAcceptedMap、preview 缓存、mergeMessagePatch…；patch 到达调用 `hydrateStagingFromPatch`；**UX 优化**：`pruneExtraMessageState` 先回滚画布再删 store；新建/删会话调用 `clearAllStagingState`；截断前 `hasPendingStagingFromMessageIndex` |
| `composables/useAiStagingHydration.ts` | patch 灌入；**UX 优化**：`onSessionLoaded` 先 `clearAllStagingState` 再全量 sync |
| `composables/useAiStagingConfirm.ts`   | 编排 confirm/reject；**UX 优化**：`enqueueConfirmApply` 串行化 API + 落盘 |
| `composables/useFlowGraph.ts`          | saveFlow 使用 `stagingFilter`；**UX 优化**：有待确认项时 warning toast（`skipPendingWarning` 供自动保存跳过） |
| `composables/useFlowValidation.ts`     | **UX 优化**：校验序列化排除 pending Staging，避免误报 error |
| `composables/ai/useAiChatSession.ts`   | 删除 messageAcceptedMap 相关（若共享）；**UX 优化**：`confirmTruncateIfMerged` 同时检测 `merged` 与 `hasPendingStagingFromIndex` |
| `panels/AiDesignChatPanel.vue`         | 删除整个 `ai-design-diff` 区块及相关 CSS/快捷键；嵌入 `AiStagingChangeSummary`                                                                                                                                         |
| `stores/flowCanvasStore.ts`            | 删除 `aiPatchVisualDiff`、`setAiPatchVisualDiff`、`clearAiPatchVisualDiff`；新增 `stagingByNodeId` / `stagingByEdgeId` 或由 aiStagingStore 持有                                                                    |
| `nodes/BaseFlowNode.vue`               | `is-ai-staging-*`；挂载 `AiStagingChrome`（node-corner）                                                                                    |
| `edges/ConditionEdge.vue` / `FlowDefaultEdge.vue` | `resolveStagingEdgeStroke` + `AiStagingChrome`（edge-midpoint）                                                                      |
| `components/AiMergeFitView.vue`        | 重命名为 `AiStagingFitView.vue`，聚焦 pending 单元                                                                                                                                                               |
| `FlowCanvasLayout.vue`                 | 移除 `AiPatchDiffOverlay`；挂载 Staging 相关组件                                                                                                                                                                 |
| `panels/NodePropertyPanel.vue`         | 集成 `AiStagingFieldDiff`                                                                                                                                                                                 |
| `panels/EdgePropertyPanel.vue`         | 同上                                                                                                                                                                                                      |
| `panels/RunConfigPanel.vue`            | 场景卡片 Staging 态 + `AiStagingScenarioBanner`                                                                                                                                                              |
| `panels/ScenarioConfigPanel.vue`       | 改场景原/现对照                                                                                                                                                                                                |
| `composables/useFlowMinimap.ts`        | staging 样式替代 aiPatchVisualDiff                                                                                                                                                                          |
| `utils/mergeHighlight.ts`              | confirm 成功后高亮（保留，改触发点）                                                                                                                                                                                  |
| `utils/aiDesignPreferences.ts`         | 删除 defaultAcceptMode、dependencyAware；`autoSaveAfterMerge` → `autoSaveAfterConfirm`                                                                                                                      |
| `utils/computeBaseGraphHash.ts`        | 保留，confirm 时 hash 校验                                                                                                                                                                                    |
| `types/aiDesignTypes.ts`               | 移除 DesignDiffItem、buildDiffItems 导出、merged 字段                                                                                                                                                           |
| `components/FlowCanvasHelpPopover.vue` | 更新快捷键说明（删除 Ctrl+Enter 合并）                                                                                                                                                                               |

### 6.3 测试文件


| 操作     | 文件                                                                                                                                                                                                                                                                                   |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **删除** | `computeAiPatchVisualDiff.test.ts`、`partialPreviewValidation.test.ts`、`previewPartialPatch.test.ts`、`previewTokenGuard.test.ts`、`mergeShortcut.test.ts`、`mergeSnapshot.test.ts`、`diffItemDependencies.test.ts`、`buildDiffItems.test.ts`、`aiDesignPreferences.test.ts`（acceptMode 部分） |
| **新建** | `buildStagingUnits.test.ts`、`aiStagingStore.test.ts`、`stagingDependencyHints.test.ts`、`confirmUnit.test.ts`、`stagingUnitIds.test.ts`、`stagingLabels.test.ts`、`stagingLocate.test.ts`、`mergeHighlight.test.ts`、`stagingFieldDiff.test.ts`、`aiStagingScenario.test.ts`、`stagingConfirmErrorHints.test.ts`；Bug 修复：`useFlowHistory.staging.test.ts`、`useAiDesign.staging.test.ts`、`aiStagingStore.conflict.test.ts`、`useAiStagingConfirm.test.ts`；UX 优化：`stagingCleanup.test.ts` |
| **修改** | `aiDesignTypes.test.ts`、`computeBaseGraphHash.test.ts`                                                                                                                                                                                                                               |


---

## 7. 后端改造清单

### 7.1 新建


| 文件                                       | 说明                                              |
| ---------------------------------------- | ----------------------------------------------- |
| `FlowDesignPatchConfirmService.java`     | 单 unitId confirm：filterPatch → merge → validate |
| `FlowDesignPatchConfirmRequest.java`     | 请求体                                             |
| `FlowDesignPatchConfirmResult.java`      | 响应体                                             |
| `FlowDesignPatchConfirmServiceTest.java` | 单测                                              |


实现要点：

1. 复用 `FlowDesignPatchNormalizer`、`FlowDesignPatchMerger.filterPatchByAccepted`（仅含单个 unitId + 依赖自动补全）
2. 复用 `GraphJsonValidator`（`ok = errors 为空`；warnings 不阻断 confirm）
3. `draftOverride` 合并进 patch 对应 node/edge/scenario 后再 normalize
4. 依赖规则：`confirm addEdge` 时若端点为 patch 内 addNode 且尚未 confirm，返回 error + dependencyHints
5. **幂等与重试**：同一 `unitId` + 相同 `draftOverride` 多次调用，结果应一致；失败时 `ok=false` 且不返回可落盘的 graphJson（或明确约定前端不得写入）
6. **错误归因**：`errors` 优先包含与本 unit 及直接依赖相关的校验项；全图 validate 的 errors 全部返回，由前端展示在触发 confirm 的单元上

### 7.2 修改


| 文件                                   | 改动                                                                  |
| ------------------------------------ | ------------------------------------------------------------------- |
| `TestFlowAiController.java`          | 新增 `POST /patch/confirmUnit`；`POST /patch/preview` 在 Step 9 与前端同批删除 |
| `FlowDesignPatchMerger.java`         | 无逻辑变更（复用）                                                           |
| `FlowDesignPatchPreviewService.java` | **删除**                                                              |


### 7.3 删除


| 文件                                       | 说明                      |
| ---------------------------------------- | ----------------------- |
| `FlowDesignPatchPreviewService.java`     | 由 ConfirmService 替代     |
| `FlowDesignPatchPreviewRequest.java`     | 由 ConfirmRequest 替代     |
| `FlowDesignPatchPreviewResult.java`      | 由 ConfirmResult 替代      |
| `FlowDesignPatchPreviewServiceTest.java` | 由 ConfirmServiceTest 替代 |


### 7.4 API 变更摘要


| 旧                                         | 新                                             |
| ----------------------------------------- | --------------------------------------------- |
| `POST /project/testFlow/ai/patch/preview` | **删除**                                        |
| —                                         | `POST /project/testFlow/ai/patch/confirmUnit` |


> 前后端同版本部署，无需兼容旧 preview 接口。

---

## 8. 删除清单（必须彻底移除）

改造 PR 合并前，以下符号/文件在仓库中 **不得残留引用**（可用 `rg` 全库验证）：

### 8.1 前端文件（整文件删除）

```
qualitest-ui/apps/web/src/views/project/testFlow/components/AiPatchDiffOverlay.vue
qualitest-ui/apps/web/src/views/project/testFlow/utils/computeAiPatchVisualDiff.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/previewPartialPatch.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/previewPartialPatchConstants.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/partialPreviewValidation.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/partialPreviewTypes.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/previewTokenGuard.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/mergeShortcut.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/mergeSnapshot.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/diffItemDependencies.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/buildDiffItems.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/diffUiLabels.ts
qualitest-ui/apps/web/src/views/project/testFlow/utils/aiDesignMessageSelectors.ts
```

### 8.2 前端符号（删除后全库零匹配）

```
messageAcceptedMap
mergeMessagePatch
toggleMessageAccepted
acceptAllForMessage
reopenMessagePatch
rollbackMessagePatch
canMergeMessage
mergeButtonLabel
mergeButtonHint
getPartialMergeValidation
partialPreviewPending
serverPartialPreviewByMessageId
aiPatchVisualDiff
previewFlowDesignPatch
shouldShowDiffSection
ai-design-diff
is-ai-patch-update（改为 is-ai-staging-*）
merged?: boolean（AiDesignMessageView）
mergeGeneration
mergeSnapshotsByMessageId
buildDiffItems
DesignDiffItem
```

### 8.3 后端文件（整文件删除）

```
qualitest-system/.../FlowDesignPatchPreviewService.java
qualitest-system/.../model/FlowDesignPatchPreviewRequest.java
qualitest-system/.../model/FlowDesignPatchPreviewResult.java
qualitest-system/.../FlowDesignPatchPreviewServiceTest.java
```

### 8.4 后端符号

```
/patch/preview
FlowDesignPatchPreviewService
FlowDesignPatchPreviewRequest
FlowDesignPatchPreviewResult
```

### 8.5 验证命令（PR 自检）

```bash
# 前端：上述 dead symbols 应为 0 命中（testProject 模块除外）
rg "messageAcceptedMap|aiPatchVisualDiff|previewFlowDesignPatch|buildDiffItems" qualitest-ui/apps/web/src/views/project/testFlow

# 后端
rg "PatchPreview|patch/preview" qualitest/qualitest-system qualitest/qualitest-admin
```

---

## 9. 实施步骤（单 PR 交付）

> **禁止**分阶段上线双轨。以下步骤在同一 feature branch 完成，**合并前**必须走完 Step 9，确保生产无旧 preview / Diff / overlay 残留。
>
> **分支内开发约定**：Step 1～8 可暂留旧代码路径，但**不得同时渲染**新旧两套 UI；破坏性删除集中在 Step 9，避免开发中期「无 preview 且无 confirm」的功能真空。

### 9.1 实施原则


| #   | 原则                                                                     |
| --- | ---------------------------------------------------------------------- |
| P1  | **纵向切片优先**：先打通 `addNode` 最小闭环（含单元重试），再扩展 update/delete/边/场景            |
| P2  | **单元独立**：某单元 confirm 失败不阻塞其他 pending 单元                                |
| P3  | **单元可重试**：失败保持 Staging + 展示 errors，改 draft 或补齐依赖后可再次 confirm（见 §3.6.1） |
| P4  | **每步门禁**：每 Step 结束跑对应单测 / 手工路径，通过再进入下一步                                |


### Step 1：后端 Confirm API（0.5d）

1. 新建 `FlowDesignPatchConfirmService` / Request / Result
2. Controller **新增** `POST /patch/confirmUnit`（分支内**暂保留** preview，Step 9 删除）
3. 实现单单元链路：`filterPatch(unitId)` → merge → `GraphJsonValidator.validate`
4. 支持 `draftOverride`；依赖未满足时返回 error + `dependencyHints`（§7.1）
5. 迁移 `FlowDesignPatchPreviewServiceTest` 用例，**新增**单单元重试相关用例（§10.1）
6. **门禁**：`mvn test -pl qualitest-system -Dtest=FlowDesignPatchConfirmServiceTest`

### Step 2：Staging 数据层（1d）

1. 新建 `aiStagingTypes.ts`、`buildStagingUnits.ts`、`aiStagingStore.ts`、`stagingDependencyHints.ts`
2. 单元 CRUD：`status`、`lastValidation`、`confirmInFlight`；失败时 `markConfirmFailed(unitId, validation)`，成功时 `markConfirmed` 并清除 `lastValidation`
3. `flowCanvasStore`：`stagingByNodeId` / `stagingByEdgeId`（或由 aiStagingStore 持有）
4. `toGraphJson`：**排除**未 confirm 的 staging 对象（同步单测）
5. `useAiDesign`：patch 到达 → `hydrateStagingFromPatch`（旧 Diff 逻辑暂不删，Step 8 再精简）
6. 新建 `buildStagingUnits.test.ts`、`aiStagingStore.test.ts`、`stagingDependencyHints.test.ts`
7. **门禁**：`pnpm test` — buildStagingUnits、aiStagingStore、stagingDependencyHints

### Step 3：addNode 纵向切片 + 单元重试（1d）

最小可验证路径，优先落地 §3.6.1 重试闭环：

1. `useAiStagingCanvas.ts`：staging add 节点 inject/remove
2. `AiStagingNodeChrome.vue`：[确认] [取消]；失败时展示 `lastValidation.errors`
3. `useAiStagingConfirm.ts` + `confirmFlowDesignUnit` API 客户端
4. **单元重试**：
  - confirm 失败 → `lastValidation` 写入、单元保持 pending、错误角标  
  - 用户改 draft → 再次点「确认」→ 重新请求（`confirmInFlight` 防重复）  
  - 其他 pending 单元不受阻塞
5. confirm 成功 → 去 staging 标记、`pushHistory`；reject → 移除临时节点
6. 改 `BaseFlowNode`、`FlowCanvasLayout`（**不删** `AiPatchDiffOverlay`，旧链路先隐藏或不触发）
7. 新建 `confirmUnit.test.ts`（含失败 → 改 draft → 再请求 mock）
8. **门禁（手工）**：addNode confirm 失败 → 改属性 → 重试成功；reject 正常

### Step 4：画布 Staging 完整渲染（1d）

1. 扩展 `useAiStagingCanvas`：update/delete 标记、边 inject/remove
2. `AiStagingEdgeChrome.vue`；改 `ConditionEdge.vue`、`useFlowMinimap.ts`
3. `AiMergeFitView.vue` → `AiStagingFitView.vue`
4. deleteNode / addEdge / updateEdge 的 confirm & reject
5. **依赖重试**：addEdge 因缺 addNode 失败 → confirm addNode → **再对 addEdge 点确认**
6. **门禁（手工）**：add / update / delete / 边四类 staging 可视；边依赖重试路径通

### Step 5：属性面板原/现对照 + update 重试（1.5d）

1. `AiStagingFieldDiff.vue`
2. 改 `NodePropertyPanel`、`EdgePropertyPanel`
3. update 类：对照区展示 errors；改「现值」draft 后重试 confirm
4. `mergeHighlight.ts` 改触发点为 confirm 成功；`computeBaseGraphHash` 纳入 confirm 请求
5. **门禁（手工）**：updateNode 校验失败 → 改现值 → 重试成功

### Step 6：Confirm 完善与偏好（0.5d）

1. 完善 `useAiStagingConfirm`：全 kind reject、`baseGraphHash` 过期处理、错误来源提示（依赖 / 画布既有 / 本单元）
2. `aiDesignPreferences.ts`：`autoSaveAfterMerge` → `autoSaveAfterConfirm`；删除 `defaultAcceptMode`、`dependencyAware`
3. 工具栏「AI 待确认 N」计数（可选）
4. **门禁（手工）**：多单元并行 pending，A 失败 B 可 confirm；连续 confirm 后 undo 逐步撤销

### Step 7：运行场景 Staging（0.5～1d）

1. `useAiStagingScenario.ts`、`AiStagingScenarioBanner.vue`
2. 改 `RunConfigPanel`、`ScenarioConfigPanel`
3. 场景单元同样支持 confirm 失败 → 改 draft → 重试
4. **门禁（手工）**：scenario add/update/delete 就地确认与重试

### Step 8：会话面板瘦身（1d）

1. `AiStagingChangeSummary.vue`
2. 重写 `AiDesignChatPanel`：删 Diff 勾选/试算/合并区块，仅保留摘要 + 定位
3. 精简 `useAiDesign.ts`：移除 messageAcceptedMap、preview 缓存、mergeMessagePatch 等
4. 更新 `FlowCanvasHelpPopover.vue`（删除 Ctrl+Enter 合并说明）
5. **门禁**：会话无 Diff UI；摘要数字与 staging store 一致

### Step 9：清理、删旧链路与全量回归（1d）

1. 执行 §8 删除清单（含 `AiPatchDiffOverlay`、preview API、Diff 相关 utils、dead symbols）
2. 删 `POST /patch/preview` 及 `FlowDesignPatchPreviewService` 等后端文件
3. `pnpm test`（testFlow 目录）+ 后端相关单测全绿
4. §10.2 手工验收清单全勾
5. `rg` 验证 §8.5 零残留

**预估总工时**：约 **8～9 人日**（1 人）；若首版砍掉 delete 浮层或场景删改，可压至 7 人日。

### Step 10：代码结构优化（约 6 人日，已完成开发）

> 在 Step 1～9 功能闭环后，对 testFlow Staging 模块做分阶段结构优化，降低重复与维护成本。验收见 **§10.6**。

| Phase | 内容 | 关键产出 |
| --- | --- | --- |
| 1 | 死代码 + 命名 | 删除 deprecated；`stagingDependencyHints` 客户端预检；`truncateStagingConfirmText` |
| 2 | 公共 utils | `stagingUnitIds`、`stagingGraphInput`、`stagingLabels`、`mergeHighlight` 收敛 |
| 3 | Composable 收敛 | `usePendingStagingUnit`、`stagingDraftSync`、`useStagingNavigation`、`useAiStagingHydration` |
| 4 | 同步引擎 | `useStagingSync` / `runStagingItemSync`；`stagingFieldMerge` |
| 5 | confirm 拆分 | `stagingConfirmDialog` + `stagingConfirmRequest` + 编排层 |
| 6 | UI/样式 | `AiStagingChrome`、`AiStagingActionButtons`、`stagingTheme`、`stagingEdgeStyle` |
| 7 | Store DRY | `buildStagingParallelMap`、`buildPersistFilter` 三分拆 |
| 8 | 测试 | `stagingUnitIds.test.ts`、`stagingLabels.test.ts`；§10.2 全量单测 |

**门禁**：§10.2 前端单测 + §10.3 死代码扫描 + §10.6 手工无回归。

---

## 10. 测试与验收

> **使用说明**：本节为上线前**完整验收手册**。建议顺序：先跑 §10.2 自动化 → §10.3 死代码扫描 → 再按 §10.4～§10.7 手工逐项勾选。所有 checkbox 勾满后方可合并/发布。

### 10.0 验收状态总览

| 类别 | 内容 | 状态 |
| --- | --- | --- |
| 前端 Staging 单测 | §10.2 命令，65 用例（含 Bug 修复 4 文件 + UX 优化 `stagingCleanup`） | ☑ 2026-07-07 全绿 |
| 后端 Confirm 单测 | `FlowDesignPatchConfirmServiceTest`、`FlowDesignPatchMergerTest` | ☑ 2026-07-07 全绿 |
| 死代码扫描 | §10.3 `rg` 零命中 | ☑ 2026-07-07 |
| Staging 核心手工 | §10.5 A～N | ☐ 待浏览器执行 |
| 结构优化手工 | §10.6 | ☐ 待浏览器执行 |
| 边界与回归 | §10.7 | ☐ 部分已由单测覆盖（见 §10.7 表注） |

---

### 10.1 单测覆盖（断言对照表）

#### 10.1.1 Staging 业务（Step 1～9）

| 场景 | 断言 | 测试文件 |
| --- | --- | --- |
| patch → staging units | add/update/delete/scenario 均生成正确 unitId | `buildStagingUnits.test.ts` |
| staging store CRUD | hydrate、markConfirmFailed、markConfirmed、buildPersistFilter | `aiStagingStore.test.ts` |
| confirm addEdge 缺 addNode | 返回 dependencyHints，保持 pending | `stagingDependencyHints.test.ts` |
| confirm 失败重试 | draft 变更后 `ok` 由 false 变 true | `confirmUnit.test.ts` |
| confirm API 客户端 | 缺 projectId 本地报错；服务端返回 graphJson | `confirmUnit.test.ts` |
| toGraphJson | 排除未 confirm 的 staging add；回滚 pending update | `graphAdapter.test.ts` |
| 原/现字段对照 | node/edge/scenario 字段 diff 与写回 draft | `stagingFieldDiff.test.ts` |
| confirm 高亮 | updateNode/updateEdge 收集端点 nodeId | `mergeHighlight.test.ts` |
| baseGraphHash | hash 不一致阻断 confirm | `computeBaseGraphHash.test.ts` |
| 场景 parallel map | addScenario / setActiveScenario 映射 | `aiStagingScenario.test.ts` |
| 摘要定位 | kind 分类、findFirstPending*、resolveScenarioId | `stagingLocate.test.ts` |
| confirm 错误分组 | 依赖 / 校验 / 画布错误文案 | `stagingConfirmErrorHints.test.ts` |
| 偏好 | autoSaveAfterConfirm 读写 | `aiDesignPreferences.test.ts` |
| undo 与 Staging 联动 | pushHistory 快照含 stagingUnits；undo 恢复 pending | `useFlowHistory.staging.test.ts` |
| AI 设计 graph 过滤 | 设计请求排除 pending staging | `useAiDesign.staging.test.ts` |
| patch 冲突回滚 | 跨 message 冲突调用 revertStagingUnitOnCanvas | `aiStagingStore.conflict.test.ts` |
| deleteNode 关联边 | confirm deleteNode 后 reject 关联 addEdge | `useAiStagingConfirm.test.ts` |
| confirm 落盘串行化 | API + apply 经 `enqueueConfirmApply`，避免并行覆盖 | `useAiStagingConfirm.test.ts`（队列逻辑） |
| 会话 Staging 清理 | 截断检测、按 message 回滚、全量 reset | `stagingCleanup.test.ts` |
| 场景 draft 落盘 | remark/onNodeFailure/onSnapshotFailure | `FlowDesignPatchConfirmServiceTest.confirm_updateScenarioWithDraft_ok` |

#### 10.1.2 代码结构优化（Phase 1～8）

| 场景 | 断言 | 测试文件 |
| --- | --- | --- |
| unitId 解析 | `objectIdFromUnitId`、`graphObjectIdFromUnit`、kind 分类 | `stagingUnitIds.test.ts` |
| canvas mode | add/update/delete → staging mark mode | `stagingUnitIds.test.ts` |
| 摘要 breakdown 文案 | `formatMessageSummaryBreakdown` 拼接与空态 | `stagingLabels.test.ts` |
| kind 标题 / 删除提示 | panel / banner 变体 | `stagingLabels.test.ts` |

---

### 10.2 自动化回归命令

在仓库根目录或对应子目录执行。**PowerShell** 下 Maven `-Dtest` 参数需加引号。

#### 10.2.1 前端（`qualitest-ui/apps/web`）

```bash
cd qualitest-ui/apps/web

# Staging 全量单测（推荐一条命令）
yarn test stagingLocate aiStagingStore buildStagingUnits stagingDependencyHints graphAdapter stagingFieldDiff mergeHighlight computeBaseGraphHash aiStagingScenario confirmUnit stagingUnitIds stagingLabels stagingConfirmErrorHints aiDesignPreferences useFlowHistory.staging useAiDesign.staging aiStagingStore.conflict useAiStagingConfirm stagingCleanup
```

**预期**：全部 Test Files passed，0 failed。

#### 10.2.2 后端（`qualitest` 目录）

```bash
cd qualitest

mvn test -pl qualitest-system "-Dtest=FlowDesignPatchConfirmServiceTest,FlowDesignPatchMergerTest"
```

**预期**：BUILD SUCCESS，Confirm / Merger 用例全绿。

> 若本地 `qualitest-system` 测试编译因无关用例（如 MCP 测试类缺依赖）失败，可先 `mvn test -pl qualitest-system "-Dtest=FlowDesignPatchConfirmServiceTest"` 单独验证 Confirm 链路，再排查环境。

#### 10.2.3 验收勾选

- [x] §10.2.1 前端命令已执行且全绿（65 passed，2026-07-07）  
- [x] §10.2.2 后端 Confirm/Merger 已执行且全绿（2026-07-07）  

---

### 10.3 死代码与双轨残留扫描

```bash
# 旧 Staging/Diff 链路（testFlow 目录应为 0 命中）
rg "messageAcceptedMap|aiPatchVisualDiff|previewFlowDesignPatch|buildDiffItems|collectHighlightNodeIds|AiStagingNodeChrome|AiStagingEdgeChrome|useAiStagingDraftSync" qualitest-ui/apps/web/src/views/project/testFlow

# 旧 preview 后端
rg "PatchPreview|patch/preview" qualitest/qualitest-system qualitest/qualitest-admin
```

**允许保留的兼容别名**（非双轨，仅 re-export）：

- `shouldBlockMergeByBaseGraphHash` → alias `shouldBlockConfirmByBaseGraphHash`（`computeBaseGraphHash.ts`）
- `truncateMergedConfirmText` → `useAiChatSession` 仍支持，testFlow 已改用 `truncateStagingConfirmText`

#### 10.3.1 验收勾选

- [x] §8.2 前端 dead symbols 在 testFlow 内零命中（`shouldBlockMergeByBaseGraphHash` alias 除外）  
- [x] §8.4 后端 preview 符号零命中  
- [x] 已删除文件不存在：`AiPatchDiffOverlay.vue`、`computeAiPatchVisualDiff.ts`、`FlowDesignPatchPreviewService.java` 等（§8.1 / §8.3）  

---

### 10.4 手工验收 — 环境与前置

| 项 | 要求 |
| --- | --- |
| 后端 | `qualitest-admin` 已启动，MySQL + Redis 正常 |
| 前端 | `qualitest-ui` dev 已启动，能打开测试流画布 |
| 账号 | 具备测试流**编辑**权限 |
| AI | 项目已配置可用 LLM 模型；网络可访问 AI 接口 |
| 数据 | 至少一个含 HTTP 节点、连线、运行场景的测试流；建议另备「空流」用于 addNode 场景 |
| 浏览器 | Chrome / Edge 最新版；打开 DevTools Network 便于观察 `confirmUnit` 请求 |

#### 10.4.1 验收勾选

- [ ] 环境满足上表  
- [ ] 打开 AI 设计侧栏无控制台报错  

---

### 10.5 手工验收 — Staging 核心流程（Step 1～9）

每条用例：**操作步骤 → 预期结果 → 勾选**。

#### A. 会话摘要（无 Diff UI）

| 步骤 | 预期 |
| --- | --- |
| 1. 打开 AI 设计侧栏，发送会返回 patch 的设计请求（如「加一个 HTTP 登录节点」） | assistant 消息下方出现 **「本次建议变更」** 摘要卡片 |
| 2. 检查侧栏 | **无** Diff 勾选列表、无「全选」、无「合并到画布」、无试算区 |
| 3. 查看摘要文案 | 含「新增节点 N · …」及「待确认 / 已确认 / 已取消」计数 |
| 4. 点击「定位到画布」 | 视口 fit 到 pending 图单元，相关节点高亮 |
| 5. 若 patch 含场景变更，点击「定位到运行场景」 | 左栏切到运行配置，选中对应场景 |

- [ ] A 通过

#### B. 新增节点（addNode）

| 步骤 | 预期 |
| --- | --- |
| 1. AI 建议新增节点后 | 节点**直接出现在画布**（紫色虚线 Staging 样式），非幽灵 overlay |
| 2. 拖拽节点 | 位置可改 |
| 3. 选中节点，右栏属性 | 可编辑名称等；有 Staging 提示文案 |
| 4. 节点右上角点 **✓** | 请求 `POST .../patch/confirmUnit`；成功后 Staging 样式消失，toast「已确认变更」 |
| 5. 点 **✕** | 节点从画布移除，摘要计数「已取消」+1 |

- [ ] B 通过

#### C. 修改节点（updateNode）

| 步骤 | 预期 |
| --- | --- |
| 1. AI 建议修改已有节点 | 节点蓝色 update 脉冲边框 |
| 2. 选中节点，右栏顶部 | **原值 / 现值** 对照表；仅现值可编辑 |
| 3. 修改现值后点确认 | confirm 成功，对照区消失，节点属性为现值 |
| 4. 点取消 | 节点恢复 baseline，Staging 标记清除 |

- [ ] C 通过

#### D. 删除节点（deleteNode）

| 步骤 | 预期 |
| --- | --- |
| 1. AI 建议删除节点 | 红色虚线删除待确认样式 |
| 2. 右栏或浮层 | 展示关联边数量提示 |
| 3. 确认 | 弹出删除确认框；确认后节点及关联边删除 |
| 4. 取消 | 删除标记消失，节点保留 |

- [ ] D 通过

#### E. 新增 / 修改边（addEdge / updateEdge）

| 步骤 | 预期 |
| --- | --- |
| 1. addEdge | 边出现在画布，紫色虚线；**边中点**有 ✓/✕ 浮层（`AiStagingChrome`） |
| 2. updateEdge | 边蓝色样式；右栏标签对照 + 确认/取消 |
| 3. 选中边 | `FlowDefaultEdge` / `ConditionEdge` stroke 与 Staging 主题一致 |
| 4. confirm / reject | 与节点语义一致 |

- [ ] E 通过

#### F. 依赖阻断与重试（addEdge → addNode）

| 步骤 | 预期 |
| --- | --- |
| 1. 同一 patch 含 addNode + addEdge，**先对 addEdge 点确认** | **客户端** toast「请先确认依赖的节点变更」（`stagingDependencyHints` 预检）；或服务端 dependencyHints |
| 2. 先 confirm addNode | addNode 成功 |
| 3. 再 confirm addEdge | addEdge 成功 |
| 4. 若 confirm 因校验失败 | 单元展示 errors（`AiStagingConfirmErrors`），保持 pending |
| 5. 修改 draft 后再次确认 | 直至成功或 reject |

- [x] F 通过（依赖预检：`stagingDependencyHints.test.ts`；E2E 重试路径建议浏览器复核）

#### G. 并行单元（A 失败 B 可成功）

| 步骤 | 预期 |
| --- | --- |
| 1. 同一消息下多个 pending 单元，制造单元 A confirm 失败（如非法 URL） | A 显示 error，仍为 pending |
| 2. 对单元 B 点确认 | B 可独立成功，不受 A 阻塞 |
| 3. A 确认进行中 | A 按钮 loading（`confirmInFlight`），B 仍可操作 |
| 4. **快速连续 confirm 多个单元** | 落盘串行（`enqueueConfirmApply`），后写不覆盖先写；刷新后各单元均持久化 |

- [x] G 通过（confirm 落盘队列 + 全链路串行化已实现；E2E 建议浏览器复核步骤 4）

#### H. 运行场景 Staging

| 步骤 | 预期 |
| --- | --- |
| 1. patch 含 addScenario | 左栏场景列表出现 Staging 卡片 + `AiStagingScenarioBanner`（✓/✕） |
| 2. 选中场景，右栏 | `ScenarioConfigPanel` 可编辑；update 类显示原/现对照 |
| 3. 场景 confirm / reject | runConfig 正确变更或回滚 |
| 4. setActiveScenario | 切换默认场景需就地确认 |

- [x] H 通过（场景 draft 落盘：`FlowDesignPatchConfirmServiceTest.confirm_updateScenarioWithDraft_ok`；面板 E2E 建议浏览器复核）

#### I. 确认后自动保存

| 步骤 | 预期 |
| --- | --- |
| 1. AI 侧栏开启「确认后自动保存」 | 偏好写入 localStorage |
| 2. confirm 任一单元成功 | 自动触发 saveFlow；失败时有 warning toast |

- [ ] I 通过（或标记 N/A 若未开启该偏好）

#### J. 撤销（History）

| 步骤 | 预期 |
| --- | --- |
| 1. 连续 confirm 2 个单元 | 每次 pushHistory |
| 2. Ctrl+Z 两次 | 逐步撤销两次 confirm 效果 |
| 3. reject 操作 | 不增加可撤销步数 |

- [x] J 通过（Staging 单元状态撤销：`useFlowHistory.staging.test.ts`；完整画布 Ctrl+Z 仍建议浏览器复核）

#### K. 保存与 Staging 过滤

| 步骤 | 预期 |
| --- | --- |
| 1. 存在 pending addNode，**不 confirm** 直接保存 | 保存内容**不含**该 staging 节点 |
| 2. confirm 后保存 | 节点写入持久化 graph_json |
| 3. pending updateNode 未 confirm 时保存 | 保存的是 baseline 而非 draft |
| 4. 存在 pending 时手动保存 | ElMessage.warning：「尚有 N 项 AI 变更待确认…」；确认后自动保存不弹此提示（`skipPendingWarning`） |

- [x] K 通过（`graphAdapter.test.ts` + `useAiDesign.staging.test.ts`；步骤 4 待浏览器复核）

#### L. 刷新与会话

| 步骤 | 预期 |
| --- | --- |
| 1. 有 pending 未保存，刷新页面 | Staging 消失（内存态）；画布回持久化快照 |
| 2. 有 confirmed 未保存，刷新 | confirmed 内容丢失，显示 dirty 或需重新加载 |
| 3. 切换 AI 会话 | 先 `clearAllStagingState` 清空画布 pending；加载目标会话后 `syncAllStagingFromMessages` |
| 4. 新建会话 / 清空对话 | 画布无上一会话 Staging 残留 |
| 5. 切换测试流 | `resetPanel` 清空 staging 与会话 |

- [ ] L 通过（步骤 3～4 逻辑已实现，待浏览器复核）

#### M. 旧 API 不可用

| 步骤 | 预期 |
| --- | --- |
| 1. DevTools 或 curl 调用 `POST .../patch/preview` | 404 或路由不存在 |
| 2. 正常 confirm 走 `POST .../patch/confirmUnit` | 200 + validation + graphJson |

- [ ] M 通过

#### N. 核心清单速查（与 §3 产品定义对照）

- [ ] AI 返回 patch 后，会话仅显示摘要，无勾选列表  
- [ ] 新增节点出现在画布，可拖拽、可编辑属性、可 confirm/reject  
- [ ] 修改节点显示原/现对照，仅现值可改，confirm 后生效  
- [ ] 删除节点显示待删标记，confirm 后删除  
- [ ] 新增/修改边行为与节点一致（边中点浮层）  
- [ ] 场景变更在运行场景面板确认，不在会话 Diff 中  
- [ ] confirm 非法单元时展示 error，不污染正式图  
- [ ] 单元重试、并行单元、依赖重试均符合 §3.6.1  
- [ ] 全库无 §8 dead symbols（§10.3 已验证）  

---

### 10.6 手工验收 — 代码结构优化（Phase 1～8）

> 本节验证重构后的**可维护性目标**是否在产品行为上无回归。

#### P1. 合并 Chrome 与共享按钮

| 步骤 | 预期 |
| --- | --- |
| 1. pending 节点 | 仅 `AiStagingChrome`（placement=node-corner），无独立 NodeChrome 组件 |
| 2. pending 边 | 边中点 `AiStagingChrome`（placement=edge-midpoint） |
| 3. `AiStagingFieldDiff` / `AiStagingScenarioBanner` | 使用 `AiStagingActionButtons`（full / compact），样式来自 `aiStaging.scss` |
| 4. confirm 失败 | `AiStagingConfirmErrors` 在 Chrome compact 与 FieldDiff 全宽模式均正常展示 |

- [ ] P1 通过

#### P2. 客户端依赖预检

| 步骤 | 预期 |
| --- | --- |
| 1. addEdge 依赖未 confirm 的 addNode 时点确认 | **不发** confirmUnit 或先发预检 warning；与 `stagingDependencyHints.test.ts` 规则一致 |

- [ ] P2 通过

#### P3. 导航与 FitView

| 步骤 | 预期 |
| --- | --- |
| 1. 摘要「定位到画布」 | 使用 `useStagingNavigation.focusGraphStagingUnit` |
| 2. 有 pending 时自动 fit | `AiStagingFitView` 使用 `NODE_MIN_H`（非硬编码 108） |
| 3. confirm 成功 | 高亮相关节点并 fitView |

- [ ] P3 通过

#### P4. 属性面板 DRY

| 步骤 | 预期 |
| --- | --- |
| 1. 节点 / 边 / 场景属性面板 | staging 单元 lookup 行为一致；update 类隐藏普通字段区，仅显示 FieldDiff |

- [ ] P4 通过

#### P5. 主题与边样式统一

| 步骤 | 预期 |
| --- | --- |
| 1. 小地图 pending 节点颜色 | 与 `stagingTheme` 一致（add/update/delete 紫/蓝/红） |
| 2. 运行场景卡片 Staging 边框 | 使用 staging 主题色 |
| 3. 默认边 / condition 边 | `resolveStagingEdgeStroke` 样式一致 |

- [ ] P5 通过

#### P6. 会话截断文案

| 步骤 | 预期 |
| --- | --- |
| 1. 后续消息有 **merged** 或 **pending Staging** 时，编辑/重新生成触发截断确认 | 文案含「截断后待确认项将丢失」（`truncateStagingConfirmText`） |
| 2. 确认截断 | `revertPendingStagingForMessageIds` 回滚画布；`removeMessageUnits` 清理 store |

- [x] P6 通过（pending 检测：`stagingCleanup.test.ts` + `hasPendingStagingFromIndex` 挂接；E2E 截断回滚建议浏览器复核）

---

### 10.7 手工验收 — 边界与易错点

| # | 场景 | 预期 | 勾选 |
| --- | --- | --- | --- |
| 1 | 同一 nodeId 两轮 AI patch | 后到覆盖先到 pending，有 conflict toast | ☑ 单测 `aiStagingStore.conflict.test.ts` |
| 2 | confirm 期间拖动画布改图 | baseGraphHash 不一致 → 提示「画布在确认期间已变更，请再次点击确认」 | ☐ 待浏览器 |
| 3 | deleteNode 确认 | ElMessageBox 二次确认；关联 staging addEdge 一并 reject | ☑ 单测 `useAiStagingConfirm.test.ts` |
| 4 | explainOnly 响应 | 无 patch、无摘要、无 staging 注入 | ☐ |
| 5 | 关闭 AI 面板再打开 | pending 仍保留；工具栏可见待确认态 | ☐ |
| 6 | Run 详情「AI 修复」入口 | 预填 run chip 与 prompt 正常 | ☐ |
| 7 | 节点右键「添加到对话」 | 侧栏打开并插入节点 mention | ☐ |
| 8 | condition 节点出边 Staging | 分支标签 + 边中点浮层不遮挡标签 | ☐ |
| 9 | **并行快速 confirm 多单元** | `enqueueConfirmApply` 串行落盘，刷新后各单元均写入 graph_json | ☑ 逻辑已实现；E2E ☐ |
| 10 | **重新生成 / 编辑历史消息（仅有 pending）** | 弹出截断确认；确认后画布 pending 回滚 | ☑ `hasPendingStagingFromIndex` + `revertPendingStagingForMessageIds`；E2E ☐ |
| 11 | **新建 / 切换 AI 会话** | 画布无上一会话 Staging 节点/高亮；加载后仅当前会话 units | ☑ `clearAllStagingState` + `onSessionLoaded`；E2E ☐ |
| 12 | **有待确认项时手动保存** | warning「尚有 N 项 AI 变更待确认…」；保存内容仍排除 pending | ☑ `useFlowGraph.saveFlow`；E2E ☐ |
| 13 | **底部校验条（仅有 pending add）** | 不因未 confirm 的 staging 节点误报结构 error | ☑ `useFlowValidation` + `stagingFilter`；E2E ☐ |

**UX 优化轮次（2026-07-07）对应实现**：

| 问题 | 关键文件 |
| --- | --- |
| 并行 confirm 后写覆盖 | `useAiStagingConfirm.ts` → `enqueueConfirmApply` |
| 截断不检测 pending / 不回滚画布 | `useAiChatSession.ts`、`stagingCleanup.ts`、`useAiDesign.ts` |
| 切换会话 Staging 残留 | `stagingCleanup.ts`、`useAiStagingHydration.ts`、`useAiDesign.ts` |
| 保存静默丢弃 pending | `useFlowGraph.ts` → `saveFlow({ skipPendingWarning? })` |
| 校验条误报 pending | `useFlowValidation.ts` → `stagingFilter` |

---

### 10.8 性能（参考）

- 取消 debounce 批量 preview，confirm 为按需单次请求，大图下更优  
- Staging 节点即真实 vue-flow 节点，300+ 节点场景需关注；单 patch 增量通常 <20，可接受  
- 可选：在 100+ 节点画布上发 AI 请求，观察注入与 fitView 无明显卡顿（>500ms 需记录）

- [ ] 性能可接受（或记录问题单号：________）

---

### 10.9 验收签字

| 角色 | 姓名 | 日期 | 签字 |
| --- | --- | --- | --- |
| 开发自测 | | | ☐ |
| 产品/业务 | | | ☐ |

---

## 11. 风险与回滚


| 风险                    | 缓解                                                                 |
| --------------------- | ------------------------------------------------------------------ |
| Staging 节点与正式节点 id 冲突 | normalize 阶段已有 id 分配；hydrate 前检测冲突 toast                           |
| 用户未 confirm 即关闭 AI 面板 | Staging 保留；工具栏显示待确认计数                                              |
| 用户未 confirm 即保存       | `toGraphJson` 排除 staging 对象，**仅保存已 confirm 内容**；有待确认项时 `saveFlow` 弹出 warning（自动保存可 `skipPendingWarning`） |
| 前后端版本不一致              | 同版本发布；无 preview 兼容层                                                |
| 回滚                    | Git revert 整 PR；数据库无 schema 变更                                     |


---

## 12. 附录：与 Cursor 代码合并的对照


| Cursor 代码合并               | 本方案 Staging                       |
| ------------------------- | --------------------------------- |
| Diff 块 inline 在编辑器        | 节点/边/场景上 Staging 标记 + 属性对照        |
| Accept / Reject 在 diff 块上 | Accept / Reject 在节点 chrome / 属性面板 |
| 会话不执行合并                   | 会话仅摘要                             |
| 可编辑 incoming 侧            | 可编辑「现值」draft                      |
| 部分 hunk accept            | 单 unit confirm，失败可重试              |


---

## 变更记录

| 日期 | 作者 | 说明 |
| --- | --- | --- |
| 2026-07-06 | AI | 初稿：Staging 合并全量改造方案 |
| 2026-07-06 | AI | 修订 §9：纵向切片、推迟破坏性删除、单元重试；补充 §3.6.1 / §10 |
| 2026-07-06 | AI | Step 1～9 + 结构优化 Phase 1～8 开发完成；§10 扩写为完整验收手册（自动化 + 手工 A～N + P1～P6）；新增 Step 10 |
| 2026-07-07 | AI | Bug 修复：undo 与 Staging 联动、pushHistory 时机、场景 draft 字段、冲突画布回滚、confirm 队列、deleteNode 关联边、AI 设计 persistFilter；§10.2/10.3 自动化验收通过 |
| 2026-07-07 | AI | UX 优化：confirm 全链路串行化、截断 pending 检测与画布回滚、会话切换 `clearAllStagingState`、保存 pending 提示、`useFlowValidation` 排除 staging；新增 `stagingCleanup.ts` + 单测；§10.5 G/K/L、§10.7 #9～13、§10.2 更新为 65 passed |
