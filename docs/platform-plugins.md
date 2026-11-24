# 质衡平台插件

> **状态**：设计草案 · 与产品 **2.0.0** **同发**（流契约重建 + 平台插件一口切；见 [flow-contract-2.0.md](./flow-contract-2.0.md)）。  
> **ROADMAP**：原 Exploring 6.2 并入 2.0.0 发布列车；**不再**写「不承诺版本」。  
> **实现**：**部分落地**——zip/本机目录登记说明书、环境启用、项目挂载、破坏性总闸、`ops-agent` + `local-exec` / `exec-agent`、存档点 Checkpoint/Restore、环境徽章与面板。官方 ops-agent 包**不**内嵌主仓 jar。缺口见 **§10.8**。  
> **不含**：JetBrains Helper、Cursor MCP（接入通道 ≠ 平台插件）。  
> **已定结论**：§9 · **实现对照**：§10（启用/调度/失败须有唯一答案）。

## 三分钟看懂

1. **插件是什么**：一份说明书（`qualitest.plugin.jsonc`）加可选的被测旁 CLI、可选的界面贡献。凡是「要被测配合才能干的事」（存档、拨钟、造数、代跑命令……）都做成插件，不写进质衡核心。
2. **启用 ≠ 调用**：环境里「启用」只是允许用；真正动被测，要等跑流、节点或按钮触发一次 **Invoke**。启用成功不代表被测已被改动（详见 §1「启用 vs Invoke」）。
3. **能力是插件私有的**：`archive.restore`、`clock.set` 这类名字只属于各自的 `pluginId`。质衡**没有**全局能力名录，调用时必须写「哪个插件 + 哪个能力」。
4. **核心只搭架子**：核心提供挂点（何时触发）、Invoker（按 pluginId 调用）、总闸、串行、审计、界面槽、`pluginAction` 节点。被测细节（怎么 dump 库、怎么改系统时间）一律在插件里。

**本文定四件事**：什么进插件、能力名叫什么、多个插件怎么共存、平台对插件开放多少。  
**怎么读**：§1 边界（含启用 vs Invoke）→ §2 插件形态（必读）→ §3 能力与界面 → §4 隔离与生命周期 → 实现时必读 **§10**。

### 规范用语

| 用语 | 含义 |
| --- | --- |
| **必须** | 违反则拒启用、图校验失败或跑流失败（原因码见 §4.2 / §10） |
| **禁止** | 实现不得提供该路径；出现则拒绝，或视为契约错误 |
| **默认** | 未另声明时的唯一行为；要覆盖，须在本文明确允许 |
| **后置** | 本期不做；只占名或指方向，不算 P0 范围 |

本文不定完整 Java/HTTP 方法签名，但**必须**定清：字段约束、校验时机、路由规则、失败语义、阶段映射。§1～§8 与 §10 冲突时，**以 §10 为准**并回改正文。

---

## 1. 边界：核心 vs 插件

> **人话**：只要事情需要被测系统「特殊配合」，就做成插件；所有项目都要、且和引擎语义强绑定的，才留在核心。核心只给插件一个「被调用」的架子。

今天测流引擎、鉴权、报告在核心；而拨钟、库存档点、场景加载、挡板渠道等散落在核心边角、运维仓或手写 HTTP。再不划界，每加一种靶场运维或测流皮肤都会渗进主仓。

插件一旦能干活，界面**必须**能表达（顶栏、徽章、贡献节点、报告块），否则视为契约不完整：启用时可警告；官方运行包**必须**至少有一种 B 类可见贡献（§3.2）。

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

**核心只给架子**：宿主挂点、按 `pluginId` 调用、总闸、串行、冲突、审计、UI 槽、`pluginAction`。  
**禁止**在核心内置「怎么打 `/test-support/*`」「怎么 dump/restore 被测库」「微信退款怎么回包」等被测细节。

### 铁律：被测要特殊处理 → 必须插件；能力归插件

| 判断 | 结论 |
| --- | --- |
| 被测必须接专用 HTTP / agent / 脚本 / 本机 dump 目录才干得成？ | **必须**做成插件；执行不得留在核心 |
| 说明书里的 `capabilities` | **该插件私有**；质衡**禁止**维护「开放能力名录」供任意包认领 |
| 质衡开放的是什么 | 宿主挂点名（`onCheckpoint` / `onRestore` / `onRunPrepare` / …）、Invoker、总闸、isolation——**不是** `archive.create` / `seed.apply` / `clock.set` 这类业务能力名 |

今日主仓直调 test-support 的 snapshot/restore：**必须**拆进存档点插件。拆完后分工如下：

| | 核心 | 插件（如库存档点 / 拨钟） |
| --- | --- | --- |
| 留下 | 挂点何时触发、画布意图、总闸 | 私有能力 + 被测旁 **CLI**（或已有 HTTP） |
| 调用 | `Invoker.invoke(pluginId, capability, …)` | CLI / routes / builtin |
| 禁止 | 内置连库 dump、拨钟细节；开放业务能力名录 | 未启用却暗门回落；无故常驻守护难卸载 |

未启用对应插件却配置了依赖该能力的意图 → 失败，**禁止**当核心功能继续用。  
跑中即时备份/恢复是本插件职责；**不要**与 demo-host 运维「金库」`data/golden` 混谈。

### 开放原则：默认拒绝，具名开放

> **人话**：平台只开放少数几个入口，其余一律不给；插件是「被调用方」，不能反过来掏质衡内部。

- **界面端 / 运行端**：**必须**只走平台调用面（Invoker、UiRegistry）；平台未开放的 Host API **禁止**调用。
- **`capabilities[]`**：插件自声明的**私有**运行能力；启用/停用时挂上/摘下；**禁止**写成质衡全局名录的条目。
- **调用方向**：挂点 / 贡献节点 / 工具条 → `Invoker.invoke(pluginId, capability, …)` → 该包实现 → 按 `runtime.kind` 分发（§10.4）。**禁止**插件反向掏质衡内部服务。
- **界面贡献**：**必须**写在 `ui`；启用时登记到 **UiRegistry**；**禁止**经 Invoker；**禁止**写进 `capabilities[]`。
- Host/Context 白名单见 §10.5；**禁止**直访 Spring 容器、任意 DataSource、质衡系统库。
- **`builtin`**：实现可在主仓；仍**必须**经 Invoker（带 `pluginId`）；注册表无实现 → 拒启用。
- **被测端**：装上后 OS 层面的行为不在平台沙箱内；`ops-agent` 启用/Invoke 前**必须**连通检查。
- **互斥**：同环境启用时，仅 `isolation` 里的 **`ui-slot:`** 同槽碰撞才拒启用；**不**靠点名其它插件，**不**靠「全局能力唯一提供者」。

`kind=builtin` 时实现可在主仓，但仍须经 Invoker（带 `pluginId`）。官方 **ops-agent** 包（存档点等）**不**内嵌主仓发行物，靠 zip / 安装目录登记。**禁止**照抄 DSH「一切皆插件 / 无特权内核」。

### 启用 vs Invoke（必读）

> **人话**：「启用」是在某个环境里把插件**打开**；「Invoke」是**这一次真去干活**。开了插件不等于已经动过被测。

