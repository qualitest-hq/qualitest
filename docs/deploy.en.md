# Deployment

Pick one path. Do not mix passwords: local `dev` uses MySQL `root` / `123456` and Redis with no password; Docker `.env` defaults `MYSQL_ROOT_PASSWORD` to `qualitest`.

| | A · No Docker | B · Docker one-click |
|--|--|--|
| Install first | JDK 17, Maven, Node ≥ 22.13, pnpm ≥ 11, MySQL 8, Redis | Git, Docker Desktop (or Engine + Compose V2 on Linux) |
| Database | You create an empty schema; Qualitest tables come from Flyway; the demo target needs two SQL files | Images / the script include them |
| UI | http://localhost:5180 | same |

Quick start summary: [README.en.md](../README.en.md). Security disclosure: [SECURITY.md](../SECURITY.md).

CI builds **`docker-app` / `docker-web` images (build only, no push)** when Dockerfiles or related paths change. GitHub images go to **[GHCR](../.github/workflows/ghcr.yml)** (`ghcr.io/qualitest-hq/qualitest-app|web`). Compose **defaults** to the Aliyun public registry (**anonymous, no login**):

- `registry.cn-hangzhou.aliyuncs.com/qualitest-hq/qualitest-app`
- `registry.cn-hangzhou.aliyuncs.com/qualitest-hq/qualitest-web`

To use GHCR, set `QUALITEST_IMAGE_PREFIX=ghcr.io/qualitest-hq` in `.env`. `quick-start` tries Aliyun, then GHCR, then a local `--build`.

**The demo target is not in this repo’s Compose** (no `--profile demo` mixed stack). To run the shop demo, clone [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) and start it with its own [docs/deploy.md](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/deploy.md) / `quick-start` (Aliyun `qualitest-demo-app|web|mysql` by default). Most users only need this repo to try Qualitest.

中文版：[deploy.md](./deploy.md)

---

## A. No Docker

Install **JDK 17, Maven, Node ≥ 22.13, pnpm ≥ 11, MySQL 8, and Redis** on the host. Nothing below uses Docker. MySQL `root` is assumed to use password `123456`. Redis needs no password; the app selects its logical database.

Paste only the block for your OS. If the repo is already cloned, start after `cd`. If GitHub is slow, replace `github.com/qualitest-hq` with `gitee.com/qualitest-hq` (read-only mirror; folder name stays the same).

### 1. Create the database

Run this in the MySQL client. An empty database is enough; Flyway creates the tables when the backend starts:

```sql
CREATE DATABASE IF NOT EXISTS qualitest
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

If `root`'s password is not `123456`, leave the database as-is and set `SPRING_DATASOURCE_DRUID_MASTER_PASSWORD` before the start command below.

Check Redis on port 6379:

```bash
redis-cli ping
```

Expect `PONG`. Qualitest uses logical database **10**.

### 2. Start Qualitest

Use two terminals in the repo. Start the backend first; start the UI after Flyway migrate succeeds in the log.

**Terminal 1 · Linux / macOS / Git Bash**

```bash
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

**Terminal 1 · Windows PowerShell**

```powershell
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

**Terminal 1 · Windows Command Prompt**

```bat
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

When the password is not `123456`, replace the start line (change only the password):

```bash
SPRING_DATASOURCE_DRUID_MASTER_PASSWORD=your-password mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

```powershell
$env:SPRING_DATASOURCE_DRUID_MASTER_PASSWORD = "your-password"
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

```bat
set SPRING_DATASOURCE_DRUID_MASTER_PASSWORD=your-password
mvn -pl qualitest-admin -am spring-boot:run -DskipTests
```

The default profile is `dev`: MySQL `localhost:3306/qualitest`, user `root`, Redis `localhost:6379` logical DB 10. Backend port **8800**.

**Terminal 2 · same on every OS** (directory `qualitest-ui`):

```bash
cd qualitest-ui
pnpm install
pnpm dev
```

