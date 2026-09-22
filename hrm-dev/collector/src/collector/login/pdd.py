# -*- coding: utf-8 -*-
"""拼多多登录流程编排（二期采集端第一版核心）。

流程（对齐 ADR §20.4.2）：
1. 打开配置的站点 URL，**先探测**是否已有有效登录态；有效则不重复登录，直接返回；
2. 无效才走登录：**先切到「密码登录」方式**——2026-09-22 采集机实测目标站默认停在「短信登录」，
   此时密码框不可见、无法填密码，故须先点击「密码登录」页签（已是密码登录态时**幂等跳过**）；
   再定位账号/密码输入框（选择器集中在 selectors.py）、按人工节奏分隔输入、**提交前校验两框均非空**、
   再按**多路径降级链**提交（回车 → 真实点击 → 强制点击 → 合成点击，见 :data:`_SUBMIT_PATHS`）；
   每条路径执行后用**可观测信号**判定提交是否生效，未生效才降级下一条，绝不把「点了没反应」当成成功；
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

人工介入登录（``login --manual``）说明：自动路径检出挑战时**立即停止自动化**，浏览器随上下文关闭，
现场无从在其窗口中完成验证（提示文案与真实行为不符，属已复现的流程缺陷）。故另设 ``--manual``：
打开**有头**浏览器把控制权交给现场人工，程序只做**只读**轮询（不点击、不输入、不滚动），
检出登录成功即导出会话并写库。该模式与 ``--run`` 互不调用，也**不是**默认行为
（红线不变：挑战只做「识别到 → 交人工」，绝不识别/破解/模拟拖动）。
"""
from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from typing import Any, Callable, Iterable, Sequence
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


class LoginTimeoutError(LoginPageError):
    """等待或交互超时（底层为 Playwright ``TimeoutError``）。"""


class LoginElementBlockedError(LoginPageError):
    """目标元素被其它元素遮挡，真实鼠标点击无法落到目标上。

    与「未定位到元素」严格区分：元素**已定位到且可见**，只是点击坐标被覆盖；
    报错必须写明遮挡者是谁，避免把遮挡误报成定位失败。
    """


class ManualLoginConfigError(LoginError):
    """人工介入登录的配置不满足要求（如要求有头却配成无头）。"""


class ManualLoginTimeoutError(LoginError):
    """人工介入登录在等待窗口内未检测到登录成功。

    与「未检测到登录态」区分：本轮是**等满后仍无成功信号**，故单独给出等待时长与排查建议。
    """

    def __init__(self, waited_minutes: int, reason: str, guidance: str) -> None:
        super().__init__(f"{reason}；排查建议：{guidance}")
        self.waited_minutes = waited_minutes
        self.reason = reason
        self.guidance = guidance


class ManualBrowserClosedError(LoginError):
    """人工介入登录等待期间浏览器窗口/标签页被关闭。

    必须与「超时」严格区分：关窗是**外部动作导致的中止**，不得伪装成「未检测到登录」。
    """


class BrowserClosedError(LoginError):
    """页面/上下文已被关闭的**内部信号**（非对外终态异常）。

    仅用于把「浏览器已被关闭」这一事实从只读探测（:func:`read_manual_snapshot`）传到轮询循环，
    由 :func:`poll_manual_login` 归类为 :data:`ManualPollOutcome.BROWSER_CLOSED`；
    对外的终态由 :class:`ManualBrowserClosedError` 表达。
    """


# 人工处置指引（出现挑战时打印给操作人）
# 注意：自动路径检出挑战后会**立即停止自动化**，浏览器随上下文一并关闭，
# 因此指引**不得**声称"请在浏览器窗口中手动完成"——那样窗口早没了，人工无从介入（已复现的流程缺陷）。
# 正确指引是改用 `login --manual`（该模式会把窗口保持打开、只读等待）。
_GUIDANCE_BY_CHALLENGE: dict[ChallengeType, str] = {
    ChallengeType.CAPTCHA: "自动化已停止且浏览器会随之关闭。请改用 `python -m collector login --manual` 打开窗口人工完成图形验证码",
    ChallengeType.SLIDER: "自动化已停止且浏览器会随之关闭。请改用 `python -m collector login --manual` 打开窗口人工完成滑块验证",
    ChallengeType.SMS: "自动化已停止且浏览器会随之关闭。请改用 `python -m collector login --manual` 打开窗口人工输入短信验证码",
    ChallengeType.RISK: "平台已提示风控/异常：请人工核实账号状态，不要重复自动重试（ADR §20.4.3）；需要现场处理时改用 `python -m collector login --manual`",
}

# 人工登录超时的排查建议（打印给操作人；不含任何敏感信息）
_MANUAL_TIMEOUT_GUIDANCE = (
    "① 确认已在窗口中完成滑块/验证码并点击「登录」；"
    "② 确认账号密码正确（凭据错误会被单独识别）；"
    "③ 确认窗口仍停留在登录页或已跳转工作台；"
    "④ 查看日志 runtime/logs/collector.log 确认每轮检测结论"
)

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


# 登录页自身控件文案：包含「验证码」字样但**不是**风控挑战提示，匹配前必须剔除。
# 起因（2026-09-22 采集机实测）：mdkd 密码登录页有「获取验证码」按钮，被 CHALLENGE_SIGNALS 的
# 通用模式「验证码」命中 → 一进页面即判 captcha → MANUAL_REQUIRED（退出码 2），登录永远走不通。
# 这里选择「剔除自身控件文案」而非删掉通用模式：删模式会让真正的「验证码」提示漏检，
# 而漏检的后果是"该停不停"，比"误停"更危险（红线 C2/C8 要求宁停不猜）。
# 长短语排在前面，避免被短短语先部分替换掉。
_BENIGN_PAGE_PHRASES: tuple[str, ...] = (
    "重新获取验证码",
    "获取短信验证码",
    "获取验证码",
    "重新获取",
    "验证码登录",
)


