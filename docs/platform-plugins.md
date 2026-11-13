# 质衡平台插件（草案）

> **状态**：设计草案 · ROADMAP **Exploring 6.2**（不承诺版本）。  
> **范围**：质衡**产品内**可装卸扩展；一个插件可含两端（正式称呼见 §3.1）：  
> **运行端** + **界面端**。  
> **不含**：JetBrains「Qualitest Helper」、Cursor MCP 工具清单本身（接入通道 ≠ 平台插件）。

本文定：**什么进插件、能力怎么命名预留、如何共存**；契约形状用 **JSON** 写清。  
举例只说明挂接与冲突，**不**展开各插件后端实现协议。

---

## 1. 为什么要做

今天质衡核心已经能：画布编排、跑流、鉴权、AI/MCP、报告。  
和**被测运行时状态**强相关的能力，有的埋在核心（HTTP `snapshotBefore` → `/test-support`），有的在运维仓（`qualitest-demo-host` 金库 / daily-reset），有的还只是测流里手写 HTTP（靶场 `scenario/load`）。

另一面同样重要：插件一旦能拨钟、还原库、挂挡板，**测流 UI 必须能表达这些能力**——环境条、节点角标、审计步样式、属性侧栏、报告卡片——否则能力「黑盒存在」，用户看不见、也配不了。呈现若继续写死在核心 Vue 里，每个官方插件都要改主仓前端，兼容性同样崩。

若不先定插件边界，后面每加一种靶场运维或一种测流皮肤/贡献位，都会继续渗进核心，**以后很难兼容**。

**原则（判定口诀）**：

| 问 | 是 → | 否 → |
| --- | --- | --- |
| 是否改被测认定的数据 / 时钟 / 进程旁路状态？ | **插件（运行端）** | 看下一问 |
| 是否改变测流在编辑/运行/报告中的呈现或可贡献控件（且随插件装卸）？ | **插件（界面端）** | 看下一问 |
| 是否强绑定某一被测、某一云厂商、某一支付渠道的细节？ | **插件**（或插件包） | 看下一问 |
| 是否所有项目都需要、且与画布**引擎语义**强耦合？ | **核心** | 倾向插件或项目配置 |

核心只提供：**挂点、能力名注册、环境总闸、串行、冲突检测、审计、UI 贡献槽与设计令牌契约**；不内置「商城金库怎么 dump」「微信退款挡板怎么回包」，也不为每个插件改一遍画布源码。

---

## 2. 核心 vs 插件

插件是**双端**的：同一包常同时带运行端能力 + 界面端贡献（例如「被测时钟」既有 `sut.clock.*`，也有画布顶栏「当前虚拟时间」）。

| 归核心（保持稳定） | 归插件（可装卸） |
| --- | --- |
| 项目 / 环境 / 成员权限 / Project Token | 被测库：检查点、整库备份还原、场景种子加载 |
| 固定 **8** 类节点的**执行语义**与图校验骨架 | 被测时钟、缓存/MQ/上传旁路、验证码后门、进程重启等 |
| 画布壳、时间线壳、报告壳、**贡献槽位与令牌表** | 测流主题令牌、节点装饰、顶栏/侧栏/时间线条目、报告块 |
| MCP/AI **协议骨架**与门控开关 | 挡板渠道包、收件应答策略、领域提示包 |
| 出站 HTTP、脚本沙箱宿主、质衡自身存储 | 强绑定被测的签头/渠道开箱预设 |
| Webhook 入站门面、鉴权 Profile 框架、排队串行调度 | （同上，运行细节） |

刻意不做（至少本阶段）：

- 用「自定义画布节点类型」当插件**主**路径（成本高；ROADMAP 5.1 另议）。界面端优先 **装饰 / 槽位 / 令牌**，而不是新增第 9 种 `FlowNodeType`。
- 插件任意注入全局 CSS 或替换整页布局（只允许契约内令牌 + 具名槽位，防把控制台弄崩）。
- 插件改质衡自身 DB / 自身 JVM 时钟 / 全局多租户状态。
- 无签名、无权限的远程 JAR/前端包热装「应用商店」（后置）。
- 把独立 DB 节点、同流压测做进插件来绕开「刻意不做」——插件不是后门。

---

## 3. 插件模型（JSON 契约）

结构一律用 **JSON**（如 `qualitest.plugin.json`、环境 composition）。阅读约定：**先 JSON，紧接「说明」**，不再把字段表拆到别处。草案形状可增字段，不可随便改已有语义。

### 3.1 一句话与两端命名

**插件 = 一份 `qualitest.plugin.json` +（可选）运行端材料 +（可选）界面端材料**，对外常打成**一个 zip**。

两端正式称呼（文档与 UI 文案统一用这套，避免和「前后端工程」「被测/质衡」糊在一起）：

