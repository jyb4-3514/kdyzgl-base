# -*- coding: utf-8 -*-
"""日志脱敏测试（红线 C5 / ADR §20.3.3：明文凭据不入日志）。"""
from __future__ import annotations

import logging

from collector.logging_setup import RedactionFormatter, add_sensitive, clear_sensitive, redact_text


def test_键值型敏感字段被打码():
    assert "password=***" in redact_text("password=P@ssw0rd")
    assert "authorization=***" in redact_text("Authorization: Bearer abc.def").lower()
    assert "anti-content=***" in redact_text("anti-content=xyz")
    assert "pdd-id=***" in redact_text("pdd-id=123456").lower()


def test_cookie整行被打码():
    assert redact_text("Cookie: a=1; b=2") == "Cookie=***"
    assert redact_text("Set-Cookie: session=abc; Path=/") == "Set-Cookie=***"


def test_手机号兜底脱敏():
    assert redact_text("请联系 16612349983") == "请联系 166****9983"


def test_登记后的明文按值替换且清理后失效():
    add_sensitive("S3cretValue")
    try:
        assert redact_text("解密得到 S3cretValue") == "解密得到 ***"
    finally:
        clear_sensitive()
    # 清理后不再替换，避免明文长期驻留内存
    assert redact_text("S3cretValue") == "S3cretValue"


def test_过短的值不登记以免误伤正常文本():
    add_sensitive("abc")
    try:
        assert redact_text("abc") == "abc"
    finally:
        clear_sensitive()


def test_formatter对含参数的日志同样脱敏():
    formatter = RedactionFormatter("%(message)s")
    record = logging.LogRecord("collector.test", logging.ERROR, __file__, 1, "password=%s", ("P@ss",), None)
    assert formatter.format(record) == "password=***"