def _strip_benign_phrases(page_text: str) -> str:
    """剔除登录页自身控件文案，避免把「获取验证码」误判为图形验证码挑战。"""
    text = page_text
    for phrase in _BENIGN_PAGE_PHRASES:
        text = text.replace(phrase, "")
    return text


def detect_challenge_from_text(page_text: str) -> ChallengeType | None:
    """从页面可见文本判定挑战类型；无命中返回 None。

    依据 selectors.CHALLENGE_SIGNALS 的顺序（越具体越靠前）。
    **未实测**：信号表需在采集机按真实登录页校准（ADR 待验证项 V17 / V21）。
    """
    if not page_text:
        return None
    text = _strip_benign_phrases(page_text)
    for signal in selectors.CHALLENGE_SIGNALS:
        if signal.pattern in text:
            return _SIGNAL_TYPE_TO_CHALLENGE[signal.type]
    return None


def is_credential_error_text(page_text: str) -> bool:
    """是否为「账号或密码错误」类提示（走不重试、通知更新凭据分支）。"""
    return bool(page_text) and selectors.CREDENTIAL_ERROR_PATTERN in page_text


def outcome_for_error(exc: BaseException) -> LoginOutcome:
    """异常 → 登录结论：挑战/风控类回落人工，其余一律判失败。

    该映射与 ``_OUTCOME_TO_STATUS`` / ``LoginResult.exit_code`` 一起构成
    「错误 → 落库状态 → CLI 退出码」的完整收敛链（集中一处，便于测试与审计）。
    """
    return LoginOutcome.MANUAL_REQUIRED if isinstance(exc, ManualInterventionRequired) else LoginOutcome.FAILED


def exit_code_for_error(exc: BaseException) -> int:
    """异常 → CLI 退出码：需人工介入 2，其余失败 1（成功路径不经过本函数）。"""
    return 2 if isinstance(exc, ManualInterventionRequired) else 1


def is_playwright_error(exc: BaseException) -> bool:
    """是否为 Playwright 抛出的异常。

    按**模块名**判定而非 ``import playwright``：开发机未装 Playwright，
    单测与静态分析不得因缺失该依赖而失败。
    """
    return (type(exc).__module__ or "").startswith("playwright.")


def is_playwright_timeout(exc: BaseException) -> bool:
    """是否为 Playwright 的 ``TimeoutError``（点击/等待超时的底层类型）。"""
    return is_playwright_error(exc) and type(exc).__name__ == "TimeoutError"


def is_browser_closed_error(exc: BaseException) -> bool:
    """是否为「浏览器/页面已被关闭」类错误（人工中途关窗）。

    判据（任一命中）：
    1. 本项目 :class:`BrowserClosedError`（``page.is_closed()`` 命中时自行抛出）；
    2. Playwright ``TargetClosedError``：类位于 ``playwright._impl._errors``，
       默认消息 "Target page, context or browser has been closed"。
       官方依据（页面关闭语义与判定入口）：https://playwright.dev/python/docs/api/class-page#page-is-closed

    按**模块名 + 类名**判定，开发机未装 Playwright 时单测仍可用同名打桩类覆盖。
    """
    if isinstance(exc, BrowserClosedError):
        return True
    return is_playwright_error(exc) and type(exc).__name__ == "TargetClosedError"


def _clip_text(text: str, limit: int = 200) -> str:
    """压缩为单行并截断，避免把整段 Playwright 调用日志写进异常与数据库。"""
    return " ".join((text or "").split())[:limit]


def classify_browser_failure(exc: Exception, stage: str) -> LoginError:
    """把交互阶段的底层异常映射为**明确类型**的登录错误（stage 指明出错步骤）。

    映射表：
    - 命中 pointer events 拦截 → :class:`LoginElementBlockedError`（遮挡，可归因到遮挡者）；
    - Playwright ``TimeoutError`` → :class:`LoginTimeoutError`；
    - 其它 Playwright 异常 → :class:`LoginPageError`；
    - 其余异常 → :class:`LoginPageError`。

    不在此处吞掉异常：调用方要么继续向上收敛（CLI 兜底），要么转成失败结果落库。
    """
    text = str(exc)
    if "intercepts pointer events" in text:
        return LoginElementBlockedError(
            f"{stage}被其它元素遮挡（Playwright 报 pointer events 被拦截），真实鼠标点击无法落到目标上。"
            + _SELECTOR_HINT
        )
    if is_playwright_timeout(exc):
        return LoginTimeoutError(f"{stage}超时（元素不可操作或页面长时间未就绪）。" + _SELECTOR_HINT)
    if is_playwright_error(exc):
        return LoginPageError(f"{stage}发生浏览器层错误：{_clip_text(text)}。" + _SELECTOR_HINT)
    return LoginPageError(f"{stage}失败：{_clip_text(text)}。" + _SELECTOR_HINT)


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
    """把候选描述翻译为 Playwright 定位器。

    CSS 候选走 ``css_selector()``：候选自带的作用域（如激活面板）会拼在选择器前面
    ——「同 id / 同 class 多份」时，仅靠 ``:visible`` 不足以区分当前生效的那一份。
    """
    if candidate.kind == "css":
        return page.locator(candidate.css_selector())
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


