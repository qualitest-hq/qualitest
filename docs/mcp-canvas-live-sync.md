# MCP / 外部变更 → Web 画布实时同步

> **用途**：描述 MCP（及 Web 全自动、人手保存等）写库后，已打开的 Web 画布及相关侧栏如何跟上，并给出高亮/动画、冲突锁与性能方案。  
> **目标体验**：在质衡 Web 打开同一测试流时，能「看着」Cursor / Agent 经 MCP 造流、改图、改素材、跑流，无需手动整页刷新。  
> 相关：[mcp.md](./mcp.md) · [ai-staging.md](./ai-staging.md)

---

## 1. 背景与结论

### 1.1 现状

| 路径 | 写库时机 | Web 如何感知 |
| ---- | -------- | ------------ |
| Web AI **半自动** | Staging ✓ → 人手保存 | 本地已是提案图，保存后一致 |
| Web AI **全自动** | 隐式落盘后 SSE `graphCommitted` | 清 Staging + `loadFlow` |
| **MCP 全自动写流** | `submit_*` / upsert / `run_test_flow` 成功即写库 | 目前靠指引「写完后刷新画布」 |

MCP 写库链路已通；缺的是 **打开中的 Web 会话订阅外部落盘**，不是再造「画布专用 MCP 工具」。

### 1.2 结论

1. **通知与拉数分离**：通道（SSE / WS / 轮询）只发「发生了什么」；画布数据仍用现有 REST（`loadFlow` 等）拉取，或事件可选携带 patch/整图以减一次往返。
2. **按变更域精准同步**：改图只同步图；改素材/鉴权/环境只同步对应库；跑流只接 Run 现场——**禁止**把外部一次小写入做成整页 `initFlow`。
3. **语义对齐** Web 全自动的 `graphCommitted` / `runStarted`，并扩展素材、鉴权等事件，统一总线、统一脏稿与锁策略。
4. **性能**：debounce、校验单例化、刷新并行、大图增量 apply、入场动画均纳入本方案（见 §5、§6、§9），不再拆「以后再说」的独立产品文档为前提。

---

## 2. 目标与边界

### 2.1 目标

1. Web 打开某 `testFlowId`（及同项目上下文）时，MCP/其它端对该流或项目资源的成功写入能驱动 UI 更新。
2. 改图后能看出新增/变更节点（高亮；可叠加入场动画）。
3. 改素材变量、鉴权配置、跑流结果时，对应面板/列表/高亮也能跟上，而不是只有画布或只有整页刷新才一致。
4. 本地有未保存脏稿时不静默覆盖；双端同时写时有租约锁，避免无声后写覆盖。
5. 同步体感轻于整页刷新；连续 MCP `submit_*` 不造成请求风暴。

### 2.2 边界

- **不要求** Agent 多调「推画布」工具；由服务端在既有落盘成功路径上发事件。
- **不覆盖**未打开该流/该项目的页面做全局预览墙（列表页最多角标/可选，非必须）。
- 推送面向 **已登录 Web 会话**；MCP Token 侧不连画布通道。

---

## 3. 变更域与同步范围

一次外部写入只触发**相关域**的刷新，互不捆绑。

| 变更域 | 典型来源 | 事件（建议） | Web 打开画布时的动作 |
| ------ | -------- | ------------ | -------------------- |
| **画布图** | MCP/Web `submit_*` 落盘、人手保存、Web 全自动隐式落盘 | `graphCommitted` | 脏稿门禁通过后：轻量同步图（§4）；差分高亮 / 动画（§6） |
| **素材 / 参数变量** | MCP `upsert_asset_variables`、Web 确认写入素材 | `assetVariablesChanged` | 刷新项目素材/变量缓存；若属性面板正编辑同 key，提示冲突，不静默覆盖未保存表单 |
| **鉴权配置** | MCP `upsert_auth_profile`、Web 写入鉴权 | `authConfigChanged` | 刷新 `loadProjectAuthConfig`；HTTP 节点鉴权展示跟新；本地未保存的鉴权表单同脏稿规则 |
| **环境列表** | 项目环境增删改（若 MCP/API 可写） | `projectEnvsChanged` | 刷新 `loadProjectEnvs`；运行配置里当前选中 env 若被删则提示 |
| **Run / 运行库** | MCP/Web `run_test_flow` 触发、Run 状态推进 | `runStarted` + 既有 Run 轮询/详情 | `watchRunLive`（对齐全自动）；Run 列表增量插入或 `loadRuns`；**不**因此重拉整图，除非同时有 `graphCommitted` |
| **流元数据** | `create_flow`、改流名称等 | `flowMetaChanged` / `flowCreated` | 若当前不在新流：列表侧可提示；若已打开该流：更新标题等；新建流不自动跳转除非产品约定 |

