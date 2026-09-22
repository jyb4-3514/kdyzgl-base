# -*- coding: utf-8 -*-
"""Windows DPAPI（CurrentUser 作用域）加解密与本地密钥文件读取。

与 scripts/env-setup.ps1 / scripts/init-local-secrets.ps1 的**两端一致契约**：
- PowerShell 写入：``[ProtectedData]::Protect(UTF8字节, $null, CurrentUser)`` → Base64
- Python 读取：``win32crypt.CryptUnprotectData(base64解码, None, None, None, 0)`` → ``(desc, 明文bytes)``

作用域为 CurrentUser（ADR §20.3.1）：只有加密时的同一用户、同一台机器才能解密。

明文零落地（ADR §20.3.3）：本模块只在内存中持有明文，不写入任何文件，不写日志。
"""
from __future__ import annotations

import base64
import binascii
import json
from pathlib import Path
from typing import Any

# pywin32 官方签名（https://mhammond.github.io/pywin32/win32crypt__CryptUnprotectData_meth.html）：
#   CryptUnprotectData(DataIn, OptionalEntropy=None, Reserved=None, PromptStruct=None, Flags=0)
#   CryptProtectData(DataIn, DataDescr=None, OptionalEntropy=None, Reserved=None, PromptStruct=None, Flags=0)
# 注意：Flags 是 int，任务书示例传的 5 个 None 会在 Flags 位置触发 TypeError，故显式传 0（当前用户作用域、非交互式）。
_FLAGS_CURRENT_USER = 0


class DpapiError(Exception):
    """DPAPI 相关错误基类。"""


class DpapiUnavailableError(DpapiError):
    """pywin32 不可用（非 Windows 或未安装依赖）。"""


class SecretsFileError(DpapiError):
    """本地密钥文件不存在或不可读。"""


class SecretsFormatError(DpapiError):
    """本地密钥文件结构不符合 local-secrets/v1 契约。"""


def _win32crypt() -> Any:
    """延迟导入 pywin32，便于在无 pywin32 的环境做纯逻辑单测。"""
    try:
        import win32crypt  # type: ignore[import-not-found]
    except ImportError as exc:  # pragma: no cover - 取决于运行环境
        raise DpapiUnavailableError(
            "未检测到 pywin32，无法解密本地凭据；请在采集机执行：pip install -r requirements.txt"
        ) from exc
    return win32crypt


def protect(plaintext: str) -> str:
    """加密为 Base64 密文。仅用于测试闭环与本地校验；生产写入由 PowerShell 脚本负责。"""
    data = plaintext.encode("utf-8")
    encrypted = _win32crypt().CryptProtectData(data, None, None, None, None, _FLAGS_CURRENT_USER)
    return base64.b64encode(encrypted).decode("ascii")


def unprotect(ciphertext_b64: str) -> str:
    """解密 Base64 密文为明文。

    失败时抛出带上下文的 DpapiError，绝不静默返回空值或默认值。
    """
    try:
        raw = base64.b64decode(ciphertext_b64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise DpapiError("密文不是合法 Base64，请确认密钥文件未被手工修改") from exc
    try:
        # 返回值为 (description, data)；description 是加密时的描述，本方案不使用
        _description, plaintext = _win32crypt().CryptUnprotectData(
            raw, None, None, None, _FLAGS_CURRENT_USER
        )
    except DpapiUnavailableError:
        raise
    except Exception as exc:  # pywintypes.error 等
        raise DpapiError(
            "DPAPI 解密失败：密文与当前 Windows 用户/机器不匹配（作用域为 CurrentUser），"
            "或密钥文件被复制自其它账户；请在生成密文的同一账户下执行"
        ) from exc
    if isinstance(plaintext, bytes):
        try:
            return plaintext.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise DpapiError("DPAPI 解密结果不是 UTF-8 文本，密钥文件可能已损坏") from exc
    return str(plaintext)


def load_secrets(secrets_file: Path | str) -> dict:
    """读取本地密钥文件（JSON）。文件由 PowerShell 写入，可能带 UTF-8 BOM，故用 utf-8-sig。"""
    path = Path(secrets_file)
    if not path.exists():
        raise SecretsFileError(
            f"未找到本地密钥文件：{path}；请先在采集机执行 scripts/env-setup.ps1 初始化环境与 MySQL 凭据"
        )
    try:
        text = path.read_text(encoding="utf-8-sig")
    except OSError as exc:
        raise SecretsFileError(f"本地密钥文件读取失败：{path}（{exc}）") from exc
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        raise SecretsFormatError(f"本地密钥文件不是合法 JSON：{path}（{exc}）") from exc
    if not isinstance(data, dict):
        raise SecretsFormatError(f"本地密钥文件根节点必须是对象：{path}")
    schema = data.get("schema")
    if schema != "local-secrets/v1":
        raise SecretsFormatError(f"本地密钥文件 schema 不符（期望 local-secrets/v1，实际 {schema!r}）：{path}")
    return data


def get_section(secrets: dict, section: str) -> dict:
    """取出指定段，缺失时给出可执行指引。"""
    value = secrets.get(section)
    if not isinstance(value, dict) or not value:
        raise SecretsFormatError(
            f"本地密钥文件缺少 {section!r} 段；请在采集机执行 "
            f"scripts/init-local-secrets.ps1 写入该段（详见 README「首次凭据初始化」）"
        )
    return value


def decrypt_secret(secrets: dict, section: str, key: str) -> str:
    """解密 ``secrets[section][key]``。报错信息只含字段路径，不含任何密文或明文片段。"""
    section_data = get_section(secrets, section)
    field = f"{section}.{key}"
    ciphertext = section_data.get(key)
    if not isinstance(ciphertext, str) or not ciphertext.strip():
        raise SecretsFormatError(f"本地密钥文件缺少字段 {field}；请重新执行对应的初始化脚本写入")
    try:
        return unprotect(ciphertext)
    except DpapiError as exc:
        raise DpapiError(f"字段 {field} 解密失败：{exc}") from exc