```json
{
  "ends": [
    {
      "name": "运行端",
      "nameEn": "Runtime",
      "manifestKeys": ["capabilities(sut.*|egress.*|ingress.*|pack.*)", "hooks", "runtime", "configSchema"],
      "zipPath": "runtime/",
      "job": "跑流前后真正干活：拨钟、还原、挡板、域名拦截等"
    },
    {
      "name": "界面端",
      "nameEn": "Console",
      "manifestKeys": ["capabilities(ui.flow.*)", "ui"],
      "zipPath": "ui/",
      "job": "测流页看得见、配得了：令牌、横幅、节点装饰/面板、时间线、报告块"
    }
  ],
  "aliasDeprecated": ["运行面", "呈现面"]
}
```

说明：

- **运行端（Runtime）**：能力名以 `sut.*` / `egress.*` / `ingress.*` / `pack.*` 等为主；执行看 `runtime.kind`。  
- **界面端（Console）**：能力名以 `ui.flow.*` 为主；描述在 `ui`；浏览器里由质衡 Web 壳渲染。  
- 旧称「运行面 / 呈现面」等同两端，后文逐步收成 **运行端 / 界面端**。  
- 可以只有一端（纯皮肤包只有界面端；纯 ops 脚本包几乎只有运行端），官方带行为的包建议两端都有。  
- **不要**把两端叫成「前端插件 / 后端插件」两套独立产品——仍是**一个插件 id、一个 zip**；只是载荷分两目录。

**运行端内部再分落点**（仍属运行端，不是第三「端」）：

| `runtime.kind` | 代码跑在哪 |
| --- | --- |
| `builtin` | 质衡 **app** 进程内（随质衡版本） |
| `httpAdapter` | 质衡 app 出站调被测/代理 |
| `opsAgent` | **被测旁**侧车/演示机（与 app 分开发装） |

### 3.2 Manifest 根对象

```json
{
  "id": "official.sut-clock",
  "version": "1.0.0",
  "displayName": "被测时钟",
  "danger": "destructive",
  "audit": true,
  "capabilities": [
    "sut.clock.read",
    "sut.clock.set",
    "sut.clock.advance",
    "sut.clock.reset",
    "ui.flow.chrome.banner",
    "ui.flow.timeline.step",
    "ui.flow.node.decorate"
  ],
  "requires": ["gate.destructive"],
  "conflicts": ["capability:sut.clock.set@other"],
  "isolation": ["sut-clock", "ui-slot:flow.banner.clock"],
  "hooks": [
    { "name": "onRunPrepare", "order": 80, "actions": ["clock.set"] },
    { "name": "onRunFinalize", "order": 20, "actions": ["clock.reset"] }
  ],
  "configSchema": {
    "type": "object",
    "properties": {
      "mode": { "enum": ["mockTimeApi", "header", "containerClock"] },
      "baseUrl": { "type": "string" },
      "defaultAdvance": { "type": "string", "description": "ISO-8601 duration，如 P2D" }
    },
    "required": ["mode"]
  },
  "ui": {
    "tokens": {},
    "chrome": {},
    "nodes": {},
    "timeline": {},
    "report": {},
    "env": {},
    "palette": {}
  },
  "runtime": {
    "kind": "httpAdapter",
    "entry": "builtin:sut-clock"
  }
}
```

说明：

- `id` / `version` / `displayName`：包标识、semver、界面名。  
- `danger`：`none` | `destructive` | `secrets_read`，驱动警告与权限。  
- `audit`：为 true 时强制写 Run 审计步。  
- `capabilities`：本包能力名（§3.4），运行端与界面端（`ui.flow.*`）写在一起。  
- `requires` / `conflicts`：依赖与互斥，可写 `capability:…` 或 `plugin:…`。  
- `isolation`：冲突与串行标签（§3.5）。  
- `hooks`：Run 生命周期（§4）；同阶段内 `order` **越小越先**。  
- `configSchema`：环境/项目配置的 JSON Schema。  
- `ui`：呈现贡献（§3.4-I）；用不到的子树可省略。  
- `runtime`：执行体入口（见下）；契约不绑死实现类名。

**有没有代码？——复杂功能必须有；JSON 只是契约层。**

怕「不加代码搞不定」：对。拨钟、金库 dump、挡板渠道验签、场景编排等**本来就要写代码**。  
草案禁止的是「无门禁地往质衡进程/浏览器热塞任意包」，**不是**禁止实现逻辑。

| 部分 | 有没有代码 | 说明 |
| --- | --- | --- |
| `qualitest.plugin.json` | **无** | 只描述能力、配置、UI 槽、hooks；再复杂的功能也先在这里登记 |
| `ui` | **默认声明式；复杂面板可后置带代码** | 第一期：壳按 JSON 画横幅/角标/简单 panel。若某官方插件属性区实在画不动，允许随版本附带**审核过的前端贡献模块**（仍走具名槽，不是整页替换） |
| `runtime.kind = httpAdapter` | **逻辑可很简单** | 被测已有 API 时，适配层薄；复杂规则仍可写在被测或下面两档 |
| `runtime.kind = builtin` | **有，完整 Java（等）实现** | **复杂官方功能的主路径**：代码在质衡主仓模块里，随版本发布，测流只启用 JSON 包名。金库策略、时钟状态机、挡板渠道包都可以长在这里 |
| `runtime.kind = opsAgent` | **有，侧车脚本/服务** | OS 拨钟、mysqldump、容器操作等不适合进 JVM 的，放运维代理；JSON 只描述怎么调 |
| 第三方任意 JAR/前端包热装 | **近期不做** | 不等于永远不做；要做也是签名 + 允许列表之后的事 |