**明确禁止**：仅因 `graphCommitted` 就重拉 env、runs、鉴权、双预检；仅因 `runStarted` 就 `loadFlow`；仅因素材变更就整图重拉。

整页进入 / 浏览器刷新仍走 `initFlow`（拉齐全部上下文），与「外部增量同步」是两条路径（§5.1）。

---

## 4. 画布图同步

### 4.1 通知 ≠ 整图重拉

```text
写库成功
  → 发布 graphCommitted（可走 SSE/WS；或轮询发现 updateTime）
  → 前端 debounce
  → 脏稿 / 锁检查
  → 应用图：默认 loadFlow(GET)；或事件已带 graphJson/patch 则本地 apply
  → 差分高亮（+ 可选入场动画）
```

SSE/WS **默认不传整图**（避免通道膨胀）；需要减延迟或减闪烁时，事件可带：

- `graphJson`：整图内联，前端跳过 GET；
- 或 `units` / `changedNodeIds` + 可选节点片段：增量 apply，失败则回退 `loadFlow`。

与 Web 全自动一致：至少保证 `type` + `testFlowId`；扩展字段向后兼容。

### 4.2 默认策略与增量策略

| 策略 | 何时用 | 行为 |
| ---- | ------ | ---- |
| **整图 GET 重拉** | 默认；事件无正文；增量 apply 失败 | 现有 `loadFlow` + 既有二段灌边 |
| **事件内联整图** | 图不大、想省一次 RTT | 校验后 `applyAdaptedGraphToStore`，仍走灌边收尾 |
| **增量 patch apply** | 连续小改、大图、要减弱闪烁 | 按单元合并进当前 store；校验失败或版本落后 → 整图兜底 |

无论哪种，同步入口必须是轻量 API（如 `syncFlowFromServer` / `applyExternalGraph`），**禁止**调用 `initFlow`。

### 4.3 差分高亮与入场动画

1. 同步前保留上一版节点/边 id 与关键字段指纹。
2. 应用新图后对比：
   - **新增**节点 → 主高亮 + 可选入场（短 fade / 缩放，避免位移乱跑）；
   - **变更**节点 → 次高亮或描边脉冲；
   - **新增边** → 弱高亮或描边；
   - 删除 → 图上消失即可。
3. 高亮 3～8s 或点击空白清除；状态与 `runHighlightNodeId`（Run 步进）**分字段**，避免互盖。
4. Run 进行中：Run 步进高亮优先；外部改图可 Toast + 延迟合并同步，或暂停自动应用图直至 Run 结束（实现选定一种并全文一致）。
5. 连续 debounce 合并后的一次同步，对窗口内所有新增 id 做一次高亮/动画，避免每个 submit 播一遍。

### 4.4 脏稿

| 画布状态 | 收到 `graphCommitted` |
| -------- | --------------------- |
| 干净（无 dirty、无未决 Staging） | 自动应用图 + 高亮/动画 |
| 有 Staging / 本地 dirty | 不覆盖；条幅「外部已更新」→「放弃本地并拉取」/「稍后」 |
| 刚处理过同版本 `updateTime` / revision | 去重，不重复灌图 |

素材面板、鉴权表单各自维护脏标记，只影响对应域事件（§3），不阻塞无关域的自动同步。

### 4.5 合并与节流

- 服务端：每次相关写库成功都可发事件。
- 前端：按 `(testFlowId, eventType)` debounce（图建议 300～800ms；素材/鉴权可略长）。
- 载荷带 `updateTime` 或单调 `graphRevision`；只应用不旧于已展示版本的数据。

---

## 5. 卡顿成因与解决方案

整图同步 **≠** 浏览器整页刷新。

### 5.1 两条路径

