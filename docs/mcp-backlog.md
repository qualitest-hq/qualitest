# MCP 后续优化 backlog

本文汇总对话中已讨论、**尚未实现**的 MCP 相关增强。  
已落地（Prompts / Resources、`guideVersion`、本地 Skill 由 frontmatter+CORE 拼接、**省略 `tools.listChanged` + FAQ 纠漂**、文档与注释等）不在「待办」叙事中展开；§1.1 / §2.1 仅留简短结案；§1.2（`submit_batch`）、§3.0（可喂 OpenAPI）已标暂缓。  
待办里较稳的下一刀：§2.2 `get_flow` compact。§1.3：**last-wins 是真缺口**（有空或复现后修）；「闪」仍是猜测。

优先级按「对造流体感 / 客诉」大致排序，实施时可再拆迭代。

---

## 1. 高优先级

### 1.1 ~~真正推送 `tools/list_changed`（或诚实降级）~~（已选降级）

**结案**  
不推送 `notifications/tools/list_changed`。`initialize` 的 `capabilities.tools` 为空对象（**不声明** `listChanged`），与 `prompts` / `resources` 一致。产品与文档写死：改写流 / 导入开关后**必须重连或刷新 MCP**。

（曾考虑做实 SSE 推送；远程 HTTP 客户端对 GET SSE + list_changed 支持不可靠，收益不确定，故不采用。）

---

### 1.2 ~~批量写图 / 小图 patch（`submit_batch`）~~（暂缓）

**决策**  
先不做 MCP `submit_batch` / 整包 raw「小图 patch」。维持「每次一个 `submit_*`、成功即写库」。吞吐不够优先靠 §2.2 控 token、规程与步数，而不是合并写工具。

**暂缓原因**  
全有或无时，一个 unit 校验失败 → 整批作废、模型要改一整坨再重试，失败面比「多调几次单 submit」更大。少 round-trip / 少推画布是副产品，不值得为此上 batch。  
Web 全自动同回合已可回合末一次 commit；MCP 每 call 独立 HTTP——缺口仍在，但用 batch 工具补的性价比当前不够。

**原目标（备查）**  
受限多单元一次落盘（typed `submit_batch` 或一批 `nodes+edges`）；成功即写库；处理好 Web 脏稿 / Staging / 写锁。

**相关**  
画布同步见 **§1.3**（last-wins 真缺口；与 `submit_batch` 解耦）。

---

### 1.3 画布 SSE：last-wins 丢增量（真缺口）与减闪（猜测）

**先分清两件事**

| | last-wins 丢增量 | 观感闪 |
|:--|:-----------------|:-------|
| 是否真实 | **代码路径上是真的** | 猜测，未复现 |
| 何时踩 | 同一条流 **&lt;≈500ms 内** 连到 ≥2 次 `graphCommitted`，且每帧只带本单元 patch | 连写时多次 toast/高亮/灌图 |
| 后果 | 画布可能缺中间节点/边（库是全的） | 看着抖 |
| 咋整 | 见下「整法」 | 未证实前可不排期 |

现网已有 500ms 防抖；MCP tool 间隔常 &gt;500ms 时，last-wins **也可能很少踩到**——但一旦同窗口多帧，现逻辑就会丢，这是确定的实现缺陷，不是产品想象。

**真缺口整法（只动前端，推荐）**

问题出在 `scheduleGraph`：窗口内 `lastGraphEvent = event` **覆盖**，到期只 apply 最后一帧。

改法：

1. 窗口内改成 **数组缓冲**（或按 id 累加 patch），不要覆盖。  
2. 到期 **一次** apply：  
   - 1 帧 → 现逻辑（有 patch 增量 / 无则 `loadFlow`）。  
   - ≥2 帧 → 按 id **合并**各帧 `nodePatches` / `edgePatches` / `deleted*` → 一次 `mergeExternalGraphPatches`；失败或全无 patch → `loadFlow`。  
3. toast / 高亮顺带合成一次即可（减闪是附带，不是开工前提）。  
4. **1 帧仍到期就推**，不必等第二帧；后端仍每次写库立刻 SSE。  
5. 脏稿 / Staging 不覆盖、MCP 成功即写库 —— 都不动。

更偷懒但正确的变体：窗口内曾 ≥2 帧则到期只 `loadFlow` 一次（不丢图，但没用上增量）。正式更干净的是跨帧合并增量。

**勿改坏**  
增量优先、失败回退整图、冲突条幅、非整页 F5。

**何时做**  
- 改 `useExternalGraphSync` 时顺手修 last-wins，或自测用短间隔连写打出缺节点后再修。  
- **不要**等「闪」被证实才修 last-wins；也 **不必**为未证实的闪单独开大迭代。

