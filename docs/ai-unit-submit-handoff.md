# 造流单单元 submit_* — 回家待办 / 未收口

> 写于 2026-09-11。主功能已落地；本文只记**没改完 / 未拍板 / 实测发现**的事项，方便回家接着做。

---

## 1. 已完成（不必重做）

- 废弃整包 `submit_flow_design_patch`；分类型 `submit_*`（节点 7+7、边 2、删除 3、场景增改 2）
- 只读：`get_edge_detail` / `get_scenario_detail`
- Capture：成功累积 / 失败不覆盖 / 同 unitId 覆盖
- `normalizeUnit`：单单元宽松；工作图推进
- Agent：非「一 submit 就结束」；催 submit；步数耗尽可带回已接受 patch
- MCP：拒绝全部 `submit_*`
- 提示词模板：V1 baseline + 本地库已 UPDATE；**不要**再加 V2
- 相关单测曾跑过；核心代码注释已补（功能说明，无文档交叉引用）

**刻意不做（产品口径，勿回退）：**

- 无 `submit_set_active_scenario`（易误解成跑流）
- 无 AI 自动 Run / 自动保存
- AI 只出 Staging，人 ✓ + 保存才落盘

---

## 2. 回家优先：实测问题

### 2.1 `AUTH_TOKEN_MISSING` 只在保存出现（未改产品）

**现象：** Staging 逐项 ✓ 不拦；点**保存**才报：

`AUTH_TOKEN_MISSING: 图中使用了…（asset.adminAuth.token），但未找到该凭证来源…`

**原因（现状即设计）：**

| 阶段 | 是否跑 AuthTokenPresenceGate |
|------|------------------------------|
| AI `submit_*`（`normalizeUnit`） | 否（跨单元门禁跳过） |
| Staging ✓（`FlowDesignPatchConfirmService`） | 否（业务门禁后移） |
| 保存（`TestFlowServiceImpl`） | **是** |

确认服务注释写明：断言路径 / token 来源 / HTTP 必填 → **保存再验**。

**未拍板：要不要提前到画布？** 可选方向：

1. **维持现状**（分批确认时图常不完整，提前硬拦易卡死）
2. **全部 Staging 清完**（最后一项 ✓ 后）跑一次 token/必填门禁，错误挂画布黄条
3. **保存前预检 API**（点保存前前端先调校验接口，错误仍显示在画布/对话框，不进写库）
4. 确认时只 **warning** 不阻断，保存仍硬拦

改动入口：

- `FlowDesignPatchConfirmService`（确认）
- `TestFlowServiceImpl` 保存校验（已有）
- 前端 Staging 错误展示（`stagingConfirmErrorHints` / `stagingAuthHints`）

### 2.2 文案像「端绑错」

实测串：展示名像 **客户端** Header，路径却是 **`asset.adminAuth.token`**（管理端常用键）。

回家先查项目 `auth_config` / Profile：客户端 Profile 是否误绑 `adminAuth`。可能不是造流代码 bug，而是项目鉴权配置问题。

门禁实现：`AuthTokenPresenceGate`；文案：`AuthDesignWarningCodes.tokenMissing`。

### 2.3 造流稳定性（需再冒烟）

回家建议用短提示 + 明确 API id 再跑一轮：

1. 空画布搭登录子流（管理端 / 客户端各一）
2. 业务主流挂 Subflow
3. 看 AI 是否按单元多次 `submit_*`，失败能否当场重试
4. Staging ✓ → 保存 → Run

关注：`explainOnly`、步数耗尽、工具 Schema 变大导致的 token/步数压力。

---

## 3. 其它未收口 / 可选优化

| 项 | 说明 | 优先级 |
|----|------|--------|
| 切默认场景 | Staging 仍有 `scenario:activeScenarioId`；AI **无**对应工具。若以后要，优先 `setAsActive` 挂增/改，勿单独工具名像「跑流」 | 低 |
| tools.json token | 分类型后 Schema 更大，每步 Agent 都会带 `tools`；可考虑描述精简 / 按意图动态子集（未做） | 中（成本） |
| 历史 dump | `sql/qualitest_*.sql` 聊天记录里旧工具名未改，可不改 | 无 |
| 文档口径 | `ai-staging.md` 等已改；`project-summary` / faq 若仍写「AI submit 硬拦 AUTH_TOKEN」需与「保存才拦」对齐 | 低 |
| 单测缺口 | `normalizeUnit` 跳过 AuthToken 的专用用例、各 submit 工具端到端，可再补 | 中 |
| Git 提交 | 本轮改动是否已 commit：**未在对话中提交**；回家自行 `status`/`diff` 后提交 | 操作 |

---

## 4. 关键文件（改门禁 / UX 时）

```
qualitest-system/.../FlowDesignPatchNormalizer.java      # normalizeUnit
qualitest-system/.../FlowDesignPatchConfirmService.java  # Staging ✓
qualitest-system/.../TestFlowServiceImpl.java            # 保存门禁
qualitest-system/.../AuthTokenPresenceGate.java
qualitest-system/.../flow-design-tools.json
qualitest-system/.../flow-design-system-prompt.txt
qualitest-ui/.../useAiStagingConfirm.ts
qualitest-ui/.../stagingAuthHints.ts
qualitest-ui/.../stagingConfirmErrorHints.ts
```

---

## 5. 回家建议顺序

1. 查项目 Profile：客户端是否误用 `adminAuth.token`
2. 决定 `AUTH_TOKEN_MISSING` 展示时机（上表 2.1 四选一）
3. 冒烟造流 + Staging + 保存
4. 按需补测 / 精简 tools 描述 / commit
