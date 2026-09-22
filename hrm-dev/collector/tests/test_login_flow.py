# -*- coding: utf-8 -*-
"""登录流程纯逻辑测试（用假页面对象，不连网、不开浏览器）。

覆盖：登录方式页签切换的**幂等判定**、切换失败的**可执行报错**、
以及同 id / 同 class 双元素场景下的可见性区分（``find_first_visible``）。
"""
from __future__ import annotations

from types import SimpleNamespace

import pytest

from collector.login import pdd, selectors


# ==================================================================
# 假页面对象：只需实现 pdd 用到的定位与可见性接口
# ==================================================================
class _FakeElement:
    def __init__(self, visible: bool, click_log: list, on_click=None) -> None:
        self.visible = visible
        self._click_log = click_log
        self._on_click = on_click

    def is_visible(self) -> bool:
        return self.visible

    def click(self) -> None:
        self._click_log.append(self)
        if self._on_click is not None:
            self._on_click()


class _FakeLocator:
    def __init__(self, elements: list[_FakeElement]) -> None:
        self._elements = elements

    def count(self) -> int:
        return len(self._elements)

    def nth(self, index: int) -> _FakeElement:
        return self._elements[index]


class _FakePage:
    """按「kind|值」注册匹配元素；未注册的键返回空匹配。"""

    def __init__(self, matches: dict[str, list[_FakeElement]] | None = None) -> None:
        self._matches = matches or {}
        self.queries: list[str] = []

    def locator(self, value: str) -> _FakeLocator:
        return self._lookup(f"css|{value}")

    def get_by_text(self, value: str, exact: bool = False) -> _FakeLocator:
        return self._lookup(f"text|{value}|{exact}")

    def get_by_role(self, role: str, name: str | None = None) -> _FakeLocator:
        return self._lookup(f"role|{role}|{name}")

    def _lookup(self, key: str) -> _FakeLocator:
        self.queries.append(key)
        return _FakeLocator(self._matches.get(key, []))


class _SilentHumanizer:
    def pause_field(self) -> None:
        pass


def _settings(probe_timeout_ms: int = 0) -> SimpleNamespace:
    return SimpleNamespace(login=SimpleNamespace(probe_timeout_ms=probe_timeout_ms))


@pytest.fixture(autouse=True)
def _no_real_wait(monkeypatch):
    # 单测无需真实等待：探测预算与轮询间隔置 0（等待语义已在生产代码内实现）
    monkeypatch.setattr(pdd, "_LOGIN_MODE_PROBE_MS", 0)
    monkeypatch.setattr(pdd, "_SCAN_POLL_INTERVAL_MS", 0)


def _candidate(value: str, kind: str = "css") -> selectors.SelectorCandidate:
    return selectors.SelectorCandidate(kind, value, "测试构造")


def _scoped(value: str) -> str:
    """实测候选的查找键：作用域（激活面板）+ 基础选择器，与 selectors 的取值保持一致。"""
    return f"{selectors.ACTIVE_PANEL.value} {value}"


def _css_key(value: str) -> str:
    return f"css|{value}"


# ==================================================================
# 登录方式页签切换：幂等判定
# ==================================================================
def test_已处于密码登录态时幂等跳过页签点击():
    click_log: list = []
    page = _FakePage(
        {
            _css_key(_scoped("input.password-input:visible")): [_FakeElement(True, click_log)],
            "text|密码登录|True": [_FakeElement(True, click_log)],
        }
    )
    pdd.ensure_password_login_mode(page, _settings(), _SilentHumanizer())
    assert click_log == [], "密码框已可见（已是密码登录态）时不得重复点击页签"


def test_短信登录态下点击密码登录页签后转为可填():
    click_log: list = []
    password_element = _FakeElement(False, click_log)
    switcher = _FakeElement(True, click_log, on_click=lambda: setattr(password_element, "visible", True))
    page = _FakePage(
        {
            _css_key(_scoped("input.password-input:visible")): [password_element],
            "text|密码登录|True": [switcher],
        }
    )
    pdd.ensure_password_login_mode(page, _settings(), _SilentHumanizer())
    assert click_log == [switcher], "短信登录态下必须点击一次「密码登录」页签"


