# 鉴权注入与 Bearer 方案（定稿）

> **状态**：设计定稿 · **分阶段实施**（**P0～P4a 已落地**：插件免登、双端 Profile 种子、补头、Staging/工具可见、匿名 path、**刷新本流托管头**；Run 遇鉴权失败走「AI 修复」对话改图，不做专用 401 启发式提案；OpenAPI / Cookie Profile 仍按需）  
> **范围**：工作区 `qualitest-all` —— 质衡主仓 `qualitest/`、靶场 `qualitest-demo/`、扫描插件 `qualitest-intellij-plugin/`  
> **触发**：T2 冒烟 AI 造流登录后调 `/api/account/auth/profile` 等接口 **401**（漏 `Authorization: Bearer {{flow.token}}`）  
> **相关**：[`test-flow-nodes.md`](./test-flow-nodes.md)、[`mcp.md`](./mcp.md)、[`全面测试手册.md`](./全面测试手册.md)、`tpl_login_bearer`  
> **实施纪律**：禁止「插件 + Profile + Normalizer + 调试台 + Staging」一次合入；每期可独立合并、独立回归，见 §7

---

## 1. 本仓库里鉴权实际长什么样

### 1.1 被测方（qualitest-demo）

| 事实 | 代码/行为 |
|------|-----------|
| 默认要登录 | `SecurityConfig`：`anyRequest().authenticated()` |
| 显式免登录 | 方法/类 `@Anonymous` → `PermitAllUrlProperties` 收进 `permitAll` |
| 另有 path 白名单 | `/login`、`/register`、`/captchaImage`、Swagger、静态资源、`/test-support/**` 等（**无注解**） |
| **双端 JWT** | `JwtAuthenticationTokenFilter`：`uri.startsWith("/api")` → 客户端 `AccountTokenService`；否则 → 管理端 `TokenService` |
| 头形态相同、值不能混 | 都是 `Authorization: Bearer …`，客户端 / 管理端 token **不可混用** |
| 公开商城浏览 | 如分类/部分商品接口已标 `@Anonymous` |

客户端登录：`POST /api/account/auth/login` → 抽到 `flow.token`。  
管理端登录：`POST /login` → 抽到 `flow.adminToken`。

### 1.2 质衡平台（现状能力）

| 能力 | 现状 | 缺口 |
|------|------|------|
| HTTP 节点 `headers` | AI/手工可写 Bearer | 模型常漏，无自动补全 |
| `useRunSession` | Run 级 **Cookie** | **不能**代替 Bearer |
| 素材库 | `{{asset.clientAuth.*}}` 等 | 管账号，不管 Authorization |
| 登录子流 | `tpl_login_bearer` 等 | 不保证后续节点带头 |
| `FlowDesignPatchNormalizer` | 规范化节点 | **已**按鉴权补 `profileManaged` 托管头 + warning |
| Staging / §B.0.1 | 改图须确认 | 补头须进 Diff |
| MCP | 只读 | 不写鉴权、不改图 |

### 1.3 IDEA 插件（现状）

| 能力 | 现状 | 说明 |
|------|------|------|
| 路径、方法、Body、Query | ✅ | — |
| `@RequestHeader` | ✅ | 少见 |
| 免登录注解列表 | ❌ **P0 先做** | **插件设置** `anonymousAnnotations[]` → 上传 `auth.mode=none` |
| `SecurityConfig` permitAll | ✅ **P3** | 项目 `anonymousPath*`（不解析 SecurityConfig）；上传命中 → `mode=none` |

---

## 2. 目标与原则

**目标**：造流/调试时需登录的 `project` HTTP 带上正确端 Bearer；双端不串 token；公开接口不加头。

| # | 原则 |
|---|------|
| 1 | **项目级定义 Profile，接口继承**；不在每个 API 复制头模板 |
| 2 | **Bearer ≠ Cookie** |
| 3 | **双端两套 Profile / 两套 `flow.*`**：`token` vs `adminToken` |
| 4 | 免登录注解在 **插件**；项目管 Profile +（可选）匿名 path |
| 5 | 补头 **Staging 可见**；MCP 不写库 |
| 6 | 配置挂 **项目**，不挂环境 |
| 7 | **改 Profile 后旧流仍按当前项目配置解析**（见 §4.5），避免头字符串永久写死 |

---

## 3. 业界映射（简）

