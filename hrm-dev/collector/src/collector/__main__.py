# -*- coding: utf-8 -*-
"""采集端 CLI 入口。

子命令：
- ``check-env``      自检：Python 版本、依赖、配置文件、本地凭据、MySQL 连通性
- ``init-db``        按 schema.sql 幂等建表
- ``login --check``  只探测当前登录态，不做登录动作
- ``login --run``    执行登录（已登录则跳过）；遇挑战时退出码 2 并给出人工处置指引
- ``session list``   列出最近的登录会话记录（脱敏输出）

用法（在 hrm-dev/collector 目录下）：
    $env:PYTHONPATH = "src"
    python -m collector check-env
"""
from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

from collector import __version__
from collector.config.pdd_account import PddAccount, PddAccountError, load_pdd_account
from collector.config.settings import ConfigError, Settings, load_settings
from collector.db import repository
from collector.db.connection import DatabaseError, MySqlConnection, load_mysql_credentials
from collector.db.migrate import MigrationError, migrate
from collector.login.browser import BrowserLaunchError
from collector.login.pdd import (
    LoginError,
    LoginPageError,
    LoginPersistenceError,
    LoginResult,
    ManualInterventionRequired,
    PddLoginService,
)
from collector.logging_setup import redact_text, setup_logging

logger = logging.getLogger("collector.cli")

# Python 最低版本：tomllib 自 3.11 起进入标准库（配置读取依赖它）
_MIN_PYTHON = (3, 11)

# 依赖自检清单：(导入名, 用途说明)
_DEPENDENCIES: tuple[tuple[str, str], ...] = (
    ("playwright", "真实浏览器自动化（登录流程）"),
    ("pymysql", "MySQL 驱动（登录态持久化）"),
    ("win32crypt", "pywin32（DPAPI 凭据解密）"),
)


def _print(message: str = "") -> None:
    print(redact_text(message))


def _add_common_options(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--settings", type=Path, default=None, help="配置文件路径（默认 config/settings.toml）")
    parser.add_argument("--secrets", type=Path, default=None, help="本地密钥文件路径（默认取配置中的 paths.secrets_file）")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="collector",
        description="快递驿站智汇系统 · 二期采集端（第一版：登录 + 多多账号密码配置 + 登录态持久化）",
    )
    parser.add_argument("--version", action="version", version=f"collector {__version__}")
    subparsers = parser.add_subparsers(dest="command", required=True)

    check_env = subparsers.add_parser("check-env", help="环境与依赖自检")
    _add_common_options(check_env)
    check_env.set_defaults(func=cmd_check_env)

    init_db = subparsers.add_parser("init-db", help="幂等建表")
    _add_common_options(init_db)
    init_db.add_argument("--schema-file", type=Path, default=None, help="自定义建表脚本路径")
    init_db.set_defaults(func=cmd_init_db)

    login = subparsers.add_parser("login", help="登录态探测与登录")
    _add_common_options(login)
    mode = login.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true", help="只探测当前登录态，不做登录动作")
    mode.add_argument("--run", action="store_true", help="执行登录（已登录则跳过）")
    login.set_defaults(func=cmd_login)

    session = subparsers.add_parser("session", help="登录会话记录")
    _add_common_options(session)
    session_sub = session.add_subparsers(dest="session_command", required=True)
    session_list = session_sub.add_parser("list", help="列出最近的登录会话（脱敏）")
    session_list.add_argument("--limit", type=int, default=20, help="返回条数上限（1~200，默认 20）")
    session_list.set_defaults(func=cmd_session_list)

    return parser


def _load_settings_or_exit(args: argparse.Namespace) -> Settings:
    try:
        return load_settings(args.settings)
    except ConfigError as exc:
        _print(f"[配置错误] {exc}")
        raise SystemExit(1) from exc


def _load_account_or_exit(settings: Settings, args: argparse.Namespace) -> PddAccount:
    secrets_file = args.secrets or settings.paths.secrets_file
    try:
        account = load_pdd_account(secrets_file)
    except PddAccountError as exc:
        _print(f"[多多账号配置错误] {exc}")
        raise SystemExit(1) from exc
    if not account.has_authorization():
        _print(
            "[警告] 本地凭据未登记站长书面授权（authorizedBy 为空）。"
            "按 ADR §20.2.9，未归档书面授权的站点不得启用自动登录。"
        )
    return account


