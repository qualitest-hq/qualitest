# 部署说明

面向 Compose 全栈与本机开发的端口、环境变量与生产加固。快速上手摘要见根目录 [README](../README.md)；安全披露见 [SECURITY.md](../SECURITY.md)。

**靶场不在本仓 Compose 内**（不做 `--profile demo` 混栈）。需要演示靶场时另 clone 独立仓 [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo)，按其 [docs/deploy.md](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/deploy.md) / `quick-start` **单独启动**。一般人只起本仓即可体验质衡。

English: [deploy.en.md](./deploy.en.md)

---

## 一键全栈（推荐）

前置：Docker Desktop / Docker Engine + Compose V2；默认占用宿主机 **80 / 3306 / 6379**（可用 `.env` 改，见下文）。

```bash
# Linux / macOS
chmod +x scripts/quick-start.sh
./scripts/quick-start.sh

# Windows
scripts\quick-start.bat

# 或手动（无 .env 时可先复制 .env.example）
# cp .env.example .env   # Windows: copy .env.example .env
docker compose up -d --build
```

- 浏览器：**http://localhost**（`WEB_PORT` 非 80 时带端口）
- 默认账号：**`admin` / `admin123`**（Flyway V1 种子；上公网前务必改掉）
- 首次以 **app 健康 / 日志 Flyway migrate 成功** 为准（不再依赖 initdb 整库 dump）
- IDEA 插件服务器地址：Compose 填 **`http://localhost/prod-api`**；本机后端填 **`http://localhost:8080`**

首次 `--build` 会拉基础镜像并编译前后端，可能较慢，属正常。

---

## 与靶场联调（可选 · 双仓各起）

主仓与 demo **各自一套 Compose**，端口已错开，可并行：

```bash
# 终端 1 — 质衡（本仓）
cd qualitest && ./scripts/quick-start.sh   # Windows: scripts\quick-start.bat

# 终端 2 — 靶场（另 clone 后）
cd qualitest-demo && ./scripts/quick-start.sh
```

| 项 | 地址 |
|----|------|
| 质衡 Web | http://localhost |
| 靶场 API / Swagger | http://localhost:8081 （Swagger：`/swagger-ui.html`） |
| 靶场 UI | http://localhost:8082 |

**环境 `baseUrl`（项目 → 环境管理）**

| 跑法 | 建议 `baseUrl` |
|------|----------------|
| 两边都在本机进程（`mvn` / `yarn`），或仅浏览器直连宿主机端口 | 种子默认 **`http://localhost:8081`** |
| **质衡 app 在 Compose 容器内**，demo 映射在宿主机 **8081** | 容器内 `localhost` 打不到靶场，改为 **`http://host.docker.internal:8081`**（Docker Desktop：Windows / macOS）。Linux 可加 compose `extra_hosts: ["host.docker.internal:host-gateway"]`，或改为本机跑质衡后端 |

靶场库表用 initdb dump + 场景 seed，**不接 Flyway**；质衡自身迁移见下文「库表迁移（Flyway）」。

---

## 仅依赖（本机开发 · 热更）

Compose 只起 MySQL + Redis；后端 / 前端在宿主机跑，便于 **devtools / JRebel / Vite HMR**。

```bash
# Linux / macOS
./scripts/dev-deps-up.sh
# 停止（保留数据卷）: ./scripts/dev-deps-down.sh

# Windows
scripts\dev-deps-up.bat
# 停止: scripts\dev-deps-down.bat

# 等价手动命令
# docker compose up -d mysql redis
```

然后本机：

```bash
# 后端（profile=dev，连 localhost:3306 / 6379）
mvn -pl qualitest-admin -am -DskipTests package
# 按根目录 qualitest.bat / qualitest.sh 或 spring-boot:run 启动

# 前端
cd qualitest-ui && yarn install && yarn dev
```

浏览器：**http://localhost:5173**。MySQL 只需空库 `qualitest`（Compose `mysql` 服务会建）；启动后端后由 **Flyway** 自动执行 `db/migration`（含种子），日志出现 migrate 成功即可登录。

> **JRebel**：与 `spring-boot-devtools` 热重启不要同时开。用 JRebel 时设 `spring.devtools.restart.enabled=false`（或去掉 / 可选依赖）。

---

