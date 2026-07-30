# 前端测试说明（qualitest-ui · apps/web）

编写约定以主仓 **[docs/测试编写约定.md](../../../docs/测试编写约定.md)** 为准（类/文件头三件套、每条 `it` 写「前提 + 期望」、中文标题、AAA、断言对行为）。本文只补运行方式与 flow 夹具对齐。

测试主要在 `src/test/`（按领域分子目录），被测源码多在 `src/utils/`、`src/views/project/testFlow/`。

## 环境要求

- Node.js（与项目 Vite 版本匹配）
- Yarn 或 npm
- 所有命令在 **`qualitest-ui/apps/web`** 目录执行（或在 `qualitest-ui` 根目录用 workspace 脚本）

## 运行方式

### 全量

```bash
cd qualitest-ui/apps/web
yarn test
```

### 按文件名过滤（Vitest 模式匹配）

```bash
yarn test graphValidate
yarn test placeholder
yarn test graphAdapter
yarn test compareRule
yarn test extract
yarn test snowflakeId
```

### 监听模式（保存后自动重跑）

```bash
yarn test:watch
```

### 指定文件

```bash
yarn test src/test/flow/compareRule.test.ts src/test/flow/extract.test.ts
```

## 配置

Vitest 配置在 `vite.config.js`：

- `environment: 'node'`
- `include: ['src/test/**/*.test.ts']`
- `reporters: ['verbose']`

当前仅 `src/test/**/*.test.ts` 会被收集，组件级 E2E 不在此范围。

## 目录结构

```
src/test/
  flow/          测试流 / staging / 图工具
  project/       项目 / API 设计补丁等
  ai/            AI 消息选择器等
  flow/fixtures/ README 占位（共享 JSON 勿再放此处）

src/utils/flow/            纯函数内核
src/views/project/testFlow/  画布、适配器、stores、composables
```

## 约定摘要（细则见主文档）

- 框架：Vitest（`describe` / `it` / `expect`）。
- 文件头：测谁、边界、`yarn test <片段>`。
- 每条 `it`：两行「前提 / 期望」；标题优先中文短句。
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
cd qualitest-ui/apps/web
yarn test compareRule extract placeholder
```

## 未覆盖范围（手动验证）

- 路径模拟（`simulatePaths` / `useFlowSimulate`）
- 场景运行（`useFlowScenarioRun` → Run API）
- 运行库回放（`usePlayback` / `runLibraryStore`）
- 画布拖拽、连线、属性面板