def _open_connection(settings: Settings, args: argparse.Namespace, required: bool) -> MySqlConnection | None:
    secrets_file = args.secrets or settings.paths.secrets_file
    try:
        credentials = load_mysql_credentials(secrets_file)
        return MySqlConnection(settings.db, credentials)
    except DatabaseError as exc:
        if required:
            _print(f"[数据库错误] {exc}")
            raise SystemExit(1) from exc
        _print(f"[警告] 数据库不可用，本次不持久化会话记录：{exc}")
        return None


# ---------------- 子命令实现 ----------------
def cmd_check_env(args: argparse.Namespace) -> int:
    _print(f"collector {__version__}")
    failures: list[str] = []

    # 1. Python 版本
    version_ok = sys.version_info >= _MIN_PYTHON
    _print(f"[{'OK ' if version_ok else 'FAIL'}] Python 版本：{sys.version.split()[0]}（要求 >= {_MIN_PYTHON[0]}.{_MIN_PYTHON[1]}）")
    if not version_ok:
        failures.append("Python 版本过低")

    # 2. 依赖
    for module_name, purpose in _DEPENDENCIES:
        try:
            __import__(module_name)
            _print(f"[OK ] 依赖 {module_name}：{purpose}")
        except ImportError:
            _print(f"[FAIL] 依赖 {module_name} 缺失：{purpose}（pip install -r requirements.txt）")
            failures.append(f"缺少依赖 {module_name}")

    # 3. 配置文件
    settings: Settings | None = None
    try:
        settings = load_settings(args.settings)
        _print(f"[OK ] 配置文件：{settings.source_file}")
    except ConfigError as exc:
        _print(f"[FAIL] 配置：{exc}")
        failures.append("配置文件不可用")

    if settings is None:
        _print("")
        _print(f"自检失败，共 {len(failures)} 项：{'; '.join(failures)}")
        return 1

    # 4. 本地凭据与多多账号密码配置
    secrets_file = args.secrets or settings.paths.secrets_file
    _print(f"[INFO] 本地密钥文件：{secrets_file}（存在={secrets_file.exists()}）")
    try:
        account = load_pdd_account(secrets_file)
        _print(f"[OK ] 多多账号配置：{account.masked_description()}")
        if not account.has_authorization():
            _print("[WARN] 多多账号配置未登记站长书面授权（ADR §20.2.9）")
    except PddAccountError as exc:
        _print(f"[FAIL] 多多账号配置：{exc}")
        failures.append("多多账号密码配置不可用")

    # 5. MySQL 连通性
    try:
        credentials = load_mysql_credentials(secrets_file)
        _print(f"[OK ] MySQL 凭据：{credentials!r}")
    except DatabaseError as exc:
        _print(f"[FAIL] MySQL 凭据：{exc}")
        failures.append("MySQL 凭据不可用")
        credentials = None

    if credentials is not None:
        try:
            MySqlConnection(settings.db, credentials).ping()
            _print(f"[OK ] MySQL 连通性：{settings.db.host}:{settings.db.port}/{settings.db.database}")
        except DatabaseError as exc:
            _print(f"[FAIL] MySQL 连通性：{exc}")
            failures.append("MySQL 不可连接")

    _print("")
    if failures:
        _print(f"自检失败，共 {len(failures)} 项：{'; '.join(failures)}")
        return 1
    _print("自检通过")
    return 0


def cmd_init_db(args: argparse.Namespace) -> int:
    settings = _load_settings_or_exit(args)
    connection = _open_connection(settings, args, required=True)
    assert connection is not None
    try:
        executed = migrate(connection, args.schema_file)
        _print(f"建表完成，共执行 {executed} 条语句（幂等，可重复执行）")
        return 0
    except MigrationError as exc:
        _print(f"[建表失败] {exc}")
        return 1
    finally:
        connection.close()


