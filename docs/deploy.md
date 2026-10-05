# 部署说明

两条路，先选一条。口令不要混用：本机 `dev` 是 MySQL `root` / `123456`、Redis 无密码；Docker `.env` 里 MySQL 口令默认是 `qualitest`。

| | 甲 · 不用 Docker | 乙 · Docker 一键 |
|--|--|--|
| 要先装好 | JDK 17、Maven、Node ≥ 22.13、pnpm ≥ 11、MySQL 8、Redis | Git、Docker Desktop（或 Linux 上 Engine + Compose V2） |
| 库 | 自己建空库；质衡表由 Flyway 建，靶场要导入两份 SQL | 镜像 / 脚本带齐 |
| 页面 | http://localhost:5180 | 同左 |

快速上手摘要见根目录 [README](../README.md)；安全披露见 [SECURITY.md](../SECURITY.md)。

CI：改 Dockerfile / 前后端相关路径时，GitHub Actions 会跑 **`docker-app` / `docker-web` 镜像构建校验（只 build 不 push）**。正式镜像由 workflow **[GHCR](../.github/workflows/ghcr.yml)** 推到：

- `ghcr.io/qualitest-hq/qualitest-app`
- `ghcr.io/qualitest-hq/qualitest-web`

（`latest` + `sha-<短提交>`；仅 `qualitest-hq/qualitest` 的 `main` / 手动触发。）

**靶场不在本仓 Compose 内**（不做 `--profile demo` 混栈）。需要演示靶场时另 clone 独立仓 [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo)，按其 [docs/deploy.md](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/deploy.md) / `quick-start` **单独启动**（亦可拉 GHCR：`ghcr.io/qualitest-hq/qualitest-demo-app|web|mysql`）。一般人只起本仓即可体验质衡。

English: [deploy.en.md](./deploy.en.md)

---

## 甲、不用 Docker

本机已装好 **JDK 17、Maven、Node ≥ 22.13、pnpm ≥ 11、MySQL 8、Redis**。下面不使用 Docker。MySQL 用你自己的 `root`（默认口令按 `123456` 写）。Redis 无密码即可，应用自己选逻辑库，不用建库。

按系统只贴对应那一段。已经克隆过的，从 `cd` 之后开始贴。GitHub 慢时把 `github.com/qualitest-hq` 换成 `gitee.com/qualitest-hq`（只读镜像，目录名不变）。

### 1. 建库

在 MySQL 客户端整段执行（空库即可，表由后端启动时的 Flyway 创建）：

```sql
CREATE DATABASE IF NOT EXISTS qualitest
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

`root` 口令不是 `123456` 时，不要改库，只在下面启动命令前改环境变量 `SPRING_DATASOURCE_DRUID_MASTER_PASSWORD`。

确认 Redis 已在本机 6379：

```bash
redis-cli ping
```

应输出 `PONG`。质衡使用逻辑库 **10**。

### 2. 启动质衡

两个终端都停在仓库里。先起后端，日志里 Flyway migrate 成功后再起前端。

**终端 1 · Linux / macOS / Git Bash**

```bash
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

**终端 1 · Windows PowerShell**

```powershell
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

**终端 1 · Windows 命令提示符**

```bat
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

口令不是 `123456` 时，把启动那一行换成（只改口令）：

```bash
SPRING_DATASOURCE_DRUID_MASTER_PASSWORD=你的口令 mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

```powershell
$env:SPRING_DATASOURCE_DRUID_MASTER_PASSWORD = "你的口令"
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

```bat
set SPRING_DATASOURCE_DRUID_MASTER_PASSWORD=你的口令
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

默认 profile 是 `dev`：MySQL `localhost:3306/qualitest`，用户 `root`，Redis `localhost:6379` 逻辑库 10。后端端口 **8800**。

**终端 2 · 三个系统相同**（在 `qualitest-ui` 目录）：

```bash
cd qualitest-ui
pnpm install
pnpm dev
```

Vite 在 **5180**，把浏览器请求代理到 `http://127.0.0.1:8800`。

