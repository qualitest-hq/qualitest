# 插件设计：被测系统拨钟（`qualitest.sut-sys-clock-dial`）

> **状态**：设计草案（Exploring · 随 [platform-plugins.md](./platform-plugins.md)）  
> **显示名**：被测系统拨钟 · **短称/徽章**：拨钟 · **id**：`qualitest.sut-sys-clock-dial`  
> **实现**：后置；契约与行为以**本文**为准（平台 §2.3.3 只留骨架）。  
> **语言**：Go（单二进制；与库存档点 CLI 样本同策）。  
> **实现仓（私有）**：[qualitest-hq/qualitest-plugin-sut-sys-clock-dial](https://github.com/qualitest-hq/qualitest-plugin-sut-sys-clock-dial)（一插件一仓）。  
> **定位**：改**被测机操作系统时间**（墙钟），以便 Quartz 等跟系统钟的调度也能被快进；**仅适用于测试/演示靶场**，非正式环境。

## 1. 要解决什么

让画布/Prepare 能读、设、快进、复位**被测机系统时间**。  
被测旁装 **CLI**（需足够权限改系统钟）；用完 **uninstall**。跑流结束按策略对时或恢复拨钟前快照。

与「业务虚拟钟 / MockTime 文件」不同：本包**直接改 OS 时间**，同机上的 JVM、Quartz、（若同机）RabbitMQ TTL/延迟等都会跟着走。

### 1.1 为何 CLI 可行（已定）

| 点 | 结论 |
| --- | --- |
| 改墙钟本身 | **能做**：Go 调系统 API / 等价命令即可（Linux 常需 root/`CAP_SYS_TIME`；Windows 需相应管理员权限） |
| 质衡怎么调到 CLI | 与存档点相同：属**核心执行通道**（同机 / sidecar / SSH），见 [platform-ops-exec-channel.md](./platform-ops-exec-channel.md) |
| 容器 / 强制 NTP | 无特权容器或宿主机拧回时间 → 可能失败或被拉回；**仅靶场**；启用时 remarks 提示 |
| Web vs MCP | Web 装不了被测旁 CLI；**MCP + 本机 Agent**（Cursor / Claude Code 等）可协助 install；跑流仍 Invoker→CLI |

用户自选是否启用本插件；未启用则质衡**不**改被测机时间。

## 2. 边界

| 做 | 不做 |
| --- | --- |
| `clock.read` / `set` / `advance` / `reset` | 改**质衡自身**所在机系统时间 |
| 环境 config 定复位策略、`prepareAt` 等 | 默认常驻守护进程；**无**项目级挂载 |
| Prepare 设钟、Finalize 复位 | 与库存档点混在同一 CLI |
| 画布 `pluginAction` 贡献「快进」节点 | 新增专用 `FlowNodeType` |
| remarks / 启用提示写明靶场风险 | 承诺适用于生产；静默改钟不审计 |

**适用前提（文档约定，非运行时强校验）**：被测为可重建的测试/演示机；操作者接受跳变对证书、日志、NTP、同机其它进程的影响。

## 3. 身份与形态

| 项 | 值 |
| --- | --- |
| `id` | `qualitest.sut-sys-clock-dial` |
| `displayName` | 被测系统拨钟（短称：拨钟） |
| `runtime.kind` | `ops-agent` |
| `agent.bundle` | `embedded` |
| CLI `bin` | `qt-sut-sys-clock-dial` |
| `isolation` | `sut-sys-clock-dial`；`ui-slot:flow.banner.clock` |

一台被测机**同时只能启用一个**带 `sut-sys-clock-dial` 的包（mutex）。

若被测已暴露「改钟」HTTP（极少见）：可另发 **`http-adapter`**（同能力名）；**默认路径仍是本 CLI 改 OS 时间**，不强迫装假钟服务。

**CLI 命名**：本包用完整 `qt-…` 名；存档点短名 `sdsp`——均以说明书 `runtime.agent.cli.bin` 为准，不强求同风格。

## 4. 私有能力

| name | effect | gate | preparePhase | 含义 |
| --- | --- | --- | --- | --- |
| `clock.read` | read | none | — | 读当前系统时间（ISO-8601）及是否相对「拨钟前/NTP」有偏移（实现可子集） |
| `clock.set` | write | destructive | `clock` | 将系统时间设到绝对时间（ISO-8601） |
| `clock.advance` | write | destructive | — | 相对快进（如 `P2D`） |
| `clock.reset` | write | destructive | — | 按 `resetPolicy` 复位（见 §5） |

能力名**仅属本包**。写能力一律走破坏性总闸 + 审计。  
启用按 gate **并集** → 只要说明书含写能力，启用即须开破坏性总闸。

## 5. 环境 `config`（`configSchema`）

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `resetPolicy` | 是 | `ntp`：复位时同步网络时间；`restore`：恢复本插件在本轮/本机记录的「拨钟前」时间戳 |
| `prepareAt` | 条件必填 | ISO-8601 绝对时间。**当**说明书 hooks 含 `onRunPrepare` → `clock.set` 时，启用**必须**有本字段；平台 Invoke Prepare 时把它作为 `clock.set` 的 `at`。去掉该 Prepare 绑定后可省略 |
| `defaultAdvance` | 否 | 画布/工具条默认快进量，如 `P2D`（只影响 `clock.advance`，**不能**替代 `prepareAt`） |
| `timezone` | 否 | 解释/展示用时区（如 `Asia/Shanghai`）；**设钟仍以绝对瞬间为准**，实现勿只改时区标签 |

示意（带默认 Prepare 设钟）：

```jsonc
{
  "resetPolicy": "ntp",
  "prepareAt": "2030-01-01T00:00:00+08:00",
  "defaultAdvance": "P2D",
  "timezone": "Asia/Shanghai"
}
```

**Prepare 时间从哪来（已定）**：只能来自环境 `prepareAt`（或节点/工具条显式传 `at`）。**禁止**用 `defaultAdvance` 冒充绝对设钟；**禁止**默认「悄悄设成某个硬编码时间」。

若环境不想在开跑时改钟：从说明书 hooks 去掉 `onRunPrepare`→`clock.set`（或启用时覆盖为空 hooks——若平台允许包 hooks 覆写；P0 以说明书为准，去掉则改说明书/虚包变体），只靠画布 `clock-advance` / 工具条。

**权限**：`set` / `advance` / `reset` 需要改系统时间的 OS 权限（Linux 常见需 root/CAP_SYS_TIME；Windows 需相应管理员权限）。`ping` 应能探测「可读时间」；能否写钟可在 ping 结果或首次写时报错并给出明确信息。

## 6. CLI 约定（设计）

```text
qt-sut-sys-clock-dial ping
qt-sut-sys-clock-dial clock.read --config <path>
qt-sut-sys-clock-dial clock.set --config <path> --at <ISO-8601>
qt-sut-sys-clock-dial clock.advance --config <path> --by <duration>
qt-sut-sys-clock-dial clock.reset --config <path>
```

| 命令 | 行为要点 |
| --- | --- |
| `ping` | 二进制可用；可读系统时间；可选报告是否具备改钟权限 |
| `clock.read` | stdout JSON：如 `{ "now": "...", "skewed": true/false }`（字段以实现为准） |
| `clock.set` / `advance` | 改 OS 系统时间；**首次写前**建议落盘「拨钟前」快照，供 `restore` 策略使用 |
| `clock.reset` | `ntp`：触发系统对时（chrony/ntp/Windows 时间服务等，平台相关）；`restore`：写回已保存的拨钟前时间；无快照且策略为 restore 时失败并明示 |

**安装 / 卸载**：同库存档点插件——`install.sh` 装二进制；`uninstall.sh` 卸二进制与本插件目录。卸载前**宜**若仍处于拨钟偏移则尝试 `reset`（失败则警告，不阻断卸载语义由实现定）。**不**留守护进程。

## 7. 与质衡的衔接（契约）

1. 环境启用：过 schema + destructive 总闸；若 hooks 含 Prepare→`clock.set` 则校验 `prepareAt`；UI/remarks 提示「改被测机系统时间，仅靶场」。  
2. 被测机 install + `ping`。  
3. Prepare：`Invoker.invoke(..., "clock.set", { "at": envConfig.prepareAt }, …)`。  
4. 画布/工具条：`clock.advance`（可用 `defaultAdvance` 作表单默认值）。  
5. Finalize：`clock.reset`（order 示例 20）。  
6. UI：顶栏 banner「系统时间 {{clock.now}}」+ 环境徽章「拨钟」+ 调色板节点 `clock-advance`。

## 8. 说明书全文示意

```jsonc
{
  "id": "qualitest.sut-sys-clock-dial",
  "version": "1.0.0",
  "displayName": "被测系统拨钟",
  "remarks": "CLI 改被测机系统时间（非生产）。写操作需破坏性总闸；Prepare 设钟靠 prepareAt；复位 ntp 或 restore。短称：拨钟。",
  "capabilities": [
    { "name": "clock.read", "effect": "read", "gate": "none" },
    { "name": "clock.set", "effect": "write", "gate": "destructive", "preparePhase": "clock" },
    { "name": "clock.advance", "effect": "write", "gate": "destructive" },
    { "name": "clock.reset", "effect": "write", "gate": "destructive" }
  ],
  "isolation": ["sut-sys-clock-dial", "ui-slot:flow.banner.clock"],
  "hooks": [
    { "name": "onRunPrepare", "order": 80, "actions": ["clock.set"] },
    { "name": "onRunFinalize", "order": 20, "actions": ["clock.reset"] }
  ],
  "configSchema": {
    "type": "object",
    "properties": {
      "resetPolicy": { "enum": ["ntp", "restore"] },
      "prepareAt": { "type": "string", "description": "ISO-8601；Prepare→clock.set 时必填" },
      "defaultAdvance": { "type": "string", "description": "如 P2D" },
      "timezone": { "type": "string", "description": "展示用，如 Asia/Shanghai" }
    },
    "required": ["resetPolicy"]
    // prepareAt：有 onRunPrepare→clock.set 时由启用算法额外校验（见 §5）
  },
  "ui": {
    "chrome": {
      "banner": [
        { "id": "clock-banner", "slot": "top", "text": "系统时间 {{clock.now}}", "tone": "warning" }
      ]
    },
    "env": { "badge": { "text": "拨钟", "tone": "warning" } },
    "palette": {
      "nodes": [
        {
          "id": "clock-advance",
          "label": "快进被测系统时间",
          "capability": "clock.advance",
          "configSchema": {
            "type": "object",
            "properties": { "by": { "type": "string" } },
            "required": ["by"]
          }
        }
      ]
    }
  },
  "runtime": {
    "kind": "ops-agent",
    "agent": {
      "bundle": "embedded",
      "cli": { "bin": "qt-sut-sys-clock-dial", "pingArgs": ["ping"] }
    }
  }
}
```

## 9. 影响面与非目标

**会跟着变（同机）**：JVM/`Instant.now()`、Quartz 等跟系统钟的调度、同机 Broker 的 TTL/延迟等。  
**分机部署**：只改应用机**不会**改另一台 RabbitMQ/DB 的宿主时间——测跨机超时/延迟时需单独约定是否拨那些机（本包默认只管「装了 CLI 的那台被测机」）。

| 非目标 / 待定 |
| --- |
| 不定：各 OS 改钟与 NTP 的具体系统调用/命令封装（Go 实现时定） |
| 不定：容器内改钟是否要求 privileged、是否只影响容器命名空间 |
| 不定：仓库路径（可与 `sdsp` 同仓多 bin 或分仓） |
| 不做：MockTime 状态文件 / 默认 HTTP 假钟服务 |
| 不做：本包内嵌 SQL 恢复；改质衡宿主时钟 |
| 不做：多项目共机时的「每项目一只系统钟」（OS 只有一只墙钟；靠 mutex + 靶场纪律） |
| 不做：用 `defaultAdvance` 充当 Prepare 绝对时间 |
| 不做：无执行通道仍声称可拨钟；靠 Web 远程改被测 OS 时间 |