Vite listens on **5180** and proxies the browser to `http://127.0.0.1:8800`.

### 3. Confirm you can sign in

With the backend still running:

**Linux / macOS / Git Bash**

```bash
curl -fsS -D - -o /dev/null http://127.0.0.1:8800/captchaImage
```

**Windows (PowerShell or Command Prompt)**

```bat
curl.exe -fsS -D - -o NUL http://127.0.0.1:8800/captchaImage
```

When the status line is `HTTP/1.1 200`, open `http://localhost:5180` and sign in **`admin` / `admin123`** (local only). IDEA plugin server URL: **`http://localhost:8800`**.

### 4. Optional: demo target

The demo repo has no Flyway. Import both SQL files into an empty database `qualitest-demo`. It can share the same MySQL and Redis processes (logical DB **11**).

In the MySQL client:

```sql
CREATE DATABASE IF NOT EXISTS `qualitest-demo`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

**Linux / macOS / Git Bash** (directory `qualitest-demo`):

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

**Windows Command Prompt**

```bat
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
mysql -uroot -p123456 qualitest-demo < deploy\mysql\docker-entrypoint-initdb.d\01-qualitest-demo.sql
mysql -uroot -p123456 qualitest-demo < deploy\mysql\docker-entrypoint-initdb.d\02_business_menus.sql
mvn -pl demo-admin -am spring-boot:run -DskipTests
```

If the password is not `123456`, change `-p123456` and set `SPRING_DATASOURCE_DRUID_MASTER_PASSWORD` before `mvn` (same pattern as Qualitest).

In another terminal:

```bash
cd demo-ui
pnpm install
pnpm dev
```

| Item | URL |
|------|-----|
| Demo UI | http://localhost:5181 (`admin` / `admin123`) |
| Demo API / Swagger | http://localhost:8801/swagger-ui.html |
| Qualitest environment `baseUrl` | **`http://localhost:8801`** |

In Qualitest, open **Project → Environments** and set the target URL to `http://localhost:8801`. A **200** from the debug console means the two apps are wired.

Stop a process with `Ctrl+C` in its terminal.

---

## B. Docker one-click

Requires **Git** and **Docker Desktop already running** (or Docker Engine + **Compose V2** on Linux). Default host ports: **5180 / 3306 / 6379**. The MySQL password is `MYSQL_ROOT_PASSWORD` in `.env` (default `qualitest`), separate from `123456` above.

Copy **only the block for your OS**. Use either the script (step 1) or plain Docker Compose (step 1b). If the repo is already cloned, start at the `cd` line or the `.env` line.

### 1. Start Qualitest

**Linux / macOS / Git Bash**

```bash
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
chmod +x scripts/quick-start.sh
./scripts/quick-start.sh
```

**Windows (PowerShell or Command Prompt)**

```bat
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
scripts\quick-start.bat
```

If GitHub is slow, use the read-only mirror (folder name stays `qualitest`):

```bash
git clone https://gitee.com/qualitest-hq/qualitest.git
```

The script copies `.env.example` to `.env` when missing, then pulls the Aliyun public images (no login). If that fails it tries GHCR, then `docker compose up -d --build`. If the pull hangs, press `Ctrl+C` and run:

```bash
docker compose up -d --build
```

### 1b. Plain Docker Compose (same result as the script)

Skip `quick-start` and paste this instead of step 1. The script copies `.env`, runs `docker compose pull app web` (Aliyun by default), then `docker compose up -d`. If the repo is already cloned, start at the `.env` line. An existing `.env` is left as-is. To use GHCR, add `QUALITEST_IMAGE_PREFIX=ghcr.io/qualitest-hq` to `.env`.

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

**Windows Command Prompt**

```bat
git clone https://github.com/qualitest-hq/qualitest.git
cd qualitest
if not exist .env copy /Y .env.example .env
docker compose pull app web
docker compose up -d
docker compose ps
curl.exe -fsS -D - -o NUL http://localhost:5180/healthz
```

