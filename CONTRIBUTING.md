# 贡献指南 · Contributing

感谢关注质衡（Qualitest）。本仓库包含 **后端多模块** 与前端目录 **`qualitest-ui/`**。相关仓：

| 仓库 | 用途 |
|------|------|
| [`qualitest`](https://github.com/qualitest-hq/qualitest)（本仓） | 主平台 + Web |
| [`qualitest-demo`](https://github.com/qualitest-hq/qualitest-demo) | 接口靶场 |
| [`qualitest-intellij-plugin`](https://github.com/qualitest-hq/qualitest-intellij-plugin) | IDEA 插件 |

安全漏洞请走 [`SECURITY.md`](./SECURITY.md)，不要开公开 Issue。

## 欢迎什么

Bug、文档笔误、小体验改进、大功能设想、甚至「有点怪但可能有用」的点子，都可以提。

不必写得很正式：说清楚你遇到了什么、或想做什么就行。半成品想法也欢迎——先开 Issue 聊聊，不一定立刻要有 PR。

## 提 Issue（可选格式，能写多少写多少）

GitHub 上「New issue」可选表单（Bug / 想法 / 提问）；空白 Issue 也开放。安全漏洞请走 [`SECURITY.md`](./SECURITY.md)。

- **Bug**：怎么复现、期望 vs 实际；有日志/截图更好  
- **想法 / 功能**：场景或动机即可；已有变通方案也可以顺手提一句  
- **提问**：搜一下已有 Issue / README；部署问题可先看 [`docs/deploy.md`](./docs/deploy.md)

## 开发环境（摘要）

- JDK 17+、Maven 3+、MySQL 8+、Redis 3+、Node 22+、pnpm 11.x  
- 复制 [`.env.example`](./.env.example) 为 `.env`（勿提交），或改 `application-dev.yml`  
- 后端：`mvn clean package` 后按根目录 `qualitest.bat` / `qualitest.sh` 启动  
- 前端：`cd qualitest-ui && pnpm install && pnpm dev`  
- 一键依赖 / 全栈：见 README「5 分钟快速开始」与 `docker compose`

本地联调靶场时，环境 `baseUrl` 一般为 `http://localhost:8801`。

## Pull Request

小修小补直接 PR 就好；大改动建议先 Issue 对齐一下方向。

1. 从最新 `main` 拉分支即可（命名随意，`fix/...` / `feat/...` 都行）。  
2. 尽量一次 PR 只做一件事；大范围纯格式化请单独开。  
3. 自测相关路径：后端可 `mvn -pl qualitest-system -am package`；前端在 `qualitest-ui` 下 `pnpm test`。有 CI 时看 GitHub Actions 是否绿。  
4. PR 描述会带简易模板（改了啥 / 怎么验证 / 清单）；能关联 Issue 更好。  
5. **请勿**提交密钥、`.env`、本地绝对路径配置、`target/` / `node_modules/` 等构建产物。

维护者会做轻量 Review。Issue / PR 在业余时间处理，**不承诺固定 SLA**；紧急安全问题请走 [`SECURITY.md`](./SECURITY.md)。

## 代码与文档（宽松约定）

跟着现有风格走即可：后端是 Spring Boot 多模块，前端是 Vue 3 + Element Plus。用户可见文案以中文为主。行为有变时，顺手改一下 README 或对应 `docs/` 就行——不必为贡献专门写长文档。  
新增/大改单测时，请参考 [`docs/testing-conventions.md`](./docs/testing-conventions.md)（测试也是给人看的）。各类 AI 助手可先看根目录 [`AGENTS.md`](./AGENTS.md)。

## 许可证

贡献默认按 **[Apache License 2.0](./LICENSE)** 授权给本项目。提交即表示你有权按该协议贡献，且同意同样授权。  
可选：在 commit 中加入 `Signed-off-by:`（DCO）。上游致谢见 [`NOTICE`](./NOTICE)。

有想法就提，谢谢！
