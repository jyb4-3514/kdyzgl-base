# -*- coding: utf-8 -*-
"""登录结果判定与挑战识别的纯函数测试（不依赖 Playwright）。"""
from __future__ import annotations

import pytest

from collector.db.repository import LoginStatus
from collector.login.pdd import (
    ChallengeType,
    LoginOutcome,
    LoginResult,
    detect_challenge_from_text,
    host_of,
    is_allowed_url,
    is_credential_error_text,
    judge_login_result,
)

_ALLOWED = ("mcmd.pinduoduo.com", "mdkd.pinduoduo.com")


# ---------------- 域名判定 ----------------
@pytest.mark.parametrize(
    ("url", "expected"),
    [
        ("https://mdkd.pinduoduo.com/", True),
        ("https://mcmd.pinduoduo.com/home", True),
        ("https://sub.mdkd.pinduoduo.com/vw/x", True),
        ("https://evil.example.com/", False),
        ("https://pinduoduo.com.evil.com/", False),
        ("", False),
    ],
)
def test_域名白名单判定(url, expected):
    assert is_allowed_url(url, _ALLOWED) is expected


def test_取主机名():
    assert host_of("https://MDKD.Pinduoduo.com/vw") == "mdkd.pinduoduo.com"
    assert host_of("not-a-url") == ""


# ---------------- 挑战识别 ----------------
@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("请拖动滑块完成验证", ChallengeType.SLIDER),
        ("请输入短信验证码", ChallengeType.SMS),  # 须先于通用「验证码」命中
        ("短信验证已发送", ChallengeType.SMS),
        ("请输入验证码", ChallengeType.CAPTCHA),
        ("为保障安全，请完成安全验证", ChallengeType.CAPTCHA),
        ("您的账号已被锁定", ChallengeType.RISK),
        ("操作过于频繁，请稍后再试", ChallengeType.RISK),
        ("欢迎使用运单查询", None),
        ("", None),
    ],
)
def test_挑战类型识别与优先级(text, expected):
    assert detect_challenge_from_text(text) is expected


def test_凭据错误单独判定且不算挑战():
    assert is_credential_error_text("账号或密码错误，请重新输入") is True
    assert is_credential_error_text("欢迎使用运单查询") is False
    # 凭据错误不应被误判为需要人工介入的挑战
    assert detect_challenge_from_text("账号或密码错误，请重新输入") is None


# ---------------- 结果判定 ----------------
def test_出现挑战即回落人工():
    assert (
        judge_login_result(
            url="https://mdkd.pinduoduo.com/",
            allowed_hosts=_ALLOWED,
            has_workbench_marker=True,
            has_login_form=False,
            challenge=ChallengeType.SLIDER,
        )
        is LoginOutcome.MANUAL_REQUIRED
    )


def test_跳出白名单判失败():
    assert (
        judge_login_result(
            url="https://evil.example.com/",
            allowed_hosts=_ALLOWED,
            has_workbench_marker=False,
            has_login_form=False,
            challenge=None,
        )
        is LoginOutcome.FAILED
    )


def test_有工作台特征且无登录表单即已登录():
    assert (
        judge_login_result(
            url="https://mdkd.pinduoduo.com/",
            allowed_hosts=_ALLOWED,
            has_workbench_marker=True,
            has_login_form=False,
            challenge=None,
        )
        is LoginOutcome.LOGGED_IN
    )


def test_登录表单仍在即未登录():
    assert (
        judge_login_result(
            url="https://mcmd.pinduoduo.com/home",
            allowed_hosts=_ALLOWED,
            has_workbench_marker=False,
            has_login_form=True,
            challenge=None,
        )
        is LoginOutcome.NOT_LOGGED_IN
    )


def test_既无工作台特征也无登录表单判失败():
    assert (
        judge_login_result(
            url="https://mdkd.pinduoduo.com/",
            allowed_hosts=_ALLOWED,
            has_workbench_marker=False,
            has_login_form=False,
            challenge=None,
        )
        is LoginOutcome.FAILED
    )


# ---------------- 结果对象 ----------------
@pytest.mark.parametrize(
    ("outcome", "expected_code"),
    [
        (LoginOutcome.LOGGED_IN, 0),
        (LoginOutcome.LOGIN_SUCCEEDED, 0),
        (LoginOutcome.NOT_LOGGED_IN, 1),
        (LoginOutcome.FAILED, 1),
        (LoginOutcome.MANUAL_REQUIRED, 2),
    ],
)
def test_退出码映射(outcome, expected_code):
    result = LoginResult(outcome=outcome, login_status=LoginStatus.PENDING)
    assert result.exit_code == expected_code