能力够不够用，看执行体档位，不看 JSON 漂亮不漂亮：

1. **被测已提供 HTTP 门面** → `httpAdapter` 往往够。  
2. **要状态机、多步、和跑流深度耦合** → `builtin` 写代码（推荐官方复杂插件这么做）。  
3. **要碰宿主机/容器/本机 DB 客户端** → `opsAgent` 写代码。  
4. **UI 声明式不够** → 官方可加审核过的前端贡献；仍挂在 `ui` 槽上，不推翻壳。

一句话：**JSON = 身份证与说明书；代码 = 干活的手。** 复杂功能把手放在 builtin / opsAgent（必要时受控前端模块），不要因为契约是 JSON 就不敢做重逻辑。

### 3.3 环境组合（谁启用哪些包）

项目/环境侧另存 composition（不是插件包内文件）：

```json
{
  "envId": 12,
  "plugins": [
    {
      "id": "official.sut-sql-backup",
      "version": "1.0.0",
      "enabled": true,
      "config": { "backupId": "golden-mall-2026-11" }
    },
    {
      "id": "official.sut-clock",
      "version": "1.0.0",
      "enabled": true,
      "config": { "mode": "mockTimeApi", "defaultAdvance": "P2D" }
    }
  ],
  "primaryDataProvider": "official.sut-sql-backup",
  "prepareOrder": ["data.reset", "aux.flush", "runtime.health", "clock.set"]
}
```

说明：

- `plugins[]`：本环境启用的包；`config` 须通过对应包的 `configSchema`。  
- `primaryDataProvider`：多个数据类插件并存时，指定 Prepare 默认真相源（金库 vs 场景加载）。  
- `prepareOrder`：可覆盖 §4 默认 Prepare 动作序；未写则用平台默认。

### 3.4 能力目录（预留名 · 便于以后兼容）

只定**名字与语义**，不定 HTTP/Java API；名字先占住，实现可分批。每组先 JSON，后说明。

#### A. 数据面 `sut.data.*`

```json
[
  { "name": "sut.data.checkpoint", "desc": "跑中检查点，返回 snapshotId", "hint": "现 /test-support + snapshotBefore" },
  { "name": "sut.data.restore", "desc": "按 snapshotId 还原", "hint": "同上" },
  { "name": "sut.data.backup", "desc": "导出可版本化备份（如 SQL 金库）", "hint": "demo-host data/golden" },
  { "name": "sut.data.reset_to_backup", "desc": "约定范围回到某备份", "hint": "daily-reset" },
  { "name": "sut.data.scenario.list", "desc": "列出被测场景目录", "hint": "mall manifest" },
  { "name": "sut.data.scenario.load", "desc": "加载场景（常含 reset+写入）", "hint": "POST .../scenario/load/{id}" },
  { "name": "sut.data.scenario.reset", "desc": "仅回基线", "hint": ".../scenario/reset" }
]
```

说明：`reset_to_backup` 与 `scenario.load` 可并存，但须用 composition 的 `primaryDataProvider` 指定 Prepare 默认真相源，禁止两个都静默清库。

#### B. 时间面 `sut.clock.*`

```json
[
  { "name": "sut.clock.read", "desc": "读取被测认定的「现在」" },
  { "name": "sut.clock.set", "desc": "设绝对时间" },
  { "name": "sut.clock.advance", "desc": "相对快进" },
  { "name": "sut.clock.reset", "desc": "清除偏移 / 恢复墙钟" }
]
```

说明：实现可以是 MockTime API、header 注入或受控容器钟；**能力名相同**，用 `isolation`（如 `sut-clock` vs `sut-clock-os`）区分危险级别。

#### C. 旁路状态 `sut.aux.*`（预留）

```json
[
  { "name": "sut.aux.cache.flush", "desc": "清被测缓存（前缀 / DB index）" },
  { "name": "sut.aux.mq.purge", "desc": "清空或跳过测试队列积压" },
  { "name": "sut.aux.mq.inject", "desc": "向队列注入测讯（慎用）" },
  { "name": "sut.aux.fs.reset", "desc": "重置上传目录 / 测试 bucket 前缀" },
  { "name": "sut.aux.session.revoke", "desc": "批量作废测试会话（若有后门）" }
]
```

说明：跨天单测常被 Redis/MQ/上传脏数据坑到；拨钟后往往要配合 `cache.flush`。

#### D. 进程与就绪 `sut.runtime.*`（预留）

