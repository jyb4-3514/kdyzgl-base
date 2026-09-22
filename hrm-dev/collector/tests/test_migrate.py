# -*- coding: utf-8 -*-
"""建表迁移测试（假连接，不实连数据库）。"""
from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path

import pytest

from collector.db.migrate import DEFAULT_SCHEMA_FILE, MigrationError, migrate, split_statements


class _FakeCursor:
    def __init__(self) -> None:
        self.statements: list[str] = []

    def execute(self, sql: str) -> None:
        self.statements.append(sql)

    def close(self) -> None:
        pass


class _FakeConnection:
    def __init__(self) -> None:
        self.cursor_obj = _FakeCursor()

    @contextmanager
    def session(self):
        yield self.cursor_obj


def test_切分语句时剔除整行注释():
    sql_text = "-- 头部注释\nCREATE TABLE a (id INT);\n-- 中间注释\nCREATE TABLE b (id INT);\n"
    statements = split_statements(sql_text)
    assert len(statements) == 2
    assert all("--" not in statement for statement in statements)
    assert statements[0].startswith("CREATE TABLE a")


def test_仅注释内容切分结果为空():
    assert split_statements("-- 只有注释\n-- 再来一行\n") == []


def test_迁移幂等执行两张表建表语句():
    connection = _FakeConnection()
    executed = migrate(connection, DEFAULT_SCHEMA_FILE)
    assert executed == 2
    assert len(connection.cursor_obj.statements) == 2
    assert all(statement.startswith("CREATE TABLE IF NOT EXISTS pdd_") for statement in connection.cursor_obj.statements)


def test_建表脚本缺失时报错(tmp_path: Path):
    with pytest.raises(MigrationError) as excinfo:
        migrate(_FakeConnection(), tmp_path / "缺失.sql")
    assert "未找到建表脚本" in str(excinfo.value)


def test_建表脚本无可执行语句时报错(tmp_path: Path):
    path = tmp_path / "empty.sql"
    path.write_text("-- 只有注释\n", encoding="utf-8")
    with pytest.raises(MigrationError) as excinfo:
        migrate(_FakeConnection(), path)
    assert "未解析到任何语句" in str(excinfo.value)


def test_建表失败时带上下文抛出(tmp_path: Path):
    class _BrokenCursor(_FakeCursor):
        def execute(self, sql: str) -> None:
            raise RuntimeError("DDL 被拒绝")

    class _BrokenConnection(_FakeConnection):
        def __init__(self) -> None:
            self.cursor_obj = _BrokenCursor()

    path = tmp_path / "broken.sql"
    path.write_text("CREATE TABLE x (id INT);", encoding="utf-8")
    with pytest.raises(MigrationError) as excinfo:
        migrate(_BrokenConnection(), path)
    assert "DDL 被拒绝" in str(excinfo.value)