If `pull app web` fails or hangs, press `Ctrl+C` and run this in the same directory (`up` still pulls MySQL / Redis when they are missing):

```bash
docker compose up -d --build
```

Use the same command after changing code or a Dockerfile. Open `http://localhost:5180` when the status line is `HTTP/1.1 200`.

### 2. Confirm you can sign in

After the script prints that the stack is up, `web` waits until the backend healthcheck passes. Open the browser after `qualitest-web` is Up and the response headers include `HTTP/1.1 200`.

**Linux / macOS / Git Bash**

```bash
docker compose ps
curl -fsS -D - -o /dev/null http://localhost:5180/healthz
```

**Windows (PowerShell or Command Prompt)**

```bat
docker compose ps
curl.exe -fsS -D - -o NUL http://localhost:5180/healthz
```

- Browser: `http://localhost:5180` (use the port you set in `WEB_PORT`)
- Built-in accounts (after Flyway including **V6**):
  - **`admin` / `admin123`**: super admin (**local / private only**; change before public exposure)
  - **`demo` / `demo123`**: demo visitor (V6 converges seed users; test management + AI only — change or disable in production)
- **Public demo hosts**: ops password is **`admin` / `QtDemo#Admin2026`**; visitors still use **`demo` / `demo123`**. Production login form is not prefilled; demo may optionally mount `config.js`. Local `.env.development` may prefill `admin`
- First boot: response header **`HTTP/1.1 200`** / Flyway migrate success in logs (empty database is migrated by Flyway; no full initdb dump)
- IDEA plugin server URL: Compose → **`http://localhost:5180/prod-api`**; local backend → **`http://localhost:8800`**

If the status line is not 200 yet:

```bash
docker compose logs -f app
```

### 3. When a port is already taken

The log shows `Bind for 0.0.0.0:3306`, `6379`, or `5180` failed. From the **qualitest** directory, paste the block for your OS (host ports become 6180 / 13306 / 16379; container ports stay the same):

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

Then open `http://localhost:6180`.

### 4. Optional: start the demo target

Open a **second terminal** and paste into a **different directory**. Each repo has its own Compose; default ports do not overlap.

**Linux / macOS / Git Bash**

```bash
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
chmod +x scripts/quick-start.sh
./scripts/quick-start.sh
```

**Windows (PowerShell or Command Prompt)**

```bat
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
scripts\quick-start.bat
```

Mirror: `https://gitee.com/qualitest-hq/qualitest-demo.git`.

To skip the script, paste one of these into a **different directory** (pick this or `quick-start`, not both).

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

**Windows Command Prompt**

```bat
git clone https://github.com/qualitest-hq/qualitest-demo.git
cd qualitest-demo
if not exist .env copy /Y .env.example .env
docker compose pull mysql app web
docker compose up -d
docker compose ps
curl.exe -fsS -D - -o NUL http://localhost:5181/
```

If `pull` fails, run `docker compose up -d --build` in `qualitest-demo`. `qualitest-demo-web` Up and `HTTP/1.1 200` from `http://localhost:5181/` means the demo UI is ready.

| Item | URL |
|------|-----|
| Qualitest Web | http://localhost:5180 |
| Demo UI | http://localhost:5181 (`admin` / `admin123`) |
| Demo API / Swagger | http://localhost:8801/swagger-ui.html |

When Qualitest runs in Compose, set the project environment `baseUrl` (Project → Environments) to:

```text
http://host.docker.internal:8801
```

`app` already has `extra_hosts: host.docker.internal:host-gateway`, so this URL works on Windows, macOS, and Linux. Save, send one demo request from the console, and a **200** means the two stacks are wired. If you changed demo `APP_PORT`, use that port here.

If demo host ports (5181 / 8801 / 3307 / 6380) are busy, edit `WEB_PORT` / `APP_PORT` / `MYSQL_PORT` / `REDIS_PORT` in `qualitest-demo/.env`, then `docker compose up -d`.

### 5. Stop

