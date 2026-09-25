# 质衡 FAQ

> **面向**：日常用质衡做接口调试、造流、Run 的人。  
> **不收录**：Demo 某条业务用例、冒烟勾选、Agent 代点细节——那些见 [全面测试手册.md](./全面测试手册.md) §F / §G。  
> 英文：[faq.en.md](./faq.en.md)

---

## 鉴权与 token

### Run 报凭证占位符未定义（`{{asset.*}}` / `{{flow.*}}` / `{{env.*}}`）

常见原因：

1. 登录节点 **extract 路径写错**，或未与 Profile 托管头目标对齐。
2. 图里用了 Bearer，但**没有**对应来源（extract / flowSeed / env / 已落盘素材）。
3. 登录口应是免登（`mode=none`），却仍被补了托管 Bearer——查项目模板与接口 `auth.mode`。

→ [project-summary.md §4](./project-summary.md) · [flow-variables-and-values.md](./flow-variables-and-values.md)

### 确认 Staging 或运行时报 `AUTH_*`

`AUTH_TOKEN_MISSING` / 登录 extract / HTTP 必填等 **只在运行硬拦**（保存可带错落盘）。Staging ✓ 能过、点保存也成功、点运行才报 AUTH → 先查项目 Profile 托管头是否绑错端（如客户端误用 `adminAuth`）、再查登录 extract，不是 confirm / 保存 bug。

**AI 自愈**（与人手改设置页等价）：

1. 造登录或修 `AUTH_TOKEN_MISSING` 前先 `list_project_auth_profiles`，核对托管头凭证目标。
2. Profile 绑错端 → `upsert_auth_profile`（半自动：聊天侧确认卡片写入 `auth_config`；全自动：工具内直写）。未确认的鉴权提案会挡住 `run_test_flow` / 隐式落盘。
3. 下一轮设计请求会带上校验条里的 `runRiskWarnings`，模型可据此继续改图。
4. 登录 extract 写成 `name=adminAuth.token` 且缺 `entryKey` 时，服务端会拆成 `entryKey` + `fieldPath`。

| 码 | 含义 | 处理 |
| --- | --- | --- |
| `AUTH_TOKEN_MISSING` | 后续 HTTP 要用凭证，但图里没有来源 | 补登录 extract / flowSeed / 环境变量等与 Profile 托管头一致的来源；文案含「疑似绑错端」时改项目鉴权 |
| `AUTH_LOGIN_FLOWKEY_COLLISION` | 两套登录写出同一凭证路径 | 双端分用 `adminAuth` / `clientAuth` |
| `AUTH_HEADER_MANAGED` | 已自动补托管头 | 提示，不拦 |

→ [ai-staging.md §5](./ai-staging.md)

### 客户端 HTTP 却报缺 `adminAuth.token`

多半是 **Profile 托管头绑错端**（客户端 Profile 的 `headerValueTemplate` 写成了 `{{asset.adminAuth.token}}`），不是画布缺登录节点。

**怎么来的**：精简模板无登录流时，旧版 Apply 曾弱默认两端都成 `adminAuth` Bearer；现已改为读 `match_config.credential`，仍无法派生则拒绝该条。

**怎么修（存量项目不自动改库）**：

1. 项目设置 → 鉴权：改对应 Profile 的托管头；或删同名 Profile 后「从项目模板添加」再 Apply。
2. AI：`upsert_auth_profile` 改 `headerValueTemplate`（半自动确认）。
3. 新建项目勾正确模板 Apply 即可一次写对。

→ [project-template.md](./project-template.md) · [project-summary.md §4](./project-summary.md)

### 账号密码应该放哪

**成功路径、可复用主测号**：素材库 + `{{asset.*}}`。  
**失败路径、场景专用账号**：节点里写**字面量**，勿绑主测号素材。  
**不要**把明文口令写进图或 flowSeed；可复用账号优先素材库。

→ [assets.md](./assets.md) · [flow-variables-and-values.md §4](./flow-variables-and-values.md)

---

## AI 改图与 Staging

### 助手说改了流，画布还是空的

- 顶栏「nodes 为空」= 库里仍是空图。
- 必须 Staging **逐项 ✓** 再点 **保存**。
- 本轮助手消息下若**没有** Staging 变更摘要 = 模型只写了方案、没改画布——请它按单元提交修改，或新开对话 + 短提示 + 接口 id。

→ [ai-staging.md](./ai-staging.md)

### 多轮 AI 修复后图很乱 / 有两个开始节点

Staging **全部 ✕** 取消坏提案，或**新建一条流**重来。不要改浏览器内存状态或直接改库里的 `graph_json`。开了「全自动」时同样：失败后再修超过 2 轮仍乱 → 切回半自动、新建流。

### 「全自动」停了 / 没写库没跑

