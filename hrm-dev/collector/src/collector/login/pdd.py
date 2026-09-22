# -*- coding: utf-8 -*-
"""拼多多登录流程编排（二期采集端第一版核心）。

流程（对齐 ADR §20.4.2）：
1. 打开配置的站点 URL，**先探测**是否已有有效登录态；有效则不重复登录，直接返回；
2. 无效才走登录：**先切到「密码登录」方式**——2026-09-22 采集机实测目标站默认停在「短信登录」，
   此时密码框不可见、无法填密码，故须先点击「密码登录」页签（已是密码登录态时**幂等跳过**）；
   再定位账号/密码输入框（选择器集中在 selectors.py）、按人工节奏分隔输入、提交；
3. **挑战探测**：出现验证码 / 滑块 / 短信二次验证 / 风控提示 → 立即停止自动化，抛
   :class:`ManualInterventionRequired`，把状态置 ``MANUAL_REQUIRED`` 并给出人工处置指引；
4. 结果判定：**不只看 URL 跳转**，结合「域名白名单 + 工作台特征元素 + 登录表单是否存在」三项可观测信号；
5. 成功后按配置导出 ``storage_state``，并把会话记录写入 MySQL（幂等 upsert）。

合规边界（红线 C3 / C2，ADR-C04）：
- 提交动作走**页面自身**的登录流程；采集端**不自行构造 HTTP 请求**、不接触 ``anti-content`` 签名；
- 验证码 / 滑块 / 短信二次验证**不识别、不打码、不绕过**，检测到即回落人工。

登录态载体说明：本设计使用 ``launch_persistent_context`` 的 profile 作为登录态**权威载体**
（ADR §20.2.5「登录态仍落在专用浏览器 profile」）。探测首个手段因此是**页面特征**，
``storage_state`` 导出仅作诊断留痕（内含 Cookie，属敏感物，落 runtime/ 且被 .gitignore 排除）。
"""
from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from typing import Any, Iterable, Sequence
from urllib.parse import urlparse

from collector.config.pdd_account import PddAccount
from collector.config.settings import Settings
from collector.db import repository
from collector.db.repository import LoginSession, LoginStatus
from collector.login import browser, selectors
from collector.login.humanize import Humanizer

logger = logging.getLogger("collector.login")


class ChallengeType(str, Enum):
    """必须回落人工的挑战类型（ADR §20.4.3）。"""

    CAPTCHA = "captcha"
    SLIDER = "slider"
    SMS = "sms"
    RISK = "risk"


class LoginOutcome(str, Enum):
    """登录流程的可观测结论。"""

    LOGGED_IN = "LOGGED_IN"            # 探测到已有有效登录态，未执行登录动作
    LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED"  # 本次执行登录并成功
    NOT_LOGGED_IN = "NOT_LOGGED_IN"    # 停在登录页，未登录
    MANUAL_REQUIRED = "MANUAL_REQUIRED"  # 需人工介入（挑战/风控/结构突变）
    FAILED = "FAILED"                  # 其它失败（凭据错误、结构突变等）


class LoginError(Exception):
    """登录流程错误基类。"""


class ManualInterventionRequired(LoginError):
    """检测到挑战或风控，必须人工处置；代码不得自行绕过。"""

    def __init__(self, challenge: ChallengeType, reason: str, guidance: str) -> None:
        super().__init__(f"{reason}；人工处置指引：{guidance}")
        self.challenge = challenge
        self.reason = reason
        self.guidance = guidance


class LoginPersistenceError(LoginError):
    """登录结果无法写入数据库（登录本身可能已成功）。"""

    def __init__(self, result: "LoginResult", cause: Exception) -> None:
        super().__init__(f"登录结果写入数据库失败：{cause}")
        self.result = result


class LoginPageError(LoginError):
    """登录页无法打开或无法定位关键元素。"""


# 人工处置指引（出现挑战时打印给操作人）
_GUIDANCE_BY_CHALLENGE: dict[ChallengeType, str] = {
    ChallengeType.CAPTCHA: "请在采集机浏览器窗口中手动完成图形验证码，完成后重跑 `login --check` 确认登录态",
    ChallengeType.SLIDER: "请在采集机浏览器窗口中手动完成滑块验证，完成后重跑 `login --check` 确认登录态",
    ChallengeType.SMS: "请在采集机浏览器窗口中手动输入收到的短信验证码，完成后重跑 `login --check` 确认登录态",
    ChallengeType.RISK: "平台已提示风控/异常，请人工登录并核实账号状态；不要重复自动重试（ADR §20.4.3）",
}

