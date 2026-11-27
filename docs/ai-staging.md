# AI Staging / Diff

> **用途**：改测试流图画布的产品路径——提案如何出现、如何确认、何时才算落盘。  
> **不是**：节点字段字典、鉴权口径、测试勾选表。  
> 英文：[ai-staging.en.md](./ai-staging.en.md)

概念地图：[project-summary.md](./project-summary.md) · 节点：[test-flow-nodes.md](./test-flow-nodes.md) · 验收铁律：手册 §B.0.1

---

## 1. 为什么必须 Staging

AI **默认不直接写库**。Web 助手按单元调用 `submit_*`（如 `submit_http_node`，`op=add|update`）后，前端把累积 patch 变成 **Staging 单元**（加节点 / 改节点 / 加边 / 删节点或边 / 场景字段等）。真人（或代点的 Agent）逐项 **✓ 确认** 或 **✕ 取消**，再点 **保存**，`graph_json` 才持久化。

```text
自然语言（或「AI 修复」）
  → 模型调工具（search_apis / get_api_details / …）
  → 多次 submit_*（每次 1 个 Staging 单元）
  → 画布 Staging 黄条 / 单元
  → ✓ 确认（必落画布；图 error 进校验条，不挡 ✓）
  → 保存（画布可见节点；有 error 也可存）
```

**例外：全自动**（AI 面板「半自动 | 全自动」，默认半自动）：请求带 `autopilotEnabled=true` 时注入 `run_test_flow`（跑前/回合结束**自动落盘**，无独立 commit 工具），且 `upsert_asset_variables` / `upsert_auth_profile` **直接写库**。隐式落盘门槛与人手**保存**相同（只拦 JSON 无法解析）；双开始节点、地板/结构/断言等 errors 保存不硬拦——开跑时再硬拦；无 schema / 字段是否存在设计期静默。造流/修复类请求完成后模型应主动 `run_test_flow`（纯答疑除外）；同会话内 upsert → `submit_*` → `run_test_flow` → 失败再修（最多再修 2 轮）。落盘成功后 SSE `graphCommitted`，前端清 Staging 并 reload 画布；`run_test_flow` 触发后 SSE `runStarted`，与人手共用轮询边跑边亮（执行中步骤即可读）。半自动则仍走上文 Staging ✓ → **人手保存**；素材与鉴权提案在聊天侧确认。只拨开关不发送请求不会跑流。

**MCP 默认只读**：无写工具。项目设置开启「允许 MCP 自动写流」后，可经 MCP 调用 `submit_*` / upsert（每次成功 submit 立即落盘）；另开「允许 MCP 自动跑流」后可调 `run_test_flow`；开启「允许 MCP 导入接口」后可调 `import_apis`（只控制导入接口，不控制改图与跑流）。MCP 与 Web 全自动落盘共用外部变更同步语义（`graphCommitted` 等）：打开中的画布经 SSE 轻量跟上，脏稿/Staging 时不静默覆盖。

---

## 2. 确认规则

| 动作 | 口径 |
| ---- | ---- |
| ✓ 确认 | 接受该单元并**必落画布**（请求成功即有 `graphJson`）。依赖顺序 / 图结构 / 断言路径**不挡 ✓**；问题进画布校验条（`ERROR`+`WARNING`，随 `validate?stage=canvas` 刷新） |
| ✕ 取消 | 丢弃该单元，不落盘 |
| 删除类 ✓ | **唯一确认**，无二次 `MessageBox`；勿对 Staging 边按键盘 Delete 当确认 |
| 保存 | 可带设计期问题落盘（仅拦 `GRAPH_JSON_INVALID`）；Staging 应清零；顶栏「nodes 为空」= 库里仍是空图，**不算造流成功** |
| 运行 | 生效 `severity=ERROR` 硬拦；`WARNING` 只提示（目录内码可由「流检查」改级；解析地板除外） |
| 仅保存已确认 | 未确认项会丢，易得半截图；应先去确认或全部 ✕ 后新建流 |
| 清除托管头 | 剥掉节点上误落的设计期托管头，**仍进 Staging**，不是静默写库；开跑时按项目配置再补 |

