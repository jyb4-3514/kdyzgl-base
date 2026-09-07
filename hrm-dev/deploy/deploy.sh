#!/usr/bin/env bash
# =========================================================================
# 快递驿站智汇系统 · 服务器端部署/更新/回滚脚本（TASK.md D03/D04 配套）
# ---------------------------------------------------------------------------
# 用途：
#   1. git pull 拉取指定分支（默认 main）；
#   2. 后端二选一：
#      - BACKEND_MODE=source（默认）：服务器 mvn clean package -DskipTests 打包；
#      - BACKEND_MODE=jar          ：使用预打包上传的 jar（本地打包 scp 上来）；
#   3. 前端二选一：
#      - FRONTEND_MODE=source（默认）：服务器 npm install/ci + npm run build；
#      - FRONTEND_MODE=dist         ：使用预构建上传的 dist 目录；
#   4. 替换运行 jar（先备份旧版到 backup/，保留最近 5 份）→ 重启服务 → 健康检查；
#   5. 前端 dist 同步到宝塔站点目录。
#
# 重启方式（RESTART_MODE）：
#   auto（默认）→ 检测到 systemd 单元 hrm-server.service 则用 systemd，否则 nohup；
#   systemd     → systemctl restart hrm-server（单元文件见 hrm-dev/deploy/hrm-server.service）；
#   nohup       → 脚本直接 nohup 拉起（PID 文件管理）；
#   bt          → 宝塔 Java 项目管理器托管：脚本只替换 jar，重启请在面板点击，
#                 此模式不做自动健康检查（见 docs/deploy.md 第 4 章）。
#
# 常用命令（在服务器上执行，root 身份）：
#   bash /www/wwwroot/kdyzgl-base/hrm-dev/deploy/deploy.sh                    # 标准部署（main 分支，源码打包）
#   DEPLOY_BRANCH=dev bash hrm-dev/deploy/deploy.sh                          # 联调环境拉 dev 分支
#   BACKEND_MODE=jar PREBUILT_JAR=/tmp/hrm-server-1.0.0.jar \
#     bash hrm-dev/deploy/deploy.sh --backend-only                           # 本地打包上传模式
#   FRONTEND_MODE=dist PREBUILT_DIST=/tmp/hrm-admin-dist \
#     bash hrm-dev/deploy/deploy.sh --frontend-only                          # 本地构建 dist 上传模式
#   bash hrm-dev/deploy/deploy.sh --rollback                                 # 回滚到上一版后端 jar
#
# 安全约束（项目规则红线）：
#   - 本脚本不硬编码任何密钥/密码，敏感项全部来自服务器磁盘上的
#     /www/wwwroot/hrm-server/config/application-prod.yml（.gitignore 已忽略，不入 Git）；
#   - 服务器代码只来源于 Git pull，脚本不修改任何业务源码；
#   - 运行目录 /www/wwwroot/hrm-server 不配置为任何 Nginx 站点根目录，
#     8080/3306/6379 仅本机访问（安全组只放行 22/80/443）。
#
# 幂等性：可重复执行；任一步失败立即中止（set -e），错误定位见 LOG_DIR。
# =========================================================================

set -euo pipefail

# ====================== 可配置变量（按服务器实际环境调整） ======================
APP_NAME="hrm-server"                # 与 hrm-server/pom.xml 的 artifactId 一致
APP_VERSION="1.0.0"                  # 与 pom.xml 的 version 一致，产物 jar 名据此推导
DEPLOY_BRANCH="${DEPLOY_BRANCH:-main}"          # 部署分支（main=生产，dev=联调）
BACKEND_MODE="${BACKEND_MODE:-source}"          # source | jar
PREBUILT_JAR="${PREBUILT_JAR:-}"                # BACKEND_MODE=jar 时预打包 jar 的绝对路径
FRONTEND_MODE="${FRONTEND_MODE:-source}"        # source | dist
PREBUILT_DIST="${PREBUILT_DIST:-}"              # FRONTEND_MODE=dist 时预构建 dist 目录的绝对路径
RESTART_MODE="${RESTART_MODE:-auto}"            # auto | systemd | nohup | bt
SYSTEMD_UNIT="hrm-server.service"
JAVA_BIN="${JAVA_BIN:-java}"                    # 宝塔 JDK 若不在 PATH，填 /www/server/java/.../bin/java
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8}"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"   # 国内镜像加速
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/api/v1/auth/login}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"         # 健康检查等待秒数（含 Flyway 首次建表时间）
KEEP_BACKUP=5                                   # 运行 jar 备份保留份数

