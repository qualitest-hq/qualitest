# 项目模板

> **用途**：鉴权 / 预制资产包怎么勾选进项目、怎么用 AI 冷启动、怎么导入导出与另存。  
> 契约与样例：[project-template/](./project-template/) · AI 生成提示词：仓库内 `qualitest-system/.../project-template/AI_PROMPT.md`（管理端「复制提示词」同源）。  
> 概念与 Apply 细节：[project-summary.md §4.1](./project-summary.md) · 建项冒烟：手册 **T1.2**。

项目模板**只收鉴权相关口**（登录 / 探活 / 验证码等），**不要**把全量业务 API 塞进模板；业务接口走 IDEA 插件上传（手册 **T1.3**）。

---

## 1. 是什么

一行模板 ≈ 一套 Auth Profile 种子包，勾选进测试项目后由 Apply 写入：

| 内容 | 结果 |
|------|------|
| 预制接口 | Profile + 项目接口（method+path 已有则跳过）；登录口通常 `auth.mode=none` |
| 预制参数 | 素材库口令等（如 `adminAuth` / `clientAuth`） |
| 预制环境 | 填建项占位 `baseUrl`、合并环境变量（已改过的 URL 不覆盖） |
| 预制测试流 | 登录流等；`extracts` 派生托管头与 `credentialApi` |
| 预制提示词 | 可选；内置鉴权模板默认可为空 |

Apply 托管头派生顺序：

1. **预制登录流** `template_flows` 里登录 HTTP 的 extracts  
2. 否则读 **`match_config.credential`**（精简包常见字段：`asset`、`extract`、`tokenField`、`headerName`、`headerValueTemplate`、`cookieName`；短占位如 `{{token}}` 会展开成 `{{asset.<entry>.<field>}}`）  
3. 仍无法得到完整 `headerName` + `headerValueTemplate` → **拒绝该条**（不再弱默认成两端共用的 `Bearer {{asset.adminAuth.token}}`）

项目设置「添加 Profile」空行不再预填 `adminAuth` Bearer，须人手或 Apply / AI 写明托管头。

**存量坏 Profile**（例如客户端误绑 `adminAuth`）：项目设置手改；或删同名 Profile 后「从项目模板添加」再 Apply；或 AI `upsert_auth_profile`（半自动确认）。**不会**自动改已有项目库。

内置只读样例：`RuoYi Bearer` / `RuoYi Session` / `客户端 Bearer` / `管理端 Bearer`（可克隆后改）。

---

## 2. 日常用法（人）

### 2.1 新建项目勾模板

1. 新建测试项目时**至少勾一套**模板（双端商城建议「管理端 Bearer」+「客户端 Bearer」）。
2. 建项后核对环境 `baseUrl`（联调 demo 一般为 `http://localhost:8801`）。
3. 有预制登录流时，主流挂该子流即可；业务 API 再插件上传。

存量项目：项目设置 →「从项目模板添加」。**同名 Profile 整份跳过**（环境 / 素材在部分路径仍可能补种，见 §4.1）。

### 2.2 管理页 CRUD

菜单「项目模板」：列表、启用、克隆、编辑抽屉。预制接口 / 素材 / 环境可在此维护；**预制测试流仅可查看画布**，不可在此新增或改图。登录流请在测试项目配好后走「另存为项目模板」，或导入含 flows 的完整包。内置行只读，改内容请先克隆。

---

## 3. 导入 / 导出

入口：项目模板列表 → **导入** / 行内 **导出**。

| 形态 | 用途 |
|------|------|
| **精简包（slim）** | 给人 / AI 写的短 JSON：apis、assets、env、credential 等；**不含** flows（误带非空 `flows` 会被忽略并 warning）。导入后展开落库，**不**在模板管理造流 |
| **完整包（full）** | 与库内列同形，**可含 flows**；导出默认；另存结果也是完整包。已含 flows 时可直接勾选 Apply |

导入步骤：

1. 打开「导入」→ 可选 **复制提示词**，贴到任意 AI，按目标系统生成精简 JSON（或多端多份，用 `---` 分隔）。提示词要求 AI **同时写出 `assets` 演示账号/密码**（从目标仓库 README 等读取），导入后即为预制参数，避免再手工补口令。
2. 粘贴或上传 JSON → 预览校验 → 确认导入（可按同名覆盖策略）。
3. 精简包若需要登录流：到跑通项目勾登录流 **另存为项目模板**（或导入已含 flows 的完整包）；模板管理侧只读查看。
4. 再到测试项目勾选 Apply。

契约：[`project-template.schema.json`](./project-template/project-template.schema.json)；样例见同目录 `examples/`。

**不要**用项目模板替代接口库全量导入；OpenAPI 直传业务接口属另一条能力。

---

## 4. 从项目另存完整模板

入口：测试项目 → **项目设置** → **另存为项目模板**。

| 项 | 规则 |
|------|------|
| 主轴 | 勾选的**测试流** |
| 必带 | 流图关联的接口与素材（不可取消） |
| 可追加 | 同 Profile 其余鉴权口、其它素材、首环境、提示词 |
| 注意 | 口令等素材**明文**写入模板；同名可覆盖 |

验收思路：跑通登录的项目 → 勾登录流另存 → 新空项目勾选 Apply → 登录流与托管头可用。

---

## 5. 和其它能力的边界

| 要做的事 | 走哪里 |
|----------|--------|
| 冷启动鉴权口 / 素材 | 复制提示词 → 精简 JSON → 导入 |
| 带上预制登录流 | 跑通项目后另存完整模板（或导入完整包） |
| 改登录流 | 在测试项目画布改流后再另存（模板管理不可改流） |
| 业务 API 同步 | IDEA 插件（或项目接口库），不是模板 |
| 只读勘察项目 | [MCP](./mcp.md)（**不写**模板） |

素材引用与口令落盘见 [assets.md](./assets.md)。