_SIGNAL_TYPE_TO_CHALLENGE: dict[str, ChallengeType] = {
    "captcha": ChallengeType.CAPTCHA,
    "slider": ChallengeType.SLIDER,
    "sms": ChallengeType.SMS,
    "risk": ChallengeType.RISK,
}

# 登录结果 → 数据库登录态
_OUTCOME_TO_STATUS: dict[LoginOutcome, LoginStatus] = {
    LoginOutcome.LOGGED_IN: LoginStatus.SUCCESS,
    LoginOutcome.LOGIN_SUCCEEDED: LoginStatus.SUCCESS,
    LoginOutcome.NOT_LOGGED_IN: LoginStatus.PENDING,
    LoginOutcome.MANUAL_REQUIRED: LoginStatus.MANUAL_REQUIRED,
    LoginOutcome.FAILED: LoginStatus.FAILED,
}

# 单个候选在一轮扫描中最多检查的匹配项数。目标站同 id / 同 class 元素各挂两份
# （短信页签与密码页签面板并存），上限用于防止兜底候选匹配过多元素时空转。
_MAX_MATCHES_PER_CANDIDATE = 8
# 单轮未命中后的轮询间隔（毫秒）。Playwright 的 locator.is_visible() 官方声明**不等待、立即返回**，
# 因此「等待元素出现」的语义由 find_first_visible 的轮询承担。
_SCAN_POLL_INTERVAL_MS = 200
# 判定「当前已是密码登录方式」的快速探测预算（毫秒）：已就绪时不应长等。
_LOGIN_MODE_PROBE_MS = 800

# 定位失败时统一给出的可执行指引（指向只读诊断脚本与选择器文件）
_SELECTOR_HINT = (
    "请先跑 `python scripts/dump-login-page.py --url <登录地址> --click-text 密码登录` 导出真实 DOM，"
    "再校准 src/collector/login/selectors.py"
)


# ==================================================================
# 纯函数区（不依赖 Playwright，可离线单测）
# ==================================================================
def host_of(url: str) -> str:
    """取 URL 主机名（小写）；无主机返回空串。"""
    return (urlparse(url or "").hostname or "").lower()


def is_allowed_url(url: str, allowed_hosts: Sequence[str]) -> bool:
    """URL 是否落在域名白名单内（允许白名单域的子域）。

    判定异常跳转（如被引导到第三方页面）依赖此函数，属「结构突变」的一类可观测信号。
    """
    host = host_of(url)
    if not host:
        return False
    return any(host == allowed.lower() or host.endswith("." + allowed.lower()) for allowed in allowed_hosts)


def detect_challenge_from_text(page_text: str) -> ChallengeType | None:
    """从页面可见文本判定挑战类型；无命中返回 None。

    依据 selectors.CHALLENGE_SIGNALS 的顺序（越具体越靠前）。
    **未实测**：信号表需在采集机按真实登录页校准（ADR 待验证项 V17 / V21）。
    """
    if not page_text:
        return None
    for signal in selectors.CHALLENGE_SIGNALS:
        if signal.pattern in page_text:
            return _SIGNAL_TYPE_TO_CHALLENGE[signal.type]
    return None


def is_credential_error_text(page_text: str) -> bool:
    """是否为「账号或密码错误」类提示（走不重试、通知更新凭据分支）。"""
    return bool(page_text) and selectors.CREDENTIAL_ERROR_PATTERN in page_text


def judge_login_result(
    *,
    url: str,
    allowed_hosts: Sequence[str],
    has_workbench_marker: bool,
    has_login_form: bool,
    challenge: ChallengeType | None,
) -> LoginOutcome:
    """登录结果判定（纯函数，便于单测）。

    判定依据（多项可观测信号，**不只看 URL 跳转**）：
    1. 出现挑战 → 必须回落人工；
    2. URL 不在白名单 → 结构/跳转突变，判失败；
    3. 有工作台特征且无登录表单 → 已登录；
    4. 登录表单仍在 → 仍在登录页，未完成；
    5. 既无工作台特征也无登录表单 → 无法识别（结构突变），判失败。
    """
    if challenge is not None:
        return LoginOutcome.MANUAL_REQUIRED
    if not is_allowed_url(url, allowed_hosts):
        return LoginOutcome.FAILED
    if has_workbench_marker and not has_login_form:
        return LoginOutcome.LOGGED_IN
    if has_login_form:
        return LoginOutcome.NOT_LOGGED_IN
    return LoginOutcome.FAILED