| | **外部增量同步** | **整页刷新 / 首次进入（`initFlow`）** |
| -- | ---------------- | -------------------------------------- |
| 做什么 | 按 §3 只刷新变更域 | `loadFlow` → 鉴权 → 项目名 → 环境 → Run 列表 → `runPreview` |
| 主要耗时 | 单域 GET/apply + 有限重绘 | 串行接口瀑布 + 灌图 + 双预检 |
| 预检 | 默认不跑；可选在图同步后 ≥1s debounce 补一次校验条 | 需要 |

### 5.2 图同步防卡

| 风险 | 对策 |
| ---- | ---- |
| 连续 submit 多次 `loadFlow` | 同一流 debounce，窗口内合并为一次应用 |
| 误走 `initFlow` | 独立同步入口；不拉 env/runs/鉴权；不默认双预检 |
| 灌图触发预检 watch | `setSuspended`；结束后不强制 `runPreview` |
| 节点与边同写丢边 | 继续 `pendingEdges` / `ensureEdgesHydrated` |
| 大图闪烁 / 主线程尖峰 | 增量 patch apply + 整图兜底（§4.2）；事件可带正文减 RTT |

**验收（性能）**：

- [ ] 连续 10 次 MCP `submit_*`，打开中画布 debounce 窗口内至多 1～2 次拉图/应用图。
- [ ] 纯 `graphCommitted` 同步不出现 env / runs / apiHealth / savePrecheck 必发请求。
- [ ] 素材-only / 鉴权-only 事件不触发 `loadFlow`。

### 5.3 按节点重复全图校验

现状：`BaseFlowNode` 与校验条均可 `useFlowValidation()`，内部对整图 `buildCanvasPersistGraph` + `validateGraphJson`，近似 ×N。

**方案（纳入本能力）**：

1. 校验改为页面级单例 / 共享 computed：只在一处算 `issues` / `issueNodeIds`；节点只读 `issueNodeIds.has(id)`。
2. 序列化结果可在 nodes/edges 引用未变时复用，减少深拷贝。
3. 与同步、刷新共用，不单独做成「可选优化」。

### 5.4 整页 `initFlow` 优化（同属画布性能）

1. `loadFlow` 先出图；`loadProjectAuthConfig` / `loadProjectName` / `loadProjectEnvs` / `loadRuns` **并行**。
2. `runPreview` 可稍后，避免白等预检。
3. 预检体过大时，可改为服务端按 `testFlowId` 读库或传摘要（与增量同步通道独立）。

### 5.5 实现锚点

- `useExternalGraphSync`（名可议）：订阅 → debounce → 脏稿/锁 → 按事件类型分发（图 / 素材 / 鉴权 / Run…）。
- `useFlowValidation` + `BaseFlowNode`：单例化。
- `FlowCanvasView.initFlow`：并行与先出图。
- `setSuspended`：灌图门闩。

---

## 6. 推送通道

画布页在无 AI 设计 SSE 时也要能收事件。

| 方案 | 优点 | 代价 |
| ---- | ---- | ---- |
| **按流/项目订阅 SSE 或 WS** | 实时、事件类型完整（图/素材/鉴权/Run） | 端点、重连、鉴权 |
| **短轮询**元数据（`updateTime` / revision / 资源版本） | 落地快 | 延迟；多域需多个版本戳或聚合接口 |
| **Redis Pub/Sub → 推到已连接会话** | 多实例 | 会话注册 |

**推荐形态**：产品化以 **SSE 或 WS 长订阅** 为主（可 Redis 扇出）；轮询可作为无 WS 环境的降级。

事件体示例：

```json
{
  "type": "graphCommitted",
  "testFlowId": "123",
  "source": "mcp",
  "updateTime": "2026-09-17T12:00:00Z",
  "graphRevision": 42,
  "changedNodeIds": ["n1", "n2"]
}
```

```json
{
  "type": "assetVariablesChanged",
  "testProjectId": "10",
  "source": "mcp",
  "keys": ["token", "baseUrl"]
}
```

```json
{
  "type": "runStarted",
  "testFlowId": "123",
  "runId": "999",
  "source": "mcp"
}
```

- `source`：`mcp` | `web-autopilot` | `web-save` | `web-ui` 等。
- 发布点：MCP commit/upsert/run 成功、Web 全自动隐式落盘、人手保存、素材/鉴权写入成功等，统一 `FlowExternalChangeEvent`（名可议）总线，避免各端私有协议。

