# -*- coding: utf-8 -*-
"""「仿人工」的唯一合法实现范围：**人类节奏与停顿**。

红线 C3（ADR §3.3 / §20.9，整条保留、不得借机放松）：
本模块**只**控制输入与点击的时间节奏，禁止且不提供以下任何能力——
隐藏 ``navigator.webdriver``、伪造 UA / 指纹、注入对抗脚本、代理轮换、验证码识别。
「像人」的收益上限是减少无意义的高频操作，绝不是「骗过风控」。

所有节拍参数均来自配置（ADR §11.4「参数外置可配」），不得在代码里硬编码经验值。
"""
from __future__ import annotations

import random
import time
from typing import Any

from collector.config.settings import HumanizeConfig


class Humanizer:
    """按配置生成随机节拍，并提供逐字符输入 / 停顿 / 自然移动等动作封装。"""

    def __init__(self, config: HumanizeConfig, rng: random.Random | None = None) -> None:
        self._config = config
        # random_seed = 0 表示真随机（系统熵播种）；非 0 用于测试复现
        self._rng = rng if rng is not None else random.Random(config.random_seed or None)

    # ---------------- 节拍取值 ----------------
    def _random_in(self, low: int, high: int) -> int:
        if low >= high:
            return low
        return self._rng.randint(low, high)

    def char_delay_ms(self) -> int:
        """单个字符的输入间隔（毫秒）。"""
        return self._random_in(self._config.char_delay_min_ms, self._config.char_delay_max_ms)

    def field_pause_ms(self) -> int:
        """字段之间的停顿（毫秒）。"""
        return self._random_in(self._config.field_pause_min_ms, self._config.field_pause_max_ms)

    def before_submit_pause_ms(self) -> int:
        """提交前的停顿（毫秒）。"""
        return self._random_in(
            self._config.before_submit_pause_min_ms, self._config.before_submit_pause_max_ms
        )

    def sleep_ms(self, milliseconds: int) -> None:
        if milliseconds > 0:
            time.sleep(milliseconds / 1000.0)

    # ---------------- 动作封装 ----------------
    def pause_field(self) -> None:
        self.sleep_ms(self.field_pause_ms())

    def pause_before_submit(self) -> None:
        self.sleep_ms(self.before_submit_pause_ms())

    def type_like_human(self, locator: Any, text: str) -> None:
        """逐字符输入。

        Playwright 官方的 ``locator.fill()`` 是瞬时赋值、无按键节奏；``locator.type()`` 已废弃，
        官方推荐逐键输入用 ``locator.press_sequentially()``（本方法对每个字符单独调用以取得逐字符随机间隔）。
        """
        try:
            locator.click()
        except Exception:
            # 部分输入框不可点（如被遮罩），退化为聚焦；仍不改变节奏语义
            locator.focus()
        for char in text:
            locator.press_sequentially(char, delay=self.char_delay_ms())

    def move_to(self, page: Any, locator: Any) -> None:
        """移动到元素中心（可选）。

        TODO(扩展): 当前仅分段直线移动，未做轨迹拟真（如贝塞尔曲线 + 加减速）；
                    如非必要不实现——避免被误用为「反检测」手段（红线 C3）。
        """
        if not self._config.mouse.enabled:
            return
        box = locator.bounding_box()
        if not box:
            return
        center_x = box["x"] + box["width"] / 2
        center_y = box["y"] + box["height"] / 2
        steps = self._random_in(self._config.mouse.steps_min, self._config.mouse.steps_max)
        page.mouse.move(center_x, center_y, steps=steps)
