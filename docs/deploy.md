# 部署说明（Compose 全栈）

## 一键全栈

前置：Docker Desktop / Docker Engine + Compose V2，本机端口 **80 / 3306 / 6379** 可用（可用 `.env` 或 `docker-compose.override.yml` 改端口）。

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

## 靶场 / RustFS（独立仓）

本仓 Compose **不含**靶场与 RustFS。接口测试靶场、可选 S3（RustFS）见独立仓：

- 仓库：[qualitest-demo](https://github.com/qualitest-hq/qualitest-demo)
- 部署说明：[docs/deploy.md](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/deploy.md)
- 一键：`scripts\quick-start.bat rustfs` / `./scripts/quick-start.sh rustfs`（会加载 `docker-compose.rustfs.yml`）

默认与主仓端口错开（Web 8082 / API 8081 / MySQL 3307 / Redis 6380），可与主仓同时运行。

## 架构

| 服务 | 容器名 | 说明 |
|------|--------|------|
| mysql | qualitest-mysql | 初始化脚本：`sql/qualitest_*.sql` |
| redis | qualitest-redis | 缓存 / 会话 |
| app | qualitest-app | Spring Boot，`profile=docker`，上传目录 `/data/upload` |
| web | qualitest-web | Nginx 静态资源 + `/prod-api` → app:8080 |

## 常用命令

```bash
docker compose logs -f app
docker compose ps
docker compose down          # 保留数据卷
docker compose down -v       # 清空 MySQL/Redis/上传卷（慎用）
```

## 相关文件

- [`docker-compose.yml`](../docker-compose.yml)
- [`Dockerfile`](../Dockerfile)（后端）
- [`deploy/docker/Dockerfile.web`](../deploy/docker/Dockerfile.web)（前端）
- [`deploy/nginx/default.conf`](../deploy/nginx/default.conf)
- [`.env.example`](../.env.example)
- [`application-docker.yml`](../qualitest-admin/src/main/resources/application-docker.yml)
