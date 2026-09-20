# MCP 接入（以 Cursor 为例）

质衡通过 **Streamable HTTP** 暴露项目级 MCP 服务，供 **支持 MCP 的 AI 编辑器 / Agent**（Cursor、VS Code 生态、Claude Code 等）查询本项目的接口、测试流与 Run 现场。  
下文配置以 **Cursor `mcp.json`** 为例；其它客户端只要支持同协议的 HTTP MCP + 自定义 Header，即可按同等字段接入。  
**默认只读**。项目设置有三道独立开关：「允许 MCP 自动写流」（`create_flow` / `update_flow_meta` / `submit_*` / upsert）、「允许 MCP 自动跑流」（仅 `run_test_flow`）与「允许 MCP 导入接口」（仅 `import_apis`）。跑流通常依赖写流已开。Token 只绑定项目身份。未开启时改画布请走 Web 端 AI 面板。

English: [mcp.en.md](./mcp.en.md)

后续未做增强项见：[mcp-backlog.md](./mcp-backlog.md)

---

## 0. 开了写流和跑流，能少干哪些活？

默认 MCP **只能看**（列流、看拓扑、看失败现场）。打开项目设置里的开关后，Agent 才能改图、才能跑：

| 开关 | 人话 | Agent 实际做的事 |
|:-----|:-----|:-----------------|
| **自动写流** | 让 Cursor **帮你搭 / 改测试流**，不用手拖画布 | `create_flow` 建空流，再按步 `submit_*` 加节点、断言、连线；**成功就写库**，Web 画布开着会跟着变 |
| **自动跑流** | 让 Cursor **帮你点运行**，不用切回浏览器 | `run_test_flow` 跑库里最新那张图；挂了用 `get_run_failure` 把失败步骤拉回对话里修 |

只开写流：能造图，跑还得回 Web 点。两道都开：同一句对话里就能「造完 → 跑通 → 挂了再修」。

### 写接口的人（后端）

改完一个 Controller / Handler，以前常是：开 Postman 改路径 → 复杂场景还要手拖节点。现在可以：

1. 在 Cursor 里直接说「登录拿 token → 调这个新接口 → 断言 200」  
2. Agent 写流并（开了跑流的话）当场跑  
3. 挂了看失败步骤，同一对话改断言 / 参数再跑  

少切工具；复杂链路不用自己点满属性面板。落的是平台上的图（可协作、可切环境），不是往业务仓塞一份难 review 的脚本。

### 写页面的人（前端）

联调时卡点多半是：**页面调不通，不知道是前端拼错了，还是后端链路 / 鉴权就没通。** MCP 帮你把「接口链路」从浏览器旁路里拆出来验：

1. **先问清链路上有没有现成流** — `list_flows` / 看拓扑，避免自己再造一份重复用例  
2. **页面依赖的登录 → 列表 → 提交**，让 Agent 按同样顺序写流并跑一遍 — 通了再查前端请求头 / 参数；不通就知道锅在服务端哪一步  
3. **接口清单从业务仓抽进项目**（开「导入接口」）— `import_apis` 不限 Java；前端仓里的 OpenAPI、router、fetch 封装都能当上下文  
4. **401 / 业务码对不上时** — 跑流看失败现场，比只盯 Network 面板少猜一轮  

一句话：**写流 = 帮你搭编排；跑流 = 帮你点运行。** 后端少离开 IDE 自测；前端少在「页面 ↔ Postman ↔ 猜后端」之间空转。

---

## 1. 准备

1. 登录质衡 → 打开目标 **测试项目** → **项目设置**。
2. 生成 / 复制 **Project Token**（刷新后旧 Token 立即失效）。
3. 同页「Cursor MCP」卡片可一键复制完整 `mcp.json` 片段；也可按下方模板手写。
4. （可选）开启 **「允许 MCP 自动写流」** 并保存，才能 `create_flow` / `update_flow_meta` / `submit_*` / upsert。
5. （可选）开启 **「允许 MCP 自动跑流」** 并保存，才能调用 `run_test_flow`（须先开写流；关写流会一并关掉跑流）。
6. （可选）开启 **「允许 MCP 导入接口」** 并保存，才能调用 `import_apis`（只控制导入接口，不控制改图与跑流）。
7. 开关变更后**必须重连或刷新 MCP**，否则编辑器常仍只列旧工具列表（服务端不主动推送工具列表变更）。
8. （可选）顶栏「MCP 造流 Agent 规程」：弹窗按编辑器切换（Cursor / Claude Code / Copilot / Windsurf / Continue / Trae / AGENTS.md），复制后保存到对应规则文件；也可在已接 MCP 的编辑器里用示例「同步/更新本地 Skill」从 Prompt 生成带 `guideVersion` 的 Cursor Skill。

