# -*- coding: utf-8 -*-
"""登录页选择器与挑战信号表（集中一处，目标站改版时只改本文件）。

**重要前提（不得编造）**：目标站登录页的真实 DOM 尚未在采集机实测确认——开发机无法访问目标站。
因此本文件每一条都带 ``verified`` 与 ``basis``：

- ``verified=False`` 且依据为「HTML 语义常识」：如 ``input[type="password"]``，与站点无关；
- ``verified=False`` 且依据为「站点分析已核实事实」：如 ``DIV[role=menuitem]``（见 collector-site-analysis.md）；
- **严禁**写死未经实测的 class / id。站点使用 ``station-`` / ``rocket-`` 前缀的自研组件，
  在实测确认前不得凭猜测编造选择器。

优先使用语义定位（文本 / role），其次才是通用 CSS，以降低改版脆性。
"""
from __future__ import annotations

from dataclasses import dataclass

_UNVERIFIED_HINT = "未核实，需在采集机实测确认"


@dataclass(frozen=True)
class SelectorCandidate:
    """一个候选选择器。

    kind: ``css`` | ``text`` | ``role``（role 的 value 形如 ``button:登录``）
    """

    kind: str
    value: str
    basis: str
    verified: bool = False

    def describe(self) -> str:
        flag = "已核实" if self.verified else _UNVERIFIED_HINT
        return f"[{self.kind}] {self.value}（依据：{self.basis}；{flag}）"


@dataclass(frozen=True)
class ChallengeSignal:
    """风控挑战信号。

    type 取值：``captcha``（图形验证码）/ ``slider``（滑块）/ ``sms``（短信二次验证）/ ``risk``（风控/异常提示）。
    """

    type: str
    pattern: str
    basis: str
    verified: bool = False


# ---------------- 登录方式切换 ----------------
# 站点分析已观测到「短信登录 / 密码登录 / 微信登录」三种方式；默认未必停在密码登录页签。
LOGIN_MODE_SWITCHERS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate("text", "密码登录", "站点分析 §20.4.1 观测到存在密码登录方式", False),
    SelectorCandidate("text", "账号登录", "同上，作为兜底文案", False),
)

# ---------------- 账号输入框 ----------------
ACCOUNT_INPUTS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate("css", 'input[type="tel"]', "手机号登录的通用语义（与站点无关）", False),
    SelectorCandidate("css", 'input[type="text"]', "通用文本输入框，作为兜底", False),
    SelectorCandidate("role", "textbox:账号", "无障碍名含「账号」时的语义定位", False),
)

# ---------------- 密码输入框 ----------------
PASSWORD_INPUTS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate("css", 'input[type="password"]', "密码框的通用语义（与站点无关，最可靠）", False),
)

# ---------------- 提交按钮 ----------------
SUBMIT_BUTTONS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate("role", "button:登录", "按无障碍名匹配「登录」按钮", False),
    SelectorCandidate("text", "登录", "按可见文本匹配，作为兜底", False),
)

# ---------------- 已登录（工作台）特征 ----------------
WORKBENCH_MARKERS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate("css", 'div[role="menuitem"]', "站点分析 §4.1 已核实：左侧一级菜单为 DIV[role=menuitem]", True),
    SelectorCandidate("text", "运单查询", "站点分析 §2 已核实：运单中心 → 运单查询为工作台入口", True),
)

# ---------------- 挑战信号（命中即停，禁止自动识别与打码） ----------------
# 表内**顺序即匹配优先级**：越具体越靠前（如「短信验证码」须先于通用「验证码」命中）。
CHALLENGE_SIGNALS: tuple[ChallengeSignal, ...] = (
    ChallengeSignal("slider", "滑块", "滑块验证文案", False),
    ChallengeSignal("slider", "拖动", "滑块拖动提示语", False),
    ChallengeSignal("slider", "拼图", "拼图式滑块文案", False),
    ChallengeSignal("sms", "短信验证码", "短信验证码文案（须先于通用「验证码」匹配）", False),
    ChallengeSignal("sms", "短信验证", "短信二次验证文案", False),
    ChallengeSignal("captcha", "请输入验证码", "图形验证码提示语", False),
    ChallengeSignal("captcha", "验证码", "图形验证码通用文案", False),
    ChallengeSignal("captcha", "安全验证", "平台通用安全校验文案", False),
    ChallengeSignal("risk", "账号已被锁定", "风控提示：账号锁定", False),
    ChallengeSignal("risk", "操作过于频繁", "风控提示：频率限制", False),
    ChallengeSignal("risk", "异常登录", "风控提示：异地/异常登录", False),
)

# 凭据错误提示。**不放进 CHALLENGE_SIGNALS**：它不属于「必须回落人工」的挑战类型，
# 而走「不重试 + 通知站长更新凭据」分支（ADR §20.4.3）。
CREDENTIAL_ERROR_PATTERN = "账号或密码错误"
