# -*- coding: utf-8 -*-
"""非敏感配置加载（config/settings.toml）。

设计要点：
1. 本模块只处理**非敏感**配置；账号密码等凭据一律走 config/local/local.secrets.json
   （DPAPI 密文），由 config/pdd_account.py 负责，本模块不接触任何明文密码。
2. 任何值都可被环境变量覆盖，命名规则 ``YIZHAN__<节>__<键>``（全大写），
   例如 ``YIZHAN__SITE__LOGIN_URL``、``YIZHAN__BROWSER__HEADLESS``。
   嵌套节点（如 humanize.mouse）的环境变量节名用下划线连接：``YIZHAN__HUMANIZE_MOUSE__ENABLED``。
3. 路径类配置相对「采集端根目录」解析，不依赖进程当前工作目录。
"""
from __future__ import annotations

import os
import tomllib
from dataclasses import dataclass
from pathlib import Path
from typing import Any

# <仓库>/hrm-dev/collector/src/collector/config/settings.py
#   parents[0]=config  [1]=collector  [2]=src  [3]=collector 根目录
COLLECTOR_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_SETTINGS_FILE = COLLECTOR_ROOT / "config" / "settings.toml"
SETTINGS_EXAMPLE_FILE = COLLECTOR_ROOT / "config" / "settings.example.toml"
DEFAULT_SECRETS_FILE = COLLECTOR_ROOT / "config" / "local" / "local.secrets.json"

ENV_PREFIX = "YIZHAN__"


class ConfigError(Exception):
    """配置缺失、格式非法或取值越界。"""


def resolve_path(value: str | Path, base: Path = COLLECTOR_ROOT) -> Path:
    """相对路径按采集端根目录展开，绝对路径原样返回。"""
    path = Path(value)
    return path if path.is_absolute() else (base / path).resolve()


def _env(section: str, key: str, default: Any) -> Any:
    """读取环境变量覆盖值，并按默认值类型做转换。"""
    name = f"{ENV_PREFIX}{section.upper()}__{key.upper()}"
    if name not in os.environ:
        return default
    raw = os.environ[name]
    try:
        if isinstance(default, bool):
            return raw.strip().lower() in {"1", "true", "yes", "on"}
        if isinstance(default, int):
            return int(raw)
        if isinstance(default, float):
            return float(raw)
        if isinstance(default, (list, tuple)):
            return tuple(item.strip() for item in raw.split(",") if item.strip())
        return raw
    except (TypeError, ValueError) as exc:
        raise ConfigError(f"环境变量 {name} 取值非法：{exc}") from exc


def _pick(raw: dict, toml_section: str, key: str, default: Any, env_section: str | None = None) -> Any:
    value = raw.get(toml_section, {}).get(key, default)
    return _env(env_section or toml_section, key, value)


def _check_range(label: str, low: int, high: int) -> None:
    if low < 0 or high < 0:
        raise ConfigError(f"{label} 不允许为负值（当前 {low}~{high}）")
    if low > high:
        raise ConfigError(f"{label} 区间非法：下限 {low} 大于上限 {high}")


@dataclass(frozen=True)
class SiteConfig:
    """登录目标站点。入口与真实工作台可能是不同域名，故 URL 必须可配。"""

    login_url: str
    workbench_url: str
    allowed_hosts: tuple[str, ...]


@dataclass(frozen=True)
class MouseConfig:
    """鼠标自然移动参数（可选能力，默认关闭）。"""

    enabled: bool
    steps_min: int
    steps_max: int


@dataclass(frozen=True)
class HumanizeConfig:
    """「仿人工」的**唯一**合法范围：人类节奏与停顿，不含任何反检测语义。"""

    char_delay_min_ms: int
    char_delay_max_ms: int
    field_pause_min_ms: int
    field_pause_max_ms: int
    before_submit_pause_min_ms: int
    before_submit_pause_max_ms: int
    random_seed: int
    mouse: MouseConfig


@dataclass(frozen=True)
class BrowserConfig:
    """浏览器持久化上下文参数。"""

    user_data_dir: Path
    headless: bool
    channel: str | None
    executable_path: str | None
    slow_mo_ms: int
    default_timeout_ms: int
    navigation_timeout_ms: int
    locale: str
    viewport_width: int
    viewport_height: int
    extra_args: tuple[str, ...]


@dataclass(frozen=True)
class LoginConfig:
    """登录流程参数。"""

    probe_timeout_ms: int
    submit_wait_ms: int
    submit_path_timeout_ms: int
    submit_effect_probe_ms: int
    export_storage_state: bool
    storage_state_rel_path: str
    auto_login_enabled: bool
    session_ttl_hours: int
    # 人工介入登录（login --manual）：等待现场人工完成挑战的时长与只读轮询间隔
    manual_wait_minutes: int
    manual_poll_interval_s: int


