# Qualitest · Product concept map

> **Purpose:** One-screen map of objects, main path, and where docs live.  
> **Not:** deploy guide, node field dictionary, or test checklists — see the index below.  
> Chinese: [project-summary.md](./project-summary.md)

---

## 1. Workspace map

| Path | Role |
| ---- | ---- |
| `qualitest/` | Platform backend (Spring Boot) + `qualitest-ui/` SPA |
| `qualitest-demo/` | Optional shop API target (separate Compose; `/test-support` snapshot sample) |
| `qualitest-intellij-plugin/` | IDEA plugin: scan Java Controllers → upload (optional; other stacks use MCP `import_apis`) |
| `qualitest-all/` | Local aggregate workspace (no own Git) |

REST/JSON between UI and API; Run/debug hit the project env `baseUrl` (Demo often `http://localhost:8801`).

---

## 2. Core objects

| Object | One-liner |
| ------ | --------- |
| **Test project** | Isolation boundary: members, tokens, APIs, flows, assets, auth |
| **Environment** | SUT `baseUrl`; optional “allow destructive restore” (then serial Runs on that env) |
| **API asset** | method/path/schema/test values/auth mode; from IDEA plugin, MCP `import_apis`, Web, or template prefab |
| **Test flow** | Canvas graph (exactly 8 node types); edits via AI Staging or property panel |
| **Asset library** | Reusable values; login secrets prefer `{{asset.*}}`; files use `storagePath` → multipart |
| **Run** | One execution; details include timeline, HTTP, audit steps (`run_config` / `snapshot` / `restore`) |

Run variables: `{{flow.*}}` · `{{env.*}}` · `{{asset.*}}` — [flow-variables-and-values.en.md](./flow-variables-and-values.en.md). `session` is Script-only. Assert paths: [test-flow-nodes.en.md](./test-flow-nodes.en.md).

---

## 3. Main path (human-reproducible)

```text
API ingest (IDEA plugin / MCP import_apis / Web)
  → Debug console (pick env)
  → Create/open test flow
  → AI assistant design or fix
  → Staging ✓ each unit (empty nodes ≠ success)
  → Save (canvas shows nodes)
  → Run → details / AI fix or manual edit on failure
```

| Rule | Notes |
| ---- | ----- |
| **Edit graph** | Web AI → Staging → save; [ai-staging.en.md](./ai-staging.en.md); MCP **read-only by default**; Project settings can enable auto-write / auto-run / import APIs |
| **Staging** | ✓ commits; delete ✓ is the only confirm; empty `nodes` ≠ success |
| **Auth** | On the **project**, not the environment — §4 |
| **Mutating scenarios** | Load demo scenario first; optional `snapshotBefore`; restore needs SUT `/test-support` |

---

## 4. Project auth

Configured in project settings → auth. Shared by Normalizer, Run, and debug.

### Templates & profiles

| Concept | Notes |
| ------- | ----- |
| `test_project_template` | One row = one Profile; built-ins: RuoYi Bearer/Session, client Bearer, admin Bearer |
| Apply | Copy into `authProfiles` (**new ids**); insert prefab `apis[]` (skip existing method+path); optional envs/params/flows. Managed header + `credentialApi`: **prefer** login-flow extracts, else **`match_config.credential`**, else **reject the row** (no weak dual `adminAuth` Bearer; no `loginHint`) |
| New project | **At least one** template; dual mall: admin Bearer then client Bearer |
| Anonymous | Prefab `apis[].authConfig.mode=none` only; no project-level anonymous path list |
| Empty config | Builtin `/login` heuristics only when auth is completely empty; Profiles with empty apis → settings banner |

### Three layers

| Layer | Notes |
| ----- | ----- |
| Project `authProfiles` | Header template + `credentialApi` + prefab `apis[]`; unmatched `pathPrefix` → **first array item**. **`loginHint` removed** |
| API `auth.mode` | `inherit` / `none` / `override` |
| Node headers | `profileManaged` rows refresh from current config at Run; unmarked headers never silently change |

**Match:** `authProfileId` if set; else longest `pathPrefix`; else first profile. **`pathPrefix="/"` forbidden.**

**Login extracts:** usually `scope=asset` into the asset library; Profile header uses `{{asset.adminAuth.token}}` etc. Must match `credentialApi`. Empty extracts on design are filled from the managed-header placeholder + schema; missing extract hard-blocks on **Run** (`AUTH_LOGIN_EXTRACT_MISSING`). Uploads merge API schema/`mode` by method+path; optional upload protection (`syncProtected=1` skips the row; built-in prefab APIs default to `1`).

| Side | Typical path | Extract |
| ---- | ------------ | ------- |
| Client | `/api/account/auth/login` | `$.data.token` → `asset.clientAuth.token` |
| Admin | `/login` | `$.token` → `asset.adminAuth.token` |

Same side: login once (or subflow), reuse managed Bearer on `asset.*`. Dual-side same graph: two logins, two extracts — never overwrite one credential path (`AUTH_LOGIN_FLOWKEY_COLLISION`). Missing source → `AUTH_TOKEN_MISSING` (**Run** hard-block; unit `submit_*` → warnings; Staging ✓ / Save soft; may tip “wrong side” Profile). Soft: `AUTH_HEADER_MANAGED`. AI: `list_project_auth_profiles` + `upsert_auth_profile`; next turn may carry `runRiskWarnings`. Details: [ai-staging.en.md](./ai-staging.en.md) · [faq.en.md](./faq.en.md) · [project-template.md](./project-template.md).

Credentials may come from extracts (`asset`/`flow`), scenario `flowSeed`, or env vars. Prefer the asset library for reusable accounts; do not put plaintext passwords on the graph.

### Snapshot on writes

HTTP `snapshotBefore` → pause on failure → restore & retry / retry / skip / abort. Needs SUT `/test-support`. With `allowDestructiveReset=0`, checkpoint/restore skip silently. **Serial Runs when restore is on.**

---

## 5. AI & Staging (short)

Details: [ai-staging.en.md](./ai-staging.en.md) · [flow-variables-and-values.en.md](./flow-variables-and-values.en.md) · [assets.en.md](./assets.en.md) · [faq.en.md](./faq.en.md)

New chat + short prompt + real api ids for long flows. Staging must clear before save counts. Semi-auto: confirm asset/auth proposals in chat. Failure paths: “expect business reject” + literals. MCP read-only by default (can list auth Profiles); enable auto-write / auto-run / **import APIs** in Project settings (reconnect after toggle). Import works for any stack via `import_apis` — not Java-only.

---

## 6. Doc index

| Doc | When |
| --- | ---- |
| **This file** | Concepts, auth, main path |
| [test-flow-nodes.en.md](./test-flow-nodes.en.md) | Seven node types + assert dialects |
| [ai-staging.en.md](./ai-staging.en.md) | AI Diff / Staging |
| [flow-variables-and-values.en.md](./flow-variables-and-values.en.md) | `{{…}}` & HTTP test values |
| [assets.en.md](./assets.en.md) | Asset library / fixture files |
| [faq.en.md](./faq.en.md) | Day-to-day Q&A (not smoke-test quirks) |
| [mcp.en.md](./mcp.en.md) | MCP tools |
| [deploy.en.md](./deploy.en.md) | Deploy / Compose |
| [Demo AI prompts](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md) | Target-app flow prompts |
| Chinese handbook | [全面测试手册.md](./全面测试手册.md) |
