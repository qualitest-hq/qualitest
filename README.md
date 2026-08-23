<div align="center">

# 质衡 · Qualitest

**企业级自动化测试与质量保障平台**

让接口同步、调试、编排与 AI 辅助设计在同一项目里闭环 —— 少切换工具，少重复录入。

<br/>

[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)
[![Website](https://img.shields.io/badge/Website-qualitest-0ea5e9?style=for-the-badge)](https://qualitest-hq.github.io/qualitest/)
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

**Qualitest** connects API sync, in-project debug, canvas orchestration, and AI design (**diff before merge**) — plus MCP so AI editors can **read** the same project.

```bash
cd qualitest
# Windows: scripts\quick-start.bat
chmod +x scripts/quick-start.sh && ./scripts/quick-start.sh
```

Open `http://localhost` (default host port **80**; if busy, set `WEB_PORT` in `.env`). Sign in **`admin`** / **`admin123`** after the backend is healthy. Docs: [README.en.md](./README.en.md) · [deploy.en.md](./docs/deploy.en.md).

</details>

<br/>

<!--
  顶部概览 GIF（建议录制规格）：
  - 内容：以 qualitest-demo 为例的一条主链路——IDEA 同步接口 → 调试台发请求 → 画布编排 → AI Diff 合并
  - 宽度 ≤ 900px、帧率 8~10fps、时长 ≤ 10s、体积尽量 < 1MB
  - 录制工具：ScreenToGif / LICEcap；超大用 gifsicle -O3 --colors 128 压缩
  - 文件占位：docs/images/overview.gif（暂未提供时此图显示为占位说明）
-->
<img src="docs/images/overview.gif" alt="质衡 Qualitest 功能概览：接口同步 → 调试 → 编排 → AI 辅助" width="860"/>

</div>

---

## 😩 为什么需要质衡

测接口这件事，团队里往往碎成一地：

| 以前 | 现在（质衡） |
|:-----|:-------------|
| 接口路径手抄进 Postman，各维护一份集合 | IDEA 插件一键同步，团队共用同一份清单 |
| 测试 / 预发 / 生产环境切换靠改 URL | 项目内调试台，一键切环境再发请求 |
| 多步用例靠长脚本，改一处查半天 | 画布拖拽编排，断点与断言一眼可见 |
| AI 改流程心里没底，怕悄悄写乱 | 自然语言出建议，**先看 Diff 再合并** |

一句话：你专注「测什么」，平台负责少折腾。

---

## 🔗 一条链路走完

> **从哪来 → 怎么调 → 怎么串 → 谁帮设计**，连成闭环。

| ① | ② | ③ | ④ | ⑤ |
|:---:|:---:|:---:|:---:|:---:|
| **IDEA 同步** | **调试台** | **测试流编排** | **AI 辅助** | **MCP 接入** |
| 接口从代码来 | 多环境自测 | 画布拖拽串联 | Diff 再合并 | IDE 可读项目 |

<p align="center"><sub>闭环 · 少切换 · 少重复录入</sub></p>

| 仓库 | 角色 |
|:-----|:-----|
| **本仓** | 质衡平台 + Web（`qualitest-ui/`） |
| [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) | 可选靶场，零配置体验演示场景 |
| [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin) | IDEA 插件：Controller → 平台 |

---

## 👥 谁适合用

<table>
<tr>
<td width="50%" valign="top">

### 写接口的人

**不用手抄第二遍，顺手就能自测**

装上 IntelliJ 插件，工程里的接口定义一键同步到平台。团队共享同一份清单；自己也能在调试台立刻验一遍，再也不用问「这个路径到底是多少」。

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

**IDE 也能读懂你的测试项目**

通过标准 MCP 只读接入（Cursor 等均可）。问「这条流有哪些节点？」「上次跑挂在哪？」——答案来自平台**真数据**。改画布仍在 Web 端 Diff 后合并。

→ 配置见 [`docs/mcp.md`](./docs/mcp.md)

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

### 接口调试台 · 选中即调、一键切环境

<!--
  录制规格：在 qualitest-demo 对应主平台项目中选中一个接口 → 切换 测试/预发 环境 → 发请求 → 展开响应
  宽度 ≤ 900px · 8~10fps · ≤ 8s · 目标 < 600KB
  文件占位：docs/images/demo-api-console.gif
-->
<img src="docs/images/demo-api-console.gif" alt="接口调试台：选中接口、切换环境、发起请求并查看响应" width="820"/>

### 测试流编排 · 画布拖拽搭建用例

<!--
  录制规格：基于 qualitest-demo 项目接口，从节点面板拖出 HTTP / 断言节点 → 连线 → 运行测试流看结果
  宽度 ≤ 900px · 8~10fps · ≤ 8s · 目标 < 800KB
  文件占位：docs/images/demo-flow-canvas.gif
-->
<img src="docs/images/demo-flow-canvas.gif" alt="测试流编排：在画布上拖拽节点、连线并运行" width="820"/>

### AI 辅助设计 · 自然语言描述、先看 Diff 再合并

<!--
  录制规格：在 qualitest-demo 项目测试流旁打开 AI 面板 → 输入「测登录失败再重试」→ 展示 Diff 预览 → 点击合并
  宽度 ≤ 900px · 8~10fps · ≤ 8s · 目标 < 800KB
  文件占位：docs/images/demo-ai-diff.gif
-->
<img src="docs/images/demo-ai-diff.gif" alt="AI 辅助设计：自然语言生成修改建议，Diff 预览后合并" width="820"/>

### MCP 接入 · IDE / AI 编辑器直接读懂测试项目

<!--
  录制规格：在 Cursor 中接入 MCP，选定 qualitest-demo 项目测试流 → list_flows → 带 testFlowId 提问 → 返回平台真数据
  宽度 ≤ 900px · 8~10fps · ≤ 10s · 目标 < 1MB
  文件占位：docs/images/demo-mcp-cursor.gif
-->
<img src="docs/images/demo-mcp-cursor.gif" alt="MCP 接入 Cursor：只读勘察测试流、节点拓扑与 Run 失败现场" width="820"/>

### 周边：IDEA 插件（独立仓库）

演示来自 [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin)；示例工程建议用 [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo)。

<!--
  录制规格：项目级上传 / Controller 全部上传 / Controller 选择上传
  文件占位：docs/images/demo-idea-sync-*.gif
-->
<img src="docs/images/demo-idea-sync-project.gif" alt="IDEA 插件：项目级上传，整工程接口同步到平台" width="820"/>

<img src="docs/images/demo-idea-sync-controller-all.gif" alt="IDEA 插件：选中 Controller 后全部上传" width="820"/>

<img src="docs/images/demo-idea-sync-controller-pick.gif" alt="IDEA 插件：选中 Controller 后勾选部分接口上传" width="820"/>

---

### 复杂演示 · 分片讲述

> 下面两条故事 **10 秒讲不完**，拆成短片连着看。统一：宽度 ≤ 900px · 8~10fps · 单片 ≤ 10s · 放 `docs/images/`。  
> 建议以 [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) 为被测；插件片依赖 [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin)。

#### 完整主链路 · 从同步到再跑绿

同步 → 调试 → 挂登录子流 → 编排业务 → AI 补断言 → Run → 失败则 AI 修 → 再绿。

##### A · 插件同步 + 调试台验通

<!--
  录制：IDEA 打开 demo → 项目级上传 → 切回质衡接口列表出现 → 调试台选环境发通一个接口
  文件：docs/images/demo-story-main-a.gif · 目标 < 800KB
-->
<img src="docs/images/demo-story-main-a.gif" alt="主链路 A：IDEA 同步接口后在调试台发通" width="820"/>

##### B · 项目模板 + 登录子流挂上主流

<!--
  录制：项目鉴权已有 Bearer（或建项勾模板）→ 画布拖 Subflow → 从平台模板创建 Bearer 登录 → 主流 Start→Subflow→业务 HTTP
  文件：docs/images/demo-story-main-b.gif · 目标 < 800KB
-->
<img src="docs/images/demo-story-main-b.gif" alt="主链路 B：登录子流模板挂入主流" width="820"/>

##### C · 画布串业务 + 首次 Run

<!--
  录制：补断言/条件 → 点运行 → 时间线展开（可含 childSteps）→ 故意留一处会挂的断言或场景，为 D 铺垫
  文件：docs/images/demo-story-main-c.gif · 目标 < 800KB
-->
<img src="docs/images/demo-story-main-c.gif" alt="主链路 C：编排业务并首次运行" width="820"/>

##### D · 失败 → AI 修复 → 再跑绿

<!--
  录制：打开失败 Run → 失败分类跳到断言步 → AI 修复 → Staging ✓ → 保存 → 再 Run 变绿
  文件：docs/images/demo-story-main-d.gif · 目标 < 1MB
-->
<img src="docs/images/demo-story-main-d.gif" alt="主链路 D：Run 失败后 AI 修复再跑绿" width="820"/>

#### 写库可回滚 · 快照、暂停与还原

靶场脏场景 → 节点勾执行前快照 → Run 写库失败暂停 → 时间线 snapshot → 还原并重试 → restore 审计。

##### A · 加载失败场景 + 勾快照开跑

<!--
  录制：demo-ui 失败场景（如 F01）点「加载此场景」→ 质衡环境开「允许还原」→ 写库 HTTP 勾「执行前快照」→ 点运行
  依赖：靶场 /test-support；文件：docs/images/demo-story-restore-a.gif
-->
<img src="docs/images/demo-story-restore-a.gif" alt="回滚故事 A：加载靶场场景并勾快照开跑" width="820"/>

##### B · 暂停决策面板

<!--
  录制：Run 进入 paused → 决策面板可见「还原并重试 / 原地重试 / 跳过 / 中止」→ 点「还原并重试」
  文件：docs/images/demo-story-restore-b.gif · 目标 < 600KB
-->
<img src="docs/images/demo-story-restore-b.gif" alt="回滚故事 B：失败暂停后的决策面板" width="820"/>

##### C · 时间线审计：snapshot → restore → 重试

<!--
  录制：Run 详情时间线依次出现 snapshot、失败步、restore、重试成功；可短停各步标签
  文件：docs/images/demo-story-restore-c.gif · 目标 < 800KB
-->
<img src="docs/images/demo-story-restore-c.gif" alt="回滚故事 C：时间线 snapshot / restore / 重试审计" width="820"/>

> GIF 暂未录制时可能显示为占位；录制后按上述文件名放入 `docs/images/` 即可。

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

想完整体验（靶场 + IDEA 同步 + AI / MCP）：见 [部署说明](./docs/deploy.md) · [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) · [IDEA 插件](https://github.com/qualitest-hq/qualitest-intellij-plugin)。

<details>
<summary>更多文档</summary>

- [部署说明](./docs/deploy.md) · [产品概念](./docs/project-summary.md) · [FAQ](./docs/faq.md)
- [测试流节点](./docs/test-flow-nodes.md) · [MCP](./docs/mcp.md)
- [前端 / 桌面](./qualitest-ui/README.md) · [贡献指南](./CONTRIBUTING.md) · [安全策略](./SECURITY.md)
- [English README](./README.en.md)

</details>

---

<div align="center">

**质衡 Qualitest** · 让质量保障更高效 · [Apache-2.0](LICENSE) · [贡献指南](CONTRIBUTING.md) · [安全策略](SECURITY.md) · [QQ 交流群](https://qm.qq.com/q/FBa9jDRhm)（`1105468427`）

<sub>部署细节以 [`docs/deploy.md`](./docs/deploy.md) 为准</sub>

</div>
