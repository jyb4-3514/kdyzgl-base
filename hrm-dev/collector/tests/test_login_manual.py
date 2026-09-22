# -*- coding: utf-8 -*-
"""人工介入登录（``login --manual``）的纯逻辑测试（假页面 / 假时钟，不连网、不开浏览器）。

起因（真实缺陷，2026-09-22 采集机实测）：
    ``login --run`` 检出 slider 挑战后立即抛 :class:`ManualInterventionRequired` 并退出，
    ``persistent_context`` 在 ``finally`` 里 ``context.close()`` 把浏览器一并关掉——
    提示却让操作人"在采集机浏览器窗口中手动完成滑块验证"，可那个窗口已经没了，
    人工根本无从介入，登录永远完不成（提示文案与真实行为不符）。

本组用例钉死：
1. ``login --manual`` 四条结束分支：成功 / 超时 / 凭据错误 / 浏览器被人工关闭；
2. 轮询**只读**：不点击、不输入、不聚焦（用"写操作即记录"的假元素锁死）；
3. ``--manual`` 与 ``--run`` 互不干扰（CLI 互斥 + ``run`` 不转入 manual）；
4. ``browser.headless=true`` 时明确报错（不静默改有头）；
5. ``--no-prefill`` 完全不触碰页面；预填失败降级为人工全填，且原因已脱敏。
"""
from __future__ import annotations

from types import SimpleNamespace

import pytest

from collector.config.pdd_account import PddAccount
from collector.config.settings import ConfigError, SETTINGS_EXAMPLE_FILE, load_settings
from collector.login import pdd, selectors
from collector.__main__ import _login_args_error, build_parser


# ==================================================================
# 假对象
# ==================================================================
class _FakeClock:
    """假时钟：把轮询的等待压缩为瞬时，便于确定性断言迭代次数。"""

    def __init__(self) -> None:
        self.t = 0.0

    def now(self) -> float:
        return self.t

    def sleep(self, seconds: float) -> None:
        self.t += seconds


# Playwright 的关闭异常真名就是 TargetClosedError（playwright._impl._errors）；
# 判定同时看模块名与类名，故这里用同名类伪装。
_FakeTargetClosedError = type("TargetClosedError", (Exception,), {})
_FakeTargetClosedError.__module__ = "playwright._impl._errors"


