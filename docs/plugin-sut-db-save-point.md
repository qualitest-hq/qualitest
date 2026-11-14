# 插件设计：被测库存档点（`qualitest.sut-db-save-point`）

> Exploring · 随 [platform-plugins.md](./platform-plugins.md)。契约文档；实现后置。  
> **语言**：Go（单二进制 `sdsp`；与拨钟 CLI 样本同策）。仓库落地后置。  
> **范围**：被测**状态存档**——首期 SQL（MySQL）+ Redis；同一 `saveId` 可一次打齐、一次按序恢复，方便断点续跑。  
> **不是** demo-host 运维金库 / SQL 事务 `SAVEPOINT`。ephemeral snapshot **迁入本插件**（或同能力 http-adapter）。  
> **命名**：id=`qualitest.sut-db-save-point`（db=数据侧，含 SQL/Redis 等）；CLI=`sdsp`。**不**再按引擎/存储拆插件 id。

## 1. 要做什么 / 怎么用

测流失败或断点续跑时，对被测侧**声明过的状态源**（库、缓存等）做**即时存档 / 读档**。须连目标 → **ops-agent + CLI**（`sdsp`），无常驻进程。

断点续跑默认：**一次 `save.create` 打齐本项目允许的全部 store**；一次 `save.restore` 按约定顺序全部灌回。也可带 `storeKey`（CLI `--db`，历史同义）只操作其中一个。

### 1.1 「挂到项目上」到底是什么意思

**不是**把 `sdsp` 程序装进质衡项目里。  
**是**：在质衡这个**项目**上存一个字段——「本项目只用哪个绑定文件名」。

```text
质衡项目（如「演示商城」，id=12）
  └─ 项目设置里多一块「库存档点」
        bindAlias = demo-mall          ← 就这行叫「挂上」
        storeKeys = [order, inventory, cache]  ← 可选；省略=bind 内全部 targets

被测机（和质衡项目不是一回事）
  └─ C:\ProgramData\sdsp\etc\bind\demo-mall.json
        里面写 projectId:"12" 和各 store 连接（mysql / redis / …）
```

| 存在哪 | 存什么 | 谁改 |
| --- | --- | --- |
| 质衡 **项目**表/项目设置 | `bindAlias`（必填）等 | 项目管理员，改一次全项目共用 |
| 质衡 **环境**插件启用行 | 开没开本插件、`bindEnvKey`/`storeKey` 等 | 环境配置；**别名从项目抄来，环境改不了** |
| 被测机 bind 文件 | 真正的 host/账号 | 被测运维 |

跑流时：平台取出**当前项目**的 `bindAlias` + **当前环境**的插件配置 + `projectId`，写进下发给 `sdsp` 的 `--config`。  
CLI 打开 `demo-mall.json`，发现文件里的 `projectId` 必须是 `12`，否则拒绝——所以项目 A 用不了项目 B 的文件。

界面示意（实现后置）：**项目设置 → 库存档点 → 绑定别名** 输入 `demo-mall` 保存。  
之后在某环境里「启用插件 `qualitest.sut-db-save-point`」时，表单上别名是只读的 `demo-mall`。

### 1.2 怎么用（步骤）

```text
① 被测机：install sdsp（全机一次，与「哪个质衡项目」无关）
② 被测机：写 bind/demo-mall.json（含 projectId=12 与 targets，可含 redis）
③ 质衡：打开该项目 → 项目设置 → 库存档点 → bindAlias=demo-mall  （挂上）
④ 质衡：某测试环境 → 启用本插件（自动带上 demo-mall）→ 开破坏性总闸 → ping
⑤ 跑流：save.create / save.restore（默认整包；可带 storeKey 单源）
⑥ 不用了：环境停用插件；可 uninstall CLI（默认不删 bind）
```

质衡**不填**密码。`explicit` 仅本机调试。可用 http-adapter 替代 CLI。

**边界**：不做 golden/daily-reset；不按引擎/存储拆插件 id；何时存档由核心定；**跨 store 不原子**（与多库相同：尽力按序，部分失败策略由核心定）。

### 1.3 Web 做不到一条龙时，MCP 怎么补（设计意向）

