# 【测完即删】库存档点插件 · 手工验收流程

> **临时文档**：验收完请删除本文件。  
> 覆盖：质衡 zip 安装 → 项目挂载 → 环境启用 → 被测机 `sdap` → 画布快照跑流。  
> 本地默认：质衡 Web `http://localhost:5180` · API `8800` · 靶场 `8801` · 账号 `admin` / `admin123`。

---

## 0. 入口总表（先认路）

| 标签 | 入口 | 路径 / 命令 | 干什么 |
| --- | --- | --- | --- |
| **E1 项目设置** | Web | 项目详情顶栏齿轮 **「项目设置」**；或测流列表/画布同名按钮 | zip 安装 + `bindAlias` 挂载 |
| **E1a** | 同上抽屉 | 卡片 **「安装插件包」** | 上传 zip → 质衡本机登记（**不是**装被测 CLI） |
| **E1b** | 同上抽屉 | 卡片 **「库存档点挂载」** | 写项目级 `bindAlias`（如 `demo-mall`） |
| **E2 环境管理** | Web | 项目详情环境选择器旁 **三条横线「环境管理」** | 开总闸 + 启用插件 |
| **E2a** | 环境抽屉 | 区块 **「被测数据还原」** | `allowDestructiveReset` ↔ `gate.destructive` |
| **E2b** | 环境抽屉 | 区块 **「平台插件」** | 开关 **启用库存档点** |
| **E3 画布** | Web | 测流画布顶栏 | 启用后应出现徽章 **「存档点」** |
| **E4 节点属性** | Web | 选中 HTTP 节点 → 属性区 | 勾选 **执行前打被测数据快照（snapshotBefore）** |
| **E5 跑流** | Web | 画布运行 / 场景跑 | 触发 checkpoint；失败续跑可选还原 |
| **E6 被测 CLI** | 本机终端 | `sdap`（见 §2） | 装 CLI、写 bind、本机 ping |
| **A1～A7** | HTTP API | 见文末附录 | 不走 UI 时的对账入口 |

**两套「安装」不要混：**

| | 质衡宿主（E1a） | 被测机（E6） |
| --- | --- | --- |
| 产物 | 解压到 `{profile}/plugins/{pluginId}` + 库表登记 | 二进制 `sdap` + bind 文件 |
| 谁写盘 | 质衡服务器（本机联调即同一台） | 够得着被测库的那台机 |
| 不做什么 | **不能**替你装 `sdap` / 写 bind | **不能**完成质衡包登记 |

说明：官方包**不再**内嵌主仓 classpath；须先走 **E1a zip 安装**（或把插件仓目录链到 `qualitest.plugin.discovery-dirs` / 安装根）才会出现在环境启用列表。

---

## 1. 前置

- [ ] 质衡后端 **8800**、前端 **5180** 已起；Flyway 已含 `V11__platform_plugin.sql`
- [ ] （可选）demo-mall **8801** 已起；库名一般为 `qualitest-demo-mall`（bind 里 database 填真实库名）
- [ ] 准备一个可写的测试环境（自行决定是否开还原总闸）
- [ ] 记下项目 ID：`testProjectId`（挂载 / bind 的 `projectId` 必须一致）

建议环境 `baseUrl`：`http://localhost:8801`

---

## 2. 【E6】被测机安装 `sdap` + bind

工作目录：`qualitest-plugin-sut-db-archive-point/`

```powershell
go build -o sdap.exe ./cmd/sdap
.\install.ps1
# 或把 sdap.exe 放到 PATH；质衡也可配 qualitest.plugin.agent-bin-dir
```

写 bind（Windows 默认目录）：

`%ProgramData%\sdap\etc\bind\demo-mall.json`

可从 `examples/bind-demo-mall.json` 复制后改：

```json
{
  "version": 1,
  "bindAlias": "demo-mall",
  "projectId": "<填质衡 testProjectId>",
  "targets": {
    "default": {
      "kind": "mysql",
      "host": "127.0.0.1",
      "port": 3306,
      "database": "qualitest-demo-mall",
      "user": "root",
      "password": "<你的密码>"
    }
  }
}
```

本机自检：

```powershell
sdap ping
# 期望 stdout 含 "version":"0.1.0"（须与说明书 / 登记版本一致）
```

