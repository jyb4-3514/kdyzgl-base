# -*- coding: utf-8 -*-
"""MySQL 连接（仅 127.0.0.1）。

驱动选型：**PyMySQL**（纯 Python、MIT License、无本地 C 扩展、易审查）。
备选 ``mysql-connector-python`` 为 Oracle 双许可（GPL-2.0 + FOSS 例外），
在「凭据可审查 + 轻量部署」的约束下许可与依赖面更复杂，故不采用。

连接参数全部来自 DPAPI 密钥文件的 ``mysql`` 段；密码只在内存使用，绝不落盘、绝不入日志。

连接池取舍：采集端固定「并发 = 1」（红线 C2 / ADR-C14），不存在并发取连接的需求，
故不引入连接池中间件，采用「单连接复用 + 失效重建」——更少的依赖即为更小的攻击面。
"""
from __future__ import annotations

import time
from contextlib import contextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterator

from collector.config.settings import DEFAULT_SECRETS_FILE, DbConfig
from collector.security import dpapi

# 只允许本机数据库
_ALLOWED_HOSTS = {"127.0.0.1", "localhost"}

# 连接建立时把会话时区固定为 UTC，使 CURRENT_TIMESTAMP 与 Python 写入的 UTC 时间口径一致
_INIT_COMMAND = "SET time_zone = '+00:00'"


class DatabaseError(Exception):
    """数据库凭据缺失、连接失败或执行失败。"""


@dataclass(frozen=True)
class MysqlCredentials:
    """MySQL 连接凭据。``password`` 标记 ``repr=False``，不随 repr 打印。"""

    host: str
    port: int
    database: str
    user: str
    password: str = field(repr=False)

    def __post_init__(self) -> None:
        if self.host not in _ALLOWED_HOSTS:
            raise DatabaseError(f"数据库 host 只允许 127.0.0.1/localhost（当前 {self.host}）")
        for name, value in (("database", self.database), ("user", self.user), ("password", self.password)):
            if not value:
                raise DatabaseError(f"数据库 {name} 为空；请重新执行 scripts/env-setup.ps1 初始化 MySQL 凭据")

    def __repr__(self) -> str:
        return (
            f"MysqlCredentials(host={self.host!r}, port={self.port!r}, "
            f"database={self.database!r}, user={self.user!r}, password='***')"
        )

    __str__ = __repr__


def load_mysql_credentials(secrets_file: Path | str | None = None) -> MysqlCredentials:
    """从 DPAPI 密钥文件读取 MySQL 应用账号（最小权限账号 yizhan@127.0.0.1）。"""
    path = Path(secrets_file) if secrets_file else DEFAULT_SECRETS_FILE
    try:
        secrets = dpapi.load_secrets(path)
        section = dpapi.get_section(secrets, "mysql")
        password = dpapi.decrypt_secret(secrets, "mysql", "appUserPasswordEnc")
    except dpapi.DpapiError as exc:
        raise DatabaseError(f"MySQL 凭据不可用：{exc}") from exc
    return MysqlCredentials(
        host=str(section.get("host") or "").strip(),
        port=int(section.get("port") or 3306),
        database=str(section.get("database") or "").strip(),
        user=str(section.get("appUser") or "").strip(),
        password=password,
    )


def _connect_once(db: DbConfig, credentials: MysqlCredentials) -> Any:
    """建立一次物理连接。pymysql 延迟导入，便于无驱动的环境做纯逻辑单测。"""
    try:
        import pymysql  # type: ignore[import-not-found]
    except ImportError as exc:  # pragma: no cover - 取决于运行环境
        raise DatabaseError("未检测到 PyMySQL，无法连接数据库；请执行：pip install -r requirements.txt") from exc
    try:
        return pymysql.connect(
            host=db.host,
            port=db.port,
            user=credentials.user,
            password=credentials.password,
            database=db.database,
            charset=db.charset,
            connect_timeout=db.connect_timeout_s,
            read_timeout=db.read_timeout_s,
            write_timeout=db.write_timeout_s,
            init_command=_INIT_COMMAND,
            cursorclass=pymysql.cursors.DictCursor,
            autocommit=False,
        )
    except Exception as exc:  # pymysql.err.OperationalError 等
        # 只暴露主机/库名，不含账号密码
        raise DatabaseError(f"MySQL 连接失败（{db.host}:{db.port}/{db.database}）：{exc}") from exc


def _connect_with_retry(db: DbConfig, credentials: MysqlCredentials) -> Any:
    """带退避的连接重试，次数与间隔来自配置。"""
    attempts = max(1, db.retry_attempts)
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            return _connect_once(db, credentials)
        except DatabaseError as exc:
            last_error = exc
            if attempt < attempts:
                time.sleep(db.retry_backoff_s * attempt)
    raise DatabaseError(f"MySQL 连接重试 {attempts} 次仍失败：{last_error}") from last_error


class MySqlConnection:
    """单连接复用器：用前探活，失效即重建；成功提交、异常回滚。"""

    def __init__(self, db: DbConfig, credentials: MysqlCredentials) -> None:
        self._db = db
        self._credentials = credentials
        self._conn: Any = None

    def _live_connection(self) -> Any:
        if self._conn is None:
            self._conn = _connect_with_retry(self._db, self._credentials)
            return self._conn
        try:
            # reconnect 参数已被 PyMySQL 标记废弃，此处仅探活；失效则重建
            self._conn.ping(reconnect=False)
        except Exception:
            self.close()
            self._conn = _connect_with_retry(self._db, self._credentials)
        return self._conn

    @contextmanager
    def session(self) -> Iterator[Any]:
        """事务边界：正常提交，异常回滚后向上抛出（不吞异常）。"""
        conn = self._live_connection()
        cursor = conn.cursor()
        try:
            yield cursor
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            cursor.close()

    def ping(self) -> None:
        """连通性自检（供 check-env 使用）。"""
        conn = _connect_with_retry(self._db, self._credentials)
        try:
            with conn.cursor() as cursor:
                cursor.execute("SELECT 1")
                cursor.fetchone()
        finally:
            try:
                conn.close()
            except Exception:  # 关闭失败不影响自检结论
                pass

    def close(self) -> None:
        if self._conn is not None:
            try:
                self._conn.close()
            except Exception:  # 关闭失败无需上抛，连接对象即被丢弃
                pass
            finally:
                self._conn = None

    def __enter__(self) -> "MySqlConnection":
        return self

    def __exit__(self, *_exc_info: object) -> None:
        self.close()