Web 跑在质衡服务器上，**写不进**被测机的 `ProgramData`/`/etc`。  
Cursor 等 Agent 若在**被测机/开发机本机**干活，可以：装 CLI、写 bind、再调质衡 MCP 改项目设置——这才是「一条龙」。

| 谁做 | 做什么 |
| --- | --- |
| **本机 Agent**（非质衡进程） | `install`、写 `{etc}/bind/{alias}.json`、本机 `sdsp ping` |
| **质衡 MCP Tool** | 写项目 `bindAlias`、环境启用插件、开总闸、查状态；**不**假装能远程改被测磁盘 |
| **跑流时** | 仍走 `Invoker` → `sdsp save.*`，不是 MCP 直接 dump |

**要不要给插件开放 Function Tools？**

- **不要**一上来让每个插件任意往全局 MCP 名录塞工具（和「不做开放业务能力名录」同理，易膨胀、难鉴权）。  
- **推荐两层**：  
  1. **先补核心 Tool**（或给已有 Tool 加参）：如项目挂载 `bindAlias` / `storeKeys`、环境启用本插件、返回安装说明与 bind 模板（只读文本）。`upsert_project_env` 等可扩展，不必为每插件新造一套。  
  2. **后置**：说明书可选 `mcpTools[]`（私有于 `pluginId`），仅当项目开关允许且环境已启用该插件时才 `tools/list` 露出；实现仍调 `Invoker` 或只读配置 API。开关、确认、禁静默 —— 对齐平台草案 MCP 装 agent → **P4**。  
- 插件贡献的 Tool **禁止**绕过总闸 / 项目挂载校验；破坏性仍要人确认。

一句话：MCP 负责「配质衡 + 指导/协助本机落地」；真正存档读档仍是插件 CLI。

## 2. 身份与能力

| 项 | 值 |
| --- | --- |
| id / displayName | `qualitest.sut-db-save-point` / 被测库存档点 |
| runtime | `ops-agent` + `embedded`；`bin=sdsp` |
| isolation | `sut-db`；`ui-slot:flow.badge.db-save-point` |

| name | effect | gate | 说明 |
| --- | --- | --- | --- |
| `list` | read | none | 列本项目+环境存档（整包 `saveId`） |
| `save.create` | write | none | 即时 dump（写磁盘；默认整包，不改业务数据语义上的「业务写入」） |
| `save.restore` | write | destructive | 按序读档灌回；`dbCompete` |
| `save.delete` | write | none | 删本地档，不改远端 store |

能力私有于本包。说明书示意见 [platform-plugins.md §2.3.3 A](./platform-plugins.md)。

## 3. 配置（参数全表）

分四层：**项目挂载** → **环境插件 config** → **Invoke 参数** → **平台下发给 CLI 的文件**（含注入字段）。账号只在被测机 bind（§5），不进质衡表单。

**用词**：逻辑名统一称 **storeKey**（`^[a-z][a-z0-9-]{0,63}$`）。配置/CLI 历史字段 `dbKey` / `--db` **同义**，可继续用。

### 3.1 项目上存的字段（见 §1.1）

落在**项目**配置（不是环境插件 zip，也不是流程图）。未配置 `bindAlias` → 环境启用本插件失败。

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `bindAlias` | **是** | 绑定文件名；环境启用时只读带入 |
| `bindAliasAllowlist` | 否 | 极少用；多个别名白名单 |
| `storeKeys`（或历史名 `dbKeys`） | 否 | 允许参与整包存档的键；省略=bind 内全部 targets |

### 3.2 环境插件 `config`（`configSchema`）

**`db.resolve=bind-file`（测试流默认）** — 字段前缀 `db` 为历史名，含义是「解析被测连接」，含 Redis。

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `db.resolve` | 是 | 固定或默认 `bind-file` |
| `db.bindAlias` | 是* | *由项目带入，环境只读；与项目挂载一致 |
| `db.bindEnvKey` | 否 | 选 bind.`envs` 剖面（如 `staging`）；不换文件 |
| `db.dbKey` / `db.storeKey` | 否 | 单源操作时的默认 store；**整包存档时省略** |
| `storeRoot` | 否 | 覆盖数据根 `{var}`；**不能**改 bind 搜索根 |
| `defaultSaveId` | 否 | 挂点未传 `saveId` 时兜底（少用） |

