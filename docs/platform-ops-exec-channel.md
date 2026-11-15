# 平台能力：ops 执行通道（含 sidecar 落地）

> **状态**：设计草案（Exploring · 随 [platform-plugins.md](./platform-plugins.md) §2.3.2）  
> **归属**：**质衡核心能力**（Invoker 的到达方式）——**不是**平台插件，用户不为「启用边车插件」勾选业务包。  
> **实现**：后置；契约以本文为准。  
> **语言（建议）**：被测旁边车二进制可用 **Rust**（并发 exec、超时、以后收紧权限）；Go 亦可。  
> **实现仓（私有，非插件）**：[qualitest-hq/qualitest-ops-sidecar](https://github.com/qualitest-hq/qualitest-ops-sidecar)。  
> **定位**：规定质衡如何在被测机上执行 ops CLI；**sidecar** 是分机时的一种落地，与 `local-exec` / `ssh` 并列。

## 1. 为何是核心，不是插件

| | 执行通道（本文） | 业务插件（存档点 / 拨钟 / 网混沌…） |
| --- | --- | --- |
| 归谁 | **质衡核心** | 插件说明书 + 被测旁业务 CLI |
| 用户感知 | 环境里选「同机 / 边车 / SSH」、填 URL | 环境里**启用**某个业务插件 |
| 改被测业务状态？ | **否**（只负责把命令跑起来） | 是 |
| 画布 / hooks 业务能力 | **无** `save.*` / `clock.*` 之类 | 有私有 `capabilities[]` |

铁律对齐：核心管「何时 invoke、**怎么够到被测机**」；插件管「够到之后干什么」。  
边车进程装在被测旁，但是 **平台交付的通道组件**，不是「又一个可选业务插件」。

## 2. 要解决什么

```text
同机：核心 channel=local-exec → 质衡进程直接 Process 调 CLI，不必装边车。
分机：质衡在 A，CLI 在 B → 核心 channel=sidecar|ssh → 在 B 上代跑。
```

```text
Invoker（核心）
  → 读环境执行通道配置
  → local-exec | sidecar | ssh
  → （若 sidecar）HTTP 打到被测机上的边车二进制
       → exec 白名单内 bin（sdsp / 拨钟 / 网混沌 / …）
```

「RPC 后置」= 不做重业务协议；**不是**没有通道。

## 3. 边界

| 做 | 不做 |
| --- | --- |
| 环境级 `channel.kind` + 对应参数；Invoke 按 kind 分发 | 把通道做成用户启用的业务插件 id |
| sidecar：`ping` / 代跑白名单 `bin` | 任意 shell；边车内实现 dump/拨钟业务 |
| 写入临时 `--config` 再 exec；超时杀进程 | 用通道绕过「业务插件未启用」 |
| 跑流期拉起边车 / 用完可停（推荐） | 必须 7×24 才算支持分机 |
| 与同机 exec、SSH **并列** | 同机还强迫装边车 |

## 4. 三种 kind（核心枚举）

| `channel.kind` | 何时用 | 质衡行为 |
| --- | --- | --- |
| `local-exec` | 质衡与被测同机 | 本机起进程跑 `runtime.agent.cli.bin` |
| `sidecar` | 分机、内网 HTTP 代跑 | 调被测旁边车 `ping`/`exec`（§6） |
| `ssh` | 已有 SSH 运维通道 | 远程 argv exec（密钥走环境密钥槽） |

启用任一 `ops-agent` 业务插件时：**必须**已配置合法通道；`sidecar`/`ssh` 探活失败 → `AGENT_UNREACHABLE`。

## 5. 环境配置（质衡侧，核心）

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `channel.kind` | 是* | *有 ops-agent 插件启用时需要；纯 http-adapter/builtin 可不配 |
| `channel.sidecar.baseUrl` | kind=sidecar 时是 | 如 `http://10.0.0.12:7920` |
| `channel.sidecar.tokenRef` | 否 | 环境密钥槽；**禁止**进流程图 |
| `channel.ssh.*` | kind=ssh 时 | host/user/密钥引用等（细节实现定） |

**禁止**写进插件 zip / 流程图。  
**无**「项目挂载边车插件」；URL 属环境（或项目默认通道，后置）。

被测机边车**本地**配置：监听地址、允许的 `bin` 路径表、临时目录——不进质衡库表业务字段。

## 6. Sidecar 最小协议（通道落地）

运输层可换，语义不变。

### 6.1 `ping`

```jsonc
{ "ok": true, "version": "0.1.0", "hostname": "sut-demo-01" }
```

### 6.2 `exec`

```jsonc
// 请求
{
  "bin": "sdsp",
  "args": ["save.create", "--config", "CONFIG_PATH"],
  "configText": "{ ... }",
  "timeoutMs": 120000,
  "env": { "RUN_ID": "1001" }
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

| 规则 | 说明 |
| --- | --- |
| `bin` 白名单 | **必须**；未知 → `BIN_NOT_ALLOWED` |
| **禁止** `bash -c` / `cmd /c` 拼串 | 只 exec 实参数组 |
| 超时 | 杀进程组 → `TIMEOUT` |
| 鉴权 | P0 内网 + token；mTLS 后置 |

组件名可叫 `qualitest-ops-sidecar`（二进制/包名），**不要**注册为业务 `pluginId` 进启用表与画布。

## 7. 生命周期与安装

| 模式 | 说明 | P0 |
| --- | --- | --- |
| **随跑随起** | Compose/脚本跑流前 start，结束后 stop | **推荐** |
| 常驻 | 演示机常开 | 后置；须鉴权 |

| 谁 | 做什么 |
| --- | --- |
| Web | 配 `channel.*`、展示 ping；**不能**往被测盘装二进制 |
| 本机 Agent + MCP | 协助 install 边车、写环境 `baseUrl` |
| 跑流 | 核心 Invoker → 通道 → 业务 CLI |

卸载边车：停进程、删通道二进制与本地配置；**不**删各业务 CLI 的 bind/存档。

## 8. 安全

1. 白名单 bin；禁止万能远程 shell 产品化。  
2. 仅内网；无鉴权不暴露公网。  
3. 审计：核心记 pluginId/capability/channel.kind；边车可记 bin/args/exit（忌密码）。  
4. 破坏性总闸仍看**业务能力** `gate`；通道不降闸、不替代启用校验。

## 9. 非目标

- 不做：把执行通道做成可启用/停用的业务插件市场条目  
- 不做：在边车内实现 `save.*` / `clock.*` / `net.*`  
- 不做：未启用业务插件却经通道暗门调用其 CLI  
- 后置：完整 RPC、公网 agent、Windows 服务化安装向导  

## 10. 验收意向

- [ ] 同机 `local-exec` 不装边车可调业务 CLI  
- [ ] 分机 `sidecar` 可完成一次业务 `ping` / 写操作  
- [ ] 非白名单 `bin` 拒  
- [ ] 超时杀进程  
- [ ] 边车不可达 → 业务 Invoke `AGENT_UNREACHABLE`  
- [ ] 环境插件列表里**没有**「执行边车」业务插件行  
