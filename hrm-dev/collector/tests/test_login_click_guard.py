# -*- coding: utf-8 -*-
"""提交按钮点击守卫与异常收敛的纯逻辑测试（用假页面对象，不连网、不开浏览器）。

起因（真实缺陷，2026-09-22 采集机实测）：
    ``button.login-btn:visible`` 定位到了按钮，但真实点击被页面元素
    （激活面板的 ``#mobile`` 与 sticky 页签容器）拦截，Playwright 重试 30s 后抛
    ``TimeoutError``，且该异常**直接抛栈**未收敛。

本组用例钉死两件事：
1. 点击前必须做落点遮挡校验（``document.elementFromPoint``），被遮挡时按滚动微调重试，
   最终失败要**归因到遮挡者**，不得报成「未定位到按钮」；
2. 异常 → 登录结论 → 落库状态 → CLI 退出码 的映射闭环。
"""
from __future__ import annotations

import logging
from types import SimpleNamespace

import pytest

from collector.config.pdd_account import PddAccount
from collector.login import pdd
from collector.login.browser import BrowserLaunchError
from collector.login.pdd import ChallengeType, LoginOutcome, LoginStatus, ManualInterventionRequired


# ==================================================================
# 假页面 / 假元素：只实现守卫用到的接口
# ==================================================================
class _FakeMouse:
    def __init__(self) -> None:
        self.wheels: list[tuple[int, int]] = []

    def wheel(self, dx: int, dy: int) -> None:
        self.wheels.append((dx, dy))


class _FakeClickPage:
    def __init__(self) -> None:
        self.mouse = _FakeMouse()


class _FakeClickable:
    """可点击元素：``blockers`` 按顺序返回每次落点命中测试的结果（耗尽后固定用最后一项）。"""

    def __init__(
        self,
        box: dict | None = {"x": 100.0, "y": 200.0, "width": 80.0, "height": 30.0},
        blockers: list[str | None] | None = None,
        on_click=None,
        evaluate_raises: bool = False,
    ) -> None:
        self._box = box
        self._blockers = blockers if blockers is not None else [None]
        self._hit_calls = 0
        self._evaluate_raises = evaluate_raises
        self.click_calls = 0
        self._on_click = on_click

    def bounding_box(self) -> dict | None:
        return self._box

    def evaluate(self, script: str, arg=None):
        if "elementFromPoint" not in script:
            return None  # scrollIntoView 之类的滚动指令
        if self._evaluate_raises:
            raise RuntimeError("模拟浏览器内校验不可用")
        index = min(self._hit_calls, len(self._blockers) - 1)
        self._hit_calls += 1
        return self._blockers[index]

    def click(self) -> None:
        self.click_calls += 1
        if self._on_click is not None:
            self._on_click()


class _FakePwError(Exception):
    """伪装 Playwright 普通底层异常：按模块名即可被 is_playwright_error 识别。"""


_FakePwError.__module__ = "playwright._impl._errors"

# Playwright 的超时异常真名就是 TimeoutError（playwright._impl._errors.TimeoutError），
# 判定同时看模块名与类名，故这里用同名类伪装。
_FakePwTimeout = type("TimeoutError", (Exception,), {})
_FakePwTimeout.__module__ = "playwright._impl._errors"


# ==================================================================
# 包围盒 / 落点校验
# ==================================================================
@pytest.mark.parametrize(
    ("box", "expected"),
    [
        (None, None),
        ({}, None),
        ({"x": 0, "y": 0, "width": 0, "height": 30}, None),
        ({"x": 0, "y": 0, "width": 80, "height": 0}, None),
        ({"x": 10, "y": 20, "width": 40, "height": 20}, (30.0, 30.0)),
    ],
)
def test_元素中心点计算(box, expected):
    assert pdd.element_center(box) == expected


def test_零尺寸元素判定为遮挡并给出明确原因():
    element = _FakeClickable(box={"x": 0, "y": 0, "width": 0, "height": 0})
    assert pdd.topmost_blocker(element, {"x": 0, "y": 0, "width": 0, "height": 0}) == "元素零尺寸（bounding box 为空）"


