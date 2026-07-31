#!/usr/bin/env bash
# 质衡开发依赖：仅启动 MySQL + Redis（本机 mvn / yarn 热更）
#
# 用法：
#   ./scripts/dev-deps-up.sh           # 启动并等待 healthy
#   ./scripts/dev-deps-up.sh -h        # 说明
#
# 停止：./scripts/dev-deps-down.sh
# 全栈一键：./scripts/quick-start.sh
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/dev-deps-up.sh           启动 MySQL + Redis（不启 app / web）
  ./scripts/dev-deps-up.sh -h        显示本说明

停止依赖:   ./scripts/dev-deps-down.sh
全栈一键:   ./scripts/quick-start.sh
说明:       docs/deploy.md「仅依赖（本机开发）」
EOF
}

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

case "${1:-}" in
  -h|--help|help)
    usage
    exit 0
    ;;
  "")
    ;;
  *)
    echo "[error] 未知参数: $1" >&2
    usage
    exit 1
    ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  echo "[error] 未找到 docker，请先安装 Docker Desktop / Docker Engine"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "[error] 需要 Docker Compose V2（docker compose）"
  exit 1
fi

if [[ ! -f .env ]]; then
  if [[ -f .env.example ]]; then
    cp .env.example .env
    echo "[info] 已从 .env.example 生成 .env（请按需修改 MYSQL_ROOT_PASSWORD）"
  fi
fi

# 若全栈容器在跑，提示仍可只起依赖；app/web 会继续依赖同一 MySQL/Redis 卷
if docker compose ps --status running --services 2>/dev/null | grep -qE '^(app|web)$'; then
  echo "[warn] 检测到 app/web 正在运行；本脚本只确保 mysql/redis up，不会停全栈"
fi

echo "[info] 启动 MySQL + Redis ..."
docker compose up -d mysql redis

echo "[info] 等待 mysql / redis healthy ..."
deadline=$((SECONDS + 120))
while true; do
  mysql_h="$(docker inspect -f '{{.State.Health.Status}}' qualitest-mysql 2>/dev/null || echo missing)"
  redis_h="$(docker inspect -f '{{.State.Health.Status}}' qualitest-redis 2>/dev/null || echo missing)"
  if [[ "$mysql_h" == "healthy" && "$redis_h" == "healthy" ]]; then
    break
  fi
  if (( SECONDS >= deadline )); then
    echo "[error] 等待超时（mysql=$mysql_h redis=$redis_h）。请执行: docker compose logs mysql redis" >&2
    exit 1
  fi
  sleep 2
done

MYSQL_PORT=3306
REDIS_PORT=6379
if [[ -f .env ]]; then
  _mp="$(grep -E '^MYSQL_PORT=' .env 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '\r' || true)"
  _rp="$(grep -E '^REDIS_PORT=' .env 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '\r' || true)"
  [[ -n "${_mp}" ]] && MYSQL_PORT="${_mp}"
  [[ -n "${_rp}" ]] && REDIS_PORT="${_rp}"
fi

echo
echo "=============================================="
echo " 开发依赖已就绪（MySQL + Redis）"
echo " MySQL:  localhost:${MYSQL_PORT}  库名 qualitest"
echo " Redis:  localhost:${REDIS_PORT}"
echo
echo " 本机后端（profile=dev，Flyway 会自动迁库）:"
echo "   mvn -pl qualitest-admin -am -DskipTests package"
echo "   再 qualitest.bat / qualitest.sh 或 spring-boot:run"
echo " 本机前端:"
echo "   cd qualitest-ui && yarn install && yarn dev"
echo "   浏览器 http://localhost:5173"
echo
echo " 默认账号: admin / admin123（首启后端迁库后）"
echo " 停止依赖: ./scripts/dev-deps-down.sh"
echo " 注意:    用 JRebel 时请关掉 spring-boot-devtools restart"
echo "=============================================="
