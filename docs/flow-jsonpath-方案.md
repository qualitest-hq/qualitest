# 测试流完整 JsonPath 方案（设计稿）

> 状态：**已落地**（前后端 Jayway / jsonpath-plus、规范化、Confirm、试算、金样与文档同一次交付）  
> 交付原则：**一次做完，不分一期/二期**；同一 PR（或同一紧密相连的提交组）内前后端、规范化、Confirm、文档与单测一并合入。  
> 验收口径：合入后按 [v1.0-周末开源冲刺手册.md](./v1.0-周末开源冲刺手册.md) **§B.0.1** 用人可复现路径回归（画布断言 + Staging），禁止 store 旁路刷绿。  
> 背景：AI / 用户常写出 `$.data.items[?(@.cartId==5001)]`、`notempty` 等；当前引擎仅支持点分路径，未知运算符在 Run 期静默失败。  
> 目标：用成熟库补齐路径能力，并**兼容现有已落库写法**。

---

## 1. 结论先说

| 问题 | 答案 |
|------|------|
| 完整 JsonPath 能否兼容现有参数？ | **能。** 现有主流写法是 JsonPath 的子集（`$.a.b.c`、`http.body.a.b.c`），用标准库求值结果应与今日一致。 |
| 是否要自研解析？ | **不必。** 后端用 Jayway `json-path`；前端用同语义库或薄封装对齐。 |
| 实现是否「很复杂」？ | **接线不大**（替换 `simpleJsonPath` / `http.body` 导航 + 单测）。真正要写清的是：**多值结果、找不到路径、`$` 与 `http.body` 两套方言、运算符别名** 的约定。 |

---

## 2. 今天的两套「路径方言」

系统里其实有两类表达式，不要混为一谈：

### 2.1 响应体 JsonPath（`extracts[].expr`、业务码字段）

- 形态：必须以 `$` 开头，相对 **HTTP 响应 body 根**。
- 现状实现：`PlaceholderResolver.simpleJsonPath` / 前端 `simpleJsonPath`——仅 `$.a.b.c` 点分，**不支持** `[0]`、`[?()]`、`[*]`。
- 存量示例：`$.data.token`、`$.data.mobile`、`$.access_token`。

### 2.2 运行时上下文路径（断言 / 条件左值、`{{…}}` 占位符）

- 形态：**无 `$`**，带 scope 前缀：
  - `flow.*` / `env.*` / `asset.*`
  - `http.status` / `http.duration` / `http.header.*` / `http.headers.*`
  - `http.body.*` → 在上一步响应 body 上做**点分导航**（与 2.1 同源简化实现）
- 入口：`resolvePathSegment`（Java / TS 各一份）。
- 存量示例：`http.body.data.code`、`flow.token`、`http.duration`。

### 2.3 本次 E1 暴露的错位

AI 断言左值写成了：

```text
$.data.items[?(@.cartId==5001)]  +  notempty
```

问题叠加：

1. **左值缺 `http.body` scope**：`resolvePathSegment` 不认 `$…`，直接当未知路径 → `null`。
2. **即便改成 `http.body.data.items[?(…)]`**，今日点分导航也不认过滤器。
3. **`notempty` 不在运算符表**（仅有 `exists` 等）；未知 op → `false`（静默失败）。

完整 JsonPath 解决的是 **2**（以及 extract 侧同等能力）；**1** 与 **3** 仍需在方案里用「规范化 / 别名 / Confirm 校验」一并收掉，否则只上库仍会挂。

---

## 3. 兼容性说明（现有参数能不能继续用）

### 3.1 保证兼容（应保持行为不变）

| 写法 | 场景 | 说明 |
|------|------|------|
| `$.data.token` | extract / 业务码 | 标准 definite path，Jayway 与现导航一致 |
| `$.data.user.id` | extract | 纯对象链 |
| `http.body.data.code` | assert / condition / `{{http.body…}}` | 等价于对 body 求 `$.data.code` |
| `flow.x` / `env.x` / `http.status` 等 | 上下文 | **不走 JsonPath**，逻辑不变 |

回归策略：用现有 `PlaceholderResolverTest`、`HttpNodeHandlerTest` extract 用例 + 补一批「点分路径前后对比」金样；**禁止**仅因换库导致标量字段取值变化。

### 3.2 兼容前提下的能力扩展（今日会失败，升级后应变好）

| 写法 | 说明 |
|------|------|
| `$.data.items[0].cartId` | 下标 |
| `$.data.items[?(@.cartId==5001)]` | 过滤器（结果常为**列表**） |
| `$.data.items[*].skuId` | 投影 |
| `http.body.data.items[0].cartId` | 上下文路径上支持同一套后缀语法 |
| `http.body.data.items[?(@.cartId==5001)]` | 断言「是否命中行」的正规写法 |

### 3.3 建议额外兼容（低成本、高收益）

| 输入 | 规范化为 | 理由 |
|------|----------|------|
| 断言左值 `$….` | `http.body` + 去掉 `$` 后的 JsonPath，或 `http.body.` + 相对 path | AI 常把 extract 方言写到 assert |
| `notempty` / `not_empty` / `isNotEmpty` | `exists` | 与 UI 运算符表对齐 |
| `equals` / `==` | `eq`（已有） | 保持 |