```json
[
  { "name": "sut.runtime.health.wait", "desc": "等到被测健康检查通过" },
  { "name": "sut.runtime.restart", "desc": "重启被测进程/容器（高危）" },
  { "name": "sut.runtime.feature.set", "desc": "拨 feature flag / 动态配置" }
]
```

说明：常见 Prepare 管线：`reset` → `restart?` → `health.wait` → `clock.set`。

#### E. 人机与通知后门 `sut.hook.*`（预留 · 高危）

```json
[
  { "name": "sut.hook.captcha.bypass", "desc": "测试环境绕过图形验证码" },
  { "name": "sut.hook.otp.peek", "desc": "读取短信/邮箱验证码" },
  { "name": "sut.hook.mail.list", "desc": "测试邮箱收件列表" },
  { "name": "sut.hook.notify.mute", "desc": "静音真实短信/邮件" }
]
```

说明：须 `danger: secrets_read`（或更高）；默认项目禁用；审计强制。

#### F. 外联仿真实 `egress.*` / `ingress.*`

```json
[
  { "name": "egress.mock.rule", "desc": "挡板路由与响应模板", "core": "7.6 门面可在核心，渠道包插件化" },
  { "name": "egress.mock.record", "desc": "查询挡板收到的请求" },
  { "name": "egress.mock.fault", "desc": "延迟 / 5xx / 错包注入" },
  { "name": "egress.intercept.domain", "desc": "把指定外联域名解析/导流到挡板（hosts、DNS 或代理）", "status": "方向预留，不承诺实现" },
  { "name": "egress.intercept.tls", "desc": "为劫持域名签发/注入测试 CA，使 HTTPS SDK 校验证书能通过", "status": "方向预留；需被测信任该 CA" },
  { "name": "egress.intercept.proxy", "desc": "边车/透明代理：不改业务代码时劫持出站 443", "status": "方向预留；opsAgent" },
  { "name": "ingress.mailbox.query", "desc": "查 7.4 收件箱", "core": "落库偏核心" },
  { "name": "ingress.mailbox.reply_policy", "desc": "定制收件应答", "core": "避免核心写死 success" }
]
```

说明：

- 质衡 HTTP 节点「扮渠道打被测 notify」仍用核心节点（7.1/7.2/7.3），一般不必插件。  
- **挡板（`egress.mock.*`）≠ 流量会自动过来**：SDK 常把微信等域名写死在封装里，业务侧改不了 baseUrl。完整「仿微信支付」需要 **mock 门面 + 被测侧域名/TLS 拦截** 两半。  
- `egress.intercept.*` 拦的是**被测进程/容器所在网络的出站**，不是质衡云端去劫持公网微信；执行体多落在 `opsAgent`（hosts、边车、mitm），与 7.6 挡板组合启用。  
- HTTPS 几乎必做：仅改 DNS/hosts 不够，通常还要测试 CA + 被测 truststore；**证书锁定（pinning）的客户端 SDK 可能做不到**，插件须标明前提。  
- 先占能力名与产品方向；实现难度高，**不排期、不承诺**，但值得作为仿渠道插件的正确形态记下来。

#### G. 资产与领域包 `pack.*`（预留）

```json
[
  { "name": "pack.api.import", "desc": "入库适配（OpenAPI 等）", "note": "IDEA 插件仍是独立产品" },
  { "name": "pack.domain.prompts", "desc": "造流提示 / 场景说明包" },
  { "name": "pack.domain.scenarios", "desc": "场景目录与质衡侧元数据同步" },
  { "name": "pack.crypto.channel", "desc": "渠道签验开箱预设", "note": "在通用 7.1 之上，不替换 7.1" }
]
```

说明：避免商城文案与渠道细节渗进质衡核心。

#### H. 可观测（预留 · 低优先级）

```json
[
  { "name": "sut.obs.logs.tail", "desc": "拉取与本 Run 相关的被测日志片段" },
  { "name": "sut.obs.metrics.snapshot", "desc": "跑前/跑后打点对比" }
]
```

说明：先占名，有痛点再做。

#### I. 界面端（`ui` · 与运行端对等）

`manifest.ui` 两路可只写一路或两路都写：

- **A 补充现有节点** → `ui.nodes`（不新增 `FlowNodeType`）  
- **B 全新样式/表面** → `ui.tokens` / `chrome` / `timeline` / `report` / `env` / `palette`