```jsonc
{
  "db": {
    "resolve": "bind-file",
    "bindAlias": "demo-mall",
    "bindEnvKey": "staging"
    // 断点整包：不设 dbKey；单源调试才设
  },
  "storeRoot": null
}
```

**`db.resolve=explicit`（仅本机调试）** — 正式测流可拒启用；一次只描述**一个** store：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `db.resolve` | 是 | `explicit` |
| `db.kind` | 是 | `mysql` \| `postgres` \| `mariadb` \| `redis` \| … |
| `db.host` | 是 | |
| `db.port` | 否 | 默认随 kind（3306 / 5432 / 6379） |
| SQL：`db.database` / `db.user` / `db.password` | kind 为 SQL 时 | **禁止**进流程图 |
| Redis：`db.redisDb` / `db.keyPrefix` / `db.password` | kind=`redis` 时 | `redisDb` 默认 0；`keyPrefix` 省略=整逻辑库 |
| `db.options` | 否 | SSL、socket 等 |
| `db.dbKey` | 否 | 逻辑名，缺省 `default` |
| `storeRoot` | 否 | 同上 |

`bind-file` 下即使误传 host/password → CLI **忽略**。

### 3.3 Invoke 参数（挂点 / 节点 / 工具条）

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `saveId` | create/restore/delete **是** | 存档名；`^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$` |
| `storeKey`（或 `dbKey`） | 否 | 省略=**整包**（项目 `storeKeys` ∩ bind targets）；指定=只打/只灌这一个 |
| `force` | 否 | 仅 create：覆盖已存在 |

断点续跑挂点：**省略 storeKey**，一次 invoke 打齐/按序恢复。

### 3.4 平台下发 CLI 的 `--config`（合并注入）

在环境 config 基础上**必须注入**（用户不填）：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `pluginId` | 是 | `qualitest.sut-db-save-point` |
| `projectId` | 是 | 存档目录 + bind 校验 |
| `envId` | 是 | 存档目录 |
| `runId` | 否 | 审计 |
| `allowDestructive` | 是 | 总闸投影；false 时 restore 拒 |
| `db.*` | 是 | 见 3.2（已解析项目 bindAlias） |
| `storeKeys` | 否 | 由项目挂载注入；整包时 CLI 只处理列表内键 |
| `storeRoot` | 否 | null=用 `{var}` 默认 |

### 3.5 多 store / 多 kind（摘要）

| | 约定 |
| --- | --- |
| 声明 | bind.`targets.{storeKey}`，每项必有 `kind` |
| `storeKey` | `^[a-z][a-z0-9-]{0,63}$`；单源缺省 `default` |
| 路径 | `{var}/{projectId}/{envId}/{saveId}/meta.json` + `{storeKey}/…` |
| SQL kind | `mysql` 首期；`postgres` / `mariadb` 后置 |
| 缓存 kind | `redis` 首期（单机）；Cluster/Sentinel 后置 |
| 整包 create | 按 bind targets 声明顺序（过滤 `storeKeys`）逐个 dump；任一步失败 → 整次失败（临时目录不发布） |
| 整包 restore | 默认顺序：**先全部 SQL kind，再 redis**（同 kind 内按声明序）；可用 bind.`restoreOrder` 覆盖 |
| 一致性 | **不**承诺跨 store 事务；只承诺同一 `saveId` 清单按序尽力 |

## 4. CLI（`sdsp`）

每次新进程；stdout 一个 JSON；日志 stderr；禁止打密码。`pingArgs: ["ping"]`。

### 4.1 总表

| 命令行 | 作用 | 质衡 |
| --- | --- | --- |
| `sdsp ping` | 浅检 | 装完自检 |
| `sdsp ping --config <path> [--db <storeKey>]` | 探活（指定则单源；否则对允许的 targets 逐个） | 启用/周期探活 |
| `sdsp list --config <path>` | 列整包存档 | `invoke list` |
| `sdsp save.create --config <path> --id <id> [--db <storeKey>] [--force]` | 存档（默认可整包） | `invoke save.create` |
| `sdsp save.restore --config <path> --id <id> [--db <storeKey>]` | 读档 | `invoke save.restore` |
| `sdsp save.delete --config <path> --id <id>` | 删整包目录 | `invoke save.delete` |
| `sdsp --help` | 用法 | — |

