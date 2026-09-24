package com.qiujie.service.sync.support;

import java.util.regex.Pattern;

/**
 * 采集时刻工具（纯函数，HH:mm 口径）。
 * <p>
 * 与既有采集校验口径一致：普通时刻 {@code 00:00-23:59}，时段结束允许 {@code 24:00}（仅结束位）。
 * 单独抽类是因为「时段模板/时间窗/时间范围」三处校验共用，避免三份正则各写一遍。
 */
public final class SyncClock {

    private static final Pattern CLOCK = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private SyncClock() {
    }

    /** 普通时刻校验 HH:mm；
     * @param value 待校验文本
     * @return 是否合法 */
    public static boolean isClock(String value) {
        return value != null && CLOCK.matcher(value).matches();
    }

    /** 结束时刻校验：允许 24:00（仅结束位） */
    public static boolean isEndClock(String value) {
        return isClock(value) || "24:00".equals(value);
    }

    /** HH:mm → 当日分钟数（仅对合法时刻有意义） */
    public static int minutesOfClock(String text) {
        String[] parts = String.valueOf(text).split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