---

## 7. 编辑租约锁（与同步正交但一起做）

锁解决双写覆盖；同步解决「看见更新」。两者都要。

### 7.1 规则

- **只锁写路径**，打开浏览不占锁。
- **先到先得**（Redis `SET NX` + TTL + 心跳）：Web 正在保存 / Staging 确认写库 / AI 全自动写库 / MCP 写工具会话，任一持锁则另一方写失败并返回明确错误（MCP 工具回执 / Web Toast）。
- 粒度：默认 **`testFlowId` 级**（改图）；素材/鉴权可用 **`testProjectId` + 资源域** 级锁，避免改变量堵住改图（或文档约定项目级粗锁，实现时选一种并写清）。
- 租约：TTL（如 30～60s）+ 持有方心跳；Tab 关闭 / Agent 结束须释放；超时视为可抢。
- 持锁方仍发 `graphCommitted` 等事件；他端若只读可同步画面；他端若有脏稿仍走 §4.4，不能因「看见了」就本地可写覆盖库（写前仍要抢锁）。

### 7.2 与脏稿

未持锁的 Web 若本地 dirty，外部事件只提示拉取；用户「放弃本地并拉取」后可继续看，若要再保存须重新抢锁。

---

## 8. 前端行为细则

打开测试流画布且上下文为该项目 / `testFlowId` 时：

1. 建立订阅（或降级轮询）；按需申请写锁心跳（仅进入写操作时）。
2. 收事件 → 按 `type` 分发（§3）。
3. 图：脏稿/锁检查 → debounce → 轻量应用图 → 高亮/动画。
4. 素材/鉴权/环境：刷新对应 store；表单脏则提示。
5. Run：`runStarted` → `watchRunLive`；列表增量更新。
6. 轻提示按 `source` 区分文案（可选）。
7. 离开页 / 切换流：取消订阅、停心跳、清高亮、释锁。

---

## 9. 后端落点

1. **发布**：各写成功路径 → 领域事件（图 / 素材 / 鉴权 / 环境 / Run / 流元数据）。
2. **投递**：订阅连接推送；多实例经 Redis；降级则 bump 版本戳供轮询。
3. **鉴权**：仅项目成员可订阅；与现有查询权限一致。
4. **锁**：写工具与 Web 保存/全自动落盘入口抢锁；失败不写库。
5. MCP 回执可带 `committed` / `lockHeldBy` / `hint`，便于 Agent 换流或等待。

---

## 10. 验收标准

**图**

- [ ] MCP 连续 `submit_*` 改打开中的流：无需手动刷新，结构跟上，新节点有高亮（动画若开启则可见且不狂闪）。
- [ ] 有本地 dirty / Staging：不覆盖，可「放弃本地并拉取」。
- [ ] 打开流 B 时，流 A 的图事件不误应用到 B。
- [ ] Web 全自动 `graphCommitted` 回归通过。

**其它域**

- [ ] MCP upsert 素材后，打开中项目的变量/素材视图更新（或脏表单提示），且不强制 `loadFlow`。
- [ ] MCP upsert 鉴权后，鉴权配置与节点鉴权展示更新，不强制整页刷新。
- [ ] MCP `run_test_flow` 后出现 Run 高亮/列表更新，不因此无故整图重拉。

**锁与性能**

- [ ] Web 持锁保存中 MCP 写同流失败且有明确错误；反之亦然。
- [ ] 浏览态不占锁；TTL 到期可再抢。
- [ ] §5.2 性能验收通过；校验无按节点 ×N 整图重算。

**指引**

- [ ] [mcp.md](./mcp.md) / Skill：改为打开对应测试流即可看到同步；说明脏稿与锁冲突时的提示。
- [ ] [ai-staging.md](./ai-staging.md)：MCP 与 Web 全自动共用外部变更同步语义。

---

## 11. 一句话

**按变更域发事件、按域轻量同步；图用 debounce 后的 loadFlow 或增量 apply，素材/鉴权/Run 各刷各的；校验单例化与租约锁一起做——通知走 SSE/WS，整图重拉仍是可选的 GET/apply，不是把整页刷新再跑一遍。**
