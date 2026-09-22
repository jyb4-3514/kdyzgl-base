# -*- coding: utf-8 -*-
"""选择器表校准结果的纯逻辑测试（不连网、不开浏览器）。

覆盖：实测条目的优先级排序、CSS 候选的可见性约束（``:visible``）是否齐备、文本候选的精确匹配标记。
"""
from __future__ import annotations

import pytest

from collector.login import selectors

_GROUPS = (
    selectors.LOGIN_MODE_SWITCHERS,
    selectors.ACCOUNT_INPUTS,
    selectors.PASSWORD_INPUTS,
    selectors.SUBMIT_BUTTONS,
)

_GROUP_PARAMS = [
    pytest.param(selectors.LOGIN_MODE_SWITCHERS, id="登录方式切换"),
    pytest.param(selectors.ACCOUNT_INPUTS, id="账号输入框"),
    pytest.param(selectors.PASSWORD_INPUTS, id="密码输入框"),
    pytest.param(selectors.SUBMIT_BUTTONS, id="提交按钮"),
]

_CSS_GROUP_PARAMS = [
    pytest.param(selectors.ACCOUNT_INPUTS, id="账号输入框"),
    pytest.param(selectors.PASSWORD_INPUTS, id="密码输入框"),
    pytest.param(selectors.SUBMIT_BUTTONS, id="提交按钮"),
]


# ---------------- 实测条目取值 ----------------
def test_登录方式切换首选实测的密码登录页签且精确匹配():
    first = selectors.LOGIN_MODE_SWITCHERS[0]
    assert (first.kind, first.value, first.exact, first.verified) == ("text", "密码登录", True, True)


def test_账号输入框首选实测的可见mobile框():
    first = selectors.ACCOUNT_INPUTS[0]
    assert (first.kind, first.value, first.verified) == ("css", "#mobile:visible", True)


def test_密码输入框首选实测的password_input与id定位():
    assert [c.value for c in selectors.PASSWORD_INPUTS[:2]] == ["input.password-input:visible", "#password:visible"]
    assert all(c.verified for c in selectors.PASSWORD_INPUTS[:2])


def test_提交按钮首选实测的login_btn():
    first = selectors.SUBMIT_BUTTONS[0]
    assert (first.kind, first.value, first.verified) == ("css", "button.login-btn:visible", True)


def test_实测条目共五条且依据写明采集机实测():
    measured = [candidate for group in _GROUPS for candidate in group if candidate.verified]
    assert len(measured) == 5  # 页签 1 + 账号 1 + 密码 2 + 提交 1
    for candidate in measured:
        assert "采集机实测" in candidate.basis


# ---------------- 排序与降级链 ----------------
@pytest.mark.parametrize("candidates", _GROUP_PARAMS)
def test_实测条目排在未核实条目之前(candidates):
    flags = [c.verified for c in candidates]
    assert flags == sorted(flags, reverse=True), "实测条目必须优先，未核实候选只能兜底"


@pytest.mark.parametrize("candidates", _GROUP_PARAMS)
def test_每条候选都写明依据(candidates):
    assert candidates, "候选组不应为空"
    for candidate in candidates:
        assert candidate.basis.strip(), f"{candidate.value!r} 缺少依据说明"


# ---------------- 可见性约束 ----------------
@pytest.mark.parametrize("candidates", _CSS_GROUP_PARAMS)
def test_所有css候选都带可见性约束(candidates):
    css_values = [c.value for c in candidates if c.kind == "css"]
    assert css_values, "应保留通用 CSS 兜底候选"
    for value in css_values:
        assert value.endswith(":visible"), (
            f"{value!r} 缺少 :visible 约束：目标站同 id / 同 class 元素各挂两份，会命中隐藏的那一份"
        )


def test_同id两份的候选必须加可见性约束():
    # 实测：短信页签与密码页签面板并存，#mobile 与 button.login-btn 各两份
    duplicated = [
        *selectors.ACCOUNT_INPUTS[:1],
        *selectors.PASSWORD_INPUTS[:2],
        *selectors.SUBMIT_BUTTONS[:1],
    ]
    for candidate in duplicated:
        assert ":visible" in candidate.value


# ---------------- 文本精确匹配 ----------------
def test_提交按钮文本兜底必须精确匹配():
    text_candidates = [c for c in selectors.SUBMIT_BUTTONS if c.kind == "text"]
    assert text_candidates, "应保留文本兜底候选"
    assert all(c.exact for c in text_candidates), "「登录」非精确匹配会命中「短信登录」等页签文案"


def test_新建候选的默认标记():
    # exact 默认 False：通用文案不应被默认为整串匹配；verified 默认 False：未实测不得自称已核实
    candidate = selectors.SelectorCandidate("text", "账号登录", "测试构造")
    assert candidate.exact is False
    assert candidate.verified is False


# ---------------- 未改动项 ----------------
def test_工作台特征保持原样未改动():
    assert [c.value for c in selectors.WORKBENCH_MARKERS] == ['div[role="menuitem"]', "运单查询"]


def test_describe区分已核实与未核实():
    assert "已核实" in selectors.ACCOUNT_INPUTS[0].describe()
    assert "未核实" in selectors.ACCOUNT_INPUTS[1].describe()
