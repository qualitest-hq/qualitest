# 质衡 · 产品概念地图

> **用途**：一屏看清「系统里有什么、主链路怎么走、文档去哪找」。  
> **不是**：部署手册、节点字段字典、测试勾选表 —— 那些见文末索引。  
> 英文：[project-summary.en.md](./project-summary.en.md)

---

## 1. 工作区地图


| 目录                           | 角色                                         |
| ---------------------------- | ------------------------------------------ |
| `qualitest/`                 | 主平台后端（Spring Boot）+ `qualitest-ui/` 前端 SPA |
| `qualitest-demo/`            | 可选商城接口靶场（独立 Compose；`/test-support` 快照样板）  |
| `qualitest-intellij-plugin/` | IDEA 插件：扫描 Java Controller → 上传（可选；其它栈用 MCP `import_apis`） |


前后端经 REST/JSON 通信；跑流与调试默认打项目环境的 `baseUrl`（Demo 常见 `http://localhost:8801`）。

---

## 2. 核心对象

```mermaid
flowchart TB
  P[测试项目 TestProject] --> E[环境 Environment<br/>baseUrl / 是否允许还原]
  P --> A[接口资产 TestProjectApi]
  P --> F[测试流 TestFlow<br/>graph_json]
  P --> V[素材库 Asset<br/>口令 / 文件 storagePath]
  P --> Auth[项目鉴权 auth_config<br/>authProfiles]
  F --> R[Run 执行记录]
  A --> Auth
```




| 对象       | 一句话                                                         |
| -------- | ----------------------------------------------------------- |
| **测试项目** | 隔离边界：成员、Token、接口、流、素材、鉴权都挂项目                                |
| **环境**   | 被测 `baseUrl`；可选「允许还原被测数据」（开则同环境串行 Run）                      |
| **接口资产** | method/path/schema/测值/鉴权 mode；来自 IDEA 插件、MCP `import_apis`、Web 维护或模板预制 |
| **测试流**  | 画布图（固定 8 种节点）；改图走 AI Staging 或属性面板                          |
| **素材库**  | 可复用测值；登录口令优先 `{{asset.*}}`；文件走 `storagePath` → multipart    |
| **Run**  | 一次执行；trigger 先返 `runId`、按节点落库，详情可边跑边看；含时间线、HTTP、审计步（`run_config` / `snapshot` / `restore`） |


跑流变量：`{{flow.*}}` · `{{env.*}}` · `{{asset.*}}`（详见 [flow-variables-and-values.md](./flow-variables-and-values.md)）。`session` 仅 Script 节点；断言左值见 [test-flow-nodes.md](./test-flow-nodes.md)。

---



## 3. 主链路（人可复现）

```text
接口入库（IDEA 插件 / MCP import_apis / Web）
  → 调试台单接口验证（选环境）
  → 新建/打开测试流
  → AI 助手自然语言造流或修复
  → Staging 逐项 ✓ 确认（禁止空 nodes 当成功）
  → 保存（画布可见节点）
  → Run → 看详情 / 失败则 AI 修复或手改
```


| 原则          | 说明                                        |
| ----------- | ----------------------------------------- |
| **改图画布**    | Web AI → Staging → 保存；规则见 [ai-staging.md](./ai-staging.md)；MCP **默认只能看**，可开写流（搭编排）/ 跑流（点运行），场景见 [mcp.md §0](./mcp.md) |
| **Staging** | ✓ = 确认提案；删除类 ✓ = 唯一确认；空 `nodes` 不算成功 |
| **鉴权**      | 挂**项目**，不挂环境；见 §4                         |
| **写库场景**    | 靶场先加载场景；节点可勾「执行前快照」；还原需被测 `/test-support` |


验收口径与 Agent 铁律见 [全面测试手册.md](./全面测试手册.md) §B.0.1。

---



## 4. 项目鉴权

配置在测试项目设置抽屉「项目鉴权」。造流 Normalizer、Run、调试台共用同一解析器。

### 4.1 模板与 Profile


