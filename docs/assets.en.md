# Asset library

> **Purpose:** Project-scoped reusable values (secrets, scalars, uploaded file paths).  
> Chinese: [assets.md](./assets.md)

变量与测值：[flow-variables-and-values.en.md](./flow-variables-and-values.en.md). File-upload acceptance: handbook **T1.4**.

---

## 1. Shape

Assets live on the **test project**, not the environment. Unique `key`, optional `remark` (not referenced), one wrapper object:

```json
{
  "key": "clientAuth",
  "assets": {
    "clientAuth": { "mobile": "13800000001", "password": "Test@123456" }
  }
}
```

Reference: `{{asset.clientAuth.password}}`.

Built-in templates seed `adminAuth` (`admin` / `admin123`) or `clientAuth` (`13800000001` / `Test@123456`) on Apply. Login extract writes `asset.*.token` (or Session `jsessionId`). Login `bodyExample` and prefab login-flow body use `{{asset.*}}`.

---

## 2. Field types

| Type | Stored | Reference |
| ---- | ------ | --------- |
| Scalar | string / number / … | `{{asset.<key>.<field>}}` |
| **file** | `{ type, fileName, storagePath }` via platform `/common/upload` | form-data row **`type=file`**, value `{{asset.<key>.storagePath}}` |
| Object / array | nested fields | Dot path |

**Product file path:** library upload → `storagePath` → Run reads disk and builds a **real multipart file part**. Not RustFS (demo media / T3.1). Absolute local paths on the node are debug-only. Keep Qualitest fixtures out of the demo business bucket. Prefer &lt; 1.5MB (engine ~2MB per file).

---

## 3. Who can write

| Path | Behavior |
| ---- | -------- |
| Project Assets UI | Direct CRUD; file rows upload and persist |
| Web AI `upsert_asset_variables` | **Proposal only** until chat-side confirm; receipts have no secrets |
| MCP `list_asset_variables` | Keys, field names, `placeholderHint`; **no plaintext** |
| MCP write | **None** |

List assets before inventing `{{asset.*}}` keys.

---

## 4. vs API defaults and node overrides

API `testValueConfig` / `bodyExample` may use `{{asset.*}}` for the main account. Per-flow values go in `requestValueOverrides`. Do not store “disabled user” as default `clientAuth`.

---

## 5. Safety

Run reports redact password/token/secret-like fields. List tools omit plaintext so secrets stay out of the chat. Assets follow project membership / project tokens.
