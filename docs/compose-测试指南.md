# Compose 全栈测试指南

> 用途：回家后按本文验收「Docker Compose 一键起质衡」。  
> 关联：[`deploy.md`](./deploy.md) · [`docker-compose.yml`](../docker-compose.yml) · [`scripts/quick-start.bat`](../scripts/quick-start.bat)

---

## 0. 前置检查（开测前）

| 项 | 要求 |
|----|------|
| Docker | Docker Desktop 已安装并**正在运行**（托盘图标正常） |
| Compose | 终端能执行 `docker compose version`（需 V2） |
| 磁盘 | 建议剩余 ≥ 10GB（首次拉镜像 + Maven/Yarn 构建） |
| 端口 | 本机 **80 / 3306 / 6379** 尽量空闲；冲突见文末「改端口」 |
| 代码 | 在仓库根目录 `qualitest/`（含 `docker-compose.yml`、`qualitest-ui/`） |

```bat
cd /d d:\Project\Composite\qualitest-all\qualitest
docker compose version
```

---

## 1. 一键全栈（主路径）

### 1.1 启动

```bat
cd /d d:\Project\Composite\qualitest-all\qualitest
scripts\quick-start.bat
```

或：

```bat
docker compose up -d --build
```

**预期：**

- 首次会构建后端镜像（Maven）和前端镜像（Yarn），可能 **15～40 分钟**（视网络/机器）
- 控制台无长时间卡在报错退出；最终 `docker compose ps` 中 mysql/redis/app/web 均为 `running`（app 建议 `healthy`）

### 1.2 看状态

```bat
docker compose ps
docker compose logs -f app
```

另开终端可看：

```bat
docker compose logs -f web
docker compose logs -f mysql
```

**预期（app）：**

- 出现 Spring Boot 启动完成日志（类似 `Started QualitestApplication`）
- healthcheck 通过后，`qualitest-app` 为 healthy（首次 `start_period` 约 90s，多等一会）

**预期（mysql）：**

- Compose MySQL **只建空库**；表结构与种子由 **app 启动时 Flyway** 迁移（`db/migration/V1__baseline.sql`）
- 已有脏卷若结构不对：可丢数据时用文末「清库重来」`down -v`；存量有数据见 `docs/deploy.md`「库表迁移」baseline

### 1.3 浏览器验收

1. 打开：**http://localhost**（若改了 `WEB_PORT`，用对应端口）
2. 应出现质衡登录页（不是 Nginx 默认页、不是纯 JSON 报错）
3. 账号：**`admin` / `admin123`**
4. 登录成功 → 能进首页 / 项目列表

| # | 检查点 | 通过标准 |
|---|--------|----------|
| A | 打开首页 | 登录页正常渲染 |
| B | 登录 | `admin` / `admin123` 成功 |
| C | 静态资源 | 刷新不白屏，CSS/JS 正常 |
| D | API 通 | 登录后菜单/接口有数据（F12 Network 里 `/prod-api/` 为 200 或业务码成功） |
| E | 刷新 SPA | 登录后任意路由刷新仍回到应用（不 404） |

### 1.4 停掉

```bat
docker compose down
```

数据卷默认保留（库里的数据还在）。

---

## 2. 仅依赖模式（可选）

验证「本机开发只借用 Compose 的 MySQL/Redis」：

```bat
docker compose up -d mysql redis
docker compose ps
```

**预期：** 只有 `qualitest-mysql`、`qualitest-redis` 在跑；本机可用 `localhost:3306`（密码默认 `qualitest`，见 `.env` / `.env.example`）和 `localhost:6379`。

再按 README「方式 B」起本机 `mvn` + `yarn dev`（本步非必须，有空再测）。

---

## 3. 常见失败与处理

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| `port is already allocated` | 80/3306/6379 被占 | 见下方「改端口」 |
| `app` 一直 unhealthy / Restarting | 等不够 / 库未就绪 / 密钥或库密码错 | `docker compose logs app`；确认 mysql healthy 后再看 app |
| 登录页 502 / `/prod-api` 502 | 后端未起来 | 等 app healthy；`logs -f app` |
| 登录页空白 | 前端构建失败 | `docker compose logs web`；重建：`docker compose build --no-cache web` |
| 登录密码不对 | 用了旧数据卷或非种子库 | 清卷重来（慎用，见下） |
| 构建 Yarn 报 lockfile | Dockerfile 未用 classic yarn | 已按 lockfile v1 配置；拉取最新代码再 build |
| 构建 Maven 超时 | 网络拉依赖慢 | 重试 `docker compose build app`；或配置镜像加速 |

### 改端口

编辑 `.env`（没有则从 `.env.example` 复制）：

```env
WEB_PORT=8088
MYSQL_PORT=3307
REDIS_PORT=6380
MYSQL_ROOT_PASSWORD=qualitest
TOKEN_SECRET=换成你自己的长随机串
```

然后：

```bat
docker compose up -d --build
```

浏览器改为 **http://localhost:8088**。

或复制 `docker-compose.override.yml.example` → `docker-compose.override.yml` 改端口（该文件勿提交）。

### 清库重来（种子 SQL 只在空数据卷时执行）

```bat
docker compose down -v
docker compose up -d --build
```

`-v` 会删 MySQL/Redis/上传卷，等于全新初始化。

---

## 4. 验收清单（打勾）

环境：

- [ ] Docker Desktop 运行中，`docker compose version` 正常
- [ ] 在 `qualitest/` 目录执行命令

全栈：

- [ ] `scripts\quick-start.bat` 或 `docker compose up -d --build` 成功
- [ ] `docker compose ps`：mysql / redis / app / web 均 `running`（app 建议 `healthy`；web 未配 healthcheck，一般仅为 running）
- [ ] http://localhost 打开登录页
- [ ] `admin` / `admin123` 登录成功
- [ ] 登录后 F12：`/prod-api/` 请求基本成功
- [ ] 刷新页面不丢成 Nginx 404

可选：

- [ ] `docker compose up -d mysql redis` 仅依赖可用
- [ ] `docker compose down` 能干净停止
- [ ] 改 `WEB_PORT` 后仍能访问
- [ ] （可选）靶场 / RustFS：见独立仓 [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo)

---

## 5. 测试结果记录（现场填）

| # | 步骤 | 结果（通过/失败） | 现象 / 日志摘要 |
|---|------|-------------------|-----------------|
| 1 | compose 构建启动 | | |
| 2 | ps / healthy | | |
| 3 | 打开登录页 | | |
| 4 | admin 登录 | | |
| 5 | /prod-api 联通 | | |
| 6 | （可选）仅 mysql+redis | | |

失败时请附上：

```bat
docker compose ps
docker compose logs --tail=200 app
docker compose logs --tail=100 web
```

---

## 6. 架构速查

```text
浏览器 :WEB_PORT(默认 80)
  └─ qualitest-web (Nginx + dist)
        └─ /prod-api → qualitest-app:8080 (SPRING_PROFILES_ACTIVE=docker)
                         ├─ qualitest-mysql:3306（种子 SQL）
                         └─ qualitest-redis:6379
```

默认账号以种子 SQL 为准：**admin / admin123**。  
Compose 默认库根密码：`MYSQL_ROOT_PASSWORD`（示例为 `qualitest`），与本机 `dev` 的 `123456` 不是同一套，勿混用。
