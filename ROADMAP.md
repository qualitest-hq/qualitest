# Roadmap

> **用途**：公开后「接下来做什么」。  
> **远程**：[GitHub qualitest-hq](https://github.com/qualitest-hq)（主仓）· [Gitee qualitest-hq](https://gitee.com/qualitest-hq)（只读镜像）  
> 不绑定硬性日期；条目可能推迟、拆分或取消。

| 标记 | 含义 |
| ---- | ---- |
| 🚧 Now | 当前优先 |
| 📋 Next | 下一阶段 |
| 🔭 Later | 尚未排期 |
| 🔬 Exploring | 评估中，不承诺 |
| ✅ Done | 已落地（只记里程碑，不当 CHANGELOG） |

```mermaid
flowchart LR
  A["插件市场"] --> B["测试流工程化"]
  B --> C["引擎与安全硬伤"]
  C --> D["有人要再说"]
```

```text
插件市场  >  测试流工程化  >  跑流/密钥硬伤  >  其余看反馈
```

---

## 🚧 Now

（暂无；按反馈再排）

---

## 📋 Next

| ID | 任务 | 产出 |
| -- | ---- | ---- |
| 2.1 | 演示动图（可选） | README + 落地页能力区已插 `debug-200` / `canvas-glance`；AI Diff、MCP 短图待补 |

---

## ✅ Done

只记已落地的方向性条目；细节以代码为准。

| ID | 任务 | 说明 |
| -- | ---- | ---- |
| 1.1 | GHCR 镜像 | `ghcr.io/qualitest-hq/qualitest-app` / `qualitest-web`；workflow `ghcr.yml`；Compose / README / deploy 已附 pull |
| 1.2 | JetBrains 插件市场上架 | [Qualitest Helper](https://plugins.jetbrains.com/plugin/34434-qualitest-helper) 已通过审核 |
| 1.3 | 插件仓首个 tag `v1.0.0` | 已打 tag；GitHub Release 附 ZIP |
| 3.1 | 简易 HTML 报告 | Run 导出单文件 HTML；运行库「导出报告」下载；失败摘要与节点卡片 |
| 3.11 | 运行详情体验 | 页眉与失败摘要、时间线短摘要、点选高亮画布；场景跑入口收束到左栏「运行场景」 |
| 3.2 | 失败摘要深化 | 失败摘要挂本步关联 flow 键值（运行详情 + HTML 报告）；占位按合法路径识别 |

---

## 🔭 Later

不绑版本号。按 Issue / 真实踩坑再排；下面只是已知方向。

### 测试流工程化（不改画布 / 固定节点核心；可逐步做）

按投入产出大致顺序，遇真实需求再提排期。

| ID | 任务 | 说明 |
| -- | ---- | ---- |
| 3.3 | 测试流目录 / 分组 | 列表树形组织；不碰执行器 |
| 3.4 | 测试套件批量跑 | `flowId[]`（可选场景）串行或有限并行调 trigger |
| 3.5 | 单流 JSON 导入导出 | 基于已有 `graphJson`；便于备份、评审、Agent 读写 |
| 3.6 | 定时调度跑流 / 套件 | cron 调现有 trigger |
| 3.7 | 数据驱动批跑 | CSV / 数据集 → 多行 `flowSeed` 循环触发同一张图 |
| 3.8 | 全场景一键跑 | 对 `meta.scenarios` 批量 trigger |
| 3.9 | 接口变更一键对齐 | 在现有健康度 / 变更提示上，支持应用到节点 |
| 3.10 | 流水线跑流 | 有人要接 CI 再做：仓库脚本优先（curl 调现有 trigger → 轮询 → 失败 `exit ≠ 0`）；附带 JUnit XML 导出；不必先做正式 CLI |

刻意不做（会动核心或另开引擎）：循环节点、同流压测、独立 DB 节点。

### 硬伤（用户能直接感到痛）

| ID | 任务 | 说明 |
| -- | ---- | ---- |
| 4.1 | 测试流超时 / 熔断 | `flow.node.timeout`、`flow.run.timeout` |
| 4.2 | 子流循环引用 / 深度限制 | 防跑飞 |
| 4.3 | AI apiKey 加密与日志脱敏 | 生产可信底线 |
| 4.4 | AI 连接测试 + LLM 韧性 | 超时 / 重试 / 熔断 |

生产须换强密钥，并关 Druid 或加白名单。

### 有空或有人要再说

| ID | 任务 |
| -- | ---- |
| 5.1 | 节点处理器 Spring 注入（`Map<String, NodeHandler>`） |
| 5.2 | 流程合并重构 + 补测（`GraphMerger` 等） |
| 5.3 | 事务 / 索引 / 分页 |
| 5.4 | 测试覆盖补强（对着真实 Bug 补，不为覆盖率） |
| 5.5 | 画布独立快照节点 |
| 5.6 | Dev Container / Codespaces |
| 5.7 | 前端打进 JAR（可选单容器） |
| 5.8 | MapStruct 收敛 DTO |
| 5.9 | GitHub Discussions |
| 5.10 | Dependabot 常态化合入 |
| 5.11 | 双远程：GitHub 为主，Gitee 只读镜像 |
| 5.12 | 报告导出可选 AI 建议 | 仅 HTML、默认关；失败 Run 勾选后现算只读诊断（可能原因与排查步骤）；不写入 JUnit |
| — | Helm Chart 实测（骨架已有，谁用谁验） |

---

## 发布约定

| 仓库 | 策略 |
| ---- | ---- |
| `qualitest` / `qualitest-demo` | semver；**不发** GitHub Release / 根目录 CHANGELOG |
| `qualitest-intellij-plugin` | 独立 semver；Release 附 ZIP；`changeNotes` 在 Gradle；首发 `v1.0.0`；修 BUG 升 PATCH |

---

## 仓库

| 仓库 | 端口 | GitHub | Gitee（只读镜像） |
| ---- | ---- | ------ | ----------------- |
| qualitest | Compose Web **80**；本机 API **8800** / UI **5180** | [GitHub](https://github.com/qualitest-hq/qualitest) | [Gitee](https://gitee.com/qualitest-hq/qualitest) |
| qualitest-demo | API **8801**；Compose UI **5181** | [GitHub](https://github.com/qualitest-hq/qualitest-demo) | [Gitee](https://gitee.com/qualitest-hq/qualitest-demo) |
| qualitest-intellij-plugin | — | [GitHub](https://github.com/qualitest-hq/qualitest-intellij-plugin) | [Gitee](https://gitee.com/qualitest-hq/qualitest-intellij-plugin) |
