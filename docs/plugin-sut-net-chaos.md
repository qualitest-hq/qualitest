# 插件设计：被测网络混沌（`qualitest.sut-net-chaos`）

> **状态**：设计草案（Exploring · 随 [platform-plugins.md](./platform-plugins.md)）  
> **显示名**：被测网络混沌 · **短称/徽章**：网混沌 · **id**：`qualitest.sut-net-chaos`  
> **实现**：后置；契约以**本文**为准。  
> **语言（建议）**：**Rust**（Linux netlink/`tc` 等系统面）；Go 亦可。  
> **实现仓（私有）**：[qualitest-hq/qualitest-plugin-sut-net-chaos](https://github.com/qualitest-hq/qualitest-plugin-sut-net-chaos)（一插件一仓）。  
> **定位**：在**被测机**上短时注入延迟、丢包等，便于测超时/重试/熔断；**仅靶场**。  
> **形态**：ops-agent + **CLI**（与存档点/拨钟同通道模型）；分机时经平台 [执行通道](./platform-ops-exec-channel.md)（sidecar / SSH）代跑。

## 1. 要解决什么

测试环境网络往往过好，部分缺陷只在「慢 / 丢包 / 短暂断依赖」时出现。  
本插件在被测机（或指定网卡/流向）上**故意把网搞差一会儿**，跑流断言后**必须恢复**。

```text
典型测流：
  节点「注入延迟 2s」→ 下单/支付 HTTP（期望超时或重试）→ 节点「恢复网络」
或：
  Prepare 注入 → 跑图 → Finalize 强制 reset
```

与存档点/拨钟的关系：正交。可同时启用；都经同一执行通道调各自 CLI。

## 2. 边界

| 做 | 不做 |
| --- | --- |
| `net.delay` / `net.loss` / `net.status` / `net.reset` | 改质衡机房网络、对公网任意目标做攻击 |
| Linux 靶场 + `tc`/netem（首期） | P0 承诺 Windows 等效（后置或标不支持） |
| 画布 `pluginAction`；Finalize 默认 reset | 新增长驻「混沌守护」业务进程（CLI 改规则后即退） |
| remarks 写明靶场风险 | 生产；静默改网不审计 |
| 作用域尽量可配（网卡 / 目的 CIDR） | 无作用域默认搞挂整机所有流量却不提示 |

**适用前提**：可重建的测试/演示机；操作者接受短暂网络异常；容器须具备改 `tc` 的权限（常见需 `NET_ADMIN`）。

## 3. 身份与形态

| 项 | 值 |
| --- | --- |
| `id` | `qualitest.sut-net-chaos` |
| `displayName` | 被测网络混沌（短称：网混沌） |
| `runtime.kind` | `ops-agent` |
| `agent.bundle` | `embedded` |
| CLI `bin` | `qt-sut-net-chaos` |
| `isolation` | `sut-net-chaos`（**mutex**：同环境第二包拒）；`ui-slot:flow.badge.net-chaos` |

一台被测机同一时刻只应有一套由本插件管理的混沌规则（避免两家互踩）；恢复靠 `net.reset`。

## 4. 私有能力

| name | effect | gate | 含义 |
| --- | --- | --- | --- |
| `net.status` | read | none | 当前是否有本插件施加的规则、摘要（延迟/丢包等） |
| `net.delay` | write | destructive | 对作用域注入延迟（如 `delayMs`） |
| `net.loss` | write | destructive | 对作用域注入丢包率（如 `percent`） |
| `net.reset` | write | destructive | **清除**本插件施加的规则，恢复默认 |

写能力一律破坏性总闸 + 审计。  
启用按 gate **并集** → 含写能力则须开总闸。

**P0 不做**（可后置占名）：`net.corrupt`、`net.duplicate`、`net.partition`（对某 CIDR 黑洞）——先 delay/loss/reset 跑通。

## 5. 环境 `config`（尽量少）

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `defaultInterface` | 否 | 默认网卡名（如 `eth0`）；省略则 CLI 按实现选默认路由网卡，选不到则失败并明示 |
| `defaultDestCidr` | 否 | 默认目的范围（如 `10.0.0.0/8`）；省略=实现定义的「本机出站默认类」——**必须在 ping/status 说明实际作用域** |
| `resetOnFinalize` | 否 | 默认 `true`：hooks `onRunFinalize` → `net.reset` |

```jsonc
{
  "defaultInterface": "eth0",
  "resetOnFinalize": true
}
```

**无**项目级挂载。连接类密钥无。

## 6. Invoke 参数

| 能力 | 参数 | 说明 |
| --- | --- | --- |
| `net.delay` | `delayMs`（必填）；`interface` / `destCidr`（可选，覆盖环境默认） | 例：`{ "delayMs": 2000 }` |
| `net.loss` | `percent`（必填，0–100）；可选作用域同上 | 例：`{ "percent": 5 }` |
| `net.reset` | 无或可选 `interface` | 清本插件规则 |
| `net.status` | 无 | 只读摘要 |

## 7. CLI 约定（设计）

```text
qt-sut-net-chaos ping
qt-sut-net-chaos net.status --config <path>
qt-sut-net-chaos net.delay --config <path> --delay-ms <n>
qt-sut-net-chaos net.loss  --config <path> --percent <n>
qt-sut-net-chaos net.reset --config <path>
```

| 命令 | 要点 |
| --- | --- |
| `ping` | 二进制可用；探测是否具备改 `tc` 的权限；stdout 可含 OS/网卡提示 |
| `net.delay` / `net.loss` | 幂等倾向：重复施加以最后一次为准；先清本插件旧规再加（实现定） |
| `net.reset` | 只清**本插件打的标记/句柄**；禁止乱删管理员手工 `tc` 规则（实现用 handle/优先级隔离） |
| `net.status` | JSON：是否 active、delayMs、loss、interface 等 |

平台下发 `--config` 注入：`pluginId`、`projectId`、`envId`、`runId`、`allowDestructive`、环境默认作用域。

**安装 / 卸载**：同其它 ops CLI。卸载前宜 `net.reset`（失败则警告）。

## 8. 与质衡衔接

1. 环境启用：schema + destructive 总闸；remarks「仅靶场，可能影响同机其它进程出站」。  
2. 被测机 install CLI +（分机）执行通道可达；`ping` 成功。  
3. 画布贡献节点例：`net-delay`、`net-reset`（`pluginAction`）。  
4. 推荐 hooks：`onRunFinalize` → `net.reset`（order 靠前，避免跑崩后网一直烂）。  
5. UI：徽章「网混沌」；可选 banner「网络混沌生效中」绑定 `net.status` 投影（后置）。

### 说明书骨架

```jsonc
{
  "id": "qualitest.sut-net-chaos",
  "version": "1.0.0",
  "displayName": "被测网络混沌",
  "remarks": "CLI 在被测机注入延迟/丢包（仅靶场）。写操作需破坏性总闸；Finalize 建议 reset。",
  "capabilities": [
    { "name": "net.status", "effect": "read", "gate": "none" },
    { "name": "net.delay", "effect": "write", "gate": "destructive" },
    { "name": "net.loss", "effect": "write", "gate": "destructive" },
    { "name": "net.reset", "effect": "write", "gate": "destructive" }
  ],
  "isolation": ["sut-net-chaos", "ui-slot:flow.badge.net-chaos"],
  "hooks": [
    { "name": "onRunFinalize", "order": 10, "actions": ["net.reset"] }
  ],
  "ui": {
    "env": { "badge": { "text": "网混沌", "tone": "warning" } },
    "palette": {
      "nodes": [
        {
          "id": "net-delay",
          "label": "注入网络延迟",
          "capability": "net.delay",
          "configSchema": {
            "type": "object",
            "properties": { "delayMs": { "type": "integer", "minimum": 1 } },
            "required": ["delayMs"]
          }
        },
        {
          "id": "net-reset",
          "label": "恢复网络",
          "capability": "net.reset",
          "configSchema": { "type": "object", "properties": {} }
        }
      ]
    }
  },
  "runtime": {
    "kind": "ops-agent",
    "agent": {
      "bundle": "embedded",
      "cli": { "bin": "qt-sut-net-chaos", "pingArgs": ["ping"] }
    }
  }
}
```

## 9. 影响面与非目标

**会受影响**：被测机上命中作用域的出站/转发流量（含同机其它进程，若作用域过宽）。  
**不会**：自动改质衡服务器网、自动混沌公网第三方（除非作用域误配——故默认要谨慎、status 要说清）。

| 非目标 / 待定 |
| --- |
| P0 不做 Windows 流量整形完整方案 |
| P0 不做 partition/corrupt/duplicate |
| 不定：具体 `tc` qdisc/handle 编号方案（实现时写进 CLI 备注） |
| 不做：在本包内嵌存档/拨钟；无执行通道仍声称可混沌 |
| 不做：当作生产演练平台（那是另一类产品） |

## 10. 一次跑流时间线（帮助建立直觉）

```text
T0  环境已启用本插件 + 破坏性总闸；分机则边车 ping OK
T1  图节点 net.delay delayMs=2000
    → Invoker →（边车）→ qt-sut-net-chaos net.delay
T2  HTTP 下单步骤变慢 / 触发重试断言
T3  图节点 net.reset 或 Finalize hooks
    → 网络恢复
```