@dataclass(frozen=True)
class DbConfig:
    """MySQL 连接参数（不含密码，密码从 DPAPI 密钥文件取）。"""

    host: str
    port: int
    database: str
    connect_timeout_s: int
    read_timeout_s: int
    write_timeout_s: int
    charset: str
    retry_attempts: int
    retry_backoff_s: float


@dataclass(frozen=True)
class PathsConfig:
    runtime_dir: Path
    log_dir: Path
    secrets_file: Path


@dataclass(frozen=True)
class LoggingConfig:
    level: str
    max_bytes: int
    backup_count: int


@dataclass(frozen=True)
class Settings:
    site: SiteConfig
    browser: BrowserConfig
    humanize: HumanizeConfig
    login: LoginConfig
    db: DbConfig
    paths: PathsConfig
    logging: LoggingConfig
    source_file: Path

    def storage_state_path(self, account_hash: str) -> Path:
        """storage_state 落盘路径（被 .gitignore 排除；profile 才是登录态的权威载体）。"""
        rel = self.login.storage_state_rel_path.format(account_hash=account_hash)
        return resolve_path(rel)


def _build(raw: dict, source_file: Path) -> Settings:
    # 默认值与 config/settings.example.toml 保持一致：登录页取 2026-09-22 采集机实测的 mdkd 地址
    # （mcmd.pinduoduo.com/home 是营销落地页，实测无登录入口，仅保留在 allowed_hosts 供跳转判定）
    site = SiteConfig(
        login_url=str(_pick(raw, "site", "login_url", "https://mdkd.pinduoduo.com/login")),
        workbench_url=str(_pick(raw, "site", "workbench_url", "https://mdkd.pinduoduo.com/")),
        allowed_hosts=tuple(_pick(raw, "site", "allowed_hosts", ("mdkd.pinduoduo.com", "mcmd.pinduoduo.com"))),
    )
    if not site.login_url.startswith("https://"):
        raise ConfigError(f"site.login_url 必须为 https:// 开头（当前 {site.login_url}）")
    if not site.allowed_hosts:
        raise ConfigError("site.allowed_hosts 不能为空：登录态判定依赖域名白名单")

    mouse = MouseConfig(
        enabled=bool(_pick(raw, "humanize.mouse", "enabled", False, env_section="humanize_mouse")),
        steps_min=int(_pick(raw, "humanize.mouse", "steps_min", 5, env_section="humanize_mouse")),
        steps_max=int(_pick(raw, "humanize.mouse", "steps_max", 12, env_section="humanize_mouse")),
    )
    _check_range("humanize.mouse.steps", mouse.steps_min, mouse.steps_max)

    humanize = HumanizeConfig(
        char_delay_min_ms=int(_pick(raw, "humanize", "char_delay_min_ms", 60)),
        char_delay_max_ms=int(_pick(raw, "humanize", "char_delay_max_ms", 180)),
        field_pause_min_ms=int(_pick(raw, "humanize", "field_pause_min_ms", 300)),
        field_pause_max_ms=int(_pick(raw, "humanize", "field_pause_max_ms", 900)),
        before_submit_pause_min_ms=int(_pick(raw, "humanize", "before_submit_pause_min_ms", 500)),
        before_submit_pause_max_ms=int(_pick(raw, "humanize", "before_submit_pause_max_ms", 1500)),
        random_seed=int(_pick(raw, "humanize", "random_seed", 0)),
        mouse=mouse,
    )
    _check_range("humanize.char_delay", humanize.char_delay_min_ms, humanize.char_delay_max_ms)
    _check_range("humanize.field_pause", humanize.field_pause_min_ms, humanize.field_pause_max_ms)
    _check_range(
        "humanize.before_submit_pause",
        humanize.before_submit_pause_min_ms,
        humanize.before_submit_pause_max_ms,
    )

    browser = BrowserConfig(
        user_data_dir=resolve_path(_pick(raw, "browser", "user_data_dir", "runtime/browser-profile")),
        headless=bool(_pick(raw, "browser", "headless", False)),
        channel=(str(_pick(raw, "browser", "channel", "")) or None),
        executable_path=(str(_pick(raw, "browser", "executable_path", "")) or None),
        slow_mo_ms=int(_pick(raw, "browser", "slow_mo_ms", 0)),
        default_timeout_ms=int(_pick(raw, "browser", "default_timeout_ms", 30000)),
        navigation_timeout_ms=int(_pick(raw, "browser", "navigation_timeout_ms", 45000)),
        locale=str(_pick(raw, "browser", "locale", "zh-CN")),
        viewport_width=int(_pick(raw, "browser", "viewport_width", 1366)),
        viewport_height=int(_pick(raw, "browser", "viewport_height", 768)),
        extra_args=tuple(_pick(raw, "browser", "extra_args", ())),
    )

    login = LoginConfig(
        probe_timeout_ms=int(_pick(raw, "login", "probe_timeout_ms", 15000)),
        submit_wait_ms=int(_pick(raw, "login", "submit_wait_ms", 30000)),
        # 提交降级链：单条路径的交互超时与生效观测预算。**必须短**——
        # 2026-09-22 采集机实测：单条 click 吃满默认 30s，4 条降级会放大到 120s+。
        # 默认值使「路径数 ×(交互 + 观测)」≈ submit_wait_ms（4×(4000+3000)=28000 ≤ 30000）。
        submit_path_timeout_ms=int(_pick(raw, "login", "submit_path_timeout_ms", 4000)),
        submit_effect_probe_ms=int(_pick(raw, "login", "submit_effect_probe_ms", 3000)),
        export_storage_state=bool(_pick(raw, "login", "export_storage_state", True)),
        storage_state_rel_path=str(
            _pick(raw, "login", "storage_state_rel_path", "runtime/storage-state/{account_hash}.json")
        ),
        auto_login_enabled=bool(_pick(raw, "login", "auto_login_enabled", True)),
        session_ttl_hours=int(_pick(raw, "login", "session_ttl_hours", 12)),
        # 人工介入登录：等待现场人工完成滑块/验证码的时长，以及只读轮询间隔
        manual_wait_minutes=int(_pick(raw, "login", "manual_wait_minutes", 10)),
        manual_poll_interval_s=int(_pick(raw, "login", "manual_poll_interval_s", 5)),
    )
    # 超时必须为正：为 0 会让 Playwright 走「不限时」语义，反而把降级链的短超时初衷废掉
    if login.submit_path_timeout_ms <= 0 or login.submit_effect_probe_ms <= 0:
        raise ConfigError(
            "login.submit_path_timeout_ms / submit_effect_probe_ms 必须为正整数"
            f"（当前 {login.submit_path_timeout_ms} / {login.submit_effect_probe_ms}）"
        )
    # 人工登录的等待窗口与轮询间隔同为正值：0 会让等待立即超时或让轮询空转
    if login.manual_wait_minutes <= 0 or login.manual_poll_interval_s <= 0:
        raise ConfigError(
            "login.manual_wait_minutes / manual_poll_interval_s 必须为正整数"
            f"（当前 {login.manual_wait_minutes} / {login.manual_poll_interval_s}）"
        )

    db = DbConfig(
        host=str(_pick(raw, "db", "host", "127.0.0.1")),
        port=int(_pick(raw, "db", "port", 3306)),
        database=str(_pick(raw, "db", "database", "yizhan_collector")),
        connect_timeout_s=int(_pick(raw, "db", "connect_timeout_s", 5)),
        read_timeout_s=int(_pick(raw, "db", "read_timeout_s", 10)),
        write_timeout_s=int(_pick(raw, "db", "write_timeout_s", 10)),
        charset=str(_pick(raw, "db", "charset", "utf8mb4")),
        retry_attempts=int(_pick(raw, "db", "retry_attempts", 3)),
        retry_backoff_s=float(_pick(raw, "db", "retry_backoff_s", 1.0)),
    )
    # MySQL 只允许本机：与 env-setup.ps1 的 bind-address=127.0.0.1 及项目红线一致
    if db.host not in {"127.0.0.1", "localhost"}:
        raise ConfigError(f"db.host 只允许 127.0.0.1/localhost（当前 {db.host}），禁止连远端数据库")

    paths = PathsConfig(
        runtime_dir=resolve_path(_pick(raw, "paths", "runtime_dir", "runtime")),
        log_dir=resolve_path(_pick(raw, "paths", "log_dir", "runtime/logs")),
        secrets_file=resolve_path(_pick(raw, "paths", "secrets_file", "config/local/local.secrets.json")),
    )

    logging_cfg = LoggingConfig(
        level=str(_pick(raw, "logging", "level", "INFO")).upper(),
        max_bytes=int(_pick(raw, "logging", "max_bytes", 5 * 1024 * 1024)),
        backup_count=int(_pick(raw, "logging", "backup_count", 5)),
    )

    return Settings(
        site=site,
        browser=browser,
        humanize=humanize,
        login=login,
        db=db,
        paths=paths,
        logging=logging_cfg,
        source_file=source_file,
    )


def load_settings(settings_file: Path | str | None = None) -> Settings:
    """加载配置；未显式指定时读 config/settings.toml。

    缺少配置文件时直接报错并给出可执行指引，不使用任何隐含默认配置静默启动。
    """
    path = Path(settings_file) if settings_file else DEFAULT_SETTINGS_FILE
    if not path.exists():
        raise ConfigError(
            f"未找到配置文件：{path}；请先复制 {SETTINGS_EXAMPLE_FILE} 为 {path.name} 并按采集机实际情况修改"
        )
    try:
        raw = tomllib.loads(path.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as exc:
        raise ConfigError(f"配置文件 {path} 解析失败：{exc}") from exc
    except OSError as exc:
        raise ConfigError(f"配置文件 {path} 读取失败：{exc}") from exc
    return _build(raw, path)