| 概念                      | 要点                                                                          |
| ----------------------- | --------------------------------------------------------------------------- |
| `test_project_template` | 一行模板 = 一条 Profile；内置 RuoYi Bearer / Session、客户端 Bearer、管理端 Bearer           |
| Apply                   | 勾选后拷入项目 `authProfiles`（**新生成 id**）；按 `templateApis[]` 插入预制接口（已有 method+path **跳过**；作者期 `testProjectApiId` 为雪花字符串，种子时换新主键）；可选种子 `templateEnvs`（填建项占位环境 URL，已定制不覆盖；`envVariables` 同 key 不覆盖；**Profile 同名跳过仍补**）与 `templateParams`（**仅** kind=asset→素材库，**Profile 同名跳过仍补 asset**；内置管理端 `adminAuth`、客户端 `clientAuth`；存量 `kind=env` 仍合并进第一条环境）与 `templateFlows`（同名流跳过；HTTP 作者期雪花 id **remap** 为项目 apiId，历史 `tpl_*` 兼容，legacy 仅 path 仍按 method+path 绑；登录 HTTP body 引用 `{{asset.*}}`）；托管头与 `credentialApi`：**优先**登录流 extracts 派生，**其次** `match_config.credential`，仍无法派生则**拒绝该条**（不再弱默认 `adminAuth` Bearer；不再写 `loginHint`） |
| 新建项目                    | **至少勾一套**；商城双端建议先「管理端 Bearer」再「客户端 Bearer」                                  |
| 免登                      | 认预制 `apis[].authConfig.mode=none`；**不再**维护项目级匿名 path 清单                     |
| 空配置                     | 才暂留 builtin `/login` 等启发式；有 Profile 但 apis 空 → 设置页黄条提示补模板                   |




### 4.2 三层叠加


| 层级                | 要点                                                                            |
| ----------------- | ----------------------------------------------------------------------------- |
| 项目 `authProfiles` | 头模板 + `credentialApi` + 预制 `apis[]`；未命中 `pathPrefix` 用**数组第一条**。**已废弃** `loginHint` |
| 接口 `auth.mode`    | `inherit` → 项目 Profile；`none` 不加头；`override` 用本接口头模板 |
| 节点 headers        | 托管头带 `profileManaged`，Run 按**当前**配置刷新；无该标记的显式头永不被静默改掉                         |


**匹配**：接口指定 `authProfileId` 优先；否则最长 `pathPrefix`；无人命中用数组第一条。**禁止** `pathPrefix="/"`。

**登录抽凭证**：登录流 HTTP `extracts`（通常 `scope=asset`）写素材库；Profile 托管头引用 `{{asset.adminAuth.token}}` 等。须命中 `credentialApi`。造流时空 extracts 按托管头占位符 + schema 补；缺 extract 硬拦（`AUTH_LOGIN_EXTRACT_MISSING`）。上传/OpenAPI 改接口行 schema 与 mode：同 method+path 合并；接口可选 **上传保护**（`sync_protected` / `syncProtected` 为 `0/1`），`1` 则导入整条跳过（**内置模板预制口默认 `1`**），`0` 后可覆盖。

### 4.3 双端与门禁


| 端   | 典型 path                   | extract                       |
| --- | ------------------------- | ----------------------------- |
| 客户端 | `/api/account/auth/login` | `$.data.token` → `asset.clientAuth.token` |
| 管理端 | `/login`                  | `$.token` → `asset.adminAuth.token`       |


- **同端**：开头只登录一次（或挂登录子流），后续靠托管 Bearer 复用 `asset.*`（HTTP 成功后 extract 落盘，跨 Run 可探活）。内置预制登录流为 **探活再登录**：`statusCheck: {mode:whitelist, values:[200,401]}` 探活 + Condition，有效则跳过登录。
- **双端同图**：两套登录、两套 extracts；**禁止**覆盖同一凭证路径（硬拦 `AUTH_LOGIN_FLOWKEY_COLLISION`）。
- **其它硬拦**：缺对应端凭证来源 → `AUTH_TOKEN_MISSING`（**仅运行**硬拦；AI 单单元 `submit_*` 进 **warnings**；整包规范化 / Staging ✓ / **保存**不硬拦）。客户端 Profile 误绑 `adminAuth`（或相反）时文案追加「疑似绑错端」。托管头补全为 soft warning（`AUTH_HEADER_MANAGED`）。
- **AI 改 Profile**：`list_project_auth_profiles`（只读）+ `upsert_auth_profile`（半自动确认 / 全自动直写）；下一轮可带 `runRiskWarnings`。细则见 [ai-staging.md](./ai-staging.md) · [faq.md](./faq.md)。
- 画布顶栏「刷新鉴权头」、HTTP 节点凭证行提示；Run 鉴权失败可用「AI 修复」。
- 凭证可落在 extract→asset/flow、场景 flowSeed、或环境变量等；**口令勿明文塞进图**，可复用账号优先素材库。

