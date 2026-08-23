# 前端测试说明（qualitest-ui · apps/web）

编写约定以主仓 **[docs/测试编写约定.md](../../../docs/测试编写约定.md)** 为准（类/文件头三件套、每条 `it` 写「前提 + 期望」、中文标题、AAA、断言对行为）。本文只补运行方式与 flow 夹具对齐。

测试统一放在 `src/test/`（按领域分子目录），被测源码在 `src/utils/`、`src/views/` 等。

## 环境要求

- Node.js ≥ 22.13
- pnpm ≥ 11
- 推荐在 **`qualitest-ui` 根目录**执行 workspace 脚本；也可在 `apps/web` 下直接跑

## 运行方式

### 全量

```bash
cd qualitest-ui
pnpm test
```

### 按域过滤

```bash
pnpm test:flow
pnpm test:project
pnpm test:ai
pnpm test:utils
```

### 按文件名过滤（Vitest 模式匹配）

```bash
pnpm test graphValidate
pnpm test templateForm
pnpm test prefabApiWorkbench
pnpm test markdown
```

### 监听模式（保存后自动重跑）

```bash
pnpm test:watch
```

### 指定文件

```bash
pnpm test src/test/flow/compareRule.test.ts src/test/project/templateForm.test.ts
```

## 配置

Vitest 配置在 [`vite.config.js`](vite.config.js) + [`vitest.test-shared.js`](vitest.test-shared.js)，使用 **`test.projects`** 分环境：

| Project | 环境 | 范围 |
|---------|------|------|
| `node` | Node | `src/test/**`（不含 `utils/`） |
| `jsdom` | jsdom | `src/test/utils/**`（DOMPurify 等需真实 DOM） |

共用：`setupFiles: src/test/vitest-setup.ts`、`reporters: verbose`。

**所有测试文件必须放在 `src/test/` 下**（不要放在 `src/utils/` 等源码目录）。

## 目录结构

```
src/test/
  helpers/       公共 setup（如 Pinia 重置 pinia.ts）
  flow/          测试流 / staging / 图工具
  project/       项目模板、鉴权、API 设计补丁
    helpers/     project 域 builder（buildTemplateRow 等）
  ai/            AI 消息选择器等
  utils/         通用工具（如 markdown）
  flow/fixtures/ README 占位（共享 JSON 勿再放此处）

src/utils/flow/            纯函数内核
src/views/project/testFlow/  画布、适配器、stores、composables
```

## 约定摘要（细则见主文档）

- 框架：Vitest（`describe` / `it` / `expect`）。
- 文件头：测谁、边界、`pnpm test <片段>`。
- 每条 `it`：两行「前提 / 期望」；标题优先中文短句。
- Pinia 测试：优先 `withFreshPinia()` / `setupFreshPinia()`（见 `src/test/helpers/pinia.ts`）。
- project 域共享 builder：见 `src/test/project/helpers/buildTemplateRow.ts`。
- 共享 flow 夹具通过别名 `@flow-fixtures` 引用权威目录（见下），勿再复制到 `src/test/flow/fixtures/`。
- 测试环境为 Node，不启动浏览器；画布交互、正式 Run API 暂无自动化覆盖。

## 共享 flow fixture（单一来源）

权威目录（后端 JUnit 与前端 Vitest 共用）：

`qualitest-system/src/test/resources/flow/`

前端配置：

- Vite alias：`@flow-fixtures` → 上述目录（见 `vite.config.js`）
- TypeScript：`tsconfig.json` 的 `paths["@flow-fixtures/*"]`

```ts
import demoGraph from '@flow-fixtures/demo-graph.json';
```

只改权威目录即可；改该路径下文件时 CI 会同时触发 frontend Vitest。

跨端抽测示例：

```bash
# 后端（qualitest 目录）
mvn test -DskipTests=false -pl qualitest-system -am "-Dtest=CompareRuleEvaluatorTest,ExtractApplicatorTest,PlaceholderResolverTest"

# 前端
cd qualitest-ui
pnpm test compareRule extract placeholder
```

## 未覆盖范围（手动验证）

- 路径模拟（`simulatePaths` / `useFlowSimulate`）
- 场景运行（`useFlowScenarioRun` → Run API）
- 运行库回放（`usePlayback` / `runLibraryStore`）
- 画布拖拽、连线、属性面板