def test_落点命中目标自身即视为未遮挡():
    element = _FakeClickable(blockers=[None])
    assert pdd.topmost_blocker(element, element.bounding_box()) is None


def test_落点被其它元素覆盖时返回遮挡者描述():
    element = _FakeClickable(blockers=["div.rocket-tabs.login-tabs"])
    assert pdd.topmost_blocker(element, element.bounding_box()) == "div.rocket-tabs.login-tabs"


def test_浏览器内校验不可用时不臆断遮挡():
    element = _FakeClickable(blockers=[None], evaluate_raises=True)
    assert pdd.topmost_blocker(element, element.bounding_box()) is None


# ==================================================================
# 点击守卫：滚动居中 + 校验 + 归因
# ==================================================================
def test_未遮挡时点击一次成功():
    page = _FakeClickPage()
    element = _FakeClickable(blockers=[None])
    pdd.click_with_occlusion_guard(page, element)
    assert element.click_calls == 1


def test_被遮挡后调整滚动重试并最终点击成功():
    page = _FakeClickPage()
    element = _FakeClickable(blockers=["div.rocket-tabs.login-tabs", None])
    pdd.click_with_occlusion_guard(page, element)
    assert element.click_calls == 1, "第二次落点校验通过后才允许点击"
    assert page.mouse.wheels, "被遮挡时应按微调序列调整纵向滚动"


def test_始终被遮挡时抛明确错误并归因到遮挡者():
    page = _FakeClickPage()
    element = _FakeClickable(blockers=["div.rocket-tabs.login-tabs"])
    with pytest.raises(pdd.LoginElementBlockedError) as excinfo:
        pdd.click_with_occlusion_guard(page, element)
    message = str(excinfo.value)
    assert "遮挡" in message
    assert "div.rocket-tabs.login-tabs" in message, "必须写明遮挡者，不得笼统报「未定位到按钮」"
    assert "dump-login-page.py" in message
    assert element.click_calls == 0, "落点校验未通过时不得发起真实点击"


def test_零尺寸目标最终按遮挡归因():
    page = _FakeClickPage()
    element = _FakeClickable(box={"x": 0, "y": 0, "width": 0, "height": 0}, blockers=[None])
    with pytest.raises(pdd.LoginElementBlockedError) as excinfo:
        pdd.click_with_occlusion_guard(page, element)
    assert "元素零尺寸" in str(excinfo.value)
    assert element.click_calls == 0


def test_点击超时映射为超时错误():
    def _raise() -> None:
        raise _FakePwTimeout("locator.click: Timeout 30000ms exceeded.")

    page = _FakeClickPage()
    element = _FakeClickable(blockers=[None], on_click=_raise)
    with pytest.raises(pdd.LoginTimeoutError):
        pdd.click_with_occlusion_guard(page, element)


def test_点击被拦截映射为遮挡错误():
    # Playwright 点击超时的报错文本含 "intercepts pointer events"，应归入遮挡而非超时
    def _raise() -> None:
        raise _FakePwTimeout("locator.click: Timeout 30000ms exceeded. <div> intercepts pointer events")

    page = _FakeClickPage()
    element = _FakeClickable(blockers=[None], on_click=_raise)
    with pytest.raises(pdd.LoginElementBlockedError):
        pdd.click_with_occlusion_guard(page, element)


# ==================================================================
# 异常收敛：类型识别 + 错误 → 结论 → 状态 → 退出码
# ==================================================================
def test_按模块名识别playwright异常():
    assert pdd.is_playwright_error(_FakePwError("x")) is True
    assert pdd.is_playwright_error(RuntimeError("x")) is False
    assert pdd.is_playwright_timeout(_FakePwTimeout("x")) is True
    assert pdd.is_playwright_timeout(_FakePwError("x")) is False, "类型名不是 TimeoutError 时不算超时"


