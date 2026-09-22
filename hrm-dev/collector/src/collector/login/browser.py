# -*- coding: utf-8 -*-
"""Playwright **持久化上下文**（真实 Chromium），登录态的权威载体。

关键纪律：
1. 使用 ``launch_persistent_context(user_data_dir=...)``（ADR §20.2.5 / §18.3 补丁 3）——
   登录态落在 profile 中，CDP 附着后可直接复用 ``browser.contexts[0]``；
   多账号/多实例 = **每账号独立 profile**，禁止为「隔离」而 ``new_context()``，否则拿到的是未登录页面。
2. **不注入任何反检测脚本**：无 stealth、无 ``navigator.webdriver`` 改写、无 UA/指纹伪造（红线 C3）。
3. 启动参数收口（ADR §10.2.1 / §18.4）：拒绝 ``--remote-debugging-address``（严防把浏览器开放到
   局域网/公网）、``--remote-allow-origins=*``、``--user-agent``、代理参数。
"""
from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

from collector.config.settings import Settings

# 启动参数黑名单前缀：命中即拒绝启动，不做「警告后继续」
_FORBIDDEN_ARG_PREFIXES: tuple[str, ...] = (
    "--remote-debugging-address",  # 一旦指向 0.0.0.0 等于把已登录浏览器开放出去
    "--remote-allow-origins",      # 通配来源可被任意网页通过 WebSocket 劫持调试通道
    "--user-agent",                # UA 伪造/轮换，红线 C3
    "--proxy-server",              # 代理轮换，红线 C3
)


class BrowserLaunchError(Exception):
    """浏览器启动参数非法或启动失败。"""


def validate_extra_args(extra_args: tuple[str, ...] | list[str]) -> None:
    """校验额外启动参数，命中黑名单即抛错。"""
    for arg in extra_args:
        if not isinstance(arg, str) or not arg.strip():
            raise BrowserLaunchError(f"browser.extra_args 存在非法项：{arg!r}")
        normalized = arg.strip()
        for prefix in _FORBIDDEN_ARG_PREFIXES:
            if normalized.startswith(prefix):
                raise BrowserLaunchError(
                    f"browser.extra_args 含被禁止的参数 {normalized!r}："
                    "反检测伪装与对外暴露调试端口均属红线（ADR §3.3 C3 / §10.2.1）"
                )


def ensure_profile_dir(user_data_dir: Path) -> Path:
    """确保 profile 目录存在；该目录被 .gitignore 排除，严禁入库。"""
    user_data_dir.mkdir(parents=True, exist_ok=True)
    return user_data_dir


def open_page(context: Any) -> Any:
    """复用上下文已有标签页，没有才新建（避免每次登录多开空白页）。"""
    pages = context.pages
    return pages[0] if pages else context.new_page()


@contextmanager
def persistent_context(settings: Settings) -> Iterator[Any]:
    """打开持久化浏览器上下文并交给调用方，退出时关闭上下文。

    注意：``context.close()`` 会关闭浏览器（持久化上下文的语义），
    但 profile 目录与其中的登录态会保留在磁盘上，供后续复用。
    """
    validate_extra_args(settings.browser.extra_args)
    ensure_profile_dir(settings.browser.user_data_dir)

    try:
        from playwright.sync_api import sync_playwright  # type: ignore[import-not-found]
    except ImportError as exc:  # pragma: no cover - 取决于运行环境
        raise BrowserLaunchError(
            "未检测到 Playwright。请先安装依赖并下载浏览器：\n"
            "  pip install -r requirements.txt\n"
            "  python -m playwright install chromium"
        ) from exc

    browser_cfg = settings.browser
    launch_kwargs: dict[str, Any] = {
        "headless": browser_cfg.headless,
        "slow_mo": browser_cfg.slow_mo_ms,
        "args": list(browser_cfg.extra_args),
        "locale": browser_cfg.locale,
        "viewport": {"width": browser_cfg.viewport_width, "height": browser_cfg.viewport_height},
        # 后续导出通道取件需要下载能力（ADR §18.3 补丁 1 的前置）
        "accept_downloads": True,
    }
    if browser_cfg.channel:
        launch_kwargs["channel"] = browser_cfg.channel
    if browser_cfg.executable_path:
        launch_kwargs["executable_path"] = browser_cfg.executable_path

    with sync_playwright() as playwright:
        try:
            context = playwright.chromium.launch_persistent_context(
                str(browser_cfg.user_data_dir), **launch_kwargs
            )
        except Exception as exc:
            raise BrowserLaunchError(
                f"启动持久化上下文失败（profile={browser_cfg.user_data_dir}）：{exc}；"
                "若提示浏览器未安装，请执行 python -m playwright install chromium"
            ) from exc
        try:
            context.set_default_timeout(browser_cfg.default_timeout_ms)
            context.set_default_navigation_timeout(browser_cfg.navigation_timeout_ms)
            yield context
        finally:
            try:
                context.close()
            except Exception:
                # 关闭失败不影响主流程结论；profile 已落盘
                pass
