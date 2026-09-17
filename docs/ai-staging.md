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
  → ✓ 确认（跑设计期门禁）
  → 保存（画布可见节点）
```

**例外：全自动**（AI 面板「半自动 | 全自动」，默认半自动）：请求带 `autopilotEnabled=true` 时注入 `run_test_flow`（跑前/回合结束**自动落盘**，无独立 commit 工具），且 `upsert_asset_variables` / `upsert_auth_profile` **直接写库**。隐式落盘门槛与人手**保存**相同（只拦无法解析 / 缺节点 id / 边端点）；双开始节点、断言路径等只进 warnings，不拦写库——开跑时再硬拦。造流/修复类请求完成后模型应主动 `run_test_flow`（纯答疑除外）；同会话内 upsert → `submit_*` → `run_test_flow` → 失败再修（最多再修 2 轮）。落盘成功后 SSE `graphCommitted`，前端清 Staging 并 reload 画布；`run_test_flow` 触发后 SSE `runStarted`，与人手共用轮询边跑边亮（执行中步骤即可读）。半自动则仍走上文 Staging ✓ → **人手保存**；素材与鉴权提案在聊天侧确认。只拨开关不发送请求不会跑流。

**MCP 只读**：无 `submit_*`、无 `upsert_asset_variables` / `upsert_auth_profile`、无 commit/run。Cursor 里改图无效；必须回 Web AI 面板。

---

## 2. 确认规则

| 动作 | 口径 |
| ---- | ---- |
| ✓ 确认 | 接受该单元；跑图结构与**本单元断言路径**门禁。token 来源 / 登录 extract / HTTP 必填**不硬拦**；若本轮已无未决单元，响应可带 `saveRiskWarnings` 提示**运行风险** |
| ✕ 取消 | 丢弃该单元，不落盘 |
| 删除类 ✓ | **唯一确认**，无二次 `MessageBox`；勿对 Staging 边按键盘 Delete 当确认 |
| 保存 | 可带设计期错误落盘（仅拦无法解析/最小 schema）；Staging 应清零；顶栏「nodes 为空」= 库里仍是空图，**不算造流成功** |
| 运行 | 结构 + 断言路径 + AUTH/必填 readiness 硬拦 |
| 仅保存已确认 | 未确认项会丢，易得半截图；应先去确认或全部 ✕ 后新建流 |
| 刷新鉴权头 | 批量改托管头，**仍进 Staging**，不是静默写库 |

画布被侧栏挡住时：关侧栏 → 拖拽 / 小地图 / 适应视图，再点 ✓。禁止 `force` 点视口外控件冒充确认。

---

## 3. 本轮没有 Staging

本轮助手消息下**没有** Staging 变更摘要（`explainOnly`）= 模型只写了方案，**没调** 任何 `submit_*` 单元工具。画布不会变。

常见原因：同会话从零搭长流、反复拉超大 `get_api_details`、步数将尽。

**例外（全自动）**：隐式落盘清空 capture 后也会 `explainOnly=true`，此时以助手气泡「工具轨迹」或 `result_meta_json.toolTrace` 为准（见 §8）。

**测法 / 用法**：新建对话 + 短提示 + 显式 API id；无 Staging 摘要时让 AI「请按单元 submit 落盘」，不要当已经改图。

---

## 4. Web 助手 vs MCP

| | Web AI 面板 | MCP |
| --- | --- | --- |
| 读接口 / 流摘要 / Run 失败 | 有 | 有（另有 `list_flows` / `get_flow`） |
| `list_project_auth_profiles` | **有** | **有**（只读） |
| `submit_*` 单元写工具 | **有** | 无 |
| `upsert_asset_variables` | **有**（半自动：提案确认后落盘；全自动：工具内直接写库） | 无 |
| `upsert_auth_profile` | **有**（半自动：提案确认后写 `auth_config`；全自动：工具内直写） | 无 |
| `append_api_design_hints` | 有（直接改接口 hint） | 无 |
| `run_test_flow` | **有**（须选「全自动」；跑前自动落盘；有 pending 素材/鉴权提案时拒绝） | **无** |

改图画布 = 只走 Web。MCP 用来勘察 `testFlowId` 和失败现场。

---

## 5. 设计期门禁（确认 / submit / 保存 / 运行）

错误串格式：`CODE: 人类文案`。前端认冒号前的 CODE。

| CODE | 硬拦？ | 何时硬拦 | 含义 |
| ---- | ---- | ---- | ---- |
| `AUTH_LOGIN_EXTRACT_MISSING` | 是 | **运行**（AI `submit_*` / Staging ✓ / **保存**跳过硬拦；**末单元 confirm 可附带 `saveRiskWarnings` 作运行风险预警**） | 登录口未抽出托管头所需凭证（`{{asset.*}}` / 存量 `{{flow.*}}`） |
| `AUTH_LOGIN_FLOWKEY_COLLISION` | 是 | **运行**（同上） | 两套不同登录口写出同一凭证路径 |
| `AUTH_TOKEN_MISSING` | 是 | **运行**（同上；末单元 confirm 可预警） | 图要用某托管 Bearer，但 extracts / assign / 子流输出 / **flowSeed(仅 flow)** 都没有该目标 |
| `AUTH_HEADER_MANAGED` | 否（soft） | — | 已按项目鉴权补托管头 |
| `AUTH_LOGIN_NO_BEARER` | 否（soft） | — | 登录/免登口剥掉了误补的托管头 |
| 断言路径结构错（`.items`、`http.body.$.…`） | 是（挂在该 assert/condition 单元） | **submit 与 Staging ✓**；**运行**亦拦全图；schema 缺字段多为警告；**保存不拦** | 见节点文档 |
| update 空数组误清空（`rules` / `extracts` / `assignments`） | 是 | **submit / preparePatch** | baseline 同字段非空时，禁止用空数组整表替换 |
| condition `branches[].target` | — | normalize **直接剔除** | 出口只认边，AI 预写 target 丢弃 |

**保存**：仅拦「无法解析 / 最小 schema（节点 id、边端点）」；结构细节、断言路径、AUTH / HTTP 必填允许带错落盘，方便 AI 继续修。校验条仍红；toast 可提示「已保存，暂不可运行」。

**运行**：前端与后端统一 readiness（图结构 + 断言路径错误 + `/patch/savePrecheck` 同口径的 AUTH / 登录 extract / HTTP 必填）。不过则不开跑。

`/patch/savePrecheck` 与末单元 `saveRiskWarnings` 现为**运行风险**预警（不阻断 ✓ / 保存），写入画布校验条；下一轮 AI 设计请求会带上 `runRiskWarnings` 注入模型 user 上下文。单单元 `submit_*` 对 AUTH_* / HTTP 必填写入 **warnings**（不进 errors）；整包规范化仍进 errors。开跑失败再 toast。

造流助手可读 `list_project_auth_profiles`、可提 `upsert_auth_profile`（半自动确认卡片写入项目 `auth_config`）。客户端 Profile 误绑 `adminAuth`（或相反）时，`AUTH_TOKEN_MISSING` 文案会追加「疑似绑错端」。登录 extract 的 `name=入口.字段` 缺 `entryKey` 时服务端会拆开。

造流 submit 工具面：节点/边/场景用 `submit_*` + `op=add|update`；删除统一 `submit_delete`（`kind`+`id`）。AI **不造** `input` 节点，**不切**默认运行场景。

灌入 Staging 前前端另做轻量 schema：缺 id / 未知 type / update·delete 目标不存在的项**不建 unit**；边端点启发式改写必 toast 提示。

鉴权产品口径见 [project-summary.md §4](./project-summary.md)。跑流变量 / flowSeed 见 [flow-variables-and-values.md](./flow-variables-and-values.md)。

---

## 6. 修复叠图

多次「Run 失败 → AI 修复 → 再确认」可能出现双开始节点、未确认项卡死。

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

排障优先看：调用顺序是否触顶、某次 `submit_edge` 的 label/校验、全自动是否真的 `run_test_flow`。轨迹只负责可证伪，不替代修 MergeHelper 等回写缺陷。
