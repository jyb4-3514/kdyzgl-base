# -*- coding: utf-8 -*-
"""回归网：登录页自身控件文案不得被误判为风控挑战。

起因（真实缺陷，2026-09-22 采集机实测）：
    mdkd 密码登录页存在「获取验证码」按钮，其文本包含「验证码」。
    挑战信号表 CHALLENGE_SIGNALS 里的通用模式「验证码」会命中它，
    导致**一进页面就判定为 captcha 挑战**并抛 ManualInterventionRequired（退出码 2），
    登录流程永远走不通（实测现象：未提交表单即报 captcha）。

修法：匹配前先剔除登录页自身控件文案（见 pdd._BENIGN_PAGE_PHRASES）。
本组用例把该行为钉死，防止日后有人"顺手"把通用模式又放回去。
"""
from __future__ import annotations

import pytest

from collector.login.pdd import detect_challenge_from_text


@pytest.mark.parametrize(
    "page_text",
    [
        "获取验证码",
        "重新获取验证码",
        "获取短信验证码",
        "重新获取",
        "验证码登录",
        # 与实测导出一致的真实登录页可见文本
        "短信登录 密码登录 微信登录 获取验证码 登录 还没有账号？免费注册 登录 还没有账号？免费注册 忘记密码",
    ],
)
def test_benign_page_controls_are_not_challenge(page_text: str) -> None:
    """页面自身控件文案不得被判为挑战（否则登录无法进行）。"""
    assert detect_challenge_from_text(page_text) is None


@pytest.mark.parametrize(
    "page_text,expected_kind",
    [
        ("请输入验证码", "CAPTCHA"),
        ("请完成安全验证", "CAPTCHA"),
        ("短信验证码已发送至您的手机", "SMS"),
        ("请进行短信验证", "SMS"),
        ("请拖动滑块完成验证", "SLIDER"),
        ("账号已被锁定", "RISK"),
        ("操作过于频繁，请稍后再试", "RISK"),
        ("检测到异常登录", "RISK"),
    ],
)
def test_real_challenges_still_detected(page_text: str, expected_kind: str) -> None:
    """剔除自身控件文案后，真挑战仍必须被检出（宁可误停，不可漏停）。"""
    got = detect_challenge_from_text(page_text)
    assert got is not None, f"应检出挑战，实际未检出：{page_text!r}"
    assert expected_kind in str(got).upper(), f"挑战类型不符：{got!r} vs {expected_kind}"


def test_benign_stripping_does_not_mask_real_challenge_in_same_text() -> None:
    """同一段文本里既有自身按钮又有真挑战提示时，仍须检出挑战。"""
    text = "获取验证码 请输入验证码"
    got = detect_challenge_from_text(text)
    assert got is not None
    assert "CAPTCHA" in str(got).upper()


def test_empty_text_is_not_challenge() -> None:
    assert detect_challenge_from_text("") is None
