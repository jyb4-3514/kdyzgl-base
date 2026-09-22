# -*- coding: utf-8 -*-
"""只读诊断：导出目标站登录页真实结构，用于校准 ``login/selectors.py``。

为什么需要它：
    登录页 DOM 无法从开发机访问，``selectors.py`` 的候选选择器只能是"未核实"状态。
    首次实测必然出现「未定位到账号输入框」——此时**不应靠猜**，而应先导出真实结构，
    用实测数据回填选择器。目标站改版后同样用本脚本重新校准。

纪律（红线）：
    - **只读**：不输入任何内容、不点击提交、不保存 cookie 明文、不做任何验证码处理
    - 不注入反检测脚本（无 stealth / 无 navigator.webdriver 改写 / 无 UA 伪造）
    - 导出的 JSON 只含 DOM 语义属性，不含任何凭据

用法（在 hrm-dev/collector 目录下）：
    $env:PYTHONPATH = "src"
    python scripts/dump-login-page.py
    python scripts/dump-login-page.py --headless        # 强制无头（排障用）
    python scripts/dump-login-page.py --settle-ms 6000  # 页面较慢时多等
"""
from __future__ import annotations

import argparse
import dataclasses
import json
import sys
from datetime import datetime
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT / "src"))

from collector.config.settings import load_settings  # noqa: E402
from collector.login.browser import persistent_context  # noqa: E402

# 采集器：在页面/框架上下文中执行，返回可 JSON 序列化的结构。
# 只取语义属性，不取 value（避免把任何已填内容带出来）。
_COLLECTOR_JS = r"""
() => {
  const vis = (el) => {
    try {
      const r = el.getBoundingClientRect();
      const s = window.getComputedStyle(el);
      return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';
    } catch (e) { return false; }
  };
  const clip = (v, n) => (v == null ? null : String(v).slice(0, n));
  const pick = (el, attrs) => {
    const o = {};
    for (const a of attrs) { o[a] = clip(el.getAttribute(a), 120); }
    o.tag = el.tagName.toLowerCase();
    o.text = clip((el.innerText || el.textContent || '').trim(), 60);
    o.visible = vis(el);
    return o;
  };

  const inputs = Array.from(document.querySelectorAll('input, textarea, select'))
    .slice(0, 60)
    .map((el) => {
      const o = pick(el, ['type', 'name', 'id', 'class', 'placeholder', 'aria-label', 'autocomplete', 'maxlength', 'role']);
      o.readOnly = !!el.readOnly;
      o.disabled = !!el.disabled;
      return o;
    });

  const buttons = Array.from(document.querySelectorAll('button, [role="button"], a[class*="btn"], a[class*="login"]'))
    .slice(0, 60)
    .map((el) => pick(el, ['type', 'name', 'id', 'class', 'aria-label', 'role', 'href']));

  const forms = Array.from(document.querySelectorAll('form')).slice(0, 20)
    .map((el) => pick(el, ['id', 'class', 'name', 'action', 'method']));

  const iframes = Array.from(document.querySelectorAll('iframe')).slice(0, 20)
    .map((el) => pick(el, ['id', 'class', 'name', 'src', 'title']));

  // 与登录/验证相关的可见文本片段，用于识别登录方式页签与挑战信号
  const KEYWORDS = ['登录', '密码', '验证码', '短信', '微信', '二维码', '滑块', '拖动', '拼图', '安全验证', '账号'];
  const hits = [];
  const walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const t = (node.nodeValue || '').trim();
    if (!t || t.length > 40) { continue; }
    if (KEYWORDS.some((k) => t.includes(k))) {
      const parent = node.parentElement;
      hits.push({ text: t, tag: parent ? parent.tagName.toLowerCase() : null, cls: parent ? clip(parent.getAttribute('class'), 100) : null });
      if (hits.length >= 80) { break; }
    }
  }

  const links = Array.from(document.querySelectorAll('a[href]'))
    .slice(0, 80)
    .map((el) => ({ text: clip((el.innerText || '').trim(), 40), href: clip(el.getAttribute('href'), 200) }));

  const bodyText = (document.body ? document.body.innerText : '').replace(/\s+/g, ' ').trim();

  return {
    url: location.href,
    title: document.title,
    readyState: document.readyState,
    hasPasswordInput: !!document.querySelector('input[type="password"]'),
    inputCount: inputs.length,
    inputs,
    buttons,
    forms,
    iframes,
    links,
    keywordHits: hits,
    bodyTextSample: bodyText.slice(0, 1500)
  };
}
"""


