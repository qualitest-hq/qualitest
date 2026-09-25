# credentialApi 与 extracts：待办备忘

> 状态：**本轮不改代码**。来自「悠度假」问题梳理时对鉴权模型的质疑：`credentialApi` 与节点 `extracts` 职责重叠，像冗余指针。单独记档，后续立项再定去留。

## 1. 为啥先搁置

- 动 `credentialApi` 会牵造流硬拦、模板派生、Profile upsert、UI、Skill/MCP 文案，不是小补丁。
- 当前跑流假绿、业务码摘要等更急；本字段不修也不挡那些修复。
- 需要先定产品：是「删掉、只留 extracts」还是「保留但降级为可选索引」。

## 2. 三样东西各自干什么

| 东西 | 干什么 | 在哪 | 跑流是否执行 |
|------|--------|------|----------------|
| **extracts** | 响应里抽啥、写到 `asset`/`flow` 哪 | HTTP **节点** | **是**（真干活） |
| **headerValueTemplate** | 后续请求怎么带凭证 | Profile | 组请求时替换占位符 |
| **credentialApi** | 只记登录口 `method`+`path` | Profile | **否**（元数据标签） |

悠度假示例（Profile 上）：

```json
"credentialApi": { "method": "POST", "path": "/login" }
"headerValueTemplate": "JSESSIONID={{asset.adminAuth.JSESSIONID}}"
```

节点上另有：

```json
"extracts": [{
  "from": "setCookie",
  "expr": "JSESSIONID",
  "scope": "asset",
  "entryKey": "adminAuth",
  "fieldPath": "JSESSIONID"
}]
```

## 3. 为何觉得重复

- **真正抽凭证的是 extracts**；没有 extracts，光有 `credentialApi` 跑流也拿不到 Session/token。
- 模板 Apply 文档写明：托管头 + `credentialApi` **优先从登录流 extracts 派生** → 承认 extracts 是源头，`credentialApi` 是抄出来的指针。
- `suggestedExtracts`（`get_api_details` 提示）又想再猜一层，和「AI/人按 headerValueTemplate 自定 extracts」叠床架屋；空数组还误导 Agent。

可记成：

```text
extracts（源头）
  → 派生/标注 → credentialApi（索引：哪个口该有 extracts）
  → 引用目标 → headerValueTemplate（后续怎么带）
```

## 4. 今天还能拿它干什么（删之前要接盘）

- 认出「这是发凭证口」：`LoginExtractSuggestor.isCredentialApiEndpoint`、designHints
- 造流硬拦登录口缺 extract：`LoginExtractPresenceGate` / `AUTH_LOGIN_EXTRACT_MISSING`（须命中 credentialApi）
- Profile 列表/upsert、项目设置 UI 展示与编辑
- 预制模板种子：`PrefabricatedTemplateExtrasSupport` 写入 Profile

若删除，至少要有替代：「哪个 HTTP 节点 extracts 写入了本端 headerValueTemplate 所引用的 asset 键 → 该节点绑定的 API 即登录口」。

## 5. 后续可选路线（未决策）

| 路线 | 做法 | 利弊 |
|------|------|------|
| **A. 删除 credentialApi** | 登录口只由「extracts → 与 header 模板同目标」推断；改门禁与工具 | 模型干净；迁移与兼容成本高 |
| **B. 保留但只读派生** | UI/MCP 不手填；保存登录流或 extracts 时自动回写 Profile | 仍有冗余，但少一份手维配置 |
| **C. 维持现状** | 文档写清「标签 ≠ 抽取」；Skill 教 AI 写 extracts，不依赖 suggestedExtracts | 改动最小；概念债仍在 |

立项时再选。**本轮不选、不改。**

## 6. 和本轮其它项的关系

| 项 | 本轮 | 说明 |
|----|------|------|
| 失败不写 asset extracts | 做 | 运行时门禁，与 credentialApi 无关 |
| 强化 suggestedExtracts / LoginExtractSuggestor | **不做** | 不堆自动建议 |
| Skill 写清 extracts 自定 | 可随 preScript/鉴权文案轻带 | 不依赖拆字段 |
| 删除或合并 credentialApi | **不做** | 见本文 |

## 7. 待决策清单

1. 选 A / B / C（或分阶段：先 B 再 A）。
2. 存量项目（悠度假等）如何迁移：手填的 `credentialApi` 与流上 extracts 不一致时以谁为准。
3. `apis[]` 与 `credentialApi` 双门槛（`findCredentialProfile` 还要求 apis 含该口）是否一并废掉。
4. `suggestedExtracts` 是否从工具契约中降级或删除，避免 Agent 依赖空数组。
