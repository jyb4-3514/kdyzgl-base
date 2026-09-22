# -*- coding: utf-8 -*-
"""DPAPI 封装测试（用 monkeypatch 打桩 pywin32，不触碰真实 DPAPI）。"""
from __future__ import annotations

import base64
import json
from pathlib import Path

import pytest

from collector.security import dpapi


class _FakeWin32Crypt:
    """伪 pywin32：`CryptUnprotectData` 返回 (description, data)，与官方签名一致。"""

    def __init__(self) -> None:
        self._blobs: dict[bytes, bytes] = {}

    def CryptProtectData(self, data, *_args, **_kwargs):  # noqa: N802 - 对齐 pywin32 方法名
        blob = b"enc:" + data
        self._blobs[blob] = data
        return blob

    def CryptUnprotectData(self, data, *_args, **_kwargs):  # noqa: N802 - 对齐 pywin32 方法名
        if data not in self._blobs:
            raise RuntimeError("blob 不可解密")
        return None, self._blobs[data]


@pytest.fixture
def fake_win32crypt(monkeypatch):
    fake = _FakeWin32Crypt()
    monkeypatch.setattr(dpapi, "_win32crypt", lambda: fake)
    return fake


def _write_secrets(path: Path, payload: dict, with_bom: bool = True) -> Path:
    text = json.dumps(payload, ensure_ascii=False)
    data = text.encode("utf-8")
    path.write_bytes((b"\xef\xbb\xbf" + data) if with_bom else data)
    return path


def test_加解密往返(fake_win32crypt):
    cipher = dpapi.protect("p@ssw0rd")
    assert cipher != "p@ssw0rd"
    assert dpapi.unprotect(cipher) == "p@ssw0rd"


def test_密文非Base64时报错清晰():
    with pytest.raises(dpapi.DpapiError) as excinfo:
        dpapi.unprotect("这不是 base64!!!")
    assert "Base64" in str(excinfo.value)


def test_解密失败提示作用域与账户(fake_win32crypt):
    wrong = base64.b64encode(b"unknown-blob").decode("ascii")
    with pytest.raises(dpapi.DpapiError) as excinfo:
        dpapi.unprotect(wrong)
    assert "CurrentUser" in str(excinfo.value)


def test_无pywin32时给出安装指引(monkeypatch):
    def _raise():
        raise dpapi.DpapiUnavailableError("未检测到 pywin32")

    monkeypatch.setattr(dpapi, "_win32crypt", _raise)
    with pytest.raises(dpapi.DpapiUnavailableError):
        dpapi.unprotect(base64.b64encode(b"x").decode("ascii"))


def test_读取带BOM的密钥文件(tmp_path):
    payload = {"schema": "local-secrets/v1", "mysql": {"host": "127.0.0.1"}}
    path = _write_secrets(tmp_path / "local.secrets.json", payload, with_bom=True)
    assert dpapi.load_secrets(path)["schema"] == "local-secrets/v1"


def test_密钥文件不存在时给出初始化指引(tmp_path):
    with pytest.raises(dpapi.SecretsFileError) as excinfo:
        dpapi.load_secrets(tmp_path / "缺失.json")
    assert "env-setup.ps1" in str(excinfo.value)


def test_密钥文件schema不符时报错(tmp_path):
    path = _write_secrets(tmp_path / "s.json", {"schema": "v2"})
    with pytest.raises(dpapi.SecretsFormatError):
        dpapi.load_secrets(path)


def test_密钥文件非法JSON时报错(tmp_path):
    path = tmp_path / "bad.json"
    path.write_text("{不是 json", encoding="utf-8")
    with pytest.raises(dpapi.SecretsFormatError):
        dpapi.load_secrets(path)


def test_缺少pdd段时提示执行初始化脚本():
    with pytest.raises(dpapi.SecretsFormatError) as excinfo:
        dpapi.get_section({"mysql": {"host": "127.0.0.1"}}, "pdd")
    assert "init-local-secrets.ps1" in str(excinfo.value)


def test_解密字段缺失时报错():
    with pytest.raises(dpapi.SecretsFormatError) as excinfo:
        dpapi.decrypt_secret({"pdd": {"site": "https://x"}}, "pdd", "accountEnc")
    assert "pdd.accountEnc" in str(excinfo.value)


def test_解密字段成功(fake_win32crypt):
    secrets = {"pdd": {"accountEnc": dpapi.protect("16612349983")}}
    assert dpapi.decrypt_secret(secrets, "pdd", "accountEnc") == "16612349983"


def test_解密字段失败时错误信息只含字段路径(fake_win32crypt):
    secrets = {"pdd": {"accountEnc": base64.b64encode(b"bad-blob").decode("ascii")}}
    with pytest.raises(dpapi.DpapiError) as excinfo:
        dpapi.decrypt_secret(secrets, "pdd", "accountEnc")
    message = str(excinfo.value)
    assert "pdd.accountEnc" in message
    assert "bad-blob" not in message