**复现（验证缺口）**  
打开画布 + MCP 写流；若能让两次 `graphCommitted` 落在 500ms 内且带不同节点 patch，看画布是否只出现最后一批节点。能复现 = 必修；复现不了 = 仍建议有空修掉 last-wins（防御性），优先级低于 §2.2。

**关键文件**  
`qualitest-ui/.../useExternalGraphSync.ts`（`scheduleGraph`）、`mergeExternalGraphPatches.ts`。

---

## 2. 中优先级

### 2.1 ~~修正 FAQ 与现网能力漂移~~（已完成）

**结案**  
`faq.md` / `faq.en.md` 已改为：默认只读；开启并保存写流 / 导入开关后可经 MCP 改图、跑流、导入；改开关后必须重连 MCP。

---

### 2.2 弱化 `get_flow` 整图默认

**现状**  
工具可返回整份 `graphJson`；规程要求不要贴整图，模型仍易踩坑烧 token。

**目标**  

- 默认返回摘要 / `compact`；  
- 整图须显式参数（如 `includeGraphJson=true`）；  
- 浏览拓扑继续引导 `get_graph_summary`（含 mermaid）。

**价值**  
控上下文、降低误用。

---

### 2.3 权限与审计再细一点

**现状**  
Project Token 只绑项目；读写靠两个项目级布尔开关，无细粒度 Token、无专用审计视图。

**目标（可分步）**  

1. 只读 Token / 短期写 Token（或 Token 作用域标记）；  
2. MCP 写操作审计日志（谁、何时、哪条流、哪类工具）；  
3. 企业侧可查、可导出。

不必一次上完整 RBAC。

**价值**  
企业安全与追责门槛。

---

### 2.4 运维类写能力补齐

**现状**  
环境 CRUD、复制/归档/删流等多靠 Web。

**目标**  
按使用频率挑几项进 MCP（建议先做「复制流再改」），仍受写流开关或单独开关控制。

**价值**  
少切 IDE ↔ Web。

---

## 3. 低优先级 / 可暂缓

### 3.0 ~~`import_apis` 支持可喂源（OpenAPI / Swagger）~~（暂缓）

**决策**  
先不做。现有 `items[]` 手抽 + IDEA 插件同步已够闭环到 `callMode=project`；OpenAPI 解析是净增能力（质衡 MCP 本身也不拉源文档），收益相对 §2.2 控 token 不明显。有明确客诉或契约仓为主真相源时再开。

**原目标（备查）**  
保留 items 幂等 upsert；另支持 OpenAPI/Swagger URL 或 YAML/JSON 原文导入。

---

### 3.1 MCP 会话与 SSE 多实例化

**现状**  
会话登记多在单 JVM 内存；多节点部署下 list_changed / 会话不共享。

**目标**  
会话与待推事件进 Redis（或等价），与推送方案一起做。

**何时做**  
生产多实例且要做 1.1 推送时再上。

---

### 3.2 协议版本追新

**现状**  
协议版本声明为 `2024-11-05`。

**目标**  
等主流客户端（Cursor 等）稳定支持更新协议后再升；优先把现有 capability 做实。

---

### 3.3 产品叙事上弱化「本地 Skill」通道（可选产品决策）

**现状**  
规程正文已统一为 CORE；本地 Skill = frontmatter + guideVersion + CORE，与 MCP Prompt 并列。

**若决策「只推 MCP Prompt」**  

- 顶栏可弱化「复制 Cursor Skill」，改为引导 Prompt / sync；  
- 或保留 sync 仅作可选离线缓存。  

**若不做产品砍通道**  
保持现状即可，无需工程必做项。

---

### 3.4 明确不做（已否决，勿再开）

| 想法 | 否决原因 |
|:-----|:---------|
| 装完 Skill 后清空 / 关闭 `prompts/list` | 堵死服务端规程更新后再 sync 的路径 |
| 项目级「隐藏 Prompt 目录」当防叠灌手段 | 同 Token 成员共享，且更新体验差；防叠灌改用 `guideVersion` |
| 把本仓 MySQL / Playwright MCP 写成质衡产品能力 | 仅开发辅助，勿混进 `/api/project/mcp` |

---

## 4. 建议落地顺序（参考）

1. ~~FAQ 修正 / `listChanged` 降级~~（已完成）  
2. `get_flow` 默认 compact（§2.2）  
3. 修 last-wins 丢增量（§1.3）——小改前端防抖缓冲；勿和「减闪猜测」绑死
4. Token / 审计 → 运维写工具 → 多实例 SSE（若将来做推送再连带）  
5. ~~`import_apis` 可喂 OpenAPI~~（暂缓，见 §3.0）  
6. ~~批量 submit / patch~~（暂缓，见 §1.2；勿与 §1.3 混为一谈）

每项开工前用本文对应小节做验收清单即可。