验收步骤见手册 **T1.2**（项目模板）、**T1.8**（登录子流）、§F #21/#24。用法（导入导出 / 另存）见 [project-template.md](./project-template.md)。

### 4.4 写操作与快照

HTTP 节点可勾 **执行前快照**（`snapshotBefore`）。失败可暂停，由用户选择还原再重试 / 原地重试 / 跳过 / 中止。被测方需 `/test-support`；环境 `allowDestructiveReset=0` 时 checkpoint/restore 静默跳过。**开启还原时同一环境串行跑。**

---



## 5. AI、Staging 与常见门禁

细则：[ai-staging.md](./ai-staging.md) · [flow-variables-and-values.md](./flow-variables-and-values.md) · [assets.md](./assets.md) · [faq.md](./faq.md)

| 主题      | 口径                                                     |
| ------- | ------------------------------------------------------ |
| 提示词三层   | **平台**造流动词 · **项目芯片**可选（鉴权模板 `template_prompts` 默认可为空；**不**内置靶场业务步骤）· **接口** `designHints` 仅短提示；商城业务正文与测值见 demo [ai-test-flow-prompts.md](../../qualitest-demo/docs/ai-test-flow-prompts.md)，勿再写登录句 |
| 造流      | 提示集 + 真实 apiId；长流**新对话 + 短提示**；本轮无 Staging 摘要 = 未落盘；鉴权可 `list_project_auth_profiles` / `upsert_auth_profile`；请求可带 `runRiskWarnings` |
| Staging | 全部 ✓ 再保存；删除 ✓ 无二次弹窗；MCP 不能 `submit`；半自动素材/鉴权提案须聊天侧确认 |
| 失败路径    | 「预期业务拒绝」+ 字面量；成功路径才用 `{{asset.*}}`                     |
| 素材 / 文件 | 口令进素材库；file → `storagePath` → multipart                |
| MCP     | 默认只能看；可开写流（搭编排）/ 跑流（点运行）/ **导入接口**；前后端场景见 [mcp.md §0](./mcp.md) |


---



## 6. 文档索引


| 文档                                                               | 何时看             |
| ---------------------------------------------------------------- | --------------- |
| **本文** | 概念、鉴权、主链路 |
| [test-flow-nodes.md](./test-flow-nodes.md) | 8 种节点与断言方言 |
| [ai-staging.md](./ai-staging.md) | AI Diff / Staging 确认；含 `toolTrace` 排障口径 |
| [flow-variables-and-values.md](./flow-variables-and-values.md) | 跑流变量与 HTTP 测值 |
| [assets.md](./assets.md) | 素材库与测参文件 |
| [faq.md](./faq.md) | 日常使用疑问（非冒烟专项） |
| [全面测试手册.md](./全面测试手册.md) | T1→T3 验收与 §F/§G |
| [mcp.md](./mcp.md) | MCP Token；默认可只读；可开写流 / 跑流 / 导入接口；§0 人话场景（前后端）；顶栏规程含单功能/整项目提示词 |
| [deploy.md](./deploy.md) | 部署 / Compose |
| [testing-conventions.md](./testing-conventions.md) | 工程测试约定 |
| [ROADMAP.md](../ROADMAP.md) | 排期与待办 |
| [Demo AI 提示集](../../qualitest-demo/docs/ai-test-flow-prompts.md) | 靶场造流提示正文 |


