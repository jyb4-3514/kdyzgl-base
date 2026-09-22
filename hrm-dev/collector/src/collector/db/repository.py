# -*- coding: utf-8 -*-
"""登录会话与采集游标的读写。

约定：
- 全部使用**参数化 SQL**（%s 占位符），禁止字符串拼接（项目规范）。
- 时间统一 UTC：连接层已 ``SET time_zone='+00:00'``，Python 侧写入无时区 UTC datetime。
- 幂等 upsert：会话按唯一键 ``(account_hash, site)``、游标按 ``(station_code, task_key)``。
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
from typing import Any

# 各列长度上限，与 schema.sql 对齐；写入前截断，避免超长导致整批失败
_LEN_ACCOUNT_MASKED = 64
_LEN_SITE = 255
_LEN_STORAGE_STATE_REF = 512
_LEN_FAIL_REASON = 512
_LEN_STATION_CODE = 64
_LEN_TASK_KEY = 128
_LEN_CURSOR_VALUE = 255

_MAX_RECENT_LIMIT = 200


class LoginStatus(str, Enum):
    """登录态。用 VARCHAR + 应用层枚举，避免 MySQL ENUM 增值得改表结构。"""

    PENDING = "PENDING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    MANUAL_REQUIRED = "MANUAL_REQUIRED"
    EXPIRED = "EXPIRED"


def utc_now() -> datetime:
    """当前 UTC 时间（无时区，直接入库 DATETIME）。"""
    return datetime.now(timezone.utc).replace(tzinfo=None)


def compute_expire_at(login_at: datetime, ttl_hours: int) -> datetime:
    """按配置的会话有效期估算失效时间。

    注意：``session_ttl_hours`` 为**未实测**的保守估计（ADR 待验证项 V18），
    实测值回填后应同步更新配置默认值与本节说明。
    """
    return login_at + timedelta(hours=max(0, ttl_hours))


def _clip(value: str | None, limit: int) -> str | None:
    if value is None:
        return None
    return value[:limit]


@dataclass(frozen=True)
class LoginSession:
    """登录会话记录（写入用）。"""

    account_masked: str
    account_hash: str
    site: str
    login_status: LoginStatus
    login_at: datetime | None = None
    expire_at: datetime | None = None
    storage_state_ref: str | None = None
    cookie_count: int = 0
    last_check_at: datetime | None = None
    fail_reason: str | None = None

    def __repr__(self) -> str:
        return (
            f"LoginSession(account_masked={self.account_masked!r}, "
            f"login_status={self.login_status.value!r}, site={self.site!r}, "
            f"cookie_count={self.cookie_count!r})"
        )

    __str__ = __repr__


# 幂等 upsert：MySQL 8.0.19+ 的行别名语法（本环境 8.0.46），替代已废弃的 VALUES() 写法
SESSION_UPSERT_SQL = (
    "INSERT INTO pdd_login_session "
    "(account_masked, account_hash, site, login_status, login_at, expire_at, "
    " storage_state_ref, cookie_count, last_check_at, fail_reason) "
    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) AS new "
    "ON DUPLICATE KEY UPDATE "
    "account_masked = new.account_masked, "
    "login_status = new.login_status, "
    "login_at = new.login_at, "
    "expire_at = new.expire_at, "
    "storage_state_ref = new.storage_state_ref, "
    "cookie_count = new.cookie_count, "
    "last_check_at = new.last_check_at, "
    "fail_reason = new.fail_reason"
)

SESSION_LIST_SQL = (
    "SELECT account_masked, account_hash, site, login_status, login_at, expire_at, "
    "       cookie_count, last_check_at, fail_reason, update_time "
    "FROM pdd_login_session "
    "ORDER BY update_time DESC "
    "LIMIT %s"
)

SESSION_DELETE_SQL = "DELETE FROM pdd_login_session WHERE account_hash = %s AND site = %s"

CURSOR_UPSERT_SQL = (
    "INSERT INTO pdd_collect_cursor (station_code, task_key, cursor_value, last_run_at) "
    "VALUES (%s, %s, %s, %s) AS new "
    "ON DUPLICATE KEY UPDATE "
    "cursor_value = new.cursor_value, "
    "last_run_at = new.last_run_at"
)

CURSOR_SELECT_SQL = (
    "SELECT station_code, task_key, cursor_value, last_run_at, update_time "
    "FROM pdd_collect_cursor WHERE station_code = %s AND task_key = %s"
)


def upsert_login_session(cursor: Any, session: LoginSession) -> None:
    """写入/更新登录会话（幂等）。"""
    cursor.execute(
        SESSION_UPSERT_SQL,
        (
            _clip(session.account_masked, _LEN_ACCOUNT_MASKED),
            session.account_hash,
            _clip(session.site, _LEN_SITE),
            session.login_status.value,
            session.login_at,
            session.expire_at,
            _clip(session.storage_state_ref, _LEN_STORAGE_STATE_REF),
            int(session.cookie_count),
            session.last_check_at,
            _clip(session.fail_reason, _LEN_FAIL_REASON),
        ),
    )


def list_recent_sessions(cursor: Any, limit: int = 20) -> list[dict]:
    """按更新时间倒序列出会话（只取非敏感列，供 CLI 脱敏输出）。"""
    safe_limit = max(1, min(int(limit), _MAX_RECENT_LIMIT))
    cursor.execute(SESSION_LIST_SQL, (safe_limit,))
    return list(cursor.fetchall())


def delete_login_session(cursor: Any, account_hash: str, site: str) -> int:
    """删除某账号在某站点的会话记录，返回影响行数。

    只删本地记录。完整的授权撤回流程（停采集 + 物理删除凭据密文 + 台账留痕，ADR §20.6.3）
    尚未实现，见下方 TODO。
    TODO(扩展): 授权撤回闭环 —— 停用该站任务下发 + 删除 DPAPI 凭据密文 + 写入授权状态台账。
    """
    cursor.execute(SESSION_DELETE_SQL, (account_hash, site))
    return int(cursor.rowcount)


def upsert_collect_cursor(
    cursor: Any,
    station_code: str,
    task_key: str,
    cursor_value: str,
    last_run_at: datetime | None = None,
) -> None:
    """写入/推进采集游标（幂等），为 P1-03 中断续采提供持久化游标。"""
    cursor.execute(
        CURSOR_UPSERT_SQL,
        (
            _clip(station_code, _LEN_STATION_CODE),
            _clip(task_key, _LEN_TASK_KEY),
            _clip(cursor_value, _LEN_CURSOR_VALUE),
            last_run_at or utc_now(),
        ),
    )


def get_collect_cursor(cursor: Any, station_code: str, task_key: str) -> dict | None:
    """读取采集游标；不存在时返回 None（由调用方决定从哪一页开始）。"""
    cursor.execute(CURSOR_SELECT_SQL, (station_code, task_key))
    return cursor.fetchone()
