# Qualitest FAQ

> **For:** day-to-day debugging, flow design, and Runs.  
> **Not here:** demo scenario checklists, smoke-test quirks, Agent click paths — see [全面测试手册.md](./全面测试手册.md) §F / §G.  
> Chinese: [faq.md](./faq.md)

---

## Auth & tokens

### Run: credential placeholder undefined (`{{asset.*}}` / `{{flow.*}}` / `{{env.*}}`)

Common causes:

1. Wrong login **extract path**, or it does not match the Profile managed-header target.
2. Graph uses Bearer but has **no** matching source (extract / flowSeed / env / persisted asset).
3. Login API should be anonymous (`mode=none`) but still got a managed Bearer — check template and API `auth.mode`.

→ [project-summary.en.md §4](./project-summary.en.md) · [flow-variables-and-values.en.md](./flow-variables-and-values.en.md)

### Staging / save: `AUTH_*` codes

Hard blocks for token / login-extract / HTTP-required run on **Run** only — Staging ✓ and Save can succeed while Run still fails with AUTH. Check Profile `headerValueTemplate` (wrong side e.g. client using `adminAuth`) and login extracts first; not a confirm/save bug.

**AI self-heal** (same fields as project settings):

1. Before fixing login / `AUTH_TOKEN_MISSING`, call `list_project_auth_profiles`.
2. Wrong-side Profile → `upsert_auth_profile` (semi-auto: confirm card writes `auth_config`; full-auto: writes in-tool). Pending auth proposals block `run_test_flow` / implicit persist.
3. Next design turn sends canvas `runRiskWarnings` in the user context.
4. Login extract `name=adminAuth.token` without `entryKey` is split server-side into entry + field.

| Code | Meaning | Fix |
| ---- | ------- | --- |
| `AUTH_LOGIN_EXTRACT_MISSING` | Login node missing extract for managed header target | Add extracts |
| `AUTH_TOKEN_MISSING` | Bearer needed but no writer in the graph | Add a matching source (login extract / flowSeed / env) for the Profile header; “wrong side” tip → fix project auth |
| `AUTH_LOGIN_FLOWKEY_COLLISION` | Two logins write the same credential path | Use `adminAuth` / `clientAuth` separately |
| `AUTH_HEADER_MANAGED` | Managed header filled | Info only |

→ [ai-staging.en.md §5](./ai-staging.en.md)

### Client HTTP fails with missing `adminAuth.token`

Usually the **client Profile’s managed header points at the wrong asset** (`{{asset.adminAuth.token}}`), not a missing login node.

**How it happened:** older Apply with empty flows weakly defaulted both sides to `adminAuth` Bearer. Now Apply reads `match_config.credential`, or **rejects** the row if it cannot derive a full header.

**Fix (existing projects are not auto-migrated):**

1. Project settings → auth: edit the Profile header, or delete the same-named Profile and “Add from project template”.
2. AI: `upsert_auth_profile` on `headerValueTemplate` (semi-auto confirm).
3. New projects with a correct template Apply once correctly.

→ [project-template.md](./project-template.md) · [project-summary.en.md §4](./project-summary.en.md)

### Where to put passwords

Reusable main accounts: asset library + `{{asset.*}}`. Failure scenarios: **literals** on the node. Do not put plaintext passwords on the graph or in flowSeed.

→ [assets.en.md](./assets.en.md) · [flow-variables-and-values.en.md](./flow-variables-and-values.en.md)

---

## AI & Staging

### Assistant says the graph changed; canvas is empty

✓ all Staging, then Save. “No Staging this turn” = no `submit` — ask it to submit or start a new chat with short prompt + api ids. [ai-staging.en.md](./ai-staging.en.md).

### Messy graph / two start nodes after many fixes

✕ all Staging or create a new flow. Do not patch Pinia or `graph_json` in SQL. Same under Full-auto: after >2 messy fix rounds, switch to Semi-auto and recreate.

### Full-auto stopped / nothing persisted or ran

- Default is **Semi-auto**: Staging / asset / auth Profile proposals need human confirm; canvas still needs **manual Save**; no `run_test_flow` until Full-auto.
- Full-auto: asset and auth Profile upserts write immediately; graph persists before `run_test_flow`.
- Pending asset or auth proposals block `run_test_flow` / implicit persist.
- Run readiness / AUTH hard blocks / paused await-input also stop — read the assistant bubble and tool hints.

→ [ai-staging.en.md](./ai-staging.en.md)

---

## Assets & files

### File upload Run fails or sends path as plain text

Library file upload → form-data row **`type=file`** → `{{asset.<key>.storagePath}}`. [assets.en.md](./assets.en.md).

---

## MCP

### MCP cannot edit the canvas

**Read-only by default** (list flows, inspect failed runs, etc.). After you enable and **save** **“Allow MCP Full-auto write”** in Project settings, MCP can call `submit_*` / `create_flow` / `run_test_flow` and similar write tools; **“Allow MCP import APIs”** gates `import_apis` only (independent of Full-auto write).

**After toggling either switch you must reconnect or refresh MCP** — editors often keep the old tool list (the server does not push tool-list changes). Without Full-auto write, edit on Web AI → Staging → Save.

→ [mcp.en.md](./mcp.en.md) · [ai-staging.md](./ai-staging.md)

---

## Deploy & project setup

### Run: `TF_STEP_ERROR: ConnectException`

HTTP step cannot reach the SUT. Often project `baseUrl` is still the placeholder `http://127.0.0.1` (port 80) — use demo `http://localhost:8801`. Ensure demo API is up.

→ [project-summary.en.md](./project-summary.en.md)

### Cannot connect / MCP 401

Backend up; correct `url` and `X-Project-Token`. [deploy.en.md](./deploy.en.md), [mcp.en.md](./mcp.en.md).

### Why must new projects pick an auth template?

Templates seed Profiles, anonymous login APIs, prefab envs/params/flows. Managed headers come from login-flow extracts or `match_config.credential` (no weak dual `adminAuth` default). Business APIs still come from the IDEA plugin. Dual mall: pick admin Bearer + client Bearer.

→ [project-template.md](./project-template.md) · [project-summary.en.md §4.1](./project-summary.en.md)
