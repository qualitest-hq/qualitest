# 质衡平台插件（草案）

> **状态**：设计草案 · ROADMAP Exploring 6.2（**不承诺版本**；契约须自洽，实现按本文执行）。  
> **不含**：JetBrains Helper、Cursor MCP（接入通道 ≠ 平台插件）。  
> **已定结论**：§9 · **实现对照**：§10（启用/调度/失败须有唯一答案）。

**本文定四件事**：什么进插件、能力名叫什么、多个插件怎么共存、平台对插件开放多少。

**怎么读**：§1 边界（含**启用 vs Invoke**、被测特殊面→插件）→ §2 形态（必读，含示例）→ §3 能力归属（**无开放名录**）→ §4 隔离与生命周期 → 实现时必读 **§10**。

### 规范用语

| 用语 | 含义 |
| --- | --- |
| **必须** | 违反则拒启用、图校验失败或跑流失败（见原因码 §4.2 / §10） |
| **禁止** | 实现不得提供该路径；出现则拒或视为契约错误 |
| **默认** | 未另声明时的唯一行为；覆盖须在本文显式允许 |
| **后置** | 本期不实现；占名或方向，不得当作 P0 范围 |

本文不定完整 Java/HTTP 方法签名，但**必须**定清：字段约束、校验时机、路由规则、失败语义、阶段映射。细节算法以 **§10** 为准；§1～§8 与 §10 冲突时，**以 §10 为准**并回改正文。

---

## 1. 边界：核心 vs 插件

今天测流引擎、鉴权、报告已在核心；拨钟、库存档点、场景加载、挡板渠道等散落在核心边角、运维仓或手写 HTTP。再不划界，每加一种靶场运维或测流皮肤都会渗进主仓，以后难兼容。插件一旦能干活，界面**必须**能表达（顶栏、徽章、贡献节点、报告块），否则视为契约不完整（启用时可警告；P0 官方包必须至少一种 B 类可见贡献，见 §7）。

### 判定口诀

| 问 | 是 → | 否 → |
| --- | --- | --- |
| 改被测数据 / 时钟 / 旁路状态？ | 插件 · 运行端 | 下一问 |
| 改测流编辑/运行/报告呈现（随插件装卸）？ | 插件 · 界面端 | 下一问 |
| 强绑某一被测 / 云厂商 / 支付渠道细节？ | 插件 | 下一问 |
| 所有项目都要，且与引擎语义强耦合？ | **核心** | 插件或项目配置 |

| 归核心 | 归插件 |
| --- | --- |
| 项目/环境/权限；核心节点执行语义；画布/时间线/报告壳；`pluginAction` 引擎类型；跑流**挂点何时触发**（如 `snapshotBefore`、续跑要不要还原） | **凡需被测特殊配合才能干的事**（接 test-support、拨钟门面、库存档点 CLI、场景种子…）的**执行**；测流主题与 UI 贡献；`ops-agent` 载荷 |
| MCP/AI 骨架、出站 HTTP、Webhook 门面、串行调度、质衡自身存储 | 渠道签验等（有插件实现再做，**不**先占质衡开放能力名） |

**核心只给架子**：宿主挂点、按 **pluginId** 调用、总闸、串行、冲突、审计、UI 槽、`pluginAction`。  
**禁止**在核心内置「怎么打 `/test-support/*`」「怎么 dump/restore 被测库」「微信退款怎么回包」等被测细节。

#### 铁律：被测要特殊处理 → 必须插件；能力归插件，质衡不开放业务能力名

| 判断 | 结论 |
| --- | --- |
| 被测必须接专用 HTTP / agent / 脚本 / 本机 dump 目录才干得成？ | **必须**做成插件；执行不得留在核心 |
| 插件说明书里的 `capabilities` | **该插件私有**；质衡**禁止**维护「开放能力名录」供任意包认领 |
| 质衡开放的是什么 | 宿主挂点名（`onCheckpoint` / `onRestore` / …）、Invoker、总闸、isolation——**不是** `save.create` / `clock.set` 这类业务能力名 |

今日主仓直调 test-support 的 snapshot/restore：**必须**拆进存档点插件。拆出去之后：

| | 核心 | 插件（如库存档点 / 拨钟） |
| --- | --- | --- |
| 留下 | 挂点何时触发、画布意图、总闸 | 私有能力 + 被测旁 **CLI**（或已有 HTTP） |
| 调用 | `Invoker.invoke(pluginId, capability, …)` | CLI / routes / builtin |
| 禁止 | 核心内置连库 dump、拨钟细节；开放业务能力名录 | 未启用却暗门回落；无故常驻守护难卸载 |

未启用对应插件却配置了依赖该能力的意图 → 失败，**禁止**当核心功能继续用。  
跑中即时备份/恢复 = 本插件职责；**不要**与 demo-host 运维「金库」`data/golden` 混谈。

### 开放原则：默认拒绝，具名开放（架子，不是业务能力）

- **界面端 / 运行端**：**必须**只走平台调用面（Invoker、UiRegistry）；平台未开放的 Host API **禁止**调用。  
- **`capabilities[]`**：插件自声明的**私有**运行能力；挂上/摘掉仅在启用/停用时发生；**禁止**写成质衡全局开放名录的条目。  
- **调用方向**：挂点 / 贡献节点 / 工具条 → `Invoker.invoke(pluginId, capability, …)` → 该包实现 → 按 `runtime.kind` 分发（§10.4）。插件是**被调用方**，**禁止**反向掏质衡内部服务。  
- **界面贡献**：**必须**写在 `ui`；启用时 **UiRegistry** 登记；**禁止**经 Invoker；**禁止**写入 `capabilities[]`。  
- Host/Context 白名单见 §10.5；**禁止**直访 Spring 容器 / 任意 DataSource / 质衡系统库。  
- **`builtin`**：实现可在主仓；仍**必须**经 Invoker（带 `pluginId`）；注册表无实现 → 拒启用。  
- **被测端**：装上后 OS 行为不在平台沙箱内；`ops-agent` 启用/invoke 前**必须**连通检查。  
- **互斥**：同环境靠 `isolation` / `conflicts`；**不**靠「全局能力名唯一提供者」那套开放名录模型。

官方 `builtin` = **虚包说明书 + 主仓实现**（§2.1）。**禁止**照抄 DSH「一切皆插件 / 无特权内核」。

### 启用 vs Invoke（必读）

这两个词后文会反复出现，指的是**两个不同时机**，不要混成「开了插件就等于干了活」。

