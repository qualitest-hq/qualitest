# 前端测试说明（qualitest-ui · flow 模块）

测试流相关单元测试在 `src/test/flow/`，被测源码主要在 `src/utils/flow/` 与 `src/views/project/testFlow/`。

## 环境要求

- Node.js（与项目 Vite 版本匹配）
- Yarn 或 npm
- 所有命令在 **`qualitest-ui/apps/web`** 目录执行

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
src/test/flow/
  graphValidate.test.ts    图 JSON 结构校验
  graphAdapter.test.ts     VueFlow ↔ GraphJson 互转
  placeholder.test.ts      占位符解析
  compareRule.test.ts      断言规则求值
  extract.test.ts          HTTP 响应提取
  snowflakeId.test.ts      节点 id 生成
  fixtures/                夹具 JSON（与后端 resources/flow 同名对齐）

src/utils/flow/            纯函数内核（placeholder、compareRule、extract 等）
src/views/project/testFlow/  画布、适配器、stores、composables
```

## 测试文件一览

| 测试文件 | 被测模块 | 说明 |
|---|---|---|
| `graphValidate.test.ts` | `@/utils/flow/graphValidate` | 开始节点、边、meta.run；数据驱动 |
| `graphAdapter.test.ts` | `@/views/project/testFlow/graphAdapter` | `toGraphJson` / `fromGraphJson` / `createEmptyGraph` |
| `placeholder.test.ts` | `@/utils/flow/placeholder` | `{{env.*}}` / `{{flow.*}}` / `{{asset.*}}` |
| `compareRule.test.ts` | `@/utils/flow/compareRule` | 9 种运算符、多作用域左值、右值占位符 |
| `extract.test.ts` | `@/utils/flow/extract` | JsonPath / header / status / asset 作用域 |
| `snowflakeId.test.ts` | `@/utils/flow/snowflakeId`、`nodeDataUtils` | 雪花 id 唯一性与 `generateNodeId` |

## 推荐回归组合

改 flow 工具函数或图适配后，建议全跑：

```bash
yarn test
```

仅改求值内核时：

```bash
yarn test src/test/flow/compareRule.test.ts src/test/flow/extract.test.ts src/test/flow/placeholder.test.ts
```

仅改图结构相关时：

```bash
yarn test src/test/flow/graphValidate.test.ts src/test/flow/graphAdapter.test.ts
```

## 约定

- 框架：Vitest（`describe` / `it` / `expect`）。
- 数据驱动用例优先读 `fixtures/*.json`，与后端 `qualitest-system/src/test/resources/flow/` 同名文件保持同步。
- 测试环境为 Node，不启动浏览器；画布交互、正式 Run API 暂无自动化覆盖。

## 与后端对齐

以下 fixtures 与后端同名，修改时需同步：

| fixtures | 后端路径 |
|---|---|
| `placeholder-cases.json` | `qualitest-system/src/test/resources/flow/placeholder-cases.json` |
| `compare-extract-cases.json` | `qualitest-system/src/test/resources/flow/compare-extract-cases.json` |
| `graph-validate-cases.json` | `qualitest-system/src/test/resources/flow/graph-validate-cases.json` |
| `demo-graph.json` | `qualitest-system/src/test/resources/flow/demo-graph.json` |
| `invalid-*.json` | 同上 |

跨端一致性校验示例（分别在两端目录执行）：

```bash
# 后端
cd qualitest
mvn test -pl qualitest-system -am -DskipTests=false "-Dtest=CompareRuleEvaluatorTest,ExtractApplicatorTest,PlaceholderResolverTest"

# 前端
cd qualitest-ui/apps/web
yarn test compareRule extract placeholder
```

## 未覆盖范围（手动验证）

- 路径模拟（`simulatePaths` / `useFlowSimulate`）
- 场景运行（`useFlowScenarioRun` → Run API）
- 运行库回放（`usePlayback` / `runLibraryStore`）
- 画布拖拽、连线、属性面板