画布被侧栏挡住时：关侧栏 → 拖拽 / 小地图 / 适应视图，再点 ✓。禁止 `force` 点视口外控件冒充确认。

---

## 3. 本轮没有 Staging

本轮助手消息下**没有** Staging 变更摘要（`explainOnly`）= 模型只写了方案，**没调** 任何 `submit_*` 单元工具。画布不会变。

常见原因：同会话从零搭长流、反复拉超大 `get_api_details`、步数将尽。

**例外（全自动）**：隐式落盘清空 capture 后也会 `explainOnly=true`，此时以助手气泡「工具轨迹」或 `result_meta_json.toolTrace` 为准（见 §8）。

**测法 / 用法**：新建对话 + 短提示 + 显式 API id；无 Staging 摘要时让 AI「请按单元 submit 落盘」，不要当已经改图。

---

## 4. Web 助手 vs MCP

| | Web AI 面板 | MCP（默认） | MCP（项目开启自动写流） |
| --- | --- | --- | --- |
| 读接口 / 流摘要 / Run 失败 | 有 | 有（另有 `list_flows` / `get_flow`） | 同左 |
| `list_project_auth_profiles` | **有** | **有**（只读） | 同左 |
| `submit_*` 单元写工具 | **有** | 无 | **有**（每次成功即落盘） |
| `upsert_asset_variables` | **有**（半自动：提案；全自动：直写） | 无 | **有**（直写） |
| `upsert_auth_profile` | **有**（半自动确认 / 全自动直写） | 无 | **有**（直写） |
| `append_api_design_hints` | 有 | 无 | **有** |
| `import_apis` | 无 | 无 | 无（须另开「允许 MCP 导入接口」） |
| `run_test_flow` | **有**（须选「全自动」） | **无** | **有**（须另开自动跑流） |

默认改图画布仍走 Web。开启 MCP 自动写后 Cursor 可端到端造流/修流；须带已有 `testFlowId`。

---

## 5. 设计期门禁（确认 / submit / 保存 / 运行）

错误串格式：`CODE: 人类文案`。前端认冒号前的 CODE。  
权威总表（severity / 收集器 / CODE 列）：[flow-contract-2.0.md §2.2 门禁矩阵](./flow-contract-2.0.md)（编排：`FlowGraphValidateService`）。本节只写 AI/Staging 侧要点，不重复揉表。

**门槛摘要**

| 动作 | 硬拦什么 | 展示 |
|---|---|---|
| Staging ✓ / submit 收单 | submit **仅地板**（缺 id / 未知引用 / update 空数组误清空等）；结构·鉴权·断言**不挡** | 合进画布后靠校验条 |
| 保存 | **仅** `GRAPH_JSON_INVALID`（解析地板，固定 ERROR，不进流检查目录） | 其它 ERROR/WARNING 可带错落盘；有 ERROR 时 toast「已保存，暂不可运行」；仅 WARNING 不说成错误 |
| 画布 `stage=canvas` | 不硬拦 | 生效 `ERROR` + `WARNING`（含鉴权/结构/断言路径/API/插件 **platform.***）；**不含** Invoke |
| 开跑 `stage=run` / 子流 | 生效 **`severity=ERROR`** | 另含插件 **Invoke**（默认 WARNING，需 `testProjectEnvId`）；WARNING 不拦跑 |
| 入站 `stage=ingress` | 生效 **`severity=ERROR`** | **故意不做** Invoke（不传环境） |

**鉴权相关码（细节）**

issue.code 统一为 `RUN_READINESS_AUTH`；下表前缀仅出现在 **message** 开头，**不是**独立 CODE，不可单独改级。

| message 前缀 / 项 | 硬拦？ | 何时硬拦 | 含义 |
| ---- | ---- | ---- | ---- |
| `AUTH_LOGIN_FLOWKEY_COLLISION:` | 是（随 `RUN_READINESS_AUTH` 生效 ERROR） | **运行**（画布已可见） | 两套不同登录口写出同一凭证路径 |
| 断言路径结构错（`.items`、`http.body.$.…`） | 默认 ERROR | **运行**；✓ / 保存 / submit 不拦 | 见节点文档；无 schema / 字段未命中静默 |
| update 空数组误清空（`rules` / `extracts` / `assignments`） | 是 | **submit 地板** | baseline 同字段非空时优先拒收 |
| condition `branches[].target` | — | normalize **直接剔除** | 出口只认边，AI 预写 target 丢弃 |