### 3. 确认可以登录

后端还在跑的前提下：

**Linux / macOS / Git Bash**

```bash
curl -fsS -D - -o /dev/null http://127.0.0.1:8800/captchaImage
```

**Windows（PowerShell 或命令提示符）**

```bat
curl.exe -fsS -D - -o NUL http://127.0.0.1:8800/captchaImage
```

响应头有 `HTTP/1.1 200` 后打开 `http://localhost:5180`，登录 **`admin` / `admin123`**（仅本地）。IDEA 插件服务器地址填 **`http://localhost:8800`**。

### 4. 可选：再起靶场

靶场没有 Flyway，必须把两份 SQL 导入空库 `qualitest-demo`。可以和质衡共用这一台 MySQL、这一台 Redis（逻辑库自动用 **11**）。

建库（仍在 MySQL 客户端）：

```sql
CREATE DATABASE IF NOT EXISTS `qualitest-demo`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

**Linux / macOS / Git Bash**（在 `qualitest-demo` 目录）：

```bash
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
mysql -uroot -p123456 qualitest-demo < deploy/mysql/docker-entrypoint-initdb.d/01-qualitest-demo.sql
mysql -uroot -p123456 qualitest-demo < deploy/mysql/docker-entrypoint-initdb.d/02_business_menus.sql
mvn -pl demo-admin -am spring-boot:run -DskipTests
```

**Windows PowerShell**

```powershell
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
Get-Content -Raw deploy\mysql\docker-entrypoint-initdb.d\01-qualitest-demo.sql | mysql -uroot -p123456 qualitest-demo
Get-Content -Raw deploy\mysql\docker-entrypoint-initdb.d\02_business_menus.sql | mysql -uroot -p123456 qualitest-demo
mvn -pl demo-admin -am spring-boot:run -DskipTests
```

**Windows 命令提示符**

```bat
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
mysql -uroot -p123456 qualitest-demo < deploy\mysql\docker-entrypoint-initdb.d\01-qualitest-demo.sql
mysql -uroot -p123456 qualitest-demo < deploy\mysql\docker-entrypoint-initdb.d\02_business_menus.sql
mvn -pl demo-admin -am spring-boot:run -DskipTests
```

口令不是 `123456` 时，把 `-p123456` 换成你的 root 口令，并在 `mvn` 前设置 `SPRING_DATASOURCE_DRUID_MASTER_PASSWORD`（写法同质衡）。

另开终端起靶场页面：

```bash
cd demo-ui
pnpm install
pnpm dev
```

| 项 | 地址 |
|----|------|
| 靶场 UI | http://localhost:5181 （`admin` / `admin123`） |
| 靶场 API / Swagger | http://localhost:8801/swagger-ui.html |
| 质衡里的环境 `baseUrl` | **`http://localhost:8801`** |

登录质衡 → **项目 → 环境管理**，把被测地址写成 `http://localhost:8801` 并保存。调试台对靶场发一条请求返回 200 即联调成功。

停后端：在对应的 `mvn` 终端按 `Ctrl+C`。停前端同样。

---

## 乙、Docker 一键

前置：本机已安装 **Git**，并且 **Docker Desktop 已经启动**（托盘图标就绪）或 Linux 上已有 Docker Engine + **Compose V2**。默认占用宿主机 **5180 / 3306 / 6379**。MySQL 口令用 `.env` 里的 `MYSQL_ROOT_PASSWORD`（默认 `qualitest`），与上面的 `123456` 无关。

按你的系统 **只复制对应那一段**。脚本（第 1 步）和直接 Docker Compose（第 1b 步）二选一。已经克隆过的，从 `cd` 或复制 `.env` 那一行开始贴。

### 1. 启动质衡

**Linux / macOS / Git Bash**

```bash
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
chmod +x scripts/quick-start.sh
./scripts/quick-start.sh
```

