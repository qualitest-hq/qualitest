#!/usr/bin/env bash
# 质衡一键启动：Docker Compose 全栈
#
# 用法：
#   ./scripts/quick-start.sh           # 优先拉 GHCR，失败则本地构建
#   ./scripts/quick-start.sh -h        # 显示本说明
#
# 说明：从任意目录调用即可（脚本会切到仓库根）；依赖 Docker Engine/Desktop + Compose V2
# 靶场 / RustFS：见独立仓 qualitest-demo
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/quick-start.sh           启动全栈（优先 GHCR 镜像；失败则 --build）
  ./scripts/quick-start.sh -h        显示本说明

仅依赖:     ./scripts/dev-deps-up.sh （停：./scripts/dev-deps-down.sh）
停止全栈:   docker compose down
本地重建:   docker compose up -d --build
靶场/RustFS: 独立仓 qualitest-demo（另起 Compose；本仓无 --profile demo）
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
    echo "[info] 已从 .env.example 生成 .env（请按需修改 MYSQL_ROOT_PASSWORD / TOKEN_SECRET）"
  fi
fi

WEB_PORT=80
if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  WEB_PORT="$(grep -E '^WEB_PORT=' .env | tail -n1 | cut -d= -f2- || true)"
  WEB_PORT="${WEB_PORT:-80}"
fi

echo "[info] 拉取 GHCR 预构建镜像（ghcr.io/qualitest-hq/qualitest-app|web）..."
if docker compose pull app web; then
  echo "[info] 启动 MySQL + Redis + 后端 + Nginx ..."
  docker compose up -d
else
  echo "[warn] pull 失败（镜像未发布 / 网络），改为本地构建 ..."
  docker compose up -d --build
fi

echo
echo "=============================================="
echo " 质衡已启动（请稍等后端健康 / Flyway 完成后再登录）"
if [[ "${WEB_PORT}" == "80" ]]; then
  echo " 浏览器打开: http://localhost"
else
  echo " 浏览器打开: http://localhost:${WEB_PORT}"
fi
echo " 默认账号:   admin / admin123"
echo " 停止:       docker compose down"
echo " 仅依赖:     ./scripts/dev-deps-up.sh"
echo " 靶场/RustFS: 见 qualitest-demo（另起 Compose；本仓无 --profile demo）"
echo "=============================================="