```json
{
  "ui": {
    "tokens": {
      "--qt-flow-accent": "#b45309",
      "--qt-flow-canvas-dot": "#fde68a"
    },
    "chrome": {
      "banner": [
        {
          "id": "clock-banner",
          "slot": "top",
          "order": 10,
          "text": "虚拟时间 {{sut.clock.now}}",
          "tone": "warning"
        }
      ],
      "toolbar": [
        {
          "id": "clock-advance",
          "order": 20,
          "label": "快进",
          "requires": ["sut.clock.advance"],
          "action": "plugin:official.sut-clock/advance"
        }
      ]
    },
    "nodes": {
      "http": {
        "decorate": [
          {
            "id": "snapshot-badge",
            "when": "node.data.snapshotBefore === true",
            "badge": "快照",
            "tone": "info"
          }
        ],
        "panel": [
          {
            "id": "data-restore-hint",
            "order": 30,
            "title": "数据插件",
            "fields": [
              { "key": "showRestoreHint", "type": "boolean", "label": "显示还原提示" }
            ]
          }
        ]
      }
    },
    "timeline": {
      "steps": {
        "clock.set": {
          "icon": "clock",
          "title": "拨钟 {{detail.to}}",
          "tone": "warning"
        },
        "data.reset_to_backup": {
          "icon": "database",
          "title": "回金库 {{detail.backupId}}"
        }
      }
    },
    "report": {
      "blocks": [
        { "id": "clock-trace", "order": 50, "title": "拨钟轨迹", "source": "audit:sut.clock.*" }
      ]
    },
    "env": {
      "badge": { "text": "拨钟", "tone": "warning" }
    },
    "palette": {
      "extras": []
    }
  }
}
```

说明：

- `tokens`（模式 B）：测流作用域令牌；键须在核心白名单；可「完全另一套」观感，**不等于**新节点类型。  
- `chrome.banner` / `toolbar`（B）：页级横幅与工具条；`toolbar[].requires` 必须对应运行能力。  
- `nodes.<type>.decorate` / `panel`（模式 A）：只补充已有 `http`/`script`/…；**不改**执行语义与 `FlowNodeType`。  
- `timeline.steps`（B）：插件审计步外观；key 与审计动作名对齐。  
- `report.blocks` / `env.badge`（B）：报告块与环境徽章。  
- `palette.extras`（B，后置）：快捷动作，**不是**可拖拽新引擎节点（真新类型走 ROADMAP 5.1）。  
- 根上 `capabilities` 应声明用到的 `ui.flow.*`（如 `ui.flow.node.decorate`、`ui.flow.chrome.banner`），便于门禁与发现。  
- A =「节点多了什么」，B =「环境/Run/领域包多了什么」；仅 A / 仅 B / A+B 均合法；官方 `sut.*` 至少提供其一。  
- 作用域仅测流编辑 / Run / 报告；多插件同槽按 `order` 或 `conflicts`；无插件时 UI 与今日一致。

### 3.5 Isolation 标签

```json
[
  { "tag": "sut-db", "effect": "写被测库 → 环境串行；通常只允许一个默认 Prepare 清库提供者" },
  { "tag": "sut-clock", "effect": "改业务时钟 → 与另一业务钟提供者互斥" },
  { "tag": "sut-clock-os", "effect": "改 OS/容器墙钟 → 与依赖真 cron 的 ops 警告/冲突" },
  { "tag": "sut-cache", "effect": "旁路缓存；可与 db 并存，写操作仍建议串行" },
  { "tag": "sut-mq", "effect": "旁路消息队列" },
  { "tag": "sut-fs", "effect": "旁路上传/文件" },
  { "tag": "sut-process", "effect": "重启被测；Prepare 末尾后须 health.wait" },
  { "tag": "egress-mock", "effect": "挡板 path 占用冲突检测" },
  { "tag": "egress-intercept", "effect": "被测侧域名/代理劫持；须 ops-agent；与真实出网策略冲突时警告" },
  { "tag": "ops-agent", "effect": "依赖侧车/演示机代理" },
  { "tag": "secrets-read", "effect": "可读验证码等 → 权限与审计升级" },
  { "tag": "ui-theme", "effect": "测流主主题；同环境通常只启用一个，装饰可叠" },
  { "tag": "ui-slot:<name>", "effect": "独占某 UI 槽；抢同一槽则拒绝或要显式优先级" }
]
```

说明：写在 manifest 的 `isolation[]`；启用时与 `conflicts` 一起做检测，并驱动环境是否串行跑流。

### 3.6 分发形态：一个压缩包（推荐）

既然可含代码，**对外分发的插件应打成单一压缩包**（建议 `.zip`；checksum + 可选签名），避免「JSON 一份、JAR 一份、脚本又一份」对不齐版本。

```json
{
  "artifact": "official.sut-clock-1.0.0.zip",
  "layout": {
    "qualitest.plugin.json": "必选，包根目录",
    "runtime/": "可选，执行体（按 kind 解释）",
    "ui/": "可选，受控前端贡献（声明式够用时可空）",
    "assets/": "可选，图标、说明静态资源",
    "SHA256SUMS": "可选但推荐"
  }
}
```

说明：

- **包内永远有**根目录 `qualitest.plugin.json`；`id`/`version` 与文件名建议一致。  
- **`runtime/`** = 运行端载荷；**`ui/`** = 界面端载荷（声明式够用时可空，只靠 manifest 里的 `ui` JSON）。  
- 安装/启用：校验 checksum → 读 manifest → 冲突检测 → 写入环境 composition；不是解压完就任意加载执行。

**两端各自怎么发出去（重点）**：

