# Flow variables & HTTP test values

> Two topics in one place: where **`{{…}}` resolves**, and how an **HTTP node’s final request** is built (API defaults vs node overrides).  
> Chinese: [flow-variables-and-values.md](./flow-variables-and-values.md)

Assert / extract dialects: [test-flow-nodes.en.md](./test-flow-nodes.en.md). Asset library: [assets.en.md](./assets.en.md).

---

## 1. Writing `{{…}}`

Formal Run is **strict** (undefined → `TF_PLACEHOLDER_UNDEFINED`). Debug may be lenient.

| Scope | Meaning | Example |
| ----- | ------- | ------- |
| `flow` | Variables in this Run | `{{flow.token}}` |
| `env` | Current test environment | `{{env.baseUrl}}` |
| `asset` | Project asset library | `{{asset.clientAuth.password}}` |
| `http` | Last HTTP snapshot | Mostly asserts |

No `{{session.*}}` — use Script `ctx.session` instead. Assets use **dot** paths; files use `{{asset.<key>.storagePath}}`.

---

## 2. Where `flow` comes from

HTTP **extracts**, **Assign**, subflow **outputs**, scenario **flowSeed** (tokens only, not passwords). Login extracts follow Profile `loginHint`.

---

## 3. Where HTTP request values come from

```text
API asset defaults → node requestValueOverrides → resolve {{…}} → send
```

Overrides may only have **`paramDefaults`** and **`bodyExample`**. Do not put body fields at the override root.

---

## 4. Passwords & scenario accounts

Main accounts: assets + `{{asset.*}}`. Failure cases: **literals** on the node. flowSeed: tokens only.

---

## 5. Not the same as assert / extract syntax

| Use | Form |
| --- | ---- |
| Request params | `{{flow.token}}` |
| Extract / biz-code | `$.data.token` |
| Assert left | `http.body.data.code` or `$.data.code` |

---

## 6. Typical errors

Undefined `{{flow.token}}` → login extract / auth header. Missing field → bad override shape. File as string → form-data `type=file` + `storagePath`.
