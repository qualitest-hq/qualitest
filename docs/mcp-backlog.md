# MCP 后续优化 backlog

本文汇总对话中已讨论、**尚未实现**的 MCP 相关增强。  
已落地（Prompts / Resources、`guideVersion`、本地 Skill 由 frontmatter+CORE 拼接、文档与注释等）不在此列。

优先级按「对造流体感 / 客诉」大致排序，实施时可再拆迭代。

---

## 1. 高优先级

### 1.1 真正推送 `tools/list_changed`（或诚实降级）

**现状**  
`initialize` 里声明 `tools.listChanged: true`，但开关变更后 SSE 基本只发连接注释，客户端常仍只见旧工具列表，须人工重连。

**目标**  
二选一，不要空头支票：

- **做实**：项目「允许 MCP 全自动写流 / 导入接口」变更后，向该项目相关 MCP 会话推送 `notifications/tools/list_changed`（必要时再推 prompts 若有变更）。
- **降级**：去掉或改为 `listChanged: false`，文档写死「改开关后必须重连 MCP」。

**价值**  
减少「开了写流还是只有只读工具」类客诉。

---

### 1.2 `import_apis` 支持可喂源（OpenAPI / Swagger）

**现状**  
只能传结构化 `items[]`；Agent 从业务仓手抽 Controller，轮次多、易错。

**目标**  
在保留现有 items 幂等 upsert 的前提下，增加至少一种：

- OpenAPI / Swagger **URL** 拉取后导入；或  
- **文件 / 原文**（YAML/JSON）导入；  
- 语义尽量贴近 IDEA 插件 REST 导入（分组、注释、鉴权建议等可渐进）。

**价值**  
缩短「缺接口 → 能 `callMode=project`」路径。

---

### 1.3 批量写图 / 小图 patch

**现状**  
规程要求每次一个 `submit_*` 单元，大流造流轮次多、易中断。

**目标**  
提供受限的「多单元一次落盘」能力，例如：

- 一批 `nodes + edges` 的 patch；或  
- `submit_batch` 接收多个已校验单元，事务或按序落库并一次推画布。

须保留「成功即写库」语义，并处理好与 Web 脏稿 / 写锁冲突。

**价值**  
端到端造流吞吐显著提升。

---

## 2. 中优先级

### 2.1 修正 FAQ 与现网能力漂移

**现状**  
`faq.md` 仍写「MCP 只读、改不了画布」；与项目开关写流、`mcp.md` 矛盾。

**目标**  
FAQ 改为：默认只读；开启并保存写流 / 导入开关后可经 MCP 改图、跑流、导入；开关后若工具列表不变请重连。

**价值**  
成本低，减少误判。

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

1. FAQ 修正（半天级）  
2. `list_changed` 做实或降级（与客诉直接相关）  
3. `import_apis` 可喂 OpenAPI  
4. 批量 submit / patch  
5. `get_flow` 默认 compact  
6. Token / 审计 → 运维写工具 → 多实例 SSE  

每项开工前用本文对应小节做验收清单即可。
