package com.qiujie.util;

import com.qiujie.common.SessionInfo;
import com.qiujie.config.AuthProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis 登录会话封装（决策 D3；多端会话改造见 ADR-MC-03）。
 * <p>
 * <b>键规范</b>：
 * <ul>
 *   <li>会话主体 {@code hrm:session:{sid}}（String(JSON) = {@link SessionInfo}，TTL = {@code hrm.auth.session-ttl-seconds}）；</li>
 *   <li>员工索引 {@code hrm:session:idx:{employeeId}}（Set&lt;sid&gt;，TTL 每次写入刷新），用于「强制下线全部会话」；</li>
 *   <li>旧键 {@code hrm:session:{employeeId}}（兼容期只读；新登不再写；见 {@link #save(Long, SessionInfo)} 旧方法）。</li>
 * </ul>
 * <b>互踢粒度</b>：由「员工」降为「端 + 设备」——同员工同端同设备重登覆盖旧会话，跨端/跨设备并存，
 * 并受限「单员工最大并发会话数」（超限淘汰最旧）。决策逻辑抽离至 {@link SessionEvictionPolicy}（纯逻辑可单测）。
 * <p>
 * <b>安全</b>：sid 为 256 bit 高熵串（{@link SessionIdGenerator}），会话键<b>不可枚举</b>；
 * 写/读路径均不拼接用户可控内容到 key（sid/employeeId 均经校验或类型约束）。
 */
@Component
public class SessionUtil {

    /** 会话键前缀（旧键与 sid 主体共用；sid 为十六进制串，employeeId 为十进制串，二者不冲突） */
    public static final String KEY_PREFIX = "hrm:session:";
    /** 会话主体键前缀：hrm:session:{sid} */
    public static final String SID_KEY_PREFIX = "hrm:session:";
    /** 员工会话索引键前缀：hrm:session:idx:{employeeId} */
    public static final String IDX_KEY_PREFIX = "hrm:session:idx:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final long ttlSeconds;
    private final int maxSessionsPerEmployee;

    public SessionUtil(RedisTemplate<String, Object> redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        // 会话 TTL 取 hrm.auth.session-ttl-seconds（默认 3 天）；与 JWT exp 双控、取较短者生效
        this.ttlSeconds = authProperties.getSessionTtlSeconds();
        this.maxSessionsPerEmployee = authProperties.getMaxSessionsPerEmployee();
    }

    // ==================== 多端会话（新，sid 主键） ====================

    /**
     * 建立多端会话：同员工同端同设备覆盖旧会话；超并发上限淘汰最旧。
     *
     * @param sid  会话标识（= JWT jti，高熵）
     * @param info 会话内容（须含 {@code sid} 与 {@code employeeId}，否则无法维护索引）
     */
    public void saveBySid(String sid, SessionInfo info) {
        if (sid == null || sid.isBlank() || info == null) {
            throw new IllegalArgumentException("sid 与 SessionInfo 不能为空");
        }
        Long employeeId = parseEmployeeId(info.getEmployeeId());
        List<SessionEvictionPolicy.Existing> existing = loadExisting(employeeId);
        List<String> evictions = SessionEvictionPolicy.selectEvictions(
                existing,
                new SessionEvictionPolicy.Existing(sid, info.getClientType(), info.getDeviceId(), info.getLoginTime()),
                maxSessionsPerEmployee);
        for (String victim : evictions) {
            deleteSidOnly(victim);
            removeFromIndex(employeeId, victim);
        }
        writeSid(sid, info, employeeId);
    }

    /** 读取 sid 会话；不存在或类型异常返回 null（sid 非法直接返回 null，避免把任意串当 key） */
    public SessionInfo getBySid(String sid) {
        if (!SessionIdGenerator.isValid(sid)) {
            return null;
        }
        Object value = redisTemplate.opsForValue().get(SID_KEY_PREFIX + sid);
        return value instanceof SessionInfo sessionInfo ? sessionInfo : null;
    }

    /**
     * 删除单会话（登出 / 单设备撤销）并从索引移除，幂等。
     * 会话主体已不存在时无法回推 employeeId，仅删除主体键（索引由 TTL 与 {@link #listSids} 过滤自愈）。
     */
    public void deleteBySid(String sid) {
        if (sid == null || sid.isBlank()) {
            return;
        }
        SessionInfo info = getBySid(sid);
        deleteSidOnly(sid);
        if (info != null) {
            removeFromIndex(parseEmployeeId(info.getEmployeeId()), sid);
        }
    }

    /**
     * 仅刷新会话主体与索引 TTL（不做淘汰决策），用于旧会话补齐 stationId 后回写。
     * 为什么单独提供：回写是「补齐已有会话字段」，不应触发同端同设备互踢或并发淘汰（会误删他人会话）。
     */
    public void updateBySid(String sid, SessionInfo info) {
        if (sid == null || sid.isBlank() || info == null || !SessionIdGenerator.isValid(sid)) {
            return;
        }
        writeSid(sid, info, parseEmployeeId(info.getEmployeeId()));
    }

    /** 该员工全部活跃 sid（索引集合快照；缺失/过期返回空集）；不保证 sid 对应会话仍存在 */
    public Set<String> listSids(Long employeeId) {
        if (employeeId == null) {
            return Set.of();
        }
        Set<Object> members = redisTemplate.opsForSet().members(IDX_KEY_PREFIX + employeeId);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return members.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 强制下线：删除该员工<b>全部</b>会话与索引（禁用 / 删除 / 改密 / 重置密码等安全事件）。
     * 与旧 {@link #delete(Long)}（仅删单键）不同，本方法按索引遍历所有端/设备会话，原有「全端失效」副作用不变。
     */
    public void deleteAllOfEmployee(Long employeeId) {
        if (employeeId == null) {
            return;
        }
        for (String sid : listSids(employeeId)) {
            deleteSidOnly(sid);
        }
        redisTemplate.delete(IDX_KEY_PREFIX + employeeId);
    }

    // ==================== 旧键（兼容期，保留） ====================

    /**
     * 旧方法：写「员工单键」会话（{@code hrm:session:{employeeId}}，覆盖写 = 全局互踢）。
     * 仅由登录改造前的调用路径或旧会话回写使用；新登录一律走 {@link #saveBySid(String, SessionInfo)}。
     */
    public void save(Long employeeId, SessionInfo info) {
        redisTemplate.opsForValue().set(KEY_PREFIX + employeeId, info, Duration.ofSeconds(ttlSeconds));
    }

    /** 旧方法：读取「员工单键」会话（旧 token 回退分支用） */
    public SessionInfo get(Long employeeId) {
        Object value = redisTemplate.opsForValue().get(KEY_PREFIX + employeeId);
        return value instanceof SessionInfo sessionInfo ? sessionInfo : null;
    }

    /** 旧方法：删除「员工单键」会话（幂等） */
    public void delete(Long employeeId) {
        redisTemplate.delete(KEY_PREFIX + employeeId);
    }

    // ==================== 内部 ====================

    /** 组装现存会话摘要（索引中已失效的 sid 自动跳过） */
    private List<SessionEvictionPolicy.Existing> loadExisting(Long employeeId) {
        List<SessionEvictionPolicy.Existing> existing = new ArrayList<>();
        for (String sid : listSids(employeeId)) {
            SessionInfo session = getBySid(sid);
            if (session != null) {
                existing.add(new SessionEvictionPolicy.Existing(
                        sid, session.getClientType(), session.getDeviceId(), session.getLoginTime()));
            }
        }
        return existing;
    }

    /** 写入会话主体并登记索引（刷新索引 TTL） */
    private void writeSid(String sid, SessionInfo info, Long employeeId) {
        redisTemplate.opsForValue().set(SID_KEY_PREFIX + sid, info, Duration.ofSeconds(ttlSeconds));
        if (employeeId != null) {
            String idxKey = IDX_KEY_PREFIX + employeeId;
            redisTemplate.opsForSet().add(idxKey, sid);
            redisTemplate.expire(idxKey, Duration.ofSeconds(ttlSeconds));
        }
    }

    /** 仅删除会话主体键（不维护索引） */
    private void deleteSidOnly(String sid) {
        if (sid != null && !sid.isBlank()) {
            redisTemplate.delete(SID_KEY_PREFIX + sid);
        }
    }

    private void removeFromIndex(Long employeeId, String sid) {
        if (employeeId != null && sid != null && !sid.isBlank()) {
            redisTemplate.opsForSet().remove(IDX_KEY_PREFIX + employeeId, sid);
        }
    }

    private Long parseEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(employeeId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