```text
插件说明书已入库（或官方虚包已随版本带上）
        │
        ▼
【启用 Enable】在「某个测试环境」里打开该插件、填 config、保存
        │  · 人操作一次（或导入环境配置）
        │  · 平台跑 §10.3：冲突、总闸、isolation、primary…
        │  · 成功后：能力挂进路由表，ui 挂进 UiRegistry
        │  · 此时通常还没对被测发任何 snapshot/restore 请求
        ▼
环境里插件处于「已启用」——顶栏/徽章可以出现，调色板可以拖节点
        │
        │  之后某次跑流 / 点工具条 / 跑到图上的 pluginAction
        ▼
【Invoke】平台按能力名真正调用一次实现去干活
        │  · 可发生很多次（每次 Prepare、每个节点、每次点按钮）
        │  · 入口：Invoker.invoke(能力名, 参数, …)（§10.4）
        │  · 再查总闸、再查 agent 连通、再按 kind 发 HTTP / 调 builtin / 支使 agent
        │  · 必须写审计时间线
        ▼
被测被改（或读到结果）/ 失败则 fail_run + 原因码
```

| | **启用 Enable** | **Invoke** |
| --- | --- | --- |
| **一句话** | 在这个环境里**允许用**这个插件 | **这一次**去执行某个能力 |
| **谁触发** | 人在环境配置里勾选/保存 | 跑流挂点、图节点、工具条等 |
| **发生次数** | 每个环境对每个插件：启用/停用，偏配置态 | 一次跑流里可以很多次 |
| **主要检查** | 说明书、冲突、config、**本包能力 gate→总闸**、agent bundle…（§10.3） | 该 `pluginId` 是否启用、本能力 gate、连通、执行结果（§10.4） |
| **成功之后** | 插件可被挂点/节点找到；**不等于**已经动过被测 | 对被测产生了一次真实副作用或读结果 |
| **失败像什么** | 插件开不了，表单报原因码 | 本次跑流中断（默认 fail_run）或节点失败 |
| **和总闸的关系** | 包内能力所需闸未开 → **拒启用** | 启用后若总闸被关掉再干活 → **拒 invoke**（再拦一次） |

对照例子（SQL 存档点 CLI，私有能力 `save.restore`，`gate=destructive`）：

1. 被测机已 `install` CLI 并写好 `{etc}/bind/{bindAlias}.json`；质衡**项目**挂上同名 `bindAlias`，环境启用并开破坏性总闸 → `ping` 成功（项目只能调自己的 bind 文件）。  
2. 挂点 `onCheckpoint` → `invoke(..., "save.create", { saveId })`（连接以**本地 bind**为准；存档按项目+环境分目录）；出问题或续跑 → `save.restore`。  
3. 不用该插件 → 环境停用 + 被测机 `uninstall.sh`，无常驻进程要杀。  
4. 中途关总闸 → 后续 **restore** Invoke **必须** `GATE_DENIED`。

**停用**是启用的反操作：摘路由与 UI；图上残留 `pluginAction` **必须**按 §2.6 **即时重算为失效**（不改流程图持久字段），之后 Invoke 不到该提供方。

后文凡写「启用时…」「Invoke 时…」，均按本小节理解。

### 刻意不做

- 无限膨胀引擎节点枚举（画布步骤 → **`pluginAction` + 贡献外观**）  
- 任意全局 CSS / 整页替换；改质衡自身 DB / JVM 钟 / 多租户  
- 公网任意 JAR 热装商店；用插件绕开未开放的引擎能力  
- DSH 式无特权内核  

### 命名风格（契约字符串）

| 类别 | 规则 | 示例 |
| --- | --- | --- |
| 枚举 / isolation / kind / 能力 `gate` | **必须** kebab-case | `ops-agent`、`http-adapter`、`destructive` |
| 插件内能力名 | **必须** `^[a-z][a-z0-9.-]*$`；**私有于该 pluginId** | `save.create`、`clock.set` |
| 插件 id | **必须** `^[a-z][a-z0-9.-]*$`，建议 `qualitest.*` / 厂商前缀 | `qualitest.sut-db-save-point` |
| 贡献短 id | **必须** `^[a-z][a-z0-9-]*$`，**禁止**含 `/` | `clock-advance` |

---

## 2. 一个插件长什么样（必读）

### 2.1 人话：最多三端，仍是一份说明书

**一个插件 = 一份说明书**（`qualitest.plugin.jsonc`，JSONC：注释 + 尾逗号，键加引号）。分发形态：

| 形态 | 规则 |
| --- | --- |
| zip 包 | 根目录**必须**含说明书；可选 `runtime/`、`agent/`、`ui/`、`assets/`、`SHA256SUMS` |
| 官方虚包 | 发行物内嵌说明书；无独立 zip；`kind=builtin` 时主仓**必须**有对应实现注册 |

**禁止**把同一插件拆成多个市场商品 id。按「装到哪」最多三端：

| 端 | 正式名 | 装到哪 | 包内目录 | 用户感知 |
| --- | --- | --- | --- | --- |
| **看得见** | 界面端 Console | 质衡 Web | 说明书 `ui` + 可选 `ui/` | 顶栏、徽章、调色板节点 |
| **质衡侧调度** | 运行端 Runtime | 质衡 app | 说明书 `runtime` / `hooks` | 跑流时平台知道何时调谁 |
| **被测旁落地** | 被测端 Agent | 被测机 / 演示机 | **`agent/`（默认 CLI）** | 库存档点、拨钟、改 hosts 等 |

硬约束：

- 被测端**必须**使用与说明书相同的插件 `id`。  
- `runtime.kind`：**必须**为 `http-adapter` \| `builtin` \| `ops-agent`（§2.3.1）。  
- `kind=ops-agent`：**必须**有 `runtime.agent`；**默认形态 = CLI**（§2.3.2），装得上卸得下。  
- `http-adapter` / `builtin`：**禁止**强依赖 `agent/` 才能启用。  
- 环境侧自定义（库连接、storeRoot、钟模式等）：进环境插件绑定的 **`config`**，由说明书 **`configSchema`** 校验；**禁止**写死在流程图。ops-agent 存档类数据 **必须**按 `projectId`/`envId` 分目录（CLI 二进制不必按项目各装一份）。

**完整** = 契约合法且启用后能按能力执行；**本期先定文档契约，实现样本后置**。

### 2.2 目录样例（ops-agent + CLI）

