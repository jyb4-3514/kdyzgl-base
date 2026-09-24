#!/usr/bin/env bash
# 宝塔 HIDS 日志轮转：只保留近 KEEP_DAYS 日，防止系统盘被入侵检测日志吃满。
# 背景：宝塔入侵检测每天写一个 JSON 日志（实测单日 240M+），无内置保留策略，
#      实测 19 天累积 4.7G 并持续增长，会挤占系统盘。
# 用法：直接执行即为一次轮转；由 /etc/cron.d/hids-log-rotate 每周触发一次（每 7 日）。
# TODO(扩展): 若宝塔面板后续提供「日志保留天数」配置项，改为面板侧配置，撤销本脚本与 cron。
set -euo pipefail

LOG_DIR="${HIDS_LOG_DIR:-/www/server/panel/data/hids_data/log}"
KEEP_DAYS="${HIDS_KEEP_DAYS:-7}"

# 目录不存在（宝塔未装/已卸载）时静默退出，不报错
[ -d "$LOG_DIR" ] || exit 0

# 以文件名内的日期为准（宝塔按天命名 YYYY-MM-DD.json），比 mtime 更可靠：
# 归档/恢复操作会改写 mtime，但文件名日期不变。
cutoff="$(date -d "$((KEEP_DAYS - 1)) days ago" +%Y-%m-%d)"
removed=0

for f in "$LOG_DIR"/*.json; do
  [ -e "$f" ] || continue
  base="$(basename "$f" .json)"
  # 非日期命名的文件一律不动，避免误删宝塔自有文件
  case "$base" in
    [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
    *) continue ;;
  esac
  if [[ "$base" < "$cutoff" ]]; then
    rm -f "$f"
    removed=$((removed + 1))
  fi
done

echo "[$(date '+%F %T')] hids-log-rotate: 保留日期 >= $cutoff，本次删除 $removed 个历史日志"