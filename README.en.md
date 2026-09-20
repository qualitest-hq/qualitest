<div align="center">

# Qualitest

**Enterprise API testing & quality platform**

Sync APIs, debug in-project, orchestrate flows, and design with AI — plus MCP **write / run** so Cursor can build a flow and hit Run next to the code you just changed (backend or frontend).

<br/>

[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)
[![Website](https://img.shields.io/badge/Website-qualitest-0ea5e9?style=for-the-badge)](https://qualitest-hq.github.io/qualitest/)
[![QQ](https://img.shields.io/badge/QQ%20group-1105468427-12b7f5?style=for-the-badge)](https://qm.qq.com/q/FBa9jDRhm)

<br/>

[Website](https://qualitest-hq.github.io/qualitest/) ·
[中文 README](./README.md) ·
[Why Qualitest](#why-qualitest) ·
[The loop](#one-loop) ·
[Who it's for](#who-its-for) ·
[Demos](#demos) ·
[Try it](#try-it) ·
[QQ group](https://qm.qq.com/q/FBa9jDRhm)

<br/>

<p><strong>Loop</strong>: API ingest → console → flow canvas → AI Diff → MCP</p>

</div>

---

## Why Qualitest

API testing often fragments across tools and people:

| Before | With Qualitest |
|:-------|:---------------|
| Hand-copy paths into Postman; everyone keeps their own collection | **IntelliJ plugin** or **MCP import** into one shared inventory (not Java-only) |
| Env switches mean editing URLs by hand | In-project console: pick test / staging / prod, then send |
| Multi-step cases live in long scripts | Canvas orchestration — breaks and asserts stay visible |
| AI edits feel unsafe | Natural-language suggestions; **Diff before merge** |
| After changing code, self-test still means Postman / the browser | **MCP write / run**: build and run the flow in Cursor (backend and frontend) |


You focus on **what** to test; the platform cuts the busywork.

---

## One loop

> **Where APIs come from → how you call them → how you chain them → who helps you design.**

| ① | ② | ③ | ④ | ⑤ |
|:---:|:---:|:---:|:---:|:---:|
| **API ingest** | **Console** | **Test flows** | **AI assist** | **MCP** |
| Plugin / MCP import | Multi-env debug | Canvas orchestration | Diff then merge | Write=build · run=hit Run |

<p align="center"><sub>Closed loop · fewer switches · less re-entry</sub></p>

| Repo | Role |
|:-----|:-----|
| **This repo** | Platform + Web (`qualitest-ui/`) |
| [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) | Optional shop API target for demos |
| [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin) | IDEA plugin: Java Controllers → platform (optional; other stacks use MCP `import_apis`) |

---

## Who it's for

<table>
<tr>
<td width="50%" valign="top">

### API authors

**No second copy-paste — and you can self-test**

**Java**: install the IntelliJ plugin and sync Controllers in one click. **Other stacks**: connect MCP in Cursor (etc.), enable **Allow MCP import APIs**, then `import_apis` into the project library. Teams share one inventory; self-test in the console — no private Postman collections, no “what’s the real path?” chats.

</td>
<td width="50%" valign="top">

### API testers

**Debug console lives in the project**

Select an API and send; switch test / staging / prod in one click. Params, body, and response are right there. Single-call checks stay here; multi-step chains belong to test flows.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Case designers

**Drag-and-drop beats long scripts**

Build order, branches, and multi-API chains on a canvas. Fold login / OAuth into reusable subflows. Where it breaks and what to assert stays visible.

→ Node reference: [`docs/test-flow-nodes.en.md`](./docs/test-flow-nodes.en.md)

</td>
<td width="50%" valign="top">

### People who want speed

**AI suggests; you decide**

Describe intent in natural language — suggestions use **this project’s real APIs**. **Preview the Diff, then merge** — nothing silently rewrites your canvas.

→ Demo prompts: [AI test-flow prompts](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md)

</td>
</tr>
<tr>
<td width="50%" valign="top">

### AI-assisted coding

**After you change code, build and run the flow in Cursor**

Connect via standard MCP. **Auto-write** = the agent builds / edits the test flow; **auto-run** = the agent hits Run and pulls failures back into the chat.  
**Backend**: fewer Postman hops after shipping an API. **Frontend**: verify the API path your page depends on before blaming the UI. Read-only by default; enable write / run in project settings.

→ Plain-language scenarios: [`docs/mcp.en.md`](./docs/mcp.en.md)

</td>
<td width="50%" valign="top">

### Team leads

**Collaboration is project-scoped**

Invite members and assign roles per project. Issue separate Tokens for the plugin and CI — no shared personal passwords.

</td>
</tr>
</table>

---

## Demos

Prefer [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) as the SUT; Java sync is optional via [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin).

### API console

Select an API → switch test / staging / prod → send → inspect response and latency.

### Test-flow canvas

Drag HTTP / assert / condition / subflow nodes → wire edges → run; expand the timeline per step. Node reference: [`docs/test-flow-nodes.en.md`](./docs/test-flow-nodes.en.md).

### AI design

Open the AI panel beside a flow, describe intent (e.g. “retry after login failure”) → **preview Diff, then merge**. Demo prompts: [AI test-flow prompts](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/ai-test-flow-prompts.md) (needs an admin-configured model; skip if you have no key).

### MCP

Read-only by default; enable **Allow MCP auto-write**, **Allow MCP auto-run**, and/or **Allow MCP import APIs**. Plain-language write-up: [`docs/mcp.en.md`](./docs/mcp.en.md).

| Switch | In plain terms | Who benefits |
|:-------|:---------------|:-------------|
| **Auto-write** | Cursor builds / edits the test flow | Backend authors the chain after an API change; frontend mirrors the page path first |
| **Auto-run** | Cursor hits Run; failures return to the chat | Both sides hop to the browser less |

1. Copy `mcp.json` from project settings → qualitest tools appear in Cursor  
2. (Recommended) enable write + run and **reconnect MCP**  
3. `create_flow` / `submit_*` to build → `run_test_flow` to verify; Web canvas syncs over SSE  
4. Read-only still supports `list_flows` / `get_graph_summary` / `get_run_failure`  

### IDEA plugin (optional)

Tools → Qualitest Helper: **project-level upload** / Controller **upload all** / **select upload**. APIs land in the project library for the console.

### Suggested trial paths

1. **Main loop**: ingest via plugin or MCP → console OK → new project with auth templates → hang a login subflow → business HTTP / asserts → Run; on failure, AI fix then green.  
2. **MCP create**: connect Token → enable write / run and reconnect → create and run from Cursor → check the Web canvas (on failure, `get_run_failure` then fix).  
3. **Writable SUT rollback** (demo `/test-support`): load a fail scenario → env allow restore → HTTP snapshot-before → on pause choose restore & retry; timeline shows snapshot / restore.

---

## Try it

Requires Docker + Compose V2. First build is slow; **containers up ≠ ready to log in** — wait until the backend is healthy / Flyway finishes (`docker compose logs -f app`).

**Windows**

```bat
cd qualitest
scripts\quick-start.bat
```

**Linux / macOS**

```bash
cd qualitest
chmod +x scripts/quick-start.sh && ./scripts/quick-start.sh
```

Open `http://localhost` (default host port **80**; if busy, set `WEB_PORT=8088` in `.env` and open `http://localhost:8088`).

Username **`admin`**, password **`admin123`**. Do not use on the public internet.

Full loop (demo target + API ingest + AI / MCP): see [Deploy](./docs/deploy.en.md) · [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) · [MCP](./docs/mcp.en.md) · [IDEA plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin) (optional for Java).

<details>
<summary>More docs</summary>

- [Deploy](./docs/deploy.en.md) · [Concepts](./docs/project-summary.en.md) · [FAQ](./docs/faq.en.md)
- [Flow nodes](./docs/test-flow-nodes.en.md) · [MCP](./docs/mcp.en.md) · [Project templates](./docs/project-template.en.md)
- [Roadmap](./ROADMAP.md) · [UI / desktop](./qualitest-ui/README.md) · [Contributing](./CONTRIBUTING.md) · [Code of Conduct](./CODE_OF_CONDUCT.md) · [Security](./SECURITY.md)
- [中文 README](./README.md)

</details>

---

<div align="center">

**Qualitest** · [Apache-2.0](LICENSE) (commercial use allowed) · [Contributing](CONTRIBUTING.md) · [Code of Conduct](CODE_OF_CONDUCT.md) · [Security](SECURITY.md) · [QQ group](https://qm.qq.com/q/FBa9jDRhm) (`1105468427`)

<sub>Deployment: [`docs/deploy.en.md`](./docs/deploy.en.md). The names “质衡” and “Qualitest” belong to the maintainers; contribution terms: [CONTRIBUTING](CONTRIBUTING.md).</sub>

</div>