# ---------------- 点击遮挡守卫（纯逻辑可离线单测） ----------------
# 滚动微调序列：每个元素是一次点击尝试前的额外纵向滚动量（像素，0 = 仅居中不做偏移）。
# 起因（2026-09-22 采集机实测）：目标站页签容器 div.rocket-tabs...login-tabs 为 sticky，
# Playwright 默认的最小滚动会把按钮停在它下面（或被激活面板的 #mobile 覆盖），
# 点击坐标被拦截并重试到超时。故先把目标滚到视口垂直居中，再校验落点、必要时上下微调。
_CLICK_SCROLL_NUDGE_PX: tuple[int, ...] = (0, -160, 160)

# 浏览器内命中测试：返回落点最顶层元素的描述；命中目标是自身或其子节点时返回 null（视为未被遮挡）。
# 仅做**只读**的 elementFromPoint 查询，不触发任何点击、提交或表单动作。
_JS_TOPMOST_BLOCKER = """
(el, point) => {
  const top = document.elementFromPoint(point.x, point.y);
  if (!top) return null;
  if (el === top || el.contains(top)) return null;
  const cls = (typeof top.className === 'string')
    ? top.className.trim().split(/\\s+/).filter(Boolean) : [];
  let desc = top.tagName.toLowerCase();
  if (top.id) desc += '#' + top.id;
  if (cls.length) desc += '.' + cls.join('.');
  return desc;
}
"""


def element_center(box: dict | None) -> tuple[float, float] | None:
    """取元素包围盒中心点；盒缺失或零尺寸时返回 None（零尺寸不可点击）。"""
    if not box:
        return None
    width = box.get("width") or 0
    height = box.get("height") or 0
    if width <= 0 or height <= 0:
        return None
    return box.get("x", 0) + width / 2, box.get("y", 0) + height / 2


def topmost_blocker(element: Any, box: dict | None) -> str | None:
    """返回点击落点最顶层的遮挡元素描述；未遮挡或无法判定时返回 None。

    判定标准与 Playwright 的「Receives Events」一致（元素须是点击点的命中目标）：
    命中元素是目标本身或其子节点即视为可点击。
    浏览器内校验不可用时返回 None，不做臆断的遮挡判定，交由 click 自身检查兜底。
    """
    center = element_center(box)
    if center is None:
        return "元素零尺寸（bounding box 为空）"
    try:
        return element.evaluate(_JS_TOPMOST_BLOCKER, {"x": center[0], "y": center[1]})
    except Exception:
        return None


def _element_box(element: Any) -> dict | None:
    """安全取包围盒：元素未布局或接口报错时按缺失处理。"""
    try:
        return element.bounding_box()
    except Exception:
        return None


def _align_target_to_center(page: Any, element: Any, nudge_px: int) -> None:
    """把目标滚到视口垂直居中（附加纵向微调），规避 sticky 容器遮挡。"""
    try:
        element.evaluate("el => el.scrollIntoView({block: 'center', inline: 'nearest'})")
    except Exception:
        try:
            element.scroll_into_view_if_needed()
        except Exception:
            return
    if nudge_px:
        try:
            page.mouse.wheel(0, nudge_px)
        except Exception:
            pass


def click_with_occlusion_guard(page: Any, element: Any, timeout_ms: int | None = None) -> None:
    """对已定位元素执行**真实鼠标点击**，点击前校验落点未被遮挡。

    处理顺序（每一步都可验证，不是「多试几次」）：
    1. 把目标滚动到视口垂直居中 —— 默认最小滚动可能让按钮停在 sticky 页签容器下面；
    2. 用浏览器内 ``document.elementFromPoint`` 取落点最顶层元素，确认它是目标本身或其子节点；
    3. 校验通过才 ``click()``；被遮挡则按 ``_CLICK_SCROLL_NUDGE_PX`` 调整纵向滚动后重试；
    4. 全部尝试仍被遮挡 → 抛 :class:`LoginElementBlockedError` 并写明**遮挡者是谁**，
       绝不报成「未定位到按钮」。

    ``timeout_ms`` 为 ``click()`` 动作超时；**生产路径必须传短超时**（来自 ``login.submit_path_timeout_ms``），
    否则会退回 Playwright 默认 30s，把降级链的短预算初衷废掉。留空仅供既有单测/调用方沿用默认语义。

    本函数**不**使用 ``dispatch_event('click')`` 或 ``evaluate`` 直接调 ``click()``（那会绕过真实鼠标）；
    该手段单独作为降级链的最后一条路径（:func:`_submit_dispatch`），并已在 README 标注其合成事件限制。
    """
    last_blocker: str | None = None
    for nudge_px in _CLICK_SCROLL_NUDGE_PX:
        _align_target_to_center(page, element, nudge_px)
        blocker = topmost_blocker(element, _element_box(element))
        if blocker is None:
            try:
                if timeout_ms is None:
                    element.click()
                else:
                    element.click(timeout=timeout_ms)
            except Exception as exc:
                raise classify_browser_failure(exc, "提交按钮点击") from exc
            return
        last_blocker = blocker
    raise LoginElementBlockedError(
        f"提交按钮被其它元素遮挡，真实鼠标点击无法落到目标上：落点最顶层元素为「{last_blocker}」。"
        "通常是登录页新增了 sticky 容器或浮层，请导出 DOM 复核。" + _SELECTOR_HINT
    )


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


def has_login_form(page: Any, timeout_ms: int = 1500) -> bool:
    return _visible_any(page, selectors.PASSWORD_INPUTS, timeout_ms) or _visible_any(
        page, selectors.SUBMIT_BUTTONS, timeout_ms
    )