def test_其它playwright异常归类为页面错误():
    mapped = pdd.classify_browser_failure(_FakePwError("boom"), "账号输入")
    assert type(mapped) is pdd.LoginPageError
    assert "账号输入" in str(mapped)


def test_非playwright异常也归类为页面错误():
    mapped = pdd.classify_browser_failure(RuntimeError("boom"), "账号输入")
    assert type(mapped) is pdd.LoginPageError
    assert "账号输入" in str(mapped)


@pytest.mark.parametrize(
    ("exc", "expected_type", "expected_outcome", "expected_code"),
    [
        (
            ManualInterventionRequired(ChallengeType.CAPTCHA, "出现验证码", "请人工完成"),
            ManualInterventionRequired,
            LoginOutcome.MANUAL_REQUIRED,
            2,
        ),
        (pdd.LoginElementBlockedError("遮挡"), pdd.LoginElementBlockedError, LoginOutcome.FAILED, 1),
        (pdd.LoginTimeoutError("超时"), pdd.LoginTimeoutError, LoginOutcome.FAILED, 1),
        (pdd.LoginPageError("定位失败"), pdd.LoginPageError, LoginOutcome.FAILED, 1),
        (BrowserLaunchError("启动失败"), BrowserLaunchError, LoginOutcome.FAILED, 1),
    ],
)
def test_异常到退出码与状态的映射链(exc, expected_type, expected_outcome, expected_code):
    assert isinstance(exc, expected_type)
    outcome = pdd.outcome_for_error(exc)
    assert outcome is expected_outcome
    assert pdd.exit_code_for_error(exc) == expected_code
    # 落库状态与退出码由同一结论推导，三者必须自洽
    result = pdd.LoginResult(outcome=outcome, login_status=pdd._OUTCOME_TO_STATUS[outcome])
    assert result.login_status is (LoginStatus.MANUAL_REQUIRED if expected_code == 2 else LoginStatus.FAILED)
    assert result.exit_code == expected_code


# ==================================================================
# 服务编排收敛：run() 不得抛出裸异常，失败必须落库
# ==================================================================
class _FakeCursor:
    pass


class _FakeSession:
    def __enter__(self) -> _FakeCursor:
        return _FakeCursor()

    def __exit__(self, *exc_info) -> bool:
        return False


class _FakeConnection:
    def session(self) -> _FakeSession:
        return _FakeSession()


class _SilentHumanizer:
    pass


def _service() -> tuple[pdd.PddLoginService, list]:
    """构造仅覆盖 run() 收敛路径所需字段的服务与「已落库会话」记录表。"""
    settings = SimpleNamespace(
        login=SimpleNamespace(auto_login_enabled=True, probe_timeout_ms=0, submit_wait_ms=0),
        site=SimpleNamespace(login_url="https://mdkd.pinduoduo.com/login"),
    )
    account = PddAccount(site="https://mdkd.pinduoduo.com/login", account="16626369983", password="pw-for-test")
    recorded: list = []
    service = pdd.PddLoginService(settings, account, connection=_FakeConnection(), humanizer=_SilentHumanizer())
    return service, recorded


@pytest.fixture(autouse=True)
def _capture_persist(monkeypatch):
    recorded: list = []
    monkeypatch.setattr(pdd.repository, "upsert_login_session", lambda cursor, session: recorded.append(session))
    yield recorded


def test_run把登录错误收敛为失败结果并落库FAILED(monkeypatch, _capture_persist):
    service, _ = _service()

    def _boom():
        raise pdd.LoginElementBlockedError("提交按钮被其它元素遮挡")

    monkeypatch.setattr(service, "_run_flow", _boom)
    result = service.run()

    assert result.outcome is LoginOutcome.FAILED
    assert result.login_status is LoginStatus.FAILED
    assert "遮挡" in result.fail_reason
    assert result.exit_code == 1
    assert [s.login_status for s in _capture_persist] == [LoginStatus.FAILED]