Postman/Apifox：集合定鉴权、请求 Inherit → 质衡 **项目 `authProfiles` + 接口 `mode=inherit`**。  
质衡多一刀：造流 **Normalizer** + Run/调试 **同一解析器**。

---

## 4. 数据模型

### 4.1 项目级 JSON（主配置）

存测试项目设置。demo / 商城联调推荐默认：

```json
{
  "defaultProfileId": "adminBearer",
  "authProfiles": [
    {
      "id": "clientBearer",
      "name": "客户端 Bearer",
      "match": {
        "pathPrefix": ["/api/"]
      },
      "header": {
        "name": "Authorization",
        "valueTemplate": "Bearer {{flow.token}}"
      },
      "loginHint": {
        "flowKey": "token",
        "extractJsonPath": "$.data.token"
      }
    },
    {
      "id": "adminBearer",
      "name": "管理端 Bearer",
      "match": {
        "pathPrefix": ["/system/", "/monitor/", "/tool/", "/web/"]
      },
      "header": {
        "name": "Authorization",
        "valueTemplate": "Bearer {{flow.adminToken}}"
      },
      "loginHint": {
        "flowKey": "adminToken",
        "extractJsonPath": "$.token"
      }
    }
  ]
}
```

| 字段 | 含义 |
|------|------|
| `authProfiles[]` | 多套 Bearer 定义；**头模板只在这里维护** |
| `match.pathPrefix` | **具体前缀**；禁止再用 `"/"` 当 match |
| `defaultProfileId` | 未命中任何 `match`、且需要登录时的兜底（demo 用 `adminBearer`，对齐「非 /api → 管理端」） |
| `loginHint.flowKey` / `extractJsonPath` | 造流/AI 抽 token；**不再要**含糊的 `preferredLoginApiHint` 字符串 |

**P3 已纳入种子默认**（`dualBearerTemplate`）：

```json
{
  "anonymousPathExact": ["/login", "/register", "/captchaImage"],
  "anonymousPathPrefix": ["/test-support/", "/swagger-ui", "/v3/api-docs"]
}
```

老项目若已有 Profile 但缺上述字段：任意次接口导入时会 **回填默认匿名 path**（不覆盖已有非空列表）。
**已砍 / 不放进项目 JSON**：`anonymousAnnotations`（→ 插件）、Profile `exclude`、`confidence`、`authInferenceMode` 三态枚举（**P1** 起用下面匹配规则即可）。

#### Profile 匹配算法（定死）

```text
输入：apiPath（建议先规范化：去 context-path、统一前导 /、去尾 /）
1. 若接口 auth.mode=none → 不套 Profile、不补头
2. 若接口已指定 authProfileId 且存在 → 用该 Profile
3. 否则：在 authProfiles 中找 pathPrefix 命中者；
   若多个命中，取 pathPrefix 最长者
4. 若无人命中 → 使用 defaultProfileId
5. 禁止把 pathPrefix="/" 配进 match
```

demo 效果：`/api/**` → `clientBearer`；`/system/**` 等 → `adminBearer`；其余需登录 → `defaultProfileId=adminBearer`。

#### 4.1a IDEA 插件：免登录注解

配置在插件（Settings / 项目级），**不进**质衡项目 JSON：

```json
{
  "anonymousAnnotations": ["Anonymous", "PermitAll", "SaIgnore", "com.example.security.NoAuth"]
}
```

- 可多个；短名或 FQCN；方法/类命中任一 → 上传 `auth.mode=none`  
- demo 默认有 `Anonymous` 即可  

### 4.2 接口级（薄标签）

```json
{
  "auth": {
    "mode": "inherit",
    "authProfileId": "clientBearer"
  }
}
```

| `mode` | 含义 |
|--------|------|
| `inherit` | 按 §4.1 算法解析 Profile 并补头 |
| `none` | 免登录（**P0 插件注解** / P3 匿名 path / 人工） |
| `override` | 本接口自定义 `header.name` + `valueTemplate`（越权等） |

P0 至少落 `mode`；`authProfileId` / `source` 可空，**P1** 再按路径回填。

### 4.3 解析器（造流补全 / Run / 调试台共用）

```text
1. mode=none        → 不加鉴权头
2. mode=override    → 用接口 override
3. mode=inherit     → 解析 Profile → valueTemplate
4. 节点 headers 已有同名 headerName
     → 若标记为 profileManaged（见 §4.5）→ 仍用当前 Profile 覆盖值
     → 否则视为人手/AI 显式写入 → 不覆盖
```

