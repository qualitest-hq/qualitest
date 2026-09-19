# MCP 后续优化 backlog

本文汇总对话中已讨论、**尚未实现**的 MCP 相关增强。  
已落地（Prompts / Resources、`guideVersion`、本地 Skill 由 frontmatter+CORE 拼接、**省略 `tools.listChanged` + FAQ 纠漂**、**画布 SSE last-wins 逐帧串行**、文档与注释等）不在「待办」叙事中展开；§1.1 / §1.3（last-wins）/ §2.1 仅留简短结案；§1.2（`submit_batch`）、§3.0（可喂 OpenAPI）已标暂缓。  
待办里较稳的下一刀：§2.2 `get_flow` compact。§1.3「减闪」仍是猜测、未排期。

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
画布同步见 **§1.3**（last-wins 已结案；与 `submit_batch` 解耦）。

---

### 1.3 ~~画布 SSE：last-wins 丢增量~~（已修）与减闪（猜测，未排期）

**结案（last-wins）**  
已去掉图变更 500ms 防抖覆盖。`useExternalGraphSync` 对 `graphCommitted` 改为**队列串行**：来一帧 `await applyGraphFromServer` 一帧（有 patch 走增量 merge，无则 `loadFlow`），不再 last-wins 丢中间帧。未做跨帧 patch 合并、未做多帧整图兜底。后端仍每次写库立刻 SSE。

**减闪（猜测，未证实）**  
连写时多次 toast / 高亮 / 灌图是否「闪」、怎么收，未复现、方案未定——**不要**与 last-wins 绑死，未证实前可不排期。

**关键文件**  
`qualitest-ui/.../useExternalGraphSync.ts`（`enqueueGraph` / `pumpGraphQueue`）、`mergeExternalGraphPatches.ts`。

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
3. ~~修 last-wins 丢增量（§1.3）~~（已完成：逐帧串行 apply；减闪仍猜测未排期）  
4. Token / 审计 → 运维写工具 → 多实例 SSE（若将来做推送再连带）  
5. ~~`import_apis` 可喂 OpenAPI~~（暂缓，见 §3.0）  
6. ~~批量 submit / patch~~（暂缓，见 §1.2；勿与 §1.3 混为一谈）

每项开工前用本文对应小节做验收清单即可。