- 默认是**半自动**：Staging / 素材 / 鉴权 Profile 提案须人审，确认后**人手保存**画布；未切到全自动时模型没有 `run_test_flow`。
- **只拨开关不会跑**：须再发一条造流/修复类请求；开关只作用于该次及之后发送。
- 全自动下素材与鉴权 Profile 的 `upsert` 会直接落盘；造流/修复完成后模型应主动 `run_test_flow`（自动写库），无需再 commit。纯答疑可不跑。
- 尚有未确认的素材或鉴权提案时，`run_test_flow` / 隐式落盘会拒绝。
- Run 就绪/AUTH 硬拦、paused（await-input）也会停；看助手气泡与工具回执 hint。
- **边跑边亮**：`run_test_flow` 与人手「运行」共用同一 trigger——先返 `runId`、执行中按节点落库；SSE 推 `runStarted` 后画布轮询详情高亮当前步（不再等跑完才回放）。

→ [ai-staging.md §6](./ai-staging.md)

---

## 素材与文件

### 上传测参文件 Run 失败，或 file 变成普通字符串

产品路径：**素材库**选 file 上传 → 节点 form-data 该行 **type=file** → value = `{{asset.<key>.storagePath}}`。  
节点上填本机绝对路径仅适合本地调试，不能代替素材库验收。

→ [assets.md](./assets.md)

---

## MCP 与集成

### 开了写流和跑流，能少干哪些活？

| 开关 | 人话 | 谁更吃这套 |
|:-----|:-----|:-----------|
| **自动写流** | 让 Cursor 帮你搭 / 改测试流，不用手拖画布 | **后端**：改完接口顺手造链路；**前端**：按页面路径（登录→列表→提交）先验通接口再查页面 |
| **自动跑流** | 让 Cursor 帮你点运行，挂了把失败步骤拉回对话 | 两边都少切浏览器；前端还能先分清是页面锅还是服务端链路锅 |

默认只能看。写库 / 跑库要在项目设置里打开。更细的场景说明见 [mcp.md §0](./mcp.md)。

### Cursor / 其它 MCP 客户端改不了画布

**默认只读**（查流、查失败现场等）。项目设置开启并**保存**「允许 MCP 自动写流」后，可经 MCP 调用 `submit_*` / `create_flow` 等改图；另开「允许 MCP 自动跑流」后可调 `run_test_flow`；「允许 MCP 导入接口」控制 `import_apis`（不影响写流）。

**改开关后必须重连或刷新 MCP**，否则编辑器常仍只见旧工具列表（服务端不主动推送工具列表变更）。未开写流时改图仍走 Web **AI 助手** → Staging → 保存。

→ [mcp.md](./mcp.md) · [ai-staging.md §4](./ai-staging.md)

---

## 环境与部署

### Run 报 `TF_STEP_ERROR: ConnectException`

HTTP 步骤连不上被测地址。常见：

1. 项目环境 `baseUrl` 仍是建项占位 `http://127.0.0.1`（无端口 → 打 80）——应改为 demo 的 `http://localhost:8801`（勾内置模板新建应自动对齐；存量可环境管理手改，或「从项目模板添加」再 Apply 补种占位 URL）。
2. demo API（默认 **8801**）未启动。

画布选环境：左栏「运行场景」→ 选中场景 → 右栏「环境」下拉。

→ 手册 **T1.4** · [project-summary.md](./project-summary.md)

### 连不上平台 / MCP 401

- 后端是否已起；MCP `url` 是否与浏览器访问的 API 一致（本地常见 `http://127.0.0.1:8800/api/project/mcp`）。
- Project Token 是否过期；Header 是否为 `X-Project-Token`。

→ [deploy.md](./deploy.md) · [mcp.md §6](./mcp.md)

### 新建项目时为什么要勾「项目模板」

模板写入 Profile、登录口免登、**预制登录口**，以及**预制环境（URL + 变量）/ 预制参数（主路径 asset 口令；flow 仅兼容存量）/ 预制测试流**（内置模板种子 `adminAuth` / `clientAuth`，占位环境写成 `http://localhost:8801`；登录流 extracts 派生托管头）；`template_prompts` 默认可为空（靶场业务提示见 demo 提示集，不进鉴权模板）。不勾无法创建。**业务 API** 走 IDEA 插件上传，或 MCP `import_apis`（项目设置开「允许 MCP 导入接口」），也可在 Web 接口库手工维护——**不限 Java**。商城类双端项目建议勾「管理端 Bearer」+「客户端 Bearer」。

→ [project-template.md](./project-template.md) · [project-summary.md §4.1](./project-summary.md) · [mcp.md §3.5](./mcp.md) · 手册 **T1.2**（项目模板）· **T1.3**（插件上传，可选）
