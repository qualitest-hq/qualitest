# 安全策略 · Security Policy

质衡（Qualitest）欢迎负责任的安全披露。请勿在公开 Issue 中贴出可利用细节或密钥。

## 支持范围

| 版本 | 状态 |
|------|------|
| `main` / 当前稳定线 | 接受安全修复 |
| 更旧的提交 / 分支 | 视影响面决定是否回溯 |

覆盖本仓库（后端 + `qualitest-ui/`）。靶场 [`qualitest-demo`](https://github.com/qualitest-hq/qualitest-demo)、IDEA 插件 [`qualitest-intellij-plugin`](https://github.com/qualitest-hq/qualitest-intellij-plugin) 的同类问题请在**对应仓库**的 `SECURITY.md` 报告，或一并邮件说明。

## 如何报告

1. 优先使用 GitHub **[Private vulnerability reporting](https://github.com/qualitest-hq/qualitest/security/advisories/new)**（Security → Advisories → Report a vulnerability）。  
2. 若暂不可用，请发邮件至 **[38680050@qq.com](mailto:38680050@qq.com)**，主题标明 `[SECURITY]`。  
3. 请尽量包含：影响版本 / commit、复现步骤、预期与实际行为、是否已在公网被利用。

我们会在精力允许时尽快确认收到（通常若干个工作日内，忙时可能更久），并在修复与披露节奏上与报告人协调（通常先私下修复，再视情况发 Advisory）。请勿期待即时响应。

## 生产部署必读

仓库内 `dev` / Compose 默认值**仅便于本地体验**，**不得**直接用于公网或生产：

- 更换强随机 `TOKEN_SECRET`（见 [`.env.example`](./.env.example)）
- 更换数据库、Redis、默认管理员口令（种子账号如 `admin` / `admin123`）
- 使用 `prod` 或 `docker` profile（`docker` 已关闭 Druid 控制台；上传目录默认 `/data/upload`）
- 勿将真实云 API Key、个人 `.env`、`application-local.yml` 提交进 Git
- MCP Project Token、LLM Key 等按最小权限发放，泄露后立即轮换

更多部署说明见 [`docs/deploy.md`](./docs/deploy.md)。

## 范围外（一般不视为漏洞）

- 仅本地 / 演示环境可复现、依赖默认弱口令且文档已警告的配置问题  
- 社会工程、物理访问、拒绝服务耗尽类报告（除非有明确可修复的服务端缺陷）  
- 依赖组件的已知 CVE：请优先跟上游；本仓会通过 Dependabot / 发版跟进

感谢帮助质衡变得更安全。
