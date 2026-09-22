#!/bin/sh
# 由环境变量生成 Basic Auth 口令文件（nginx 官方镜像会自动执行 /docker-entrypoint.d/*.sh）
#
# 为什么不在镜像里放 .htpasswd：演示站是一次性测试环境，但口令仍属凭据。
# 规则 §7 要求凭据只存服务器外置配置，不入仓库、不入镜像层；用「启动时写入」的方式，
# 镜像层里永远没有口令，容器重启换环境变量即可轮换。
#
# 为什么用哈希而非明文：nginx 的 auth_basic_user_file 只接受 crypt()/SHA/PLAIN 格式，
# 哈希由部署方用 `openssl passwd -apr1 '<强口令>'` 预先生成，明文口令不进入服务器磁盘。
set -e

: "${BASIC_AUTH_USER:?必须设置 BASIC_AUTH_USER（网关账号）}"
: "${BASIC_AUTH_HASH:?必须设置 BASIC_AUTH_HASH（openssl passwd -apr1 生成）}"

# 校验哈希格式，避免把明文误填进来——明文一旦写进 .htpasswd 会以 {PLAIN} 之外的形式被拒，
# 表现为「输对了口令也 401」，排查成本高，故这里显式失败
case "$BASIC_AUTH_HASH" in
  '$apr1$'*|'$1$'*|'{SHA}'*) ;;
  *)
    echo "[basic-auth] BASIC_AUTH_HASH 不是 apr1/md5/sha 哈希，拒绝启动" >&2
    exit 1
    ;;
esac

printf '%s:%s\n' "$BASIC_AUTH_USER" "$BASIC_AUTH_HASH" > /etc/nginx/.htpasswd

# 权限必须让 nginx worker 可读：worker 以 nginx 用户运行，若设成 600（root 独占）
# 会报 open() "/etc/nginx/.htpasswd" failed (13: Permission denied) → 鉴权阶段直接 500
# （已实测踩过，表现是「不带凭据返回 401、带凭据反而 500」，极易误判为哈希格式错误）。
# 正确模型：属主 root、属组 nginx、640 —— 部署方独占写，worker 只读，其他用户不可读。
if chgrp nginx /etc/nginx/.htpasswd 2>/dev/null; then
  chmod 640 /etc/nginx/.htpasswd
else
  # 兜底：镜像内若无 nginx 组（非官方镜像情形），退化为其他用户可读，保证功能可用
  chmod 644 /etc/nginx/.htpasswd
  echo "[basic-auth] 警告：未找到 nginx 组，已退化为 644" >&2
fi

echo "[basic-auth] 已生成 /etc/nginx/.htpasswd（用户 $BASIC_AUTH_USER）"
