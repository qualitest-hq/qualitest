# AI Staging / Diff

> **用途**：改测试流图画布的产品路径——提案如何出现、如何确认、何时才算落盘。  
> **不是**：节点字段字典、鉴权口径、测试勾选表。  
> 英文：[ai-staging.en.md](./ai-staging.en.md)

概念地图：[project-summary.md](./project-summary.md) · 节点：[test-flow-nodes.md](./test-flow-nodes.md) · 验收铁律：手册 §B.0.1

---

## 1. 为什么必须 Staging

AI **不直接写库**。Web 助手调 `submit_flow_design_patch` 后，前端把 patch 变成 **Staging 单元**（加节点 / 改节点 / 加边 / 删节点或边 / 场景字段等）。真人（或代点的 Agent）逐项 **✓ 确认** 或 **✕ 取消**，再点 **保存**，`graph_json` 才持久化。

```text
自然语言（或「AI 修复」）
  → 模型调工具（search_apis / get_api_details / …）
  → submit_flow_design_patch
  → 画布 Staging 黄条 / 单元
  → ✓ 确认（跑设计期门禁）
  → 保存（画布可见节点）
```

**MCP 只读**：无 `submit_*`、无 `upsert_asset_variables`。Cursor 里改图无效；必须回 Web AI 面板。

---

## 2. 确认规则

| 动作 | 口径 |
| ---- | ---- |
| ✓ 确认 | 接受该单元；会跑设计期门禁（鉴权、断言路径等） |
| ✕ 取消 | 丢弃该单元，不落盘 |
| 删除类 ✓ | **唯一确认**，无二次 `MessageBox`；勿对 Staging 边按键盘 Delete 当确认 |
| 保存 | Staging 应清零；顶栏「nodes 为空」= 库里仍是空图，**不算造流成功** |
| 仅保存已确认 | 未确认项会丢，易得半截图；应先去确认或全部 ✕ 后新建流 |
| 刷新鉴权头 | 批量改托管头，**仍进 Staging**，不是静默写库 |

画布被侧栏挡住时：关侧栏 → 拖拽 / 小地图 / 适应视图，再点 ✓。禁止 `force` 点视口外控件冒充确认。

---

## 3. 本轮没有 Staging

助手气泡 **「本轮未提交 Staging」**（`explainOnly`）= 模型只写了方案，**没调** `submit_flow_design_patch`。画布不会变。

常见原因：同会话从零搭长流、反复拉超大 `get_api_details`、步数将尽。

**测法 / 用法**：新建对话 + 短提示 + 显式 API id；看到该气泡就让 AI「请 submit 落盘」，不要当已经改图。

---

## 4. Web 助手 vs MCP

| | Web AI 面板 | MCP |
| --- | --- | --- |
| 读接口 / 流摘要 / Run 失败 | 有 | 有（另有 `list_flows` / `get_flow`） |
| `submit_flow_design_patch` | **有** | 无 |
| `upsert_asset_variables` | **有**（提案，聊天侧确认后落盘） | 无 |
| `append_api_design_hints` | 有（直接改接口 hint） | 无 |

改图画布 = 只走 Web。MCP 用来勘察 `testFlowId` 和失败现场。

---

## 5. 设计期门禁（确认 / submit / 保存）

错误串格式：`CODE: 人类文案`。前端认冒号前的 CODE。

| CODE | 硬拦？ | 含义 |
| ---- | ------ | ---- |
| `AUTH_LOGIN_EXTRACT_MISSING` | 是 | 登录口未抽出 Profile.`loginHint` 对应的 flow 变量 |
| `AUTH_LOGIN_FLOWKEY_COLLISION` | 是 | 两套不同登录口写出同一个 `flow.token`（等） |
| `AUTH_TOKEN_MISSING` | 是 | 图要用某端 Bearer，但 extracts / assign / 子流输出 / **flowSeed** 都没有该键 |
| `AUTH_HEADER_MANAGED` | 否（soft） | 已按项目鉴权补托管头 |
| `AUTH_LOGIN_NO_BEARER` | 否（soft） | 登录/免登口剥掉了误补的托管头 |
| 断言路径结构错（`.items`、`http.body.$.…`） | 是（挂在该 assert/condition 单元） | 见节点文档；schema 缺字段多为警告 |

鉴权产品口径见 [project-summary.md §4](./project-summary.md)。跑流变量 / flowSeed 见 [flow-variables-and-values.md](./flow-variables-and-values.md)。

---

## 6. 修复叠图

多次「Run 失败 → AI 修复 → 再确认」可能出现双开始节点、未确认项卡死。

- 图乱：Staging **全部 ✕**，或 **新建流**（修复超 2 轮仍乱优先新建）。
- 禁止空白 `nodes: []` 当成功。
- 禁止改 Pinia / 直接 UPDATE `graph_json` 刷绿。

---

## 7. 和接口设计 AI 的边界

测试流助手改的是 **画布图**。另有接口设计助手（`submit_api_design_patch`）改接口资产 schema/测值，同样先 Diff 再合并。两套不要混用验收口径。
