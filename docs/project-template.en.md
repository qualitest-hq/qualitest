# Project templates

> **Purpose:** How to apply auth / prefab asset packs into a project, AI cold-start, import / export, and save-as.  
> Chinese: [project-template.md](./project-template.md)  
> Schema & samples: [project-template/](./project-template/) · AI prompt: `qualitest-system/.../project-template/AI_PROMPT.md` (same as admin **Copy prompt**).  
> Concepts & Apply details: [project-summary.en.md §4.1](./project-summary.en.md).

Project templates hold **auth-related APIs only** (login / probe / captcha, etc.). **Do not** stuff full business APIs into a template — ingest those via the IntelliJ plugin, MCP `import_apis`, or the Web API library ([mcp.en.md §3.5](./mcp.en.md)).

---

## 1. What it is

One template row ≈ one Auth Profile seed pack. After you check it into a test project, Apply writes:

| Content | Result |
|---------|--------|
| Prefab APIs | Profile + project APIs (skip existing method+path); login APIs usually `auth.mode=none` |
| Prefab params | Asset-library credentials (e.g. `adminAuth` / `clientAuth`) |
| Prefab env | Fill create-project placeholder `baseUrl`, merge env vars (customized URLs are not overwritten) |
| Prefab flows | Login flows, etc.; `extracts` derive managed headers and `credentialApi` |
| Prefab prompts | Optional; built-in auth templates may leave this empty |

Managed-header derivation order on Apply:

1. Login-HTTP `extracts` in prefab flows (`template_flows`)  
2. Else **`match_config.credential`** (common slim fields: `asset`, `extract`, `tokenField`, `headerName`, `headerValueTemplate`, `cookieName`; short placeholders like `{{token}}` expand to `{{asset.<entry>.<field>}}`)  
3. Still missing a full `headerName` + `headerValueTemplate` → **reject the row** (no weak dual-side default `Bearer {{asset.adminAuth.token}}`)

Empty “Add Profile” rows in project settings no longer prefill `adminAuth` Bearer — set the managed header by hand, Apply, or AI.

**Broken existing Profiles** (e.g. client bound to `adminAuth`): edit in project settings; or delete the same-named Profile then **Add from project template** and Apply; or AI `upsert_auth_profile` (semi-auto confirm). Existing project libraries are **not** rewritten automatically.

Built-in read-only samples: `RuoYi Bearer` / `RuoYi Session` / `Client Bearer` / `Admin Bearer` (clone before editing).

---

## 2. Day-to-day (humans)

### 2.1 New project — pick templates

1. When creating a test project, check **at least one** template (dual mall: **Admin Bearer** + **Client Bearer**).  
2. After create, verify env `baseUrl` (demo target is usually `http://localhost:8801`).  
3. If a prefab login flow exists, hang it as a subflow on the main flow; ingest business APIs via plugin / MCP / Web.

Existing projects: project settings → **Add from project template**. **Same-named Profiles are skipped entirely** (env / assets may still backfill on some paths — see §4.1 in the summary).

### 2.2 Admin CRUD

Menu **Project templates**: list, enable, clone, edit drawer. Prefab APIs / assets / envs are editable here; **prefab flows are view-only** on the canvas — you cannot add or edit graphs in the template admin. For login flows, configure them in a test project then **Save as project template**, or import a full pack that includes flows. Built-in rows are read-only — clone first to change content.

---

## 3. Import / export

Entry: project-template list → **Import** / row **Export**.

| Shape | Use |
|-------|-----|
| **Slim** | Short JSON for humans / AI: apis, assets, env, credential, etc.; **no** flows (non-empty `flows` are ignored with a warning). Expands into DB on import; does **not** build flows in template admin |
| **Full** | Same shape as DB columns; **may include flows**; default export; save-as also produces full packs. With flows present you can Apply directly |

Import steps:

1. Open **Import** → optional **Copy prompt**, paste into any AI to generate slim JSON for the target system (or multiple sides separated by `---`). The prompt asks the model to **also write `assets` demo credentials** (from the target repo README, etc.) so you do not hand-fill passwords after import.  
2. Paste or upload JSON → preview / validate → confirm (optional overwrite-by-name).  
3. If a slim pack needs a login flow: in a working project, check the login flow and **Save as project template** (or import a full pack that already has flows); template admin stays view-only for graphs.  
4. Then Apply from the test project.

Schema: [`project-template.schema.json`](./project-template/project-template.schema.json); samples under `examples/`.

**Do not** use project templates as a full API-library ingest path; OpenAPI / plugin / MCP import of business APIs is a separate capability.

---

## 4. Save as full template from a project

Entry: test project → **Project settings** → **Save as project template**.

| Item | Rule |
|------|------|
| Primary | Checked **test flows** |
| Required | APIs and assets linked from those graphs (cannot uncheck) |
| Optional add-ons | Other auth APIs on the same Profile, other assets, first env, prompts |
| Note | Credentials in assets are stored **in cleartext** in the template; same name may overwrite |

Acceptance sketch: project with a working login → check login flow → save as → new empty project Apply → login flow and managed headers work.

---

## 5. Boundaries vs other features

| Goal | Where |
|------|--------|
| Cold-start auth APIs / assets | Copy prompt → slim JSON → import |
| Prefab login flow included | Save full template from a working project (or import a full pack) |
| Edit login flow | Edit on the test-project canvas, then save as again (template admin cannot edit flows) |
| Business API sync | IntelliJ plugin, MCP `import_apis`, or project API library — not templates |
| Survey / write flows / import APIs | [MCP](./mcp.en.md) (**does not** write templates; import needs **Allow MCP import APIs**) |

Asset references and credential storage: [assets.en.md](./assets.en.md).
