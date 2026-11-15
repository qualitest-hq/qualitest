# 平台能力：ops 执行通道

> **状态**：设计草案（Exploring · 随 [platform-plugins.md](./platform-plugins.md) §2.3.2）  
> **归属**：**质衡核心**（Invoker 如何调到业务 CLI）——**不是**插件，无启用行、无画布能力。  
> **主路径（已定）**：**业务 CLI + `local-exec`（同机）**；分机优先 **SSH 远程跑同一条 CLI**。  
> **sidecar（HTTP 代跑服务）**：**后置**，非新手路径；不单独建插件仓、不挡 P0。

## 1. 用户怎么用（先看这个）

官方业务插件（存档点 / 拨钟等）**本身就是插件用法的新手引导**：

```text
① 被测机（或本机一体）安装该插件的 CLI（如 sdsp）
② 质衡：项目挂载 / 环境启用插件 / 开总闸（按专文）
③ 环境通道：默认 local-exec（同机）
④ 跑流 → 平台 Invoker 直接起 CLI；MCP 可协助装 CLI、配质衡，不代替跑流调用
```

**不必**先理解边车。没有业务 CLI，插件无法干活；有 CLI 且同机，**不需要**任何代跑服务。

| | 业务插件 CLI | 执行通道 |
| --- | --- | --- |
| 是什么 | dump / 拨钟 / 混沌… | 核心：怎么把 CLI 跑起来 |
| 用户要装吗 | **要**（被测旁） | 同机：不用额外装；分机 SSH：用已有 SSH |
| 算插件吗 | 是 | **否** |

## 2. 通道 kind

| `channel.kind` | 阶段 | 质衡行为 |
| --- | --- | --- |
| **`local-exec`** | **P0 默认** | 本机 `Process` 跑说明书里的 `cli.bin` |
| **`ssh`** | 分机常用 | 远程 argv 执行同一 CLI（密钥走环境密钥槽） |
| `sidecar` | **后置** | 被测旁 HTTP 小服务代 `exec`；仅当「不要 SSH、只要内网 HTTP」时考虑 |

启用 `ops-agent` 业务插件时须有合法通道；默认未配可视为 `local-exec`（实现定）。`ssh` 探活失败 → `AGENT_UNREACHABLE`。

## 3. 环境配置（核心）

| 字段 | 说明 |
| --- | --- |
| `channel.kind` | 默认 `local-exec`；分机改 `ssh` |
| `channel.ssh.*` | kind=ssh 时：host/user/密钥引用（细节实现定）；**禁止**进流程图 |
| `channel.sidecar.*` | **后置**；未实现前文档仅占位 |

**禁止**把通道做成可启用业务插件。

## 4. 与插件仓的关系

- 业务插件：**一插件一私有仓**（CLI + README + install）  
- 本能力：写在 **`qualitest` 主仓**（Java Invoker）；**不**设 `qualitest-ops-sidecar` 独立仓为交付前提  
- 官方样本插件兼作 **「如何用插件」** 的引导：装 CLI → 启用 → 跑流见徽章/挂点/节点

## 5. sidecar 后置备忘（非主路径）

若将来做：被测旁短驻进程，白名单 `bin`，`ping`/`exec`，内网 + token。  
协议细节实现时再补；**不得**写进新手 README 主流程。

## 6. 验收（P0）

- [ ] 同机只装业务 CLI + `local-exec` 可完成存档点或拨钟一次 invoke  
- [ ] 环境插件列表**无**「执行边车」业务行  
- [ ] 官方插件仓 README 主路径不提必须装 sidecar  