## 端口

| 项 | Compose 全栈 | 本机开发 | 说明 |
|----|--------------|----------|------|
| 质衡 Web | **`WEB_PORT` → 默认 80** | Vite **5173** | Compose 经 Nginx 提供静态页 |
| 质衡 API | 容器内 **8080**（默认不映射宿主机） | **8080** | 浏览器走 Nginx **`/prod-api`**；插件 Compose 填 `http://localhost/prod-api` |
| MySQL | **`MYSQL_PORT` → 默认 3306** | 本机或同上 | 库名 `qualitest` |
| Redis | **`REDIS_PORT` → 默认 6379** | 本机或同上 | Compose 内 app 用库号 `0`；本机 `.env.example` 示例多为 `10` |
| 靶场 API（demo） | **8081** | 同左 | **独立仓**另起 Compose；本仓不混入 |
| 靶场 UI（demo Compose） | **8082** | 按 demo 文档 | demo MySQL/Redis 默认 **3307 / 6380** |

### 改端口（最小示例）

复制 [`.env.example`](../.env.example) 为 `.env`（勿提交），例如：

```env
WEB_PORT=8088
MYSQL_PORT=33066
REDIS_PORT=63790
```

更复杂的本机覆盖（挂载、额外服务等）可用 `docker-compose.override.yml`（勿提交含密钥的内容；正式模板见路线图阶段 2）。

调试需宿主机直连后端时，可在 `docker-compose.yml` 的 `app` 服务解开 `ports: "8080:8080"` 注释后重建。

---

## Kubernetes / Helm（可选 · 社区自测）

> **状态**：仓库提供 `deploy/helm/qualitest` Chart 骨架，**维护者未在真实集群做端到端验证**。安装、排障、生产加固由使用者自行验证；问题欢迎提 Issue。  
> 日常试用请优先 **Compose**；本 Chart 面向「已有 K8s / 私有化要进集群」的场景。

### 目录

```text
deploy/helm/qualitest/
├── Chart.yaml
├── values.yaml
└── templates/          # app / web / 可选 mysql·redis / Ingress / Secret
```

### 准备镜像

Chart 默认镜像名与 Compose 一致（`qualitest-hq/qualitest-app` / `qualitest-web`）。需先构建并推到集群能拉到的仓库（官方 GHCR 发布见路线图阶段 2）：

```bash
docker compose build app web
# kind 示例
# kind load docker-image qualitest-hq/qualitest-app:latest
# kind load docker-image qualitest-hq/qualitest-web:latest
# 或 docker tag + push 到你们的 registry，再用 --set image.*.repository=...
```

### 安装

```bash
helm upgrade --install qualitest ./deploy/helm/qualitest \
  --namespace qualitest --create-namespace \
  --set secrets.tokenSecret='<strong-random-32+>' \
  --set secrets.mysqlRootPassword='<strong-password>'
```

未开 Ingress 时：

```bash
kubectl -n qualitest port-forward svc/qualitest-web 8080:80
# 浏览器 http://127.0.0.1:8080 ；默认 admin / admin123（立刻改掉）
# Service 名随 Release：{{ release }}-web；上例 Release 名为 qualitest
```

### 常用开关

| 需求 | 示例 |
|------|------|
| 开 Ingress | `--set ingress.enabled=true --set ingress.hosts[0].host=qualitest.example.com` |
| 外置 MySQL/Redis | `--set mysql.enabled=false --set redis.enabled=false`，并设置 `app.external.*` |
| 改镜像仓库 | `--set image.app.repository=ghcr.io/you/qualitest-app --set image.web.repository=...` |

默认内置 MySQL/Redis **仅适合 PoC**；生产请用托管库 / 已有中间件，并换强密钥、配 TLS Ingress。

安装后终端 NOTES 有完整提示；参数见 [`deploy/helm/qualitest/values.yaml`](../deploy/helm/qualitest/values.yaml)。

---

## 环境变量

权威清单见 [`.env.example`](../.env.example)。Compose 会把部分项注入 `app`；本机 `dev` / `prod` 也可同名覆盖。

