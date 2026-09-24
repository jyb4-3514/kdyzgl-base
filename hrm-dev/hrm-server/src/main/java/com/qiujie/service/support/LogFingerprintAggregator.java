package com.qiujie.service.support;

import com.qiujie.config.AlgoProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * 日志指纹索引（S8 §10.2「Map 指纹索引 + 惰性窗口清扫」的服务端落地）。
 * <p>
 * 职责：在 JVM 内维护 {@code 指纹 → (DB 行 id, 最近事件时间)}，把「同一指纹在去重窗口内重复只累加 count」
 * 的判定从「每次查库」降为 O(1) 命中；未命中时才回库查一次（覆盖跨实例/重启场景），保证多实例下仍不产生重复行。
 * <p>
 * 为什么需要内存索引而非每次查库：报错风暴下同指纹在窗口内高频到达，逐条查库会放大 DB 压力；
 * 索引把热路径压到内存，DB 只承担持久化与冷启动兜底（与 S8 的 O(1) 均摊目标一致）。
 * <p>
 * <b>窗口锚点为「事件时间」而非墙钟</b>：与 S8 离线原型 {@code aggregateWindowed}（{@code row.time - hit.lastTime <= window}）
 * 及 {@code client_log.last_time} 列语义一致；时钟回拨（事件时间倒退）按差值为负 ≤ 窗口处理，仍判为命中，
 * last_time 随之回退（与算法原型逐位一致）。
 * <p>
 * 阈值全部来自 {@code hrm.algo.log.*}（禁止硬编码，规则 §11.4）：
 * {@code dedupeWindowSeconds}=窗口秒数，{@code sweepEvery}=惰性清扫间隔（条），{@code ringBufferCap}=索引条数上限。
 * <p>
 * 并发：方法级 synchronized。多实例下索引本地、窗口判定存在秒级漂移，可能产生少量重复行（去重为「降噪」而非强一致），
 * 单实例部署无此问题。
 * TODO(扩展): 多实例上线前把索引迁至 Redis（指纹 → rowId 带 TTL），见 algo §12.2 TODO-6。
 */
@Component
public class LogFingerprintAggregator {

    private final int windowSeconds;
    private final int sweepEvery;
    private final int capacity;

    /** 指纹 → 索引项 */
    private final Map<String, Entry> index = new HashMap<>();
    /** 自上次清扫以来的操作数（惰性清扫计数） */
    private long ops;

    public LogFingerprintAggregator(AlgoProperties algoProperties) {
        this.windowSeconds = Math.max(1, algoProperties.getLog().getDedupeWindowSeconds());
        this.sweepEvery = Math.max(1, algoProperties.getLog().getSweepEvery());
        this.capacity = Math.max(1, algoProperties.getLog().getRingBufferCap());
    }

    /**
     * 查命中：指纹在窗口内已存在则返回其 DB 行 id，并把最近事件时间推进到 {@code eventTime}；否则返回 null。
     * 命中即代表「本次应累加计数到该行」，不新建记录。
     */
    public synchronized Long findHit(String fingerprint, LocalDateTime eventTime) {
        maybeSweep(eventTime);
        Entry entry = index.get(fingerprint);
        if (entry == null) {
            return null;
        }
        if (ChronoUnit.SECONDS.between(entry.lastTime, eventTime) <= windowSeconds) {
            entry.lastTime = eventTime;
            return entry.rowId;
        }
        // 已超出窗口：不作为命中（同指纹视为新一批次），条目交给后续清扫/覆盖
        return null;
    }

    /** 登记/更新指纹索引（新建行或回库兜底命中后调用） */
    public synchronized void remember(String fingerprint, Long rowId, LocalDateTime eventTime) {
        Entry entry = index.get(fingerprint);
        if (entry != null) {
            entry.rowId = rowId;
            entry.lastTime = eventTime;
            return;
        }
        ensureCapacity(eventTime);
        index.put(fingerprint, new Entry(rowId, eventTime));
    }

    /** 失效单个指纹（回写 DB 发现行已被清理时调用） */
    public synchronized void evict(String fingerprint) {
        index.remove(fingerprint);
    }

    /** 清空索引（清空日志端点调用，避免指向已删除的行） */
    public synchronized void clear() {
        index.clear();
        ops = 0;
    }

    /** 当前索引条数（单测断言用） */
    public synchronized int size() {
        return index.size();
    }

    /** 惰性清扫：每 sweepEvery 次操作清一次过期条目，避免索引随指纹数无界增长 */
    private void maybeSweep(LocalDateTime now) {
        ops++;
        if (ops % sweepEvery != 0) {
            return;
        }
        index.entrySet().removeIf(e -> ChronoUnit.SECONDS.between(e.getValue().lastTime, now) > windowSeconds);
    }

    /** 容量保护：先清扫过期，仍超上限则淘汰最近事件时间最早的一项（环缓冲上限的兜底语义） */
    private void ensureCapacity(LocalDateTime now) {
        if (index.size() < capacity) {
            return;
        }
        index.entrySet().removeIf(e -> ChronoUnit.SECONDS.between(e.getValue().lastTime, now) > windowSeconds);
        if (index.size() < capacity) {
            return;
        }
        String oldestKey = null;
        LocalDateTime oldestTime = null;
        for (Map.Entry<String, Entry> e : index.entrySet()) {
            if (oldestTime == null || e.getValue().lastTime.isBefore(oldestTime)) {
                oldestTime = e.getValue().lastTime;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            index.remove(oldestKey);
        }
    }

    /** 指纹索引项 */
    private static final class Entry {
        private Long rowId;
        private LocalDateTime lastTime;

        private Entry(Long rowId, LocalDateTime lastTime) {
            this.rowId = rowId;
            this.lastTime = lastTime;
        }
    }
}