# ---- 服务器目录规划（与 docs/deploy.md 第 0 章一致）----
RUNTIME_DIR="/www/wwwroot/hrm-server"          # 后端运行目录（非 Nginx 站点，勿对外暴露）
CONFIG_DIR="$RUNTIME_DIR/config"               # 外置 application-prod.yml 所在目录
LOG_DIR="$RUNTIME_DIR/logs"
BACKUP_DIR="$RUNTIME_DIR/backup"
RUNTIME_JAR="$RUNTIME_DIR/hrm-server.jar"      # 固定文件名：宝塔/systemd 配置始终指向它
PID_FILE="$RUNTIME_DIR/hrm-server.pid"         # nohup 模式 PID 文件
SITE_DIR="${SITE_DIR:-/www/wwwroot/hrm-admin}" # 宝塔前端站点根目录

# ---- 仓库定位：本脚本位于 <仓库根>/hrm-dev/deploy/，向上两级即仓库根 ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="${REPO_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
SERVER_SRC_DIR="$REPO_DIR/hrm-dev/hrm-server"   # 后端源码（pom.xml 所在）
ADMIN_SRC_DIR="$REPO_DIR/hrm-dev/hrm-admin"     # 前端源码（package.json 所在）
BUILT_JAR="$SERVER_SRC_DIR/target/${APP_NAME}-${APP_VERSION}.jar"

# ---- 运行期变量 ----
LOG_FILE=""          # 在 main() 中初始化为 LOG_DIR/deploy-时间戳.log
GIT_COMMIT=""        # 本次部署的 git 提交号（写入 RELEASE_INFO 便于追溯）
SKIP_BACKEND=0
SKIP_FRONTEND=0
ACTION="deploy"      # deploy | rollback
ROLLBACK_JAR=""      # --rollback=<文件名> 指定备份

# =========================================================================
# 基础函数（日志 / 终止）
# =========================================================================
_now() { date '+%F %T'; }

log() {
  local msg="[$(_now)] [INFO ] $*"
  echo "$msg"
  [[ -d "$LOG_DIR" ]] && echo "$msg" >> "$LOG_FILE" 2>/dev/null || true
}

warn() {
  local msg="[$(_now)] [WARN ] $*"
  echo "$msg"
  [[ -d "$LOG_DIR" ]] && echo "$msg" >> "$LOG_FILE" 2>/dev/null || true
}

die() {
  local msg="[$(_now)] [ERROR] $*"
  echo "$msg" >&2
  [[ -d "$LOG_DIR" ]] && echo "$msg" >> "$LOG_FILE" 2>/dev/null || true
  exit 1
}

usage() {
  cat <<'EOF'
用法：bash deploy.sh [选项]
选项：
  --branch <分支>       部署分支（默认 main；也可用环境变量 DEPLOY_BRANCH 指定）
  --mode <source|jar>  后端模式（默认 source=服务器 mvn 打包；jar=使用预打包 jar）
  --backend-only        只部署后端
  --frontend-only       只部署前端
  --rollback [文件名]   回滚后端到备份 jar（默认取最近一份；可用 --rollback=xxx.jar 指定）
  -h, --help            显示本帮助
环境变量（覆盖顶部默认值）：
  DEPLOY_BRANCH / BACKEND_MODE / PREBUILT_JAR / FRONTEND_MODE / PREBUILT_DIST
  RESTART_MODE(auto|systemd|nohup|bt) / SITE_DIR / JAVA_BIN / JAVA_OPTS
  NPM_REGISTRY / HEALTH_TIMEOUT / REPO_DIR
详见 docs/deploy.md 第 4 章。
EOF
}

# =========================================================================
# 步骤函数
# =========================================================================

