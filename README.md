<div align="center">

# 质衡 · Qualitest

**企业级自动化测试与质量保障平台**

让接口同步、调试、编排与 AI 辅助设计在同一项目里闭环 —— 少切换工具，少重复录入。

<br/>

[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)
[![Website](https://img.shields.io/badge/Website-qualitest-0ea5e9?style=for-the-badge)](https://qualitest-hq.github.io/qualitest/)
[![Gitee](https://img.shields.io/badge/Gitee-镜像-c71d23?style=for-the-badge)](https://gitee.com/qualitest-hq/qualitest)
[![QQ](https://img.shields.io/badge/QQ%20群-1105468427-12b7f5?style=for-the-badge)](https://qm.qq.com/q/FBa9jDRhm)

<br/>

[官网](https://qualitest-hq.github.io/qualitest/) ·
[English](./README.en.md) ·
[为什么需要](#-为什么需要质衡) ·
[主链路](#-一条链路走完) ·
[谁适合用](#-谁适合用) ·
[功能演示](#-功能演示) ·
[试一把](#-试一把) ·
[QQ 交流群](https://qm.qq.com/q/FBa9jDRhm)

<br/>

<details>
<summary><strong>English Summary</strong> — value prop &amp; quick start · <a href="./README.en.md">full English README</a></summary>

<br/>

**Qualitest** connects API sync (IntelliJ **or** MCP import — not Java-only), in-project debug, canvas orchestration, and AI design (**diff before merge**) — plus MCP **write / run** so Cursor can build a flow and hit Run next to the code you just changed (backend or frontend).

```bash
cd qualitest
# Windows: scripts\quick-start.bat
chmod +x scripts/quick-start.sh && ./scripts/quick-start.sh
```

Open `http://localhost` (default host port **80**; if busy, set `WEB_PORT` in `.env`). Sign in **`admin`** / **`admin123`** after the backend is healthy. Docs: [README.en.md](./README.en.md) · [deploy.en.md](./docs/deploy.en.md).

</details>

<br/>

<p><strong>主链路</strong>：接口入库 → 调试台 → 测试流画布 → AI Diff → MCP</p>

</div>

---

## 😩 为什么需要质衡

测接口这件事，团队里往往碎成一地：

| 以前 | 现在（质衡） |
|:-----|:-------------|
| 接口路径手抄进 Postman，各维护一份集合 | **IDEA 插件**或 **MCP 导入**入库，团队共用同一份清单（不限 Java） |
| 测试 / 预发 / 生产环境切换靠改 URL | 项目内调试台，一键切环境再发请求 |
| 多步用例靠长脚本，改一处查半天 | 画布拖拽编排，断点与断言一眼可见 |
| AI 改流程心里没底，怕悄悄写乱 | 自然语言出建议，**先看 Diff 再合并** |
| 改完代码还要切 Postman / 浏览器自测 | **MCP 写流 / 跑流**：Cursor 里搭流并跑通（前后端都适用） |

一句话：你专注「测什么」，平台负责少折腾。

---

## 🔗 一条链路走完

> **从哪来 → 怎么调 → 怎么串 → 谁帮设计**，连成闭环。

| ① | ② | ③ | ④ | ⑤ |
|:---:|:---:|:---:|:---:|:---:|
| **接口入库** | **调试台** | **测试流编排** | **AI 辅助** | **MCP 接入** |
| 插件 / MCP 导入 | 多环境自测 | 画布拖拽串联 | Diff 再合并 | 写流=搭编排 · 跑流=点运行 |

<p align="center"><sub>闭环 · 少切换 · 少重复录入</sub></p>

| 仓库 | 角色 | GitHub | Gitee（只读镜像） |
|:-----|:-----|:-------|:------------------|
| **本仓** | 质衡平台 + Web（`qualitest-ui/`） | [qualitest](https://github.com/qualitest-hq/qualitest) | [qualitest](https://gitee.com/qualitest-hq/qualitest) |
| qualitest-demo | 可选靶场，零配置体验演示场景 | [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) | [qualitest-demo](https://gitee.com/qualitest-hq/qualitest-demo) |
| qualitest-intellij-plugin | IDEA 插件：Java Controller → 平台（可选；其它栈可用 MCP `import_apis`） | [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin) | [qualitest-intellij-plugin](https://gitee.com/qualitest-hq/qualitest-intellij-plugin) |

> **托管**：GitHub 为主仓（Issue / PR / Release / CI）；Gitee 为国内只读镜像，请勿向镜像提交代码。

---

## 👥 谁适合用

<table>
<tr>
<td width="50%" valign="top">

### 写接口的人

**不用手抄第二遍，顺手就能自测**

**Java**：装 IntelliJ 插件，Controller 一键同步。**其它语言 / 栈**：在 Cursor 等编辑器接 MCP，开启「允许 MCP 导入接口」后用 `import_apis` 入库。团队共享同一份清单；调试台立刻自测，再也不用问「这个路径到底是多少」。

</td>
<td width="50%" valign="top">

### 测接口的人

**调试台就长在项目里**

选中接口即可发请求，一键切换测试 / 预发 / 生产。参数、请求体、响应当场看清；单接口验证在这里完成，多步串联交给测试流。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 编排用例的人

**画布拖拽，比写脚本直观**

用流程图画出先后依赖、分支与多接口串联。登录、OAuth 等常见步骤可收成子流复用；哪里断了、哪里要加断言，一眼看清。

→ 节点说明见 [`docs/test-flow-nodes.md`](./docs/test-flow-nodes.md)

</td>
<td width="50%" valign="top">

### 想省时间的人

**AI 当助手，你来拍板**

用自然语言描述意图——AI 结合**本项目真实接口**给出修改建议。**先预览 Diff，确认后再合并**，不会悄悄改乱你的流程。

→ 靶场提示集：[AI 测试流提示](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md)

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 用 AI 编辑器写代码的人

**改完代码，在 Cursor 里搭流并跑通**

通过标准 MCP 接入。**写流** = 让 Agent 帮你搭 / 改测试流；**跑流** = 让 Agent 帮你点运行，挂了把失败步骤拉回对话。  
**后端**：改完接口少开 Postman。**前端**：先按页面路径把接口链路跑通，再分清是页面锅还是服务端锅。默认只能看；写库 / 跑库要在项目设置里打开。

→ 场景说明见 [`docs/mcp.md`](./docs/mcp.md)

</td>
<td width="50%" valign="top">

### 带团队的人

**协作按项目隔离**

按项目邀请成员、分配角色；为插件与 CI 单独签发 Token，工具接入不共用个人密码，边界清晰。

</td>
</tr>
</table>

---

## 🎬 功能演示

建议以 [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) 为被测；Java 同步可选 [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin)。

### 接口调试台

选中接口 → 切换测试 / 预发 / 生产环境 → 发请求 → 看响应与耗时。

### 测试流画布

从节点面板拖出 HTTP / 断言 / 条件 / 子流 → 连线 → 运行；时间线可展开每步结果。节点说明见 [`docs/test-flow-nodes.md`](./docs/test-flow-nodes.md)。

### AI 辅助设计

在测试流旁打开 AI 面板，用自然语言描述意图（如「登录失败再重试」）→ **先预览 Diff，确认后再合并**。靶场提示集：[AI 测试流提示](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md)（需管理员配置大模型；无 Key 可跳过）。

### MCP 接入

默认只能看；可分别开启「允许 MCP 自动写流」「允许 MCP 自动跑流」「允许 MCP 导入接口」。人话说明见 [`docs/mcp.md`](./docs/mcp.md)。

| 开关 | 人话 | 谁更吃这套 |
|:-----|:-----|:-----------|
| **自动写流** | 让 Cursor 帮你搭 / 改测试流 | 后端改完接口造链路；前端按页面路径先验接口 |
| **自动跑流** | 让 Cursor 帮你点运行，挂了拉回对话再修 | 两边都少切浏览器 |

1. 项目设置复制 `mcp.json` → Cursor 出现 qualitest 工具  
2. （推荐）开写流 + 跑流并**重连 MCP**  
3. `create_flow` / `submit_*` 造流 → `run_test_flow` 跑通；Web 画布经 SSE 同步  
4. 只读时仍可用 `list_flows` / `get_graph_summary` / `get_run_failure` 勘察  

### IDEA 插件（可选）

Tools → Qualitest Helper：**项目级上传** / Controller **全部上传** / **选择上传**。上传后接口进入项目库，即可进调试台。

### 推荐试用路径

1. **主链路**：插件或 MCP 入库 → 调试台发通 → 建项勾鉴权模板 → 画布挂登录子流 → 串业务 HTTP / 断言 → Run；失败可用 AI 修复后再绿。  
2. **MCP 造流**：接 Token → 开写流 / 跑流并重连 → Cursor 造流并 Run → 回 Web 看画布（失败则 `get_run_failure` 再修）。  
3. **写库可回滚**（demo `/test-support`）：加载失败场景 → 环境开「允许还原」→ HTTP 勾「执行前快照」→ 失败暂停后选「还原并重试」；时间线可见 snapshot / restore。

---

## ⚡ 试一把

需 Docker + Compose V2。首次构建较慢；**脚本结束不等于能立刻登录**，请等后端健康 / Flyway 跑完（可 `docker compose logs -f app`）。

**Windows**

```bat
cd qualitest
scripts\quick-start.bat
```

**Linux / macOS**

```bash
cd qualitest
chmod +x scripts/quick-start.sh && ./scripts/quick-start.sh
```

浏览器打开 `http://localhost`（默认映射宿主机 **80**；若被占用，在 `.env` 设 `WEB_PORT=8088` 之类，则打开 `http://localhost:8088`）。

登录账号 **`admin`**，密码 **`admin123`**。勿用于公网。

想完整体验（靶场 + 接口入库 + AI / MCP）：见 [部署说明](./docs/deploy.md) · [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) · [MCP](./docs/mcp.md) · [IDEA 插件](https://github.com/qualitest-hq/qualitest-intellij-plugin)（Java 可选）。

<details>
<summary>更多文档</summary>

- [部署说明](./docs/deploy.md) · [产品概念](./docs/project-summary.md) · [FAQ](./docs/faq.md)
- [测试流节点](./docs/test-flow-nodes.md) · [MCP](./docs/mcp.md) · [项目模板](./docs/project-template.md)
- [路线图](./ROADMAP.md) · [前端 / 桌面](./qualitest-ui/README.md) · [贡献指南](./CONTRIBUTING.md) · [行为准则](./CODE_OF_CONDUCT.md) · [安全策略](./SECURITY.md)
- [English README](./README.en.md)

</details>

---

<div align="center">

**质衡 Qualitest** · 让质量保障更高效 · [Apache-2.0](LICENSE)（可商用） · [贡献指南](CONTRIBUTING.md) · [行为准则](CODE_OF_CONDUCT.md) · [安全策略](SECURITY.md) · [QQ 交流群](https://qm.qq.com/q/FBa9jDRhm)（`1105468427`）

<sub>部署细节以 [`docs/deploy.md`](./docs/deploy.md) 为准。名称与标识「质衡」「Qualitest」归项目维护方；贡献许可见 [CONTRIBUTING](CONTRIBUTING.md)。</sub>

</div>
