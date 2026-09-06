# qualitest-ui（质衡前端）

本目录为 **pnpm workspace 根**，包含浏览器端 Vue SPA（`apps/web`）、Electron 桌面应用（`apps/desktop`）与共享契约包（`packages/transport-types`）。被测 HTTP：浏览器经 Vite 代理与 axios（`ApiDebugTab`）；桌面端经 Electron 主进程 IPC。

## 环境要求

- Node.js 22+（与 CI / pnpm 11 要求一致，建议 ≥ 22.13）
- pnpm 11.x（推荐 Corepack：`corepack enable`）
- 开发联调时需先启动质衡后端（默认 `http://localhost:8800`），与 `apps/web` 下 Vite 代理配置一致

## 安装依赖

在 **`qualitest-ui`** 根目录执行：

```bash
pnpm install
```

镜像源由本目录 `.npmrc` 统一配置（默认 npmmirror）；海外可改该文件或覆盖本地配置。

## 常用命令

均在 **`qualitest-ui`** 根目录执行。

### 桌面开发为什么要起 Vite？

当前工程里 **界面仍是同一套 Vue SPA**（`apps/web`），Electron 在**开发模式**下用 `BrowserWindow.loadURL` 打开本地 Vite 地址，这样才能有 **热更新（HMR）**、与浏览器调试一致的体验。  
**生产安装包**里会把 `apps/web/dist` 打进应用，用 `loadFile` 打开，**不需要**再跑 Web 服务。

### 命令一览

| 命令 | 说明 |
|------|------|
| `pnpm dev` 或 `pnpm dev:web` | 启动 Web 开发服务（Vite，默认端口 **5180**，可用环境变量 `VITE_DEV_SERVER_PORT` 覆盖） |
| `pnpm build` 或 `pnpm build:web` | 生产构建 Web，产物目录 **`apps/web/dist`** |
| `pnpm build:stage` | 使用 staging 模式构建 Web |
| `pnpm preview` | 本地预览已构建的 Web 静态资源 |
| `pnpm dev:desktop` | **推荐**：一条命令并行启动 Vite（`dev:web`）与 Electron；关掉任一进程会结束本次开发会话（`concurrently -k`） |
| `pnpm dev:desktop:only` | 仅启动 Electron 壳（需本机 **已有** 在跑的 `pnpm dev:web`，用于单独调试主进程） |
| `pnpm build:desktop` | 先执行 `build:web`，再打包桌面应用（`electron-builder --dir`） |

## apps 与 packages

| 目录 | 语义 | 本仓内容 |
|------|------|----------|
| **`apps/`** | 可部署、可运行的应用 | `web`：Vue SPA；`desktop`：Electron 安装包（主进程 + preload） |
| **`packages/`** | 被 apps 引用的共享库 | `transport-types`：错误码、IPC channel 等无 UI 契约 |

依赖方向：`apps/web` → `packages/*`；`apps/desktop` → `packages/*` 并读取 `apps/web/dist`；**禁止** `apps/web` 依赖 `apps/desktop`。

## 目录说明

| 路径 | 说明 |
|------|------|
| `apps/web` | Vue 3 + Vite 单页应用，包名 `@qualitest/web` |
| `apps/web/src/transport` | HTTP 传输抽象（浏览器直连 / Java 转发 / Electron IPC） |
| `apps/desktop` | Electron 主进程与 preload，包名 `@qualitest/desktop` |
| `packages/transport-types` | 传输层契约，包名 `@qualitest/transport-types` |

## 环境变量（Web）

在 **`apps/web`** 下维护 `.env.development`、`.env.production`、`.env.staging` 等（Vite 从该目录读取）。

| 变量 | 说明 |
|------|------|
| `VITE_APP_BASE_API` | 治理类接口同源前缀（如 `/dev-api`），开发时由 Vite 代理到后端 |
| `VITE_DEV_SERVER_PORT` | 可选：本地开发服务器端口（默认 `5180`），改端口时请同步设置桌面端 `QUALITEST_WEB_DEV_URL` |
| `VITE_QUALITEST_HTTP_TRANSPORT` | 可选：构建默认调试传输模式；**项目设置中的切换优先于此项** |
| `VITE_HTTP_FORWARD_API` | 与 `VITE_APP_BASE_API` 同源的 Java 转发路径，如 `/dev-api/test/http-forward`（对应后端 `POST /test/http-forward`，需登录 JWT；实现见 `qualitest-admin` `TestHttpForwardController`） |

| 模式 | 触发条件 | API 调试请求去向 | 备注 |
|------|----------|------------------|------|
| `browser` | 默认，未配置 `VITE_QUALITEST_HTTP_TRANSPORT` | 浏览器 axios 直连被测 URL | 受浏览器 CORS 限制 |
| `browser-java-forward` | 项目设置 →「服务端代理」，或 `VITE_QUALITEST_HTTP_TRANSPORT=browser-java-forward` | 先发到 `VITE_HTTP_FORWARD_API`，再由 Java 转发 | 需配置 `VITE_HTTP_FORWARD_API` 且质衡后端已启动 |
| `electron-main` | Electron preload 自动注入 | Electron 主进程直连被测 URL | Web 构建无需配置；项目设置中不展示切换项 |

Electron 下是否走主进程由 preload 注入的 `window.__QUALITEST_ELECTRON__` 判定，无需在 Web 构建里写 `electron-main`。

## 环境变量（桌面开发 / 打包）

在启动 **Electron** 的进程环境中设置（勿写入会被打进浏览器 bundle 的 `VITE_*` 敏感值）。

| 变量 | 说明 |
|------|------|
| `QUALITEST_BACKEND_BASE_URL` | 质衡后端绝对地址，默认 `http://127.0.0.1:8800`（主进程治理 HTTP 使用） |
| `QUALITEST_WEB_DEV_URL` | 开发时加载的 Web 地址，默认 `http://127.0.0.1:5180`（须与 `pnpm dev:web` 终端里 Local 地址一致） |
| `QUALITEST_DEV_API_PREFIX` | 与 Web 的 `VITE_APP_BASE_API` 一致的前缀，默认 `/dev-api` |

桌面打包产物默认在 **`apps/desktop/release/`**（已在 `.gitignore` 中忽略）。Windows 下若遇 electron-builder 与签名相关错误，当前脚本已设置 `CSC_IDENTITY_AUTO_DISCOVERY=false` 并关闭可执行文件签名，便于本地出包。

## 与后端仓库的关系

`qualitest-ui` 位于主仓根目录下（与 Maven 模块并列）；前端构建产物路径为 **`qualitest-ui/apps/web/dist`**；部署时将该目录作为静态资源由 Nginx 或网关托管即可。
