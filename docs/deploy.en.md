# Deployment

Ports, env vars, and production hardening for Compose full stack and local development. Quick start: [README.en.md](../README.en.md). Security disclosure: [SECURITY.md](../SECURITY.md).

**The demo target is not in this repo’s Compose** (no `--profile demo` mixed stack). To run the shop demo, clone [qualitest-demo](https://github.com/qualitest-hq/qualitest-demo) and start it with its own [docs/deploy.md](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/deploy.md) / `quick-start`. Most users only need this repo to try Qualitest.

中文版：[deploy.md](./deploy.md)

---

## One-command full stack (recommended)

Requires Docker Desktop / Engine + Compose V2. Default host ports: **80 / 3306 / 6379** (override via `.env`).

```bash
# Linux / macOS
chmod +x scripts/quick-start.sh
./scripts/quick-start.sh

# Windows
scripts\quick-start.bat

# Or manually (copy .env.example first if needed)
# cp .env.example .env   # Windows: copy .env.example .env
docker compose up -d --build
```

- Browser: **http://localhost** (include port if `WEB_PORT` ≠ 80)
- Default login: **`admin` / `admin123`** (Flyway V1 seed — change before public exposure)
- First boot: wait for **app healthy / Flyway migrate success** in logs (no full initdb dump)
- IDEA plugin server URL: Compose → **`http://localhost/prod-api`**; local backend → **`http://localhost:8080`**

First `--build` pulls base images and compiles front/back — expect a longer wait.

---

## Optional: demo target · two Compose stacks

Qualitest and demo each have their own Compose; ports are offset so they can run together:

```bash
# Terminal 1 — Qualitest (this repo)
cd qualitest && ./scripts/quick-start.sh   # Windows: scripts\quick-start.bat

# Terminal 2 — demo (after separate clone)
cd qualitest-demo && ./scripts/quick-start.sh
```

| Item | URL |
|------|-----|
| Qualitest Web | http://localhost |
| Demo API / Swagger | http://localhost:8081 (`/swagger-ui.html`) |
| Demo UI | http://localhost:8082 |

**Environment `baseUrl` (Project → Environments)**

| Setup | Suggested `baseUrl` |
|-------|---------------------|
| Both as host processes (`mvn` / `yarn`), or browser hits host ports | Seed default **`http://localhost:8081`** |
| **Qualitest app inside Compose**, demo mapped on host **8081** | Container `localhost` cannot reach the demo — use **`http://host.docker.internal:8081`** (Docker Desktop: Windows / macOS). On Linux add `extra_hosts: ["host.docker.internal:host-gateway"]`, or run Qualitest backend on the host |

Demo schema uses initdb dump + scenario seed — **no Flyway**. Qualitest migrations: see [Schema migration (Flyway)](#schema-migration-flyway).

---

## Dependencies only (local development · hot reload)

Compose runs MySQL + Redis only; run backend / frontend on the host for **devtools / JRebel / Vite HMR**.

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
cd qualitest-ui && yarn install && yarn dev
```

Browser: **http://localhost:5173**. MySQL only needs empty DB `qualitest` (Compose `mysql` creates it). On backend start, **Flyway** runs `db/migration` (including seed). Login once migrate succeeds in logs.

> **JRebel:** do not combine with `spring-boot-devtools` restart. Set `spring.devtools.restart.enabled=false` (or drop the optional dependency) when using JRebel.

---

## Ports

| Item | Compose full stack | Local dev | Notes |
|------|--------------------|-----------|-------|
| Qualitest Web | **`WEB_PORT` → default 80** | Vite **5173** | Compose serves static via Nginx |
| Qualitest API | **8080** in container (usually not published) | **8080** | Browser uses Nginx **`/prod-api`**; plugin Compose URL `http://localhost/prod-api` |
| MySQL | **`MYSQL_PORT` → 3306** | Host or same | DB name `qualitest` |
| Redis | **`REDIS_PORT` → 6379** | Host or same | Compose app uses DB `0`; local `.env.example` often `10` |
| Demo API | **8081** | Same | **Separate repo** Compose; not mixed here |
| Demo UI | **8082** | See demo docs | Demo MySQL/Redis default **3307 / 6380** |

### Change ports (minimal)

Copy [`.env.example`](../.env.example) to `.env` (do not commit), e.g.:

```env
WEB_PORT=8088
MYSQL_PORT=33066
REDIS_PORT=63790
```

Richer local overrides: `docker-compose.override.yml` (never commit secrets).

To hit the backend from the host while debugging, uncomment `ports: "8080:8080"` on `app` in `docker-compose.yml` and recreate.

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

Default image names match Compose (`qualitest-hq/qualitest-app` / `qualitest-web`). Build and push (or `kind load`) before install:

```bash
docker compose build app web
# kind load docker-image qualitest-hq/qualitest-app:latest
# kind load docker-image qualitest-hq/qualitest-web:latest
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
kubectl -n qualitest port-forward svc/qualitest-web 8080:80
# http://127.0.0.1:8080 — default admin / admin123 (change immediately)
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
| `SERVER_PORT` | Backend listen port | Fixed 8080 in Compose |
| `QUALITEST_PROFILE` | Upload directory | Compose / docker profile default `/data/upload` (volume `upload_data`) |
| `SPRING_DATASOURCE_DRUID_MASTER_*` | JDBC URL / user / password | Compose points at service `mysql`; local → localhost |
| `SPRING_DATA_REDIS_*` | Redis host / port / database / password | Compose host=`redis` |
| `LOGGING_LEVEL_COM_QUALITEST` | App log level | Default `info` |
| `QUALITEST_IMAGE_TAG` | Local image tag | Default `latest` |
| `DRUID_STAT_USERNAME` / `DRUID_STAT_PASSWORD` | Druid console (**dev** only) | docker / prod **disable** console — never expose `dev` publicly |

---

## Architecture

```text
┌─────────────┐     /prod-api      ┌──────────────────┐
│   nginx     │ ─────────────────► │ qualitest-admin  │
│  (static)   │                    │   (8080)         │
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
| web | qualitest-web | Nginx static + `/prod-api` → `app:8080` |

---

## Production hardening

Defaults are for local demos — **do not** ship them to the public internet or production as-is.

1. **Secrets**  
   - Strong random `TOKEN_SECRET`  
   - Change `MYSQL_ROOT_PASSWORD` (and local datasource passwords)  
   - Change seed user `admin` / `admin123` immediately after first login  
   - If Redis is reachable from outside, set `SPRING_DATA_REDIS_PASSWORD` and align Compose  

2. **Profiles**  
   - Compose uses **`docker`**: Druid **statViewServlet / webStatFilter off**; upload `/data/upload`  
   - Non-Compose production: **`prod`**, keep Druid console off; **never** expose `dev` (default Druid credentials) publicly  

3. **HTTPS**  
   - This Compose stack is **HTTP only** by default (`WEB_PORT` → container 80)  
   - Terminate TLS on a reverse proxy (Nginx / Caddy / cloud LB) to `http://127.0.0.1:${WEB_PORT}`; or use your own Nginx with `deploy/nginx/default.conf` as upstream reference  
   - Do not bake certs/keys into images or commit them  

4. **Network & data**  
   - Prefer exposing only the Web port; keep MySQL / Redis on the container network  
   - Data lives in volumes `mysql_data` / `redis_data` / `upload_data` — back up and migrate yourself  
   - Never commit `.env`, `application-local.yml`, real cloud API keys, MCP / LLM secrets  

See also [SECURITY.md](../SECURITY.md).

---

## Common commands

```bash
docker compose logs -f app
docker compose ps
docker compose down          # keep volumes
docker compose down -v       # wipe MySQL / Redis / upload (destructive)
docker compose up -d --build # rebuild after code / Dockerfile changes
```

---

## Troubleshooting

| Symptom | Try |
|---------|-----|
| `Bind for 0.0.0.0:80 failed` | Change `WEB_PORT` / `MYSQL_PORT` / `REDIS_PORT` in `.env`, then `up` again |
| Slow first start / build failure | Check Docker resources & network; `docker compose build --no-cache app` (or `web`) |
| Page down but containers up | `docker compose ps`; `logs -f web` / `logs -f app`; confirm `WEB_PORT` |
| Login fail / 401 | Seed account? After changing `TOKEN_SECRET`, re-login; check `app` logs |
| Empty DB, no tables / login fail | Flyway migrate success in `app` logs? `spring.flyway.enabled=true` and **url/user/password match Druid master** (Qualitest does not use default `spring.datasource`) |
| `Found non-empty schema without metadata` | See [existing DB baseline](#existing-databases-local-data--future-production); do not run full V1 on a filled DB |
| `Checksum mismatch` | An already-applied migration file was edited — restore it; fix forward with a new `V{n}` |
| Old volume still wrong after migration edits | If data is disposable: `down -v` then `up`; in production only add incremental `V{n}` |
| Plugin can’t connect | Compose: `http://localhost/prod-api`; local: `http://localhost:8080` — don’t mix |
| Port clash with demo | Demo defaults 8081/8082/3307/6380; re-check if you remapped this repo |
| Debug / flow can’t reach demo | Demo started separately? Compose Qualitest app must not use `localhost:8081` — use `host.docker.internal:8081` (see [demo target](#optional-demo-target--two-compose-stacks)) |

---

## Related files

- [`deploy/helm/qualitest/`](../deploy/helm/qualitest/) (Helm Chart; community-tested)
- [`docker-compose.yml`](../docker-compose.yml)
- [`Dockerfile`](../Dockerfile) (backend)
- [`deploy/docker/Dockerfile.web`](../deploy/docker/Dockerfile.web) (frontend)
- [`deploy/nginx/default.conf`](../deploy/nginx/default.conf)
- [`.env.example`](../.env.example)
- [`application-docker.yml`](../qualitest-admin/src/main/resources/application-docker.yml)
- Migrations: [`qualitest-admin/.../db/migration/`](../qualitest-admin/src/main/resources/db/migration/)
- Demo deploy: [qualitest-demo/docs/deploy.md](https://github.com/qualitest-hq/qualitest-demo/blob/main/docs/deploy.md)
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
| New features | **Only add** new `V{n}`; never edit applied files; never treat root `sql/qualitest_*.sql` as the upgrade path |
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

1. Backup (`sql/backup_db.bat` or mysqldump)
2. Confirm schema ≈ what V1 describes
3. Temporarily set `spring.flyway.baseline-on-migrate: true` (`baseline-version: 1` is configured)
4. Start once → `flyway_schema_history` gets baseline version **1** without executing V1 body
5. Set `baseline-on-migrate: false` again
6. Upgrade only via new `V2`, `V3`, …

Root `sql/qualitest_*.sql` may still serve as disaster / offline export material; **all new changes go into migrations.**
