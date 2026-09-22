# -*- coding: utf-8 -*-
"""仿人工节奏测试：延时区间边界、随机可复现、鼠标关闭时不动鼠标。"""
from __future__ import annotations

from collector.config.settings import HumanizeConfig, MouseConfig
from collector.login.humanize import Humanizer


def _config(**overrides) -> HumanizeConfig:
    payload = {
        "char_delay_min_ms": 60,
        "char_delay_max_ms": 180,
        "field_pause_min_ms": 300,
        "field_pause_max_ms": 900,
        "before_submit_pause_min_ms": 500,
        "before_submit_pause_max_ms": 1500,
        "random_seed": 0,
        "mouse": MouseConfig(enabled=False, steps_min=5, steps_max=12),
    }
    payload.update(overrides)
    return HumanizeConfig(**payload)


def test_逐字符延时落在配置区间内():
    humanizer = Humanizer(_config())
    samples = [humanizer.char_delay_ms() for _ in range(2000)]
    assert min(samples) >= 60
    assert max(samples) <= 180
    assert len(set(samples)) > 1  # 随机而非定值


def test_字段与提交前停顿落在配置区间内():
    humanizer = Humanizer(_config())
    field_samples = [humanizer.field_pause_ms() for _ in range(500)]
    submit_samples = [humanizer.before_submit_pause_ms() for _ in range(500)]
    assert 300 <= min(field_samples) and max(field_samples) <= 900
    assert 500 <= min(submit_samples) and max(submit_samples) <= 1500


def test_上下限相等时取该定值():
    humanizer = Humanizer(_config(char_delay_min_ms=120, char_delay_max_ms=120))
    assert {humanizer.char_delay_ms() for _ in range(50)} == {120}


def test_固定种子时序列可复现():
    first = Humanizer(_config(random_seed=12345))
    second = Humanizer(_config(random_seed=12345))
    assert [first.char_delay_ms() for _ in range(20)] == [second.char_delay_ms() for _ in range(20)]


class _ExplodingMouse:
    def move(self, *_args, **_kwargs):  # pragma: no cover - 被调用即视为失败
        raise AssertionError("鼠标未启用时不应产生鼠标动作")


class _PageStub:
    def __init__(self) -> None:
        self.mouse = _ExplodingMouse()


def test_鼠标未启用时不产生鼠标动作():
    humanizer = Humanizer(_config())
    # locator 传 None：若实现先访问 locator 说明未按开关提前返回
    humanizer.move_to(_PageStub(), None)


def test_零延时不做休眠():
    humanizer = Humanizer(_config())
    humanizer.sleep_ms(0)  # 不应抛错、也不应实际等待