def has_workbench_marker(page: Any, timeout_ms: int = 1500) -> bool:
    return _visible_any(page, selectors.WORKBENCH_MARKERS, timeout_ms)


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
# 提交降级链（2026-09-22 采集机实测新增）
# ==================================================================
# 起因（真实 traceback）：``button.login-btn:visible`` **已定位成功**（element is visible, enabled
# and stable），但真实点击的落点被激活面板的 ``input#mobile`` 子树与 sticky 页签容器
# ``div.rocket-tabs...login-tabs`` 拦截（pointer events），Playwright 硬等默认 30s 才抛
# ``TimeoutError``——单条路径吃满 30s；若再叠加 3 条降级路径会放大到 90s+。
#
# 对策：把「提交」拆成多条**短超时**降级路径，逐条尝试；每条执行后用**可观测信号**判定是否生效，
# 未生效才降级下一条。**严禁**把「点了但没反应」当成成功（红线：宁停不猜）。
#
# 合成点击（dispatch_event）说明：它派发的是 ``isTrusted=false`` 的 DOM 事件，**不是**真实鼠标动作，
# 也无法绕过页面自身的校验与风控；仅作最后兜底，命中即记日志便于复盘。
# 官方语义：locator.dispatch_event = "Programmatically dispatch an event on the matching element"
# https://playwright.dev/python/docs/api/class-locator#locator-dispatch-event


def submit_effect_signal(
    *,
    url: str,
    baseline_url: str,
    has_workbench_marker: bool,
    has_login_form: bool,
    challenge: ChallengeType | None,
    credential_error: bool,
) -> str | None:
    """判定「提交动作是否已被页面接收并生效」（纯函数，可离线单测）。

    **不以「有没有抛异常」判定**，只看可观测信号（任一命中即认为提交已生效）：

    1. 出现挑战（验证码/滑块/短信/风控）→ 提交已触发，只是需人工介入；
    2. URL 相对提交前发生变化（含重定向）；
    3. 出现工作台特征元素；
    4. 出现凭据错误提示；
    5. 登录表单消失（页面已切换）。

    以上都未命中 = 「点了但没反应」，返回 None，**绝不能当成成功**。
    """
    if challenge is not None:
        return f"出现 {challenge.value} 挑战"
    if url != baseline_url:
        return f"页面地址变化（{_clip_text(url, 120)}）"
    if has_workbench_marker:
        return "出现工作台特征元素"
    if credential_error:
        return "出现凭据错误提示"
    if not has_login_form:
        return "登录表单已消失"
    return None


def observe_submit_effect(
    page: Any,
    settings: Settings,
    baseline_url: str,
    timeout_ms: int,
    poll_interval_ms: int = 500,
) -> str | None:
    """在 ``timeout_ms`` 预算内轮询观测提交是否生效；未生效返回 None。

    复用既有的挑战检测（:func:`detect_challenge_from_text`）与凭据错误识别。
    可见性探测传 ``timeout=0``（= 立即扫描一轮，非「不限时」），避免单轮探测本身吃掉整段预算。
    """
    deadline = time.monotonic() + max(timeout_ms, 0) / 1000.0
    while True:
        page_text = _page_text(page, poll_interval_ms)
        signal = submit_effect_signal(
            url=page.url,
            baseline_url=baseline_url,
            has_workbench_marker=has_workbench_marker(page, 0),
            has_login_form=has_login_form(page, 0),
            challenge=detect_challenge_from_text(page_text),
            credential_error=is_credential_error_text(page_text),
        )
        if signal is not None:
            return signal
        if time.monotonic() >= deadline:
            return None
        time.sleep(poll_interval_ms / 1000.0)


def _dismiss_overlays(page: Any) -> None:
    """尝试收起可能遮挡提交按钮的浮层（自动完成/输入提示）。

    先 ``mouse.move(0, 0)`` 让 hover 态收起，再按 Escape 交给页面自身的浮层逻辑关闭。
    **不确定**：Escape 能否关闭任意自定义浮层取决于页面实现，官方未做此承诺
    （Escape 键名依据：https://playwright.dev/python/docs/api/class-keyboard#keyboard-press ）。
    验证方法：采集机实跑时观察遮挡者是否从「下拉浮层」变为按钮自身。无论是否生效都继续走降级链。
    """
    try:
        page.mouse.move(0, 0)
    except Exception:
        pass
    try:
        page.keyboard.press("Escape")
    except Exception:
        pass


# 单条降级路径的动作签名：(page, submit, password, timeout_ms) -> None
SubmitAction = Callable[[Any, Any, Any, int], None]


def _submit_press_enter(page: Any, submit: Any, password: Any, timeout_ms: int) -> None:
    """路径 A：在密码框上回车提交（首选，最接近人工）。

    ``locator.press`` = 先聚焦元素，再用 ``keyboard.down`` / ``keyboard.up`` 发按键；
    按键**不依赖鼠标坐标**，天然绕开「点击落点被覆盖」。
    官方依据：https://playwright.dev/python/docs/api/class-locator#locator-press
    """
    password.press("Enter", timeout=timeout_ms)


def _submit_click(page: Any, submit: Any, password: Any, timeout_ms: int) -> None:
    """路径 B：正常真实点击（含落点遮挡预校验与居中滚动）。"""
    click_with_occlusion_guard(page, submit, timeout_ms=timeout_ms)


def _submit_force_click(page: Any, submit: Any, password: Any, timeout_ms: int) -> None:
    """路径 C：强制点击。

    ``force=True`` 跳过 actionability 检查（含 Receives Events），但官方说明**仍会**用
    ``page.mouse`` 在元素中心点击，故落点仍可能被覆盖——只作降级手段，不保证一定生效。
    官方依据：https://playwright.dev/python/docs/api/class-locator#locator-click
    """
    submit.click(force=True, timeout=timeout_ms)