### 4.4 和现有对象

| 对象 | 职责 |
|------|------|
| 项目设置 | （P1）`authProfiles` + `defaultProfileId`；（P3）`anonymousPath*`（种子默认，无专用 Web 编辑器） |
| IDEA 插件 | （P0）`anonymousAnnotations[]` |
| 环境 | `baseUrl` / 还原等，**不放** Profile |
| 素材库 | 登录账号 |
| HTTP 节点 `headers` | 落盘；Profile 托管头带标记 |
| `useRunSession` | 仅 Cookie 场景 |

### 4.5 改 Profile 之后，旧流怎么办（定死）

| 策略 | 说明 |
|------|------|
| **提交时** | Normalizer 写入头，并打 `profileManaged: true`（实现可用 headers 项扩展字段或节点 data 旁路字段；UI Staging 显示「按项目鉴权补全」） |
| **Run / 调试时** | 对 `project` HTTP：若需登录且（无头 **或** `profileManaged`）→ **按当前项目 Profile 再解析一遍**再发请求 |
| **人手/AI 显式头** | 无 `profileManaged` → 永不被 Profile 静默改掉（越权用例安全） |
| **可选 UI** | 「按项目鉴权刷新本流托管头」——批量重写 `profileManaged` 头，仍走保存/Staging 规范；**已落地**：画布顶栏「刷新鉴权头」→ `POST /project/testFlow/refreshAuthHeaders` → Staging |

这样项目改 `valueTemplate`（例如改 token 变量名）后，旧流 Run 立即跟新配置，不必全员重造流。

---

## 5. 行为设计

### 5.1 上传 / 同步

**P0（插件期）**

1. 插件按 `anonymousAnnotations` 扫描 → 命中则上传 `auth.mode=none`。  
2. 未命中：可省略 `auth`，或上传 `mode=inherit`（**不要求**此时已有 Profile）。  
3. 质衡侧：持久化接口薄 `auth`（至少 `mode`）；Web 可读即可，复杂编辑可留给后续期。

**P1+**

4. 服务端：已是 `none` 则保持；否则 `inherit` + 按 §4.1 算法写 `authProfileId`（可空=运行时再 match）。  
5. （P3）再套 `anonymousPath*`；可展示推断清单供改。

### 5.2 造流补全（**P1 核心**）

`FlowDesignPatchNormalizer`：需登录且缺托管头 → 追加 Profile 模板并 `profileManaged` + warning → Staging。

### 5.3 AI 接入

**主：Normalizer / Run 解析器；辅：Prompt + 工具回传 `auth`/`headerHint`。**

- Prompt：Bearer≠Cookie；`flow.token` / `flow.adminToken`；勿写死 Token；免登录不加头。  
- `get_api_detail` / `search_apis`：回传精简 `auth` + `headerHint`（与解析器同一套）。  
- 不把整份项目 JSON 塞进每轮 context；可选并入 `get_flow_meta` 的 auth 摘要（**不强制新工具**）。  
- 账号走素材；token 只进 `flow.*`；子流输出名对齐 `loginHint.flowKey`。

（Prompt 全文草案实现时贴入 `flow-design-system-prompt.txt`，要点同上。）

### 5.4 Staging 门禁（P2）

分端检查：用到 `clientBearer` 则图中须有 `flow.token` 来源；`adminBearer` 须有 `flow.adminToken`。不可「有任一 token 即过」。

### 5.5 调试台（**P1，与造流同解析器**）

调试发送 `project` 接口时走 §4.3，避免只修造流、调试仍 401。

### 5.6 鉴权失败回写（P4）

**不做**专用「401 启发式改图提案」。Run 详情「AI 修复」带上失败 Run；若步骤含 HTTP 401，预填鉴权排查提示，由模型 `submit_flow_design_patch` → Staging；禁止自动改图 / 自动改 `mode`。

确定性批量对齐 Profile 走 §4.5「刷新鉴权头」（P4a），与是否 401 无关。

### 5.7 与有效配置合并

补头结果并入节点 `headers` 后，须与现有 `TestProjectApiEffectiveConfigResolver`（资产 requestConfig + overrides）顺序一致：**显式节点头优先于资产默认头**；`profileManaged` 头在 Run 前按 §4.5 刷新。实现时补单测，避免补了又被盖掉。

