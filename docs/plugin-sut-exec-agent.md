# 插件设计：被测执行代理（`qualitest.sut-exec-agent`）

> **状态**：设计草案（Exploring · 随 [platform-plugins.md](./platform-plugins.md)）  
> **显示名**：被测执行代理 · **短称/徽章**：执行代理 · **id**：`qualitest.sut-exec-agent`  
> **原称**：曾用「ops sidecar / 边车」——易与业务 CLI 混淆；**现名强调：在被测机上代跑其它插件的 CLI**。  
> **实现仓（私有）**：[qualitest-hq/qualitest-plugin-sut-exec-agent](https://github.com/qualitest-hq/qualitest-plugin-sut-exec-agent)  
> **语言（建议）**：Rust 或 Go（单二进制）；实现后置。  
> **定位**：质衡与被测**分机**时，在被测旁提供 `ping` / `exec`，代跑存档点、拨钟、网混沌等业务 CLI。

## 1. 要解决什么 / 怎么用

```text
同机：不必启用本插件；通道 local-exec，质衡直接起 sdsp 等即可。
分机：被测机装本插件（执行代理进程）+ 各业务 CLI
      环境启用本插件，channel.kind=exec-agent，填 baseUrl
      跑流 → Invoker → 本代理 HTTP → exec 业务 CLI
```

| | 本插件（执行代理） | 存档点 / 拨钟 / 网混沌 |
| --- | --- | --- |
| 干什么 | **代跑**别人的命令 | dump / 改钟 / 搞网 |
| 日常形态 | 短驻或随跑随起的**小服务**（仍用 install 的二进制，可有 `serve` 子命令） | 短命业务 CLI |
| 同机要不要 | **不要** | 要（有业务就要装） |
| 算官方插件吗 | **是**（一插件一仓；可启用、可引导「分机怎么配」） | 是 |

**新手引导分工**：存档点/拨钟教「业务插件 + 同机」；本包教「分机还要一个执行代理」。

旧仓 `qualitest-ops-sidecar` 已归档，**由本仓接替**，不是简单改名同一交付物叙事——产品名与插件模型已对齐。

## 2. 边界

| 做 | 不做 |
| --- | --- |
| `agent.ping` / 代跑白名单内业务 `bin` | 自己实现 `save.*` / `clock.*` / `net.*` |
| 写临时 `--config` 再 exec；超时杀进程 | 任意 shell（`bash -c`） |
| 环境启用 + `channel.kind=exec-agent` + `baseUrl` | 同机强迫启用 |
| 徽章「执行代理」 | 冒充开放业务能力名录条目 |

## 3. 身份与形态

| 项 | 值 |
| --- | --- |
| `id` | `qualitest.sut-exec-agent` |
| `displayName` | 被测执行代理 |
| `runtime.kind` | `ops-agent` |
| `agent.bundle` | `embedded` |
| 二进制 | `qt-sut-exec-agent`（例：`serve` / `ping`） |
| `isolation` | `sut-exec-agent`（mutex）；`ui-slot:flow.badge.exec-agent` |

同环境只启用一个执行代理包。

## 4. 私有能力（示意）

| name | effect | gate | 含义 |
| --- | --- | --- | --- |
| `agent.ping` | read | none | 代理可达、版本、hostname |
| `agent.exec` | write | none* | 代跑白名单 bin；\*破坏性仍由**被代跑的业务能力** gate 约束，本能力不降闸 |

平台 Invoke 业务插件时：若 `channel.kind=exec-agent`，核心经本代理 `exec` 转发，**不必**业务方再调 `agent.exec` 节点（转发是通道行为）。  
`agent.ping` 供启用探活与徽章。

## 5. 环境 config

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `listen` 相关 | 被测机本地 | 代理本机监听；也可只在被测配置文件 |
| （质衡侧通道）`channel.execAgent.baseUrl` | kind=exec-agent 时 | 如 `http://10.0.0.12:7920` |
| `channel.execAgent.tokenRef` | 否 | 环境密钥槽 |

启用本插件且选用分机 HTTP 通道时，平台将 `channel.kind` 与 baseUrl 一并校验；缺 URL → 拒启用或拒 invoke。

## 6. 最小协议（与通道一致）

`GET/POST …/ping`、`POST …/exec`（`bin` 白名单、`args`、`configText`、`timeoutMs`）——详见 [platform-ops-exec-channel.md](./platform-ops-exec-channel.md) §协议。  
语义不变；运输层可换。

## 7. 说明书骨架

```jsonc
{
  "id": "qualitest.sut-exec-agent",
  "version": "1.0.0",
  "displayName": "被测执行代理",
  "remarks": "分机时在被测旁代跑其它官方 CLI；同机不必启用。短称：执行代理。",
  "capabilities": [
    { "name": "agent.ping", "effect": "read", "gate": "none" },
    { "name": "agent.exec", "effect": "write", "gate": "none" }
  ],
  "isolation": ["sut-exec-agent", "ui-slot:flow.badge.exec-agent"],
  "ui": {
    "env": { "badge": { "text": "执行代理", "tone": "info" } }
  },
  "runtime": {
    "kind": "ops-agent",
    "agent": {
      "bundle": "embedded",
      "cli": { "bin": "qt-sut-exec-agent", "pingArgs": ["ping"] }
    }
  }
}
```

## 8. 非目标

- 不做：同机默认依赖本插件  
- 不做：无白名单的远程任意命令产品  
- 不做：与 `qualitest-ops-sidecar` 双轨长期并存（旧仓归档即可）  