def _submit_dispatch(page: Any, submit: Any, password: Any, timeout_ms: int) -> None:
    """路径 D：派发合成 click 事件（最后兜底，事件为 ``isTrusted=false``）。"""
    submit.dispatch_event("click", timeout=timeout_ms)


# 降级链顺序即尝试顺序：A 回车（最像人工、绕开遮挡）→ B 真实点击 → C 强制点击 → D 合成点击。
_SUBMIT_PATHS: tuple[tuple[str, SubmitAction], ...] = (
    ("A-回车提交", _submit_press_enter),
    ("B-真实点击", _submit_click),
    ("C-强制点击", _submit_force_click),
    ("D-合成点击", _submit_dispatch),
)
# 降级路径条数：与配置预算一起决定提交阶段的总耗时上限（见 settings.example.toml 注释）
SUBMIT_PATH_COUNT = len(_SUBMIT_PATHS)


def submit_with_degradation(
    page: Any, submit: Any, password: Any, *, baseline_url: str, settings: Settings
) -> str:
    """按 :data:`_SUBMIT_PATHS` 顺序尝试提交，返回**已生效**的路径名。

    每条路径：先做低成本遮挡消除 → 执行动作（短超时）→ 用可观测信号观测是否生效。
    生效即返回；未生效则记日志并降级下一条。全部未生效 → 抛
    :class:`LoginElementBlockedError`（点不动/被遮挡是最可能的归因），**绝不返回成功**。
    """
    _dismiss_overlays(page)
    timeout_ms = settings.login.submit_path_timeout_ms
    probe_ms = settings.login.submit_effect_probe_ms
    last_error: BaseException | None = None
    for index, (label, action) in enumerate(_SUBMIT_PATHS, start=1):
        logger.info("提交按钮：尝试第 %d/%d 条路径「%s」", index, SUBMIT_PATH_COUNT, label)
        try:
            action(page, submit, password, timeout_ms)
        except Exception as exc:
            # 单条路径失败不中断降级链（如路径 B 的落点遮挡预校验会直接抛错）
            last_error = exc
            logger.info("提交按钮：路径「%s」执行未成功：%s", label, _clip_text(str(exc), 160))
            continue
        signal = observe_submit_effect(page, settings, baseline_url, probe_ms)
        if signal is not None:
            logger.info("提交按钮：路径「%s」已生效（可观测信号：%s）", label, signal)
            return label
        logger.info("提交按钮：路径「%s」已执行但未观测到生效信号，降级下一条", label)

    labels = "/".join(label for label, _ in _SUBMIT_PATHS)
    last_error_text = _clip_text(str(last_error)) if last_error is not None else "无"
    raise LoginElementBlockedError(
        f"{SUBMIT_PATH_COUNT} 条提交路径（{labels}）均未使登录生效：提交按钮很可能被其它元素遮挡"
        f"（pointer events 被拦截），或页面结构已变化。最后一条路径的报错：{last_error_text}。"
        + _SELECTOR_HINT
    )


# ---------------- 填表自检（提交前，仅统计字符数） ----------------
def _input_length(element: Any, stage: str) -> int:
    """读取输入框字符数；**只返回长度，绝不返回值本身**（红线：密码不落日志/异常）。

    读取失败按 :class:`LoginPageError` 上抛（定位到的元素可能已不是可读输入框）。
    官方依据（``locator.input_value`` 返回 ``input.value``）：
    https://playwright.dev/python/docs/api/class-locator#locator-input-value
    """
    try:
        value = element.input_value()
    except Exception as exc:
        raise classify_browser_failure(exc, f"{stage}内容自检") from exc
    return len(value or "")


def verify_credentials_filled(account_element: Any, password_element: Any) -> tuple[int, int]:
    """提交前自检：账号框与密码框**各自都非空**，返回 ``(账号字符数, 密码字符数)``。

    为什么这样做：本次故障（2026-09-22 采集机实测）暴露了「输入可能未真正落到框里」，
    带着空框去提交只会得到难以归因的失败。此处只统计**字符长度**用于日志，
    绝不读取/记录/返回输入值本身（密码尤甚），从源头保证密码不进日志与异常。
    """
    account_len = _input_length(account_element, "账号输入")
    password_len = _input_length(password_element, "密码输入")
    if account_len == 0 or password_len == 0:
        empty = [name for name, length in (("账号", account_len), ("密码", password_len)) if length == 0]
        raise LoginPageError(
            f"提交前自检失败：{'、'.join(empty)}输入框为空，已中止提交（不用空值登录）。"
            "请核对输入是否真正落到框内。" + _SELECTOR_HINT
        )
    logger.info("提交前自检通过：账号 %d 字符、密码 %d 字符（仅记录长度，不记录内容）", account_len, password_len)
    return account_len, password_len


# ==================================================================
# 人工介入登录（login --manual）
# 现场人工在浏览器窗口里完成挑战（程序全程不识别、不拖动、不点击挑战元素），
# 程序只做**只读**轮询判定登录态。红线不变：挑战只做「识别到 → 交人工」。
# ==================================================================
def page_is_closed(page: Any) -> bool:
    """页面是否已关闭（人工中途关窗的判定入口）。

    优先用 Playwright ``page.is_closed()``：官方语义为「页面已关闭则返回 True」，
    只读且不发协议请求（https://playwright.dev/python/docs/api/class-page#page-is-closed ）。
    该接口不可用时，回落到一次只读的 ``title()`` 往返——页面已断开时 Playwright 会抛
    ``TargetClosedError``，据此判定；其它异常不臆断（交由后续只读操作兜底）。
    """
    try:
        return bool(page.is_closed())
    except Exception:
        pass
    try:
        page.title()
        return False
    except Exception as exc:
        return is_browser_closed_error(exc)


