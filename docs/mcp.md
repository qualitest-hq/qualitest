# MCP 接入（以 Cursor 为例）

质衡通过 **Streamable HTTP** 暴露项目级 MCP 服务，供 **支持 MCP 的 AI 编辑器 / Agent**（Cursor、VS Code 生态、Claude Code 等）**只读**查询本项目的接口、测试流与 Run 现场。  
下文配置以 **Cursor `mcp.json`** 为例；其它客户端只要支持同协议的 HTTP MCP + 自定义 Header，即可按同等字段接入。  
**改画布请走 Web 端 AI 面板**（先 Diff 再合并）；MCP **不会**写库，也不提供 `submit_*` 类工具。

English: [mcp.en.md](./mcp.en.md)

---

## 1. 准备

1. 登录质衡 → 打开目标 **测试项目** → **项目设置**。
2. 生成 / 复制 **Project Token**（刷新后旧 Token 立即失效）。
3. 同页「Cursor MCP」卡片可一键复制完整 `mcp.json` 片段；也可按下方模板手写。

本地开发默认后端：`http://127.0.0.1:8080`。  
Compose 全栈经 Nginx 时，把 `url` 改成浏览器能访问到的 API 根（常见为 `http://localhost/api/project/mcp`），以项目设置里生成的为准。

---

## 2. Cursor `mcp.json`

把下列内容合并进 Cursor 的 MCP 配置（用户级或项目级均可）。`X-Project-Token` 换成真实 Token。

```json
{
  "mcpServers": {
    "qualitest": {
      "url": "http://127.0.0.1:8080/api/project/mcp",
      "headers": {
        "X-Project-Token": "<YOUR_PROJECT_TOKEN>"
      }
    }
  }
}
```

| 字段 | 说明 |
|:-----|:-----|
| `url` | MCP 端点，路径固定为 `/api/project/mcp`（无尾斜杠） |
| `headers.X-Project-Token` | 项目 Token；权限与签发时一致，按最小权限发放 |

配置生效后，在 Cursor Agent / Chat 中应能看到名为 `qualitest` 的 MCP 服务器及其工具列表。

---

## 3. 只读工具（摘要）

MCP 侧共 **13** 个只读工具（相对 Web AI 面板：多 `list_flows` / `get_flow`，无 `submit_*` / `upsert_asset_variables`）。

| 工具 | 用途 |
|:-----|:-----|
| `list_flows` | 按关键词列举本项目测试流摘要 |
| `get_flow` | 读单条流完整 `graphJson`（浏览拓扑优先用下面两个） |
| `get_graph_summary` / `get_subflow_detail` | 拓扑摘要、子流结构（多数勘察够用） |
| `get_flow_meta` | 场景、seed、flowOutputs 等元数据 |
| `get_node_detail` | 单个节点配置 |
| `get_run_failure` | 失败 Run 的步骤现场 |
| `get_flow_api_health` | HTTP 节点绑定 / API 语义告警 |
| `search_apis` / `get_api_details` | 查项目接口（详情支持一次传多个 id） |
| `list_project_envs` | 环境列表 |
| `list_asset_variables` | 项目素材库（参数资产）key/字段名，不含明文；**写入请走 Web AI 的 `upsert_asset_variables`（聊天侧提案确认后落盘）** |
| `list_subflow_templates` | 平台子流模板 |

典型勘察顺序：`list_flows` → 记下 `testFlowId` → `get_graph_summary` / `get_run_failure`。

---

## 4. 三条示例提问

在 Cursor 里直接问即可（模型会调 MCP 工具）。建议先让 AI `list_flows`，再带上具体 `testFlowId`。

**① 这个项目里有哪些测试流？**

```text
用 qualitest MCP 的 list_flows 列出当前项目的测试流（可按名称搜「登录」），
返回 testFlowId 和名称即可。
```

**② 这条流大概长什么样？**

```text
testFlowId 用 <上一步拿到的 id>。
用 get_graph_summary（或 get_subflow_detail）说明：有哪些节点、主路径怎么走、关键断言在哪。
不要贴整份 graphJson。
```

**③ 上次跑挂在哪？**

```text
同一个 testFlowId。调用 get_run_failure，总结失败步骤、断言/HTTP 错误信息，
以及建议我下一步在画布上改哪里（只给建议，不要改库）。
```

---

## 5. 安全与限制

- Token 等同项目凭证：勿提交进 Git / 截图外传；泄露后立即在项目设置刷新。
- MCP **只读**：设计改动请在 Web **AI 助手**里预览 Diff 后合并。
- 靶场联调、自然语言造流示例见：[qualitest-demo · AI 提示集](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md)。
- IDEA / OpenAPI 同步后：检查登录口（`/login`、`/api/account/auth/login` 等）资产的 `auth.mode` 应为 `none`；若仍为 `inherit`，造流可能误补 `Bearer {{flow.token}}`。存量可用 `sql/fix_anonymous_auth_builtin_paths.sql` 预览后修复。

---

## 6. 排障

| 现象 | 排查 |
|:-----|:-----|
| Cursor 连不上 | 后端是否已起；`url` 主机/端口是否与浏览器访问一致（IDE 不走 Vite 代理） |
| 401 / 无工具 | Token 是否过期或复制不完整；header 名是否为 `X-Project-Token` |
| 工具报无权限 / 空列表 | Token 是否属于当前要查的那个项目 |
| 只有旧配置 | 刷新 Token 后须重新复制整段 `mcp.json` |