# ==================================================================
# 登录方式页签切换：失败报错必须可执行
# ==================================================================
def test_未定位到密码登录页签时报错指向诊断脚本():
    page = _FakePage()  # 密码框不可见、页签也不存在
    with pytest.raises(pdd.LoginPageError) as excinfo:
        pdd.ensure_password_login_mode(page, _settings(), _SilentHumanizer())
    message = str(excinfo.value)
    assert "未定位到「密码登录」页签" in message
    assert "dump-login-page.py" in message
    assert "selectors.py" in message


def test_点击页签后密码框仍不可见时报错指向诊断脚本():
    click_log: list = []
    switcher = _FakeElement(True, click_log)  # 点击后不改变密码框可见性，模拟登录页改版
    page = _FakePage(
        {
            _css_key(_scoped("input.password-input:visible")): [_FakeElement(False, click_log)],
            "text|密码登录|True": [switcher],
        }
    )
    with pytest.raises(pdd.LoginPageError) as excinfo:
        pdd.ensure_password_login_mode(page, _settings(), _SilentHumanizer())
    message = str(excinfo.value)
    assert "密码输入框仍未出现" in message
    assert "dump-login-page.py" in message
    assert click_log == [switcher]


# ==================================================================
# 同 id / 同 class 双元素的可见性区分
# ==================================================================
def test_同id两份时跳过隐藏项取可见项():
    click_log: list = []
    hidden = _FakeElement(False, click_log)
    visible = _FakeElement(True, click_log)
    page = _FakePage({"css|#mobile:visible": [hidden, visible]})
    assert pdd.find_first_visible(page, (_candidate("#mobile:visible"),), timeout_ms=0) is visible


def test_全部匹配项均不可见时返回None():
    page = _FakePage({"css|#mobile:visible": [_FakeElement(False, [])]})
    assert pdd.find_first_visible(page, (_candidate("#mobile:visible"),), timeout_ms=0) is None


def test_降级链在首选候选未命中时回落到通用候选():
    page = _FakePage(
        {
            _css_key(_scoped("#mobile:visible")): [],
            'css|input[type="text"]:visible': [_FakeElement(True, [])],
        }
    )
    assert pdd.find_first_visible(page, selectors.ACCOUNT_INPUTS, timeout_ms=0) is not None


def test_首个作用域候选定位异常时不阻断后续候选():
    class _BoomPage(_FakePage):
        def locator(self, value: str) -> _FakeLocator:
            self.queries.append(f"css|{value}")
            if value == _scoped("#mobile:visible"):
                raise RuntimeError("模拟单个候选不可用")
            return super().locator(value)

    page = _BoomPage({_css_key(_scoped("input.password-input:visible")): [_FakeElement(True, [])]})
    assert pdd.find_first_visible(page, selectors.PASSWORD_INPUTS, timeout_ms=0) is not None
    assert pdd.find_first_visible(page, selectors.ACCOUNT_INPUTS, timeout_ms=0) is None


# ==================================================================
# 定位器翻译：可见性约束与精确匹配必须原样透传
# ==================================================================
def test_css候选的可见性约束原样透传():
    page = _FakePage()
    pdd._locator(page, _candidate("#mobile:visible"))
    assert page.queries == ["css|#mobile:visible"]


def test_css候选的作用域在定位时前置():
    page = _FakePage()
    pdd._locator(page, selectors.ACCOUNT_INPUTS[0])
    assert page.queries == [_css_key(_scoped("#mobile:visible"))]


def test_文本候选的精确匹配透传():
    page = _FakePage()
    pdd._locator(page, selectors.SelectorCandidate("text", "密码登录", "测试构造", True, exact=True))
    assert page.queries == ["text|密码登录|True"]


def test_role候选按无障碍名透传():
    page = _FakePage()
    pdd._locator(page, _candidate("button:登录", kind="role"))
    assert page.queries == ["role|button|登录"]


def test_未知选择器类型抛错():
    with pytest.raises(pdd.LoginPageError):
        pdd._locator(_FakePage(), _candidate("//button", kind="xpath"))