| 变量 | 典型用途 | 备注 |
|------|----------|------|
| `WEB_PORT` / `MYSQL_PORT` / `REDIS_PORT` | 宿主机端口映射 | 仅 Compose |
| `MYSQL_ROOT_PASSWORD` | MySQL root；同时作为 Compose 内数据源密码 | **生产必改**；默认 `qualitest` 仅本地 |
| `TOKEN_SECRET` | JWT 签名密钥 | **生产必改**为强随机长串 |
| `TOKEN_EXPIRE_TIME` | Token 有效期（分钟） | 可选 |
| `SERVER_PORT` | 后端监听端口 | Compose 内固定 8080 |
| `QUALITEST_PROFILE` | 上传文件目录 | Compose / docker profile 默认 `/data/upload`（卷 `upload_data`） |
| `SPRING_DATASOURCE_DRUID_MASTER_*` | JDBC URL / 用户 / 密码 | Compose 已写死连服务名 `mysql`；本机改 localhost |
| `SPRING_DATA_REDIS_*` | Redis host / port / database / password | Compose 内 host=`redis` |
| `LOGGING_LEVEL_COM_QUALITEST` | 业务日志级别 | 默认 `info` |
| `QUALITEST_IMAGE_TAG` | 本地构建镜像 tag | 默认 `latest` |
| `DRUID_STAT_USERNAME` / `DRUID_STAT_PASSWORD` | Druid 控制台（仅 **dev**） | docker / prod **已关闭**控制台，勿对公网开 dev |

---

## 架构

```text
┌─────────────┐     /prod-api      ┌──────────────────┐
│   nginx     │ ─────────────────► │ qualitest-admin  │
│  (静态 dist) │                    │   (8080)         │
└─────────────┘                    └────────┬─────────┘
                                            │
                                   ┌────────┴────────┐
                                   │ mysql │ redis  │
                                   └─────────────────┘
```

| 服务 | 容器名 | 说明 |
|------|--------|------|
| mysql | qualitest-mysql | 仅建空库 `MYSQL_DATABASE=qualitest`；表结构与种子由 app 启动时 Flyway 迁移 |
| redis | qualitest-redis | 缓存 / 会话 |
| app | qualitest-app | Spring Boot，`SPRING_PROFILES_ACTIVE=docker`，上传 `/data/upload`；启动时 Flyway migrate |
| web | qualitest-web | Nginx 静态资源 + `/prod-api` → `app:8080` |

---

## 生产加固

默认值仅便于本地体验，**不得**原样用于公网或生产。

1. **密钥与口令**  
   - 强随机 `TOKEN_SECRET`  
   - 修改 `MYSQL_ROOT_PASSWORD`（及本机数据源口令）  
   - 登录后立即修改种子账号 `admin` / `admin123`  
   - Redis 若对公网可达，设置 `SPRING_DATA_REDIS_PASSWORD` 并同步 compose  

2. **Profile**  
   - Compose 使用 **`docker`**：Druid **statViewServlet / webStatFilter 已关闭**，上传默认 `/data/upload`  
   - 非 Compose 生产用 **`prod`**，同样不要启用 Druid 控制台；**切勿**把 `dev`（含默认 Druid 账号）暴露到公网  

3. **HTTPS**  
   - 本仓 Compose **默认仅 HTTP**（`WEB_PORT`→容器 80）  
   - 生产请在前面加反向代理（Nginx / Caddy / 云 LB）终结 TLS，反代到 `http://127.0.0.1:${WEB_PORT}`；或自建证书挂到自有 Nginx，把 `deploy/nginx/default.conf` 作 upstream 参考  
   - 证书与私钥不要打进镜像、不要提交进 Git  

4. **网络与数据**  
   - 尽量只暴露 Web 端口；MySQL / Redis 端口可不映射到公网（仅容器网络访问）  
   - 数据在 Docker 卷：`mysql_data` / `redis_data` / `upload_data`；备份与迁移需自行处理  
   - 勿提交 `.env`、`application-local.yml`、真实云 API Key、MCP / LLM 密钥  

更多见 [SECURITY.md](../SECURITY.md)。

---

## 常用命令

```bash
docker compose logs -f app
docker compose ps
docker compose down          # 保留数据卷
docker compose down -v       # 清空 MySQL / Redis / 上传卷（慎用，等于重装库）
docker compose up -d --build # 改代码或 Dockerfile 后重建
```

---

## 排障