本地开发默认后端：`http://127.0.0.1:8800`。  
Compose 全栈经 Nginx 时，把 `url` 改成浏览器能访问到的 API 根（常见为 `http://localhost/api/project/mcp`），以项目设置里生成的为准。

---

## 2. Cursor `mcp.json`

把下列内容合并进 Cursor 的 MCP 配置（用户级或项目级均可）。`X-Project-Token` 换成真实 Token。

```json
{
  "mcpServers": {
    "qualitest": {
      "url": "http://127.0.0.1:8800/api/project/mcp",
      "headers": {
        "X-Project-Token": "<YOUR_PROJECT_TOKEN>"
      }
    }
  }
}
```

| 字段 | 说明 |
|:-----|:-----|
| `url` | MCP 端点，路径固定为 `/api/project/mcp`（无尾斜杠） |
| `headers.X-Project-Token` | 项目 Token；只绑定项目身份（读写由项目设置 MCP 开关控制），按最小权限发放 |

配置生效后，在 Cursor Agent / Chat 中应能看到名为 `qualitest` 的 MCP 服务器及其工具列表。

---

## 3. 工具（摘要）

### 3.1 默认只读（始终可用）

MCP 侧默认 **17** 个只读工具（相对 Web AI 面板：多 `list_flows` / `get_flow` / `get_mcp_guide_version`）。

| 工具 | 用途 |
|:-----|:-----|
| `list_flows` | 按关键词列举本项目测试流摘要 |
| `get_flow` | 读单条流完整 `graphJson`（浏览拓扑优先用下面两个） |
| `get_graph_summary` / `get_subflow_detail` | 拓扑摘要、子流结构；`get_graph_summary` 另含 `mermaid`（flowchart 正文，供 Cursor 预览） |
| `get_flow_meta` | 场景、seed、flowOutputs 等元数据 |
| `get_node_detail` / `get_edge_detail` / `get_scenario_detail` | 单个节点 / 边 / 场景配置 |
| `get_run_failure` | 失败 Run 的步骤现场 |
| `get_flow_api_health` | HTTP 节点绑定 / API 语义健康告警 |
| `search_apis` / `get_api_details` | 查项目接口（详情支持一次传多个 id） |
| `list_project_envs` | 环境列表 |
| `list_asset_variables` | 项目素材库 key/字段名，不含明文 |
| `list_project_auth_profiles` | 项目鉴权 Profile 摘要（无密钥明文） |
| `list_subflow_templates` | 平台子流模板 |
| `get_mcp_guide_version` | 造流规程合成 `guideVersion`（内容指纹 + 可选 `+autowrite`/`+autorun`/`+importApis`）；与本地 Skill 字符串全等则未过期 |

典型勘察顺序：`list_flows` → 记下 `testFlowId` → `get_graph_summary` / `get_run_failure`。

### 3.2 Prompts / Resources（规程下发）

握手 `initialize` 声明 `prompts` 与 `resources`；`serverInfo.guideVersion` 与 `get_mcp_guide_version` 同值（均为合成串）。

`guideVersion` 格式：`{CORE+SURVEY+FIX_RUN 源文指纹 12 位}[+autowrite][+autorun][+importApis]`。改项目写流/跑流/导入开关后后缀会变，须重连 MCP 并再 sync Skill。

`prompts/get` / `resources/read` / 本地 Skill 正文均按当前项目三道开关裁剪（关导入则无 `import_apis` 段；关写流则无 `submit_*` 等；关跑流则无立刻 `run_test_flow` 规程）。

| Prompt | 用途 |
|:-------|:-----|
| `qualitest_core` | 造流 / 修流硬规矩（与 Resource `qualitest://docs/core` 同源，已按开关裁剪） |
| `qualitest_survey` | 只读勘察推荐顺序 |
| `qualitest_fix_run` | 修失败 + 最多再修 2 轮（关写流时无写流步骤） |
| `qualitest_sync_local_skill` | 安装或更新 `.cursor/skills/qualitest/SKILL.md`（先比合成 guideVersion，全等则停止） |

- **不关闭** `prompts/list`：目录始终可见，以便服务端规程变更后再次 sync。
- 本地 Skill 已带相同 `guideVersion` 时：日常只用 Skill，勿再 `prompts/get` 三条正文（防叠灌）。
- 顶栏「复制规程」可带当前路由 `testProjectId` 按项目开关裁剪；未进入项目时为只读门控。

### 3.3 MCP 自动写工具（须「允许 MCP 自动写流」）

开启后 `tools/list` 追加：`create_flow`、`update_flow_meta`、全部 `submit_*`、`upsert_asset_variables`、`upsert_auth_profile`、`append_api_design_hints`（**不含** `run_test_flow`）。

