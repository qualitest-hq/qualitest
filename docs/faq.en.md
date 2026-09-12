# Qualitest FAQ

> **For:** day-to-day debugging, flow design, and Runs.  
> **Not here:** demo scenario checklists, smoke-test quirks, Agent click paths — see [全面测试手册.md](./全面测试手册.md) §F / §G.  
> Chinese: [faq.md](./faq.md)

---

## Auth & tokens

### Run: `{{flow.token}}` or `{{flow.adminToken}}` undefined

Usually: wrong login extract path (admin `$.token`→`adminToken`, client `$.data.token`→`token`); Bearer used without a login extract; or anonymous login still got `Bearer {{flow.token}}`. See [project-summary.en.md §4](./project-summary.en.md), [flow-variables-and-values.en.md](./flow-variables-and-values.en.md).

### Staging / save: `AUTH_*` codes

Hard blocks for token / login-extract / HTTP-required run on **Save** only — Staging ✓ can pass and Save still fail with AUTH. Check Profile `headerValueTemplate` (wrong side e.g. client using `adminAuth`) and login extracts first; not a confirm bug.

| Code | Fix |
| ---- | --- |
| `AUTH_LOGIN_EXTRACT_MISSING` | Add extract per managed header target |
| `AUTH_TOKEN_MISSING` | Add login / assign / subflow output, or flowSeed (tokens only); verify Profile header template |
| `AUTH_LOGIN_FLOWKEY_COLLISION` | Use separate `adminAuth` / `clientAuth` paths |
| `AUTH_HEADER_MANAGED` | Info only |

See [ai-staging.en.md §5](./ai-staging.en.md).

### Where to put passwords

Reusable main accounts: asset library + `{{asset.*}}`. Failure scenarios: **literals** on the node. Do not put passwords in flowSeed.

See [assets.en.md](./assets.en.md).

---

## AI & Staging

### Assistant says the graph changed; canvas is empty

✓ all Staging, then Save. “No Staging this turn” = no `submit` — ask it to submit or start a new chat with short prompt + api ids. [ai-staging.en.md](./ai-staging.en.md).

### Messy graph / two start nodes after many fixes

✕ all Staging or create a new flow. Do not patch Pinia or `graph_json` in SQL.

---

## Assets & files

### File upload Run fails or sends path as plain text

Library file upload → form-data row **`type=file`** → `{{asset.<key>.storagePath}}`. [assets.en.md](./assets.en.md).

---

## MCP

### MCP cannot edit the canvas

By design — read-only. Edit on Web AI → Staging → Save. [mcp.en.md](./mcp.en.md).

---

## Deploy & project setup

### Cannot connect / MCP 401

Backend up; correct `url` and `X-Project-Token`. [deploy.en.md](./deploy.en.md), [mcp.en.md](./mcp.en.md).

### Why must new projects pick an auth template?

Templates seed Profiles, anonymous login APIs, and prefab endpoints. [project-summary.en.md §4.1](./project-summary.en.md).
