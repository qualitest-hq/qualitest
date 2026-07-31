#!/usr/bin/env bash
# 质衡开发依赖：停止 MySQL + Redis（保留数据卷）
#
# 用法：
#   ./scripts/dev-deps-down.sh           # stop mysql redis
#   ./scripts/dev-deps-down.sh -v        # 同上并删除 mysql/redis 容器（仍保留卷）
#   ./scripts/dev-deps-down.sh -h
#
# 清空数据卷请用: docker compose down -v（会波及全栈卷，慎用）
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/dev-deps-down.sh           停止 MySQL + Redis（保留容器与数据卷）
  ./scripts/dev-deps-down.sh -v        停止并删除 mysql/redis 容器（仍保留数据卷）
  ./scripts/dev-deps-down.sh -h        显示本说明

启动依赖:   ./scripts/dev-deps-up.sh
清空全部卷: docker compose down -v   # 慎用，等于重装库
EOF
}

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REMOVE_CONTAINERS=0
case "${1:-}" in
  -h|--help|help)
    usage
    exit 0
    ;;
  -v|--rm|--remove)
    REMOVE_CONTAINERS=1
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
  echo "[error] 未找到 docker"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "[error] 需要 Docker Compose V2（docker compose）"
  exit 1
fi

if docker compose ps --status running --services 2>/dev/null | grep -qE '^(app|web)$'; then
  echo "[warn] app/web 仍在运行；停掉 mysql/redis 后全栈将不可用。建议先: docker compose stop app web"
fi

if [[ "$REMOVE_CONTAINERS" -eq 1 ]]; then
  echo "[info] 停止并删除 mysql / redis 容器（数据卷保留）..."
  docker compose rm -sf mysql redis
else
  echo "[info] 停止 mysql / redis ..."
  docker compose stop mysql redis
fi

echo "[info] 完成。数据在卷 mysql_data / redis_data 中；再次启动: ./scripts/dev-deps-up.sh"