```text
说明书已入库（zip 安装或本机发现目录登记）
        │
        ▼
【启用 Enable】在某个环境里打开插件、填 config、保存
        │  · 人操作一次（或导入环境配置）
        │  · 平台跑 §10.3：冲突、总闸、isolation…
        │  · 成功后：插件登记进启用集，ui 挂进 UiRegistry
        │  · 此时通常还没对被测发过任何请求
        ▼
插件「已启用」——顶栏/徽章可见，调色板可拖节点
        │
        │  之后：跑流 / 点工具条 / 跑到图上的 pluginAction
        ▼
【Invoke】Invoker.invoke(pluginId, 能力名, 参数, …)（§10.4）
        │  · 可发生很多次（每次 Prepare、每个节点、每次点按钮）
        │  · 再查总闸、再查 agent 连通、再按 kind 发 HTTP / 调 builtin / 起 CLI
        │  · 必须写审计时间线
        ▼
被测被改（或读到结果）；失败则 fail_run + 原因码
```

| | **启用 Enable** | **Invoke** |
| --- | --- | --- |
| **一句话** | 在这个环境里**允许用**这个插件 | **这一次**去执行某个能力 |
| **谁触发** | 人在环境配置里勾选/保存 | 跑流挂点、图节点、工具条等 |
| **次数** | 每个环境对每个插件：启用/停用，偏配置态 | 一次跑流里可以很多次 |
| **主要检查** | 说明书、冲突、config、**本包能力 gate→总闸**、agent bundle…（§10.3） | 该 `pluginId` 是否启用、本能力 gate、连通、执行结果（§10.4） |
| **成功之后** | 插件可被挂点/节点找到；**不等于**已动过被测 | 对被测产生了一次真实副作用或读到结果 |
| **失败像什么** | 插件开不了，表单报原因码 | 本次跑流中断（默认 `fail_run`）或节点失败 |
| **和总闸** | 包内能力所需闸未开 → **拒启用** | 启用后总闸被关 → **拒 Invoke**（再拦一次） |

例子（存档点 CLI，私有能力 `archive.restore`，`gate=destructive`）：

1. 被测机已 `install` CLI 并写好 `{etc}/bind/{bindAlias}.json`；质衡**项目**挂上同名 `bindAlias`，环境启用并开破坏性总闸 → `ping` 成功（项目只能调自己的 bind 文件）。
2. 挂点 `onCheckpoint` → `invoke(…, "archive.create", { saveId })`（连接以**本地 bind**为准；存档按项目+环境分目录）；出问题或续跑 → `archive.restore`。
3. 不想用了 → 环境停用 + 被测机 `uninstall.sh`，没有常驻进程要杀。
4. 中途关总闸 → 之后的 **restore** Invoke **必须** `GATE_DENIED`。

**停用**是启用的反操作：摘路由与 UI；图上残留的 `pluginAction` **必须**按 §2.6 **即时重算为失效**（不改流程图持久字段），之后 Invoke 不到该提供方。

后文凡写「启用时…」「Invoke 时…」，都按本小节理解。

### 刻意不做

- 无限膨胀引擎节点枚举（画布步骤 → **`pluginAction` + 贡献外观**）
- 任意全局 CSS / 整页替换；改质衡自身 DB / JVM 钟 / 多租户
- 公网任意 JAR 热装商店；用插件绕开未开放的引擎能力
- DSH 式无特权内核

### 命名风格（契约字符串）

| 类别 | 规则 | 示例 |
| --- | --- | --- |
| 枚举 / isolation / kind / 能力 `gate` | **必须** kebab-case | `ops-agent`、`http-adapter`、`destructive` |
| 插件内能力名 | **必须** `^[a-z][a-z0-9.-]*$`；**私有于该 pluginId** | `archive.create`、`clock.set` |
| 插件 id | **必须** `^[a-z][a-z0-9.-]*$`，建议 `qualitest.*` / 厂商前缀 | `qualitest.sut-db-archive-point` |
| 贡献短 id | **必须** `^[a-z][a-z0-9-]*$`，**禁止**含 `/` | `clock-advance` |

---

## 2. 一个插件长什么样（必读）

### 2.1 一份说明书，最多三端

> **人话**：一个插件 = 一份说明书。它按「装在哪」最多分三块：界面（装在质衡 Web）、调度（装在质衡 app）、被测旁 CLI（装在被测机）。说明书的 id 三处必须一致。

说明书文件名 `qualitest.plugin.jsonc`（JSONC：可写注释和尾逗号，键要加引号）。分发形态：

| 形态 | 规则 |
| --- | --- |
| zip 包 | **主路径**。根目录**必须**含说明书；可选 `runtime/`、`agent/`、`ui/`、`assets/`、`SHA256SUMS`。上传到质衡本机安装目录并登记 |
| 本机发现目录 | 开发联调：把含说明书的包目录放到 `{install-dir}` 或 `qualitest.plugin.discovery-dirs` 下，启动时扫描登记 |
| `builtin` | 仅当实现必须在主仓进程内时：说明书随主仓 + Java 注册表；**不是**官方 ops-agent 包的默认形态 |

官方存档点 / 造数 / 执行代理 / 拨钟等：**一插件一仓**，主仓只给文档引导；**禁止**再往质衡 jar 里塞 classpath「虚包」说明书。

**禁止**把同一插件拆成多个市场商品 id。

| 端 | 正式名 | 装到哪 | 包内位置 | 用户感知 |
| --- | --- | --- | --- | --- |
| **看得见** | 界面端 Console | 质衡 Web | 说明书 `ui` + 可选 `ui/` | 顶栏、徽章、调色板节点 |
| **质衡侧调度** | 运行端 Runtime | 质衡 app | 说明书 `runtime` / `hooks` | 跑流时平台知道何时调谁 |
| **被测旁落地** | 被测端 Agent | 被测机 / 演示机 | **`agent/`（默认 CLI）** | 库存档点、拨钟、改 hosts 等 |

硬约束：

- 被测端**必须**使用与说明书相同的插件 `id`。
- `runtime.kind`：**必须**为 `http-adapter` \| `builtin` \| `ops-agent`（§2.3.1）。
- `kind=ops-agent`：**必须**有 `runtime.agent`；**默认形态 = CLI**（§2.3.2），装得上卸得下。
- `http-adapter` / `builtin`：**禁止**强依赖 `agent/` 才能启用。
- 环境侧自定义：写进环境插件绑定的 **`config`**（用 `configSchema` 校验）；**禁止**写死在流程图。
- **项目级挂载**（§2.5.1）：如存档点的 `bindAlias`——存在**项目**上，启用时只读带入；**禁止**当成环境可改字段。
- 被测机侧连接/密钥（host、密码等）：默认放被测旁 **bind 文件**（或等价信任根），**禁止**进流程图；质衡表单原则上不收密码。
- ops-agent 的存档类数据**必须**按 `projectId`/`envId` 分目录（CLI 二进制不必按项目各装一份）。

**「完整」的含义**：契约合法，且启用后能按能力执行。样本逐个落地：存档点已部分落地（见 §10.8），拨钟样本尚未落地，其余以各插件仓为准。

### 2.2 目录样例（ops-agent + CLI）

