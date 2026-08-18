# Test-flow nodes

The canvas supports exactly **seven** node types (`FlowNodeType`). Custom `type` values are rejected. Persisted codes are lowercase; UI labels are English.

Chinese: [test-flow-nodes.md](./test-flow-nodes.md)

---

## Overview

| type | Label | Role |
|------|-------|------|
| `http` | HTTP | Call a project API or external URL |
| `assert` | Assert | Compare rules against context (AND) |
| `condition` | Condition | IF / ELIF / ELSE branches |
| `assign` | Assign | Write `flow` variables |
| `delay` | Delay | Wait |
| `script` | Script | GraalVM sandbox (JS / Python) |
| `subflow` | Subflow | Run another project flow as one step |

Runs may also record audit steps (not drag-and-drop nodes): `run_config`, `snapshot` / `restore`, etc. — see Run details.

---

## HTTP (`http`)

Two paths via `data.callMode`, sharing forward + `extracts`:

| callMode | Behavior |
|----------|----------|
| `project` | Bind `testProjectApiId`; merge project API assets; pre/post scripts from the API definition |
| `external` | Use `externalUrl` / `httpMethod` / `headers` / `requestBody`; external-URL permission checks; sensitive fields redacted in step reports |

Common features:

- Placeholder resolution on params / body (`flow` / `env` / `session`, …)
- **Project auth** (see below)
- Success checks: non-2xx HTTP status fails first; optional business-code allowlist (`successCheck`)
- On success: write `lastResponse`, then run `extracts`

### Project auth

Configured on the **test project** (settings drawer), not on environments. Flow Normalizer, Run, and debug share one resolver. Project templates + prefabricated `apis`：backend landed; UI picker is next.

| Layer | Notes |
|-------|--------|
| Project `authProfiles` | Header templates + `credentialApi` + `loginHint` + prefabricated `apis[]`; unmatched `pathPrefix` uses the **first array item**; anonymous = `apis[].authConfig.mode=none` |
| API `auth.mode` | `inherit` → project Profile; `none` → no auth header; `override` → API `header.name` + `valueTemplate`. API rows do not store `loginHint` |
| Node headers | Managed rows use `profileManaged` and refresh from **current** config at Run; explicit headers without that flag are never silently changed |

**Match:** use `authProfileId` if set; else longest matching `pathPrefix`; else the first profile. **`pathPrefix="/"` is forbidden.**

Login extracts (`from`+`expr`, including `setCookie`) should align with **Profile.`loginHint`** when the API matches that profile's `credentialApi` (`flow.token` / `flow.adminToken`, …). Register/captcha do not extract credentials. Node `useRunSession` is retired (ignored). Missing per-side token sources hard-block AI submit / Staging confirm / save (`AUTH_TOKEN_MISSING`). Canvas **Refresh auth headers** proposes managed-header updates via Staging. Builtin `/login` heuristics apply only when project auth is completely empty. Uploads change API schema/`mode` only and do not overwrite Profile hints.

**Mutating calls:** enable **snapshot before** (`snapshotBefore`) on the node. On failure the run can pause; the user may restore SUT data and retry / retry in place / skip / abort. The SUT must expose `/test-support`. With env `allowDestructiveReset=0` (production default), checkpoint/restore are skipped silently. **When restore is enabled, run that environment serially.**

---

## Assert (`assert`)

Evaluates `data.rules[]` one by one (`CompareRuleEvaluator`); **all must pass**.  
Does not check HTTP status (that is the HTTP step). Typical rule fields: `left`, `operator` (default `eq`), `right` (placeholders allowed).

### Path dialects

| Use | Form | Notes |
|-----|------|--------|
| Assert / condition left | `http.body.data.code`, `http.body.data[?(@.cartId=='5001')].quantity` | Relative to last HTTP body; supports index, filter, `[*]`, `length()`. Do **not** write JSON Schema keyword `.items` into paths when `data` is already an array |
| Shorthand (normalized) | `$.data.code` | Becomes `http.body.data.code` at design-time and runtime |
| Extract / biz-code field | `$.data.token` | **Must start with `$`**, relative to body root |
| Other context | `flow.*` / `env.*` / `asset.*` / `http.status` / `http.duration` | **Not** JsonPath |