```text
qualitest.sut-db-save-point-1.0.0.zip
├─ qualitest.plugin.jsonc                  ← 必须（JSONC）
├─ agent/                                  ← ops-agent 必须
│   ├─ install.sh / uninstall.sh           ← 安装到 PATH 或固定目录；卸载删干净
│   ├─ qt-plugin                           ← CLI 入口（名可自定，说明书声明）
│   └─ install.md
├─ ui/                                     ← 可选
├─ assets/
└─ SHA256SUMS
```

### 2.3 说明书字段 + 运行落点

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `id` / `version` / `displayName` | 必须 | 身份；`version` 为 semver 字符串 |
| `remarks` | 可选 | 给人看的说明；**禁止**参与启用/Invoke 判定 |
| `capabilities` | 必须（可 `[]`） | **本包私有**能力对象数组，见下；**禁止** `ui.*`；**不是**质衡开放名录 |
| `requires` | 默认 `[]` | 仅 `plugin:<id>`；**禁止** `gate.*`；**禁止**引用「全局能力名」 |
| `conflicts` | 默认 `[]` | 仅 `plugin:<id>`；主互斥靠 isolation |
| `isolation` | 默认 `[]` | 标签 ∈ §4.1 |
| `hooks` | 默认 `[]` | `actions[]` **必须**为本包 `capabilities[].name` |
| `configSchema` | 可选 | JSON Schema；环境 `config` 须通过才能启用 |
| `ui` | 可选 | 见 §3.2 |
| `runtime` | 有运行能力时必须 | 见 §2.3.1 |

**禁止**旧稿包级 `danger`。总闸看**本包能力对象上的 `gate`**。

#### `capabilities[]` 对象（插件私有）

```jsonc
{
  "name": "save.restore", // 包内唯一
  "effect": "write",         // read | write
  "gate": "destructive",     // 省略/"none" | destructive | secrets-read
  "preparePhase": "data",    // 仅 onRunPrepare；省略 = 禁止进 Prepare
  "dbCompete": true
}
```

| `gate` | 启用时 | Invoke 时 |
| --- | --- | --- |
| 无 / `none` | 无额外总闸 | 无额外总闸 |
| `destructive` | 环境 `gate.destructive`（= `allowDestructiveReset=1`）须开 | 本能力再查一次 |
| `secrets-read` | 环境 `gate.secrets-read` 须开（默认关） | 本能力再查一次 |

1. **启用**：本包能力 `gate` 并集均须已开。  
2. **Invoke**：只查本次能力的 `gate`。  
3. `remarks` 不影响开闸。  
4. 高危 gate 的 Invoke **必须**审计。

### 2.3.1 `runtime.kind`

| `kind` | 何时用 | 质衡行为 | 被测旁 |
| --- | --- | --- | --- |
| `http-adapter` | 被测**已有**稳定 HTTP 门面 | 按 `runtime.http.routes` 出站 | 无 `agent/` 前提 |
| `builtin` | 必须与跑流引擎进程内耦合 | 主仓按 `pluginId` 调实现 | 无 `agent/` 前提 |
| `ops-agent` | 须碰 OS / 库 / 容器 / 本机文件 | 调被测旁 **CLI（默认）** | **必须**有 `agent/` |

选型口诀：已有 HTTP → `http-adapter`；强绑引擎 → `builtin`；否则碰被测机 → **`ops-agent` + CLI**。  
**禁止**第三方 JAR 热装进质衡进程。  
**禁止**为「方便」默认起常驻 HTTP 守护；能 CLI 一次做完的，不要常驻进程（卸载麻烦）。

### 2.3.2 被测端默认形态：CLI（装 / 卸）

`ops-agent` **默认**交付为命令行工具，而不是常驻服务：

| 要求 | 说明 |
| --- | --- |
| 安装 | `agent/install.sh`（或文档步骤）把 CLI 放到约定路径 / PATH |
| 卸载 | `agent/uninstall.sh` **必须**能删干净二进制与本插件配置目录，不留守护进程 |
| 调用 | 质衡 Invoke → 在被测机执行：`qt-plugin <capability> --config <env下发的配置文件>`（命令名以说明书为准） |
| 配置 | DB 连接、storeRoot、钟文件路径等走环境插件 **`config`**（`configSchema`）；Invoke 时写入临时/约定路径并注入 `projectId`/`envId` 再传给 CLI；**密钥不进流程图** |
| 探活 | 启用/Invoke 前跑 `qt-plugin ping`（或 `--help` 约定）；失败 → `AGENT_UNREACHABLE` |
| 非默认 | 仅当被测**已经**提供 HTTP 且不愿装 CLI 时，改用 `http-adapter` |

说明书 `runtime.agent` 示意：

```jsonc
"runtime": {
  "kind": "ops-agent",
  "agent": {
    "bundle": "embedded",
    "cli": {
      "bin": "sdsp",   // install 后的命令名
      "pingArgs": ["ping"],
      // invoke：bin + capability 名 + --config <path> + 能力参数
    }
  }
}
```

### 2.3.3 文档示意（非实现承诺）

下列两包**仅说明书示意**，用于对齐契约；**可运行样本后置**。官方 ops CLI 样本语言定为 **Go**（单二进制）；仓库路径后置。

完整设计（边界、CLI、config、衔接）：

- [plugin-sut-db-save-point.md](./plugin-sut-db-save-point.md) — `qualitest.sut-db-save-point`
- [plugin-sut-sys-clock-dial.md](./plugin-sut-sys-clock-dial.md) — `qualitest.sut-sys-clock-dial`

#### 示例 A：被测库存档点（ops-agent + CLI）

测中即时备份 / 出问题或断点续跑时恢复——须碰被测 **SQL / Redis 等状态源**，故 CLI；同一 `saveId` 默认可**整包**存/按序恢复。测试流推荐 **`bind-file`**（`bindAlias` 如 `demo-mall.json`；多 store 的 `kind` 见 [plugin-sut-db-save-point.md](./plugin-sut-db-save-point.md)）。

