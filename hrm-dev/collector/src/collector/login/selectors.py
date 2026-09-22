# -*- coding: utf-8 -*-
"""登录页选择器与挑战信号表（集中一处，目标站改版时只改本文件）。

**证据分级（2026-09-22 采集机实测后修订）**——每条候选都带 ``verified`` 与 ``basis``：

- ``verified=True``：**采集机实测确认**，``basis`` 写明实测时间、地址与被命中的真实属性；
- ``verified=False``：**仍未实测**，依据仅为 HTML 语义常识或站点分析文档，不得当作已验证使用。

实测环境：采集机 ``PC-20260112CXLA``（Win10 19045.4291）；实测入口
``https://mdkd.pinduoduo.com/login``（原配的 ``mcmd.pinduoduo.com/home`` 是营销落地页，
实测 ``input 数=0``、无密码框）。复现方式（只读导出，不提交表单）::

    python scripts/dump-login-page.py --url https://mdkd.pinduoduo.com/login
    python scripts/dump-login-page.py --url https://mdkd.pinduoduo.com/login --click-text 密码登录

**已由采集机实测确认（verified=True）的条目**：

1. 登录方式页签可见文本「密码登录」（页面默认停在「短信登录」，必须点此切换）；
2. 账号输入框 ``#mobile``（class 含 ``rocket-input``）；
3. 密码输入框 ``input.password-input``（id 为 ``password``，密码页签下唯一）；
4. 提交按钮 ``button.login-btn``（class 含 ``rocket-btn`` / ``rocket-btn-primary``）。

**仍未实测（verified=False）的条目**：上述之外的全部内容，包括各降级候选、
``WORKBENCH_MARKERS``、``CHALLENGE_SIGNALS`` 与 ``CREDENTIAL_ERROR_PATTERN``。
其中 ``WORKBENCH_MARKERS`` 的 ``verified=True`` 来源于**站点分析文档核实**，
并非采集机实测（登录成功后的工作台 DOM 尚未实测）。

**同 id / 同 class 两份的陷阱（实测确认）**：短信页签与密码页签的面板**同时挂在 DOM 上**，
切换页签只是切显隐，因此 ``#mobile`` 与 ``button.login-btn`` 各存在两份。
仅按 id / class 定位会命中隐藏的那一份（填错框 / 点击无效），故：

- 本文件对需要交互的 CSS 候选**一律附加 Playwright 的 ``:visible`` 伪类**（只匹配可见元素）；
- ``pdd.find_first_visible`` 另按「逐个匹配项校验可见性」兜底（text / role 候选同样受此保护）。

Playwright ``:visible`` 官方依据（语义：仅匹配可见元素）：
https://playwright.dev/python/docs/other-locators#css-matching-only-visible-elements

可见（visible）的判定定义——非空 bounding box 且计算样式非 ``visibility:hidden``
（零尺寸与 ``display:none`` 不算可见，``opacity:0`` 仍算可见）：
https://playwright.dev/python/docs/actionability#visible

优先使用语义定位（文本 / role），其次才是通用 CSS，以降低改版脆性。
"""
from __future__ import annotations

from dataclasses import dataclass

_UNVERIFIED_HINT = "未核实，需在采集机实测确认"
_MEASURED = "2026-09-22 采集机实测（PC-20260112CXLA）：mdkd.pinduoduo.com/login"


@dataclass(frozen=True)
class SelectorCandidate:
    """一个候选选择器。

    kind: ``css`` | ``text`` | ``role``（role 的 value 形如 ``button:登录``）
    exact: **仅 ``text`` 类型生效**——True 表示整串精确匹配，避免「登录」误命中「短信登录」等页签文案。
    """

    kind: str
    value: str
    basis: str
    verified: bool = False
    exact: bool = False

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
# 实测：登录页默认停在「短信登录」，另有「密码登录」/「微信登录」；不切页签则密码框不可见。
LOGIN_MODE_SWITCHERS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate(
        "text",
        "密码登录",
        f"{_MEASURED} 页签可见文本「密码登录」（须精确匹配，避免误点含「登录」的其它文案）",
        True,
        exact=True,
    ),
    SelectorCandidate("text", "账号登录", "站点分析 §20.4.1 观测到存在密码登录方式，作为兜底文案（未实测）", False),
)

# ---------------- 账号输入框 ----------------
ACCOUNT_INPUTS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate(
        "css",
        "#mobile:visible",
        f"{_MEASURED} 密码登录页签下可见的手机号框（id=mobile、class 含 rocket-input）；"
        "同 id 在短信页签另挂一份隐藏副本，故加 :visible",
        True,
    ),
    SelectorCandidate("role", "textbox:手机号", "按无障碍名兜底（实测 placeholder=手机号，语义定位未实测）", False),
    SelectorCandidate("css", 'input[name="mobile"]:visible', "按 name 属性兜底（未实测）", False),
    SelectorCandidate("css", 'input[type="tel"]:visible', "手机号登录的通用语义（与站点无关）", False),
    SelectorCandidate("css", 'input[type="text"]:visible', "通用文本输入框，最后兜底", False),
    SelectorCandidate("role", "textbox:账号", "无障碍名含「账号」时的语义定位（原候选，未实测）", False),
)

# ---------------- 密码输入框 ----------------
PASSWORD_INPUTS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate(
        "css",
        "input.password-input:visible",
        f"{_MEASURED} 密码登录页签下唯一的密码框（class 为 rocket-input password-input）",
        True,
    ),
    SelectorCandidate(
        "css",
        "#password:visible",
        f"{_MEASURED} 同上密码框的 id=password 定位",
        True,
    ),
    SelectorCandidate("css", 'input[type="password"]:visible', "密码框的通用语义（与站点无关，最可靠兜底）", False),
)

# ---------------- 提交按钮 ----------------
SUBMIT_BUTTONS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate(
        "css",
        "button.login-btn:visible",
        f"{_MEASURED} 登录提交按钮（class 含 rocket-btn login-btn rocket-btn-primary）；同 class 有两份，故加 :visible",
        True,
    ),
    SelectorCandidate("role", "button:登录", "按无障碍名匹配「登录」按钮；role 定位默认排除隐藏元素（未实测）", False),
    SelectorCandidate(
        "text",
        "登录",
        "按可见文本兜底；须精确匹配，否则会命中「短信登录」等页签文案（未实测）",
        False,
        exact=True,
    ),
)

# ---------------- 已登录（工作台）特征 ----------------
# 注意：本组两条的依据是「站点分析文档已核实」，**尚未在采集机实测**（登录成功后的工作台 DOM 未导出）。
WORKBENCH_MARKERS: tuple[SelectorCandidate, ...] = (
    SelectorCandidate("css", 'div[role="menuitem"]', "站点分析 §4.1 已核实：左侧一级菜单为 DIV[role=menuitem]", True),
    SelectorCandidate("text", "运单查询", "站点分析 §2 已核实：运单中心 → 运单查询为工作台入口", True),
)

# ---------------- 挑战信号（命中即停，禁止自动识别与打码） ----------------
# 表内**顺序即匹配优先级**：越具体越靠前（如「短信验证码」须先于通用「验证码」命中）。
# **未实测**：待按真实登录页校准（ADR 待验证项 V17 / V21）。
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
