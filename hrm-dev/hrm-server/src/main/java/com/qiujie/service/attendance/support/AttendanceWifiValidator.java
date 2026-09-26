package com.qiujie.service.attendance.support;

import com.qiujie.entity.WifiEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 打卡规则 WiFi 白名单的校验与归一（纯逻辑，零 Spring 依赖）。
 * <p>
 * 为什么单独抽出：原校验只有「数组非空 + ssid 非空」，与前端已先行的「32 字符 / MAC 格式 / 重复 / 条数」约束
 * 不对称，PC 端与直调 API 可绕过。此处收口为单一真源，保存链路与单测共用同一判据，防止前后端口径漂移。
 * <p>
 * 口径依据：① SSID 长度 1-32 个字符（IEEE 802.11 的客观上限为 32 octets；本实现按字符数校验，与前端
 * {@code maxlength=32} 对齐，多字节字符不做字节折算）；② BSSID 可空，非空须为
 * {@code AA:BB:CC:DD:EE:FF}（十六进制、大小写不敏感）；③ 去重按<b>区分大小写</b>精确比对，与打卡判定
 * {@code w.ssid === wifiSsid} 口径一致（不得改为忽略大小写，否则会出现"保存放行、打卡判不中"）；
 * ④ 白名单每站至多 1 条（用户裁定「每个站点指定一个」）。
 * <p>
 * 错误码由调用方统一使用 {@code ErrorCode.BAD_REQUEST}，本类只返回首个错误文案（无错返回 {@code null}）。
 */
public final class AttendanceWifiValidator {

    /** SSID 长度上限：32 个字符（对应 IEEE 802.11 的 32 octets；实现按字符数校验，与前端 maxlength=32 对齐） */
    public static final int SSID_MAX_LEN = 32;
    /** 白名单条数上限：用户口径「每个站点指定一个」 */
    public static final int MAX_ENTRIES = 1;
    /** MAC 判据：固定 6 段十六进制、冒号分隔、大小写不敏感 */
    private static final Pattern BSSID_PATTERN = Pattern.compile("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$");

    private AttendanceWifiValidator() {
    }

    /**
     * 校验白名单：返回首个错误文案，合法返回 {@code null}。
     * <p>
     * {@code null} 表示「本次未提交该字段」→ 沿用现值、不校验（与 {@code checkPeriods} 的缺省语义一致）。
     * 校验顺序：逐条字段（ssid/bssid）→ 去重 → 条数。先去重后判条数，是为了让「区分大小写」可被观测：
     * 完全相同的两条报「重复」，仅大小写不同的两条不报重复、落到「条数上限」。
     */
    public static String validate(List<WifiEntry> wifiList) {
        if (wifiList == null) {
            return null;
        }
        Set<String> seen = new HashSet<>();
        for (WifiEntry entry : wifiList) {
            if (entry == null) {
                return "WiFi 白名单条目不可为空";
            }
            String ssid = entry.getSsid() == null ? null : entry.getSsid().trim();
            if (AttendanceSupport.isBlank(ssid)) {
                return "WiFi 名称不可为空";
            }
            // trim 后按字符数判长：与前端 maxlength=32 对齐；IEEE 802.11 客观上限为 32 octets，此处按字符数近似，多字节不做字节折算
            if (ssid.length() > SSID_MAX_LEN) {
                return "WiFi 名称须为 1-32 个字符";
            }
            String bssid = entry.getBssid() == null ? null : entry.getBssid().trim();
            if (!AttendanceSupport.isBlank(bssid) && !BSSID_PATTERN.matcher(bssid).matches()) {
                return "BSSID 须为 AA:BB:CC:DD:EE:FF 格式";
            }
            // 区分大小写的精确去重：seen 直接存原始 ssid，不做 toLowerCase
            if (!seen.add(ssid)) {
                return "WiFi 名称重复：" + ssid;
            }
        }
        if (wifiList.size() > MAX_ENTRIES) {
            return "WiFi 白名单同一驿站仅允许配置 1 条";
        }
        return null;
    }

    /**
     * 归一写入值：{@code ssid} 保存前 trim；{@code bssid} 空串/纯空白统一归一为 {@code null}（可空语义）。
     * 返回新列表与新对象，不修改入参（入参可能被上层复用）。
     */
    public static List<WifiEntry> normalize(List<WifiEntry> wifiList) {
        List<WifiEntry> result = new ArrayList<>(wifiList.size());
        for (WifiEntry w : wifiList) {
            WifiEntry entry = new WifiEntry();
            entry.setSsid(w.getSsid() == null ? null : w.getSsid().trim());
            String bssid = w.getBssid() == null ? null : w.getBssid().trim();
            entry.setBssid(AttendanceSupport.isBlank(bssid) ? null : bssid);
            result.add(entry);
        }
        return result;
    }
}
