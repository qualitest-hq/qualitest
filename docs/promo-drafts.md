# 推广投稿草稿

提交前请自行核对：HelloGitHub 官网是否已收录本项目；掘金/社区勿与近期 V2EX 帖大段重复灌水。

---

## 1. HelloGitHub 提交字段

提交入口：https://github.com/521xueweihan/HelloGitHub/issues/new/choose  
或官网提交页（若开放）。

| 字段 | 建议填写 |
|------|----------|
| 项目地址 | `https://github.com/qualitest-hq/qualitest` |
| 类别 | `Java` |
| 项目标题（≤50 字） | `Cursor 里写测跑测的开源接口自动化平台` |
| 项目描述（32–256 字） | 见下方 |
| 亮点 | 见下方 |
| 截图/演示 | `https://github.com/qualitest-hq/qualitest/blob/main/docs/images/canvas-glance.gif` · `debug-200.gif` · 官网 https://qualitest-hq.github.io/qualitest/ |

### 项目描述（约 180 字，可直接贴）

质衡（Qualitest）是开源接口自动化测试平台：Compose 五分钟起栈，项目内调试台 + 测试流画布编排。特色是标准 MCP——在 Cursor 里导入接口、写测试流、跑流，失败步骤拉回对话继续改；也可用 IDEA 插件同步 Java 接口。适合前后端少切 Postman、把多步场景留在同一项目里的团队。Apache-2.0。

### 亮点

- **Cursor MCP 写流 / 跑流**：改完代码不必先切 Postman；默认可只读勘察，项目设置可开写库与跑库。
- **画布一等公民**：请求、断言、分支、子流等节点编排，Run 时间线可回看每步。
- **AI Diff 再合并**：自然语言改流先预览 Diff，确认后才进画布。
- **入库双通道**：IDEA 插件（Java）或 MCP `import_apis`（不限栈）。
- **一键体验**：`scripts/quick-start.sh` / `.bat` + 官方 GHCR 镜像；国内有 Gitee 只读镜像。

---

## 2. 掘金文章草稿

**标题候选（选一）**

1. 改完接口还要切 Postman？我用 Cursor MCP 在对话里写测试流并跑通  
2. 开源接口测试平台质衡：画布编排 + Cursor 写流跑流  
3. 从写接口到跑通测试流，我把 Postman 这一步留在了 Cursor 里  

**标签建议**：开源、接口测试、自动化测试、Cursor、MCP、Java、Vue  

**正文（可再润色）**

---

大家好，分享一个自己在用的开源项目：**质衡 Qualitest**。

### 痛点

写完接口（或前端联调前想先验链路）时，常见路径是：

1. 手抄路径进 Postman / Apifox  
2. 多步场景再写一套脚本或工作流  
3. Cursor 里改完代码，还要切出去点「发送」

我想把「造场景 + 跑通」留在写代码的同一个对话里，同时也给测试同学留一个正经的画布，而不是只靠聊天记录。

### 质衡是什么

一句话：

> **在 Cursor 里：导入接口 → 写测试流 → 跑流 → 挂了继续改**；  
> 也可以用 Web **调试台 + 测试流画布**完整自测。

技术栈大致是 Java 17 / Spring Boot + Vue3，Apache-2.0，Docker Compose 能起。

主仓：https://github.com/qualitest-hq/qualitest  
官网：https://qualitest-hq.github.io/qualitest/  
Demo 靶场：https://github.com/qualitest-hq/qualitest-demo  
IDEA 插件：https://plugins.jetbrains.com/plugin/34434-qualitest-helper  

### 两条用法

**给人用的是画布**

- 接口进项目库，调试台直接发，环境一键切  
- 多步场景拖节点：请求、断言、分支、赋值、子流  
- 登录鉴权可收成子流；AI 改流会先出 Diff，确认再合并  

**给我自己用得最多的是 MCP**

在 Cursor 接上平台的 `mcp.json` 后（项目设置可复制）：

- 只读：列测试流、看拓扑、拉上次 Run 失败点  
- 开权限后：`create_flow` / `submit_*` 写流，`run_test_flow` 跑流  
- Web 画布经 SSE 同步，挂了可以在对话里继续修  

人话文档：[docs/mcp.md](https://github.com/qualitest-hq/qualitest/blob/main/docs/mcp.md)

### 5 分钟起栈

```bash
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
# Windows: scripts\quick-start.bat
chmod +x scripts/quick-start.sh && ./scripts/quick-start.sh
```

打开 http://localhost ，默认 `admin` / `admin123`（等后端健康后再登；生产务必改密）。

### 和「又一个接口平台」差在哪

概念上接近「多步接口场景编排」，质衡把这块做成项目内一等公民（画布 + Run），并加上 **Cursor MCP 写流跑流** 这条主叙事。不是要取代你们已经熟的 Apifox/Postman，而是给「改完代码立刻自测链路」多一条少切工具的路。

### 求个 Star / 试用反馈

若你也在用 Cursor 写后端或先验前端依赖的接口链路，欢迎试用、提 Issue，或加 QQ 群 `1105468427` 聊聊。Star 是对开源最大的鼓励：https://github.com/qualitest-hq/qualitest

---

## 3. 你需要手动完成的动作

1. 打开 HelloGitHub Issue 模板，粘贴上一节字段并附 GIF。  
2. 掘金发布上文（可配上 canvas / debug GIF）。  
3. （可选）IDEA 插件市场更新「简介」首句为同一句 MCP 主叙事。  
4. （可选）把项目提交到 cursor.directory 等 MCP 目录（按各站表单填写，卖点同 README 首屏）。