class ManualPollOutcome(str, Enum):
    """人工登录只读轮询的终止原因。"""

    LOGGED_IN = "LOGGED_IN"                # 检测到登录成功
    CREDENTIAL_ERROR = "CREDENTIAL_ERROR"  # 页面提示账号或密码错误（不重试，走更新凭据分支）
    TIMEOUT = "TIMEOUT"                    # 等满等待窗口仍无成功信号
    BROWSER_CLOSED = "BROWSER_CLOSED"      # 人工中途关闭了浏览器窗口/标签页


@dataclass(frozen=True)
class ManualSnapshot:
    """一轮只读检测的快照（由 :func:`read_manual_snapshot` 产出）。"""

    logged_in: bool
    credential_error: bool


def poll_manual_login(
    *,
    observe: Callable[[], ManualSnapshot],
    wait_seconds: float,
    poll_interval_s: float,
    now: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> ManualPollOutcome:
    """按固定间隔只读轮询，返回终止原因（纯逻辑，时钟/休眠可注入以便单测）。

    每轮只调用 ``observe`` 做**只读**检测，本函数自身**不触碰页面**（不点击、不输入、不滚动），
    以免与现场人工操作打架。终止条件（先判成功、再判凭据错误、最后判超时）：

    - 检出登录成功 → ``LOGGED_IN``（立即返回，不再多等一轮）；
    - 页面提示凭据错误 → ``CREDENTIAL_ERROR``（继续等也没意义，交由上层走更新凭据分支）；
    - ``observe`` 抛出「浏览器已关闭」类异常 → ``BROWSER_CLOSED``（不得归因为超时）；
    - 等满 ``wait_seconds`` 仍无上述信号 → ``TIMEOUT``。
    """
    interval = max(poll_interval_s, 0.0)
    deadline = now() + max(wait_seconds, 0.0)
    while True:
        try:
            snapshot = observe()
        except Exception as exc:
            if is_browser_closed_error(exc):
                return ManualPollOutcome.BROWSER_CLOSED
            raise  # 其它异常不吞：交上层统一收敛
        if snapshot.logged_in:
            return ManualPollOutcome.LOGGED_IN
        if snapshot.credential_error:
            return ManualPollOutcome.CREDENTIAL_ERROR
        if now() >= deadline:
            return ManualPollOutcome.TIMEOUT
        sleep(interval)


def read_manual_snapshot(page: Any, settings: Settings) -> ManualSnapshot:
    """人工等待期间的一轮**只读**检测，复用 :func:`judge_login_result` 判定。

    只读页面地址、可见性、正文，**不点击、不输入、不滚动**任何元素，避免干扰人工操作。
    判定逻辑**复用既有** :func:`judge_login_result`，不另起一套标准。
    页面/上下文已被关闭时抛 :class:`BrowserClosedError`，由 :func:`poll_manual_login`
    归类为 ``BROWSER_CLOSED``（与超时严格区分）。
    """
    if page_is_closed(page):
        raise BrowserClosedError("人工登录等待期间浏览器窗口或标签页已被关闭")
    text = _page_text(page, settings.login.probe_timeout_ms)
    outcome = judge_login_result(
        url=page.url,
        allowed_hosts=settings.site.allowed_hosts,
        has_workbench_marker=has_workbench_marker(page, 0),
        has_login_form=has_login_form(page, 0),
        challenge=detect_challenge_from_text(text),
    )
    return ManualSnapshot(
        logged_in=outcome is LoginOutcome.LOGGED_IN,
        credential_error=is_credential_error_text(text),
    )


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


@dataclass(frozen=True)
class ManualReadyInfo:
    """人工登录就绪信息（浏览器已打开、预填是否完成），供 CLI 打印现场指引。"""

    prefilled: bool
    prefill_note: str | None = None


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
        """执行登录（已登录则跳过）并返回结果。

        **全流程异常收敛**（2026-09-22 采集机实测暴露的缺陷：Playwright 点击超时直接抛栈）：

        - 挑战/风控 → 先落库 ``MANUAL_REQUIRED``，再抛 :class:`ManualInterventionRequired`（退出码 2）；
        - 定位失败 / 超时 / 遮挡 / 浏览器错误 → 落库 ``FAILED`` 并返回失败结果（退出码 1）；
        - 任何失败都带**脱敏后**的 ``fail_reason``，绝不把裸 traceback 抛到 CLI 顶层。
        """
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

            try:
                return self._run_flow()
            except ManualInterventionRequired as exc:
                # 落库失败不得掩盖「必须人工介入」这一结论：日志告警后仍按退出码 2 上抛
                try:
                    self._persist_failure(LoginOutcome.MANUAL_REQUIRED, exc.reason)
                except LoginPersistenceError as persist_exc:
                    logger.warning("人工介入结论未能落库（不影响停止自动化）：%s", persist_exc)
                raise
            except LoginPersistenceError:
                # 落库自身失败不能伪装成业务失败，交由 CLI 按「部分失败」处理
                raise
            except browser.BrowserLaunchError as exc:
                logger.error("浏览器启动/运行失败：%s；%s", self._account.masked_description(), exc)
                return self._persist_failure(LoginOutcome.FAILED, f"浏览器启动/运行失败：{exc}")
            except LoginError as exc:
                logger.error("登录失败：%s；%s", self._account.masked_description(), exc)
                return self._persist_failure(LoginOutcome.FAILED, str(exc))
            except Exception as exc:
                # 非预期异常同样收敛：debug 留堆栈，info 只给结论
                logger.debug("登录流程未预期异常", exc_info=True)
                mapped = classify_browser_failure(exc, "登录流程")
                logger.error("登录失败：%s；%s", self._account.masked_description(), mapped)
                return self._persist_failure(LoginOutcome.FAILED, str(mapped))
        finally:
            self._clear_secret_redaction()

    def manual(
        self,
        *,
        prefill: bool = True,
        on_ready: Callable[[ManualReadyInfo], None] | None = None,
    ) -> LoginResult:
        """人工介入登录（``login --manual``）：有头浏览器交给现场人工，程序只读轮询登录态。

        与 :meth:`run` 的边界：
        - **互不调用**：``run`` 检出挑战即抛 :class:`ManualInterventionRequired`，绝不自动转入本模式；
          本模式也不会自动提交或处理挑战（红线：挑战只做「识别到 → 交人工」）。
        - **不是默认行为**：仅由 CLI 显式传 ``--manual`` 触发。

        结束分支（与 CLI 退出码一一对应）：
        - 检测到登录成功 → 导出 storage_state、落库 ``SUCCESS``、返回结果（退出码 0）；
        - 页面提示凭据错误 → 落库 ``FAILED`` 并返回（退出码 1，提示更新凭据）；
        - 等满 ``manual_wait_minutes`` → 落库 ``MANUAL_REQUIRED`` 后抛
          :class:`ManualLoginTimeoutError`（退出码 2）；
        - 人工中途关窗 → 落库 ``MANUAL_REQUIRED`` 后抛 :class:`ManualBrowserClosedError`（退出码 2）。
        """
        if self._settings.browser.headless:
            # 人工看不到无头窗口，本模式必须有头；**不静默改配置**，直接以明确错误退出
            raise ManualLoginConfigError(
                "人工介入登录要求有头浏览器（人工需要看到窗口才能完成挑战），"
                "但配置 browser.headless = true。请把 browser.headless 改为 false 后重跑"
                "（或设置环境变量 YIZHAN__BROWSER__HEADLESS=false）"
            )

        self._register_secret_for_redaction()
        try:
            try:
                return self._run_manual_flow(prefill=prefill, on_ready=on_ready)
            except (ManualLoginTimeoutError, ManualBrowserClosedError):
                # 已带明确结论且已落库，直达 CLI 按对应退出码处理
                raise
            except browser.BrowserLaunchError as exc:
                logger.error("人工登录：浏览器启动/运行失败：%s；%s", self._account.masked_description(), exc)
                return self._persist_failure(LoginOutcome.FAILED, f"浏览器启动/运行失败：{exc}")
            except LoginError as exc:
                logger.error("人工登录失败：%s；%s", self._account.masked_description(), exc)
                return self._persist_failure(LoginOutcome.FAILED, str(exc))
            except Exception as exc:
                logger.debug("人工登录未预期异常", exc_info=True)
                mapped = classify_browser_failure(exc, "人工登录流程")
                logger.error("人工登录失败：%s；%s", self._account.masked_description(), mapped)
                return self._persist_failure(LoginOutcome.FAILED, str(mapped))
        finally:
            self._clear_secret_redaction()

    def _run_manual_flow(
        self,
        *,
        prefill: bool,
        on_ready: Callable[[ManualReadyInfo], None] | None,
    ) -> LoginResult:
        """人工介入登录主流程：有头浏览器 → 预填（可关）→ 交人工 → 只读轮询 → 落库。

        上下文在 ``with`` 内保持打开：**只在**「登录成功 / 超时 / 浏览器被人工关闭」才退出并关闭，
        等待期间绝不主动 close —— 这是修复「提示人工介入却已把窗口关掉」这一流程缺陷的关键。
        """
        with browser.persistent_context(self._settings) as context:
            page = browser.open_page(context)
            self._goto_login(page)

            # 已有有效登录态则不必打扰人工
            if probe_state(page, self._settings) is LoginOutcome.LOGGED_IN:
                result = self._build_result(LoginOutcome.LOGGED_IN, context)
                self._persist(result)
                logger.info("人工登录：检测到已有有效登录态，无需人工操作：%s", self._account.masked_description())
                return result

            note = self._prepare_manual(page, prefill)
            if on_ready is not None:
                # 浏览器已打开、预填结论已定，此时告知操作人该做什么最准确
                on_ready(ManualReadyInfo(prefilled=note is None, prefill_note=note))

            outcome = poll_manual_login(
                observe=lambda: read_manual_snapshot(page, self._settings),
                wait_seconds=self._settings.login.manual_wait_minutes * 60,
                poll_interval_s=self._settings.login.manual_poll_interval_s,
            )

            if outcome is ManualPollOutcome.BROWSER_CLOSED:
                self._persist_manual_failure_quietly("人工登录等待期间浏览器窗口/标签页被关闭")
                raise ManualBrowserClosedError(
                    "浏览器窗口已被关闭，本轮人工登录已中止（**不是**「未检测到登录」）。"
                    "请重新执行 `python -m collector login --manual`"
                )

            if outcome is ManualPollOutcome.CREDENTIAL_ERROR:
                reason = (
                    "账号或密码错误（人工登录期间页面提示），按 ADR §20.4.3 不重试，"
                    "请站长更新凭据后重跑 init-local-secrets.ps1"
                )
                result = self._build_result(LoginOutcome.FAILED, None, fail_reason=reason)
                self._persist(result)
                logger.error("人工登录失败：%s；%s", self._account.masked_description(), reason)
                return result

            if outcome is ManualPollOutcome.TIMEOUT:
                minutes = self._settings.login.manual_wait_minutes
                reason = f"未在 {minutes} 分钟内检测到登录成功（人工登录超时）"
                self._persist_manual_failure_quietly(reason)
                raise ManualLoginTimeoutError(minutes, reason, _MANUAL_TIMEOUT_GUIDANCE)

            result = self._build_result(LoginOutcome.LOGIN_SUCCEEDED, context)
            self._persist(result)
            logger.info("人工介入登录成功：%s", self._account.masked_description())
            return result

    def _prepare_manual(self, page: Any, prefill: bool) -> str | None:
        """人工登录的预填步骤：返回 None 表示已预填，否则返回未预填的原因。

        预填**不致命**：失败一律降级为「不预填 + 提示人工全部手填」，仅把原因（脱敏、截断）带回。
        ``--no-prefill``（``prefill=False``）时**完全不触碰页面**，连账号密码框都不去定位。
        """
        if not prefill:
            return "本次以 --no-prefill 启动，未执行预填"
        try:
            self._prefill_credentials(page)
        except Exception as exc:
            from collector import logging_setup

            # 先整串脱敏再截断：截断后再脱敏会让被切断的密钥片段躲过替换
            reason = _clip_text(logging_setup.redact_text(str(exc)), 160)
            logger.warning("人工登录预填失败，降级为人工全部手填：%s", reason)
            return f"预填失败：{reason}"
        return None

    def _prefill_credentials(self, page: Any) -> None:
        """按人工节奏把账号密码预填进密码登录页签（**不提交**，提交交给现场人工）。

        失败向上抛，由 :meth:`_prepare_manual` 统一降级；本方法不吞异常、也不记录任何输入值。
        """
        ensure_password_login_mode(page, self._settings, self._humanizer)

        account_input = find_first_visible(page, selectors.ACCOUNT_INPUTS)
        if account_input is None:
            raise LoginPageError("未定位到账号输入框：ACCOUNT_INPUTS 与登录页结构不匹配。" + _SELECTOR_HINT)
        self._type_like_human(page, account_input, self._account.account, "账号输入")

        password_input = find_first_visible(page, selectors.PASSWORD_INPUTS)
        if password_input is None:
            raise LoginPageError("未定位到密码输入框：PASSWORD_INPUTS 与登录页结构不匹配。" + _SELECTOR_HINT)
        self._type_like_human(page, password_input, self._account.password, "密码输入")

        # 预填自检：两框都非空（只比对字符数，不读值），避免人工面对空框还得自己找问题
        verify_credentials_filled(account_input, password_input)
        logger.info("人工登录：账号与密码已预填（仅记录字符数，不记录内容），等待现场人工完成挑战并提交")

    def _persist_manual_failure_quietly(self, reason: str) -> None:
        """人工登录的失败结论落库；落库失败只告警，**不得**掩盖已确定的结论。"""
        try:
            self._persist_failure(LoginOutcome.MANUAL_REQUIRED, reason)
        except LoginPersistenceError as exc:
            logger.warning("人工登录结论未能落库（不影响结果判定）：%s", exc)

    def _run_flow(self) -> LoginResult:
        """登录主流程（探测 → 切页签 → 填表提交 → 结果判定）；异常由 :meth:`run` 统一收敛。"""
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
        self._type_like_human(page, account_input, self._account.account, "账号输入")

        password_input = find_first_visible(page, selectors.PASSWORD_INPUTS)
        if password_input is None:
            raise LoginPageError("未定位到密码输入框：PASSWORD_INPUTS 与登录页结构不匹配。" + _SELECTOR_HINT)
        self._type_like_human(page, password_input, self._account.password, "密码输入")

        # 提交前自检：两框都非空（只比对字符数，不读值），避免带着空框去提交
        verify_credentials_filled(account_input, password_input)

        submit = find_first_visible(page, selectors.SUBMIT_BUTTONS)
        if submit is None:
            raise LoginPageError("未定位到登录提交按钮：SUBMIT_BUTTONS 与登录页结构不匹配。" + _SELECTOR_HINT)
        self._humanizer.pause_before_submit()
        self._humanizer.move_to(page, submit)
        # 提交走多路径降级链（回车 → 真实点击 → 强制点击 → 合成点击），每条短超时并用可观测信号判定生效
        baseline_url = page.url
        submit_with_degradation(
            page, submit, password_input, baseline_url=baseline_url, settings=self._settings
        )

    def _type_like_human(self, page: Any, element: Any, value: str, stage: str) -> None:
        """按人工节奏输入；底层异常映射为明确的登录错误类型（不裸抛）。"""
        try:
            self._humanizer.move_to(page, element)
            self._humanizer.type_like_human(element, value)
        except Exception as exc:
            raise classify_browser_failure(exc, stage) from exc
        self._humanizer.pause_field()

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

    def _persist_failure(self, outcome: LoginOutcome, reason: str) -> LoginResult:
        """把失败/需人工介入的结论落库（状态取 ``_OUTCOME_TO_STATUS``），并返回结果对象。

        ``fail_reason`` 先**脱敏**再截断：底层异常文本可能夹带页面内容或敏感串，
        且该列长度为 512（repository 侧还会再截一次）。
        """
        from collector import logging_setup

        result = self._build_result(outcome, None, fail_reason=logging_setup.redact_text(reason or "")[:400])
        self._persist(result)
        return result

    def _register_secret_for_redaction(self) -> None:
        # 仅在流程存续期间把密码纳入日志脱敏表，用完即清（明文零落地，ADR §20.3.3）
        from collector import logging_setup

        logging_setup.add_sensitive(self._account.password)

    def _clear_secret_redaction(self) -> None:
        from collector import logging_setup

        logging_setup.clear_sensitive()