preflight() {
  [[ $EUID -eq 0 ]] || die "请以 root 身份运行本脚本（需写 /www/wwwroot 与 systemctl 权限）"
  [[ -d "$REPO_DIR/.git" ]] || die "$REPO_DIR 不是 Git 仓库：请先按 docs/deploy.md 第 4.1 节完成 git clone"

  if [[ $SKIP_BACKEND -eq 0 ]]; then
    if [[ "$BACKEND_MODE" == "source" ]]; then
      command -v mvn >/dev/null 2>&1 \
        || die "未找到 mvn：请安装 Maven（见 docs/deploy.md 4.3），或改用 BACKEND_MODE=jar 模式"
    else
      [[ -n "$PREBUILT_JAR" ]] || die "BACKEND_MODE=jar 需同时指定 PREBUILT_JAR=<预打包jar绝对路径>"
      [[ -f "$PREBUILT_JAR" ]] || die "预打包 jar 不存在：$PREBUILT_JAR"
    fi
    command -v "$JAVA_BIN" >/dev/null 2>&1 || die "未找到 Java（$JAVA_BIN）：请先安装 JDK17"
  fi

  if [[ $SKIP_FRONTEND -eq 0 ]]; then
    if [[ "$FRONTEND_MODE" == "source" ]]; then
      command -v node >/dev/null 2>&1 \
        || die "未找到 node（Vite 6 需 Node >= 18）：宝塔 Node 版本管理器安装，或改用 FRONTEND_MODE=dist 模式"
      command -v npm >/dev/null 2>&1 || die "未找到 npm"
    else
      [[ -n "$PREBUILT_DIST" ]] || die "FRONTEND_MODE=dist 需同时指定 PREBUILT_DIST=<预构建dist目录>"
      [[ -f "$PREBUILT_DIST/index.html" ]] || die "预构建 dist 缺少 index.html：$PREBUILT_DIST"
    fi
    [[ -d "$SITE_DIR" ]] || die "站点目录不存在：$SITE_DIR（请先在宝塔创建站点，见 docs/deploy.md 5.2）"
  fi
}

# 校验外置配置：缺失或含未替换占位符时直接中止（防忘配导致启动失败难排查）
check_external_config() {
  local f="$CONFIG_DIR/application-prod.yml"
  [[ -f "$f" ]] || die "外置配置缺失：$f（按 docs/deploy.md 4.2 由 env.example 生成并替换所有占位符）"
  if grep -q 'change_me' "$f"; then
    grep -n 'change_me' "$f" | head -5
    die "外置配置仍含 change_me_* 占位符（上方即残留项），必须全部替换为真实值后再部署"
  fi
  log "外置配置检查通过：$f"
}

pull_code() {
  # 服务器代码只来源于 Git：本地有未提交改动时拒绝部署（防止覆盖来源不明的修改）
  if [[ -n "$(git -C "$REPO_DIR" status --porcelain 2>/dev/null)" ]]; then
    warn "仓库存在未提交的本地改动（服务器上不应有）："
    git -C "$REPO_DIR" status --porcelain | head -20 || true
    die "请先清理（git stash / git checkout .）后重试，保证服务器代码与远端一致"
  fi
  log "拉取代码：origin/$DEPLOY_BRANCH"
  git -C "$REPO_DIR" fetch origin --prune
  git -C "$REPO_DIR" checkout "$DEPLOY_BRANCH"
  git -C "$REPO_DIR" pull --ff-only origin "$DEPLOY_BRANCH"
  GIT_COMMIT="$(git -C "$REPO_DIR" rev-parse --short HEAD)"
  log "当前部署版本：$DEPLOY_BRANCH @ $GIT_COMMIT"
}

build_backend() {
  local mvn_log="$LOG_DIR/maven-$(date +%Y%m%d-%H%M%S).log"
  log "后端打包：cd hrm-server && mvn -B clean package -DskipTests（日志：$mvn_log）"
  if ( cd "$SERVER_SRC_DIR" && mvn -B clean package -DskipTests ) >> "$mvn_log" 2>&1; then
    log "Maven 打包完成"
  else
    tail -n 50 "$mvn_log" || true
    die "Maven 打包失败，完整日志：$mvn_log"
  fi
  [[ -f "$BUILT_JAR" ]] || die "打包产物缺失：$BUILT_JAR"
}

