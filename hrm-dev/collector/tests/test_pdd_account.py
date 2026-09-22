# -*- coding: utf-8 -*-
"""多多采集账号密码配置模块测试（脱敏、去重键、校验、加载分支）。"""
from __future__ import annotations

import re
from pathlib import Path

import pytest

import collector.config.pdd_account as mod
from collector.config.pdd_account import PddAccount, PddAccountError
from collector.security.dpapi import SecretsFormatError

_GOOD_SITE = "https://mcmd.pinduoduo.com/home"


def _account(**overrides) -> PddAccount:
    payload = {
        "site": _GOOD_SITE,
        "account": "16612349983",
        "password": "P@ssw0rd-明文-不应外泄",
        "authorized_by": "张三",
        "authorized_at": "2026-09-22T22:41:24",
    }
    payload.update(overrides)
    return PddAccount(**payload)


# ---------------- 脱敏 ----------------
def test_手机号账号脱敏保留前3后4():
    assert _account().masked_account() == "166****9983"


def test_短账号脱敏不泄露原文():
    assert _account(account="ab").masked_account() == "**"
    assert _account(account="abcde").masked_account() == "a***e"


def test_repr与str均不包含密码明文():
    account = _account()
    for text in (repr(account), str(account), account.masked_description()):
        assert "P@ssw0rd" not in text
        assert "明文" not in text
    assert "password='***'" in repr(account)


def test_masked_description只含脱敏账号():
    description = _account().masked_description()
    assert "166****9983" in description
    assert "16612349983" not in description


# ---------------- 去重键 ----------------
def test_账号哈希稳定且为64位十六进制():
    first = _account().account_hash()
    second = _account().account_hash()
    assert first == second
    assert re.fullmatch(r"[0-9a-f]{64}", first)


def test_不同账号哈希不同():
    assert _account(account="16612349983").account_hash() != _account(account="16600000000").account_hash()


# ---------------- 校验 ----------------
# 注意：密码不做 strip（首尾空格可能是密码本身的一部分），故空密码用 "" 而非空格验证
@pytest.mark.parametrize("overrides", [{"account": "   "}, {"password": ""}])
def test_账号或密码为空时抛错(overrides):
    with pytest.raises(PddAccountError):
        _account(**overrides)


@pytest.mark.parametrize("site", ["", "mcmd.pinduoduo.com/home", "http://mcmd.pinduoduo.com/home"])
def test_站点URL非法时抛错(site):
    with pytest.raises(PddAccountError):
        _account(site=site)


def test_未登记授权时has_authorization为假():
    assert _account(authorized_by=None).has_authorization() is False
    assert _account().has_authorization() is True


# ---------------- 加载 ----------------
def _patch_secrets(monkeypatch, secrets: dict, account: str, password: str) -> None:
    monkeypatch.setattr(mod.dpapi, "load_secrets", lambda _path: secrets)
    monkeypatch.setattr(mod.dpapi, "get_section", lambda data, section: data[section])

    def _decrypt(_data, _section, key: str) -> str:
        return {"accountEnc": account, "passwordEnc": password}[key]

    monkeypatch.setattr(mod.dpapi, "decrypt_secret", _decrypt)


def test_正常加载pdd段(monkeypatch, tmp_path: Path):
    secrets = {
        "schema": "local-secrets/v1",
        "pdd": {
            "site": _GOOD_SITE,
            "authorizedBy": "张三",
            "authorizedAt": "2026-09-22T22:41:24",
        },
    }
    _patch_secrets(monkeypatch, secrets, "16612349983", "P@ssw0rd")
    account = mod.load_pdd_account(tmp_path / "local.secrets.json")
    assert account.account == "16612349983"
    assert account.authorized_by == "张三"
    assert account.has_authorization() is True


def test_缺少pdd段时给出可执行指引(monkeypatch, tmp_path: Path):
    secrets = {"schema": "local-secrets/v1"}

    def _raise(_data, section):
        raise SecretsFormatError(f"本地密钥文件缺少 {section!r} 段")

    monkeypatch.setattr(mod.dpapi, "load_secrets", lambda _path: secrets)
    monkeypatch.setattr(mod.dpapi, "get_section", _raise)
    with pytest.raises(PddAccountError) as excinfo:
        mod.load_pdd_account(tmp_path / "local.secrets.json")
    assert "init-local-secrets.ps1" in str(excinfo.value)


def test_解密失败时抛PddAccountError(monkeypatch, tmp_path: Path):
    secrets = {"schema": "local-secrets/v1", "pdd": {"site": _GOOD_SITE}}
    monkeypatch.setattr(mod.dpapi, "load_secrets", lambda _path: secrets)
    monkeypatch.setattr(mod.dpapi, "get_section", lambda data, section: data[section])

    def _raise(_data, _section, _key):
        raise mod.dpapi.DpapiError("字段 pdd.accountEnc 解密失败")

    monkeypatch.setattr(mod.dpapi, "decrypt_secret", _raise)
    with pytest.raises(PddAccountError):
        mod.load_pdd_account(tmp_path / "local.secrets.json")