Forbidden: `http.body.$.…` (Confirm / save fails).

### Operators

UI: `eq/ne/gt/gte/lt/lte/contains/not_contains/exists`.  
Aliases: `equals`/`==` → `eq`; `notempty`/`not_empty` → `exists` (do not invent `notempty` in UI).

Collection semantics: `exists` requires non-empty collection; `eq` unboxes only when size is exactly 1; `contains` on a list passes if **any** element matches. Prefer `http.body.data[?(@.cartId=='5001')]` + `exists` when `data` is already an array (never `data.items[…]`). Design-time: confirming an **assert/condition** Staging unit checks `http.body…` lefts against the upstream API **response schema** on a preview graph that includes pending upstream nodes/edges (**hard-block** on structural mistakes like `.items` / `http.body.$.…`; **warning** when a field is missing from schema or schema is empty — confirm/save still allowed); missing upstream hard-blocks **that unit** (edge confirm is not blocked by the assert gate). AI submit / **save** still run the full-graph gate. Response **examples are for humans** (property-panel soft trial may highlight empty results) and do **not** hard-block. Formal Run does not apply this design gate.

---

## Condition (`condition`)

Scans `data.branches[]` in order IF → ELIF → ELSE:

- **if / elif**: hit when every entry in `conditions[]` is true (AND)
- **else**: fallback when nothing earlier matched

Matched branch must have a non-empty `target` (next node id). Result is stored on the step as `branchTaken` (`branchId` / `kind`).

---

## Assign (`assign`)

Applies `data.assignments[]` to the **`flow` scope only**:

| op | Behavior |
|----|----------|
| `set` | Resolve placeholders in value, then overwrite |
| `add` / `sub` / `mul` / `div` | Arithmetic from current value or `ifMissing`, by `step` |

Before/after per assignment is recorded on the step. Formal Runs use **strict** placeholders: undefined tokens fail the step.

---

## Delay (`delay`)

Blocks the worker thread for `data.ms`. Cap: **60_000 ms** per step.

---

## Script (`script`)

| Field | Meaning |
|-------|---------|
| `language` | `javascript` (default) or `python` |
| `source` | Script body |
| `timeoutMs` | Timeout (normalized to a safe range) |

Runs in a GraalVM sandbox: read/write `flow` and `session`; read `env` / `asset`; optional controlled `ctx.http`. `ctx.setFlow` merges into the current Run. Failures surface as step error code + message.

Use for project-specific signing or field assembly when declarative nodes are not enough; prefer HTTP / Assign / Assert when they suffice.

---

## Subflow (`subflow`)

Collapses another flow in the same project into one step:

1. Load by `subflowId` (must share `testProjectId`)
2. Resolve `inputs` placeholders into the child `flow` context
3. Run the child graph in memory (fail-fast); nesting is capped (parent → child → grandchild, max **2** subflow levels)
4. Merge `outputs` (or child `meta.flowOutputs`) back into the parent `flow`
5. Attach `subflow.childSteps` for Run UI and MCP `get_run_failure`

| versionPolicy | Behavior |
|---------------|----------|
| `latest` | Each Run loads the child’s current graph |
| `pinned` | Prefer node `pinnedGraphJson` snapshot |

Platform **subflow templates** (login / OAuth / captcha, …) can be forked and parameterized.

---

## Design tips

1. **Happy path:** HTTP (`project`) → Assert; variables via extracts / Assign.
2. **Branches:** wire Condition arms carefully before merge; extract complex arms into subflows.
3. **Reuse:** multi-step auth → Subflow; parent only maps inputs/outputs.
4. **Escape hatch:** signing / dynamic assembly → Script; keep scripts short and testable.
5. **Destructive writes:** `snapshotBefore` + env allows restore; don’t parallelize the same env.

See also: [deploy.en.md](./deploy.en.md), [mcp.en.md](./mcp.en.md), [demo AI prompts](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md).