def cmd_login(args: argparse.Namespace) -> int:
    settings = _load_settings_or_exit(args)
    account = _load_account_or_exit(settings, args)
    connection = _open_connection(settings, args, required=False)

    service = PddLoginService(settings, account, connection)
    try:
        if args.check:
            result = service.check()
        else:
            result = service.run()
    except ManualInterventionRequired as exc:
        _print(f"[需人工介入] {exc.reason}")
        _print(f"[人工处置指引] {exc.guidance}")
        return 2
    except (BrowserLaunchError, LoginPageError) as exc:
        _print(f"[登录失败] {exc}")
        return 1
    except LoginPersistenceError as exc:
        _print(f"[部分失败] {exc}")
        _print("说明：浏览器登录动作可能已完成，但会话记录未写入数据库，请修复数据库后重跑以留痕")
        return 1
    finally:
        if connection is not None:
            connection.close()

    _print_login_result(result)
    return result.exit_code


def _print_login_result(result: LoginResult) -> None:
    _print(
        f"结论：{result.outcome.value}（数据库登录态={result.login_status.value}）"
    )
    if result.cookie_count:
        _print(f"cookie 条数：{result.cookie_count}（仅计数，不输出内容）")
    if result.expire_at:
        _print(f"预计失效时间（UTC，未实测估值）：{result.expire_at.isoformat()}")
    if result.storage_state_ref:
        _print(f"storage_state 落盘路径：{result.storage_state_ref}")
    if result.fail_reason:
        _print(f"原因：{result.fail_reason}")


def cmd_session_list(args: argparse.Namespace) -> int:
    settings = _load_settings_or_exit(args)
    connection = _open_connection(settings, args, required=True)
    assert connection is not None
    try:
        with connection.session() as cursor:
            rows = repository.list_recent_sessions(cursor, args.limit)
    except Exception as exc:
        _print(f"[查询失败] {exc}")
        return 1
    finally:
        connection.close()

    if not rows:
        _print("暂无登录会话记录")
        return 0
    _print("账号(脱敏)      | 登录态         | 更新时间(UTC)       | cookie | 失败原因")
    for row in rows:
        updated = row.get("update_time")
        updated_text = updated.isoformat(sep=" ") if hasattr(updated, "isoformat") else str(updated)
        _print(
            f"{str(row.get('account_masked', '')):<15}"
            f" | {str(row.get('login_status', '')):<14}"
            f" | {updated_text:<19}"
            f" | {str(row.get('cookie_count', 0)):<6}"
            f" | {str(row.get('fail_reason') or '')}"
        )
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    # 日志先按默认值初始化，配置加载成功后再按配置调整级别
    settings_file = getattr(args, "settings", None)
    try:
        settings = load_settings(settings_file)
    except ConfigError:
        settings = None
    if settings is not None:
        setup_logging(settings.logging, settings.paths.log_dir)
    else:
        from collector.config.settings import COLLECTOR_ROOT, LoggingConfig

        setup_logging(LoggingConfig(level="INFO", max_bytes=5 * 1024 * 1024, backup_count=5), COLLECTOR_ROOT / "runtime" / "logs")

    # 顶层兜底：任何未在子命令内收敛的异常都不得以裸 traceback 输出。
    # 堆栈只进 debug 级日志（排障用），info 级只给结论与可执行指引。
    try:
        return args.func(args)
    except SystemExit:
        raise
    except KeyboardInterrupt:
        _print("[已中断] 采集端被手动中断，未完成的登录动作视为未执行")
        return 1
    except LoginError as exc:
        logger.debug("登录流程异常", exc_info=True)
        _print(f"[登录失败] {exc}")
        return 1
    except Exception as exc:
        logger.debug("未预期异常", exc_info=True)
        _print(f"[未预期错误] 采集端执行失败：{exc}")
        _print("排障：查看 debug 级日志 runtime/logs/collector.log；若与登录页结构有关，"
               "先跑 `python scripts/dump-login-page.py --url <登录地址> --click-text 密码登录` 导出 DOM 再校准 selectors.py")
        return 1


if __name__ == "__main__":
    sys.exit(main())