```text
同一 zip
  ├─ qualitest.plugin.json     → 质衡 app 入库（扩展库）
  ├─ ui/                       → 界面端：由质衡 Web 下发到浏览器
  └─ runtime/                  → 运行端：按 kind 留在 app 或下发到被测旁
```

| 端 | 分发路径 | 说明 |
| --- | --- | --- |
| **界面端** | zip → 质衡 **插件库（app 侧存储）** → 浏览器打开测流时，Web 按项目/环境已启用列表拉取 `ui` JSON（及可选 `ui/` 模块） | 用户**不用**单独装前端包；跟登录质衡同一站点走。静态资源建议经 app 反代，如 `/prod-api/plugins/{id}/{version}/ui/...`，带权限。第一期多数只需下发 JSON，无独立 JS |
| **运行端 · builtin** | 已在质衡 **app** 发行物内；扩展目录里点启用即可 | 用户不必下 zip；开发者仍可按 zip 布局维护源码 |
| **运行端 · httpAdapter** | 配置与模板进插件库；执行仍在 app 出站 | 薄包或内置 |
| **运行端 · opsAgent** | zip 内 `runtime/` **另装到被测机/演示机**（或 compose 边车）；app 只保存「agent 地址 + 密钥」 | 这是用户会感觉「还要装一端」的部分；UI 上应写清「需在被测环境安装运行端代理」 |

```json
{
  "distribute": {
    "console": {
      "channel": "qualitest-web",
      "whoInstalls": "无人单独装；启用插件后浏览器自动拉",
      "payload": ["manifest.ui", "optional zip:ui/"]
    },
    "runtimeBuiltin": {
      "channel": "qualitest-app image/jar",
      "whoInstalls": "升级质衡即可",
      "payload": ["Java module keyed by plugin id"]
    },
    "runtimeOpsAgent": {
      "channel": "same zip or agent-only extract",
      "whoInstalls": "被测/演示机管理员",
      "payload": ["zip:runtime/", "agent listen URL registered in env config"]
    }
  }
}
```

说明：界面端**不**走 npm/独立 CDN 商店；跟质衡会话同源，避免跨域与假包。运行端 opsAgent 与界面端生命周期可以不同（先启 agent 再启用插件，或反过来在 UI 上红灯提示「运行端未连通」）。

**和 `runtime.kind` 的关系（不必每个官方功能都逼用户下 zip）**：

| 种类 | 用户是否必须下 zip | 说明 |
| --- | --- | --- |
| `builtin` | **通常不必** | 运行端已在 app；界面端随启用下发 JSON |
| `httpAdapter` | 可以是薄 zip | 或直接内置 |
| `opsAgent` | **建议独立 zip（或从全量 zip 只解 runtime/）** | 运行端装被测旁；界面端仍只走质衡 Web |
| 私有化自有扩展（后置） | **必须 zip + 签名** | 允许列表校验后再用 |

一句话：**一个插件、一个 zip、两个端名（运行端 / 界面端）**；界面端由质衡 Web 分发，运行端按 kind 留在 app 或装到被测旁，不要拆成两个市场商品。

---

## 4. 生命周期挂点

```json
{
  "hookSequence": [
    "onRunPrepare",
    "onCheckpoint",
    "onRestore",
    "onRunFinalize"
  ],
  "prepareDefaultActions": [
    "data.reset_or_scenario",
    "aux.flush",
    "runtime.restart?",
    "runtime.health.wait",
    "clock.set"
  ],
  "finalizeDefaultActions": ["clock.reset"]
}
```

说明：

- `hookSequence`：`hooks[].name` 允许取值；跑流时按此阶段推进。  
- `prepareDefaultActions`：`onRunPrepare` 默认动作序；环境 `prepareOrder` 可覆盖。  
- `finalizeDefaultActions`：收尾建议只 `clock.reset` 等可逆项，**不要**默认把跑后库写回金库真相源。  
- 失败策略：`fail_run` / `skip_and_warn`（须审计可见）。  
- `destructive` / `secrets_read` 必须过环境总闸（泛化自 `allowDestructiveReset`）。  
- 声明写隔离标签的环境保持 Run **串行**。  
- 审计步外观由 `ui.timeline.steps` 声明；不冒充业务 HTTP 节点。  
- 画布意图与实现解耦：无对应 capability 则失败或跳过。  
- 打开测流合并已启用插件的 `ui`；切换环境则令牌/横幅随 composition 变。

```text
Run 创建 → onRunPrepare → 跑图（snapshotBefore→onCheckpoint；还原→onRestore）→ onRunFinalize → 终态
```

---

## 5. 与现有机制的关系

