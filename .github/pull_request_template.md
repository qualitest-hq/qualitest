## 改了什么

<!-- 一两句即可；大改动可列要点 -->

## 为什么

<!-- 关联动机 / Issue；无则写场景 -->

## 怎么验证

<!-- 自测路径：Compose / 本机 / 相关接口或页面；有 CI 看 Actions 是否绿 -->

- [ ] 已在本地验证相关路径（或说明为何无法本地验证）
- [ ] 若含库表变更：已新增 `db/migration/V{n}__….sql`（勿改已执行过的 migration / 勿只改根目录 dump）
- [ ] 行为有变时已更新 README / 对应 `docs/`（如适用）
- [ ] 未提交密钥、`.env`、本地绝对路径、`target/` / `node_modules/` 等

## 流热点（改鉴权 / HTTP data / validate Stage 时勾）

<!-- 未触及可整段删除 -->

- [ ] **BE 唯一源**：业务规则只在服务端（`FlowGraphValidateService` / Gates / `FlowDesignHttpNodeNormalizer` / `project.auth`）
- [ ] **FE 无镜像**：未在 `testFlow/utils` 新增 pathPrefix / AUTH 硬拦 / HTTP overrides 差分镜像
- [ ] **文档矩阵**：`docs/flow-contract-2.0.md` Stage×CODE 表与 `ai-staging.md` §5 已同步（若行为有变）
- [ ] **金样**：canvas/run issues 的 CODE+nodeId 集合与改前一致，或 PR 中显式列出批准的差异

## 关联

<!-- 例如：Closes #123 ；无则删本段 -->