```jsonc
{
  "id": "qualitest.sut-db-save-point",
  "version": "1.0.0",
  "displayName": "被测库存档点",
  "remarks": "CLI+本地 bind；targets 可含 mysql/redis 等。断点默认整包 saveId。restore 需破坏性总闸。跨 store 不原子。",
  "capabilities": [
    { "name": "list", "effect": "read", "gate": "none" },
    { "name": "save.create", "effect": "write", "gate": "none", "preparePhase": "data" },
    { "name": "save.restore", "effect": "write", "gate": "destructive", "dbCompete": true },
    { "name": "save.delete", "effect": "write", "gate": "none" }
  ],
  "isolation": ["sut-db", "ui-slot:flow.badge.db-save-point"],
  "hooks": [
    { "name": "onCheckpoint", "order": 50, "actions": ["save.create"] },
    { "name": "onRestore", "order": 50, "actions": ["save.restore"] }
  ],
  "configSchema": {
    // 完整字段表见 plugin-sut-db-save-point.md §3
    "type": "object",
    "properties": {
      "storeRoot": { "type": "string", "description": "可选；覆盖 {var}，禁止改 bind 根" },
      "defaultSaveId": { "type": "string" },
      "db": {
        "type": "object",
        "properties": {
          "resolve": { "type": "string", "enum": ["bind-file", "explicit"], "default": "bind-file" },
          "bindAlias": { "type": "string", "pattern": "^[a-z][a-z0-9-]{1,63}$" },
          "bindEnvKey": { "type": "string", "pattern": "^[a-z][a-z0-9-]{1,63}$" },
          "dbKey": { "type": "string", "pattern": "^[a-z][a-z0-9-]{0,63}$", "description": "默认库；多库时用" },
          "engine": { "type": "string" },
          "host": { "type": "string" },
          "port": { "type": "integer" },
          "database": { "type": "string" },
          "user": { "type": "string" },
          "password": { "type": "string" },
          "options": { "type": "object" }
        },
        "required": ["resolve", "bindAlias"]
      }
    },
    "required": ["db"]
  },
  "ui": {
    "env": { "badge": { "text": "存档点", "tone": "warning" } }
  },
  "runtime": {
    "kind": "ops-agent",
    "agent": {
      "bundle": "embedded",
      "cli": { "bin": "sdsp", "pingArgs": ["ping"] }
    }
  }
}
```

存档落盘 **必须** `{var}/{projectId}/{envId}/{saveId}/meta.json` + `{storeKey}/…`（见 [plugin-sut-db-save-point.md §3.5 / §4.3](./plugin-sut-db-save-point.md)）。

#### 示例 B：被测系统拨钟（ops-agent + CLI）

用 CLI **改被测机操作系统时间**（便于 Quartz 等跟系统钟的调度）；**仅靶场/演示机**。Finalize 按 `resetPolicy` 对时或恢复拨钟前快照。**不要**默认起常驻钟服务。详见 [plugin-sut-sys-clock-dial.md](./plugin-sut-sys-clock-dial.md)。

