# -*- coding: utf-8 -*-
"""配置加载测试（纯逻辑，不连库、不开浏览器）。"""
from __future__ import annotations

from pathlib import Path

import pytest

from collector.config.settings import (
    COLLECTOR_ROOT,
    SETTINGS_EXAMPLE_FILE,
    ConfigError,
    load_settings,
    resolve_path,
)


def _write_toml(tmp_path: Path, content: str) -> Path:
    path = tmp_path / "settings.toml"
    path.write_text(content, encoding="utf-8")
    return path


def test_示例配置可正常加载且取值符合预期():
    settings = load_settings(SETTINGS_EXAMPLE_FILE)
    assert settings.site.login_url.startswith("https://")
    assert "mdkd.pinduoduo.com" in settings.site.allowed_hosts
    assert settings.db.host == "127.0.0.1"
    assert settings.humanize.char_delay_min_ms < settings.humanize.char_delay_max_ms
    assert settings.login.auto_login_enabled is True


def test_缺少配置文件时报错并给出可执行指引():
    with pytest.raises(ConfigError) as excinfo:
        load_settings(Path("Z:/不存在的目录/settings.toml"))
    assert "settings.example.toml" in str(excinfo.value)


def test_环境变量可覆盖配置项(monkeypatch):
    monkeypatch.setenv("YIZHAN__SITE__LOGIN_URL", "https://mdkd.pinduoduo.com/")
    monkeypatch.setenv("YIZHAN__BROWSER__HEADLESS", "true")
    monkeypatch.setenv("YIZHAN__SITE__ALLOWED_HOSTS", "a.example.com, b.example.com")
    settings = load_settings(SETTINGS_EXAMPLE_FILE)
    assert settings.site.login_url == "https://mdkd.pinduoduo.com/"
    assert settings.browser.headless is True
    assert settings.site.allowed_hosts == ("a.example.com", "b.example.com")


def test_环境变量取值非法时报错(monkeypatch):
    monkeypatch.setenv("YIZHAN__BROWSER__HEADLESS", "也许")
    # 布尔解析对未知取值按 False，不抛错；这里改测整型字段
    monkeypatch.setenv("YIZHAN__LOGIN__SESSION_TTL_HOURS", "十二")
    with pytest.raises(ConfigError) as excinfo:
        load_settings(SETTINGS_EXAMPLE_FILE)
    assert "SESSION_TTL_HOURS" in str(excinfo.value)


def test_禁止连接非本机数据库(tmp_path):
    path = _write_toml(tmp_path, '[db]\nhost = "10.0.0.1"\n')
    with pytest.raises(ConfigError) as excinfo:
        load_settings(path)
    assert "127.0.0.1" in str(excinfo.value)


def test_仿人工区间非法时报错(tmp_path):
    path = _write_toml(tmp_path, "[humanize]\nchar_delay_min_ms = 300\nchar_delay_max_ms = 100\n")
    with pytest.raises(ConfigError) as excinfo:
        load_settings(path)
    assert "char_delay" in str(excinfo.value)


def test_站点URL必须为https(tmp_path):
    path = _write_toml(tmp_path, '[site]\nlogin_url = "http://mcmd.pinduoduo.com/home"\n')
    with pytest.raises(ConfigError):
        load_settings(path)


def test_storage_state路径按账号哈希展开():
    settings = load_settings(SETTINGS_EXAMPLE_FILE)
    path = settings.storage_state_path("deadbeef")
    assert path.name == "deadbeef.json"
    assert path.is_absolute()
    assert "runtime" in path.parts


def test_相对路径按采集端根目录解析():
    assert resolve_path("runtime/logs") == (COLLECTOR_ROOT / "runtime" / "logs").resolve()
    absolute = (COLLECTOR_ROOT / "config").resolve()
    assert resolve_path(absolute) == absolute