def test_run把挑战收敛为需人工介入并落库MANUAL_REQUIRED(monkeypatch, _capture_persist):
    service, _ = _service()

    def _challenge():
        raise ManualInterventionRequired(ChallengeType.SLIDER, "出现滑块验证", "请人工完成滑块")

    monkeypatch.setattr(service, "_run_flow", _challenge)
    with pytest.raises(ManualInterventionRequired):
        service.run()
    assert [s.login_status for s in _capture_persist] == [LoginStatus.MANUAL_REQUIRED]


def test_run把浏览器错误收敛为失败结果(monkeypatch, _capture_persist):
    service, _ = _service()

    def _boom():
        raise BrowserLaunchError("启动持久化上下文失败")

    monkeypatch.setattr(service, "_run_flow", _boom)
    result = service.run()

    assert result.login_status is LoginStatus.FAILED
    assert "浏览器" in result.fail_reason


def test_fail_reason落库前脱敏(monkeypatch, _capture_persist):
    service, _ = _service()

    def _boom():
        raise pdd.LoginPageError("底层异常：password=pw-for-test")

    monkeypatch.setattr(service, "_run_flow", _boom)
    service.run()

    assert "pw-for-test" not in _capture_persist[0].fail_reason


# ==================================================================
# 提交降级链（2026-09-22 采集机实测：点击落点被遮挡，硬等 30s）
# ==================================================================
class _FakeMouseMove:
    def __init__(self) -> None:
        self.moves: list[tuple] = []

    def move(self, x, y, steps=None) -> None:
        self.moves.append((x, y))


class _FakeKeyboard:
    def __init__(self) -> None:
        self.presses: list[str] = []

    def press(self, key: str) -> None:
        self.presses.append(key)


class _ChainPage:
    """降级链假页面：只实现遮挡消除与 URL 读取（生效观测由 monkeypatch 打桩）。"""

    def __init__(self, url: str = "https://mdkd.pinduoduo.com/login") -> None:
        self.url = url
        self.mouse = _FakeMouseMove()
        self.keyboard = _FakeKeyboard()


class _FakePassword:
    def __init__(self) -> None:
        self.presses: list[tuple[str, dict]] = []

    def press(self, key: str, **kwargs) -> None:
        self.presses.append((key, kwargs))


class _ChainSubmit:
    """提交按钮假元素：同时支持守卫的包围盒/命中测试与各条路径的点击方式。"""

    def __init__(self, blockers: list[str | None] | None = None, click_error: Exception | None = None) -> None:
        self._blockers = blockers if blockers is not None else [None]
        self._hit = 0
        self._click_error = click_error
        self.click_calls: list[dict] = []
        self.dispatch_calls: list[tuple[str, dict]] = []

    def bounding_box(self) -> dict:
        return {"x": 100.0, "y": 200.0, "width": 80.0, "height": 30.0}

    def evaluate(self, script: str, arg=None):
        if "elementFromPoint" not in script:
            return None
        index = min(self._hit, len(self._blockers) - 1)
        self._hit += 1
        return self._blockers[index]

    def click(self, **kwargs) -> None:
        self.click_calls.append(kwargs)
        if self._click_error is not None:
            raise self._click_error

    def dispatch_event(self, event_type: str, **kwargs) -> None:
        self.dispatch_calls.append((event_type, kwargs))


def _chain_settings(path_timeout: int = 10, probe: int = 0) -> SimpleNamespace:
    return SimpleNamespace(login=SimpleNamespace(submit_path_timeout_ms=path_timeout, submit_effect_probe_ms=probe))


def test_降级链固定为四条路径且顺序为回车点击强制合成():
    assert pdd.SUBMIT_PATH_COUNT == 4
    assert [label for label, _ in pdd._SUBMIT_PATHS] == ["A-回车提交", "B-真实点击", "C-强制点击", "D-合成点击"]


