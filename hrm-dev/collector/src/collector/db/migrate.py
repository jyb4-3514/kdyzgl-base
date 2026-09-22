# -*- coding: utf-8 -*-
"""建表迁移：幂等执行 schema.sql。

不引入 Flyway（版本化迁移属 Java 侧一期后端的约定）；采集端本地库结构简单，
用 ``CREATE TABLE IF NOT EXISTS`` 幂等建表即可，避免引入额外迁移框架与版本表。

TODO(扩展): 若后续采集端表结构需要增量演进，改为与一期一致的版本化脚本目录
            （schema/V{n}__描述.sql + 已执行记录表），届时再评估是否需要迁移框架。
"""
from __future__ import annotations

from pathlib import Path
from typing import Any

DEFAULT_SCHEMA_FILE = Path(__file__).resolve().parent / "schema.sql"


class MigrationError(Exception):
    """建表脚本缺失或执行失败。"""


def split_statements(sql_text: str) -> list[str]:
    """按分号切分 SQL 语句，并剔除整行 ``--`` 注释。

    约定：schema.sql 内的 ``--`` 注释只出现在独立行（不在字符串字面量中），
    故此处不做复杂的词法解析，保持实现精简可读。
    """
    kept_lines = [line for line in sql_text.splitlines() if not line.strip().startswith("--")]
    statements = []
    for raw in "\n".join(kept_lines).split(";"):
        statement = raw.strip()
        if statement:
            statements.append(statement)
    return statements


def migrate(connection: Any, schema_file: Path | str | None = None) -> int:
    """按 schema.sql 幂等建表，返回执行的语句数。

    参数 connection 为 :class:`collector.db.connection.MySqlConnection`。
    """
    path = Path(schema_file) if schema_file else DEFAULT_SCHEMA_FILE
    if not path.exists():
        raise MigrationError(f"未找到建表脚本：{path}")
    try:
        sql_text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise MigrationError(f"建表脚本读取失败：{path}（{exc}）") from exc

    statements = split_statements(sql_text)
    if not statements:
        raise MigrationError(f"建表脚本中未解析到任何语句：{path}")

    executed = 0
    with connection.session() as cursor:
        for statement in statements:
            try:
                cursor.execute(statement)
            except Exception as exc:
                head = statement.splitlines()[0][:60]
                raise MigrationError(f"建表语句执行失败（{head}...）：{exc}") from exc
            executed += 1
    return executed