# ==================================================================
# 页面操作区（依赖 Playwright）
# ==================================================================
def _locator(page: Any, candidate: selectors.SelectorCandidate) -> Any:
    """把候选描述翻译为 Playwright 定位器（可见性由候选自身携带，如 CSS 的 ``:visible``）。"""
    if candidate.kind == "css":
        return page.locator(candidate.value)
    if candidate.kind == "text":
        return page.get_by_text(candidate.value, exact=candidate.exact)
    if candidate.kind == "role":
        role, _, accessible_name = candidate.value.partition(":")
        return page.get_by_role(role, name=accessible_name) if accessible_name else page.get_by_role(role)
    raise LoginPageError(f"未知选择器类型：{candidate.kind!r}")


def _first_visible_match(page: Any, candidate: selectors.SelectorCandidate) -> Any | None:
    """在单个候选的匹配项中取首个可见元素；无可见项返回 None。

    逐个匹配项校验可见性，而**不是只取 ``.first``**：目标站同 id / 同 class 元素各挂两份
    （短信页签与密码页签面板并存），``.first`` 会命中隐藏的那一份，导致填错框或点击无效。
    """
    try:
        locator = _locator(page, candidate)
        count = locator.count()
    except Exception:
        # 单个候选不可用不应中断整体降级链
        return None
    for index in range(min(count, _MAX_MATCHES_PER_CANDIDATE)):
        element = locator.nth(index)
        try:
            if element.is_visible():
                return element
        except Exception:
            continue
    return None


def find_first_visible(
    page: Any, candidates: Iterable[selectors.SelectorCandidate], timeout_ms: int = 1500
) -> Any | None:
    """按候选顺序返回首个可见元素；在 ``timeout_ms`` 总预算内未命中则返回 None。

    候选之间是**降级关系**：实测定位优先、语义定位次之、通用 CSS 兜底，避免目标站改版即全盘失效。

    等待语义由本函数承担：``locator.is_visible()`` 的 ``timeout`` 选项官方已标记为**被忽略**、
    且该方法**不等待、立即返回**（见
    https://playwright.dev/python/docs/api/class-locator#locator-is-visible ），
    故此处按总预算轮询扫描（单轮立即扫一次，未命中再按间隔重试）。
    """
    candidate_list = tuple(candidates)
    deadline = time.monotonic() + max(timeout_ms, 0) / 1000.0
    while True:
        for candidate in candidate_list:
            element = _first_visible_match(page, candidate)
            if element is not None:
                return element
        if time.monotonic() >= deadline:
            return None
        time.sleep(_SCAN_POLL_INTERVAL_MS / 1000.0)


def _visible_any(page: Any, candidates: Iterable[selectors.SelectorCandidate], timeout_ms: int = 1500) -> bool:
    return find_first_visible(page, candidates, timeout_ms) is not None


def _page_text(page: Any, timeout_ms: int) -> str:
    try:
        return page.locator("body").inner_text(timeout=timeout_ms) or ""
    except Exception:
        # 页面尚未就绪时读不到正文，按空文本处理（后续判定会走「结构突变」分支）
        return ""


def detect_challenge(page: Any, timeout_ms: int) -> ChallengeType | None:
    """在页面上探测挑战。基于可见文本匹配，不触碰任何签名或风控参数。"""
    return detect_challenge_from_text(_page_text(page, timeout_ms))


def has_login_form(page: Any) -> bool:
    return _visible_any(page, selectors.PASSWORD_INPUTS) or _visible_any(page, selectors.SUBMIT_BUTTONS)


def has_workbench_marker(page: Any) -> bool:
    return _visible_any(page, selectors.WORKBENCH_MARKERS)