def test_降级链首条回车生效即返回且不再尝试后续路径(monkeypatch):
    monkeypatch.setattr(
        pdd, "observe_submit_effect", lambda page, settings, baseline_url, timeout_ms: "出现工作台特征元素"
    )
    page = _ChainPage()
    submit = _ChainSubmit()
    password = _FakePassword()

    label = pdd.submit_with_degradation(
        page, submit, password, baseline_url=page.url, settings=_chain_settings(path_timeout=10)
    )

    assert label == "A-回车提交"
    assert password.presses == [("Enter", {"timeout": 10})], "路径 A 必须在密码框上回车，且透传短超时"
    assert submit.click_calls == [] and submit.dispatch_calls == [], "首条生效后不得再点按钮"
    assert page.keyboard.presses == ["Escape"], "提交前应收起潜在浮层"
    assert page.mouse.moves == [(0, 0)], "提交前应把鼠标移开以收起 hover 态"


def test_首条未生效时逐级降级并透传短超时(monkeypatch):
    signals = iter([None, None, "出现 slider 挑战"])
    monkeypatch.setattr(pdd, "observe_submit_effect", lambda *a, **k: next(signals))
    page = _ChainPage()
    submit = _ChainSubmit()
    password = _FakePassword()

    label = pdd.submit_with_degradation(
        page, submit, password, baseline_url=page.url, settings=_chain_settings(path_timeout=7)
    )

    assert label == "C-强制点击"
    assert password.presses == [("Enter", {"timeout": 7})]
    assert {"timeout": 7} in submit.click_calls, "路径 B 必须透传短超时（不得吃默认 30s）"
    assert {"force": True, "timeout": 7} in submit.click_calls, "路径 C 为强制点击"
    assert submit.dispatch_calls == [], "路径 C 已生效，不应走到合成点击"


def test_单条路径抛错不中断降级链(monkeypatch):
    # 路径 B 的落点遮挡预校验会抛 LoginElementBlockedError；此时应记录后继续降级
    signals = iter([None, "出现工作台特征元素"])
    monkeypatch.setattr(pdd, "observe_submit_effect", lambda *a, **k: next(signals))
    page = _ChainPage()
    submit = _ChainSubmit(blockers=["div.rocket-tabs.login-tabs"])  # 始终被遮挡 → 路径 B 必抛
    password = _FakePassword()

    label = pdd.submit_with_degradation(
        page, submit, password, baseline_url=page.url, settings=_chain_settings()
    )

    assert label == "C-强制点击"
    assert submit.click_calls == [{"force": True, "timeout": 10}], "路径 B 被遮挡抛错、路径 C 才真正点击"


def test_全部路径未生效时抛遮挡错误且点明每条路径(monkeypatch):
    monkeypatch.setattr(pdd, "observe_submit_effect", lambda *a, **k: None)
    page = _ChainPage()
    submit = _ChainSubmit()
    password = _FakePassword()

    with pytest.raises(pdd.LoginElementBlockedError) as excinfo:
        pdd.submit_with_degradation(page, submit, password, baseline_url=page.url, settings=_chain_settings())

    message = str(excinfo.value)
    assert "A-回车提交" in message and "D-合成点击" in message, "失败必须点明尝试过哪些路径"
    assert "遮挡" in message and "dump-login-page.py" in message
    assert submit.dispatch_calls == [("click", {"timeout": 10})], "合成点击是最后兜底"


def test_四条路径动作的单条调用契约():
    page = _ChainPage()
    submit = _ChainSubmit()
    password = _FakePassword()

    pdd._submit_press_enter(page, submit, password, 5)
    assert password.presses == [("Enter", {"timeout": 5})]

    pdd._submit_click(page, submit, password, 6)
    assert submit.click_calls == [{"timeout": 6}], "路径 B 走守卫后仍是普通点击"

    pdd._submit_force_click(page, submit, password, 7)
    assert submit.click_calls[-1] == {"force": True, "timeout": 7}

    pdd._submit_dispatch(page, submit, password, 8)
    assert submit.dispatch_calls == [("click", {"timeout": 8})]