```text
qualitest.sut-db-archive-point-1.0.0.zip
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
| `capabilities` | 必须（可 `[]`） | **本包私有**能力对象数组（见下）；**禁止** `ui.*`；**不是**质衡开放名录 |
| `requires` | 默认 `[]` | 仅 `plugin:<id>`；**禁止** `gate.*`；**禁止**引用「全局能力名」 |
| `isolation` | 默认 `[]` | 见 §4.1；启用互斥**仅**认 `ui-slot:<名>` |
| `hooks` | 默认 `[]` | `actions[]` **必须**为本包 `capabilities[].name` |
| `configSchema` | 可选 | JSON Schema；环境 `config` 须通过才能启用 |
| `projectMount` | 可选 | 项目级挂载声明（§2.5.1）；缺省 = 不需要挂载 |
| `push` | 可选 | 经执行代理推送的制品白名单：`push.products[]`，每项含 `productId` 与制品相对路径（`artifact` / `artifactWin`） |
| `channelProvider` | 可选 | `true` 时本包可作为 Invoker 的 `exec-agent` 通道提供者 |
| `ui` | 可选 | 见 §3.2 |
| `runtime` | 有运行能力时必须 | 见 §2.3.1 |

总闸看**本包能力对象上的 `gate`**。

#### `requires[]`

> **人话**：「我要先有另一个插件才能用」。例子：场景造数（scenario-seed）依赖存档点（archive-point）——环境里得先启用存档点，才能启用造数。

条目写 `plugin:<id>` 或裸 `<id>`（二者等价）；启用时目标插件须已在本环境启用，否则 `REQUIRES_UNSATISFIED`。

#### `capabilities[]` 对象（插件私有）

```jsonc
{
  "name": "archive.restore", // 包内唯一
  "effect": "write",         // read | write
  "gate": "destructive"      // 省略/"none" | destructive | secrets-read
}
```

| `gate` | 启用时 | Invoke 时 |
| --- | --- | --- |
| 无 / `none` | 无额外总闸 | 无额外总闸 |
| `destructive` | 环境 `gate.destructive`（= `allowDestructiveReset=1`）须开 | 本能力再查一次 |
| `secrets-read` | 环境 `gate.secrets-read` 须开（默认关） | 本能力再查一次 |

1. **启用**：本包所有能力 `gate` 的并集均须已开。
2. **Invoke**：只查本次能力的 `gate`。
3. `remarks` 不影响开闸。
4. 高危 gate 的 Invoke **必须**审计。

#### `projectMount`（项目挂载声明）

> **人话**：不是装 CLI 进项目，是项目上存 `bindAlias`。详见 §2.5.1。

```jsonc
"projectMount": {
  "fields": [ { "name": "bindAlias", "required": true, "pattern": "^[a-z][a-z0-9-]{1,63}$" } ],
  "inject": [ { "from": "bindAlias", "to": "db.bindAlias" } ],
  "inheritFrom": "qualitest.sut-db-archive-point" // 可选：复用另一插件的项目挂载（如造数复用存档点）
}
```

#### `runtime.agent.cli.argMap`

可选。把 Invoke `args` 的键映射成 CLI 开关，例如 `{"saveId":"--id","scenarioId":"--id","storeKey":"--db"}`。未声明时平台只用通用约定。

### 2.3.1 `runtime.kind`

> **人话**：被测已有 HTTP 门面 → `http-adapter`；必须和引擎进程内耦合 → `builtin`；其余需要碰被测机 → `ops-agent` + CLI。

| `kind` | 何时用 | 质衡行为 | 被测旁 |
| --- | --- | --- | --- |
| `http-adapter` | 被测**已有**稳定 HTTP 门面 | 按 `runtime.http.routes` 出站 | 无 `agent/` 前提 |
| `builtin` | 必须与跑流引擎进程内耦合 | 主仓按 `pluginId` 调实现 | 无 `agent/` 前提 |
| `ops-agent` | 须碰 OS / 库 / 容器 / 本机文件 | 调被测旁 **CLI（默认）** | **必须**有 `agent/` |

- **禁止**第三方 JAR 热装进质衡进程。
- **禁止**为「方便」默认起常驻 HTTP 守护；能 CLI 一次做完的，不要常驻进程（卸载麻烦）。
- `http-adapter`：每个可 invoke 的能力**必须**有 `routes`。`builtin`：**禁止**写 `entry`。

### 2.3.2 被测端默认形态：CLI（装 / 卸）

> **人话**：`ops-agent` 默认交付为一个命令行工具，不是常驻服务。装得上、卸得掉、不留后台进程。

| 要求 | 说明 |
| --- | --- |
| 安装 | `agent/install.sh`（或文档步骤）把 CLI 放到约定路径 / PATH |
| 卸载 | `agent/uninstall.sh` **必须**能删干净二进制与本插件配置目录，不留守护进程 |
| 调用 | 质衡 Invoke → 在被测机执行：`qt-plugin <capability> --config <env下发的配置文件>`（命令名以说明书为准） |
| 配置 | 环境插件 **`config`**（策略类：storeRoot、复位策略、`prepareAt` 等）+ 平台注入 `projectId`/`envId`/总闸投影；被测连接以 **bind 等本机信任根**为准（见各插件专文）。Invoke 时合并写入临时/约定路径再传 CLI；**密钥不进流程图** |
| 探活 | 启用/Invoke 前跑 `qt-plugin ping`（或 `--help` 约定）；失败 → `AGENT_UNREACHABLE` |
| 非默认 | 仅当被测**已经**提供 HTTP 且不愿装 CLI 时，改用 `http-adapter` |

#### 执行通道：CLI 怎么被「够到」

> **人话**：「载荷」是被测机上要跑的业务 CLI；「通道」是质衡怎么把它起起来。同机的话只需要记载荷这一层。

| 层 | 含义 | 已定 |
| --- | --- | --- |
| **载荷** | 被测机上跑的**业务 CLI**（dump、拨钟等） | **同机主路径** |
| **通道** | 质衡 Invoker **如何**起该 CLI | **核心**分发 `channel.kind` |

通道有三种：

1. **`local-exec`（默认 · 已落地）**：同机直接起业务 CLI，**不必**装执行代理。
2. **`exec-agent`（已落地）**：分机 HTTP 代跑——须启用官方插件 **被测执行代理**（`qualitest.sut-exec-agent`）。见 [执行代理专文](https://github.com/qualitest-hq/qualitest-plugin-sut-exec-agent/blob/main/docs/design.md)、[platform-ops-exec-channel.md](./platform-ops-exec-channel.md)。
3. **`ssh`（后置 · 未实现）**：分机远程跑同一业务 CLI（只占契约名）。

**归属**：`channel.kind` 分发 = 核心；执行代理 = **正式官方插件**（可启用、一仓、有徽章）；业务插件只声明各自的 `cli.bin`。  
**新手引导（已定）**：存档点/拨钟 → 同机业务插件怎么用；执行代理 → 需要分机代跑时怎么用。

### 2.3.3 官方插件索引与骨架

> **人话**：下面只是索引和两份骨架，字段全表以各插件仓的 `docs/design.md` 为准。官方插件一插件一私有仓，实现语言统一 **Go**；官方包同时兼作插件用法的新手引导。

**落地现状**：存档点已部分落地（`sdap` CLI，首期 MySQL，`install` / `ping` / `create` / `restore`；项目挂载 `bindAlias`；环境徽章）。执行代理的通道已落地。**拨钟样本尚未落地**，网混沌仍是后置示意；造数等以各仓为准。总清单见 §10.8。

**仓库策略（已定）**：各仓 **`docs/design.md` 为该插件权威契约**；Invoker / 执行通道 / 本文骨架在 **`qualitest` 主仓**。本文不维护插件专文全文，只做索引。

| 包 | 专文 | 实现仓（私有） | 要点 |
| --- | --- | --- | --- |
| `qualitest.sut-db-archive-point` | [design.md](https://github.com/qualitest-hq/qualitest-plugin-sut-db-archive-point/blob/main/docs/design.md) | [仓](https://github.com/qualitest-hq/qualitest-plugin-sut-db-archive-point) | 断点存档；CLI `sdap` / `archive.*` |
| `qualitest.sut-db-scenario-seed` | [design.md](https://github.com/qualitest-hq/qualitest-plugin-sut-db-scenario-seed/blob/main/docs/design.md) | [仓](https://github.com/qualitest-hq/qualitest-plugin-sut-db-scenario-seed) | 场景造数；CLI `sdss` / `seed.*` |
| `qualitest.sut-sys-clock-dial` | [design.md](https://github.com/qualitest-hq/qualitest-plugin-sut-sys-clock-dial/blob/main/docs/design.md) | [仓](https://github.com/qualitest-hq/qualitest-plugin-sut-sys-clock-dial) | 业务 + 同机引导；拨钟 |
| `qualitest.sut-exec-agent` | [design.md](https://github.com/qualitest-hq/qualitest-plugin-sut-exec-agent/blob/main/docs/design.md) | [仓](https://github.com/qualitest-hq/qualitest-plugin-sut-exec-agent) | **执行代理**（分机代跑）；同机不必启 |
| `qualitest.sut-net-chaos` | [design.md](https://github.com/qualitest-hq/qualitest-plugin-sut-net-chaos/blob/main/docs/design.md) | [仓](https://github.com/qualitest-hq/qualitest-plugin-sut-net-chaos) | 网混沌；后置示意 |

通道分发（核心）→ [platform-ops-exec-channel.md](./platform-ops-exec-channel.md)。

```jsonc
// 存档点 · 能力/挂点骨架（configSchema / ui 细节见专文）
{
  "id": "qualitest.sut-db-archive-point",
  "version": "1.0.0",
  "displayName": "被测库存档点",
  "capabilities": [
    { "name": "list", "effect": "read", "gate": "none" },
    { "name": "archive.create", "effect": "write", "gate": "none" },
    { "name": "archive.restore", "effect": "write", "gate": "destructive" },
    { "name": "archive.delete", "effect": "write", "gate": "none" }
  ],
  "isolation": ["ui-slot:flow.badge.db-archive-point"],
  "hooks": [
    { "name": "onCheckpoint", "order": 50, "actions": ["archive.create"] },
    { "name": "onRestore", "order": 50, "actions": ["archive.restore"] }
  ],
  "runtime": {
    "kind": "ops-agent",
    "agent": { "bundle": "embedded", "cli": { "bin": "sdap", "pingArgs": ["ping"] } }
  }
}
```

```jsonc
// 拨钟 · 能力/挂点骨架（configSchema / ui 细节见专文；样本尚未落地）
{
  "id": "qualitest.sut-sys-clock-dial",
  "version": "1.0.0",
  "displayName": "被测系统拨钟",
  "capabilities": [
    { "name": "clock.read", "effect": "read", "gate": "none" },
    { "name": "clock.set", "effect": "write", "gate": "destructive" },
    { "name": "clock.advance", "effect": "write", "gate": "destructive" },
    { "name": "clock.reset", "effect": "write", "gate": "destructive" }
  ],
  "isolation": ["ui-slot:flow.banner.clock"],
  "hooks": [
    { "name": "onRunPrepare", "order": 80, "actions": ["clock.set"] },
    { "name": "onRunFinalize", "order": 20, "actions": ["clock.reset"] }
  ],
  "runtime": {
    "kind": "ops-agent",
    "agent": { "bundle": "embedded", "cli": { "bin": "qt-sut-sys-clock-dial", "pingArgs": ["ping"] } }
  }
}
```

跑中的 ephemeral checkpoint/restore（今日 test-support）：**迁入存档点插件**（或同能力 `http-adapter`）；**禁止**写成「运维金库」语义。

### 2.4 画布「新节点」：插件贡献节点

> **人话**：画布上不为每个插件新增节点类型，统一用一种节点 `pluginAction`，由插件说明书决定它长什么样、调哪个能力。

**禁止**为每个插件新增 `FlowNodeType`。**必须**使用唯一引擎类型 `pluginAction` + 说明书 `ui.palette.nodes` 贡献多颗外观。

| 用法 | 适合 |
| --- | --- |
| 生命周期挂点 | 每次跑流默认准备/收尾 |
| 顶栏/工具条 | 临时触发，不改图 |
| `ui.palette.nodes` | 写入图的步骤 |

图节点**必须**具备的落图/落库字段：

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
- 规范化：`canonicalContributionId = pluginId + "/" + contributionId`（说明书写短 id；图存储**必须**存 `pluginId` + `contributionId` 两个字段）。
- **可用 / 失效（启用态）即时算，禁止落图**：打开画布、切换环境、图校验、跑流时，按**当前环境**启用集 + 包登记 + 说明书推导（§2.6 算法）。
  - 插件未启用 / 未登记 / 贡献 id 未知 / 能力不在说明书 → UI **必须**标失效（可复用节点既有启用/禁用呈现）；跑流 **禁止**静默跳过（`NODE_CAPABILITY_UNAVAILABLE`）。
  - **禁止**把这种瞬时状态写成节点上的持久 `enabled`/`disabled` 并随流程图保存；换环境后必须重算。
- 核心节点若另有「用户手动跳过/禁用」字段，与上条正交：手动禁用是图配置；插件失效是环境投影。两者同时存在时：**任一不可用则不调度**。
- P0：契约 + 校验；P2：至少 1 颗可拖且可执行的样本。

### 2.5 环境组合（开哪些包）

> **人话**：每个环境开了哪些插件、各自的 config，存在项目/环境这一侧，不写进插件 zip。

环境组合是 §2.6「环境插件绑定」的 API/编辑投影，**禁止**写进插件 zip。

```jsonc
{
  "envId": 12,
  "plugins": [
    {
      "id": "qualitest.sut-db-archive-point",
      "version": "1.0.0",
      "enabled": true,
      // bindAlias 来自项目挂载（只读注入），环境只配剖面/覆盖项
      "config": { "db": { "resolve": "bind-file", "bindEnvKey": "staging" } }
    }
  ]
}
```

**禁止**环境级 `prepareOrder`。Prepare/Finalize 按 `hooks.order` 调度，见 §4.2。  
跑流、顶栏、调色板**必须**只读取**当前环境**已启用的组合。

### 2.5.1 项目级挂载（project mount）

> **人话**：不是装 CLI 进项目，是项目上存 `bindAlias`。它是个「绑定键」，告诉平台这个项目对应被测机上哪份 bind 文件；跨环境共用，环境里改不了。

部分插件除「环境启用 + config」外，还需要**项目级**字段。启用/Invoke 时由平台注入。

| | 规则 |
| --- | --- |
| 存哪 | 项目设置 / 项目扩展表（按 `pluginId` 分块）；**禁止**写进插件 zip |
| 谁改 | 项目管理员；改一次，该项目下各环境共用 |
| 声明 | 说明书 `projectMount`（§2.3）；平台按 `fields` 校验、按 `inject` 写入 config 只读视图 |
| 启用时 | 声明了 `projectMount` 但缺必填 → **拒启用**（`PROJECT_MOUNT_REQUIRED`）；有 `inheritFrom` 则读被继承插件的项目挂载 |
| 环境 | **禁止**覆盖项目挂载里的只读键；可另配环境专有项（如 `bindEnvKey`） |
| 首例 | 存档点：`bindAlias`（必填）、`storeKeys`（可选）——见专文 |

未声明项目挂载的插件（如拨钟）→ 只需环境 config。

### 2.6 包 JSON vs 平台落库（必须分清）

> **人话**：说明书是「这个插件是什么」，基本不变；平台数据库存的是「这个环境开了什么」，经常变。两者不要混。

**禁止**混成一张「插件表把说明书字段拆成列、又把环境启用写进 zip」。

| | **包契约 JSON** | **平台 SQL（运营态）** |
| --- | --- | --- |
| 载体 | `qualitest.plugin.jsonc`（zip 根或本机发现目录） | 质衡库表（实体见 Flyway） |
| 谁写 | 插件作者 / 插件仓 | 安装、环境配置、跑流 |
| 变不变 | 随 `id@version` 基本不变 | 启用、config、闸随环境变 |
| 典型内容 | capabilities / hooks / ui / runtime / remarks / configSchema | 包登记、环境启用行、闸、审计 |

#### 包契约（只在 JSON，不拆成环境列）

说明书字段以 §2.3 / §10.2 为准。入库时**必须**保留可校验的说明书快照（整包 JSON 或等价规范化文档）；**禁止**只存 `displayName` 而丢掉 `capabilities`/`hooks`。

#### 平台实体（逻辑表，不定列类型）

| 实体 | 回答什么 | 关键字段（语义） | 备注 |
| --- | --- | --- | --- |
| **包登记** | 平台里有哪些插件版本 | `plugin_id`、`version`、`manifest` 快照、`content_hash`、状态、入库时间 | zip 上传或启动扫描安装/发现目录写入 |
| **环境插件绑定** | 这个环境开了谁 | `env_id`、`plugin_id`、`version`、`enabled`、`config`（过 configSchema） | = §2.5 投影；**唯一**持久的「插件启用/停用」 |
| **环境总闸** | 允不允许破坏性操作 | `gate.destructive`（**1:1** 今日 `test_project_env.allow_destructive_reset`）；`gate.secrets-read` 仅预留 | 不进插件包 |
| **环境主键数据提供方** | 双清时听谁的 | `primary_data_provider`（可空） | 仅 §10.3 竞争时必填 |
| **跑流审计** | 谁在何时 invoke 了什么 | 时间线条目（capability、pluginId、source、结果、原因码） | 挂现有 Run/时间线，不另造插件审计库也可 |

**禁止落库 / 禁止落图**：

- 节点上「因插件停用而失效」——**即时算**（§2.4）。
- 已启用插件注册表、UiRegistry——进程内/缓存，由启用集派生，可重建。
- `remarks`——在说明书快照里即可，**禁止**单独建业务表。

#### 节点可用态算法（即时）

对图上每个 `pluginAction` 节点 N、当前环境 E：

1. 查 E 的环境插件绑定：存在 `plugin_id=N.pluginId` 且 `enabled=true` 且版本已包登记 → 否则 **失效**。
2. 读该版本说明书：存在贡献 `contributionId`，且 `capability` ∈ 本包 `capabilities[].name` → 否则 **失效**。
3. （可选展示）所需 `gate` 未开 → 可标「闸未开」告警；是否允许保存图不阻拦，**跑流/Invoke 仍按 §10.4 拒**。
4. 若节点另有用户手动禁用 → **不可调度**（与 1～2 独立）。

切换环境或停用插件后：**必须**重算；**禁止**要求用户改图才能看到失效。

### 2.7 怎么发到用户手里（含被测端安装）

> **人话**：质衡侧靠 zip（或开发机发现目录）登记说明书与可选 `ui/`；被测机上的 CLI 另装，不会「解压即执行」。本地联调看各插件仓 README 即可。

| 端 | 到用户 | 安装 |
| --- | --- | --- |
| 界面端 | zip / 发现目录入库 → Web 读说明书 `ui` | 随包登记 |
| 质衡宿主 | zip → `POST /project/pluginHost/install` 落盘 `{profile}/plugins/{pluginId}` 并登记 | **项目设置 → 安装插件包**；不是被测机 |
| 运行端 builtin | 主仓实现 + 已登记说明书 | 启用即可（**必须**已注册实现） |
| 被测端 | CLI（install/uninstall） | **必须**显式安装；`ping` 失败 → 提示；不用则 **uninstall** |

- 启用（质衡侧）**必须**走 §10.3 全序校验；**禁止**「解压即执行」。
- 私有化签名 / 允许列表：**后置**。界面资源**禁止**走 npm/独立 CDN。
- zip 上限与 `spring.servlet.multipart.max-file-size` 对齐（默认 10MB）；配置项 `qualitest.plugin.install-dir`（空则 `{qualitest.profile}/plugins`）。
- 被测端安装通道：人工脚本、Compose 旁路容器、**控制台推送**（经已运行的执行代理）。**首装执行代理**仍须人工或 Compose 边车；无代理的静默推送仍后置。

#### Web / MCP / 本机 Agent（已定）

| 谁 | 做什么 | 结论 |
| --- | --- | --- |
| **质衡 Web** | 跑在质衡服务器 | **自己写不进**被测盘；无代理时不能替用户 `install` / 写 bind。**若环境已启用** `qualitest.sut-exec-agent` 且通道 `exec-agent` 可达，可经 Host **编排**推送：装业务 CLI、写 bind（由代理写盘） |
| **本机 Agent**（Cursor、Claude Code、其它能跑命令且已挂质衡 MCP 的客户端） | 在**够得着被测盘**的机器上：装 CLI、写 bind、本机 `ping`；亦可首装执行代理 | **可以**；关键是「有盘/有权限」，不是 IDE 品牌 |
| **质衡 MCP** | 项目挂载、环境启用插件、总闸、查状态、返回安装说明/bind 模板 | **可以**；只配质衡侧，**禁止**假装远程改被测盘 |
| **跑流** | `Invoker` → 执行通道 → CLI | MCP **不**直接 dump/拨钟 |

云端-only、碰不到被测机的 Agent ≈「无代理的 Web」：MCP 仍能配质衡侧，装 CLI/写 bind 须人、有盘的 Agent 或已部署的执行代理边车来做。  
优先**扩核心 Tool / 已有 Tool 参数**；插件可选 `mcpTools[]` 后置。细节见 [存档点 design.md §1.3](https://github.com/qualitest-hq/qualitest-plugin-sut-db-archive-point/blob/main/docs/design.md)、[执行通道 · 控制台推送](./platform-ops-exec-channel.md)。

---

## 3. 能力归属与宿主挂点

> **人话**：质衡不发「能力名」，只发「挂点名」。能力名是各插件自己起的，只在自己名下有效。

### 3.0 质衡**不**做开放业务能力名录

| 是 | 否 |
| --- | --- |
| 宿主挂点：`onRunPrepare` / `onCheckpoint` / `onRestore` / `onRunFinalize` … | ~~`sut.data.checkpoint` 等写进质衡开放 API~~ |
| 插件在说明书里声明**私有** `capabilities[]` | ~~平台占名表，谁都能认领~~ |
| `Invoker.invoke(pluginId, name, …)` | ~~`invoke("sut.data.checkpoint")` 全局按名找提供方~~ |

存档/读档拆进插件后，它们只是该插件的 `capabilities[].name`（如 `archive.create`），**禁止**再表述为「质衡开放能力」。

### 3.1 官方示例包（示意，非名录）

| 包 id | 为何存在 | 私有能力（示例） | kind / 被测旁 |
| --- | --- | --- | --- |
| `qualitest.sut-db-archive-point` | 须连被测状态源；断点打存档/读档 | `list` / `archive.*` | ops-agent + **CLI `sdap`** |
| `qualitest.sut-db-scenario-seed` | 跑前场景造数（可审阅小 SQL） | `seed.list` / `seed.apply` | ops-agent + **CLI `sdss`**；`requires` 存档点挂载 |
| `qualitest.sut-sys-clock-dial` | 须改被测机**系统时间**（靶场） | `clock.*` | ops-agent + **CLI** |
| `qualitest.sut-exec-agent` | 分机时在被测旁**代跑**其它业务 CLI | `agent.ping` / `agent.exec` | ops-agent + 执行代理进程 |
| `qualitest.sut-net-chaos` | 须在被测机注入延迟/丢包（靶场） | `net.*` | ops-agent + **CLI** |

通道**分发** = 核心，见 [platform-ops-exec-channel.md](./platform-ops-exec-channel.md)（已落地 `local-exec` / `exec-agent`；`ssh` 后置）；执行代理**是插件**（上表）。说明书见 §2.3.3 与各专文。

### 3.2 界面贡献（近期路径）

> **人话**：插件在说明书的 `ui` 里写想要什么界面，启用时平台登记即可；不需要再声明任何「界面能力名」。

有 `ui` 块即登记；**禁止**要求再声明 `ui.flow.*` 能力名。

| 模式 | 写哪里 | 阶段 |
| --- | --- | --- |
| B | `ui.chrome` / `ui.timeline` / `ui.env` /（后）`ui.report` | P0：官方运行包**必须**至少一种徽章或横幅可渲染 |
| C | `ui.palette.nodes` | 契约 P0；可拖可执行 P2 |
| A | `ui.nodeDecorations` 等 | **后置** P3+ |
| 主题 | isolation `ui-theme`；独立 tokens **后置** | 后置 |

#### `ui.env`（环境管理槽）

平台提供通用壳（启用开关、`configSchema` 表单、`projectMount` 表单）；插件用说明书声明贡献，**禁止**在核心硬编码某插件的表单。

表单渲染：宿主用 **JSON Schema → [`@lljj/vue3-form-element`](https://www.npmjs.com/package/@lljj/vue3-form-element)**（vue-json-schema-form 的 Element Plus 适配）。`configSchema` **原样**传入，不做 form-create rule 或其它 UI DSL 转换；`projectMount.fields` 先收成等价 JSON Schema 再交给同一组件。前端校验只为体验，启用仍以 Java **networknt** 为准。

| 子字段 | 含义 |
| --- | --- |
| `badge` | `{ text, tone }`；环境页徽章 |
| `actions[]` | `{ id, label, capability, panel? }`；`panel` 可为内置 `invoke-table` |
| `panel` | 可选远程面板：`{ entry: "ui/env-panel.js", mount: "slot" }`；`ui/` 经 `GET /project/pluginHost/ui/{pluginId}/**` 托管；前端 ESM `import` 后调 `mount(el, ctx)` / `unmount()` |

`ui.chrome.badges` 用于测流顶栏；`ui.env.badge` 用于环境页——**可并存**。

`palette.nodes[].capability` **必须**是运行能力且 ∈ 本包 `capabilities`。作用域**仅**测流编辑 / Run / 报告。

**UI 槽互斥**：`isolation` 中的 `ui-slot:<名>` 是**唯一**启用冲突键。`banner.slot`（`top`\|`bottom`）只管布局。若存在 `ui.chrome.banner` 且未声明任何 `ui-slot:*`，启用时**必须**自动加入 `ui-slot:flow.banner.{banner.id}`。

模板绑定：仅绑定**已启用插件**导出的只读投影（如时钟包的 `clock.now`）；规则见 §10.6。

---

## 4. 隔离、生命周期、安全

### 4.1 隔离标签（`isolation[]`）

> **人话**：目前只有一种冲突——两个插件想占同一个界面槽位。

Exploring **只认** `ui-slot:<名>`：同环境另一 `pluginId` 已占用相同完整槽名 → `ISOLATION_MUTEX`。同一 `pluginId` 再启用 / 换版本不算冲突。  
官方说明书 `isolation` **只应写**需要的 `ui-slot:`；通道身份用 `channelProvider`，不另造类别标签。

### 4.2 跑流挂点

> **人话**：一次跑流的顺序是固定的：准备 → 跑图（中间可能存档/还原）→ 收尾。同一阶段里有多个插件，按 `order` 再按 `pluginId` 排。出错默认直接中断。

```text
启动 → onRunPrepare → 跑图（快照→onCheckpoint；还原→onRestore）→ onRunFinalize → 终态
```

**排序（Exploring）**：收集已启用插件对应 hook 的 `actions`，按 `hooks.order` → `pluginId` 依次 `Invoker.invoke`。  
跑中快照：**必须**走宿主 `onCheckpoint` / `onRestore`；**禁止**塞进 Prepare。

#### 失败策略与原因码

| 策略 | 何时 | 行为 |
| --- | --- | --- |
| `fail_run` | **默认**；P0 白名单为空集 | 中断跑流；时间线记失败；写审计 |
| `skip_and_warn` | **仅当**能力 ∈ 本文白名单 | 跳过该 action；时间线**必须**可见警告 |

**P0 `skip_and_warn` 白名单 = ∅**（一律 `fail_run`）。追加白名单**必须**改正文这张表，禁止实现私自添加。

| 原因码 | 含义 |
| --- | --- |
| `MANIFEST_INVALID` | 说明书解析/字段/字符集失败 |
| `PROJECT_MOUNT_REQUIRED` | 包需要项目挂载但项目未配齐（如缺 `bindAlias`） |
| `UNKNOWN_CAPABILITY` | 能力名 ∉ 该 pluginId 说明书 |
| `REQUIRES_UNSATISFIED` | requires 未满足 |
| `ISOLATION_MUTEX` | 同环境 ui-slot 被另一插件占用 |
| `GATE_DENIED` | 总闸未开 |
| `BUILTIN_IMPL_MISSING` | builtin 无注册实现 |
| `AGENT_BUNDLE_INVALID` | agent bundle/目录不合法 |
| `AGENT_UNREACHABLE` | 被测端未连通 |
| `CONFIG_SCHEMA_INVALID` | 环境 config 不符 schema |
| `HOOK_ACTION_NOT_OWNED` | hook action ∉ 本包 capabilities |
| `PROVIDER_NOT_FOUND` | invoke 时无启用提供方 |
| `NODE_CAPABILITY_UNAVAILABLE` | 图节点能力不可用 |
| `INVOKE_FAILED` | 适配器/实现执行失败 |
| `HOST_API_DENIED` | 试图使用未开放 Host API |

无对应能力却配置了意图：**禁止**静默成功；**必须**失败，或（仅白名单内）警告。

### 4.3 安全

1. **必须**只动环境配置的被测/代理；**禁止**改质衡系统时间/清质衡库。
2. 安装与危险配置权限 ≥ 环境管理；挂点**禁止**绕过总闸。
3. 密钥进环境 config，**禁止**进流程图。
4. 破坏性能力（存档点 restore、拨钟写等）：**必须**审计。
5. 启用**必须**执行 §10.3；未声明的能力**禁止**当作已提供。
6. 官方包走 zip / 发现目录；远程市场**后置**。

---

## 5. 与现有机制

> **人话**：现有功能哪些留核心、哪些迁进插件。

| 现有 | 插件化方向 |
| --- | --- |
| `snapshotBefore` / 续跑还原 | 意图留核心；**执行** → `qualitest.sut-db-archive-point`（CLI 为主） |
| 今日 `/test-support` 快照 | **迁入存档点插件后删除**被测端点与核心直调；不保留「核心回落 HTTP」 |
| `allowDestructiveReset` | **1:1** → `gate.destructive` |
| 拨钟 / 库存档点 | **插件 + 被测旁 CLI**（§2.3.2 通道 + §2.3.3）；用户自选启用；策略进环境 config，连接进 bind / 项目挂载 |
| 节点注册表 | 核心节点 + **`pluginAction`**（P2） |
| Delay / retryUntil | 留核心 |
| Webhook / 挡板 | 入站偏核心；有插件实现再做 |
| Script / MCP | 后期只读安全子集；MCP 装 agent → P4 |

---

## 6. 共存规则与常见坑

> **人话**：不同插件基本可以同时开；真正会冲突的只有界面槽和写入顺序。

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
- **同 UI 槽两家**：`ui-slot:` 碰撞 → `ISOLATION_MUTEX`。

---

## 7. 非目标

不做：Delay 冒充过期；改质衡服务器时间；无限私增 `FlowNodeType`；公网任意热装；DSH 无特权内核；任意全局 CSS/换 SPA；整仓并进 demo-host；把质衡存储/Flyway/成员权限当被测插件；**维护质衡开放业务能力名录**。

阶段规划见 ROADMAP；落地缺口以文首状态条与 **§10.8** 为准。

---

## 8. 相关链接

- 发布列车：**2.0.0** 与 [flow-contract-2.0.md](./flow-contract-2.0.md) 同发；ROADMAP 原 Exploring **6.2** 并入该列车；Later **7.4～7.6**、**5.5** 仍后置  
- 流侧协议入口 / 消费面：`flow-contract-2.0.md` §2.3、§7（`{protocol}-entry` 等与本稿 `pluginAction` 贡献面在 2.0 列车内对齐）。**现状**：引擎仅 type 识别骨架；codec / 该 kind 交付登记 / 可演示样本 **后置**。2.0 批 4 本轮验收以官方 **存档点**可装可跑为准（见 §10.8）  
- 写库快照主路径：`project-summary.md`（经存档点插件；旧 `HttpEndpointSnapshotAdapter` / 核心直调待清干净）  
- 被测 `/test-support`：demo-mall 历史端点——**迁完即删**，禁止核心回落  
- 运维金库（**不是**本插件）：`qualitest-demo-host` `data/golden` / daily-reset  
- 官方插件专文：仅各实现仓 `docs/design.md`（存档点 / 造数 / 执行代理 / 拨钟 / 网混沌）  
- 执行通道：`platform-ops-exec-channel.md`（**已落地** `local-exec`、`exec-agent`；契约中的 `ssh` **尚未实现**）  

---

## 9. 已定结论（追溯）

> **人话**：下表是设计讨论中已经拍板的点，正文已按它们改写；想再改，先改正文并更新本表。细则以 **§10** 为准。

| 条 | 结论 |
| --- | --- |
| A1 | hooks / 节点引用**本包**能力名；invoke **必须**带 pluginId |
| A2 / B3 | **禁止**环境 `prepareOrder`；阶段序写死；阶段内按 `hooks.order` |
| A3 | 契约字符串 **kebab-case** |
| A4 | **`ui-slot:…` 互斥**；`slot` 只管布局 |
| A5 | 短 id + 存储 `pluginId`/`contributionId` |
| A6 | 与产品 **2.0.0** 同发（流契约 + 插件一口切）；不再「不承诺版本」 |
| B1 | **无**全局能力唯一提供者；启用互斥仅 `ui-slot:` |
| B2 | 首版不做 primary / 写库竞争调度 |
| B4 | gate 在**插件能力对象**上；`remarks` 仅说明 |
| B5 | 启用互斥仅 `ui-slot:`；通道用 `channelProvider` |
| B6 / D2 | capabilities **只放运行且私有于包**；UI 看 `ui` |
| B7 | 近期仅示意 UI 路径；A/tokens **后置** |
| B8 | http-adapter / builtin / ops-agent；builtin 靠 id；http 用 routes |
| B9 | 默认 **fail_run**；P0 skip 白名单 = ∅ |
| B10 | ops-agent **默认 CLI**（install/uninstall/ping）；`runtime.agent.cli` |
| C1 | **先文档契约**；样本逐个落地（存档点已部分落地，拨钟尚未，见 §10.8） |
| C2 | `builtin` = 主仓实现 + 已登记说明书；官方 **ops-agent** 包**不**内嵌主仓 jar |
| C3 | 类型名 **`pluginAction`** |
| C4 | allowDestructiveReset **1:1** → gate.destructive |
| 持久化 | **包 JSONC ≠ 环境 SQL**；节点可用/失效 **即时算** |
| C5 | 质衡**不做**开放业务能力名录；说明书 **JSONC** |
| C6 | **被测特殊面→插件**（用户自选启用）；库存档点/拨钟 = **CLI**；`/test-support` **迁完即删**，不留核心回落 |
| C7 | Web **自己**不写被测盘；**MCP + 本机 Agent** 配质衡 + 落地 CLI；**经已运行的 exec-agent** 可由 Web 编排装业务 CLI / 写 bind；无代理的静默装机仍禁止 |
| C8 | **项目级挂载**（§2.5.1）；存档点 `bindAlias` 项目只读注入，环境不可改 |
| C9 | 拨钟 Prepare=`clock.set` **必须**环境 `prepareAt`（拨钟专文） |
| C10 | 平台 §2.3.3 **只留骨架**；字段全表以插件专文为准 |
| C11 | ops-agent：**载荷=业务 CLI**；**通道=核心**分发；默认 `local-exec`；分机优先 `exec-agent`；`ssh` 契约后置 |
| C12 | 通道专文留主仓；执行代理专文在 [plugin-sut-exec-agent](https://github.com/qualitest-hq/qualitest-plugin-sut-exec-agent/blob/main/docs/design.md) |
| C13 | **被测执行代理** = **正式官方插件**；同机不必启用 |
| C14 | **官方插件一插件一私有仓**；专文只在各仓 `docs/design.md`；主仓**不**再留专文/跳转页 |
| D1 | runtime.kind = **§2.3.1**；CLI = **§2.3.2** |
| D3 | 实现缺口对照 **§10.8**（不在本文另维护阶段验收清单） |
| 严谨化 | **§10 实现契约** |

---

## 10. 实现契约（对照执行）

> **人话**：这一章写给实现者，是「到底怎么校验、怎么调用」的硬规则。和前文冲突时，以本章为准。

### 10.1 不变量

1. 能力名**私有于** `pluginId`；质衡**禁止**维护开放业务能力名录。
2. `capabilities` ∩ UI 贡献 = ∅（UI 只在 `ui`）。
3. Invoker / UiRegistry 是唯一入口。
4. Host/Context 不得越权（§10.5）。
5. Prepare 阶段序不可被环境覆盖。
6. P0 挂点失败 → `fail_run`。
7. `pluginAction` 是唯一的插件步骤引擎类型。
8. 总闸由**本包能力对象**的 `gate` 推导；`remarks` 不参与判定。
9. `builtin` 若存在：说明书登记与主仓实现须同发版可用；ops-agent 官方包靠外置 zip/发现目录。  
10. 原因码以 §4.2 为准，禁止未列出的静默成功。
11. **被测特殊面的执行禁止留核心**：只经 `invoke(pluginId, …)`；未启用不得回落。
12. 宿主挂点只调度**已启用**且声明了对应 hook 的插件。
13. `ops-agent` **默认 CLI**：须能 uninstall 干净；策略进环境 `config`；被测连接默认进本机信任根；项目挂载键只读注入。

### 10.2 说明书字段约束（摘要）

| 字段 | 类型约束 |
| --- | --- |
| 文件名 | `qualitest.plugin.jsonc`（JSONC） |
| `id` | §1 字符集 |
| `version` | semver |
| `remarks` | 可选；仅展示 |
| `capabilities[]` | 对象：`name` 包内唯一；`effect`；可选 `gate` |
| `requires[]` | 仅 `plugin:<id>` |
| `isolation[]` | 见 §4.1；启用互斥仅 `ui-slot:<slug>` |
| `hooks[].name` | `onRunPrepare` \| `onRunFinalize` \| `onCheckpoint` \| `onRestore` |
| `hooks[].order` | 整数；默认 `100` |
| `hooks[].actions` | ⊆ 本包 `capabilities[].name` |
| `configSchema` | 可选；环境 `config` 必须通过 |
| `projectMount` | 可选；`fields` / `inject` / `inheritFrom` |
| `push.products[]` | 可选；`productId` + 制品相对路径 |
| `channelProvider` | 可选布尔；为 true 时可作为 exec-agent 通道 |
| `runtime.kind` | 三枚举 |
| `runtime.http.routes` | kind=http-adapter 时必须 |
| `runtime.agent.bundle` | kind=ops-agent 时必须 |
| `runtime.agent.cli` | kind=ops-agent 默认必须：`bin`、`pingArgs`；可选 `argMap` |
| `ui.env` | 可选；`badge` / `actions` / `panel.entry` |

能力写成「质衡开放名」→ `MANIFEST_INVALID`。说明书文件名须为 `qualitest.plugin.jsonc`。

### 10.3 启用校验算法（必须按序）

对「将启用插件 P，环境 E」（E 所属项目为 Proj）：

1. **Parse** → 失败 `MANIFEST_INVALID`。
2. **Project mount**（§2.5.1）：若 P 需要项目挂载，校验 Proj 上必填已齐 → 否则 `PROJECT_MOUNT_REQUIRED`；把只读键注入待校验 config 视图（环境提交值**不得**覆盖这些键）。
3. **Schema**：合并后的 `config` vs `configSchema` → `CONFIG_SCHEMA_INVALID`（含：Prepare 绑了 `clock.set` 则须有 `prepareAt` 等专文约束）。
4. **Kind**：builtin 注册表 / http routes / ops-agent bundle（同前）。
5. **Gate**：本包能力 `gate` **并集**查 E → 未开则 `GATE_DENIED`（有 destructive 能力则启用即须开总闸，即使本次只用只读能力——**宁严勿松**）。
6. **Requires**：每个 `plugin:` 须已启用 → 否则 `REQUIRES_UNSATISFIED`。
7. **ui-slot**：P 的每个 `ui-slot:*` 若已被**另一**已启用 `pluginId` 占用 → `ISOLATION_MUTEX`；若存在 `ui.chrome.banner` 且未声明任何 `ui-slot:*`，启用时**必须**自动加入 `ui-slot:flow.banner.{banner.id}`。
8. **Hooks**：每个 action ∈ 本包能力名 → 否则 `HOOK_ACTION_NOT_OWNED`。Prepare/Finalize 按 `hooks.order` 调度。
9. **Commit**：环境绑定；UiRegistry；登记「已启用 pluginId → 说明书」（**无**全局 capability→plugin 名录表）。

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
| `envConfig` | 该插件在环境中的 config（只读视图；已合并项目挂载只读键） |
| `projectMount` | 该插件在项目上的挂载块（只读；无则空） |
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
| 核心直调 snapshot/restore Adapter | **禁止**（P1 拆净；迁完删 `/test-support`） |
| ops-agent 无到达方式仍声称可用 | **禁止**（P0 至少 `local-exec` 能起业务 CLI） |
| 拆 gate 为多闸 | 暂 1:1 |

### 10.8 实现检查表（对照缺口）

**后端**

- [x] 说明书解析 + §10.2 校验（JSONC；首期子集）
- [x] §10.3 启用关键序 + 原因码（挂载 / gate / 包登记；非全序逐项）
- [x] 包登记 + 环境插件绑定落库（§2.6 实体；发现 upsert）
- [x] 按 pluginId 启用集调度（无全局能力名录）
- [x] zip 上传安装：`POST /project/pluginHost/install` → `{profile}/plugins/{pluginId}` 并登记
- [ ] Prepare/Finalize 调度 + §4.2 排序（本轮仅 Checkpoint/Restore 经存档点）
- [x] Invoker + ops-agent/`local-exec`（http-adapter / builtin 后置）
- [x] gate.destructive ↔ allowDestructiveReset
- [x] 审计：Invoke 日志含 pluginId/capability/source（时间线字段可再加深）
- [ ] Host/Context 白名单边界测试

**前端**

- [x] 环境插件启用 + 错误展示；项目挂载 bindAlias
- [x] 项目设置：上传 zip 安装到质衡本机（非被测机 CLI）
- [x] UiRegistry：徽章至少一种
- [x] `pluginAction` 引擎类型 + Handler（可用态按启用集即时失败；不落盘 enabled）
- [ ] 模板不可用占位 / 画布失效外观即时重算
- [x] ops-agent 未连通：跑流/Invoke 失败明示

**官方包**

- [x] 库存档点：专文 + `sdap` CLI（首期 MySQL）install/ping/create/restore
- [x] 项目挂载（存档点）
- [x] B 类 UI 可见（存档点徽章）
- [x] 2.0 批 4 样本路径：插件仓 README「最短上手」（同机 local-exec；zip/发现目录 → bindAlias → 启用 → 徽章 → snapshotBefore）
- [ ] 协议入口 `{protocol}-entry` 可演示消费面（codec / 交付登记）— **后置**，不挡批 4 存档点验收
- [ ] 拨钟样本