| 选项 | 说明 |
| --- | --- |
| `--config` | 除浅检 ping/help 外必须 |
| `--id` | create/restore/delete |
| `--db <storeKey>` | 单源；省略则整包（create/restore/ping） |
| `--force` | 仅 create 覆盖已存在 |

字段以 **§3.4** 为准；示意：

```jsonc
{
  "pluginId": "qualitest.sut-db-save-point",
  "projectId": "12", "envId": "34", "runId": "1001",
  "storeRoot": null,
  "storeKeys": ["order", "inventory", "cache"],
  "db": { "resolve": "bind-file", "bindAlias": "demo-mall", "bindEnvKey": "staging" },
  "allowDestructive": true
}
```

### 4.2 行为要点

| 命令 | 要点 |
| --- | --- |
| `ping` | 解析 bind；按 `--db` 或 storeKeys 连通。stdout 含各 `storeKey`+`kind`（无密码） |
| `list` | 扫 `{projectId}/{envId}/*/meta.json` |
| `save.create` | 无 `--db`：整包写入 `{saveId}.tmp/` 再 rename；有 `--db`：只写该 store（仍落在同一 `saveId` 目录树，meta 标注 scope） |
| `save.restore` | 校验 allowDestructive、档、各 store 的 kind/storeKey；按 §3.5 顺序灌回 |
| `save.delete` | 删整个 `{saveId}/` |

**按 kind 的落地（设计，实现后置）**

| kind | create | restore |
| --- | --- | --- |
| `mysql`（等 SQL） | 逻辑 dump（工具链后定） | 灌回该库；校验 `meta.kind` |
| `redis` | 导出指定 `redisDb`；若有 `keyPrefix` 则只导匹配键（**尽量保留 TTL**） | 先按约定清理范围内键，再写入；非整实例误伤其它前缀 |

成功 exit 0 + `{ok:true,…}`；失败非 0 + `{ok:false,code,message}`。  
常见 code：`BIND_*`、`GATE_DENIED`、`SAVE_EXISTS`/`SAVE_NOT_FOUND`、`KIND_MISMATCH`、`NAMESPACE_MISMATCH`、`TOOLING_MISSING`、`STORE_UNREACHABLE`、`CONFIG_INVALID`、`BUSY`、`BIND_ALIAS_NOT_ALLOWED`、`PARTIAL_RESTORE`（若核心允许记部分成功时）。

实现：同命名空间写建议目录锁；create 先 `{id}.tmp/` 再 rename；超时由平台杀进程。

### 4.3 安装与路径

| | Windows | Linux |
| --- | --- | --- |
| `{etc}` | `%ProgramData%\sdsp\etc` | `/etc/sdsp` |
| `{var}` | `%ProgramData%\sdsp\data` | `/var/lib/sdsp` |
| `{opt}` | `%ProgramFiles%\sdsp` | `/usr/local/lib/sdsp` |

```text
{etc}/bind/{bindAlias}.json
{etc}/secrets/…
{var}/{projectId}/{envId}/{saveId}/meta.json
{var}/{projectId}/{envId}/{saveId}/{storeKey}/…   # dump + 分项 meta
{opt}/bin/sdsp[.exe]
```

`meta.json`（整包）示意：

```jsonc
{
  "saveId": "cp-001",
  "createdAt": "2026-11-14T10:00:00Z",
  "scope": "all",                 // 或 "single"
  "stores": [
    { "storeKey": "order", "kind": "mysql" },
    { "storeKey": "inventory", "kind": "mysql" },
    { "storeKey": "cache", "kind": "redis" }
  ],
  "restoreOrder": ["order", "inventory", "cache"]
}
```

- 不是项目 Git 根目录；可用 `SDSP_ETC`/`VAR`/`OPT` 覆盖（仅被测机侧）。  
- 质衡 `storeRoot` 只能改 `{var}`，**禁止**改 `{etc}`。  
- `install.(ps1|sh)`：装 `{opt}`，建空 bind/var，可附 `_example.json`；**不**自动按项目生成 bind。  
- `uninstall`：只卸程序；默认保留 bind/存档（`--purge-data` 另议）。  
- bind 权限：运维可写，跑 CLI 账号可读；容器建议只读挂载 `{etc}`。  
- **禁止** Invoke 远程改 `{etc}`；禁止默认扫 cwd/项目根 `.qt-bind.json`。

