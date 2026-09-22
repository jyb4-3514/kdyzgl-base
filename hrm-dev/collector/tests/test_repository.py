# -*- coding: utf-8 -*-
"""repository 参数化 SQL 与幂等 upsert 测试（用假 cursor，不实连数据库）。"""
from __future__ import annotations

from datetime import datetime, timezone

from collector.db import repository
from collector.db.repository import LoginSession, LoginStatus, compute_expire_at, utc_now


class _FakeCursor:
    def __init__(self) -> None:
        self.calls: list[tuple[str, tuple | None]] = []
        self.rowcount = 1
        self._rows: list[dict] = []

    def execute(self, sql, params=None):
        self.calls.append((sql, params))

    def fetchall(self):
        return self._rows

    def fetchone(self):
        return None


def _session(**overrides) -> LoginSession:
    payload = {
        "account_masked": "166****9983",
        "account_hash": "a" * 64,
        "site": "https://mcmd.pinduoduo.com/home",
        "login_status": LoginStatus.SUCCESS,
        "login_at": datetime(2026, 9, 22, 12, 0, 0),
        "expire_at": datetime(2026, 9, 23, 0, 0, 0),
        "storage_state_ref": "runtime/storage-state/aaaa.json",
        "cookie_count": 7,
        "last_check_at": datetime(2026, 9, 22, 12, 0, 0),
        "fail_reason": None,
    }
    payload.update(overrides)
    return LoginSession(**payload)


def test_会话upsert使用幂等语法且占位符与参数一一对应():
    cursor = _FakeCursor()
    repository.upsert_login_session(cursor, _session())
    sql, params = cursor.calls[0]
    assert "ON DUPLICATE KEY UPDATE" in sql
    assert "INSERT INTO pdd_login_session" in sql
    assert sql.count("%s") == len(params)


def test_会话upsert不把取值拼进SQL字符串():
    cursor = _FakeCursor()
    repository.upsert_login_session(cursor, _session())
    sql, params = cursor.calls[0]
    # 账号与站点必须走参数，不得出现在 SQL 文本中（防注入 + 便于审计）
    assert "166****9983" not in sql
    assert "mcmd.pinduoduo.com" not in sql
    assert "166****9983" in params
    assert "https://mcmd.pinduoduo.com/home" in params


def test_失败原因超长时按列上限截断():
    cursor = _FakeCursor()
    repository.upsert_login_session(cursor, _session(fail_reason="x" * 900))
    _sql, params = cursor.calls[0]
    assert len(params[-1]) == 512


def test_会话列表条数被限制在合法区间():
    cursor = _FakeCursor()
    repository.list_recent_sessions(cursor, 0)
    assert cursor.calls[0][1] == (1,)
    cursor.calls.clear()
    repository.list_recent_sessions(cursor, 9999)
    assert cursor.calls[0][1] == (200,)


def test_会话列表只查非敏感列():
    cursor = _FakeCursor()
    repository.list_recent_sessions(cursor, 10)
    sql = cursor.calls[0][0]
    assert "account_masked" in sql
    assert "storage_state_ref" not in sql  # 落盘路径属敏感物，列表不返回


def test_删除会话返回影响行数():
    cursor = _FakeCursor()
    cursor.rowcount = 1
    assert repository.delete_login_session(cursor, "a" * 64, "https://x") == 1
    assert cursor.calls[0][1] == ("a" * 64, "https://x")


def test_游标upsert占位符与参数一一对应():
    cursor = _FakeCursor()
    repository.upsert_collect_cursor(cursor, "ST001", "waybill_full", "200")
    sql, params = cursor.calls[0]
    assert "ON DUPLICATE KEY UPDATE" in sql
    assert sql.count("%s") == len(params) == 4
    assert params[:3] == ("ST001", "waybill_full", "200")


def test_游标拼写超过列长度时截断():
    cursor = _FakeCursor()
    repository.upsert_collect_cursor(cursor, "ST001", "k" * 300, "v" * 400)
    params = cursor.calls[0][1]
    assert len(params[1]) == 128
    assert len(params[2]) == 255


def test_读取游标参数化():
    cursor = _FakeCursor()
    assert repository.get_collect_cursor(cursor, "ST001", "waybill_full") is None
    assert cursor.calls[0][1] == ("ST001", "waybill_full")


def test_过期时间按小时推算():
    base = datetime(2026, 9, 22, 0, 0, 0)
    assert compute_expire_at(base, 12) == datetime(2026, 9, 22, 12, 0, 0)
    assert compute_expire_at(base, -5) == base  # 负值归零，不产生过去时间之外的意外


def test_utc_now为无时区UTC():
    now = utc_now()
    assert now.tzinfo is None
    reference = datetime.now(timezone.utc).replace(tzinfo=None)
    assert abs((reference - now).total_seconds()) < 5


def test_会话记录repr不含凭据字段():
    text = repr(_session())
    assert "166****9983" in text
    assert "password" not in text
