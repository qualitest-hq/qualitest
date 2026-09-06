# 质衡 FAQ

> **面向**：日常用质衡做接口调试、造流、Run 的人。  
> **不收录**：Demo 某条业务用例、冒烟勾选、Agent 代点细节——那些见 [全面测试手册.md](./全面测试手册.md) §F / §G。  
> 英文：[faq.en.md](./faq.en.md)

---

## 鉴权与 token

### Run 报 `{{asset.*.token}}` 或旧 `{{flow.token}}` 未定义

常见原因：

1. 登录节点 **extract 路径写错**（管理端 `/login` 用 `$.token`→`asset.adminAuth.token`，客户端用 `$.data.token`→`asset.clientAuth.token`）。
2. 图里用了 Bearer，但**没有**对应端的登录抽取 / 已落盘素材。
3. 登录口应是免登（`mode=none`），却仍被补了托管 Bearer——查项目模板与接口 `auth.mode`。

→ [project-summary.md §4](./project-summary.md) · [flow-variables-and-values.md](./flow-variables-and-values.md)

### 确认 Staging 或保存时报 `AUTH_*`

| 码 | 含义 | 处理 |
| --- | --- | --- |
| `AUTH_LOGIN_EXTRACT_MISSING` | 登录口没抽出托管头所需凭证（asset/flow） | 补 extracts 或让 AI 按托管头占位符补 |
| `AUTH_TOKEN_MISSING` | 后续 HTTP 要用凭证，但图里没有来源 | 同端补登录 extract（或存量 flowSeed 仅对 flow 目标） |
| `AUTH_LOGIN_FLOWKEY_COLLISION` | 两套登录写出同一凭证路径 | 双端分用 `adminAuth` / `clientAuth` |
| `AUTH_HEADER_MANAGED` | 已自动补托管头 | 提示，不拦 |

→ [ai-staging.md §5](./ai-staging.md)

### 账号密码应该放哪

**成功路径、可复用主测号**：素材库 + `{{asset.*}}`。  
**失败路径、场景专用账号**：节点里写**字面量**，勿绑主测号素材。  
**不要**用 flowSeed 塞口令（flowSeed 只适合调试时预置 token）。

→ [assets.md](./assets.md) · [flow-variables-and-values.md §4](./flow-variables-and-values.md)

---

## AI 改图与 Staging

### 助手说改了流，画布还是空的

- 顶栏「nodes 为空」= 库里仍是空图。
- 必须 Staging **逐项 ✓** 再点 **保存**。
- 气泡 **「本轮未提交 Staging」** = 模型只写了方案，没 `submit`——请它落盘，或新开对话 + 短提示 + 接口 id。

→ [ai-staging.md](./ai-staging.md)

### 多轮 AI 修复后图很乱 / 有两个开始节点

Staging **全部 ✕** 取消坏提案，或**新建一条流**重来。不要改浏览器内存状态或直接改库里的 `graph_json`。

→ [ai-staging.md §6](./ai-staging.md)

---

## 素材与文件

### 上传测参文件 Run 失败，或 file 变成普通字符串

产品路径：**素材库**选 file 上传 → 节点 form-data 该行 **type=file** → value = `{{asset.<key>.storagePath}}`。  
节点上填本机绝对路径仅适合本地调试，不能代替素材库验收。

→ [assets.md](./assets.md)

---

## MCP 与集成

### Cursor / 其它 MCP 客户端改不了画布

**设计如此**：MCP **只读**（查流、查失败现场等）。改图须在 Web **AI 助手** → Staging → 保存。

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

模板写入 Profile、登录口免登、**预制登录口**，以及**预制环境（URL + 变量）/ 预制参数（主路径 asset 口令；flow 仅兼容存量）/ 预制测试流**（内置模板种子 `adminAuth` / `clientAuth`，占位环境写成 `http://localhost:8801`；登录流 extracts 派生托管头与 `credentialApi`，**不再**写 `loginHint`）；`template_prompts` 默认可为空（靶场业务提示见 demo 提示集，不进鉴权模板）。不勾无法创建。**业务 API** 仍须 IDEA 插件上传（手册 **T1.3**）。商城类双端项目建议勾「管理端 Bearer」+「客户端 Bearer」。

→ [project-template.md](./project-template.md) · [project-summary.md §4.1](./project-summary.md) · 手册 **T1.2**（项目模板）· **T1.3**（插件上传）