以上可在 **AI patch 规范化**（`FlowDesignAssertNodeNormalizer`）与 **Confirm 校验** 两侧做，不依赖 JsonPath 库本身。

### 3.4 已知不保证 / 需显式约定的点

| 点 | 建议约定 |
|----|----------|
| 过滤器 / `[*]` 返回 **List** | `exists`：非 null 且（非空串；若为 Collection/数组则 **非空**）算通过；`eq`：与标量比较时，若仅 1 个元素可拆箱再比，多个元素则失败并提示 |
| 路径不存在 | 与今日一致：取值为 `null` / `undefined`（Jayway 配 `DEFAULT_PATH_LEAF_TO_NULL` 或统一 catch `PathNotFound` → null），**不**因换库改成抛错（正式 Run 的 STRICT 仅针对 `{{占位符}}`） |
| 仅 `$` 或 `http.body` | 返回整个 body 对象 |
| 脚本 / 非 Map 的 body | 先 `JSON` 序列化再 parse 进 JsonPath 配置；非 JSON 结构 → null，并在 Confirm/文档说明 |
| JsonPath 函数（如 `length()`） | **本次交付支持**（Jayway 默认能力内）；用例与文档写清示例 |
| 脚本滤镜 / 任意代码执行 | **不做**（见 §10 非目标） |

---

## 4. 推荐库

### 4.1 后端（质衡 Run 真相源）

- **Jayway JsonPath**：`com.jayway.jsonpath:json-path`
- 业界默认选择；过滤器、下标、投影齐全。
- 配置建议（实现阶段再落代码）：
  - `Option.DEFAULT_PATH_LEAF_TO_NULL`（或等价）：缺叶 → null，贴近现行为
  - 明确 `JSONProvider`：body 多为 Fastjson2 `JSONObject` / `Map` 时，用 **Jackson** 映射或先转成标准 `Map`/`List` 再读，避免 provider 与 Fastjson 类型拧巴
- **不要**再维护第二套手写 `navigate`，`simpleJsonPath` 改为库调用门面（可保留方法名以降低 diff）。

### 4.2 前端（设计态预览 / 本地模拟）

Run 以服务端为准；前端必须同期对齐，禁止「仅后端上 JsonPath、前端仍点分」。

- 库：`jsonpath-plus`（或实现时锁定的等价库）封装为同名 `simpleJsonPath` / `http.body` 求值
- 与后端共用金样用例表（同一 JSON、同一批 path → 期望值）
- 设计态提供 **对着上一次调试 / Run 响应试算 JsonPath**（输入表达式 → 显示结果或错误），与完整能力一起交付

### 4.3 为何说「没那么复杂」

核心改动面集中：

1. `PlaceholderResolver.simpleJsonPath` + `http.body.` 后缀求值  
2. `ExtractApplicator` / 业务码读取（已走 `simpleJsonPath`）  
3. 前端 `placeholder.ts` 对齐  
4. 规范化：`$` 左值、`notempty`  
5. 单测 + 少量文档（本文 + `test-flow-nodes.md` 补一节）

不涉及改图存储格式、不强制迁库已有 flow JSON。

---

## 5. 目标架构（逻辑）

```text
                    ┌─────────────────────────────────────┐
  extracts.expr     │  JsonPath.eval(body, expr)          │  要求 expr 以 $ 开头
  successCheck path │  （原 simpleJsonPath）               │
                    └─────────────────────────────────────┘

  assert/condition  ┌─────────────────────────────────────┐
  left / {{path}}   │  resolvePathSegment(ctx, path)      │
                    │    flow/env/asset/http.status|…     │  不变
                    │    http.body.<jsonpath-relative>    │──► JsonPath.eval(body, "$." + relative
                    │    纯 $.… → 视作 http.body（规范化） │      或 "$"+relative）
                    └─────────────────────────────────────┘
```

相对路径拼 `$` 时注意：

- `http.body.data.items[0]` → `$.data.items[0]`
- 若已误写 `http.body.$.data…` → Confirm 直接拒绝

---

## 6. 运算符与断言语义（与 JsonPath 配套）

现有 UI 运算符（`COND_OPERATORS`）保持：`eq/ne/gt/gte/lt/lte/contains/not_contains/exists`。

| 增强 | 行为 |
|------|------|
| `notempty` 等别名 | 规范化为 `exists`（设计态 + 运行态双保险） |
| `exists` + 集合 | 空列表 / 空数组 → false；非空 → true |
| `contains` + 列表左值 | **任一元素**转字符串后包含右值即通过；全不包含则失败（写死并单测）。购物车含 id 仍推荐过滤器 + `exists` |

**不**新增独立的 `notempty` UI 选项（别名归一到 `exists` 即可，避免运算符表膨胀）。

---

## 7. 设计态 / Confirm / AI