class _ReadElement:
    """只读假元素：点击/输入/聚焦都只是**记录**，供断言「轮询与预填不写页面」。"""

    def __init__(self, mutations: list, visible: bool = True, text: str = "", value: str = "") -> None:
        self._mutations = mutations
        self._visible = visible
        self._text = text
        self._value = value

    def is_visible(self) -> bool:
        return self._visible

    def inner_text(self, timeout=None) -> str:  # noqa: ANN001 - 对齐 Playwright 签名
        return self._text

    def input_value(self) -> str:
        return self._value

    def bounding_box(self) -> dict:
        return {"x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0}

    def evaluate(self, *args, **kwargs):
        return None

    def click(self, *args, **kwargs) -> None:
        self._mutations.append("click")

    def fill(self, *args, **kwargs) -> None:
        self._mutations.append("fill")

    def press(self, *args, **kwargs) -> None:
        self._mutations.append("press")

    def press_sequentially(self, *args, **kwargs) -> None:
        self._mutations.append("press_sequentially")

    def focus(self) -> None:
        self._mutations.append("focus")


class _ReadLocator:
    def __init__(self, elements: list[_ReadElement]) -> None:
        self._elements = elements

    def count(self) -> int:
        return len(self._elements)

    def nth(self, index: int) -> _ReadElement:
        return self._elements[index]

    def inner_text(self, timeout=None) -> str:  # noqa: ANN001 - 对齐 Playwright 签名
        return self._elements[0].inner_text(timeout) if self._elements else ""


class _ReadPage:
    """只读假页面：按「kind|值」注册匹配元素；正文走 ``locator("body")``。"""

    def __init__(
        self,
        *,
        mutations: list | None = None,
        matches: dict | None = None,
        body_text: str = "",
        closed: bool = False,
        url: str = "https://mdkd.pinduoduo.com/login",
    ) -> None:
        self.mutations = mutations if mutations is not None else []
        self._matches = matches or {}
        self._body_text = body_text
        self._closed = closed
        self.url = url
        self.queries: list[str] = []

    def is_closed(self) -> bool:
        return self._closed

    def locator(self, value: str) -> _ReadLocator:
        self.queries.append(f"css|{value}")
        if value == "body":
            return _ReadLocator([_ReadElement(self.mutations, visible=True, text=self._body_text)])
        return _ReadLocator(self._matches.get(value, []))

    def get_by_text(self, value: str, exact: bool = False) -> _ReadLocator:
        key = f"text|{value}|{exact}"
        self.queries.append(key)
        return _ReadLocator(self._matches.get(key, []))

    def get_by_role(self, role: str, name: str | None = None) -> _ReadLocator:
        key = f"role|{role}|{name}"
        self.queries.append(key)
        return _ReadLocator(self._matches.get(key, []))


class _RecordingHumanizer:
    """假人工节奏：记录被输入的明文值，但不触碰页面元素。"""

    def __init__(self) -> None:
        self.typed: list[str] = []

    def move_to(self, page, element) -> None:  # noqa: ANN001
        pass

    def type_like_human(self, element, value: str) -> None:  # noqa: ANN001
        self.typed.append(value)

    def pause_field(self) -> None:
        pass

    def pause_before_submit(self) -> None:
        pass


# 与 selectors 的作用域取值保持一致的查找键
_WORKBENCH_KEY = 'div[role="menuitem"]'
_ACCOUNT_KEY = f"{selectors.ACTIVE_PANEL.value} #mobile:visible"
_PASSWORD_KEY = f"{selectors.ACTIVE_PANEL.value} input.password-input:visible"
_SWITCHER_KEY = "text|密码登录|True"


def _read_settings(probe_timeout_ms: int = 0) -> SimpleNamespace:
    return SimpleNamespace(
        login=SimpleNamespace(probe_timeout_ms=probe_timeout_ms),
        site=SimpleNamespace(allowed_hosts=("mdkd.pinduoduo.com", "mcmd.pinduoduo.com")),
    )


def _service_settings(headless: bool = False, wait_minutes: int = 10, poll_interval_s: int = 5) -> SimpleNamespace:
    return SimpleNamespace(
        browser=SimpleNamespace(headless=headless),
        login=SimpleNamespace(
            probe_timeout_ms=0,
            manual_wait_minutes=wait_minutes,
            manual_poll_interval_s=poll_interval_s,
            auto_login_enabled=True,
        ),
        site=SimpleNamespace(login_url="https://mdkd.pinduoduo.com/login"),
    )


def _account() -> PddAccount:
    return PddAccount(site="https://mdkd.pinduoduo.com/login", account="16626369983", password="pw-secret")


def _service(headless: bool = False, humanizer=None) -> pdd.PddLoginService:  # noqa: ANN001
    return pdd.PddLoginService(
        _service_settings(headless=headless),
        _account(),
        humanizer=humanizer or _RecordingHumanizer(),
    )


@pytest.fixture(autouse=True)
def _no_real_wait(monkeypatch):
    # 单测无需真实等待：探测预算与轮询间隔置 0（等待语义已在生产代码内实现）
    monkeypatch.setattr(pdd, "_LOGIN_MODE_PROBE_MS", 0)
    monkeypatch.setattr(pdd, "_SCAN_POLL_INTERVAL_MS", 0)


# ==================================================================
# 浏览器关闭的识别
# ==================================================================
def test_识别playwright的targetclosed异常():
    assert pdd.is_browser_closed_error(_FakeTargetClosedError("Target page, context or browser has been closed"))


def test_识别项目内的browserclosed信号():
    assert pdd.is_browser_closed_error(pdd.BrowserClosedError("窗口已关闭"))


def test_普通异常不算浏览器关闭():
    assert not pdd.is_browser_closed_error(RuntimeError("boom"))


def test_同名但非playwright模块的异常不算浏览器关闭():
    class TargetClosedError(Exception):  # noqa: N801 - 故意同名，验证模块名判据
        pass

    assert not pdd.is_browser_closed_error(TargetClosedError("x"))


def test_page_is_closed优先用官方接口():
    assert pdd.page_is_closed(SimpleNamespace(is_closed=lambda: True)) is True
    assert pdd.page_is_closed(SimpleNamespace(is_closed=lambda: False)) is False


def test_is_closed不可用时用只读往返兜底():
    class _ClosedByTitle:
        def is_closed(self):
            raise RuntimeError("接口不可用")

        def title(self):
            raise _FakeTargetClosedError("Target page, context or browser has been closed")

    assert pdd.page_is_closed(_ClosedByTitle()) is True


def test_只读往返正常时判定页面存活():
    class _Alive:
        def is_closed(self):
            raise RuntimeError("接口不可用")

        def title(self):
            return "代收点"

    assert pdd.page_is_closed(_Alive()) is False


def test_只读往返抛其它异常时不臆断为关闭():
    class _OtherBoom:
        def is_closed(self):
            raise RuntimeError("接口不可用")

        def title(self):
            raise RuntimeError("别的错误")

    assert pdd.page_is_closed(_OtherBoom()) is False


# ==================================================================
# 轮询四条结束分支
# ==================================================================
def test_轮询检出登录成功即返回():
    seq = iter([pdd.ManualSnapshot(False, False), pdd.ManualSnapshot(True, False)])
    clock = _FakeClock()
    outcome = pdd.poll_manual_login(
        observe=lambda: next(seq),
        wait_seconds=600,
        poll_interval_s=5,
        now=clock.now,
        sleep=clock.sleep,
    )
    assert outcome is pdd.ManualPollOutcome.LOGGED_IN


def test_轮询等满等待窗口判超时():
    clock = _FakeClock()
    calls: list[int] = []

    def observe() -> pdd.ManualSnapshot:
        calls.append(1)
        return pdd.ManualSnapshot(False, False)

    outcome = pdd.poll_manual_login(
        observe=observe, wait_seconds=10, poll_interval_s=5, now=clock.now, sleep=clock.sleep
    )

    assert outcome is pdd.ManualPollOutcome.TIMEOUT
    assert len(calls) == 3, "10 秒窗口 / 5 秒间隔：应在 t=0、5、10 各检测一次后判超时"


def test_轮询检出凭据错误即返回():
    clock = _FakeClock()
    outcome = pdd.poll_manual_login(
        observe=lambda: pdd.ManualSnapshot(False, True),
        wait_seconds=600,
        poll_interval_s=5,
        now=clock.now,
        sleep=clock.sleep,
    )
    assert outcome is pdd.ManualPollOutcome.CREDENTIAL_ERROR


def test_轮询把浏览器关闭归为关窗而非超时():
    def observe() -> pdd.ManualSnapshot:
        raise _FakeTargetClosedError("Target page, context or browser has been closed")

    clock = _FakeClock()
    outcome = pdd.poll_manual_login(
        observe=observe, wait_seconds=600, poll_interval_s=5, now=clock.now, sleep=clock.sleep
    )
    assert outcome is pdd.ManualPollOutcome.BROWSER_CLOSED


def test_轮询不吞其它异常():
    def observe() -> pdd.ManualSnapshot:
        raise RuntimeError("boom")

    with pytest.raises(RuntimeError):
        pdd.poll_manual_login(observe=observe, wait_seconds=1, poll_interval_s=1)


# ==================================================================
# 只读检测：判定复用 judge_login_result，且绝不写页面
# ==================================================================
def test_只读检测到工作台特征即判定已登录():
    mutations: list = []
    page = _ReadPage(mutations=mutations, matches={_WORKBENCH_KEY: [_ReadElement(mutations, visible=True)]})
    snapshot = pdd.read_manual_snapshot(page, _read_settings())
    assert snapshot.logged_in is True
    assert mutations == [], "只读检测不得点击/输入/聚焦任何元素"


def test_只读检测到登录表单仍在即未登录():
    mutations: list = []
    page = _ReadPage(mutations=mutations, matches={_PASSWORD_KEY: [_ReadElement(mutations, visible=True)]})
    snapshot = pdd.read_manual_snapshot(page, _read_settings())
    assert snapshot.logged_in is False
    assert mutations == []


def test_只读检测到挑战既不判成功也不判凭据错误():
    page = _ReadPage(body_text="请拖动滑块完成验证")
    snapshot = pdd.read_manual_snapshot(page, _read_settings())
    assert snapshot.logged_in is False
    assert snapshot.credential_error is False


def test_只读检测到凭据错误单独识别():
    page = _ReadPage(body_text="账号或密码错误，请重新输入")
    snapshot = pdd.read_manual_snapshot(page, _read_settings())
    assert snapshot.credential_error is True
    assert snapshot.logged_in is False


def test_页面已关闭时抛关窗信号():
    mutations: list = []
    page = _ReadPage(mutations=mutations, closed=True)
    with pytest.raises(pdd.BrowserClosedError):
        pdd.read_manual_snapshot(page, _read_settings())
    assert mutations == []


def test_整个轮询过程不产生任何写操作():
    mutations: list = []
    page = _ReadPage(mutations=mutations, matches={_PASSWORD_KEY: [_ReadElement(mutations, visible=True)]})
    clock = _FakeClock()

    outcome = pdd.poll_manual_login(
        observe=lambda: pdd.read_manual_snapshot(page, _read_settings()),
        wait_seconds=10,
        poll_interval_s=5,
        now=clock.now,
        sleep=clock.sleep,
    )

    assert outcome is pdd.ManualPollOutcome.TIMEOUT
    assert mutations == [], "轮询只读，不得干扰现场人工操作"


# ==================================================================
# 服务编排：headless 明确报错 / 预填 / no-prefill
# ==================================================================
def test_headless配置下人工登录明确报错且不静默改有头():
    service = _service(headless=True)
    with pytest.raises(pdd.ManualLoginConfigError) as excinfo:
        service.manual()
    message = str(excinfo.value)
    assert "browser.headless" in message
    assert "false" in message


def test_no_prefill完全不触碰页面():
    mutations: list = []
    page = _ReadPage(mutations=mutations)
    humanizer = _RecordingHumanizer()
    service = _service(humanizer=humanizer)

    note = service._prepare_manual(page, prefill=False)

    assert note is not None and "no-prefill" in note
    assert page.queries == [], "跳过硬预填时连账号/密码框都不应定位"
    assert humanizer.typed == []
    assert mutations == []


def test_预填成功后返回None且不提交():
    mutations: list = []
    page = _ReadPage(
        mutations=mutations,
        matches={
            _ACCOUNT_KEY: [_ReadElement(mutations, visible=True, value="16626369983")],
            _PASSWORD_KEY: [_ReadElement(mutations, visible=True, value="pw-secret")],
        },
    )
    humanizer = _RecordingHumanizer()
    service = _service(humanizer=humanizer)

    assert service._prepare_manual(page, prefill=True) is None
    assert humanizer.typed == ["16626369983", "pw-secret"]
    assert mutations == [], "预填只填不提交，不得点击任何按钮"


def test_预填失败降级为人工全填并给出原因():
    mutations: list = []
    page = _ReadPage(mutations=mutations)  # 密码框不可见、页签也不存在 → 切页签阶段即失败
    service = _service()

    note = service._prepare_manual(page, prefill=True)

    assert note is not None and note.startswith("预填失败")
    assert "密码登录" in note, "降级原因必须说清卡在哪一步"


def test_预填失败原因已脱敏不含密码值():
    mutations: list = []

    class _BoomSwitcher(_ReadElement):
        def click(self, *args, **kwargs) -> None:
            raise RuntimeError("boom password=pw-secret")

    page = _ReadPage(mutations=mutations, matches={_SWITCHER_KEY: [_BoomSwitcher(mutations, visible=True)]})
    service = _service()

    note = service._prepare_manual(page, prefill=True)

    assert note is not None
    assert "pw-secret" not in note, "预填降级原因不得带出敏感值"


# ==================================================================
# manual 与 run 互不干扰；manual 绝非默认
# ==================================================================
def test_run检出挑战不会自动转入manual(monkeypatch):
    service = _service()

    def _challenge() -> None:
        raise pdd.ManualInterventionRequired(pdd.ChallengeType.SLIDER, "出现滑块", "改用 --manual")

    def _forbid_manual(*args, **kwargs) -> None:
        raise AssertionError("--run 不得自动调用人工登录模式")

    monkeypatch.setattr(service, "_run_flow", _challenge)
    monkeypatch.setattr(service, "manual", _forbid_manual)

    with pytest.raises(pdd.ManualInterventionRequired):
        service.run()


def test_cli必须显式指定一种登录模式():
    with pytest.raises(SystemExit):
        build_parser().parse_args(["login"])


def test_cli中manual与run互斥():
    with pytest.raises(SystemExit):
        build_parser().parse_args(["login", "--run", "--manual"])


def test_cli解析manual与no_prefill():
    args = build_parser().parse_args(["login", "--manual", "--no-prefill"])
    assert args.manual is True
    assert args.no_prefill is True
    assert _login_args_error(args) is None


def test_manual不是默认模式():
    args = build_parser().parse_args(["login", "--run"])
    assert args.manual is False


def test_no_prefill与run同用被判为参数错误():
    args = build_parser().parse_args(["login", "--run", "--no-prefill"])
    error = _login_args_error(args)
    assert error is not None and "--manual" in error


def test_run的挑战指引指向manual且不声称窗口内手动完成():
    for challenge, guidance in pdd._GUIDANCE_BY_CHALLENGE.items():
        assert "login --manual" in guidance, f"{challenge.value} 的指引必须指向 --manual"
        assert "浏览器窗口中手动" not in guidance, "窗口会被关闭，不得再声称在窗口中手动完成"


# ==================================================================
# 配置项
# ==================================================================
def test_示例配置含人工登录参数且为正():
    settings = load_settings(SETTINGS_EXAMPLE_FILE)
    assert settings.login.manual_wait_minutes == 10
    assert settings.login.manual_poll_interval_s == 5


def test_人工登录参数非正时配置报错(tmp_path):
    path = tmp_path / "settings.toml"
    path.write_text("[login]\nmanual_wait_minutes = 0\n", encoding="utf-8")
    with pytest.raises(ConfigError) as excinfo:
        load_settings(path)
    assert "manual_wait_minutes" in str(excinfo.value)
