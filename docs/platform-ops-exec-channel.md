# 执行通道

> **一句话**：质衡怎么「够到」被测机上的业务 CLI（`sdap` / `sdss` 等）。  
> **状态**：Exploring；**已落地** `local-exec`、`exec-agent`；`ssh` 后置未实现。  
> **总览**：[platform-plugins.md](./platform-plugins.md) · **执行代理专文**：[design.md](https://github.com/qualitest-hq/qualitest-plugin-sut-exec-agent/blob/main/docs/design.md)

---

## 1. 先分清两件事

| | 是什么 | 例子 |
| --- | --- | --- |
| **载荷** | 被测机上真正干活的命令 | `sdap archive.create`（存档点自己的 CLI） |
| **通道** | 质衡怎么把这条命令跑起来 | 本机直接起进程，或 HTTP 让远处代理代跑 |

通道是**核心**能力，不是业务插件。  
「执行代理」是**官方插件**：分机时代跑别人的 CLI，并承接 Web 推送装包/写 bind。

```text
同机（最常见）：
  装业务 CLI → 环境启用业务插件 → local-exec → 跑流

分机（HTTP）：
  被测机：装执行代理进程 + 业务 CLI
  质衡：启用 qualitest.sut-exec-agent，填 baseUrl
  跑流 → Invoker → 执行代理 → 业务 CLI

分机（SSH · 后置）：
  被测机只装业务 CLI；质衡用 ssh 远程起命令（代码尚未实现）
```

| | 核心通道 | 执行代理插件 |
| --- | --- | --- |
| 干什么 | Invoker 怎么连到被测 | 被测旁的代跑小服务 + 徽章 |
| 同机要不要 | 用 `local-exec` | **不必启用** |
| 算插件吗 | 否 | **是** |

---

## 2. 三种 `channel.kind`

| kind | 人话 | 实现 |
| --- | --- | --- |
| **`local-exec`** | 质衡本机直接起 `cli.bin`（默认） | **已落地** |
| **`exec-agent`** | 把命令交给已启用的执行代理（HTTP `ping`/`exec`） | **已落地** |
| **`ssh`** | 远程 SSH 跑同一条 CLI | **后置**；现配置不认时按 `local-exec` |

用 `exec-agent` 时：环境须已启用执行代理，且 `baseUrl` 能通，否则拒启用 / `AGENT_UNREACHABLE`。

### 环境里相关字段

| 字段 | 说明 |
| --- | --- |
| `channel.kind` | 默认 `local-exec` |
| `channel.execAgent.baseUrl` | `exec-agent` 时必填 |
| `channel.execAgent.tokenRef` | 可选密钥槽；**禁止**写进流程图 |
| `channel.ssh.*` | 仅契约占名（未实现） |

实践上：启用了执行代理且填了 `baseUrl` → 走 `exec-agent`；否则 → `local-exec`。

---

## 3. 执行代理协议（HTTP）

前提：被测旁**已经在跑**执行代理（Compose 边车或人工首装）。  
质衡 Web **自己写不进**被测盘；有代理后可经 Host `sut-push` 让代理写盘。

### `ping`

```jsonc
{ "ok": true, "version": "0.1.0", "hostname": "sut-demo-01" }
```

### `exec`（代跑业务 CLI）

```jsonc
// 请求
{
  "bin": "sdap",
  "args": ["archive.create", "--config", "CONFIG_PATH"],
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

规则：`bin` 白名单；禁止 `bash -c` 拼串；超时杀进程组；内网 + token。

### `install`（推送装业务 CLI）

```jsonc
// POST …/install
{
  "productId": "sdap",
  "version": "0.1.0",
  "artifactUrl": "http://qualitest:8800/project/pluginHost/artifact/<token>",
  "sha256": "<hex>"
}
// 响应
{ "ok": true, "productId": "sdap", "binPath": "/usr/local/lib/sdap/bin/sdap" }
```

代理拉制品、校验哈希、落到固定 `{opt}/bin`，并建 `etc/bind` / `var`。非白名单或哈希不符 → 失败。

### `bind-write`（只写绑定文件）

```jsonc
// POST …/bind-write
{
  "productId": "sdap",
  "bindAlias": "demo-mall",
  "body": { "bindAlias": "demo-mall", "projectId": "12", "targets": { } }
}
// 响应
{ "ok": true, "path": "/etc/sdap/bind/demo-mall.json" }
```

只允许写 `{etc}/bind/{bindAlias}.json`，禁止任意路径。鉴权同 `exec`（Bearer）。

Host API（需项目权限）：

- `POST /project/pluginHost/env/{envId}/sut-push/install`
- `POST /project/pluginHost/env/{envId}/sut-push/bind`

制品由 Host 用短时 token 暴露本机已落盘的二进制。

---

## 4. 落地对照

- [x] 同机：不启用执行代理，业务 CLI + `local-exec` 可跑通（存档点主路径）
- [x] 环境可启用「执行代理」插件行（不是 dump/拨钟本身）
- [ ] 分机：`exec-agent` 转发一次业务 `ping` / `archive.create`
- [ ] 非白名单 `bin` 拒绝
- [ ] 经 `/install` + `/bind-write` 后业务可通；坏 token / 非白名单 product 明确失败
- [ ] `ssh` 通道（后置）
