# MCP integration (Cursor example)

Qualitest exposes a **project-scoped MCP** service over **Streamable HTTP** so **MCP-capable AI editors / agents** (Cursor, VS Code ecosystem, Claude Code, …) can query this project’s APIs, test flows, and Run context.  
Examples below use **Cursor `mcp.json`**. Other clients that support HTTP MCP + custom headers can use the same fields.  
**Read-only by default.** Three independent Project-settings switches: **“Allow MCP auto-write”** (`create_flow` / `update_flow_meta` / `submit_*` / upserts), **“Allow MCP auto-run”** (`run_test_flow` only), and **“Allow MCP import APIs”** (`import_apis` only). Auto-run usually requires write. The Token only binds project identity.

中文版：[mcp.md](./mcp.md)

---

## 0. What do auto-write and auto-run actually buy you?

By default MCP is **read-only** (list flows, inspect topology, inspect failed runs). Project-settings switches unlock graph edits and runs:

| Switch | In plain terms | What the agent does |
|:-------|:---------------|:--------------------|
| **Auto-write** | Let Cursor **build / edit a test flow** for you — no hand-dragging the canvas | `create_flow`, then unit `submit_*` for nodes, asserts, edges; **each success persists**; an open Web canvas syncs over SSE |
| **Auto-run** | Let Cursor **hit Run** for you — no hopping back to the browser | `run_test_flow` on the latest graph in the DB; on failure, `get_run_failure` pulls the step back into the chat |

Write-only can build the graph; Run still needs the Web UI unless auto-run is on. With both on, create → verify → fix stays in one chat.

### Backend (API authors)

After changing a Controller / Handler, instead of opening Postman and dragging nodes for multi-step cases:

1. In Cursor: “login for a token → call this new API → assert 200”  
2. The agent writes the flow and (with auto-run) runs it  
3. On failure, inspect the step, fix asserts / params in the same chat, re-run  

Fewer tool switches; long chains without clicking every property panel. You get a collaborative, env-switchable platform graph — not hard-to-review scripts dumped into the business repo.

### Frontend (page authors)

The usual stuck point: **the page fails — is it a bad client request, or did the backend chain / auth never work?** MCP lets you verify the **API path** separately from the UI:

1. **Check for an existing flow** — `list_flows` / topology, so you do not reinvent the same case  
2. **Mirror the page path** (login → list → submit) — have the agent write and run that flow; if green, dig into front-end headers / params; if red, you know which server step broke  
3. **Ingest APIs from the business repo** (enable import) — `import_apis` is not Java-only; OpenAPI, routers, and fetch wrappers in a front-end repo all work as context  
4. **401 / business codes disagree** — run the flow and read the failure site; fewer guesses than Network-panel-only debugging  

In one line: **write = build the orchestration; run = hit Run.** Backend self-tests stay in the IDE; frontend spends less time bouncing between the page, Postman, and “maybe the backend?”.

---

## 1. Prepare

1. Sign in → open the **test project** → **Project settings**.
2. Generate / copy the **Project Token** (refresh invalidates the old token immediately).
3. The “Cursor MCP” card can copy a full `mcp.json` snippet; or use the template below.
4. (Optional) Enable **“Allow MCP auto-write”** for graph write tools.
5. (Optional) Enable **“Allow MCP auto-run”** for `run_test_flow` (requires write; turning write off also turns auto-run off).
6. (Optional) Enable **“Allow MCP import APIs”** for `import_apis` (independent of auto-write).
7. After toggling any switch, **reconnect or refresh MCP** — editors often keep the old tool list (the server does not push tool-list changes).

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
| `get_mcp_guide_version` | Composite `guideVersion` (content fingerprint + optional `+autowrite`/`+autorun`/`+importApis`); exact string match vs local Skill |

Typical inspect order: `list_flows` → note `testFlowId` → `get_graph_summary` / `get_run_failure`.

### Prompts / Resources

`initialize` advertises `prompts` and `resources`; `serverInfo.guideVersion` matches `get_mcp_guide_version` (both composite).

`guideVersion` format: `{12-hex fingerprint of CORE+SURVEY+FIX_RUN source}[+autowrite][+autorun][+importApis]`. Toggling project write/run/import switches changes the suffix — reconnect MCP and re-sync Skill.

`prompts/get`, `resources/read`, and Skill body are gated by the two project switches (no `import_apis` section when import is off; no `submit_*` when write is off).

| Prompt | Purpose |
|:-------|:--------|
| `qualitest_core` | Core flow-design rules (same as resource `qualitest://docs/core`, gated) |
| `qualitest_survey` | Read-only survey order |
| `qualitest_fix_run` | Fix failed run (max 2 repair rounds; empty write steps when write is off) |
| `qualitest_sync_local_skill` | Install/update `.cursor/skills/qualitest/SKILL.md` (skip if composite `guideVersion` matches exactly) |

Do **not** hide `prompts/list`. If local Skill already has the same `guideVersion`, do not `prompts/get` the three body prompts again (avoids double token cost). Re-run sync when the server version changes (including switch-suffix changes). Navbar “copy guide” passes route `testProjectId` when available; otherwise read-only gates.
### Write tools (auto-write switch on)

When enabled, `tools/list` also exposes `create_flow`, `update_flow_meta`, all `submit_*`, upserts, and `append_api_design_hints` (**not** `run_test_flow`). Use `create_flow` only to create an empty flow; rename or change description with `update_flow_meta` (do not create a new flow to fake a rename). Graph edits still require `testFlowId`.

### Auto-run (“Allow MCP auto-run” on)

When enabled, `tools/list` also exposes `run_test_flow`. Usually requires auto-write. Turning write off forces auto-run off on the server.

### Import APIs (“Allow MCP import APIs” on)

Exposes `import_apis`: structured `items[]` upsert by HTTP method + normalized path into the project API library; persists immediately; returns created/updated/skipped/conflicts and `testProjectApiId`. Does not overwrite API design hints. **Any language stack**: have the AI extract from the business repo (controllers / routers / OpenAPI notes, etc.) — no IDEA / Java required. This switch does not control graph edits or runs. IDEA plugin REST import is unaffected.

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
Use get_graph_summary / get_node_detail for topology.
```

**③ Where did the last run fail?**

```text
Same testFlowId. Call get_run_failure; summarize failed step, assert/HTTP errors,
and suggest what to change on the canvas (advice only).
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
| Only read-only tools | auto-write and/or import APIs enabled **and saved**? After saving you **must** reconnect / refresh MCP |
| Stale config | After refresh Token, re-copy the full `mcp.json` snippet |