| 现象 | 可尝试 |
|------|--------|
| `Bind for 0.0.0.0:80 failed` 等端口占用 | 改 `.env` 中 `WEB_PORT` / `MYSQL_PORT` / `REDIS_PORT` 后重新 `up` |
| 首次启动很慢 / 构建失败 | 确认 Docker 资源与网络；重试 `docker compose build --no-cache app`（或 `web`） |
| 打不开页面但容器在跑 | `docker compose ps`；`logs -f web` / `logs -f app`；确认访问的是 `WEB_PORT` |
| 登录失败 / 401 | 确认种子账号；若改过 `TOKEN_SECRET` 需重新登录；查 `app` 日志 |
| 空库启动后无表 / 登录失败 | 看 `app` 日志是否 Flyway migrate 成功；确认 `spring.flyway.enabled=true` 且 **url/user/password 与 Druid master 一致**（质衡非默认 `spring.datasource`） |
| 存量库报 `Found non-empty schema without metadata` | 见下文「库表迁移」存量 baseline；勿对已有库直接跑完整 V1 |
| `Checksum mismatch` | 改了已执行过的 migration 文件；应还原文件，用新的 `V{n}` 正向修复 |
| 改完 migration 旧卷仍不对 | 可丢数据时用 `down -v` 再 `up`；生产用增量 `V{n}`，禁止改已执行脚本 |
| 插件连不上 | Compose 用 `http://localhost/prod-api`；本机用 `http://localhost:8080`；勿混用 |
| 与 demo 端口冲突 | demo 默认 8081/8082/3307/6380，一般不冲突；若自改过主仓端口再核对 |
| 调试/测试流连不上靶场 | 确认 demo 已另起；Compose 内质衡 app 勿用 `localhost:8081`，改用 `host.docker.internal:8081`（见上文「与靶场联调」） |

---

## 相关文件

- [`deploy/helm/qualitest/`](../deploy/helm/qualitest/)（Helm Chart；社区自测）
- [`docker-compose.yml`](../docker-compose.yml)
- [`Dockerfile`](../Dockerfile)（后端）
- [`deploy/docker/Dockerfile.web`](../deploy/docker/Dockerfile.web)（前端）
- [`deploy/nginx/default.conf`](../deploy/nginx/default.conf)
- [`.env.example`](../.env.example)
- [`application-docker.yml`](../qualitest-admin/src/main/resources/application-docker.yml)
- 迁移脚本：[`qualitest-admin/.../db/migration/`](../qualitest-admin/src/main/resources/db/migration/)
- 靶场部署：[qualitest-demo/docs/deploy.md](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/deploy.md)

---

## 库表迁移（Flyway）

质衡（**仅主仓**）用 Flyway：空库启动应用自动 migrate；之后改表只加增量脚本。脚本目录：`qualitest-admin/src/main/resources/db/migration/`。

| 约定 | 说明 |
|------|------|
| 命名 | `V{n}__short_desc.sql`（两个下划线），如 `V2__add_api_group_index.sql` |
| 空库 | Compose 只建空库 `qualitest` → app 启动跑 `V1`…`Vn` |
| 新功能 | **只加**新的 `V{n}`；禁止改已执行文件、禁止只改根目录 `sql/qualitest_*.sql` 当升级路径 |
| 配置 | Druid master 下须显式配 `spring.flyway.url` / `user` / `password`（见各 `application-*.yml`） |
| 生产 | 禁止 `clean`；回滚靠新版本正向修复或备份还原（Community 无自动 down） |

查当前版本：

```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### 存量库接入（本地有数据 / 将来生产）

**禁止**对已有业务库直接执行完整 `V1__baseline.sql`（含 `DROP` / 重复 `CREATE`）。

1. 备份库（`sql/backup_db.bat` 或 mysqldump）
2. 确认当前结构 ≈ V1 所描述结构
3. 临时设置 `spring.flyway.baseline-on-migrate: true`（`baseline-version: 1` 已配置）
4. 启动一次 → `flyway_schema_history` 出现 baseline 记录（版本 1），**不**执行 V1 文件体
5. 改回 `baseline-on-migrate: false`
6. 之后只通过新增 `V2`、`V3`… 升级

根目录 `sql/qualitest_*.sql` 可作灾难备份 / 离线导出原料；**新变更只加 migration。**