locate_backend_artifact() {
  if [[ "$BACKEND_MODE" == "source" ]]; then
    build_backend
    log "使用服务器打包产物：$BUILT_JAR"
  else
    BUILT_JAR="$PREBUILT_JAR"
    log "使用预上传 jar：$BUILT_JAR"
  fi
}

backup_runtime_jar() {
  if [[ -f "$RUNTIME_JAR" ]]; then
    local bak="$BACKUP_DIR/${APP_NAME}-$(date +%Y%m%d-%H%M%S).jar"
    cp -f "$RUNTIME_JAR" "$bak"
    log "旧版本已备份：$bak"
    # 仅保留最近 KEEP_BACKUP 份备份，控制磁盘占用
    ( ls -1t "$BACKUP_DIR"/${APP_NAME}-*.jar 2>/dev/null \
        | tail -n +$((KEEP_BACKUP + 1)) \
        | xargs -r rm -f ) || true
  else
    log "首次部署，无旧版本需要备份"
  fi
}

deploy_backend_jar() {
  backup_runtime_jar
  cp -f "$BUILT_JAR" "$RUNTIME_JAR"
  chmod 644 "$RUNTIME_JAR"
  log "运行 jar 已更新：$RUNTIME_JAR"
  # 记录版本追溯信息（回滚与审计用）
  {
    echo "deploy_time : $(_now)"
    echo "branch     : $DEPLOY_BRANCH"
    echo "commit     : ${GIT_COMMIT:-unknown}"
    echo "jar_source : $BACKEND_MODE"
  } > "$RUNTIME_DIR/RELEASE_INFO"
  chmod 644 "$RUNTIME_DIR/RELEASE_INFO"
}

resolve_restart_mode() {
  if [[ "$RESTART_MODE" == "auto" ]]; then
    if [[ -f "/etc/systemd/system/$SYSTEMD_UNIT" ]] \
       || systemctl list-unit-files 2>/dev/null | grep -q "^${SYSTEMD_UNIT}"; then
      RESTART_MODE="systemd"
    else
      RESTART_MODE="nohup"
    fi
    log "自动选择重启方式：$RESTART_MODE（显式指定请设置 RESTART_MODE=systemd|nohup|bt）"
  fi
}

stop_backend_nohup() {
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid="$(cat "$PID_FILE" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      log "停止旧进程（PID $pid）..."
      kill "$pid" 2>/dev/null || true
      local waited=0
      while kill -0 "$pid" 2>/dev/null && (( waited < 30 )); do
        sleep 1; waited=$((waited + 1))
      done
      if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid" 2>/dev/null || true
      fi
    fi
    rm -f "$PID_FILE"
  fi
  # 兜底清理：按运行 jar 路径匹配残留的 java 进程（不会误伤本脚本自身）
  pkill -f "java .*${RUNTIME_JAR}" 2>/dev/null || true
}

start_backend_nohup() {
  log "nohup 启动后端（控制台输出：$LOG_DIR/stdout.log）"
  # 注意：additional-location 以 / 结尾表示目录，Spring 会自动加载其中的
  # application-prod.yml（profile 由 --spring.profiles.active=prod 激活）
  nohup "$JAVA_BIN" $JAVA_OPTS -jar "$RUNTIME_JAR" \
    --spring.profiles.active=prod \
    --spring.config.additional-location="file:$CONFIG_DIR/" \
    >> "$LOG_DIR/stdout.log" 2>&1 &
  echo $! > "$PID_FILE"
  log "后端 PID：$(cat "$PID_FILE")"
}