- [ ] `sdap` 在 PATH 或已配 `agent-bin-dir`
- [ ] bind 文件名 = 即将挂载的 alias（`demo-mall.json`）
- [ ] `projectId` 与质衡项目一致
- [ ] `ping` 成功

---

## 3. 【E1a】质衡 zip 安装（宿主登记）

打 zip（根目录或一级子目录须含说明书）：

```powershell
cd qualitest-plugin-sut-db-archive-point
# 至少含 qualitest.plugin.jsonc；可附 agent/、examples/ 等
Compress-Archive -Path qualitest.plugin.jsonc,examples,README.md -DestinationPath ..\qualitest.sut-db-archive-point-0.1.0.zip -Force
```

**入口 E1 → E1a：**

1. 打开项目 → 顶栏 **项目设置**
2. **安装插件包** → 拖入 zip → **安装到质衡**
3. 成功提示 `qualitest.sut-db-archive-point@0.1.0`；列表出现该包
4. 记下页面上的 **安装目录**（默认约 `D:/qualitest/uploadPath/plugins`，以页面为准）

核对磁盘：`{installDir}/qualitest.sut-db-archive-point/qualitest.plugin.jsonc` 存在。

负向（可选）：

- [ ] 非 zip / 无说明书 → 明确失败文案
- [ ] >10MB → 被 multipart 或服务拒绝

---

## 4. 【E1b】项目挂载 `bindAlias`

仍在 **项目设置** → **库存档点挂载**：

1. 填 `demo-mall`（与 bind 文件名一致，无 `.json`）
2. **保存挂载**

- [ ] 保存成功
- [ ] （可选）故意留空再去做 E2b，启用应失败 / 提示缺挂载（`PROJECT_MOUNT_REQUIRED` 一类）

---

## 5. 【E2】环境：总闸 + 启用插件

**入口 E2 → E2a / E2b：**

1. 项目详情 → 选中目标环境 → **环境管理**
2. **前置 URL**：`http://localhost:8801`（按实际）
3. **被测数据还原** → 打开 **允许还原被测数据**
4. **平台插件** → 打开 **启用库存档点**
5. 保存环境其它字段（若有未保存项）

期望：

- [ ] 启用成功 toast
- [ ] 缺 bindAlias 时启用失败且有可读错误
- [ ] 停用开关可关掉绑定

---

## 6. 【E3】画布徽章

打开该项目下任意测流画布（场景绑到刚配置的环境）：

- [ ] 顶栏出现插件徽章文案 **「存档点」**（title 可含 pluginId）
- [ ] 停用插件后刷新/重进，徽章消失

---

## 7. 【E4】节点勾 snapshotBefore

1. 选中一个会写库或可观察的 HTTP 节点
2. 属性区勾选 **执行前打被测数据快照（snapshotBefore）**
3. （可选）填表范围，如 `orders`；留空=不限制表列表

提示态：

- [ ] 环境未开总闸：提示 checkpoint 将**静默跳过**
- [ ] 已开总闸：提示依赖插件 + bindAlias、串行跑

场景侧若有「失败后还原」类开关，按面板说明打开（需配合总闸；**同环境串行跑**）。

---

## 8. 【E5】跑流验收

1. 保存画布
2. 跑场景 / Run
3. **预跑校验**：有 snapshot + 已开总闸但未启用插件 → 应拦下，文案类似：  
   `当前环境已开启数据还原且流程含快照节点，请先启用库存档点插件并完成项目挂载（bindAlias）`
4. 插件已启用且 `sdap` 正常：节点执行前应走 `archive.create`（看质衡日志 / 被测侧 dump 目录）
5. （可选）故意让节点失败 → 暂停 → 选 **还原再重试**，应走 `archive.restore`

日志关键字（后端）：`pluginId` / `archive.create` / `archive.restore` / `local-exec` / `sdap`

- [ ] 正常路径 checkpoint 成功
- [ ] CLI 不在 PATH：Invoke 失败且错误可读（非静默当成功）
- [ ] 总闸关 + 仍勾 snapshot：不炸流，checkpoint 跳过
- [ ] 并行双开同一环境（可选破坏性）：确认会互相踩库——验收时**不要**这么干，只确认文案提醒过

---

## 8b. 场景造数（`sdss`）最短验收

> 种子 SQL **不要**指望造数插件包内置；用 demo-mall 部署到约定目录。

