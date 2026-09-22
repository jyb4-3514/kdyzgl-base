# -*- coding: utf-8 -*-
"""日志初始化与**全局脱敏**。

红线 C5 / ADR §20.3.3：
- 明文凭据零落地——不落盘、不入日志；日志中不得出现密码、完整 Cookie、token、签名；
- 脱敏必须**全局强制**（覆盖第三方库输出，如 Playwright / PyMySQL 经 root handler 的输出）；
- 账号一律只打印脱敏值（由调用方通过 ``PddAccount.masked_account()`` 提供）。

实现要点：用 Formatter 做整条日志（含异常堆栈）的正文替换，
比 Filter 更彻底——异常堆栈里的口令同样会被替换。
"""
from __future__ import annotations

import logging
import re
import sys
import threading
from logging.handlers import RotatingFileHandler
from pathlib import Path

from collector.config.settings import COLLECTOR_ROOT, LoggingConfig

_MASK = "***"
_HANDLER_MARKER = "_collector_handler"

# 键值型敏感字段：``password=xxx`` / ``Authorization: Bearer xxx`` 等
_KEY_VALUE_RE = re.compile(
    r"(?i)\b(password|passwd|pwd|sms_?code|verification_?code|authorization|anti-content|etag|pdd-id)"
    r"\b\s*[:=]\s*\S+"
)
# Cookie 类：整行到行尾都可能是凭据，一律打掉
_COOKIE_RE = re.compile(r"(?i)\b(set-cookie|cookie)\b\s*[:=]\s*[^\r\n]+")
# 手机号兜底脱敏（即便未来某处误记账号，也不落全量）
_PHONE_RE = re.compile(r"\b(1[3-9]\d)\d{4}(\d{4})\b")

_sensitive_lock = threading.Lock()
_sensitive_values: set[str] = set()


def add_sensitive(value: str | None) -> None:
    """登记一个需要在日志中脱敏的明文值（如登录密码）。用完必须调用 clear_sensitive。"""
    if value and len(value) >= 4:  # 过短的值替换会误伤大量正常文本
        with _sensitive_lock:
            _sensitive_values.add(value)


def clear_sensitive() -> None:
    """清空脱敏登记，避免明文长期驻留内存。"""
    with _sensitive_lock:
        _sensitive_values.clear()


def redact_text(text: str) -> str:
    """对任意文本做脱敏，供日志与提示输出复用。"""
    if not text:
        return text
    with _sensitive_lock:
        registered = tuple(_sensitive_values)
    for secret in registered:
        text = text.replace(secret, _MASK)
    text = _KEY_VALUE_RE.sub(lambda m: f"{m.group(1)}={_MASK}", text)
    text = _COOKIE_RE.sub(lambda m: f"{m.group(1)}={_MASK}", text)
    text = _PHONE_RE.sub(lambda m: f"{m.group(1)}****{m.group(2)}", text)
    return text


class RedactionFormatter(logging.Formatter):
    """对最终渲染文本（含异常堆栈）统一脱敏。"""

    def format(self, record: logging.LogRecord) -> str:
        return redact_text(super().format(record))


def setup_logging(config: LoggingConfig, log_dir: Path | None = None) -> Path:
    """初始化 root logger：控制台 + 滚动文件，均经脱敏 Formatter。返回日志文件路径。"""
    target_dir = Path(log_dir) if log_dir else (COLLECTOR_ROOT / "runtime" / "logs")
    target_dir.mkdir(parents=True, exist_ok=True)
    log_file = target_dir / "collector.log"

    formatter = RedactionFormatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s")
    root = logging.getLogger()
    root.setLevel(getattr(logging, config.level, logging.INFO))

    # 幂等：重复调用只更新级别，不重复挂 handler
    existing = [h for h in root.handlers if getattr(h, _HANDLER_MARKER, False)]
    if existing:
        for handler in existing:
            handler.setLevel(root.level)
        return log_file

    console = logging.StreamHandler(stream=sys.stdout)
    console.setFormatter(formatter)
    setattr(console, _HANDLER_MARKER, True)
    root.addHandler(console)

    file_handler = RotatingFileHandler(
        log_file, maxBytes=config.max_bytes, backupCount=config.backup_count, encoding="utf-8"
    )
    file_handler.setFormatter(formatter)
    setattr(file_handler, _HANDLER_MARKER, True)
    root.addHandler(file_handler)

    # 第三方库降低噪声；其输出仍经 root 的脱敏 Formatter
    logging.getLogger("playwright").setLevel(logging.WARNING)
    logging.getLogger("pymysql").setLevel(logging.WARNING)
    return log_file
