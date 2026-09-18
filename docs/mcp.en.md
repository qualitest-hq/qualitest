# MCP integration (Cursor example)

Qualitest exposes a **project-scoped MCP** service over **Streamable HTTP** so **MCP-capable AI editors / agents** (Cursor, VS Code ecosystem, Claude Code, …) can query this project’s APIs, test flows, and Run context.  
Examples below use **Cursor `mcp.json`**. Other clients that support HTTP MCP + custom headers can use the same fields.  
**Read-only by default.** Two independent Project-settings switches: **“Allow MCP Full-auto write”** (`create_flow` / `submit_*` / upserts / `run_test_flow`) and **“Allow MCP import APIs”** (`import_apis` only). The Token only binds project identity.

中文版：[mcp.md](./mcp.md)

---

## 1. Prepare

1. Sign in → open the **test project** → **Project settings**.
2. Generate / copy the **Project Token** (refresh invalidates the old token immediately).
3. The “Cursor MCP” card can copy a full `mcp.json` snippet; or use the template below.
4. (Optional) Enable **“Allow MCP Full-auto write”** for graph write / run tools.
5. (Optional) Enable **“Allow MCP import APIs”** for `import_apis` (independent of Full-auto write).
6. After toggling, reconnect or refresh MCP if the editor still lists the old tool set.

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

**17** read-only tools (vs Web AI panel: adds `list_flows` / `get_flow` / `get_mcp_guide_version`).

| Tool | Purpose |
|:-----|:--------|
| `list_flows` | List flow summaries (optional keyword) |
| `get_flow` | Full `graphJson` (prefer the next two for browsing) |
| `get_graph_summary` / `get_subflow_detail` | Topology / subflow structure; `get_graph_summary` also returns `mermaid` (flowchart body for Cursor preview) |
| `get_flow_meta` | Scenario, seed, flowOutputs, … |
| `get_node_detail` / `get_edge_detail` / `get_scenario_detail` | Single node / edge / scenario |
| `get_run_failure` | Failed Run step context |
| `get_flow_api_health` | HTTP binding / API semantic warnings |
| `search_apis` / `get_api_details` | Project APIs (batch detail by id list) |
| `list_project_envs` | Environments |
| `list_asset_variables` | Project asset variable keys/fields (no plaintext values); **writes via Web AI `upsert_asset_variables` (Semi-auto: confirm; Full-auto: immediate)** |
| `list_project_auth_profiles` | Project auth Profile summaries (pathPrefix, managed header, credential target; no secrets); **writes via Web AI `upsert_auth_profile` (Semi-auto confirm / Full-auto in-tool)** |
| `list_subflow_templates` | Platform subflow templates |
| `get_mcp_guide_version` | Guide content fingerprint; compare with local Skill `guideVersion` |

Typical inspect order: `list_flows` → note `testFlowId` → `get_graph_summary` / `get_run_failure`.

### Prompts / Resources

`initialize` advertises `prompts` and `resources`; `serverInfo.guideVersion` matches `get_mcp_guide_version`.

| Prompt | Purpose |
|:-------|:--------|
| `qualitest_core` | Core flow-design rules (same text as resource `qualitest://docs/core`) |
| `qualitest_survey` | Read-only survey order |
| `qualitest_fix_run` | Fix failed run (max 2 repair rounds) |
| `qualitest_sync_local_skill` | Install/update `.cursor/skills/qualitest/SKILL.md` (skip if `guideVersion` matches) |

Do **not** hide `prompts/list`. If local Skill already has the same `guideVersion`, do not `prompts/get` the three body prompts again (avoids double token cost). Re-run sync when the server version changes.

### Write tools (Full-auto switch on)

When enabled, `tools/list` also exposes `create_flow`, all `submit_*`, upserts, `append_api_design_hints`, and `run_test_flow`. Use `create_flow` when no suitable empty flow exists; graph edits still require `testFlowId`.

### Import APIs (“Allow MCP import APIs” on)

Exposes `import_apis`: structured `items[]` upsert by HTTP method + normalized path into the project API library; persists immediately; returns created/updated/skipped/conflicts and `testProjectApiId`. Does not overwrite API design hints. This switch does not control graph edits or runs. IDEA plugin REST import is unaffected.

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
- MCP is **read-only by default**; graph write vs API import are controlled by two Project switches (not Token “scopes”). Staging rules: [ai-staging.en.md](./ai-staging.en.md). Stuck: [faq.en.md](./faq.en.md).
- Without MCP write: Project Settings → copy **testable prompts** (incremental / full) into Cursor, then paste short prompts back into Web AI.
- Demo target + NL flow prompts: [qualitest-demo · AI prompts](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md).

---

## 6. Troubleshooting

| Symptom | Check |
|:--------|:------|
| Cursor can’t connect | Backend up? Host/port in `url` match browser access (IDE does not use Vite proxy) |
| 401 / no tools | Token expired or truncated? Header name exactly `X-Project-Token`? |
| Empty / forbidden | Token belongs to the project you intend to query? |
| Only read-only tools | Full-auto write and/or import APIs enabled **and saved**? Reconnect / refresh MCP after saving |
| Stale config | After refresh Token, re-copy the full `mcp.json` snippet |
