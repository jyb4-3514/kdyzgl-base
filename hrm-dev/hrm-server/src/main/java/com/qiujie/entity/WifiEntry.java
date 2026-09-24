package com.qiujie.entity;

import lombok.Data;

/**
 * WiFi 白名单条目（{@code attendance_rule.wifi_list} JSON 内嵌结构，非独立表）。
 * <p>
 * 字段名与 JSON 键逐字一致（{@code ssid/bssid}），由 Jackson 直接映射；{@code bssid} 可为 null。
 */
@Data
public class WifiEntry {

    /** WiFi 名称（判定命中依据） */
    private String ssid;

    /** WiFi 物理地址（可为空，仅作展示/辅助） */
    private String bssid;
}