restart_backend() {
  resolve_restart_mode
  case "$RESTART_MODE" in
    systemd)
      log "systemd 重启：systemctl restart $SYSTEMD_UNIT"
      systemctl restart "$SYSTEMD_UNIT"
      systemctl is-active --quiet "$SYSTEMD_UNIT" \
        || die "服务未能运行，排查：journalctl -u $SYSTEMD_UNIT -n 100"
      ;;
    nohup)
      stop_backend_nohup
      start_backend_nohup
      ;;
    bt)
      # 宝塔 Java 项目管理器托管：进程由面板守护，脚本不直接操作进程
      log "RESTART_MODE=bt：请在【宝塔面板 → 网站 → Java项目 → hrm-server】点击重启"
      ;;
    *)
      die "未知 RESTART_MODE：$RESTART_MODE（可选 auto/systemd/nohup/bt）"
      ;;
  esac
}

health_check() {
  # bt 模式由用户在面板重启后手工验证，脚本不做等待
  [[ "$RESTART_MODE" == "bt" ]] && return 0
  log "健康检查（最长 ${HEALTH_TIMEOUT}s）：POST $HEALTH_URL"
  # 说明：携带非法账密的登录请求会返回业务错误 JSON（如 code=400/1001），
  # 任何 HTTP 响应都证明 Web 容器已就绪；若 Flyway 迁移失败，应用启动失败、
  # 端口不会打开，此处自然超时报错。
  local deadline=$((SECONDS + HEALTH_TIMEOUT)) code
  while (( SECONDS < deadline )); do
    code="$(curl -s -o /dev/null -w '%{http_code}' -m 3 -X POST "$HEALTH_URL" \
      -H 'Content-Type: application/json' \
      -d '{"username":"deploy_probe","password":"deploy_probe"}' 2>/dev/null || true)"
    case "$code" in
      200|400|401|403|404)
        log "后端已就绪（HTTP $code）"
        return 0
        ;;
      000|"")
        :  # 尚未启动完成，继续等待
        ;;
      *)
        warn "HTTP $code（非预期但已响应），继续观察..."
        ;;
    esac
    sleep 3
  done
  die "健康检查超时：请查看 $LOG_DIR/stdout.log、journalctl -u $SYSTEMD_UNIT 与 MySQL/Redis 状态"
}

npm_ci_or_install() {
  # 有 lockfile 用 npm ci（可复现构建），否则退回 npm install；均走国内镜像
  if [[ -f package-lock.json ]]; then
    npm ci --registry="$NPM_REGISTRY"
  else
    npm install --registry="$NPM_REGISTRY"
  fi
}

build_frontend() {
  local npm_log="$LOG_DIR/npm-$(date +%Y%m%d-%H%M%S).log"
  log "前端构建：npm ci/install + npm run build（镜像：$NPM_REGISTRY，日志：$npm_log）"
  if ( cd "$ADMIN_SRC_DIR" && npm_ci_or_install && npm run build ) >> "$npm_log" 2>&1; then
    log "前端构建完成"
  else
    tail -n 50 "$npm_log" || true
    die "前端构建失败，完整日志：$npm_log"
  fi
  [[ -f "$ADMIN_SRC_DIR/dist/index.html" ]] || die "前端产物缺失：dist/index.html"
}

sync_frontend() {
  local src_dist
  if [[ "$FRONTEND_MODE" == "source" ]]; then
    build_frontend
    src_dist="$ADMIN_SRC_DIR/dist"
  else
    src_dist="$PREBUILT_DIST"
  fi
  log "同步前端产物：$src_dist -> $SITE_DIR"
  # 保留 .well-known（Let's Encrypt 证书验证）与 .user.ini（宝塔站点配置）
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete --exclude='.well-known' --exclude='.user.ini' "$src_dist"/ "$SITE_DIR"/
  else
    find "$SITE_DIR" -mindepth 1 -maxdepth 1 \
      ! -name '.well-known' ! -name '.user.ini' -exec rm -rf {} +
    cp -a "$src_dist"/. "$SITE_DIR"/
  fi
  chown -R www:www "$SITE_DIR" 2>/dev/null || warn "chown www 失败（可忽略，确认站点属主即可）"
  log "前端同步完成"
}