def probe_state(page: Any, settings: Settings) -> LoginOutcome:
    """探测当前页面状态（不做任何登录动作）。"""
    return judge_login_result(
        url=page.url,
        allowed_hosts=settings.site.allowed_hosts,
        has_workbench_marker=has_workbench_marker(page),
        has_login_form=has_login_form(page),
        challenge=detect_challenge(page, settings.login.probe_timeout_ms),
    )


def password_login_ready(page: Any) -> bool:
    """是否已处于「密码登录」方式。

    判据 = **密码输入框可见**（2026-09-22 采集机实测：密码页签下密码框唯一且可见；
    短信页签下该框被隐藏）。该判据同时是页签切换的幂等依据。
    """
    return _visible_any(page, selectors.PASSWORD_INPUTS, _LOGIN_MODE_PROBE_MS)


def ensure_password_login_mode(page: Any, settings: Settings, humanizer: Humanizer) -> None:
    """确保页面停在「密码登录」方式；已是密码登录态则**幂等跳过**（不重复点击页签）。

    2026-09-22 采集机实测：目标登录页默认停在「短信登录」，此时页面虽能定位到手机号框，
    但密码框被隐藏、无法输入密码；必须先点击「密码登录」页签，否则登录流程必然卡在密码步骤。
    """
    if password_login_ready(page):
        return

    timeout_ms = settings.login.probe_timeout_ms
    switcher = find_first_visible(page, selectors.LOGIN_MODE_SWITCHERS, timeout_ms)
    if switcher is None:
        raise LoginPageError(
            "未定位到「密码登录」页签：登录页结构可能与 selectors.py 的 LOGIN_MODE_SWITCHERS 不匹配。" + _SELECTOR_HINT
        )

    switcher.click()
    humanizer.pause_field()

    if not _visible_any(page, selectors.PASSWORD_INPUTS, timeout_ms):
        raise LoginPageError(
            "已点击「密码登录」页签，但密码输入框仍未出现：疑似登录页改版或页签文案变化，"
            "请核对 selectors.py 的 LOGIN_MODE_SWITCHERS / PASSWORD_INPUTS。" + _SELECTOR_HINT
        )


def _wait_for_login_signal(page: Any, settings: Settings, timeout_ms: int, poll_interval_ms: int = 500) -> None:
    """轮询等待可观测的登录信号（工作台出现 / 挑战出现 / 凭据错误 / 登录表单消失）。

    不依赖固定 sleep，也不依赖单一 URL 跳转——任何一项信号出现即可进入结果判定。
    """
    deadline = time.monotonic() + timeout_ms / 1000.0
    while time.monotonic() < deadline:
        if has_workbench_marker(page):
            return
        if detect_challenge(page, poll_interval_ms) is not None:
            return
        if is_credential_error_text(_page_text(page, poll_interval_ms)):
            return
        if not has_login_form(page):
            return
        time.sleep(poll_interval_ms / 1000.0)


# ==================================================================
# 服务编排
# ==================================================================
@dataclass(frozen=True)
class LoginResult:
    outcome: LoginOutcome
    login_status: LoginStatus
    fail_reason: str | None = None
    storage_state_ref: str | None = None
    cookie_count: int = 0
    expire_at: datetime | None = None

    @property
    def exit_code(self) -> int:
        """CLI 退出码：0 成功/已登录；2 需人工介入；1 其它失败。"""
        if self.outcome in (LoginOutcome.LOGGED_IN, LoginOutcome.LOGIN_SUCCEEDED):
            return 0
        if self.outcome is LoginOutcome.MANUAL_REQUIRED:
            return 2
        return 1

    def __repr__(self) -> str:
        return (
            f"LoginResult(outcome={self.outcome.value!r}, login_status={self.login_status.value!r}, "
            f"cookie_count={self.cookie_count!r}, fail_reason={self.fail_reason!r})"
        )

    __str__ = __repr__


