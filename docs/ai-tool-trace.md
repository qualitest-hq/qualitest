# AI 工具调用轨迹

> **状态**：已实现（方案 A：助手 `result_meta_json.toolTrace`）。  
> **用途**：说明「造流会话为什么对不上工具调用」、轨迹记什么、落哪里。  
> **相关**：[ai-staging.md](./ai-staging.md) · [mcp.md](./mcp.md)

---

## 1. 现状缺口（已补）

Web 造流 Agent（`TestFlowDesignAgent` → `AiAgentRunner`）在内存里跑多轮：

```text
LLM → tool_calls → 执行工具 → 追加 tool 消息 → 再 LLM …
```

`ai_chat_message` 仍只落 `user` / `assistant`（不落 `tool` 角色）；助手 `result_meta_json` 在原有字段之外增加 **`toolTrace`**：

| 字段 | 含义 |
|------|------|
| `summary` | 展示文案（常等于模型终稿） |
| `explainOnly` | 本轮是否**没有**可灌 Staging 的成功 `submit_*`（全自动隐式落盘清空 capture 后也会变 true） |
| `patchStats` / `patchJson` | 半自动时本轮累积 patch 摘要；全自动落盘后常为空 |
| `assetProposals` / `authProfileProposals` | 半自动素材/鉴权提案 |
| `vendorName` / `modelName` | 模型信息 |
| **`toolTrace`** | 本轮工具名、入参、结果摘要、ok/耗时、步数（脱敏截断） |

因此排障可逐步对账：「模型没调」vs「调了但 FUN TOOLS / MergeHelper 弄坏」vs「全自动已落盘但 meta 显示 explainOnly」。

典型案例（2026-09-16 悦一彤「管理端登录子流」）：画布已有 `else_cred` 边，但 `branches.target` 未回写 → Run 假成功；现可在助手气泡「工具轨迹」中查看当轮 `submit_edge` 的 label/结果。

---

## 2. 要回答的排障问题

实现后应能快速回答：

1. 本轮调用了哪些工具、顺序、步数是否触顶？
2. 某次 `submit_edge` 的 `source` / `target` / `label` 实际是什么？校验 ok 还是 errors？
3. merge / reconcile 之后，对应 condition 的 `branches[].target` 是否与边一致？（轨迹给 args + validation；回写缺陷仍须修 MergeHelper）
4. 全自动是否触发隐式落盘 / `run_test_flow`？看轨迹中对应工具的 result（commit / runId / status 等）
5. 助手文案声称「已补 ELSE」时，工具层是否真的 add 成功？

---

## 3. 落库形态（方案 A）

助手 `result_meta_json`：

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
          "edgeId": "2100…"
        }
      }
    ]
  }
}
```

- 一条助手气泡 = 一轮 Agent 跑完的完整轨迹  
- 不改表结构；历史会话无该字段即「旧数据」  
- `args` / `result` **截断**：单字段上限 2KB、calls 最多 maxSteps；密码 / token / Authorization 值打码为 `***`

方案 B（独立表 `ai_chat_tool_call`）未上：量很大、要 SQL 检索时再考虑。

### 不建议

- 把完整 LLM `messages[]`（含全部 tool 原文）原样落库：体积大、含敏感测值、难脱敏  
- 只打应用日志不落库：本地排障可以，跨机器 / 事后会话对账仍困难

---

## 4. 采集点

| 位置 | 记什么 |
|------|--------|
| `AiAgentRunner` 每步 tool 执行前后 | name、耗时、脱敏截断后的 args/result、`ok` |
| `TestFlowDesignAgent` / `ApiDesignAgent` 写助手 meta 前 | 把本轮 `toolTrace` 写入 `result_meta_json` |
| 全自动落盘 / `run_test_flow` | 依赖工具返回字段进入 `result`（不另开旁路） |

实现类：`AiToolTraceSupport`（脱敏/截断/组装）、前端 `AiToolTracePanel`（默认折叠）。

只读工具同样记 name + 精简结果（由工具返回决定，不再二次拉 schema）。

---

## 5. 展示与权限

- 造流助手气泡展开「工具轨迹」只读列表（默认折叠）
- 会话摘要 API **保留** `toolTrace`（已截断打码）
- MCP / 对外 API：当前不暴露会话 meta；实时 SSE 仍只推工具名（`tool_start`/`tool_end`），回合结束由 `done.result.toolTrace` 带上

---

## 6. 与现有口径的关系

- `explainOnly=true` **不等于**「本轮零工具」：全自动可能已 `submit_*` + 隐式落盘再清空 capture；轨迹能消歧。  
- Staging 文档「无 Staging 摘要 = 没调 submit_*」对**半自动**仍成立；全自动请以轨迹 / `graphCommitted` 为准。  
- 修 Condition 边与 `branches.target` 同步（MergeHelper / `matchOutEdge`）是**另一件事**；轨迹只负责让这类问题可证伪，不替代修回写。

---

## 7. 验收

1. 全自动搭「探活再登录」：助手 meta 能看到按序的 `submit_condition_node` / `submit_http_node` / `submit_edge`，且 `submit_edge` 的 `label` 可读。  
2. 故意让 `submit_edge` 校验失败：轨迹里 `ok=false` + errors，助手仍可写自然语言，但不谎称「已落盘」。  
3. 半自动仅答疑：`toolTrace.calls` 可为空或仅含只读工具；`explainOnly=true`。  
4. args 中的口令 / Bearer 在落库与展示中均为打码。  
5. 用户取消 / SSE 断连：助手消息仍落库，`interrupted=true`，并带上已有 `toolTrace` / 正文 / thinking（有多少写多少）；全自动不补做回合末隐式落盘。

---

## 8. 中断半成品落盘

- SSE `onCompletion` / `onTimeout` / `onError` 与发送失败会置协作取消标志；`AiAgentRunner` 在步间停止并返回 `interrupted`。  
- 造流 / 接口设计 Agent 将半成品写入助手 `result_meta_json`（含 `interrupted`、`toolTrace`）。  
- 前端取消后刷新会话；拉不到时用本地已流式正文兜底。  
- **不**回滚已发生的全自动写库 / Run；**不**打断正在飞行的单次 LLM HTTP。

---

## 9. 非目标（本备忘不覆盖）

- 实时 SSE 推送每一步 tool args/result（可后续加；首版回合结束或中断点写入即可）  
- 用轨迹自动回放造流  
- 修改「无 target = 结束」的运行语义  
- 取消后回滚已执行的全自动副作用
