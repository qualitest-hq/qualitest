<div align="center">

# Qualitest

**Enterprise API testing & quality platform**

Sync APIs, debug in-project, orchestrate flows, and design with AI — in one place, with fewer tool switches.

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

<img src="docs/images/overview.gif" alt="Qualitest overview: sync → debug → orchestrate → AI assist" width="860"/>

</div>

---

## Why Qualitest

API testing often fragments across tools and people:

| Before | With Qualitest |
|:-------|:---------------|
| Hand-copy paths into Postman; everyone keeps their own collection | IntelliJ plugin syncs once; the team shares one inventory |
| Env switches mean editing URLs by hand | In-project console: pick test / staging / prod, then send |
| Multi-step cases live in long scripts | Canvas orchestration — breaks and asserts stay visible |
| AI edits feel unsafe | Natural-language suggestions; **Diff before merge** |

You focus on **what** to test; the platform cuts the busywork.

---

## One loop

> **Where APIs come from → how you call them → how you chain them → who helps you design.**

| ① | ② | ③ | ④ | ⑤ |
|:---:|:---:|:---:|:---:|:---:|
| **IDEA sync** | **Console** | **Test flows** | **AI assist** | **MCP** |
| APIs from code | Multi-env debug | Canvas orchestration | Diff then merge | IDE can read it |

<p align="center"><sub>Closed loop · fewer switches · less re-entry</sub></p>

| Repo | Role |
|:-----|:-----|
| **This repo** | Platform + Web (`qualitest-ui/`) |
| [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) | Optional shop API target for demos |
| [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin) | IDEA plugin: Controllers → platform |

---

## Who it's for

<table>
<tr>
<td width="50%" valign="top">

### API authors

**No second copy-paste — and you can self-test**

Install the IntelliJ plugin and sync Controller definitions in one click. Teams share one inventory; you can also hit the console right away to verify your own APIs — no private Postman collections, no “what’s the real path?” chats.

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

**Your IDE can read the test project**

Connect via standard **MCP** (Cursor and others). Ask “what nodes are on this flow?” / “where did the last run fail?” — answers come from **live platform data**. With write enabled, the editor can also create / fix / run flows.

→ Setup: [`docs/mcp.en.md`](./docs/mcp.en.md)

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

### API console · select, switch env, send

<img src="docs/images/demo-api-console.gif" alt="API console: select API, switch environment, send and view response" width="820"/>

### Test-flow canvas · drag nodes and run

<img src="docs/images/demo-flow-canvas.gif" alt="Test flow: drag nodes, wire edges, run" width="820"/>

### AI design · Diff then merge

<img src="docs/images/demo-ai-diff.gif" alt="AI assist: natural language suggestion, Diff preview, then merge" width="820"/>

### MCP · IDE reads (and can write) the test project

Read-only by default; with **Allow MCP autopilot write** enabled, Cursor (etc.) can create / fix / run flows. Setup: [`docs/mcp.en.md`](./docs/mcp.en.md).

<!--
  Record (survey): Cursor + qualitest MCP → list_flows → ask topology / last failed Run with testFlowId → live platform data
  Spec: width ≤ 900px · 8–10 fps · ≤ 10s · < 1MB
  File: docs/images/demo-mcp-survey.gif
-->
<img src="docs/images/demo-mcp-survey.gif" alt="MCP survey: list flows, topology, and failed Run context in Cursor" width="820"/>

<!--
  Record (write): autopilot write enabled → Cursor create_flow / submit_* / run_test_flow → Web canvas updates via SSE
  Spec: width ≤ 900px · 8–10 fps · ≤ 10s · < 1MB
  File: docs/images/demo-mcp-autowrite.gif
-->
<img src="docs/images/demo-mcp-autowrite.gif" alt="MCP autopilot: create/submit/run a flow in Cursor; Web canvas syncs" width="820"/>

### Ecosystem: IDEA plugin (separate repo)

Demos for [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin); sample project: [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo).

<img src="docs/images/demo-idea-sync-project.gif" alt="IDEA plugin: project-level upload" width="820"/>

<img src="docs/images/demo-idea-sync-controller-all.gif" alt="IDEA plugin: upload entire Controller" width="820"/>

<img src="docs/images/demo-idea-sync-controller-pick.gif" alt="IDEA plugin: pick endpoints from a Controller" width="820"/>

---

### Longer stories · split into short clips

> These journeys **do not fit in 10s**. Watch the clips in order. Spec: width ≤ 900px · 8–10 fps · ≤ 10s each · under `docs/images/`.  
> Prefer [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) as SUT; plugin clips need [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin).

#### MCP autopilot · survey to green