do_deploy() {
  preflight
  check_external_config

  # 是否需要拉代码：后端/前端至少一端从仓库源码构建时才 pull
  local backend_from_repo=0 frontend_from_repo=0
  [[ $SKIP_BACKEND -eq 0 && "$BACKEND_MODE" == "source" ]] && backend_from_repo=1
  [[ $SKIP_FRONTEND -eq 0 && "$FRONTEND_MODE" == "source" ]] && frontend_from_repo=1
  if [[ $backend_from_repo -eq 1 || $frontend_from_repo -eq 1 ]]; then
    pull_code
  else
    log "后端/前端均使用预构建产物，跳过 git pull"
  fi

  if [[ $SKIP_BACKEND -eq 0 ]]; then
    locate_backend_artifact
    deploy_backend_jar
    restart_backend
    health_check
  else
    log "跳过后端（--frontend-only）"
  fi

  if [[ $SKIP_FRONTEND -eq 0 ]]; then
    sync_frontend
  else
    log "跳过前端（--backend-only）"
  fi

  log "====================== 部署完成 ======================"
  log "版本    ：$DEPLOY_BRANCH @ ${GIT_COMMIT:-N/A}"
  log "后端 jar：$RUNTIME_JAR（重启方式：$RESTART_MODE）"
  log "前端目录：$SITE_DIR"
  log "验证命令：curl -s -X POST $HEALTH_URL -H 'Content-Type: application/json' -d '{}'"
}

do_rollback() {
  preflight
  check_external_config
  local target=""
  if [[ -n "$ROLLBACK_JAR" ]]; then
    target="$BACKUP_DIR/$ROLLBACK_JAR"
    [[ -f "$target" ]] || die "指定备份不存在：$target（可用备份见 ls $BACKUP_DIR）"
  else
    target="$(ls -1t "$BACKUP_DIR"/${APP_NAME}-*.jar 2>/dev/null | head -n 1 || true)"
    [[ -n "$target" && -f "$target" ]] || die "无可用备份（$BACKUP_DIR 为空），无法回滚"
  fi
  log "回滚目标：$target"
  # 当前 jar 也留档，防止误回滚后想再切回来
  backup_runtime_jar
  cp -f "$target" "$RUNTIME_JAR"
  chmod 644 "$RUNTIME_JAR"
  {
    echo "rollback_time : $(_now)"
    echo "rollback_from : $target"
  } > "$RUNTIME_DIR/RELEASE_INFO"
  restart_backend
  health_check
  log "====================== 回滚完成 ======================"
  log "当前运行：$target"
  log "注意：回滚只还原应用；数据库结构由 Flyway 前向管理不会自动回退，"
  log "     数据库处理策略见 docs/deploy.md 第 8 章。"
}

# =========================================================================
# 入口
# =========================================================================
main() {
  # 解析命令行参数
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --branch|-b)
        [[ $# -ge 2 ]] || die "--branch 需要分支名参数"
        DEPLOY_BRANCH="$2"; shift 2 ;;
      --mode)
        [[ $# -ge 2 ]] || die "--mode 需要 source|jar 参数"
        BACKEND_MODE="$2"; shift 2 ;;
      --backend-only)
        SKIP_FRONTEND=1; shift ;;
      --frontend-only)
        SKIP_BACKEND=1; shift ;;
      --rollback)
        ACTION="rollback"; shift ;;
      --rollback=*)
        ACTION="rollback"; ROLLBACK_JAR="${1#*=}"; shift ;;
      -h|--help)
        usage; exit 0 ;;
      *)
        usage >&2; die "未知参数：$1" ;;
    esac
  done

  # 初始化运行目录与日志（先建目录再写日志，保证 die 信息也能落盘）
  mkdir -p "$CONFIG_DIR" "$LOG_DIR" "$BACKUP_DIR"
  LOG_FILE="$LOG_DIR/deploy-$(date +%Y%m%d-%H%M%S).log"

  # 任一步骤失败即中止（set -e 已启用），此处补充定位信息
  trap 'echo "[$(_now)] [ERROR] 脚本在第 ${LINENO} 行失败，已中止（详见 $LOG_FILE）" >&2' ERR

  log "====================== 部署开始 ======================"
  log "仓库目录：$REPO_DIR"

  case "$ACTION" in
    deploy)   do_deploy ;;
    rollback) do_rollback ;;
  esac
}

main "$@"
