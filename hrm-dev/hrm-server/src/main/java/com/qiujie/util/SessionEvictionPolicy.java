package com.qiujie.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 多端会话淘汰策略（纯逻辑，无 Redis 依赖，可离线单测）。
 * <p>
 * 落实两条安全要求（架构 ADR-MC-03 / security-auth-review §4.5）：
 * <ol>
 *   <li><b>互踢粒度降为「端 + 设备」</b>：同员工同 {@code clientType} 同 {@code deviceId} 重登 → 覆盖旧会话；
 *       跨端 / 跨设备仍并存（三端共用同一后端不再互相顶下线）；</li>
 *   <li><b>单员工最大并发会话数上限</b>：新增会话后总数超过上限时，<b>淘汰最旧</b>（按 {@code loginTime} 升序，
 *       同刻按 sid 升序保证确定性），防止会话数量膨胀与单 token 泄露的横向影响面扩大。</li>
 * </ol>
 * 本类只做「应删除哪些 sid」的<b>决策</b>，不触碰 Redis；由 {@link SessionUtil} 执行删除与写入。
 */
public final class SessionEvictionPolicy {

    /**
     * 现存会话摘要（只取决策所需字段，避免策略类依赖 {@code SessionInfo} 造成循环引用）。
     *
     * @param sid        会话标识
     * @param clientType 端类型（可空，空视为未知端）
     * @param deviceId   设备标识（可空，空视为未知设备）
     * @param loginTime  登录时间（{@code yyyy-MM-dd HH:mm:ss}，可空；空值按最旧处理）
     */
    public record Existing(String sid, String clientType, String deviceId, String loginTime) {
    }

    private SessionEvictionPolicy() {
    }

    /**
     * 计算为写入 incoming 会话而必须先淘汰的 sid 列表。
     *
     * @param existing    该员工现存会话摘要（可为 null/空）
     * @param incoming    待写入的新会话摘要
     * @param maxSessions 单员工最大并发会话数；{@code <= 0} 视为不限（避免误配置把员工全部会话清空）
     * @return 待删除 sid（去重、稳定顺序）；不包含 incoming 自身
     */
    public static List<String> selectEvictions(List<Existing> existing, Existing incoming, int maxSessions) {
        if (incoming == null || incoming.sid() == null) {
            return List.of();
        }
        LinkedHashSet<String> evict = new LinkedHashSet<>();
        List<Existing> others = new ArrayList<>();

        for (Existing item : existing == null ? List.<Existing>of() : existing) {
            if (item == null || item.sid() == null || item.sid().equals(incoming.sid())) {
                continue; // 自身不参与淘汰；脏数据跳过
            }
            if (sameClientAndDevice(item, incoming)) {
                evict.add(item.sid()); // 规则① 同端同设备重登 → 覆盖旧会话
                continue;
            }
            others.add(item);
        }

        // 规则② 并发上限：写入 incoming 后总数不得超过 maxSessions，超出的从最旧开始淘汰
        if (maxSessions > 0) {
            int overflow = (others.size() + 1) - maxSessions;
            if (overflow > 0) {
                List<Existing> sorted = new ArrayList<>(others);
                sorted.sort(Comparator
                        .comparing((Existing e) -> nullToEmpty(e.loginTime()))
                        .thenComparing(e -> nullToEmpty(e.sid())));
                for (int i = 0; i < overflow && i < sorted.size(); i++) {
                    evict.add(sorted.get(i).sid());
                }
            }
        }
        return new ArrayList<>(evict);
    }

    /** 同端同设备判定：clientType 忽略大小写与前后空白，deviceId 精确比对；null 归一为空串（未知端/未知设备） */
    private static boolean sameClientAndDevice(Existing a, Existing b) {
        return normalize(a.clientType()).equals(normalize(b.clientType()))
                && normalize(a.deviceId()).equals(normalize(b.deviceId()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
