# 平台能力：ops 执行通道

> **状态**：设计草案（Exploring · 随 [platform-plugins.md](./platform-plugins.md) §2.3.2）  
> **归属**：**质衡核心**负责 `channel.kind` 分发与 Invoker 到达方式。  
> **主路径**：**业务 CLI + `local-exec`（同机）**。  
> **分机 HTTP**：启用官方插件 [被测执行代理](https://github.com/qualitest-hq/qualitest-plugin-sut-exec-agent/blob/main/docs/design.md)（`qualitest.sut-exec-agent`），`channel.kind=exec-agent`。  
> **分机 SSH**：`channel.kind=ssh`（不强制执行代理插件）。

## 1. 怎么用（分层）

```text
同机新手：
  装业务 CLI（sdsp / 拨钟…）→ 启用业务插件 → local-exec → 跑流

分机（HTTP 代跑）：
  被测机：装「执行代理」插件 + 各业务 CLI
  质衡：启用 qualitest.sut-exec-agent，channel=exec-agent，填 baseUrl
  跑流 → Invoker → 执行代理 → 业务 CLI

分机（已有 SSH）：
  被测机：只装业务 CLI
  质衡：channel=ssh → 远程直接跑业务 CLI
```

| | 核心通道 | 执行代理插件 |
| --- | --- | --- |
| 是什么 | Invoker 怎么连到被测 | 被测旁的代跑进程 + 启用/徽章 |
| 同机 | `local-exec` | **不必启用** |
| 算插件吗 | 否 | **是**（一插件一仓） |

旧称「sidecar / 边车」易误解；产品名统一为 **执行代理**。归档仓 `qualitest-ops-sidecar` 由 `qualitest-plugin-sut-exec-agent` 接替。

## 2. `channel.kind`

| kind | 说明 |
| --- | --- |
| **`local-exec`** | P0 默认；本机起业务 `cli.bin` |
| **`ssh`** | 远程 argv 跑业务 CLI |
| **`exec-agent`** | 经已启用的 `qualitest.sut-exec-agent` 的 HTTP `ping`/`exec` |

选用 `exec-agent` 时：**必须**环境已启用执行代理插件且 baseUrl 可达，否则 `AGENT_UNREACHABLE` / 拒启用。

## 3. 环境配置（核心）

| 字段 | 说明 |
| --- | --- |
| `channel.kind` | 默认 `local-exec` |
| `channel.ssh.*` | kind=ssh |
| `channel.execAgent.baseUrl` | kind=exec-agent |
| `channel.execAgent.tokenRef` | 可选密钥槽；禁止进流程图 |

兼容旧草稿字段名 `channel.sidecar.*`：实现可读作 `execAgent` 别名一期，文档正文只用 `execAgent`。

## 4. 协议（exec-agent）

### ping

```jsonc
{ "ok": true, "version": "0.1.0", "hostname": "sut-demo-01" }
```

### exec

```jsonc
// 请求
{
  "bin": "sdsp",
  "args": ["save.create", "--config", "CONFIG_PATH"],
  "configText": "{ ... }",
  "timeoutMs": 120000
}
// 响应
{
  "ok": true,
  "exitCode": 0,
  "stdout": "{ \"ok\": true, ... }",
  "stderrTail": "...",
  "durationMs": 842
}
```

规则：`bin` **白名单**；禁止 `bash -c` 拼串；超时杀进程组；P0 内网 + token。

## 5. 验收

- [ ] 同机：不启用执行代理，仅业务 CLI + `local-exec` 可跑通  
- [ ] 分机：启用执行代理 + `exec-agent` 可转发一次业务 `ping`/`save.create`  
- [ ] 非白名单 `bin` 拒  
- [ ] 启用列表可出现「执行代理」插件行；**不是**业务 dump/拨钟本身  