def _collect(frame, label: str) -> dict:
    try:
        data = frame.evaluate(_COLLECTOR_JS)
        data["frameLabel"] = label
        data["frameUrl"] = frame.url
        return data
    except Exception as exc:  # 跨域/已卸载等；如实记录，不伪装成功
        return {"frameLabel": label, "frameUrl": getattr(frame, "url", ""), "error": f"{type(exc).__name__}: {exc}"}


def main() -> int:
    parser = argparse.ArgumentParser(description="导出目标站登录页结构（只读诊断）")
    parser.add_argument("--headless", action="store_true", help="强制无头（覆盖配置）")
    parser.add_argument("--url", default="", help="覆盖登录地址（不改配置文件，用于试探真实入口）")
    parser.add_argument("--settle-ms", type=int, default=5000, help="页面加载后额外等待毫秒（默认 5000）")
    parser.add_argument("--out", default="", help="输出文件路径（默认 runtime/diagnostics/login-page-<时间>.json）")
    args = parser.parse_args()

    settings = load_settings()
    # 配置 dataclass 是 frozen 的：只读不可变是刻意设计，覆盖需走 replace 生成新实例
    if args.headless:
        settings = dataclasses.replace(settings, browser=dataclasses.replace(settings.browser, headless=True))
    if args.url:
        settings = dataclasses.replace(settings, site=dataclasses.replace(settings.site, login_url=args.url))

    out_path = (
        Path(args.out)
        if args.out
        else settings.paths.runtime_dir / "diagnostics" / f"login-page-{datetime.now():%Y%m%d-%H%M%S}.json"
    )
    out_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"[诊断] 目标地址：{settings.site.login_url}")
    print(f"[诊断] profile：{settings.browser.user_data_dir}（headless={settings.browser.headless}）")

    report: dict = {
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "loginUrl": settings.site.login_url,
        "headless": settings.browser.headless,
        "frames": [],
    }

    with persistent_context(settings) as context:
        page = context.pages[0] if context.pages else context.new_page()
        page.goto(settings.site.login_url, wait_until="domcontentloaded")
        page.wait_for_timeout(args.settle_ms)

        report["mainFrame"] = _collect(page.main_frame, "main")
        for idx, frame in enumerate(page.frames):
            if frame == page.main_frame:
                continue
            report["frames"].append(_collect(frame, f"child-{idx}"))

    out_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    # ---- 控制台摘要：只打结论性信息，便于快速判断 ----
    main = report["mainFrame"]
    print(f"[诊断] 最终 URL：{main.get('url')}")
    print(f"[诊断] 标题：{main.get('title')}")
    print(f"[诊断] 存在密码框：{main.get('hasPasswordInput')}；input 数={main.get('inputCount')}；子框架数={len(report['frames'])}")
    for i in main.get("inputs", [])[:20]:
        print(f"  input type={i.get('type')} name={i.get('name')} id={i.get('id')} ph={i.get('placeholder')} "
              f"aria={i.get('aria-label')} cls={i.get('class')} visible={i.get('visible')}")
    for b in main.get("buttons", [])[:15]:
        print(f"  button text={b.get('text')!r} type={b.get('type')} id={b.get('id')} cls={b.get('class')}")
    kws = [h.get("text") for h in main.get("keywordHits", [])][:25]
    print(f"[诊断] 关键词命中文本：{kws}")
    # 链接：优先看疑似登录/工作台入口，用于定位真实登录地址
    hot = [l for l in main.get("links", []) if any(k in (l.get("text") or "") + (l.get("href") or "")
                                                  for k in ("登录", "login", "work", "admin", "门店", "工作台"))]
    print(f"[诊断] 疑似登录入口链接（{len(hot)} 条）：")
    for l in hot[:25]:
        print(f"  text={l.get('text')!r} href={l.get('href')}")
    print(f"[诊断] 可见文本片段：{(main.get('bodyTextSample') or '')[:400]}")
    print(f"[诊断] 已写出：{out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