**Windows（PowerShell 或命令提示符）**

```bat
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
scripts\quick-start.bat
```

GitHub 克隆很慢时，把地址换成只读镜像（目录名仍是 `qualitest`）：

```bash
git clone https://gitee.com/qualitest-hq/qualitest.git
```

脚本会：没有 `.env` 时从 `.env.example` 复制；先 `docker compose pull` 拉 GHCR；拉取失败则自动 `docker compose up -d --build`（首次本地构建可能要十几分钟）。拉取一直停住时，`Ctrl+C` 后在仓库目录执行：

```bash
docker compose up -d --build
```

### 1b. 直接 Docker Compose（与上面的脚本等价）

不跑 `quick-start` 时，用下面整段代替第 1 步。脚本内部就是：复制 `.env` → `docker compose pull app web` → `docker compose up -d`。已经克隆过的，从复制 `.env` 那一行开始贴。`.env` 已存在时不会覆盖。

**Linux / macOS / Git Bash**

```bash
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
cp -n .env.example .env
docker compose pull app web
docker compose up -d
docker compose ps
curl -fsS -D - -o /dev/null http://localhost:5180/healthz
```

**Windows PowerShell**

```powershell
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose pull app web
docker compose up -d
docker compose ps
curl.exe -fsS -D - -o NUL http://localhost:5180/healthz
```

**Windows 命令提示符**

```bat
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
if not exist .env copy /Y .env.example .env
docker compose pull app web
docker compose up -d
docker compose ps
curl.exe -fsS -D - -o NUL http://localhost:5180/healthz
```

`pull app web` 失败或一直停住时，`Ctrl+C` 后在同一目录执行（MySQL / Redis 仍由 `up` 按需拉取）：

```bash
docker compose up -d --build
```

改过代码或 Dockerfile 后同样用这条重建。响应头出现 `HTTP/1.1 200` 后打开 `http://localhost:5180`。

### 2. 确认可以登录

脚本打印「已启动」后，`web` 会等后端健康检查通过才起来。再执行下面的检查，响应头里有 `HTTP/1.1 200` 后再打开浏览器。

**Linux / macOS / Git Bash**

```bash
docker compose ps
curl -fsS -D - -o /dev/null http://localhost:5180/healthz
```

**Windows（PowerShell 或命令提示符）**

```bat
docker compose ps
curl.exe -fsS -D - -o NUL http://localhost:5180/healthz
```

- 浏览器：`http://localhost:5180`（若改过 `WEB_PORT`，用改后的端口）
- 开箱账号（Flyway 跑完后，含 **V6**）：
  - **`admin` / `admin123`**：超级管理员（**仅本地 / 私有环境**；公网务必改密）
  - **`demo` / `demo123`**：演示访客（由 V6 将种子账号收敛而来；仅测试管理 + AI；正式环境请改密或停用）