| 层 | 做什么 |
|----|--------|
| AI Normalizer | `$` 断言左值 → `http.body…`；`notempty` → `exists`；可选改写明显错误 |
| Confirm 校验 | 非法 scope、空 left、无法解析的 JsonPath 语法 → **确认失败**（早于 Save/Run） |
| Prompt / 工具摘要 | 写明：extract 用 `$…`；assert 左值用 `http.body…`（允许过滤器）；运算符用 `exists` 非 `notempty` |
| 幽灵边等问题 | 另案（端点必须存在）；与 JsonPath 无关但同属「Confirm 早失败」原则 |

人用正规路径：**确认报错 → 改节点或让 AI「修复本节点」→ 再确认**，而不是改 Pinia / 改库。

---

## 8. 一次交付范围（Checklist）

下列全部进入**同一次交付**，不做「先能用再补体验」拆分：

| # | 项 |
|---|-----|
| 1 | 后端 Jayway 替换 `simpleJsonPath`；删除手写点分 `navigate` 作为主路径（可留私有测试对照至删除） |
| 2 | `http.body.<…>` 与 extract 的 `$…` 走同一求值门面；支持下标、`[*]`、`[?()]`、Jayway 内置函数（含 `length()`） |
| 3 | 前端同期换库对齐 + **调试/Run 响应上的 JsonPath 试算** |
| 4 | `notempty` 等 → `exists`；断言左值 `$…` → `http.body…`（设计态 Normalizer + 运行态双保险） |
| 5 | 多值 / PathNotFound→null / `exists` 集合 / `eq` 唯一拆箱 / `contains` 任一元素 —— 语义按本文一次定死并单测 |
| 6 | Confirm：坏 JsonPath、非法 `http.body.$.…`、空 left → **确认失败 + 可读错误** |
| 7 | Run 详情断言失败时展示左值**实际求值结果**（含空列表），避免再「静默 false」 |
| 8 | 更新 `test-flow-nodes.md`（及英文版若有对等节）、AI prompt / 工具摘要中的路径与运算符说明 |
| 9 | 金样兼容 + 扩展 + 规范化 + 前后端契约表 + 现有相关单测全绿 |

合并门禁：上述未完成则不算本需求完成；**禁止**只合并后端半套。

---

## 9. 测试计划（同一次交付内）

1. **金样兼容**：旧 `$.data.token`、`http.body.data.code` 与换库前逐值对比。  
2. **扩展**：`[0]`、`[*]`、`[?(@.cartId==5001)]`、`length()`、空匹配、多匹配 + `exists`/`eq`/`contains`。  
3. **断言规范化**：`notempty`、`$` 左值。  
4. **Extract**：带过滤器的 extract 写入 `flow` 后，下游 assert 可读。  
5. **Confirm**：故意坏路径须失败且文案可读。  
6. **前端试算**：同一金样 path 在试算面板与 `simpleJsonPath` 一致。  
7. **回归**：`CompareRuleEvaluator*`、`PlaceholderResolver*`、`HttpNodeHandler*`、前端 `placeholder.test.ts` / `compareRule.test.ts`。

---

## 10. 非目标（明确不做，不是「以后再说的期」）

- 替换 Fastjson2 为全局 Jackson（仅为 JsonPath 读 body 做必要转换除外）  
- 浏览器内嵌 Jayway/WASM；前后端以**契约金样同结果**为准即可  
- 支持任意脚本滤镜 / 不安全 eval（`(?…)` 脚本扩展等）  
- 批量改写库里历史 flow JSON（打开图时走 Normalizer / Confirm 即可）

---

## 11. 工作量粗估

| 项 | 量级 |
|----|------|
| 后端门面 + 配置 + 规范化 + Confirm | 中 |
| 前端对齐 + 试算 UI | 中 |
| 单测与金样 / 契约表 | 中 |
| 文档 / prompt | 小 |

一个聚焦交付完成；复杂度在语义与测试，不在自研解析。

---

## 12. 待拍板（实现前写死进代码，不再分期纠结）

1. 断言左值允许简写 `$….` 并规范化为 `http.body…`？→ **是**。  
2. 多元素 `eq`？→ **仅当结果恰好 1 个元素时拆箱再比，否则失败**。  
3. 前端库？→ 默认 **`jsonpath-plus`**，若实现中发现硬伤再换等价库（须保持金样）。  
4. 对外文档？→ **同一次交付**改 `test-flow-nodes.md`（及英文对等节）。

---

## 13. 与周末冲刺的关系

- 本方案一次落地后，E1 类「车内是否含 cartId」可用过滤器 + `exists`。  
- 合入并重启 8080 前，冲刺可继续其它流；遇同类断言等本能力或临时改节点。  
- 手册 §F 可记：JsonPath 不足 / `notempty` 静默失败 —— 本交付关闭。

---

## 参考（实现时查阅）

- 后端：`PlaceholderResolver`、`ExtractApplicator`、`CompareRuleEvaluator`、`FlowDesignAssertNodeNormalizer`  
- 前端：`apps/web/src/utils/flow/placeholder.ts`、`compareRule.ts`  
- 节点说明：`docs/test-flow-nodes.md` § Assert  
- Jayway：https://github.com/json-path/JsonPath  
