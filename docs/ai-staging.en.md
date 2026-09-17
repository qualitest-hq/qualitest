# AI Staging / Diff

> **Purpose:** How canvas edits become real — proposal, confirm, save.  
> Chinese: [ai-staging.md](./ai-staging.md)

See also: [project-summary.en.md](./project-summary.en.md) · [test-flow-nodes.en.md](./test-flow-nodes.en.md)

---

## 1. Why Staging exists

The model **does not write the DB by default**. The Web assistant calls typed `submit_*` tools (e.g. `submit_http_node` with `op=add|update`), one Staging unit per call; the UI merges the accumulated patch into **Staging units**. A human (or an agent clicking the UI) **✓ confirms** or **✕ cancels**, then **Save**. Only then is `graph_json` persisted.

**Full-auto (opt-in):** Web panel **Semi-auto | Full-auto** (default Semi-auto). With Full-auto (`autopilotEnabled`), the model gets `run_test_flow` (auto-persists pending submit_* before run / at turn end), and `upsert_asset_variables` / `upsert_auth_profile` write immediately. Implicit persist uses the same floor as human **Save** (unparseable / missing node id / edge endpoints only); multiple start nodes, assert paths, etc. become warnings and do not block write — Run hard-blocks later. No separate commit tool. Max 2 fix rounds after a failed run. Semi-auto still uses Staging ✓ then **manual Save**; asset and auth proposals confirm in chat.

**MCP is read-only** — no `submit_*`, no asset/auth upsert writers, no commit/run. Edit graphs on the Web AI panel.

```text
Prompt → tools → multiple submit_* (1 unit each) → Staging → ✓ → Save (canvas has nodes)
```

---

## 2. Confirm rules

| Action | Rule |
| ------ | ---- |
| ✓ Confirm | Accept unit; graph structure + **assert-path for this unit**. AUTH / login extract / HTTP-required stay soft on ✓; last unresolved unit may return `saveRiskWarnings` |
| ✕ | Drop unit |
| Delete ✓ | **Only** confirm; no second dialog. Do not keyboard-Delete Staging edges |
| Save | May persist with design-time errors (only unparseable / minimal schema hard-block). Staging should be empty. Banner “nodes empty” = empty graph in DB — **not** a pass |
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
| `AUTH_LOGIN_EXTRACT_MISSING` | Yes | **Run** (unit `submit_*` → warnings; Staging ✓ / Save soft; last-unit confirm may warn via `saveRiskWarnings`) | Login node missing extract for managed header target |
| `AUTH_LOGIN_FLOWKEY_COLLISION` | Yes | **Run** (same) | Two logins write the same credential path |
| `AUTH_TOKEN_MISSING` | Yes | **Run** (same; last-unit confirm may warn) | Bearer needed but no extract / assign / subflow output / **flowSeed(flow only)**; may tip “wrong side” Profile |
| `AUTH_HEADER_MANAGED` / `AUTH_LOGIN_NO_BEARER` | Soft | — | Managed header filled / stripped on anonymous login |
| Bad assert path (`.items`, `http.body.$.…`) | Yes on that unit | **submit and Staging ✓**; also **Run**; Save soft | See node docs |
| Update empty array wipe (`rules`/`extracts`/`assignments`) | Yes | **submit / preparePatch** | Empty array vs non-empty baseline blocked |
| condition `branches[].target` | — | stripped in normalize | Exits follow edges only |

**Save** only hard-blocks unparseable / minimal schema. **Run** readiness = structure + assert-path errors + `/patch/savePrecheck` (AUTH / login extract / HTTP required).

`/patch/savePrecheck` and last-unit `saveRiskWarnings` are **run-risk** advisories (do not block ✓ / Save); they feed the validation bar and the next design request’s `runRiskWarnings`. Unit `submit_*` puts AUTH_* / HTTP-required into **warnings**; full-graph normalize still uses **errors**.

Assistants may call `list_project_auth_profiles` and `upsert_auth_profile`. Dotted extract names like `adminAuth.token` without `entryKey` are split server-side.

Auth model: concept map §4. Variables / flowSeed: [flow-variables-and-values.en.md](./flow-variables-and-values.en.md).

---

## 6. Stacked repairs

Repeated “fix from Run” can yield two start nodes. ✕ all Staging or **new flow** after ~2 messy rounds. Empty `nodes: []` is never success. Do not patch Pinia or `graph_json` in SQL to go green.

---

## 7. API-design AI

A separate assistant (`submit_api_design_patch`) edits API assets. **Semi-auto** (default): Diff → apply to workbench draft → human save. **Full-auto**: auto-applies Diff to draft (still human save; no auto debug send). Do not mix with flow Staging when scoring tests.

---

## 8. Tool trace (`toolTrace`)

Assistant messages store only `user` / `assistant` roles. Per-turn tool calls land in `result_meta_json.toolTrace` (name, redacted args/result, ok, latency, step count). UI: collapsible tool-trace panel. `explainOnly=true` does **not** mean zero tools under Full-auto (implicit persist may clear Staging capture) — trust the trace / `graphCommitted`. Cancel / SSE drop may persist `interrupted=true` plus partial trace; Full-auto DB writes / Runs already done are **not** rolled back. Details: Chinese [ai-staging.md](./ai-staging.md) §8.