## 5. 绑定文件（信任根）

连哪以被测机文件为准；质衡改不了 host。防的是表单填错；防不住能改 bind 的人。

`bindAlias`：`^[a-z][a-z0-9-]{1,63}$`；机上唯一；与文件名、JSON 内字段一致。

```jsonc
{
  "version": 1,
  "bindAlias": "demo-mall",
  "projectId": "12",
  "targets": {
    "default": {
      "kind": "mysql",
      "host": "127.0.0.1", "port": 3306,
      "database": "demo_mall", "user": "dump_user", "password": "***"
    },
    "order": {
      "kind": "mysql",
      "host": "127.0.0.1", "port": 3306,
      "database": "demo_order", "user": "dump_user", "password": "***"
    },
    "inventory": {
      "kind": "postgres",
      "host": "127.0.0.1", "port": 5432,
      "database": "demo_inv", "user": "dump_user",
      "passwordFile": "C:\\\\ProgramData\\\\sdsp\\\\etc\\\\secrets\\\\inv.pw"
    },
    "cache": {
      "kind": "redis",
      "host": "127.0.0.1", "port": 6379,
      "redisDb": 0,
      "keyPrefix": "mall:",              // 省略则该 redisDb 整库
      "passwordFile": "C:\\\\ProgramData\\\\sdsp\\\\etc\\\\secrets\\\\redis.pw"
    }
  },
  // 可选：覆盖默认「先 SQL 后 redis」
  "restoreOrder": ["order", "inventory", "cache"],
  // 可选：按测试环境覆盖某 storeKey 的字段（深度合并到 targets）
  "envs": {
    "staging": {
      "order": { "database": "demo_order_staging" },
      "cache": { "redisDb": 1, "keyPrefix": "mall-staging:" }
    }
  },
  "envAliases": { "34": "staging" }
}
```

契约从一开始就用 `targets`（单源也写 `targets.default`），每项**必须**有 `kind`。  
（若实现期遇到仅含历史字段 `engine` 的样本：可把 `engine` 视为 `kind` 的别名；**新文档只写 `kind`。）

选连接：定 `storeKey` → 取 `targets[storeKey]` → 若有 `bindEnvKey`/`envAliases` 则合并 `envs[…][storeKey]`。  
无此键 → `STORE_KEY_UNKNOWN`。`password`/`passwordFile` 二选一。

解析：读文件 → 校验别名/projectId → 选 store(s) → 按 kind 干活。  
可选：仅本机 host；库内标记。

**Redis 约定（摘要）**

| 项 | 约定 |
| --- | --- |
| 范围 | `keyPrefix` 优先；省略=该 `redisDb` 全部键 |
| TTL | create/restore **应**尽量保留过期时间 |
| 不存 | 可重建的纯加速缓存可由项目不列入 `storeKeys` |
| 误伤 | restore 只清理「本档范围内」的键，禁止无前缀时误 `FLUSHALL` 整实例（无前缀时最多 `FLUSHDB` 当前逻辑库，且须在 remarks/实现里写死） |

## 6. 与质衡衔接

1. 项目挂 `bindAlias`（及可选 `storeKeys`）；环境启用过 schema；含 restore → 开破坏性总闸。  
2. 被测机已装 CLI + bind 文件；`ping` 成功，UI 只读展示各 store（kind + 目标摘要）。  
3. 挂点断点续跑：默认**不带** storeKey，一次 `save.create` / `save.restore`；单源调试才带键。部分失败策略由**核心**定。  
4. 未启用却依赖存档 → 失败；禁止核心直调 Adapter dump。  
5. UI：徽章「存档点」。

## 7. 非目标 / 待定

- 不做：golden/daily-reset；按 kind 拆插件 id；无 bind 却声称保证打对目标；远程改 bind；跨 MySQL/Redis **原子**事务。  
- 不做：首期 Redis Cluster/Sentinel；任意「其它缓存产品」未列 kind 前不承诺。  
- 首期：`mysql` + `redis`（单机）+ `bind-file` + 整包 saveId 即可支撑断点续跑；`postgres` 等紧随。  
- 待定：各 kind 客户端/工具链细节、`--purge-data`、macOS 默认根、Adapter 切换节奏、单源 create 与整包 meta 的合并规则细项。  