Connect MCP → survey flows → (optional) enable write → Cursor create/fix + Run → Web canvas sync. Needs a project Token; write clips require **Allow MCP autopilot write**. See [`docs/mcp.en.md`](./docs/mcp.en.md).

##### A · Connect + read-only survey

<!--
  Record: copy mcp.json from project settings → qualitest tools in Cursor → list_flows → get_flow / failed Run → live data in the answer
  File: docs/images/demo-story-mcp-a.gif · < 800KB
-->
<img src="docs/images/demo-story-mcp-a.gif" alt="MCP story A: connect and survey flows / Runs" width="820"/>

##### B · Enable write + create/run in Cursor

<!--
  Record: enable & save autopilot write → reconnect MCP → Cursor create_flow / submit_* / run_test_flow → tools succeed
  File: docs/images/demo-story-mcp-b.gif · < 1MB
-->
<img src="docs/images/demo-story-mcp-b.gif" alt="MCP story B: write enabled, create and run from Cursor" width="820"/>

##### C · Web canvas syncs the change

<!--
  Record: open the flow canvas → SSE shows new nodes/edges (or discard-local-and-pull) → Run timeline visible
  File: docs/images/demo-story-mcp-c.gif · < 800KB
-->
<img src="docs/images/demo-story-mcp-c.gif" alt="MCP story C: Web canvas syncs MCP commits" width="820"/>

#### Main loop · sync to green again

Sync → console → login subflow → orchestrate → AI asserts → Run → fail → AI fix → green.

##### A · Plugin sync + console OK

<!--
  Record: IDEA project-level upload → API list appears → console send succeeds
  File: docs/images/demo-story-main-a.gif
-->
<img src="docs/images/demo-story-main-a.gif" alt="Story main A: sync then console OK" width="820"/>

##### B · Auth template + login subflow on the main flow

<!--
  Record: Subflow from platform Bearer login template → Start→Subflow→business HTTP
  File: docs/images/demo-story-main-b.gif
-->
<img src="docs/images/demo-story-main-b.gif" alt="Story main B: login subflow on main flow" width="820"/>

##### C · Orchestrate + first Run

<!--
  Record: add asserts → run → timeline (optional childSteps) → leave a failing assert for D
  File: docs/images/demo-story-main-c.gif
-->
<img src="docs/images/demo-story-main-c.gif" alt="Story main C: orchestrate and first run" width="820"/>

##### D · Fail → AI fix → green

<!--
  Record: failed Run → AI fix → Staging ✓ → save → re-run green
  File: docs/images/demo-story-main-d.gif
-->
<img src="docs/images/demo-story-main-d.gif" alt="Story main D: AI fix after failed run" width="820"/>

#### Writable SUT rollback · snapshot, pause, restore

Dirty demo scenario → snapshotBefore → pause on write failure → timeline snapshot → restore & retry → restore audit.

##### A · Load fail scenario + snapshot then run

<!--
  Record: demo-ui load F01 → env allow restore → HTTP snapshotBefore → run
  Needs: demo /test-support · File: docs/images/demo-story-restore-a.gif
-->
<img src="docs/images/demo-story-restore-a.gif" alt="Story restore A: load scenario and run with snapshot" width="820"/>

##### B · Pause decision panel

<!--
  Record: paused run → restore & retry / retry in place / skip / abort → click restore & retry
  File: docs/images/demo-story-restore-b.gif
-->
<img src="docs/images/demo-story-restore-b.gif" alt="Story restore B: pause decision panel" width="820"/>

##### C · Timeline audit: snapshot → restore → retry

<!--
  Record: timeline shows snapshot, fail step, restore, successful retry
  File: docs/images/demo-story-restore-c.gif
-->
<img src="docs/images/demo-story-restore-c.gif" alt="Story restore C: snapshot / restore / retry audit" width="820"/>

> Until GIFs are recorded, images may show as placeholders; drop files into `docs/images/` with the names above when ready.

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

Full loop (demo target + IDEA sync + AI / MCP): see [Deploy](./docs/deploy.en.md) · [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) · [IDEA plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin).

<details>
<summary>More docs</summary>

- [Deploy](./docs/deploy.en.md) · [Concepts](./docs/project-summary.en.md) · [FAQ](./docs/faq.en.md)
- [Flow nodes](./docs/test-flow-nodes.en.md) · [MCP](./docs/mcp.en.md)
- [UI / desktop](./qualitest-ui/README.md) · [Contributing](./CONTRIBUTING.md) · [Security](./SECURITY.md)
- [中文 README](./README.md)

</details>

---

<div align="center">

**Qualitest** · [Apache-2.0](LICENSE) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [QQ group](https://qm.qq.com/q/FBa9jDRhm) (`1105468427`)

<sub>Deployment details: [`docs/deploy.en.md`](./docs/deploy.en.md)</sub>

</div>