1. 已完成存档点：bind + 项目挂载 + 环境启用存档点 + 开总闸  
2. 被测机：
   ```powershell
   cd qualitest-plugin-sut-db-scenario-seed
   go build -o sdss.exe ./cmd/sdss
   .\install.ps1
   cd ..\qualitest-demo-mall\sql\seed
   .\deploy_to_sdss.ps1
   ```
3. 质衡：zip 安装造数说明书 → 环境启用造数，填 `scenarioId=S01`  
4. `sdss ping --config …`（或跑流前 Prepare）：应探通库 + 看到 seedRoot/manifest  
5. 跑流：时间线应出现「插件调用 · seed.apply」审计步；库内数据符合 S01  

- [ ] 未部署种子时 ping/apply 失败可读（`message` 字段）  
- [ ] 未开总闸无法启用或 apply 报 `GATE_DENIED`  
- [ ] 未启用存档点时启用造数 → `REQUIRES_UNSATISFIED`

---

## 8c. 分机执行代理最短验收

1. 边车 / 本机：`qt-sut-exec-agent serve --listen :7920 --token demo-token`（非本机回环**必须**带 token）  
2. 环境启用「执行代理」，`baseUrl=http://127.0.0.1:7920`，token 一致 → 点 **代理探活**  
3. 点 **推送安装 sdap**（productId 必须是 `sdap`，不是历史错写 `sdsp`）→ 代理机出现 `{SDAP_OPT}/bin/sdap`  
4. （可选）推送安装 `sdss`；种子仍用靶场目录挂载/部署，不经插件 zip  
5. 停掉本机 PATH 里的 sdap，只靠代理代跑：再跑一次存档点 checkpoint，应成功  

- [ ] productId=`sdap` 安装成功；误传其它 id 被拒  
- [ ] 无 token 监听 `:7920` 启动失败  
- [ ] `127.0.0.1:7920` 允许空 token（仅本机联调）

---

## 9. 建议验收顺序（最短路径）

```text
E6 sdap+bind+ping
  → E1a zip 安装（可选但建议）
  → E1b bindAlias=demo-mall
  → E2a 开总闸 → E2b 启用插件
  → E3 看徽章
  → E4 勾 snapshotBefore
  → E5 跑流 / 还原
  → （可选）8b 造数 / 8c 分机代理
```

测完可逆清理：

1. E2b 停用插件；E2a 关总闸  
2. E1b 清空挂载（可选）  
3. 删 `%ProgramData%\sdap\etc\bind\demo-mall.json`；可选卸载 `sdap`  
4. 删 `{installDir}/qualitest.sut-db-archive-point` 后重启：包应不再被自动登记（除非仍在 discovery-dirs）  
5. **删除本文件** `docs/_TMP_plugin-manual-test.md`

---

## 附录 · API 入口（A）

基址：`http://localhost:8800`，Header：`Authorization: Bearer <token>`  
权限：包相关 `project:testPluginPackage:*`；绑定 `project:testPluginEnvBinding:*`；挂载 `project:testPluginProjectMount:*`

| 标签 | Method | Path | 说明 |
| --- | --- | --- | --- |
| **A1** | GET | `/project/pluginHost/packages` | 已登记包 |
| **A2** | GET | `/project/pluginHost/installDir` | 安装根目录 |
| **A3** | POST | `/project/pluginHost/install` | multipart 字段名 `file` |
| **A4** | POST | `/project/pluginHost/discover` | 手动再发现 |
| **A5** | GET/PUT | `/project/pluginHost/project/{projectId}/mount?pluginId=qualitest.sut-db-archive-point` | 读/写挂载 |
| **A6** | POST | `/project/pluginHost/env/{envId}/enable` | body: `{ "pluginId":"qualitest.sut-db-archive-point" }` |
| **A7** | POST | `/project/pluginHost/env/{envId}/disable` | 同上 |
| **A8** | GET | `/project/pluginHost/env/{envId}/bindings` | 环境绑定列表 |
| **A9** | GET | `/project/pluginHost/env/{envId}/ui` | 徽章等 UI 投影 |

配置项：`qualitest.plugin.install-dir` / `QUALITEST_PLUGIN_INSTALL_DIR`（空 → `{qualitest.profile}/plugins`）；`qualitest.plugin.agent-bin-dir`；zip ≤ **10MB**。

库表（只读核对）：`test_plugin_package` · `test_plugin_env_binding` · `test_plugin_project_mount`。
