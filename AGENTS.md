# Agent 说明

本仓库面向人与各类 AI 助手的约定以 **`docs/`** 与根目录社区文件为准，**不依赖**提交 `.cursor/` 等编辑器私有配置。

## 写 / 改测试时

先阅读并遵循：**[docs/testing-conventions.md](./docs/testing-conventions.md)**  
（JUnit / Vitest；可读命名、AAA、断言对行为；生成代码必审。）

## 其它常用入口

| 文档 | 用途 |
|------|------|
| [README.md](./README.md) | 快速开始与模块说明（含 English Summary） |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | 贡献方式 |
| [SECURITY.md](./SECURITY.md) | 安全披露 |
| [docs/deploy.md](./docs/deploy.md) | 部署 / Compose / Flyway 运维约定 |
| [docs/mcp.md](./docs/mcp.md) | MCP 配置与示例提问（以 Cursor 为例） |
| [docs/project-summary.md](./docs/project-summary.md) | 产品概念 / 鉴权 |
| [docs/ai-staging.md](./docs/ai-staging.md) | AI Staging / Diff |
| [docs/flow-variables-and-values.md](./docs/flow-variables-and-values.md) | 跑流变量与 HTTP 测值 |
| [docs/assets.md](./docs/assets.md) | 素材库 |
| [docs/project-template.md](./docs/project-template.md) | 项目模板（勾选 / 导入导出 / 另存） |
| [docs/faq.md](./docs/faq.md) | 日常使用 FAQ（冒烟/靶场见测试手册 §F） |

回复与用户可见说明默认使用中文。