```jsonc
{
  "id": "qualitest.sut-sys-clock-dial",
  "version": "1.0.0",
  "displayName": "被测系统拨钟",
  "remarks": "CLI 改被测机系统时间（非生产）。写操作需破坏性总闸；复位建议 ntp 或 restore。短称：拨钟。",
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
      "resetPolicy": { "enum": ["ntp", "restore"], "description": "ntp=同步网络时间；restore=恢复拨钟前快照" },
      "defaultAdvance": { "type": "string", "description": "如 P2D" },
      "timezone": { "type": "string", "description": "展示/解释用，如 Asia/Shanghai" }
    },
    "required": ["resetPolicy"]
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

跑中 ephemeral checkpoint/restore（今日 test-support）：**迁入本存档点插件**（或同能力 http-adapter）；**禁止**再写成「运维金库」语义。  
`http-adapter`：若使用，每个 invoke 能力**必须**有 `routes`。`builtin`：**禁止**写 `entry`。

### 2.4 画布「新节点」：插件贡献节点

**禁止**为每插件新增 `FlowNodeType`。**必须**使用唯一引擎类型 `pluginAction` + 说明书 `ui.palette.nodes` 贡献多颗外观。

| 用法 | 适合 |
| --- | --- |
| 生命周期挂点 | 每次跑流默认准备/收尾 |
| 顶栏/工具条 | 临时触发，不改图 |
| `ui.palette.nodes` | 写入图的步骤 |

图节点**必须**具备（**落图/落库**的字段）：

```jsonc
{
  "type": "pluginAction",
  "pluginId": "qualitest.sut-sys-clock-dial",
  "contributionId": "clock-advance",
  "capability": "clock.advance",
  "config": { "by": "P2D" }
}
```

规则：

- `capability` **必须** ∈ 该 `pluginId` 说明书的 `capabilities[].name`。  
- 规范化：`canonicalContributionId = pluginId + "/" + contributionId`（说明书写短 id；图存储**必须**存 `pluginId` + `contributionId` 两字段）。  
- **可用 / 失效（启用态）即时算，禁止落图**：打开画布、切换环境、图校验、跑流时，按**当前环境**启用集 + 包登记 + 说明书推导（见 §2.7）。  
  - 插件未启用 / 未登记 / 贡献 id 未知 / 能力不在说明书 → UI **必须**标失效（可复用节点既有启用/禁用呈现）；跑流 **禁止**静默跳过（`NODE_CAPABILITY_UNAVAILABLE`）。  
  - **禁止**把该瞬时态写成节点上的持久 `enabled`/`disabled` 并随流程图保存；换环境后必须重算。  
- 核心节点若另有「用户手动跳过/禁用」字段，与上条正交：手动禁用是图配置；插件失效是环境投影。两者同时存在时：**任一不可用则不调度**。  
- P0：契约 + 校验；P2：至少 1 颗可拖且可执行样本。

### 2.5 环境组合（开哪些包）

环境组合是 **§2.7 环境绑定表的 API/编辑投影**，存在**项目/环境侧**，**禁止**写进插件 zip。

```jsonc
{
  "envId": 12,
  "plugins": [
    {
      "id": "qualitest.sut-db-save-point",
      "version": "1.0.0",
      "enabled": true,
      "config": {
        "db": { "resolve": "bind-file", "bindAlias": "demo-mall" }
      }
    },
    {
      "id": "qualitest.sut-sys-clock-dial",
      "version": "1.0.0",
      "enabled": true,
      "config": { "resetPolicy": "ntp", "defaultAdvance": "P2D", "timezone": "Asia/Shanghai" }
    }
  ]
  // ≥2 个 sut-db 写竞争插件时才要 primaryDataProvider
}
```

**禁止**环境级 `prepareOrder`。Prepare 阶段序与排序算法见 §4.2 / §10.4。  
跑流、顶栏、调色板**必须**只读取**当前环境**已启用组合。

`primaryDataProvider` 必填条件（精确定义见 §10.3）：启用集中「数据写竞争插件」数 ≥ 2。单数据插件或未构成竞争时**必须**允许省略。

### 2.6 包 JSON vs 平台落库（必须分清）

两套结构，**禁止**混成一张「插件表把说明书字段拆成列、又把环境启用写进 zip」。

| | **包契约 JSON** | **平台 SQL（运营态）** |
| --- | --- | --- |
| 载体 | `qualitest.plugin.jsonc`（zip 根或官方虚包） | 质衡库表（P0 实体如下；DDL 开干时 Flyway） |
| 谁写 | 插件作者 / 发行版内嵌 | 安装、环境配置、跑流 |
| 变不变 | 随 `id@version` 基本不变 | 启用、config、闸随环境变 |
| 典型内容 | capabilities / hooks / ui / runtime / remarks / configSchema | 包登记、环境启用行、闸、审计 |

#### 包契约（只在 JSON，不拆成环境列）

说明书字段以 §2.3 / §10.2 为准。入库时**必须**保留可校验的说明书快照（整包 JSON 或等价规范化文档）；**禁止**只存 `displayName` 而丢掉 `capabilities`/`hooks`。

#### 平台实体（逻辑表，不定列类型）

| 实体 | 回答什么 | 关键字段（语义） | 备注 |
| --- | --- | --- | --- |
| **包登记** | 平台里有哪些插件版本 | `plugin_id`、`version`、`source`=`builtin`\|`zip`、`manifest` 快照、`content_hash`、状态、入库时间 | 官方虚包随发版写入或启动同步；zip 上传写入 |
| **环境插件绑定** | 这个环境开了谁 | `env_id`、`plugin_id`、`version`、`enabled`、`config`（过 configSchema） | = §2.5 投影；**唯一**持久的「插件启用/停用」 |
| **环境总闸** | 允不允许破坏性操作 | `gate.destructive`（**1:1** 今日 `test_project_env.allow_destructive_reset`）；`gate.secrets-read` 仅预留 | 不进插件包 |
| **环境主键数据提供方** | 双清时听谁的 | `primary_data_provider`（可空） | 仅 §10.3 竞争时必填 |
| **跑流审计** | 谁在何时 invoke 了什么 | 时间线条目（capability、pluginId、source、结果、原因码） | 挂现有 Run/时间线，不另造插件审计库也可 |

**禁止落库 / 禁止落图**：

- 节点上的「因插件停用而失效」——**即时算**（§2.4）。  
- 已启用插件注册表、UiRegistry——进程内/缓存，由启用集派生，可重建。  
- `remarks`——在说明书快照里即可，**禁止**单独业务表。

#### 节点可用态算法（即时）

对图上每个 `pluginAction` 节点 N、当前环境 E：

1. 查 E 的环境插件绑定：存在 `plugin_id=N.pluginId` 且 `enabled=true` 且版本已包登记 → 否则 **失效**。  
2. 读该版本说明书：存在贡献 `contributionId`，且 `capability` ∈ 本包 `capabilities[].name` → 否则 **失效**。  
3. （可选展示）所需 `gate` 未开 → 可标「闸未开」告警；是否允许保存图不阻，**跑流/Invoke 仍按 §10.4 拒**。  
4. 若节点另有用户手动禁用 → **不可调度**（与 1～2 独立）。

切换环境或停用插件后：**必须**重算；**禁止**要求用户改图才能看到失效。

### 2.7 怎么发到用户手里（含被测端安装）

| 端 | 到用户 | 安装 |
| --- | --- | --- |
| 界面端 | zip 入库或官方虚包 → Web 下发说明书 `ui` | 无需单独装 |
| 运行端 builtin / http-adapter | 虚包或入库 | 启用即可（builtin **必须**已注册实现） |
| 被测端 | `agent/` CLI（install/uninstall） | **必须**显式安装；`ping` 失败 → 提示；不用则 **uninstall** |

启用（质衡侧）**必须**走 §10.3 全序校验；**禁止**「解压即执行」。  
私有化签名 / 允许列表：**后置**。界面资源**禁止**走 npm/独立 CDN。  

被测端安装通道：人工脚本、Compose 边车、控制台推送（后置）。MCP 协助安装：**后置 P4**；须确认、禁静默；不是第四端。  
MCP 与平台插件：**通道 ≠ 插件**。Web 改不了被测机磁盘；本机 Agent 可写 bind/装 CLI，质衡 MCP 只配项目/环境。优先**扩核心 Tool / 已有 Tool 参数**；插件可选贡献 `mcpTools[]`（私有于 pluginId、受项目开关约束）后置，禁止无闸全局乱挂。见 [plugin-sut-db-save-point.md §1.3](./plugin-sut-db-save-point.md)。

---

## 3. 能力归属与宿主挂点

### 3.0 质衡**不**做开放业务能力名录

| 是 | 否 |
| --- | --- |
| 宿主挂点：`onRunPrepare` / `onCheckpoint` / `onRestore` / `onRunFinalize` … | ~~`sut.data.checkpoint` 等写进质衡开放 API~~ |
| 插件在说明书里声明**私有** `capabilities[]` | ~~平台 §3.1 占名表，谁都能认领~~ |
| `Invoker.invoke(pluginId, name, …)` | ~~`invoke("sut.data.checkpoint")` 全局按名找提供方~~ |

因此：存档/读档 **拆进插件后**，它们只是该插件的 `capabilities[].name`（如 `save.create`），**禁止**再表述为「质衡开放能力」。

### 3.1 官方示例包（示意，非名录）

| 插件 id | 为何必须是插件 | 私有能力（示例） | kind / 被测旁 |
| --- | --- | --- | --- |
| `qualitest.sut-db-save-point` | 须连被测状态源（SQL/Redis 等）、碰宿主机；整包存档/断点读档；`kind` 多存储 | `list` / `save.create` / `save.restore` / `save.delete` | ops-agent + **CLI** |
| `qualitest.sut-sys-clock-dial` | 须改被测机**系统时间**（靶场） | `clock.*` | ops-agent + **CLI**（需改钟权限） |

说明书见 §2.3.3。**可运行实现后置**；与 demo-host 运维金库无关。

### 3.2 界面贡献（近期路径）

有 `ui` 块即登记；**禁止**要求再声明 `ui.flow.*` 能力名。

| 模式 | 写哪里 | 阶段 |
| --- | --- | --- |
| B | `ui.chrome` / `ui.timeline` / `ui.env` /（后）`ui.report` | P0：**必须**至少徽章或横幅一种可渲染（官方运行包） |
| C | `ui.palette.nodes` | 契约 P0；可拖执行 P2 |
| A | `ui.nodeDecorations` 等 | **后置** P3+ |
| 主题 | isolation `ui-theme`；独立 tokens **后置** | 后置 |

`palette.nodes[].capability` **必须**为运行能力且 ∈ 本包 `capabilities`。作用域**仅**测流编辑 / Run / 报告。

**UI 槽互斥**：`isolation` 中 `ui-slot:<名>` 为唯一冲突键（效应 `mutex`）。`banner.slot`（`top`\|`bottom`）只布局。若存在 `ui.chrome.banner` 且未声明任何 `ui-slot:*`，启用时**必须**自动加入 `ui-slot:flow.banner.{banner.id}`。

模板绑定：仅绑定**已启用插件**导出的只读投影（如时钟包的 `clock.now`）；规则见 §10.6。

---

## 4. 隔离、生命周期、安全

### 4.1 隔离标签（`isolation[]`）

未列标签 → `UNKNOWN_ISOLATION`。

| 标签 | 效应 | 启用时 | 跑流时 |
| --- | --- | --- | --- |
| `sut-db` | serialize + 竞争检测 | 参与 primary（§10.3） | 写操作串行 |
| `sut-sys-clock-dial` | mutex | 第二插件带此标签 → 拒启用 | — |
| `ops-agent` | 标记依赖 | kind 校验 | invoke 前**必须**连通 |
| `ui-theme` | mutex | 两主主题拒 | — |
| `ui-slot:<名>` | mutex | 同槽第二插件拒 | — |

`conflicts: ["plugin:<id>"]`：若环境已启用该 id → 拒启用。  
未知 isolation 标签 → 拒启用（`UNKNOWN_ISOLATION`）。

### 4.2 跑流挂点

```text
启动 → onRunPrepare → 跑图（快照→onCheckpoint；还原→onRestore）→ onRunFinalize → 终态
```

#### Prepare 阶段桶（平台写死，环境禁止改）

阶段 id 顺序：`data` → `aux` → `process` → `health` → `clock`。  
每个 Prepare action **必须**带该插件能力对象上的 `preparePhase`（对应上表）；缺省/null → `HOOK_ACTION_BAD_PHASE`。

**排序算法**（`onRunPrepare`）：

1. 收集已启用插件中 `hooks.name=onRunPrepare`，展开 `actions`。  
2. 解析为本包能力；取 `preparePhase`；非法则失败。  
3. 按阶段序号分桶 → 桶内 `order` → `pluginId`。  
4. 依次 `Invoker.invoke(pluginId, capability, …)`。

跑中快照：**必须**走宿主 `onCheckpoint` / `onRestore` → 快照插件私有能力；**禁止**塞进 Prepare。  
`onRunFinalize`：按 `order` + `pluginId`；时钟示例用 `clock.reset`；快照包默认无 Finalize 动作。

#### 失败策略与原因码

| 策略 | 何时 | 行为 |
| --- | --- | --- |
| `fail_run` | **默认**；P0 白名单为空集 | 中断跑流；时间线记失败；写审计 |
| `skip_and_warn` | **仅当**能力 ∈ 本文白名单 | 跳过该 action；时间线**必须**可见警告 |

**P0 `skip_and_warn` 白名单 = ∅**（一律 `fail_run`）。追加白名单**必须**改正文本表，禁止实现私加。

| 原因码 | 含义 |
| --- | --- |
| `MANIFEST_INVALID` | 说明书解析/字段/字符集失败 |
| `UNKNOWN_CAPABILITY` | 能力名 ∉ 该 pluginId 说明书 |
| `UNKNOWN_ISOLATION` | 隔离标签未知 |
| `REQUIRES_UNSATISFIED` | requires 未满足 |
| `ISOLATION_MUTEX` | isolation mutex 冲突 |
| `PLUGIN_CONFLICT` | conflicts 命中 |
| `PRIMARY_DATA_REQUIRED` | 需要 primary 未填或非法 |
| `GATE_DENIED` | 总闸未开 |
| `BUILTIN_IMPL_MISSING` | builtin 无注册实现 |
| `AGENT_BUNDLE_INVALID` | agent bundle/目录不合法 |
| `AGENT_UNREACHABLE` | 被测端未连通 |
| `CONFIG_SCHEMA_INVALID` | 环境 config 不符 schema |
| `HOOK_ACTION_NOT_OWNED` | hook action ∉ 本包 capabilities |
| `HOOK_ACTION_BAD_PHASE` | Prepare action 无 preparePhase |
| `PROVIDER_NOT_FOUND` | invoke 时无启用提供方 |
| `NODE_CAPABILITY_UNAVAILABLE` | 图节点能力不可用 |
| `INVOKE_FAILED` | 适配器/实现执行失败 |
| `HOST_API_DENIED` | 试图使用未开放 Host API |

无对应能力却配置了意图：**禁止**静默成功；**必须**失败或（仅白名单内）警告。

### 4.3 安全

1. **必须**只动环境配置的被测/代理；**禁止**改质衡系统时间/清质衡库。  
2. 安装与危险配置权限 ≥ 环境管理；挂点**禁止**绕过总闸。  
3. 密钥进环境 config，**禁止**进流程图。  
4. 破坏性能力（存档点 restore、拨钟写等）：**必须**审计。  
5. 启用**必须**执行 §10.3；未声明能力**禁止**当作已提供。  
6. 第一期官方虚包；远程市场**后置**。

---

## 5. 与现有机制

| 现有 | 插件化方向 |
| --- | --- |
| `snapshotBefore` / 续跑还原 | 意图留核心；**执行** → `qualitest.sut-db-save-point`（或 http-adapter 同能力） |
| `allowDestructiveReset` | **1:1** → `gate.destructive` |
| 拨钟 / 库存档点 | **插件 + 被测旁 CLI**（§2.3.3）；config 含库连接等；不进开放名录 |
| 节点注册表 | 核心节点 + **`pluginAction`**（P2） |
| Delay / retryUntil | 留核心 |
| Webhook / 挡板 | 入站偏核心；有插件实现再做 |
| Script / MCP | 后期只读安全子集；MCP 装 agent → P4 |

---

## 6. 共存规则与常见坑

| 规则 | 说明 |
| --- | --- |
| 正交可并存 | 不同能力名可同时启用 |
| isolation mutex | 启用时拒 |
| isolation serialize | 跑流写串行 |
| 阶段序写死 | 无环境 prepareOrder |
| 无开放能力名录 | 能力私有于 pluginId；gate 看本包能力对象；`remarks` 不参与判定 |
| 默认 fail_run | P0 无 skip 白名单 |
| 运行+界面 | 官方运行包缺 B 类 UI → 启用警告（P0 官方包必须有） |
| 能力名只追加 | 废弃留别名 ≥1 大版本 |

常见坑：

- **总闸关闭**：破坏性能力 → `GATE_DENIED`。  
- **无被测门面**：test-support / 钟面未接 → 插件 invoke 失败，不是核心兜底。  
- **两家 sut-db 写竞争**：须 `primaryDataProvider`。  
- **两主主题 / 两拨钟**：`ui-theme` / `sut-sys-clock-dial` mutex。

---

## 7. 演进 / 非目标 / 验收

| 阶段 | 目标 |
| --- | --- |
| P0 | **契约文档**自洽（JSONC、CLI agent、configSchema）；启用/挂点算法可对照 §10 |
| P1 | 主仓 Invoker 骨架 + 虚包启用；UI B 最小可见 |
| P2 | 可复制样本：库存档点 CLI + 拨钟 CLI（**Go**；仓后定）；≥1 可拖 `pluginAction` |
| P3 | 模式 A 后置；其它能力等质衡有实现再开目录行 |
| P4 | 权限、MCP 只读；MCP 协助装 agent |
| P5 | 签名包；不做公网海量商店 |

**非目标**：Delay 冒充过期；改质衡服务器时间；无限私增 `FlowNodeType`；公网任意热装；DSH 无特权内核；任意全局 CSS/换 SPA；整仓并进 demo-host；把质衡存储/Flyway/成员权限当被测插件；**维护质衡开放业务能力名录**。

**验收检查单**：

- [ ] 零插件行为与今日一致  
- [ ] 开数据插件后快照路径可用且可见  
- [ ] Prepare 按 §4.2 阶段桶稳定  
- [ ] 可拖「快进」复现跨天（P2）  
- [ ] 数据写竞争时 primary 必填；单插件可省略  
- [ ] 无能力无假按钮/假节点  
- [ ] UI 仅影响测流编辑/Run/报告  
- [ ] 停用/切环境后贡献节点**即时**失效（图 JSON 无 enabled 脏写）  
- [ ] 同能力第二提供方启用失败  
- [ ] ops-agent 未连通明示；连通后可调度  
- [ ] 启用失败均落在 §4.2 原因码  

---

## 8. 相关链接

- ROADMAP Exploring **6.2**；Later **7.4～7.6**、**5.5**  
- 现状写库快照（迁移前）：`project-summary.md`；`HttpEndpointSnapshotAdapter`  
- 被测 test-support（ephemeral，待迁入存档点插件）：`qualitest-demo-mall` / `test-support-starter`  
- 运维金库（**不是**本插件）：`qualitest-demo-host` `data/golden` / daily-reset  
- 两插件设计：`plugin-sut-db-save-point.md`、`plugin-sut-sys-clock-dial.md`  
 

---

## 9. 已定结论（追溯）

> 原待定点已消化进正文；严谨化细则以 **§10** 为准。再开争议须改正文并更新本表。

| 条 | 结论 |
| --- | --- |
| A1 | hooks / 节点引用 **本包**能力名；invoke **必须**带 pluginId |
| A2 / B3 | **禁止**环境 `prepareOrder`；阶段序写死；阶段内 `hooks.order` |
| A3 | 契约字符串 **kebab-case** |
| A4 | **`ui-slot:…` 互斥**；`slot` 只布局 |
| A5 | 短 id + 存储 `pluginId`/`contributionId` |
| A6 | ROADMAP 指向草案 |
| B1 | **无**全局能力唯一提供者；互斥靠 isolation |
| B2 | primary **仅 sut-db 竞争时必填** |
| B4 | **废包级 danger**；gate 在**插件能力对象**上；`remarks` 仅说明 |
| B5 | 主靠 **isolation**；conflicts 可选 `plugin:<id>` |
| B6 / D2 | capabilities **只放运行且私有于包**；UI 看 `ui` |
| B7 | 近期仅示意 UI 路径；A/tokens **后置** |
| B8 | http-adapter / builtin / ops-agent；builtin 靠 id；http 用 routes |
| B9 | 默认 **fail_run**；P0 skip 白名单 = ∅ |
| B10 | ops-agent **默认 CLI**（install/uninstall/ping）；`runtime.agent.cli` |
| C1 | **先文档契约**；可运行样本后置 |
| C2 | 官方 builtin = **虚包** + 主仓实现 |
| C3 | 类型名 **`pluginAction`** |
| C4 | allowDestructiveReset **1:1** → gate.destructive |
| 持久化 | **包 JSONC ≠ 环境 SQL**；节点可用/失效 **即时算** |
| C5 | 质衡**不做**开放业务能力名录；说明书 **JSONC** |
| C6 | **被测特殊面→插件**；库存档点/拨钟示意为 **CLI + configSchema**；ephemeral snapshot **迁入**存档点插件 |
| C7 | MCP 安装细节在 P4 |
| D1 | runtime.kind = **§2.3.1**；CLI = **§2.3.2** |
| D3 | 验收 checkbox |
| 严谨化 | **§10 实现契约** |

---

## 10. 实现契约（对照执行）

> 本章给实现者。与 §1～§8 冲突时以本章为准。

### 10.1 不变量

1. 能力名**私有于** `pluginId`；质衡**禁止**维护开放业务能力名录。  
2. `capabilities` ∩ UI 贡献 = ∅（UI 只在 `ui`）。  
3. Invoker / UiRegistry 是唯一入口。  
4. Host/Context 不得越权（§10.5）。  
5. Prepare 阶段序不可被环境覆盖。  
6. P0 挂点失败 → `fail_run`。  
7. `pluginAction` 是唯一插件步骤引擎类型。  
8. 总闸由**本包能力对象**的 `gate` 推导；**禁止**包级 `danger`；`remarks` 不参与判定。  
9. 官方 builtin 虚包与主仓注册表必须同发版可用。  
10. 原因码以 §4.2 为准，禁止未列出的静默成功。  
11. **被测特殊面执行禁止留核心**：只经 `invoke(pluginId, …)`；未启用不得回落。  
12. 宿主挂点只调度**已启用**且声明了对应 hook 的插件。  
13. `ops-agent` **默认 CLI**：须能 uninstall 干净；自定义连接等只进环境 `config`。

### 10.2 说明书字段约束（摘要）

| 字段 | 类型约束 |
| --- | --- |
| 文件名 | `qualitest.plugin.jsonc`（JSONC） |
| `id` | §1 字符集 |
| `version` | semver |
| `remarks` | 可选；仅展示 |
| `capabilities[]` | 对象：`name` 包内唯一；`effect`；可选 `gate` / `preparePhase` / `dbCompete` |
| `requires[]` | 仅 `plugin:<id>` |
| `conflicts[]` | 仅 `plugin:<id>` |
| `isolation[]` | ∈ §4.1 或 `ui-slot:<slug>` |
| `hooks[].name` | `onRunPrepare` \| `onRunFinalize` \| `onCheckpoint` \| `onRestore` |
| `hooks[].order` | 整数；默认 `100` |
| `hooks[].actions` | ⊆ 本包 `capabilities[].name` |
| `configSchema` | 可选；环境 `config` 必须通过 |
| `runtime.kind` | 三枚举 |
| `runtime.http.routes` | kind=http-adapter 时必须 |
| `runtime.agent.bundle` | kind=ops-agent 时必须 |
| `runtime.agent.cli` | kind=ops-agent 默认必须：`bin`、`pingArgs` |

出现已废字段 `danger` / 旧 `.json5` 契约若仍提交 → 按实现策略拒或迁移；能力写成「质衡开放名」→ `MANIFEST_INVALID`。

### 10.3 启用校验算法（必须按序）

对「将启用插件 P，环境 E」：

1. **Parse** → 失败 `MANIFEST_INVALID`。  
2. **Schema**：`config` vs `configSchema` → `CONFIG_SCHEMA_INVALID`。  
3. **Kind**：builtin 注册表 / http routes / ops-agent bundle（同前）。  
4. **Gate**：本包能力 `gate` 并集查 E → 未开则 `GATE_DENIED`。  
5. **Requires**：每个 `plugin:` 须已启用 → 否则 `REQUIRES_UNSATISFIED`。  
6. **Conflicts** → `PLUGIN_CONFLICT`。  
7. **Isolation** mutex → `ISOLATION_MUTEX`；补全缺省 `ui-slot:flow.banner.{id}`。  
8. **Primary data**：启用集中 `isolation` 含 `sut-db` **且** 存在 `dbCompete=true` 的能力 的插件数 ≥ 2 → 须填 `primaryDataProvider` ∈ S。  
9. **Hooks**：每个 action ∈ 本包能力名 → 否则 `HOOK_ACTION_NOT_OWNED`。  
10. **Commit**：环境绑定；UiRegistry；登记「已启用 pluginId → 说明书」（**无**全局 capability→plugin 名录表）。

停用：摘注册；图上相关节点**即时重算失效**，禁止落图。

### 10.4 Invoker 与 kind 分发

```text
invoke(envId, pluginId, capability, args, source) → Result | Error(code)
```

步骤：

1. `pluginId` 在 E 已启用 → 否则 `PROVIDER_NOT_FOUND`。  
2. `capability` ∈ 该包 `capabilities[].name` → 否则 `UNKNOWN_CAPABILITY`。  
3. **Gate**：该能力对象的 `gate`；未开 → `GATE_DENIED`。  
4. ops-agent：`cli ping`（或约定探活）失败 → `AGENT_UNREACHABLE`。  
5. 按 kind 分发：http routes / builtin 注册表 / **CLI 执行**（下发 config 文件）→ 失败 `INVOKE_FAILED`。  
6. **必须**写审计（含 pluginId、capability）；高危 gate 字段须齐全。  
7. serialize isolation：同 env 同标签 write **必须**排队。

Prepare 调度：**必须**实现 §4.2 排序算法。

### 10.5 Host/Context 白名单

调用实现时**允许**注入：

| 键 | 用途 |
| --- | --- |
| `envId` / `runId` / `projectId` | 定位 |
| `pluginId` / `capability` | 当前调用 |
| `args` | 调用参数（已按节点/hook schema 校验） |
| `envConfig` | 该插件在环境中的 config（只读视图） |
| `audit` | 仅追加审计步 API |
| `httpClient` | 仅 http-adapter 使用的受控出站客户端（目标受 routes 约束） |
| `agentClient` | 仅 ops-agent；只达环境登记的 agent URL |

**禁止**注入/访问：Spring `ApplicationContext`、任意 Bean 查找、质衡业务 Mapper/Repository、改质衡系统时钟、未登记 URL 的裸 `HttpURLConnection`、文件系统任意路径（除非经 agent）。

违规尝试 → `HOST_API_DENIED`，并 `fail_run`。

### 10.6 UI 登记与模板绑定

1. 启用时遍历 `ui`：登记 chrome/timeline/env/palette。  
2. `palette.nodes`：校验 capability ∈ 本包 capabilities；登记 `(pluginId, contributionId)`。  
3. 模板 `{{path}}`：  
   - **允许**绑定：已启用插件声明的只读投影（如时钟包 `clock.read` → `clock.now`）；键**必须**带 plugin 作用域，禁止当成质衡全局变量名开放。  
   - 解析失败或插件未启用：**必须**明确占位并告警；**禁止**静默变空串。  
4. 作用域外页面：**禁止**渲染插件 UI。

### 10.7 P0 明确不做（防范围漂移）

| 不做 | 说明 |
| --- | --- |
| 环境 prepareOrder | 已删 |
| skip_and_warn 非空白名单 | 空集 |
| ops-agent 常驻守护为默认 | 默认 CLI；RPC/守护后置 |
| ops-agent 完整 RPC 协议 | 仅 CLI + ping；协议后置 |
| UI 模式 A / tokens | 后置 |
| pack/domain 能力 | 不进目录 |
| MCP 安装被测端 | P4 |
| 公网插件商店 / JAR 热装 | 非目标 |
| 质衡开放业务能力名录 | **禁止** |
| 核心直调 snapshot/restore Adapter | **禁止**（P1 拆净） |
| 拆 gate 为多闸 | 暂 1:1 |

### 10.8 P0 实现检查表

**后端**

- [ ] 说明书解析 + §10.2 校验  
- [ ] §10.3 启用全序 + 原因码  
- [ ] 包登记 + 环境插件绑定落库（§2.6 实体）  
- [ ] 按 pluginId 启用集调度（无全局能力名录）  
- [ ] Prepare/Finalize 调度 + §4.2 排序  
- [ ] Invoker + http-adapter / builtin 分发  
- [ ] gate.destructive ↔ allowDestructiveReset  
- [ ] 审计时间线字段  
- [ ] Host/Context 白名单边界测试  

**前端**

- [ ] 环境插件启用/配置表单 + 错误码展示  
- [ ] UiRegistry：徽章/横幅至少一种  
- [ ] `pluginAction` 可用态**即时算**（切环境/停用后重算，不落图）  
- [ ] 模板不可用占位  
- [ ] ops-agent 未连通提示  

**官方包（后置样本）**

- [ ] 库存档点 / 拨钟：说明书 JSONC + CLI install/uninstall/ping 契约可对照  
- [ ] 环境 config（含 db）过 configSchema  
- [ ] B 类 UI 可见（有实现时）  