Run this in each repo directory. `down` keeps data; `down -v` wipes the database.

```bash
docker compose down
```

### Official images

Compose default (anonymous pull, no login):

| Image | Role |
|------|------|
| `registry.cn-hangzhou.aliyuncs.com/qualitest-hq/qualitest-app` | Backend (Spring Boot) |
| `registry.cn-hangzhou.aliyuncs.com/qualitest-hq/qualitest-web` | Frontend (Nginx + SPA; `/prod-api` → app) |

GHCR copies: `ghcr.io/qualitest-hq/qualitest-app|web`. Packages: https://github.com/orgs/qualitest-hq/packages  

Switch to GHCR: `QUALITEST_IMAGE_PREFIX=ghcr.io/qualitest-hq` in `.env`. Do not `docker login` for the public Aliyun repos.

Pin a commit: `QUALITEST_IMAGE_TAG=sha-<short>` in `.env` (matches GHCR tags; Aliyun currently tracks `latest`). Compose still needs **MySQL + Redis**. `mysql:8.0` / `redis:7-alpine` still come from Docker Hub.

---

## Optional: demo target · two Compose stacks

Full paste blocks are in [step 4](#4-optional-start-the-demo-target) above. Each repo has its own Compose; default ports can run together.

| Item | URL |
|------|-----|
| Qualitest Web | http://localhost:5180 |
| Demo API / Swagger | http://localhost:8801 (`/swagger-ui.html`) |
| Demo UI | http://localhost:5181 |

**Environment `baseUrl` (Project → Environments)**

| Setup | Suggested `baseUrl` |
|-------|---------------------|
| Both as host processes (`mvn` / `pnpm`), or the browser hits host ports | **`http://localhost:8801`** |
| **Qualitest app inside Compose**, demo mapped on host **8801** | **`http://host.docker.internal:8801`** (`extra_hosts` is already in `docker-compose.yml`) |

Demo schema uses initdb dump + scenario seed — **no Flyway**. Qualitest migrations: see [Schema migration (Flyway)](#schema-migration-flyway).

---

## Host hot reload (MySQL / Redis still in Compose)

This is not the no-Docker path. Compose runs MySQL + Redis only; the backend and frontend stay on the host for **devtools / JRebel / Vite HMR**. For no Docker at all, use [A. No Docker](#a-no-docker).

```bash
# Linux / macOS
./scripts/dev-deps-up.sh
# Stop (keep volumes): ./scripts/dev-deps-down.sh

# Windows
scripts\dev-deps-up.bat
# Stop: scripts\dev-deps-down.bat

# Equivalent
# docker compose up -d mysql redis
```

Then on the host:

```bash
# Backend (profile=dev → localhost:3306 / 6379)
mvn -pl qualitest-admin -am -DskipTests package
# Start via qualitest.bat / qualitest.sh or spring-boot:run

# Frontend
cd qualitest-ui && pnpm install && pnpm dev
```

Browser: `http://localhost:5180`. MySQL only needs empty DB `qualitest` (Compose `mysql` creates it). On backend start, **Flyway** runs `db/migration` (including seed). Login once migrate succeeds in logs.

> **JRebel:** do not combine with `spring-boot-devtools` restart. Set `spring.devtools.restart.enabled=false` (or drop the optional dependency) when using JRebel.

---

## Ports

| Item | Compose full stack | Local dev | Notes |
|------|--------------------|-----------|-------|
| Qualitest Web | **`WEB_PORT` → default 5180** (Nginx in container also **5180**) | Vite **5180** | Compose serves static via Nginx |
| Qualitest API | **8800** in container (usually not published) | **8800** | Browser uses Nginx **`/prod-api`**; plugin Compose URL `http://localhost:5180/prod-api` |
| MySQL | **`MYSQL_PORT` → 3306** | Host or same | DB name `qualitest` |
| Redis | **`REDIS_PORT` → 6379** | Host or same | Compose app uses DB `0`; local `.env.example` often `10` |
| Demo API | **8801** | Same | **Separate repo** Compose; not mixed here |
| Demo UI | **5181** | See demo docs | Demo MySQL/Redis default **3307 / 6380** |

### Change ports (minimal)

Copy [`.env.example`](../.env.example) to `.env` (do not commit), e.g.:

```env
WEB_PORT=6180
MYSQL_PORT=33066
REDIS_PORT=63790
```

Richer local overrides (mounts, expose `app:8800`, log level, …):

```bash
# Windows
copy docker-compose.override.yml.example docker-compose.override.yml
# Linux / macOS
cp docker-compose.override.yml.example docker-compose.override.yml
```

Uncomment what you need, then `docker compose up -d`. Real `docker-compose.override.yml` is **gitignored** — do not commit secrets. Template: [`docker-compose.override.yml.example`](../docker-compose.override.yml.example).

To hit the backend from the host: uncomment `app.ports: "8800:8800"` in the override (do not commit edits to `docker-compose.yml`).

---

## Kubernetes / Helm (optional · community-tested)

> **Status:** Chart skeleton lives at `deploy/helm/qualitest`. **Maintainers have not run end-to-end verification on a real cluster.** Install, troubleshoot, and harden yourself; Issues welcome.  
> Prefer **Compose** for day-to-day trials. This Chart is for “we already have Kubernetes / private-cloud must land in-cluster”.

### Layout

```text
deploy/helm/qualitest/
├── Chart.yaml
├── values.yaml
└── templates/          # app / web / optional mysql·redis / Ingress / Secret
```

### Images

Chart defaults to GHCR (`ghcr.io/qualitest-hq/qualitest-app` / `qualitest-web`). If the cluster can reach ghcr.io, install directly; otherwise retarget `image.*.repository` or build locally and `kind load`:

```bash
docker compose build app web
# kind load docker-image ghcr.io/qualitest-hq/qualitest-app:latest
# kind load docker-image ghcr.io/qualitest-hq/qualitest-web:latest
```

### Install

```bash
helm upgrade --install qualitest ./deploy/helm/qualitest \
  --namespace qualitest --create-namespace \
  --set secrets.tokenSecret='<strong-random-32+>' \
  --set secrets.mysqlRootPassword='<strong-password>'
```

Without Ingress:

```bash
kubectl -n qualitest port-forward svc/qualitest-web 5180:5180
# http://127.0.0.1:5180 — default admin / admin123 (change immediately)
# Service name follows Release: <release>-web (example Release name: qualitest)
```

| Need | Example |
|------|---------|
| Ingress | `--set ingress.enabled=true --set ingress.hosts[0].host=qualitest.example.com` |
| External MySQL/Redis | `--set mysql.enabled=false --set redis.enabled=false` + `app.external.*` |
| Custom registry | `--set image.app.repository=ghcr.io/you/qualitest-app ...` |

Bundled MySQL/Redis are **PoC only**. See [`values.yaml`](../deploy/helm/qualitest/values.yaml) and post-install NOTES.

---

## Environment variables

Authoritative list: [`.env.example`](../.env.example). Compose injects some into `app`; local `dev` / `prod` can override by the same names.

| Variable | Typical use | Notes |
|----------|-------------|-------|
| `WEB_PORT` / `MYSQL_PORT` / `REDIS_PORT` | Host port maps | Compose only |
| `MYSQL_ROOT_PASSWORD` | MySQL root; also Compose datasource password | **Change in production**; default `qualitest` is local-only |
| `TOKEN_SECRET` | JWT signing key | **Strong random string in production** |
| `TOKEN_EXPIRE_TIME` | Token TTL (minutes) | Optional |
| `SERVER_PORT` | Backend listen port | Fixed 8800 in Compose |
| `QUALITEST_PROFILE` | Upload directory | Compose / docker profile default `/data/upload` (volume `upload_data`) |
| `SPRING_DATASOURCE_DRUID_MASTER_*` | JDBC URL / user / password | Compose points at service `mysql`; local → localhost |
| `SPRING_DATA_REDIS_*` | Redis host / port / database / password | Compose host=`redis` |
| `LOGGING_LEVEL_COM_QUALITEST` | App log level | Default `info` |
| `QUALITEST_IMAGE_PREFIX` | Compose image registry prefix | Default Aliyun `registry.cn-hangzhou.aliyuncs.com/qualitest-hq`; GHCR: `ghcr.io/qualitest-hq` |
| `QUALITEST_IMAGE_TAG` | Compose / local image tag | Default `latest` |
| `DRUID_STAT_USERNAME` / `DRUID_STAT_PASSWORD` | Druid console (**dev** only) | docker / prod **disable** console — never expose `dev` publicly |

---

## Architecture

```text
┌─────────────┐     /prod-api      ┌──────────────────┐
│   nginx     │ ─────────────────► │ qualitest-admin  │
│  (static)   │                    │   (8800)         │
└─────────────┘                    └────────┬─────────┘
                                            │
                                   ┌────────┴────────┐
                                   │ mysql │ redis  │
                                   └─────────────────┘
```

| Service | Container | Role |
|---------|-----------|------|
| mysql | qualitest-mysql | Empty DB `MYSQL_DATABASE=qualitest`; schema + seed via Flyway on app start |
| redis | qualitest-redis | Cache / session |
| app | qualitest-app | Spring Boot, `SPRING_PROFILES_ACTIVE=docker`, upload `/data/upload`; Flyway migrate on start |
| web | qualitest-web | Nginx static + `/prod-api` → `app:8800` |

---

## Production hardening

Defaults are for local demos — **do not** ship them to the public internet or production as-is.

1. **Secrets**  
   - Strong random `TOKEN_SECRET`  
   - Change `MYSQL_ROOT_PASSWORD` (and local datasource passwords)  
   - Change seed user `admin` / `admin123` immediately after first login; in production also **change or disable** built-in demo user `demo` / `demo123`  
   - If Redis is reachable from outside, set `SPRING_DATA_REDIS_PASSWORD` and align Compose  

2. **Profiles**  
   - Compose uses **`docker`**: Druid **statViewServlet / webStatFilter off**; upload `/data/upload`  
   - Non-Compose production: **`prod`**, keep Druid console off; **never** expose `dev` (default Druid credentials) publicly  

3. **HTTPS**  
   - This Compose stack is **HTTP only** by default (`WEB_PORT` → container **5180**)  
   - Terminate TLS on a reverse proxy (Nginx / Caddy / cloud LB) to `http://127.0.0.1:${WEB_PORT}`; or use your own Nginx with `deploy/nginx/default.conf` as upstream reference  
   - Do not bake certs/keys into images or commit them  

4. **Network & data**  
   - Prefer exposing only the Web port; keep MySQL / Redis on the container network  
   - Data lives in volumes `mysql_data` / `redis_data` / `upload_data` — back up and migrate yourself  
   - Never commit `.env`, `application-local.yml`, real cloud API keys, MCP / LLM secrets  

See also [SECURITY.md](../SECURITY.md).

---

## Common commands

Full from-zero paste: [Plain Docker Compose](#1b-plain-docker-compose-same-result-as-the-script). Once you are already in the repo and images are present:

```bash
docker compose logs -f app
docker compose ps
docker compose down          # keep volumes
docker compose down -v       # wipe MySQL / Redis / upload (destructive)
docker compose pull app web  # Aliyun by default; MySQL / Redis are pulled by up when missing
docker compose up -d         # start with existing / pulled images
docker compose up -d --build # rebuild after code / Dockerfile changes, or when prebuilt pull fails
```

---

## Troubleshooting

| Symptom | Try |
|---------|-----|
| `Bind for 0.0.0.0:5180 failed` | Change `WEB_PORT` / `MYSQL_PORT` / `REDIS_PORT` in `.env`, then `up` again |
| Slow first start / build failure | Check Docker resources & network; `docker compose build --no-cache app` (or `web`) |
| Page down but containers up | `docker compose ps`; `logs -f web` / `logs -f app`; confirm `WEB_PORT` |
| Login fail / 401 | Seed account? After changing `TOKEN_SECRET`, re-login; check `app` logs |
| Empty DB, no tables / login fail | Flyway migrate success in `app` logs? `spring.flyway.enabled=true` and **url/user/password match Druid master** (Qualitest does not use default `spring.datasource`) |
| `Found non-empty schema without metadata` | See [existing DB baseline](#existing-databases-local-data--future-production); do not run full V1 on a filled DB |
| `Checksum mismatch` | An already-applied migration file was edited — restore it; fix forward with a new `V{n}` |
| Old volume still wrong after migration edits | If data is disposable: `down -v` then `up`; in production only add incremental `V{n}` |
| Plugin can’t connect | Compose: `http://localhost:5180/prod-api`; local: `http://localhost:8800` — don’t mix |
| Port clash with demo | Demo defaults 8801/5181/3307/6380; re-check if you remapped this repo |
| Debug / flow can’t reach demo | Demo started separately? In Compose, set environment `baseUrl` to `http://host.docker.internal:8801` (see [step 4](#4-optional-start-the-demo-target)) |

---

## GitHub Pages landing

Source: [`site/`](../site/). Live: `https://qualitest-hq.github.io/qualitest/`. Local / CI notes: [`site/README.md`](../site/README.md).

Local: `cd site && pnpm install && pnpm build && pnpm preview` → `http://127.0.0.1:4321/qualitest/`.

---

## Related files

- [`site/`](../site/) (GitHub Pages landing)
- [`deploy/helm/qualitest/`](../deploy/helm/qualitest/) (Helm Chart; community-tested)
- [`docker-compose.yml`](../docker-compose.yml)
- [`docker-compose.override.yml.example`](../docker-compose.override.yml.example) (local YAML override template; real override is gitignored)
- [`Dockerfile`](../Dockerfile) (backend)
- [`deploy/docker/Dockerfile.web`](../deploy/docker/Dockerfile.web) (frontend)
- [`deploy/nginx/default.conf`](../deploy/nginx/default.conf)
- [`.env.example`](../.env.example)
- [`application-docker.yml`](../qualitest-admin/src/main/resources/application-docker.yml)
- Migrations: [`qualitest-admin/.../db/migration/`](../qualitest-admin/src/main/resources/db/migration/)
- Demo deploy: [qualitest-demo/docs/deploy.md](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/deploy.md)

---

## Schema migration (Flyway)

**This repo only.** Empty DB → app starts → automatic migrate. Later schema changes = new incremental scripts under `qualitest-admin/src/main/resources/db/migration/`.

| Rule | Detail |
|------|--------|
| Naming | `V{n}__short_desc.sql` (double underscore), e.g. `V2__add_api_group_index.sql` |
| Empty DB | Compose creates empty `qualitest` → app runs `V1`…`Vn` |
| New features | **Only add** new `V{n}`; never edit applied files; **do not** use a full local mysqldump as the upgrade path |
| Config | With Druid master, set explicit `spring.flyway.url` / `user` / `password` (see `application-*.yml`) |
| Production | No `clean`; roll forward with a new version or restore from backup (Community has no auto down) |

Current version:

```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### Existing databases (local data / future production)

**Do not** run full `V1__baseline.sql` on a DB that already has business data (`DROP` / duplicate `CREATE`).

1. Backup (`mysqldump`, etc. — **do not commit** dump files)
2. Confirm schema ≈ what V1 describes
3. Temporarily set `spring.flyway.baseline-on-migrate: true` (`baseline-version: 1` is configured)
4. Start once → `flyway_schema_history` gets baseline version **1** without executing V1 body
5. Set `baseline-on-migrate: false` again
6. Upgrade only via new `V2`, `V3`, …

**All new changes go into migrations.** Keep local full dumps / ad-hoc upgrade scripts outside the repo.
