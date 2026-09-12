# AI Staging / Diff

> **Purpose:** How canvas edits become real — proposal, confirm, save.  
> Chinese: [ai-staging.md](./ai-staging.md)

See also: [project-summary.en.md](./project-summary.en.md) · [test-flow-nodes.en.md](./test-flow-nodes.en.md)

---

## 1. Why Staging exists

The model **does not write the DB**. The Web assistant calls typed `submit_*` tools (e.g. `submit_add_http_node`), one Staging unit per call; the UI merges the accumulated patch into **Staging units**. A human (or an agent clicking the UI) **✓ confirms** or **✕ cancels**, then **Save**. Only then is `graph_json` persisted.

**MCP is read-only** — no `submit_*`, no `upsert_asset_variables`. Edit graphs on the Web AI panel.

```text
Prompt → tools → multiple submit_* (1 unit each) → Staging → ✓ → Save (canvas has nodes)
```

---

## 2. Confirm rules

| Action | Rule |
| ------ | ---- |
| ✓ | Accept unit; structure gates may run. **Does not** run token / login-extract / HTTP-required / assert-path checks — those run on **Save** |
| ✕ | Drop unit |
| Delete ✓ | **Only** confirm; no second dialog. Do not keyboard-Delete Staging edges |
| Save | Staging should be empty. Banner “nodes empty” = empty graph in DB — **not** a pass |
| Save confirmed only | Leftover units vanish → half graph. Confirm first or ✕ all and recreate |
| Refresh auth headers | Still goes through Staging |

If the canvas is covered: close sidebars, pan / minimap / fit, then ✓. Do not `force`-click off-screen controls.

---

## 3. No Staging this turn

Bubble **“no Staging submitted this turn”** (`explainOnly`) means the model never called any `submit_*` unit tool. New chat + short prompt + real api ids. Ask it to submit units; do not treat the essay as a graph change.

---

## 4. Web vs MCP

Web has typed `submit_*` unit writers, `upsert_asset_variables` (proposal → confirm), `append_api_design_hints`. MCP adds `list_flows` / `get_flow` and **cannot write** (`submit_*` rejected).

---

## 5. Design-time codes (`CODE: message`)

| CODE | Hard? | When | Meaning |
| ---- | ----- | ---- | ------- |
| `AUTH_LOGIN_EXTRACT_MISSING` | Yes | **Save** (AI `submit_*` / Staging ✓ skip) | Login node missing extract for managed header target |
| `AUTH_LOGIN_FLOWKEY_COLLISION` | Yes | **Save** (same) | Two logins write the same credential path |
| `AUTH_TOKEN_MISSING` | Yes | **Save** (same) | Bearer needed but no extract / assign / subflow output / **flowSeed** |
| `AUTH_HEADER_MANAGED` / `AUTH_LOGIN_NO_BEARER` | Soft | — | Managed header filled / stripped on anonymous login |
| Bad assert path (`.items`, `http.body.$.…`) | Yes on that unit | Save (not on ✓) | See node docs |

Auth model: concept map §4. Variables / flowSeed: [flow-variables-and-values.en.md](./flow-variables-and-values.en.md).

---

## 6. Stacked repairs

Repeated “fix from Run” can yield two start nodes. ✕ all Staging or **new flow** after ~2 messy rounds. Empty `nodes: []` is never success. Do not patch Pinia or `graph_json` in SQL to go green.

---

## 7. API-design AI

A separate assistant (`submit_api_design_patch`) edits API assets. Do not mix its Diff with flow Staging when scoring tests.
