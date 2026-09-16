# AI 工具调用轨迹（设计备忘）

> **状态**：未实现，仅设计口径。排障需要时再开工。  
> **用途**：说明「造流会话为什么对不上工具调用」、将来落轨迹时应记什么、落哪里。  
> **相关**：[ai-staging.md](./ai-staging.md) · [mcp.md](./mcp.md)

---

## 1. 现状缺口

Web 造流 Agent（`TestFlowDesignAgent` → `AiAgentRunner`）在内存里跑多轮：

```text
LLM → tool_calls → 执行工具 → 追加 tool 消息 → 再 LLM …
```

但 **`ai_chat_message` 只落 `user` / `assistant`**，不落 `tool` 角色；助手 `result_meta_json` 目前大致只有：

| 字段 | 含义 |
|------|------|
| `summary` | 展示文案（常等于模型终稿） |
| `explainOnly` | 本轮是否**没有**可灌 Staging 的成功 `submit_*`（全自动隐式落盘清空 capture 后也会变 true） |
| `patchStats` / `patchJson` | 半自动时本轮累积 patch 摘要；全自动落盘后常为空 |
| `assetProposals` / `authProfileProposals` | 半自动素材/鉴权提案 |
| `vendorName` / `modelName` | 模型信息 |

**没有**：每次工具名、入参、校验 errors/warnings、normalize/merge 后的关键字段（如 condition `branches[].target`）、是否写库。

因此事后只能靠：

- 助手口述（可能前后矛盾）
- 终态 `graph_json`
- `test_flow_run` / `test_flow_run_step`

**无法逐步对账**：「模型没调」vs「调了但 FUN TOOLS / MergeHelper 弄坏」vs「全自动已落盘但 meta 显示 explainOnly」。

典型案例（2026-09-16 悦一彤「管理端登录子流」）：画布已有 `else_cred` 边，但 `branches.target` 未回写 → Run 假成功；会话里看不到当轮 `submit_edge` 的 label/结果，只能靠代码推断 MergeHelper / `matchOutEdge` 缺陷。

---

## 2. 要回答的排障问题

实现后应能快速回答：

1. 本轮调用了哪些工具、顺序、步数是否触顶？
2. 某次 `submit_edge` 的 `source` / `target` / `label` 实际是什么？校验 ok 还是 errors？
3. merge / reconcile 之后，对应 condition 的 `branches[].target` 是否与边一致？
4. 全自动是否触发隐式落盘 / `run_test_flow`？落盘前后边数、节点数？
5. 助手文案声称「已补 ELSE」时，工具层是否真的 add 成功？

---

## 3. 建议落库形态（择一即可）

### 方案 A（推荐 · 轻量）：挂在助手消息 meta

助手 `result_meta_json` 增加：

```json
{
  "toolTrace": {
    "stepsUsed": 12,
    "maxSteps": 24,
    "truncated": false,
    "calls": [
      {
        "i": 1,
        "name": "submit_edge",
        "ok": true,
        "ms": 18,
        "args": {
          "op": "add",
          "source": "n_cred",
          "target": "n_login",
          "label": "else_cred"
        },
        "result": {
          "ok": true,
          "errors": [],
          "warnings": [],
          "edgeId": "2100…",
          "note": "可选：merge 后源节点 branches 摘要"
        }
      }
    ]
  }
}
```

- 一条助手气泡 = 一轮 Agent 跑完的完整轨迹  
- 不改表结构；历史会话无该字段即「旧数据」  
- `args` / `result` **截断**：单字段上限（如 2KB）、列表最多 N 条；密码 / token / Authorization 值打码

### 方案 B：独立表

例如 `ai_chat_tool_call`（`session_id`、`assistant_message_id`、`seq`、`tool_name`、`args_json`、`result_json`、`ok`、`duration_ms`）。  
适合量很大、要 SQL 检索时再上；首版不必。

### 不建议

- 把完整 LLM `messages[]`（含全部 tool 原文）原样落库：体积大、含敏感测值、难脱敏  
- 只打应用日志不落库：本地排障可以，跨机器 / 事后会话对账仍困难

---

## 4. 采集点（实现时）

| 位置 | 记什么 |
|------|--------|
| `AiAgentRunner` 每步 tool 执行前后 | name、耗时、原始 args、工具返回 JSON 是否含 `error` |
| `FlowDesignUnitSubmitSupport` / Normalizer / Merger | `submit_*` 的校验 errors/warnings；condition 边同步后的 `branchId → target` 快照（短） |
| 全自动落盘 / `run_test_flow` | 是否 commit、`graphCommitted`、runId、status |
| `TestFlowDesignAgent` 写助手 meta 前 | 把本轮累积 `toolTrace` 写入 `result_meta_json` |

只读工具（`search_apis`、`get_graph_summary`…）可只记 name + 精简结果（命中条数 / 摘要），避免 schema 全文。

---

## 5. 展示与权限

- **开发 / 排障**：会话详情或助手气泡展开「工具轨迹」只读列表即可  
- **默认对业务用户折叠或仅管理员可见**（避免干扰造流 UX）  
- MCP / 对外 API：**默认不返回**完整 `toolTrace`（或仅管理员 Token）

---

## 6. 与现有口径的关系

- `explainOnly=true` **不等于**「本轮零工具」：全自动可能已 `submit_*` + 隐式落盘再清空 capture；轨迹能消歧。  
- Staging 文档「无 Staging 摘要 = 没调 submit_*」对**半自动**仍成立；全自动请以轨迹 / `graphCommitted` 为准。  
- 修 Condition 边与 `branches.target` 同步（MergeHelper / `matchOutEdge`）是**另一件事**；轨迹只负责让这类问题可证伪，不替代修回写。

---

## 7. 验收（将来开工时）

1. 全自动搭「探活再登录」：助手 meta 能看到按序的 `submit_condition_node` / `submit_http_node` / `submit_edge`，且 `submit_edge` 的 `label` 可读。  
2. 故意让 `submit_edge` 校验失败：轨迹里 `ok=false` + errors，助手仍可写自然语言，但不谎称「已落盘」。  
3. 半自动仅答疑：`toolTrace.calls` 可为空或仅含只读工具；`explainOnly=true`。  
4. args 中的口令 / Bearer 在落库与展示中均为打码。

---

## 8. 非目标（本备忘不覆盖）

- 实时 SSE 推送每一步 tool（可后续加，首版回合结束写入即可）  
- 用轨迹自动回放造流  
- 修改「无 target = 结束」的运行语义