缺凭证写出路径**不再**产出 `AUTH_TOKEN_MISSING` 硬拦；真跑时占位符未定义才失败。托管头仅运行期按项目配置补；设计期 normalize /「清除托管头」只剥不写。无独立 soft 提示码。免登只认接口 `auth.mode=none`。

下一轮 AI 设计请求会从校验条按码过滤出 `runRiskWarnings` 注入 user 上下文。开跑失败再 toast。项目「流检查」可把目录内业务码改为 WARNING/OFF。

造流助手可读 `list_project_auth_profiles`、可提 `upsert_auth_profile`（半自动确认卡片写入项目 `auth_config`）。登录 extract 的 `name=入口.字段` 缺 `entryKey` 时服务端会拆开。

造流 submit 工具面：节点/边/场景用 `submit_*` + `op=add|update`；删除统一 `submit_delete`（`kind`+`id`）。AI **不造** `input` 节点，**不切**默认运行场景。

灌入 Staging 前前端另做轻量 schema：缺 id / 未知 type / update·delete 目标不存在的项、以及 addEdge 端点不在「画布 ∪ 本批新增节点」的项**不建 unit**（toast 提示）。

鉴权产品口径见 [project-summary.md §4](./project-summary.md)。跑流变量 / inSeed 见 [flow-variables-and-values.md](./flow-variables-and-values.md)。

---

## 6. 修复叠图

多次「Run 失败 → AI 修复 → 再确认」可能出现双开始节点、未确认项卡死。

- 画布校验：有 **pending 边单元** 时请求 `validate?stage=canvas` 带 `deferTopology=true`，避免未确认连线造成开始节点/无入边等假错误；开跑再硬拦。
- 图乱：Staging **全部 ✕**，或 **新建流**（修复超 2 轮仍乱优先新建）。
- 禁止空白 `nodes: []` 当成功。
- 禁止改 Pinia / 直接 UPDATE `graph_json` 刷绿。

---

## 7. 和接口设计 AI 的边界

测试流助手改的是 **画布图**。另有接口设计助手（`submit_api_design_patch`）改接口资产 schema/测值：

- **半自动**（默认）：Diff 勾选 →「应用到工作台」草稿 → 人手保存接口库
- **全自动**：有 patch 时前端自动应用到工作台草稿（仍须人手保存；不自动调试发送）

两套不要混用验收口径。

---

## 8. 工具轨迹 `toolTrace`

`ai_chat_message` 只落 `user` / `assistant`（不落 `tool` 角色）。助手 `result_meta_json.toolTrace` 记录本轮工具名、脱敏截断后的 args/result、`ok` / 耗时 / 步数，供排障对账。

| 要点 | 口径 |
|------|------|
| 落哪里 | 助手气泡 meta；前端 `AiToolTracePanel` 默认折叠 |
| 谁采集 | `AiAgentRunner` 逐步记录 → 造流 / 接口设计 Agent 写入 meta |
| 截断 | 单字段约 2KB、calls ≤ maxSteps；口令 / Bearer 打码 `***` |
| 与 `explainOnly` | `explainOnly=true` **不等于**零工具：全自动可能已 submit + 隐式落盘再清空 capture，以轨迹 / `graphCommitted` 为准 |
| 半自动无 Staging | 仍表示本轮没有可灌 Staging 的成功 `submit_*` |
| 中断 | 取消 / SSE 断连：助手可带 `interrupted=true` + 已有轨迹；**不**回滚已发生的全自动写库 / Run |

排障优先看：调用顺序是否触顶、某次 `submit_edges` 的 label/校验、全自动是否真的 `run_test_flow`。轨迹只负责可证伪，不替代修 MergeHelper 等回写缺陷。