| 现有 | 插件化方向 |
| --- | --- |
| `snapshotBefore` + `HttpEndpointSnapshotAdapter` | 画布保留意图；执行委托 `sut.data.checkpoint/restore`；徽标/面板走 `ui.flow.node.*` |
| `allowDestructiveReset` | 升为破坏性插件总闸；UI 用 `env.badge` / `chrome.banner` 明示 |
| demo-host 金库 / daily-reset | 契约归 `sut.data.backup` / `reset_to_backup`；文件可仍在运维机 |
| 靶场 `scenario/load` | 今日测流手写 HTTP → 宜 `sut.data.scenario.*` + 侧栏场景选择面板 |
| 前端 `nodeRegistry` / 节点样式 | 核心保留 8 类注册表；插件只 **decorate / panel**，不直接改 Registry 源码 |
| Delay / `retryUntil` | 留核心；跨天过期用 `sut.clock.*` + 顶栏虚拟时间 |
| 7.4 Webhook 门面 | 入站与落库偏核心；应答策略可 `ingress.mailbox.reply_policy` |
| 7.6 挡板 | 门面 + 记录偏核心；渠道/模板包走 `egress.mock.*`；SDK 写死域名时叠加预留的 `egress.intercept.*`（opsAgent） |
| 7.1 脚本加密 | 通用工具留核心；渠道开箱预设可 `pack.crypto.channel` |
| 质衡 `qualitest.storage` | **不是**被测插件；属平台自身素材存储 |
| Script `ctx` / MCP | 后期只读暴露已启用插件安全子集；写配置走原有权限 |

---

## 6. 安全与多租户

1. 作用对象仅限环境配置的被测 / 代理；禁止「改质衡系统时间 / 清质衡库」类 API。  
2. 安装与改危险配置 ≥ 环境管理权；跑流触发 hook 不突破总闸。  
3. DB/SSH/备份路径等进环境密钥，不进流程图。  
4. 强制审计：拨钟、还原、窥 OTP、重启、挡板改规则。  
5. 启用时做 `conflicts` + `isolation` 检测（§7）。  
6. 第一期：官方内置或随版本附带；远程市场后置。

---

## 7. 兼容与共存

### 7.1 通用规则

| 规则 | 说明 |
| --- | --- |
| 能力正交则可并存 | 例：`sut.clock.read` + `sut.data.checkpoint` |
| 同隔离写冲突则互斥 | 两个默认 `reset_to_backup` 提供者 → 启用拒绝 |
| 管线用阶段排序，不靠互相调用私协议 | Prepare 固定序（§4）；`requires` 只表达硬依赖 |
| 环境串行 | 任一写库/写钟/重启/清缓存影响共享被测 → 串行 |
| 意图无实现则显式失败或警告 | 禁止静默 no-op 到让人以为已还原 |
| 金库真相源默认只读于跑流 | `backup` 写入须显式人操或独立任务，防演示金库被跑流污染 |
| 运行端与界面端宜成对 | 有 `sut.*` 无任何 `ui.flow.*` → 启用时警告（官方插件应避免） |
| UI 主主题互斥、装饰可叠 | 两个 `ui-theme` 冲突；多个 `node.decorate` 可叠加 |

### 7.2 示意组合（不写具体插件设计）

**A. SQL 金库 × 被测时钟（常见）**

```json
{
  "plugins": [
    { "id": "official.sut-sql-backup", "enabled": true },
    { "id": "official.sut-clock", "enabled": true, "config": { "defaultAdvance": "P2D" } }
  ],
  "primaryDataProvider": "official.sut-sql-backup"
}
```

说明：`sut-db` + `sut-clock` 可并存；Prepare 先 reset 再 dial；Finalize 只 `clock.reset`。UI 上备份包常用 A（`nodes.http`）+ B（`env.badge`），时钟包以 B（`chrome.banner` + `timeline`）为主。

**B. 金库 × 场景加载（易踩坑）**

- 两者都能「清库再灌」→ 必须指定环境级 **primary data provider**  
- 推荐：日常回归用 `scenario.load`；灾难/日切用 `reset_to_backup`；同 Run 不要各执行一遍除非文档写明差分语义

**C. 时钟 × 旁路缓存**

- 拨钟后若权限/券状态缓存在 Redis → Prepare 在 `clock.set` 之后（或之前约定清楚）调用 `sut.aux.cache.flush`  
- 否则会出现「库已过期、缓存未过期」的假绿/假红

**D. 数据插件 × 挡板渠道包**

- 一般正交：一个动被测库，一个扮第三方  
- 冲突点在 **被测配置的外联 URL** 是否指向本环境挡板，属环境配置问题，不是 capability 互斥

**E. OS 级拨钟 × 定时任务类 ops**

- `sut-clock-os` 与依赖宿主机真实 cron 的任务 → 警告或 conflicts  
- 优先业务 MockTime，降低与证书、日志、外部支付的纠缠

**F. OTP 窥视 × 多租户共用被测**

- `secrets-read` + 共享环境 → 强烈建议独占环境或账号命名空间；否则冲突在「安全」而非「能力名」

**G. 两个领域皮肤 × 同一测流**

- 两个 `ui.flow.tokens` 主主题 → `ui-theme` 冲突，启用拒绝或需指定 primary theme  
- 装饰类（角标、时间线图标）不占 `ui-theme`，可与单一主题并存

**H. 仅界面端、无运行端**