class PddLoginService:
    """登录编排服务：浏览器 + 人工节奏 + 会话落库。"""

    def __init__(
        self,
        settings: Settings,
        account: PddAccount,
        connection: Any | None = None,
        humanizer: Humanizer | None = None,
    ) -> None:
        self._settings = settings
        self._account = account
        self._connection = connection
        self._humanizer = humanizer or Humanizer(settings.humanize)

    # ---------------- 对外入口 ----------------
    def check(self) -> LoginResult:
        """只探测当前登录态，不做登录动作。"""
        self._register_secret_for_redaction()
        try:
            with browser.persistent_context(self._settings) as context:
                page = browser.open_page(context)
                self._goto_login(page)
                outcome = probe_state(page, self._settings)
                result = self._build_result(outcome, None)
                self._persist(result)
                logger.info("登录态探测完成：%s", result.outcome.value)
                return result
        finally:
            self._clear_secret_redaction()

    def run(self) -> LoginResult:
        """执行登录（已登录则跳过），返回结果；遇挑战抛 ManualInterventionRequired。"""
        self._register_secret_for_redaction()
        try:
            if not self._settings.login.auto_login_enabled:
                # ADR §20.2.9：自动登录生效条件未满足的站点应回落人工登录
                result = self._build_result(
                    LoginOutcome.MANUAL_REQUIRED,
                    None,
                    fail_reason="自动登录未启用（login.auto_login_enabled=false），按 ADR §20.2.9 回落人工登录",
                )
                self._persist(result)
                logger.warning("自动登录未启用，回落人工：%s", self._account.masked_description())
                return result

            with browser.persistent_context(self._settings) as context:
                page = browser.open_page(context)
                self._goto_login(page)

                # 1) 先探测：已有有效登录态则不重复登录
                outcome = probe_state(page, self._settings)
                if outcome is LoginOutcome.LOGGED_IN:
                    result = self._build_result(LoginOutcome.LOGGED_IN, context)
                    self._persist(result)
                    logger.info("已有有效登录态，跳过登录动作：%s", self._account.masked_description())
                    return result
                if outcome is LoginOutcome.MANUAL_REQUIRED:
                    raise self._manual_required(page)

                # 2) 执行登录
                self._submit_credentials(page)

                # 3) 等待可观测信号后复核
                _wait_for_login_signal(page, self._settings, self._settings.login.submit_wait_ms)
                page_text = _page_text(page, self._settings.login.probe_timeout_ms)
                challenge = detect_challenge_from_text(page_text)
                outcome = judge_login_result(
                    url=page.url,
                    allowed_hosts=self._settings.site.allowed_hosts,
                    has_workbench_marker=has_workbench_marker(page),
                    has_login_form=has_login_form(page),
                    challenge=challenge,
                )

                if outcome is LoginOutcome.MANUAL_REQUIRED:
                    raise self._manual_required(page)
                if outcome is LoginOutcome.LOGGED_IN:
                    result = self._build_result(LoginOutcome.LOGIN_SUCCEEDED, context)
                    self._persist(result)
                    logger.info("登录成功：%s", self._account.masked_description())
                    return result

                reason = (
                    "账号或密码错误（按 ADR §20.4.3 不重试，请站长更新凭据后重跑 init-local-secrets.ps1）"
                    if is_credential_error_text(page_text)
                    else "登录未完成且未识别到明确原因（疑似登录页结构突变，需人工核实）"
                )
                result = self._build_result(outcome, None, fail_reason=reason)
                self._persist(result)
                logger.error("登录失败：%s；%s", self._account.masked_description(), reason)
                return result
        finally:
            self._clear_secret_redaction()

    # ---------------- 内部步骤 ----------------
    def _goto_login(self, page: Any) -> None:
        url = self._settings.site.login_url
        try:
            page.goto(url, wait_until="domcontentloaded", timeout=self._settings.browser.navigation_timeout_ms)
        except Exception as exc:
            raise LoginPageError(f"打开登录页失败（{url}）：{exc}") from exc

    def _submit_credentials(self, page: Any) -> None:
        # 实测目标站默认停在短信登录，先切到密码登录（已就绪则幂等跳过），否则密码框不可见
        ensure_password_login_mode(page, self._settings, self._humanizer)

        if detect_challenge(page, self._settings.login.probe_timeout_ms) is not None:
            raise self._manual_required(page)

        account_input = find_first_visible(page, selectors.ACCOUNT_INPUTS)
        if account_input is None:
            raise LoginPageError("未定位到账号输入框：ACCOUNT_INPUTS 与登录页结构不匹配。" + _SELECTOR_HINT)
        self._humanizer.move_to(page, account_input)
        self._humanizer.type_like_human(account_input, self._account.account)
        self._humanizer.pause_field()

        password_input = find_first_visible(page, selectors.PASSWORD_INPUTS)
        if password_input is None:
            raise LoginPageError("未定位到密码输入框：PASSWORD_INPUTS 与登录页结构不匹配。" + _SELECTOR_HINT)
        self._humanizer.move_to(page, password_input)
        self._humanizer.type_like_human(password_input, self._account.password)
        self._humanizer.pause_field()

        submit = find_first_visible(page, selectors.SUBMIT_BUTTONS)
        if submit is None:
            raise LoginPageError("未定位到登录提交按钮：SUBMIT_BUTTONS 与登录页结构不匹配。" + _SELECTOR_HINT)
        self._humanizer.pause_before_submit()
        self._humanizer.move_to(page, submit)
        submit.click()

    def _manual_required(self, page: Any) -> ManualInterventionRequired:
        challenge = detect_challenge(page, self._settings.login.probe_timeout_ms) or ChallengeType.RISK
        reason = f"登录页出现 {challenge.value} 类挑战/风控提示，已按红线 C2/C8 立即停止自动化"
        return ManualInterventionRequired(challenge, reason, _GUIDANCE_BY_CHALLENGE[challenge])

    def _build_result(
        self, outcome: LoginOutcome, context: Any | None, fail_reason: str | None = None
    ) -> LoginResult:
        login_status = _OUTCOME_TO_STATUS[outcome]
        if context is None or outcome not in (LoginOutcome.LOGGED_IN, LoginOutcome.LOGIN_SUCCEEDED):
            return LoginResult(outcome=outcome, login_status=login_status, fail_reason=fail_reason)

        login_at = repository.utc_now()
        return LoginResult(
            outcome=outcome,
            login_status=login_status,
            fail_reason=None,
            storage_state_ref=self._export_storage_state(context),
            cookie_count=self._count_cookies(context),
            expire_at=repository.compute_expire_at(login_at, self._settings.login.session_ttl_hours),
        )

    def _count_cookies(self, context: Any) -> int:
        try:
            return len(context.cookies())
        except Exception:
            logger.warning("读取 cookie 计数失败，按 0 记录（不打印 cookie 内容）")
            return 0

    def _export_storage_state(self, context: Any) -> str | None:
        """导出 storage_state 到被 gitignore 的路径，仅存落盘路径引用。

        注意：该文件内含 Cookie，属敏感物；采集端**不把其内容写入数据库**。
        TODO(扩展): 与 ADR §20.2.5 对齐 —— 若评审认定 profile 已足够承载登录态，
                    可整体关闭导出（login.export_storage_state=false）以进一步缩小明文落盘面；
                    文件 ACL 收紧纳入 P2-06/P2-09 装机 checklist。
        """
        if not self._settings.login.export_storage_state:
            return None
        path = self._settings.storage_state_path(self._account.account_hash())
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            context.storage_state(path=str(path))
        except Exception:
            logger.warning("导出 storage_state 失败，登录态仍以 browser profile 承载", exc_info=True)
            return None
        return str(path)

    def _persist(self, result: LoginResult) -> None:
        """写入会话记录；未提供数据库连接时跳过并告警（不静默假装已持久化）。"""
        if self._connection is None:
            logger.warning("未提供数据库连接，本次登录会话未持久化")
            return
        now = repository.utc_now()
        session = LoginSession(
            account_masked=self._account.masked_account(),
            account_hash=self._account.account_hash(),
            site=self._settings.site.login_url,
            login_status=result.login_status,
            login_at=now if result.login_status is LoginStatus.SUCCESS else None,
            expire_at=result.expire_at,
            storage_state_ref=result.storage_state_ref,
            cookie_count=result.cookie_count,
            last_check_at=now,
            fail_reason=result.fail_reason,
        )
        try:
            with self._connection.session() as cursor:
                repository.upsert_login_session(cursor, session)
        except Exception as exc:
            raise LoginPersistenceError(result, exc) from exc

    def _register_secret_for_redaction(self) -> None:
        # 仅在流程存续期间把密码纳入日志脱敏表，用完即清（明文零落地，ADR §20.3.3）
        from collector import logging_setup

        logging_setup.add_sensitive(self._account.password)

    def _clear_secret_redaction(self) -> None:
        from collector import logging_setup

        logging_setup.clear_sensitive()