class _BoomPage:
    class _Boom:
        def move(self, *a, **k):
            raise RuntimeError("鼠标不可用")

        def press(self, *a, **k):
            raise RuntimeError("键盘不可用")

    def __init__(self) -> None:
        self.mouse = self._Boom()
        self.keyboard = self._Boom()


def test_遮挡消除失败不得中断提交():
    # Escape 能否关闭自定义浮层本身不确定；无论如何都不应因它抛错而中断降级链
    pdd._dismiss_overlays(_BoomPage())


# ==================================================================
# 生效信号判定（纯函数）
# ==================================================================
@pytest.mark.parametrize(
    ("kwargs", "expected"),
    [
        ({"challenge": ChallengeType.SLIDER}, "挑战"),
        ({"url": "https://mdkd.pinduoduo.com/home"}, "地址变化"),
        ({"has_workbench_marker": True}, "工作台"),
        ({"credential_error": True}, "凭据错误"),
        ({"has_login_form": False}, "登录表单已消失"),
        ({}, None),
    ],
)
def test_生效信号按可观测信号判定(kwargs, expected):
    base = dict(
        url="https://mdkd.pinduoduo.com/login",
        baseline_url="https://mdkd.pinduoduo.com/login",
        has_workbench_marker=False,
        has_login_form=True,
        challenge=None,
        credential_error=False,
    )
    base.update(kwargs)
    signal = pdd.submit_effect_signal(**base)
    if expected is None:
        assert signal is None, "点了没反应必须返回 None，绝不能当成成功"
    else:
        assert expected in signal


def test_挑战优先级高于其它信号():
    signal = pdd.submit_effect_signal(
        url="https://mdkd.pinduoduo.com/home",
        baseline_url="https://mdkd.pinduoduo.com/login",
        has_workbench_marker=True,
        has_login_form=True,
        challenge=ChallengeType.SMS,
        credential_error=True,
    )
    assert "挑战" in signal


# ==================================================================
# 填表自检（仅统计字符数，密码值永不落日志/异常）
# ==================================================================
class _FakeInput:
    def __init__(self, value: str | None = None, error: Exception | None = None) -> None:
        self._value = value
        self._error = error

    def input_value(self):
        if self._error is not None:
            raise self._error
        return self._value


def test_自检通过时只返回字符数():
    assert pdd.verify_credentials_filled(_FakeInput("13800000000"), _FakeInput("pw-secret")) == (11, 9)


def test_账号框为空时自检报错并指向选择器文件():
    with pytest.raises(pdd.LoginPageError) as excinfo:
        pdd.verify_credentials_filled(_FakeInput(""), _FakeInput("pw-secret"))
    message = str(excinfo.value)
    assert "账号" in message and "空" in message
    assert "selectors.py" in message


def test_密码框为空时自检报错并指向选择器文件():
    with pytest.raises(pdd.LoginPageError) as excinfo:
        pdd.verify_credentials_filled(_FakeInput("13800000000"), _FakeInput(""))
    message = str(excinfo.value)
    assert "密码" in message and "空" in message
    assert "selectors.py" in message


def test_自检失败信息绝不包含密码值():
    # 红线：即使密码框有值也要报错，也绝不能把密码值写进异常
    with pytest.raises(pdd.LoginPageError) as excinfo:
        pdd.verify_credentials_filled(_FakeInput(""), _FakeInput("pw-must-not-leak"))
    assert "pw-must-not-leak" not in str(excinfo.value)


def test_自检日志只记长度不记密码值(caplog):
    caplog.set_level(logging.INFO, logger="collector.login")
    pdd.verify_credentials_filled(_FakeInput("13800000000"), _FakeInput("pw-must-not-leak"))
    assert "pw-must-not-leak" not in caplog.text, "密码值严禁写入日志"
    assert "仅记录长度" in caplog.text


def test_输入框取值失败时映射为页面错误():
    with pytest.raises(pdd.LoginPageError):
        pdd.verify_credentials_filled(_FakeInput(error=_FakePwError("读取失败")), _FakeInput("pw"))

