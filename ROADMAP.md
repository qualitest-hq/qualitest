# Roadmap

> **用途**：公开后「接下来做什么」。  
> **远程**：[qualitest-hq](https://github.com/qualitest-hq)  
> 不绑定硬性日期；条目可能推迟、拆分或取消。

| 标记 | 含义 |
| ---- | ---- |
| 🚧 Now | 当前优先 |
| 📋 Next | 下一阶段 |
| 🔭 Later | 尚未排期 |
| 🔬 Exploring | 评估中，不承诺 |

```mermaid
flowchart LR
  A["GHCR"] --> B["插件市场"]
  B --> C["引擎与安全硬伤"]
  C --> D["有人要再说"]
```

```text
GHCR  >  动图（可选）  >  插件市场  >  跑流/密钥硬伤  >  其余看反馈
```

---

## 🚧 Now

| ID | 任务 | 产出 |
| -- | ---- | ---- |
| 1.1 | GHCR 镜像 | `ghcr.io/qualitest-hq/qualitest`；README / [`deploy.md`](./docs/deploy.md) 附 pull |
| 1.2 | JetBrains 插件市场上架 | 审核通过；README 链到市场 |
| 1.3 | 插件仓首个 tag `v1.0.0` | 触发 Release；附 ZIP |

---

## 📋 Next

| ID | 任务 | 产出 |
| -- | ---- | ---- |
| 2.1 | 演示动图（可选） | README / 落地页目前为文字步骤；有空再补 GIF 亦可 |

---

## 🔭 Later

不绑版本号。按 Issue / 真实踩坑再排；下面只是已知方向。

### 硬伤（用户能直接感到痛）

| ID | 任务 | 说明 |
| -- | ---- | ---- |
| 3.1 | 测试流超时 / 熔断 | `flow.node.timeout`、`flow.run.timeout` |
| 3.2 | 子流循环引用 / 深度限制 | 防跑飞 |
| 3.3 | AI apiKey 加密与日志脱敏 | 生产可信底线 |
| 3.4 | AI 连接测试 + LLM 韧性 | 超时 / 重试 / 熔断 |

生产须换强密钥，并关 Druid 或加白名单。

### 有空或有人要再说

| ID | 任务 |
| -- | ---- |
| 4.1 | 节点处理器 Spring 注入（`Map<String, NodeHandler>`） |
| 4.2 | 流程合并重构 + 补测（`GraphMerger` 等） |
| 4.3 | 事务 / 索引 / 分页 |
| 4.4 | 测试覆盖补强（对着真实 Bug 补，不为覆盖率） |
| 4.5 | 画布独立快照节点 |
| 4.6 | Dev Container / Codespaces |
| 4.7 | 前端打进 JAR（可选单容器） |
| 4.8 | MapStruct 收敛 DTO |
| 4.9 | GitHub Discussions |
| 4.10 | Dependabot 常态化合入 |
| 4.11 | 双远程：GitHub 为主，Gitee 只读镜像 |
| — | Helm Chart 实测（骨架已有，谁用谁验） |

---

## 发布约定

| 仓库 | 策略 |
| ---- | ---- |
| `qualitest` / `qualitest-demo` | semver；**不发** GitHub Release / 根目录 CHANGELOG |
| `qualitest-intellij-plugin` | 独立 semver；Release 附 ZIP；`changeNotes` 在 Gradle；首发 `v1.0.0`；修 BUG 升 PATCH |

---

## 仓库

| 仓库 | 端口 |
| ---- | ---- |
| [qualitest](https://github.com/qualitest-hq/qualitest) | Compose Web **80**；本机 API **8800** / UI **5180** |
| [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) | API **8801**；Compose UI **5181** |
| [qualitest-intellij-plugin](https://github.com/qualitest-hq/qualitest-intellij-plugin) | — |