- **公网演示环境**：运维口令为 **`admin` / `QtDemo#Admin2026`**，访客仍用 **`demo` / `demo123`**（见 [qualitest-demo-host](https://github.com/38680050/qualitest-demo-host)）。正式登录页不预填；演示可选挂载 `config.js` 预填（运维仓 `1panel/login-defaults.js`）。本地 `.env.development` 可预填 `admin`
- 首次以响应头 **`HTTP/1.1 200`** / 日志 Flyway migrate 成功为准（空库由 Flyway 建表，不再依赖 initdb 整库 dump）
- IDEA 插件服务器地址：Compose 填 **`http://localhost:5180/prod-api`**；本机后端填 **`http://localhost:8800`**

`healthz` 暂时失败时看后端日志，出现 Flyway 成功后再查一次：

```bash
docker compose logs -f app
```

### 3. 端口被占用时

日志出现 `Bind for 0.0.0.0:3306`、`6379` 或 `5180` failed。在 **qualitest 目录**整段再贴一次（把宿主机端口改成 6180 / 13306 / 16379，容器内部端口不变）：

**Linux / macOS**

```bash
docker compose down
sed -i.bak -e 's/^WEB_PORT=.*/WEB_PORT=6180/' -e 's/^MYSQL_PORT=.*/MYSQL_PORT=13306/' -e 's/^REDIS_PORT=.*/REDIS_PORT=16379/' .env
docker compose up -d
curl -fsS -D - -o /dev/null http://localhost:6180/healthz
```

**Windows PowerShell**

```powershell
docker compose down
(Get-Content .env) -replace '^WEB_PORT=.*','WEB_PORT=6180' -replace '^MYSQL_PORT=.*','MYSQL_PORT=13306' -replace '^REDIS_PORT=.*','REDIS_PORT=16379' | Set-Content .env -Encoding ascii
docker compose up -d
curl.exe -fsS -D - -o NUL http://localhost:6180/healthz
```

浏览器改为 `http://localhost:6180`。

### 4. 可选：再起靶场

另开一个终端，到 **另一个目录** 整段粘贴。质衡和靶场各一套 Compose，端口已错开。

**Linux / macOS / Git Bash**

```bash
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
chmod +x scripts/quick-start.sh
./scripts/quick-start.sh
```

**Windows（PowerShell 或命令提示符）**

```bat
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
scripts\quick-start.bat
```

国内镜像：`https://gitee.com/qualitest-hq/qualitest-demo.git`。

不用脚本时，在**另一个目录**整段粘贴（与上面的 `quick-start` 二选一）。

**Linux / macOS / Git Bash**

```bash
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
cp -n .env.example .env
docker compose pull mysql app web
docker compose up -d
docker compose ps
curl -fsS -D - -o /dev/null http://localhost:5181/
```

**Windows PowerShell**

```powershell
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose pull mysql app web
docker compose up -d
docker compose ps
curl.exe -fsS -D - -o NUL http://localhost:5181/
```

**Windows 命令提示符**

```bat
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
if not exist .env copy /Y .env.example .env
docker compose pull mysql app web
docker compose up -d
docker compose ps
curl.exe -fsS -D - -o NUL http://localhost:5181/
```

`pull` 失败时在 `qualitest-demo` 目录执行 `docker compose up -d --build`。`qualitest-demo-web` 为 Up，且 `http://localhost:5181/` 响应头为 `HTTP/1.1 200` 即可。

| 项 | 地址 |
|----|------|
| 质衡 Web | http://localhost:5180 |
| 靶场 UI | http://localhost:5181 （`admin` / `admin123`） |
| 靶场 API / Swagger | http://localhost:8801/swagger-ui.html |

质衡跑在 Compose 里时，到 **项目 → 环境管理**，把被测 `baseUrl` 写成：

```text
http://host.docker.internal:8801
```

`app` 已配置 `extra_hosts: host.docker.internal:host-gateway`，Windows、macOS、Linux 都用这一行。保存后在调试台发一条靶场接口，返回 200 即联调成功。靶场端口若改过 `APP_PORT`，这里的 `8801` 一起改。

靶场宿主机端口（5181 / 8801 / 3307 / 6380）被占用时，在 `qualitest-demo/.env` 改 `WEB_PORT` / `APP_PORT` / `MYSQL_PORT` / `REDIS_PORT`，再执行 `docker compose up -d`。

### 5. 停机

在各自仓库目录执行。`down` 保留数据；`down -v` 清空数据库，等于重装。

```bash
docker compose down
```

### 官方镜像（GHCR）

| 镜像 | 说明 |
|------|------|
| `ghcr.io/qualitest-hq/qualitest-app` | 后端（Spring Boot） |
| `ghcr.io/qualitest-hq/qualitest-web` | 前端（Nginx + SPA，反代 `/prod-api` → app） |

Packages：https://github.com/orgs/qualitest-hq/packages  

官方包已为 **Public**，可匿名 `docker pull`。若拉取私有 fork / 自建包，需 `docker login ghcr.io`（PAT 勾选 `read:packages`）。

新包首次推送默认为 Private：组织 Settings → Packages 需允许 Public，再到包页 **Change visibility** → **Public**（不可逆）。

指定提交：`.env` 设 `QUALITEST_IMAGE_TAG=sha-<短 sha>`（与 Actions 推送的 tag 一致）。仍需 Compose 内的 **MySQL + Redis**（或自备等价服务）。

---

## 与靶场联调（可选 · 双仓各起）

完整复制粘贴见上文 **「4. 可选：再起靶场」**。两边各一套 Compose，默认可并行。

**环境 `baseUrl`（项目 → 环境管理）**

| 跑法 | 建议 `baseUrl` |
|------|----------------|
| 两边都在本机进程（`mvn` / `pnpm`），或仅浏览器直连宿主机端口 | **`http://localhost:8801`** |
| **质衡 app 在 Compose 容器内**，demo 映射在宿主机 **8801** | **`http://host.docker.internal:8801`**（本仓 `docker-compose.yml` 已写入 `extra_hosts`） |

靶场库表用 initdb dump + 场景 seed，**不接 Flyway**；质衡自身迁移见下文「库表迁移（Flyway）」。

---

## 本机改代码（热更 · MySQL / Redis 仍用 Compose）

这不是「不用 Docker」。只把 MySQL + Redis 放在 Compose 里，后端 / 前端在宿主机跑，便于 **devtools / JRebel / Vite HMR**。完全不用 Docker 走上文 **「甲、不用 Docker」**。

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
cd qualitest-ui && pnpm install && pnpm dev
```

浏览器：`http://localhost:5180`。MySQL 只需空库 `qualitest`（Compose `mysql` 服务会建）；启动后端后由 **Flyway** 自动执行 `db/migration`（含种子），日志出现 migrate 成功即可登录。

> **JRebel**：与 `spring-boot-devtools` 热重启不要同时开。用 JRebel 时设 `spring.devtools.restart.enabled=false`（或去掉 / 可选依赖）。

---

## 端口

| 项 | Compose 全栈 | 本机开发 | 说明 |
|----|--------------|----------|------|
| 质衡 Web | **`WEB_PORT` → 默认 5180**（容器内 Nginx 同为 **5180**） | Vite **5180** | Compose 经 Nginx 提供静态页 |
| 质衡 API | 容器内 **8800**（默认不映射宿主机） | **8800** | 浏览器走 Nginx **`/prod-api`**；插件 Compose 填 `http://localhost:5180/prod-api` |
| MySQL | **`MYSQL_PORT` → 默认 3306** | 本机或同上 | 库名 `qualitest` |
| Redis | **`REDIS_PORT` → 默认 6379** | 本机或同上 | Compose 内 app 用库号 `0`；本机 `.env.example` 示例多为 `10` |
| 靶场 API（demo） | **8801** | 同左 | **独立仓**另起 Compose；本仓不混入 |
| 靶场 UI（demo Compose） | **5181** | 按 demo 文档 | demo MySQL/Redis 默认 **3307 / 6380** |

### 改端口（最小示例）

复制 [`.env.example`](../.env.example) 为 `.env`（勿提交），例如：

```env
WEB_PORT=6180
MYSQL_PORT=33066
REDIS_PORT=63790
```

更复杂的本机覆盖（挂载、暴露 app:8800、调日志等）：

```bash
# Windows
copy docker-compose.override.yml.example docker-compose.override.yml
# Linux / macOS
cp docker-compose.override.yml.example docker-compose.override.yml
```

按需取消注释后 `docker compose up -d`。真正的 `docker-compose.override.yml` **勿提交**（已在 `.gitignore`）。模板见 [`docker-compose.override.yml.example`](../docker-compose.override.yml.example)。

调试需宿主机直连后端时：在 override 里解开 `app.ports: "8800:8800"`（勿改仓库内 `docker-compose.yml` 再提交）。

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

Chart 默认已指向 GHCR（`ghcr.io/qualitest-hq/qualitest-app` / `qualitest-web`）。集群能访问 ghcr.io 时可直接装；离线 / 私有仓库时再改 `image.*.repository`，或本地构建后 `kind load`：

```bash
docker compose build app web
# kind 示例
# kind load docker-image ghcr.io/qualitest-hq/qualitest-app:latest
# kind load docker-image ghcr.io/qualitest-hq/qualitest-web:latest
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
kubectl -n qualitest port-forward svc/qualitest-web 5180:5180
# 浏览器 http://127.0.0.1:5180 ；默认 admin / admin123（立刻改掉）
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
| `SERVER_PORT` | 后端监听端口 | Compose 内固定 8800 |
| `QUALITEST_PROFILE` | 上传文件目录 | Compose / docker profile 默认 `/data/upload`（卷 `upload_data`） |
| `SPRING_DATASOURCE_DRUID_MASTER_*` | JDBC URL / 用户 / 密码 | Compose 已写死连服务名 `mysql`；本机改 localhost |
| `SPRING_DATA_REDIS_*` | Redis host / port / database / password | Compose 内 host=`redis` |
| `LOGGING_LEVEL_COM_QUALITEST` | 业务日志级别 | 默认 `info` |
| `QUALITEST_IMAGE_TAG` | Compose / 本地镜像 tag | 默认 `latest`；可与 GHCR 的 `sha-xxxx` 对齐 |
| `DRUID_STAT_USERNAME` / `DRUID_STAT_PASSWORD` | Druid 控制台（仅 **dev**） | docker / prod **已关闭**控制台，勿对公网开 dev |

---

## 架构

```text
┌─────────────┐     /prod-api      ┌──────────────────┐
│   nginx     │ ─────────────────► │ qualitest-admin  │
│  (静态 dist) │                    │   (8800)         │
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
| web | qualitest-web | Nginx 静态资源 + `/prod-api` → `app:8800` |

---

## 生产加固

默认值仅便于本地体验，**不得**原样用于公网或生产。

1. **密钥与口令**  
   - 强随机 `TOKEN_SECRET`  
   - 修改 `MYSQL_ROOT_PASSWORD`（及本机数据源口令）  
   - 登录后立即修改种子账号 `admin` / `admin123`；生产环境同时**改密或停用**开箱演示账号 `demo` / `demo123`  
   - Redis 若对公网可达，设置 `SPRING_DATA_REDIS_PASSWORD` 并同步 compose  

2. **Profile**  
   - Compose 使用 **`docker`**：Druid **statViewServlet / webStatFilter 已关闭**，上传默认 `/data/upload`  
   - 非 Compose 生产用 **`prod`**，同样不要启用 Druid 控制台；**切勿**把 `dev`（含默认 Druid 账号）暴露到公网  

3. **HTTPS**  
   - 本仓 Compose **默认仅 HTTP**（`WEB_PORT`→容器 **5180**）  
   - 生产请在前面加反向代理（Nginx / Caddy / 云 LB）终结 TLS，反代到 `http://127.0.0.1:${WEB_PORT}`；或自建证书挂到自有 Nginx，把 `deploy/nginx/default.conf` 作 upstream 参考  
   - 证书与私钥不要打进镜像、不要提交进 Git  

4. **网络与数据**  
   - 尽量只暴露 Web 端口；MySQL / Redis 端口可不映射到公网（仅容器网络访问）  
   - 数据在 Docker 卷：`mysql_data` / `redis_data` / `upload_data`；备份与迁移需自行处理  
   - 勿提交 `.env`、`application-local.yml`、真实云 API Key、MCP / LLM 密钥  

更多见 [SECURITY.md](../SECURITY.md)。

---

## 常用命令

从零整段粘贴见上文 **「1b. 直接 Docker Compose」**。已经在仓库目录、镜像也齐时，用下面的单条命令：

```bash
docker compose logs -f app
docker compose ps
docker compose down          # 保留数据卷
docker compose down -v       # 清空 MySQL / Redis / 上传卷（慎用，等于重装库）
docker compose pull app web  # 只拉质衡 GHCR；MySQL / Redis 由 up 按需拉取
docker compose up -d         # 用已有 / 已 pull 的镜像起栈
docker compose up -d --build # 改代码、Dockerfile，或 GHCR 拉取失败时本地重建
```

---

## 排障

| 现象 | 可尝试 |
|------|--------|
| `Bind for 0.0.0.0:5180 failed` 等端口占用 | 改 `.env` 中 `WEB_PORT` / `MYSQL_PORT` / `REDIS_PORT` 后重新 `up` |
| 首次启动很慢 / 构建失败 | 确认 Docker 资源与网络；重试 `docker compose build --no-cache app`（或 `web`） |
| 打不开页面但容器在跑 | `docker compose ps`；`logs -f web` / `logs -f app`；确认访问的是 `WEB_PORT` |
| 登录失败 / 401 | 确认种子账号；若改过 `TOKEN_SECRET` 需重新登录；查 `app` 日志 |
| 空库启动后无表 / 登录失败 | 看 `app` 日志是否 Flyway migrate 成功；确认 `spring.flyway.enabled=true` 且 **url/user/password 与 Druid master 一致**（质衡非默认 `spring.datasource`） |
| 存量库报 `Found non-empty schema without metadata` | 见下文「库表迁移」存量 baseline；勿对已有库直接跑完整 V1 |
| `Checksum mismatch` | 改了已执行过的 migration 文件；应还原文件，用新的 `V{n}` 正向修复 |
| 改完 migration 旧卷仍不对 | 可丢数据时用 `down -v` 再 `up`；生产用增量 `V{n}`，禁止改已执行脚本 |
| 插件连不上 | Compose 用 `http://localhost:5180/prod-api`；本机用 `http://localhost:8800`；勿混用 |
| 与 demo 端口冲突 | demo 默认 8801/5181/3307/6380，一般不冲突；若自改过主仓端口再核对 |
| 调试/测试流连不上靶场 | 确认 demo 已另起；Compose 内质衡把环境 `baseUrl` 写成 `http://host.docker.internal:8801`（见上文「4. 可选：再起靶场」） |

---

## GitHub Pages 落地页

门面站源码 [`site/`](../site/)，线上：`https://qualitest-hq.github.io/qualitest/`。本地与 CI 说明见 [`site/README.md`](../site/README.md)。

本地预览：`cd site && pnpm install && pnpm build && pnpm preview` → `http://127.0.0.1:4321/qualitest/`。

---

## 相关文件

- [`site/`](../site/)（GitHub Pages 落地页）
- [`deploy/helm/qualitest/`](../deploy/helm/qualitest/)（Helm Chart；社区自测）
- [`docker-compose.yml`](../docker-compose.yml)
- [`docker-compose.override.yml.example`](../docker-compose.override.yml.example)（本机 YAML 覆盖模板；真实 override 勿提交）
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
| 新功能 | **只加**新的 `V{n}`；禁止改已执行文件；**不要**用本机 mysqldump 全库导出当升级路径 |
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

1. 备份库（`mysqldump` 等，备份文件**勿提交**进 Git）
2. 确认当前结构 ≈ V1 所描述结构
3. 临时设置 `spring.flyway.baseline-on-migrate: true`（`baseline-version: 1` 已配置）
4. 启动一次 → `flyway_schema_history` 出现 baseline 记录（版本 1），**不**执行 V1 文件体
5. 改回 `baseline-on-migrate: false`
6. 之后只通过新增 `V2`、`V3`… 升级

**新变更只加 migration。** 本机全库 dump / 临时升级脚本请放仓外，勿进仓库。