- **每次成功 `submit_*` 立即写库**；同流已在 Web 打开时画布自动同步。
- 改名称/说明用 `update_flow_meta`（成功即落库，不动画布）；禁止用 `create_flow` 新建冒充改名。
- 无合适流时可先 `create_flow` 拿 `testFlowId`；改图须带已有 `testFlowId`。
- 若 Web 本地有未保存修改或未决 Staging，会提示「放弃本地并拉取」，不会静默覆盖。
- 未开启时调用写工具会得到明确拒绝文案。
- 若写工具返回 `lockHeldBy`：另一端（Web 脏稿持锁 / 保存）占用该流写锁，按 `hint` 稍后重试或换一流。

### 3.4 MCP 自动跑流（须「允许 MCP 自动跑流」）

开启后追加 `run_test_flow`：跑库中最新图；须显式 `testFlowId`。通常须同时开启写流。关写流时服务端会强制关掉本开关。

### 3.5 MCP 导入接口（须「允许 MCP 导入接口」）

开启后追加 `import_apis`：将结构化 `items[]`（method、path、name、参数摘要等）按 HTTP 方法 + 规范化 path 写入本项目接口库（已存在则更新，不存在则新增），成功即落库；回执含 created/updated/skipped/conflicts 与 `testProjectApiId`。不会改写接口上的设计提示。**任意语言栈**均可：由 AI 从业务仓（Controller / router / OpenAPI 注释等）抽取后调用即可，不依赖 IDEA / Java。本开关不影响写流权限；IDEA 插件的 REST 导入不受本开关限制。

---

## 4. 示例提问

在 Cursor 里直接问即可（模型会调 MCP 工具）。建议先让 AI `list_flows`，再带上具体 `testFlowId`。

**① 这个项目里有哪些测试流？**

```text
用 qualitest MCP 的 list_flows 列出当前项目的测试流（可按名称搜「登录」），
返回 testFlowId 和名称即可。
```

**② 这条流大概长什么样？**

```text
testFlowId 用 <上一步拿到的 id>。
用 get_graph_summary（或 get_subflow_detail）说明：有哪些节点、主路径怎么走、关键断言在哪。
拓扑用 get_graph_summary / get_node_detail。
```

**③ 上次跑挂在哪？**

```text
同一个 testFlowId。调用 get_run_failure，总结失败步骤、断言/HTTP 错误信息，
以及建议我下一步在画布上改哪里（只给建议）。
```

**④ 同步 / 更新本地 Cursor Skill**

```text
用质衡 MCP 执行 qualitest_sync_local_skill（安装或更新本地 Skill）：
1. 先 get_mcp_guide_version，记下服务端 guideVersion（合成串：指纹[+autowrite][+importApis]）。
2. 若业务仓已有 .cursor/skills/qualitest/SKILL.md 且文内 guideVersion 与服务端字符串全等：停止；勿再 get 正文。
3. 否则 prompts/get qualitest_core（正文已按当前开关裁剪）；写入 SKILL.md = Cursor frontmatter（含 guideVersion）+ CORE 正文。
4. 之后日常造流只靠本地 Skill；仅当 version 不一致（含改开关导致后缀变化）或我明确要求更新时再跑本流程。改开关后须先重连 MCP 再 sync。
```

---

## 5. 安全与限制

- Token 等同项目凭证：勿提交进 Git / 截图外传；泄露后立即在项目设置刷新。
- MCP **默认只读**；写流与导入接口由项目设置两道独立开关控制（不由 Token「权限」区分）。落盘规则见 [ai-staging.md](./ai-staging.md)。卡住见 [faq.md](./faq.md)。
- 未开 MCP 写权限时：顶栏「MCP 造流 Agent 规程」可复制单功能 / 整项目提示词贴到 Cursor，汇总短提示后再贴回 Web 造流。
- 靶场联调、自然语言造流示例见：[qualitest-demo · AI 提示集](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md)。
- IDEA / OpenAPI 同步后：检查登录口（`/login`、`/api/account/auth/login` 等）资产的 `auth.mode` 应为 `none`；若仍为 `inherit`，造流可能误补托管 Bearer。存量可用 `sql/fix_anonymous_auth_builtin_paths.sql` 预览后修复。

---

## 6. 排障

| 现象 | 排查 |
|:-----|:-----|
| Cursor 连不上 | 后端是否已起；`url` 主机/端口是否与浏览器访问一致（IDE 不走 Vite 代理） |
| 401 / 无工具 | Token 是否过期或复制不完整；header 名是否为 `X-Project-Token` |
| 工具报无权限 / 空列表 | Token 是否属于当前要查的那个项目 |
| 只有只读工具 | 是否已开启并**保存**「允许 MCP 自动写流」和/或「允许 MCP 导入接口」；保存后**必须**重连/刷新 MCP |
| 只有旧配置 | 刷新 Token 后须重新复制整段 `mcp.json` |