---

## 6. 端到端

| 场景 | 期望 |
|------|------|
| A1 登录+资料 | AI 可不写 Authorization；Staging/Run 仍有 `Bearer {{flow.token}}` |
| `@Anonymous` 分类 | `mode=none`，不补头 |
| 双端审核流 | 客户端步骤 client 头；管理端步骤 admin 头 |
| 越权 F09 | 显式头、无 `profileManaged`，不被刷新覆盖 |
| 改项目 valueTemplate | 托管头旧流 Run 跟新模板 |

---

## 7. 分期（插件优先 · 禁止一把梭）

### 7.1 为什么要拆

| 风险 | 若一次做完 |
|------|------------|
| 改动面 | 插件扫描 + 项目 JSON + Normalizer + Run/调试解析 + Prompt + Staging，任一环回归失败难定位 |
| 验收混杂 | 「免登录标错」和「漏 Bearer」同时炸，分不清是注解还是补头 |
| 可回滚 | 大 PR 难 revert；小期可独立合入 / 回退 |

**结论：先插件、再平台补头、再体验与白名单。** 不把「一起实现」当作默认路径。

### 7.2 期次表

| 期次 | 仓 | 做什么 | 本期交付 / 不做什么 | 独立验收 |
|------|----|--------|---------------------|----------|
| **P0 插件** | `qualitest-intellij-plugin`（+ 质衡侧薄 `auth` 落库若尚无） | Settings：`anonymousAnnotations[]`（可多个，短名/FQCN）；扫描方法/类注解；上传带 `auth.mode=none`（命中）或 `inherit`/省略（未命中） | **做**免登录标签进资产；**不做** Profile、Normalizer、自动补 Bearer、`anonymousPath*`、SecurityConfig 解析 | 重扫 demo：带 `@Anonymous` 的分类等 → 资产 `mode=none`；登录/下单等无注解 → 非 `none`。**不期望**单独修好 T2.A1 的 401 |
| **P1 平台补头** | `qualitest` | 项目 `authProfiles` + `defaultProfileId` + §4.1 匹配；Normalizer 补头 + `profileManaged`；**Run/调试同一解析器**；Prompt 要点；可选商城项目种子 JSON | **做**漏头自动补；**不做** Staging 分端硬拦、完整 Web 编辑器、401 回写 | A1：AI 可不写 Authorization，Staging/Run 仍有 `Bearer {{flow.token}}`；免登录节点不补头 |
| **P2 可见与门禁** | `qualitest` | 接口 `auth` Web 编辑；工具回传 `auth`/`headerHint`；Staging「按项目鉴权补全」标记；分端缺 token 检查（soft warning） | **已落地**；硬拦留给后续 | Staging 可见托管头；缺 token 分端提示 |
| **P3 匿名 path** | `qualitest` | 项目 `anonymousPathExact` / `anonymousPathPrefix`；上传命中 → `mode=none`；缺字段回填 demo 默认 | **已落地**（无 Web 编辑器；运行时仍只认接口 mode） | 白名单 path → `mode=none` |
| **P4 增强** | 按需 | 刷新本流托管头；鉴权失败走 AI 修复；OpenAPI 导入；Cookie Profile | **P4a 已落地**；鉴权失败不另做 401 启发式提案；OpenAPI/Cookie 仍非主路径 | 手册 §F 单独立项 |

### 7.3 当前开工顺序

1. ~~**先做 P0（插件）** → 合并 → 对 demo 重扫验 `mode`。~~ **P0 已落地**（插件 `anonymousAnnotations` + 上传 `auth`；质衡 `auth_config` 并入 baseline）。  
2. ~~项目鉴权配置种子~~ **已落地**：`uploadType=project` 的项目级上传且 `auth_config` 为空时写入双端 Bearer 模板，并回填接口 `authProfileId`。  
3. ~~P1 剩余（Normalizer / Run / 调试补头）~~ **已落地**：`AuthHeaderResolver`；造流补托管头；Run `mergeHeaders` 按当前项目配置刷新；调试 `http-forward` 带 `testProjectApiId` 时同解析器补缺失头；Prompt 鉴权要点。  
4. ~~P2 可见与门禁~~ **已落地**：`get_api_detail`/`search_apis` 回传 `auth`/`headerHint`；Staging Diff/聊天展示「按项目鉴权补全」；分端缺 `flow.token`/`flow.adminToken` soft warning；接口设计 Tab 可编辑 `mode`/`authProfileId`。  
5. ~~P3 匿名 path~~ **已落地**：`anonymousPath*` 进双端种子；导入时命中 → `mode=none`；老项目缺字段则回填默认。→ 再跑 T2.A1 / `smoke-S01`；重扫后验 `/login`、`/test-support/**` 为 `none`。  
6. ~~P4a 刷新本流托管头~~ **已落地**：顶栏「刷新鉴权头」→ 提案进 Staging → 确认保存。  
7. 鉴权失败：Run「AI 修复」对话改图（含 401 预填提示）；**已砍**专用 401 Staging 启发式提案。OpenAPI / Cookie Profile 仍按需。

