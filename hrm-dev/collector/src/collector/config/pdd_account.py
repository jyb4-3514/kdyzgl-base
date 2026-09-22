# -*- coding: utf-8 -*-
"""多多采集账号密码配置模块（二期采集端「多多账号密码配置」的唯一落点）。

职责：
1. 从本地密钥文件 ``config/local/local.secrets.json`` 的 ``pdd`` 段读取并解密多多账号密码；
2. 暴露不可变值对象 :class:`PddAccount`（site / account / password / authorized_by / authorized_at）；
3. 提供脱敏展示（``masked_account``）与去重键（``account_hash``）；
4. 校验账号、密码、站点 URL；缺失或解密失败即抛出**可执行指引**的异常。

安全约束（红线 C5 / ADR §20.3.3）：
- 任何日志、异常、``__repr__``、``__str__`` 只允许出现脱敏账号，密码一律显示为 ``***``；
- 绝不静默使用任何默认账号密码：缺少 ``pdd`` 段或解密失败时直接报错。
"""
from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlparse

from collector.config.settings import DEFAULT_SECRETS_FILE
from collector.security import dpapi

# 脱敏保留位：账号长度足够时保留前 3 位 + 后 4 位（手机号形如 166****9983）
_MASK_KEEP_HEAD = 3
_MASK_KEEP_TAIL = 4

# 本地密钥文件中 pdd 段的字段名（契约固定，不得改名）
_FIELD_SITE = "site"
_FIELD_ACCOUNT_ENC = "accountEnc"
_FIELD_PASSWORD_ENC = "passwordEnc"
_FIELD_AUTHORIZED_BY = "authorizedBy"
_FIELD_AUTHORIZED_AT = "authorizedAt"


class PddAccountError(Exception):
    """多多账号密码配置缺失、非法或不可解密。"""


@dataclass(frozen=True)
class PddAccount:
    """多多采集账号值对象。

    `password` 字段标记 ``repr=False``，即使走自动 repr 也不会暴露明文。
    """

    site: str
    account: str
    password: str = field(repr=False)
    authorized_by: str | None = None
    authorized_at: str | None = None

    def __post_init__(self) -> None:
        site = (self.site or "").strip()
        account = (self.account or "").strip()
        # 密码不做 strip：首尾空格可能是密码本身的一部分
        password = self.password or ""
        if not site:
            raise PddAccountError("多多站点 URL 为空；请检查密钥文件 pdd.site 或重新执行 init-local-secrets.ps1")
        if not site.startswith("https://") or not urlparse(site).netloc:
            raise PddAccountError(f"多多站点 URL 非法（必须形如 https://host/...）：{site!r}")
        if not account:
            raise PddAccountError("多多账号为空；请重新执行 scripts/init-local-secrets.ps1 写入账号")
        if not password:
            raise PddAccountError("多多密码为空；请重新执行 scripts/init-local-secrets.ps1 写入密码")

    def masked_account(self) -> str:
        """脱敏账号，供日志与数据库展示列使用。"""
        text = self.account
        if len(text) <= 2:
            return "*" * len(text)
        if len(text) >= _MASK_KEEP_HEAD + _MASK_KEEP_TAIL:
            return f"{text[:_MASK_KEEP_HEAD]}{'*' * _MASK_KEEP_TAIL}{text[-_MASK_KEEP_TAIL:]}"
        return f"{text[0]}{'*' * (len(text) - 2)}{text[-1]}"

    def account_hash(self) -> str:
        """账号 SHA-256 十六进制串，仅作数据库去重键。

        说明：账号为低熵标识，此哈希**不是**保密手段，也不用于存放凭据；
        明文凭据仍只存在于 DPAPI 密文与内存中。
        """
        return hashlib.sha256(self.account.encode("utf-8")).hexdigest()

    def has_authorization(self) -> bool:
        """是否已登记站长书面授权（ADR §20.2.9 生效条件①）。"""
        return bool(self.authorized_by and self.authorized_by.strip())

    def masked_description(self) -> str:
        """供日志使用的一行摘要，绝不含密码。"""
        authorization = self.authorized_by or "未登记"
        return f"site={self.site} account={self.masked_account()} authorizedBy={authorization}"

    def __repr__(self) -> str:
        return (
            f"PddAccount(site={self.site!r}, account={self.masked_account()!r}, "
            f"password='***', authorized_by={self.authorized_by!r}, authorized_at={self.authorized_at!r})"
        )

    __str__ = __repr__


def load_pdd_account(secrets_file: Path | str | None = None) -> PddAccount:
    """加载并校验多多账号密码。

    参数
    ----
    secrets_file: 本地密钥文件路径；默认 ``config/local/local.secrets.json``。

    异常
    ----
    PddAccountError: 文件缺失、缺少 pdd 段、字段缺失或解密失败（消息内附可执行指引）。
    """
    path = Path(secrets_file) if secrets_file else DEFAULT_SECRETS_FILE
    try:
        secrets = dpapi.load_secrets(path)
        section = dpapi.get_section(secrets, "pdd")
        site = str(section.get(_FIELD_SITE) or "").strip()
        account = dpapi.decrypt_secret(secrets, "pdd", _FIELD_ACCOUNT_ENC)
        password = dpapi.decrypt_secret(secrets, "pdd", _FIELD_PASSWORD_ENC)
    except dpapi.DpapiError as exc:
        # 已含可执行指引的底层消息不再重复追加，避免提示冗长
        message = str(exc)
        if "init-local-secrets.ps1" not in message:
            message = (
                f"{message}。请先在采集机执行 scripts/init-local-secrets.ps1 写入 pdd 段"
                "（见 README「首次凭据初始化」）"
            )
        raise PddAccountError(f"多多账号密码不可用：{message}") from exc

    try:
        return PddAccount(
            site=site,
            account=account,
            password=password,
            authorized_by=(str(section.get(_FIELD_AUTHORIZED_BY)).strip() if section.get(_FIELD_AUTHORIZED_BY) else None),
            authorized_at=(str(section.get(_FIELD_AUTHORIZED_AT)).strip() if section.get(_FIELD_AUTHORIZED_AT) else None),
        )
    except PddAccountError as exc:
        raise PddAccountError(f"多多账号密码校验失败：{exc}") from exc
