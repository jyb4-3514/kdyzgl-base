#!/usr/bin/env bash
# 三端演示 Demo · 测试环境一键部署（幂等）
#
# 用法（在 hrm-dev/deploy/docker-demo 目录下执行）：
#   ./deploy-demo.sh up        构建并启动（先校验 .env 与 dist）
#   ./deploy-demo.sh status    查看容器状态并做回环自验
#   ./deploy-demo.sh rollback  停掉并删除本服务容器（不影响现网任何容器）
#
# 回滚边界（重要）：
#   本脚本只能回滚「本服务」；现网 Nginx 那一处 location 改动由人工按
#   hrm-dev/docs/demo-docker-deploy.md 的回滚步骤执行（脚本故意不碰现网配置）。
set -euo pipefail

cd "$(dirname "$0")"

HOST_PORT="$(grep -E '^HOST_PORT=' .env 2>/dev/null | cut -d= -f2 || true)"
HOST_PORT="${HOST_PORT:-8090}"

require_env() {
  if [ ! -f .env ]; then
    echo "[错误] 缺少 .env —— 请复制 .env.example 为 .env 并填写真实值（勿入库）" >&2
    exit 1
  fi
  # compose 的 ${VAR:?} 已能拦缺失，这里提前给出人话提示
  for k in BASIC_AUTH_USER BASIC_AUTH_HASH; do
    if ! grep -qE "^${k}=." .env; then
      echo "[错误] .env 缺少 ${k}" >&2
      exit 1
    fi
  done
  if grep -qE '^BASIC_AUTH_HASH=.*[^$]change_me' .env; then
    echo "[错误] .env 里 BASIC_AUTH_HASH 仍是模板占位值" >&2
    exit 1
  fi
}

require_dist() {
  if [ ! -f dist/index.html ] || [ ! -f dist/pc.html ] || [ ! -f dist/mobile.html ]; then
    echo "[错误] dist 不是完整的三入口产物（需 index.html / pc.html / mobile.html）" >&2
    echo "       本地执行：npm run build --mode demo，再上传 dist/" >&2
    exit 1
  fi
}

case "${1:-up}" in
  up)
    require_env
    require_dist
    docker compose up -d --build
    echo "--- 等待健康检查 ---"
    for i in $(seq 1 30); do
      code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${HOST_PORT}/healthz" || true)"
      [ "$code" = "200" ] && break
      sleep 2
    done
    docker compose ps
    echo "--- 自验 ---"
    echo "healthz(应 200)       : $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${HOST_PORT}/healthz")"
    echo "根路径(应 401 未鉴权)  : $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${HOST_PORT}/")"
    echo "缺失资源(应 404)       : $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${HOST_PORT}/assets/not-exist-abcdefgh.js")"
    echo "带凭据的鉴权验证需用明文口令，脚本故意不保存明文 → 请人工执行："
    echo "  curl -u '$(grep -E '^BASIC_AUTH_USER=' .env | cut -d= -f2):<口令>' -o /dev/null -w '%{http_code}\\n' http://127.0.0.1:${HOST_PORT}/"
    ;;
  status)
    docker compose ps
    echo "healthz: $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${HOST_PORT}/healthz")"
    ;;
  rollback)
    docker compose down --remove-orphans
    echo "[完成] 本服务容器已删除；现网 courier-server 未受影响（可 docker compose ls 复核）"
    ;;
  *)
    echo "用法: $0 {up|status|rollback}" >&2
    exit 1
    ;;
esac
