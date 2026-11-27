# AI Staging / Diff

> **Purpose:** How canvas edits become real — proposal, confirm, save.  
> Chinese: [ai-staging.md](./ai-staging.md)

See also: [project-summary.en.md](./project-summary.en.md) · [test-flow-nodes.en.md](./test-flow-nodes.en.md)

---

## 1. Why Staging exists

The model **does not write the DB by default**. The Web assistant calls typed `submit_*` tools (e.g. `submit_http_node` with `op=add|update`), one Staging unit per call; the UI merges the accumulated patch into **Staging units**. A human (or an agent clicking the UI) **✓ confirms** or **✕ cancels**, then **Save**. Only then is `graph_json` persisted.

**Full-auto (opt-in):** Web panel **Semi-auto | Full-auto** (default Semi-auto). With Full-auto (`autopilotEnabled`), the model gets `run_test_flow` (auto-persists pending submit_* before run / at turn end), and `upsert_asset_variables` / `upsert_auth_profile` write immediately. Implicit persist matches human **Save** (hard-blocks only unparseable JSON); floor / structure / assert errors do not hard-block write — Run hard-blocks later; empty schema / unmatched fields stay silent at design time. No separate commit tool. Max 2 fix rounds after a failed run. Semi-auto still uses Staging ✓ then **manual Save**; asset and auth proposals confirm in chat.

**MCP is read-only** — no `submit_*`, no asset/auth upsert writers, no commit/run. Edit graphs on the Web AI panel.

```text
Prompt → tools → multiple submit_* (1 unit each) → Staging → ✓ → Save (canvas has nodes)
```

---

## 2. Confirm rules

| Action | Rule |
| ------ | ---- |
| ✓ Confirm | Accept unit and **always apply to canvas** when the request succeeds. Dependency / structure / assert-path do **not** block ✓ (canvas graph errors + Run gate). AUTH / login extract / HTTP-required stay soft and appear in the same canvas graph-error bar (`validate?stage=canvas`) |
| ✕ | Drop unit |
| Delete ✓ | **Only** confirm; no second dialog. Do not keyboard-Delete Staging edges |
| Save | May persist with design-time errors (only unparseable JSON hard-blocks). Staging should be empty. Banner “nodes empty” = empty graph in DB — **not** a pass |
| Run | Structure + assert-path + AUTH / required readiness hard-block |
| Save confirmed only | Leftover units vanish → half graph. Confirm first or ✕ all and recreate |
| Refresh auth headers | Still goes through Staging |

If the canvas is covered: close sidebars, pan / minimap / fit, then ✓. Do not `force`-click off-screen controls.

---

## 3. No Staging this turn

Bubble with **no Staging change summary** (`explainOnly`) means the model never called any `submit_*` unit tool. New chat + short prompt + real api ids. Ask it to submit units; do not treat the essay as a graph change.

---

## 4. Web vs MCP

| | Web AI panel | MCP |
| --- | --- | --- |
| Read APIs / graph / Run failure | Yes | Yes (`list_flows` / `get_flow`, …) |
| `list_project_auth_profiles` | Yes | Yes (read-only) |
| `submit_*` writers | **Yes** | No |
| `upsert_asset_variables` | **Yes** (semi-auto: confirm; full-auto: in-tool) | No |
| `upsert_auth_profile` | **Yes** (semi-auto: confirm → `auth_config`; full-auto: in-tool) | No |
| `append_api_design_hints` | Yes | No |
| `run_test_flow` | **Yes** (Full-auto only; blocked if pending asset/auth proposals) | **No** |

Edit the canvas on Web only. Use MCP to inspect `testFlowId` and failure context.

---

## 5. Design-time codes (`CODE: message`)

| CODE | Hard? | When | Meaning |
| ---- | ----- | ---- | ------- |
| `AUTH_LOGIN_FLOWKEY_COLLISION` | Yes | **Run** (same) | Two logins write the same credential path |
| `AUTH_TOKEN_MISSING` | Yes | **Run** (same; canvas graph error may warn) | Bearer needed but no extract / assign / child-graph extract / **inSeed**; may tip “wrong side” Profile |
| Bad assert path (`.items`, `http.body.$.…`) | Yes (canvas / Run) | **Run** hard-blocks; **Staging ✓ / Save / submit accept soft** (submit only floor-rejects) | See node docs |
| Update empty array wipe (`rules`/`extracts`/`assignments`) | Yes | **submit floor** (may not enter Staging); Staging ✓ / Save soft | Prefer reject at submit vs wiping baseline |
| condition `branches[].target` | — | stripped in normalize | Exits follow edges only |

**Save** only hard-blocks unparseable JSON. **Run** readiness = structure + assert-path + AUTH / login extract / HTTP required (`validate?stage=run`).

Managed headers are written by normalize / “Refresh auth headers”; Staging Diff may label headers as filled from project auth. No soft tip codes.

AUTH / login extract / HTTP-required join **canvas graph errors** (`validate?stage=canvas`; do not block ✓ / Save). The next design request may filter those codes into `runRiskWarnings` in the user context. Unit `submit_*`: **floor only** (missing id / unknown refs) rejects; structure / AUTH / assert do not block accept—fix via canvas and Run.

Assistants may call `list_project_auth_profiles` and `upsert_auth_profile`. Dotted extract names like `adminAuth.token` without `entryKey` are split server-side.

Before hydrate, the Web also soft-gates the patch: missing id / unknown type / update·delete target absent / **addEdge endpoints not in canvas ∪ batch addNodes** → skip that unit (toast).

Auth model: concept map §4. Variables / inSeed: [flow-variables-and-values.en.md](./flow-variables-and-values.en.md).

---

## 6. Stacked repairs

Repeated “fix from Run” can yield two start nodes. ✕ all Staging or **new flow** after ~2 messy rounds. Empty `nodes: []` is never success. Do not patch Pinia or `graph_json` in SQL to go green.

---

## 7. API-design AI

A separate assistant (`submit_api_design_patch`) edits API assets. **Semi-auto** (default): Diff → apply to workbench draft → human save. **Full-auto**: auto-applies Diff to draft (still human save; no auto debug send). Do not mix with flow Staging when scoring tests.

---

## 8. Tool trace (`toolTrace`)

Assistant messages store only `user` / `assistant` roles. Per-turn tool calls land in `result_meta_json.toolTrace` (name, redacted args/result, ok, latency, step count). UI: collapsible tool-trace panel. `explainOnly=true` does **not** mean zero tools under Full-auto (implicit persist may clear Staging capture) — trust the trace / `graphCommitted`. Cancel / SSE drop may persist `interrupted=true` plus partial trace; Full-auto DB writes / Runs already done are **not** rolled back. Details: Chinese [ai-staging.md](./ai-staging.md) §8.