回归基线（自 P1 补头起）：A1 流、`smoke-S01`；手册 §F。

**P4 验收要点**：改项目 `valueTemplate` → 顶栏「刷新鉴权头」→ Staging Diff 见新模板 → 确认保存；造漏头流 Run 出 401 →「AI 修复」预填鉴权提示 → 模型出 patch → Staging 确认前图不变。

---

## 8. 非目标

- 引擎隐式加签却不在节点/Staging 展示  
- `useRunSession` 冒充 Bearer  
- 项目 JSON 维护注解列表  
- Profile `match: ["/"]`  
- MCP 写配置/改图  
- 每个 API 复制完整头模板  

---

## 9. 已拍板

| 议题 | 决定 |
|------|------|
| Profile 位置 | 项目设置 |
| Token 变量 | `flow.token` / `flow.adminToken` |
| 匹配 | 最长 `pathPrefix`；否则 `defaultProfileId`；**禁止** match=`/` |
| 旧流 | Run/调试对 `profileManaged` **按当前 Profile 再解析** |
| 实施顺序 | **分阶段**；**先插件 P0，再平台补头 P1**；禁止一把梭 |
| 免登录注解 | **仅插件** `anonymousAnnotations[]`（P0） |
| 匿名 path | **P3**，不进 P0/P1 |
| `authInferenceMode` | **不引入**；靠 `mode` + 匹配算法（P1） |
| 调试台 | 与造流同解析器，进 **P1** |
| 补全可见 | Staging warning +「按项目鉴权补全」（P1 可 warning，P2 完善） |

---

## 10. 实现锚点

| 区域 | 位置 |
|------|------|
| 解析器 | `AuthHeaderResolver`（Normalizer / Run / 调试 forward 共用）；`ManagedAuthHeaderApplier`；`FlowAuthHeaderRefreshService` |
| 工具鉴权摘要 | `AuthHeaderHintSupport` → `get_api_detail` / `search_apis` |
| 分端 token 软提示 | `AuthTokenPresenceGate`（Normalizer / Staging confirm → warnings） |
| Prompt | `.../ai/flow-design-system-prompt.txt` |
| Normalizer | `FlowDesignPatchNormalizer` |
| 有效配置合并 | `TestProjectApiEffectiveConfigResolver` |
| 登录子流 | `ai/subflow-templates.json` |
| 插件注解 | Settings：`anonymousAnnotations[]` → `ApiAuthExtractor` → 上传 `auth` |
| 质衡落库 | `test_project_api.auth_config`（baseline）；导入 `ApiImportItem.auth` |
| 项目鉴权 | `test_project.auth_config`；种子双端模板含 `anonymousPath*`；导入 `matchesAnonymousPath` → `mode=none` |
| Web 薄编辑 | `ApiDesignTab`：`mode` / `authProfileId` |
| Staging 可见 | `AiStagingConfirmWarnings`；Diff headers 标签；warning 稳定码 `AUTH_HEADER_MANAGED` / `AUTH_TOKEN_MISSING` |
| 刷新托管头 | 画布顶栏「刷新鉴权头」→ `POST /project/testFlow/refreshAuthHeaders` → Staging |
| 鉴权失败修复 | Run 详情「AI 修复」（含 401 预填提示）→ AI patch → Staging |
| demo 双端 | `JwtAuthenticationTokenFilter` |
| 节点文档 | 落地后补 `test-flow-nodes.md` |

---

## 11. 一句话

**先插件标 `@Anonymous`→`mode=none`，再项目双端 Profile + Normalizer/Run 补托管头——分阶段落地，改配置旧流不丢，AI 漏头不再大面积 401。**
