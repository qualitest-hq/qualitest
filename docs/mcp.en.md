# MCP integration (Cursor example)

Qualitest exposes a **project-scoped MCP** service over **Streamable HTTP** so **MCP-capable AI editors / agents** (Cursor, VS Code ecosystem, Claude Code, …) can **read** this project’s APIs, test flows, and Run context.  
Examples below use **Cursor `mcp.json`**. Other clients that support HTTP MCP + custom headers can use the same fields.  
**Edit the canvas via the Web AI panel** (Diff then merge, or switch to Full-auto for Web auto-persist + `run`). MCP **does not** write the database and has no `submit_*` / `run_test_flow` tools.

中文版：[mcp.md](./mcp.md)

---

## 1. Prepare

1. Sign in → open the **test project** → **Project settings**.
2. Generate / copy the **Project Token** (refresh invalidates the old token immediately).
3. The “Cursor MCP” card can copy a full `mcp.json` snippet; or use the template below.

Local default backend: `http://127.0.0.1:8800`.  
With full-stack Compose (Nginx), set `url` to the API root your browser can reach (often `http://localhost/api/project/mcp`). Prefer the snippet from Project settings.

---

## 2. Cursor `mcp.json`

Merge into Cursor’s MCP config (user or project). Replace `X-Project-Token` with a real token.

```json
{
  "mcpServers": {
    "qualitest": {
      "url": "http://127.0.0.1:8800/api/project/mcp",
      "headers": {
        "X-Project-Token": "<YOUR_PROJECT_TOKEN>"
      }
    }
  }
}
```

| Field | Meaning |
|:------|:--------|
| `url` | MCP endpoint; path is always `/api/project/mcp` (no trailing slash) |
| `headers.X-Project-Token` | Project Token; same scope as when issued — least privilege |

After reload, Cursor Agent / Chat should list the `qualitest` MCP server and its tools.

---

## 3. Read-only tools (summary)

**13** read-only tools (vs Web AI panel: adds `list_flows` / `get_flow`; no `submit_*` / `upsert_asset_variables` / `run_test_flow`).

| Tool | Purpose |
|:-----|:--------|
| `list_flows` | List flow summaries (optional keyword) |
| `get_flow` | Full `graphJson` (prefer the next two for browsing) |
| `get_graph_summary` / `get_subflow_detail` | Topology / subflow structure |
| `get_flow_meta` | Scenario, seed, flowOutputs, … |
| `get_node_detail` | Single node config |
| `get_run_failure` | Failed Run step context |
| `get_flow_api_health` | HTTP binding / API semantic warnings |
| `search_apis` / `get_api_details` | Project APIs (batch detail by id list) |
| `list_project_envs` | Environments |
| `list_asset_variables` | Project asset variable keys/fields (no plaintext values); **writes via Web AI `upsert_asset_variables` (Semi-auto: confirm; Full-auto: immediate)** |
| `list_project_auth_profiles` | Project auth Profile summaries (pathPrefix, managed header, credential target; no secrets); **writes via Web AI `upsert_auth_profile` (Semi-auto confirm / Full-auto in-tool)** |
| `list_subflow_templates` | Platform subflow templates |

Typical inspect order: `list_flows` → note `testFlowId` → `get_graph_summary` / `get_run_failure`.

---

## 4. Three sample prompts

Ask in Cursor (the model will call MCP). Prefer `list_flows` first, then a concrete `testFlowId`.

**① Which flows exist in this project?**

```text
Use qualitest MCP list_flows to list flows in the current project
(optional: search name "login"). Return testFlowId and name only.
```

**② What does this flow look like?**

```text
testFlowId = <id from previous step>.
Use get_graph_summary (or get_subflow_detail): nodes, main path, key asserts.
Do not dump the full graphJson.
```

**③ Where did the last run fail?**

```text
Same testFlowId. Call get_run_failure; summarize failed step, assert/HTTP errors,
and suggest what to change on the canvas (advice only — do not write the DB).
```

---

## 5. Security & limits

- A Token is a project credential: keep it out of Git and screenshots; rotate in Project settings if leaked.
- MCP is **read-only**; design changes go through Web **AI assistant** (Diff then merge). Rules: [ai-staging.en.md](./ai-staging.en.md). Stuck: [faq.en.md](./faq.en.md). Draft MCP Full-auto write+run: [mcp-autopilot-write-run.md](./mcp-autopilot-write-run.md) (Chinese).
- Without MCP write: Project Settings → copy **testable prompts** (incremental / full) into Cursor, then paste short prompts back into Web AI.
- Demo target + NL flow prompts: [qualitest-demo · AI prompts](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md).

---

## 6. Troubleshooting

| Symptom | Check |
|:--------|:------|
| Cursor can’t connect | Backend up? Host/port in `url` match browser access (IDE does not use Vite proxy) |
| 401 / no tools | Token expired or truncated? Header name exactly `X-Project-Token`? |
| Empty / forbidden | Token belongs to the project you intend to query? |
| Stale config | After refresh Token, re-copy the full `mcp.json` snippet |
