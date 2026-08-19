# 素材库

> **用途**：项目级可复用测值（口令、普通字段、上传文件路径）怎么存、怎么引用、AI 怎么改。  
> 英文：[assets.en.md](./assets.en.md)

`{{…}}` 与请求测值见 [flow-variables-and-values.md](./flow-variables-and-values.md)。测参文件验收步骤见手册 **T1.4**。

---

## 1. 是什么

素材挂在 **测试项目**（`asset_variables` JSON），不挂环境。每条有项目内唯一 `key`、可选 `remark`（不参与引用）、以及一层包装：

```json
{
  "key": "clientAuth",
  "remark": "主测号",
  "assets": {
    "clientAuth": {
      "mobile": "13800000001",
      "password": "Test@123456"
    }
  }
}
```

引用：`{{asset.clientAuth.mobile}}`、`{{asset.clientAuth.password}}`。

---

## 2. 字段类型

UI 按行选类型（与调试台 KV 表同类）：

| 类型 | 落盘 | 引用 |
| ---- | ---- | ---- |
| 普通标量 | 字符串 / 数等 | `{{asset.<key>.<field>}}` |
| **file** | `{ type, fileName, storagePath }`；选文件走平台 `/common/upload` 落到本机盘 | HTTP form-data **`type=file`** 的 value = `{{asset.<key>.storagePath}}` |
| 对象 / 数组 | 可加子字段 | 点分下钻 |

**文件主路径**：素材库上传 → `storagePath`（如 `/profile/upload/...`）→ 跑流读盘组 **真 multipart 文件 part**。不依赖 RustFS（那是 demo 业务媒体 / T3.1）。

节点上填本机绝对路径仅调试兜底，**不能**当作「产品测参已通」。质衡测参文件不要塞进 demo 业务 bucket。

样例建议 &lt; 1.5MB（引擎单文件约 2MB 上限）。

---

## 3. 人怎么改 vs AI 怎么改

| 路径 | 行为 |
| ---- | ---- |
| 项目「素材库」页 | 直接 CRUD；file 行选文件并持久化 |
| Web AI `upsert_asset_variables` | **只记提案**，聊天侧确认后才落盘；回执不含明文 |
| MCP `list_asset_variables` | 只读：key、字段名、`placeholderHint`；**无明文** |
| MCP 写入 | **没有**；改素材回 Web |

造流前模型应先 `list_asset_variables`，再写 `{{asset.*}}`，避免臆造 key。

---

## 4. 和接口默认测值、节点覆盖

- 接口资产 `testValueConfig` / `bodyExample` 可以写 `{{asset.*}}`，全项目接口共用主测号。
- 某条流要换测值：节点 `requestValueOverrides`，不要改素材污染其它流。
- 失败用例（停用号等）：优先节点字面量，不要把「坏账号」做成默认 `clientAuth`。

---

## 5. 安全

- 跑流步骤报告会对 password / token / secret 等字段脱敏。
- AI 列举工具故意不带回明文，避免口令进对话上下文。
- 项目 Token / 成员权限按项目隔离；素材随项目走。
