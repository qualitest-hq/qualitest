# Changelog

本文件记录质衡（Qualitest）主仓的重要变更，格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

发 GitHub Release 时，可将对应版本章节复制为 Release Note（再补 tag / 附件说明即可）。

## [Unreleased]

### Added

### Changed

### Fixed

### Security

## [1.0.0] - Unreleased

开源首发候选。打 `v1.0.0` tag 时将本条日期改为发布日，并清空上方 `[Unreleased]` 中已并入条目。

### Added

- 企业级接口测试与质量平台：项目隔离、接口资产、多环境调试台（浏览器直连 / 服务端代理）
- 测试流画布编排：HTTP、断言、条件分支、脚本、子流等节点；内置登录 / OAuth 等子流模板
- AI 辅助设计测试流：自然语言建议 → **Diff 预览后再合并**（不静默改库）
- MCP 只读接入（Cursor 等）：查接口、列举/勘察测试流、读 Run 失败现场等（[`docs/mcp.md`](./docs/mcp.md)）
- 被测数据快照 / 还原重试：节点 `snapshotBefore` → 失败暂停后可选还原重试（生产默认关闭；同环境请串行）
- Docker Compose 全栈一键体验：MySQL + Redis + 后端 + Nginx（[`docs/deploy.md`](./docs/deploy.md)、`scripts/quick-start.*`）
- 前端并入主仓：`qualitest-ui/`（Web / Electron）
- 社区与协议：Apache-2.0、`SECURITY.md`、`CONTRIBUTING.md`；配置口令外置（`.env.example`）

### Companion

周边仓库（独立版本，不随本文件 semver）：

- [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) — 商城接口靶场（含 `/test-support`）
- [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin) — IDEA 接口同步（含项目级上传与分组注释过滤）

### Security

- `dev` / Compose 默认口令仅供本地体验；生产须更换 `TOKEN_SECRET`、数据库与 Redis 口令
- `docker` profile 关闭 Druid 控制台；切勿将个人 `.env` / 真实云 Key 提交进仓

[Unreleased]: https://github.com/qualitest-hq/qualitest/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/qualitest-hq/qualitest/releases/tag/v1.0.0
