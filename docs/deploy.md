# 部署说明（Compose 全栈）

## 一键全栈

前置：Docker Desktop / Docker Engine + Compose V2，本机端口 **80 / 3306 / 6379** 可用（启用 RustFS 时另需 **9000 / 9001**；可用 `.env` 或 `docker-compose.override.yml` 改端口）。

```bash
# Linux / macOS
chmod +x scripts/quick-start.sh
./scripts/quick-start.sh

# Windows
scripts\quick-start.bat

# 或手动
docker compose up -d --build
```

浏览器打开 **http://localhost**，默认账号 **`admin` / `admin123`**（以种子 SQL 为准）。

生产务必修改 `.env` 中的 `MYSQL_ROOT_PASSWORD`、`TOKEN_SECRET`。

## 仅依赖（本机开发）

```bash
docker compose up -d mysql redis
```

然后本机：

```bash
# 后端（profile=dev，连 localhost:3306 / 6379）
mvn -pl qualitest-admin -am -DskipTests package
# 按 qualitest.bat / spring-boot:run 启动

# 前端
cd qualitest-ui && yarn install && yarn dev
```

## 可选：RustFS（S3，供 qualitest-demo 文件 API）

默认**不启动**。需要时：

```bash
docker compose --profile rustfs up -d
# 或全栈一并启动
scripts\quick-start.bat rustfs
# ./scripts/quick-start.sh rustfs
```

| 项 | 默认 |
|----|------|
| S3 API | http://localhost:9000 |
| 控制台 | http://localhost:9001 |
| Access / Secret | `rustfsadmin` / `rustfsadmin` |

本机跑 `qualitest-demo` 时，确认 `demo.rustfs.enabled=true`，且 `endpoint` 指向 `http://127.0.0.1:9000`。  
**推荐**：靶场独立 Compose 见 [qualitest-demo/docs/deploy.md](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/deploy.md)（`scripts\quick-start.bat rustfs`），不必依赖本仓 profile。

## 架构

| 服务 | 容器名 | 说明 |
|------|--------|------|
| mysql | qualitest-mysql | 初始化脚本：`sql/qualitest_*.sql` |
| redis | qualitest-redis | 缓存 / 会话 |
| app | qualitest-app | Spring Boot，`profile=docker`，上传目录 `/data/upload` |
| web | qualitest-web | Nginx 静态资源 + `/prod-api` → app:8080 |
| rustfs（可选） | qualitest-rustfs | S3 API :9000 / 控制台 :9001；`--profile rustfs` |

## 常用命令

```bash
docker compose logs -f app
docker compose ps
docker compose down          # 保留数据卷
docker compose down -v       # 清空 MySQL/Redis/上传/RustFS 等数据卷（慎用）
```

## 相关文件

- [`docker-compose.yml`](../docker-compose.yml)
- [`Dockerfile`](../Dockerfile)（后端）
- [`deploy/docker/Dockerfile.web`](../deploy/docker/Dockerfile.web)（前端）
- [`deploy/nginx/default.conf`](../deploy/nginx/default.conf)
- [`.env.example`](../.env.example)
- [`application-docker.yml`](../qualitest-admin/src/main/resources/application-docker.yml)