- 允许：纯 `pack.domain` + `ui.flow.tokens` 的「教学皮肤 / 品牌测流」（模式 B）  
- 不得伪造具备 `sut.data.*` 的操作按钮；工具条动作必须 `requires` 对应运行能力

**I. 模式 A × 模式 B（补充节点 vs 全新表面）**

- 可并存：例如数据插件 = A（HTTP 角标 + panel）+ B（环境徽章）；时钟插件 = B（顶栏）为主、A 为辅  
- 两个插件都对同一 `node.panel.http` 扩展 → `order` 排列或槽位冲突规则，与主题互斥分开处理  
- 「完全新皮肤」（B tokens）不排斥继续对旧节点做 A 装饰；卸掉插件后 A/B 贡献一并消失，画布回到核心默认

**J. 仿微信支付：挡板 × 域名拦截（方向预留）**

```json
{
  "plugins": [
    { "id": "official.egress-mock-wechat", "enabled": true },
    {
      "id": "official.egress-intercept",
      "enabled": true,
      "config": {
        "domains": ["api.mch.weixin.qq.com", "api.weixin.qq.com"],
        "mode": "hosts+testCa",
        "note": "须被测信任测试 CA；pinning 场景可能失败"
      }
    }
  ],
  "requiresIsolation": ["egress-mock", "ops-agent"]
}
```

说明：测「被测主动调微信」且 SDK 写死域名时用；测「微信回调被测」只需核心 HTTP 扮渠道，不必 intercept。能力名已占，实现另议。

### 7.3 能力名稳定性

1. 新增能力只**追加**，不改已发布名字的语义。  
2. 废弃用 `deprecated` + 迁移说明，保留别名至少一个大版本。  
3. 官方插件与第三方插件都只通过能力名协作，禁止「写死依赖某插件 id」作为唯一扩展方式（`requires` 可写 id，但能力名优先）。

---

## 8. 演进阶段

| 阶段 | 目标 |
| --- | --- |
| **P0 契约** | 敲定 `qualitest.plugin.json`、环境 composition、**zip 包布局**、能力名、Hook、冲突、`ui` 两模式 |
| **P1 收编** | 现有 snapshot/restore → 官方数据插件 + 最小节点徽标/时间线样式；行为对外兼容 |
| **P2 样本** | SQL 备份还原 + 被测时钟（运行 + 顶栏/审计 UI）；验证 §7.2-A；至少 1 个可导出的示范 zip |
| **P3 对齐 7.x** | 挡板渠道包 / 收件应答 + 对应报告块/面板 |
| **P4 开放** | 贡献槽稳定、权限、MCP 只读列表、可选 Script API |
| **P5 扩展目录（克制）** | 官方内置启用 + 可选上传/导入签名 zip（私有化）；不做公网海量商店 |

旁路状态、OTP、进程重启等：**先占能力名，实现按痛点插入**，不必阻塞 P0–P2。  
界面端与运行端 **同版本契约**；避免先只做运行端、界面端长期写死在主仓。

**扩展怎么进用户手里**：长期只做克制的官方扩展目录（内置启用、版本随质衡走、权限与冲突在启用时校验）。私有化若要自有扩展，再单开「签名包 + 允许列表」，仍不是公开应用商店。

---

## 9. 非目标

- 用 Delay 加速冒充被测过期。  
- 在插件里改质衡服务器时间。  
- 第一期自定义节点类型。  
- 公网海量第三方插件市场 / 任意热装。  
- 插件契约使用 YAML（只认 JSON）。  
- 代码与 manifest 分多处散落、无统一包格式（应对齐为单一 zip 布局）。  
- 插件任意全局 CSS / 劫持路由替换整个 SPA。  
- 把 `qualitest-demo-host` 整仓并进质衡；只吸收契约与调用方式。  
- 把质衡自身对象存储、Flyway、成员权限做成「被测插件」。

---

## 10. 验收直觉

1. 新环境默认零插件，跑流行为与测流外观与今日一致。  
2. 启用官方数据插件后，原快照/还原路径仍可用；画布上能看见对应徽标/审计样式。  
3. 金库 + 拨钟同开时，Prepare 顺序稳定，顶栏能读到虚拟时间，可复现「回基线 → T+N → 断言」。  
4. 场景加载与金库同环境时，有明确 primary，不会双清库。  
5. 未启用对应能力时，危险意图不会静默落到错误系统；工具条也不出现假按钮。  
6. 换一套 `ui.flow.tokens` 只影响测流作用域，登录页/系统管理不受影响。  
7. 新增官方能力时，追加 §3.3 名字 +（若可见）UI 槽贡献即可，不必改跑流核心语义。

---

## 11. 相关链接

- ROADMAP Exploring **6.2**；Later **7.4–7.6**、**5.5**  
- 写库快照：`project-summary.md` §4.4；`HttpEndpointSnapshotAdapter`  
- 靶场场景与 test-support：`qualitest-demo-mall` README / `test-support-starter`  
- 演示金库：`qualitest-demo-host` `data/golden`、GUIDE  
