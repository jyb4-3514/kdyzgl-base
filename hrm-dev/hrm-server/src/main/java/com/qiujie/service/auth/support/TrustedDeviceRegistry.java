package com.qiujie.service.auth.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiujie.entity.AuthTrustedDevice;
import com.qiujie.mapper.AuthTrustedDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 受信设备登记与撤销（表 {@code auth_trusted_device}）。
 * <p>
 * <b>信任态由服务端持有的落地</b>（security-auth-review §4.2）：
 * <ul>
 *   <li>「是否受信」只认服务端签发令牌的<b>摘要</b>匹配（{@link #findUsableByToken}），
 *       客户端弱信号（deviceId/platform…）一律不参与放行；</li>
 *   <li>登记以 {@code (employee_id, device_fingerprint)} 唯一键<b>幂等 upsert</b>，重信复用同一行；</li>
 *   <li>单员工可信任设备数上限由调用方传入（{@code hrm.auth.max-trusted-devices-per-employee}），
 *       超限淘汰<b>最久未活跃</b>者（撤销而非删除，保留审计）；</li>
 *   <li>撤销一律软撤销（{@code revoked=1} + {@code revoked_at}）。</li>
 * </ul>
 * SQL 全部走 MyBatis-Plus 参数化 API（无字符串拼接，规则 §4）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustedDeviceRegistry {

    private final AuthTrustedDeviceMapper mapper;

    /**
     * 按「员工 + 服务端令牌摘要」查可用受信设备（信任放行的<b>唯一</b>依据）。
     *
     * @param employeeId   归属员工
     * @param tokenHash    {@code device_token} 的 SHA-256 摘要（明文由 Cookie 携带，摘要由服务端计算）
     * @param nowEpochSec  当前服务端时刻（epoch 秒，用于有效期判定）
     * @return 可用行；不存在 / 未受信 / 已撤销 / 已过期 返回 {@code null}
     */
    public AuthTrustedDevice findUsableByToken(Long employeeId, String tokenHash, long nowEpochSec) {
        if (employeeId == null || tokenHash == null || tokenHash.isBlank()) {
            return null;
        }
        AuthTrustedDevice row = mapper.selectOne(new LambdaQueryWrapper<AuthTrustedDevice>()
                .eq(AuthTrustedDevice::getEmployeeId, employeeId)
                .eq(AuthTrustedDevice::getDeviceTokenHash, tokenHash)
                .eq(AuthTrustedDevice::getTrusted, 1)
                .eq(AuthTrustedDevice::getRevoked, 0)
                .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        Long expiresEpochSec = toEpochSeconds(row.getExpiresAt());
        return TrustedDevicePolicy.isUsable(row.getTrusted(), row.getRevoked(), expiresEpochSec, nowEpochSec)
                ? row : null;
    }

    /**
     * 注册 / 刷新受信设备（幂等 upsert），并按上限淘汰最久未活跃者。
     *
     * @param employeeId   归属员工
     * @param fingerprint  服务端 HMAC 指纹（唯一键之一，幂等依据）
     * @param tokenHash    本次签发的 {@code device_token} 摘要（覆盖旧摘要 → 旧令牌立即失效）
     * @param deviceId     前端上报设备 ID（弱信号，仅展示）
     * @param platform     端平台（ANDROID/IOS/H5/WEB）
     * @param model        型号（弱信号）
     * @param osVersion    系统版本（弱信号）
     * @param appVersion   壳版本（弱信号）
     * @param lastIp       最近来源 IP（出参脱敏）
     * @param ttlSeconds   信任有效期（秒；{@code hrm.auth.device-trust-ttl-seconds}）
     * @param maxDevices   单员工可信任设备数上限（{@code <=0} 视为不限）
     * @return 落库后的受信设备行
     */
    public AuthTrustedDevice upsertTrusted(Long employeeId, String fingerprint, String tokenHash,
                                          String deviceId, String platform, String model, String osVersion,
                                          String appVersion, String lastIp, LocalDateTime now,
                                          long ttlSeconds, int maxDevices) {
        LocalDateTime expiresAt = now.plusSeconds(Math.max(0L, ttlSeconds));
        AuthTrustedDevice existing = mapper.selectOne(new LambdaQueryWrapper<AuthTrustedDevice>()
                .eq(AuthTrustedDevice::getEmployeeId, employeeId)
                .eq(AuthTrustedDevice::getDeviceFingerprint, fingerprint)
                .last("LIMIT 1"));

        AuthTrustedDevice row;
        if (existing == null) {
            row = new AuthTrustedDevice();
            row.setEmployeeId(employeeId);
            row.setDeviceFingerprint(fingerprint);
            row.setFirstSeenTime(now);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            fillMutable(row, tokenHash, deviceId, platform, model, osVersion, appVersion, lastIp, now, expiresAt);
            mapper.insert(row);
        } else {
            row = existing;
            // 软撤销字段须显式置空：updateById 的默认策略会忽略 null，无法清空 revoked_at
            mapper.update(null, new LambdaUpdateWrapper<AuthTrustedDevice>()
                    .eq(AuthTrustedDevice::getId, existing.getId())
                    .set(AuthTrustedDevice::getDeviceTokenHash, tokenHash)
                    .set(AuthTrustedDevice::getDeviceId, deviceId)
                    .set(AuthTrustedDevice::getPlatform, platform)
                    .set(AuthTrustedDevice::getModel, model)
                    .set(AuthTrustedDevice::getOsVersion, osVersion)
                    .set(AuthTrustedDevice::getAppVersion, appVersion)
                    .set(AuthTrustedDevice::getLastIp, lastIp)
                    .set(AuthTrustedDevice::getLastSeenTime, now)
                    .set(AuthTrustedDevice::getExpiresAt, expiresAt)
                    .set(AuthTrustedDevice::getTrusted, 1)
                    .set(AuthTrustedDevice::getRevoked, 0)
                    .set(AuthTrustedDevice::getRevokedAt, null)
                    .set(AuthTrustedDevice::getUpdateTime, now));
            fillMutable(row, tokenHash, deviceId, platform, model, osVersion, appVersion, lastIp, now, expiresAt);
            row.setRevokedAt(null);
        }
        evictOverflow(employeeId, maxDevices, now);
        return row;
    }

    /** 刷新增受信设备最近活跃时间（受信设备再次登录时调用；失败不影响登录主流程） */
    public void touch(Long deviceRowId, LocalDateTime now) {
        if (deviceRowId == null || now == null) {
            return;
        }
        mapper.update(null, new LambdaUpdateWrapper<AuthTrustedDevice>()
                .eq(AuthTrustedDevice::getId, deviceRowId)
                .set(AuthTrustedDevice::getLastSeenTime, now)
                .set(AuthTrustedDevice::getUpdateTime, now));
    }

    /** 本人设备列表（仅未撤销；按最近活跃倒序） */
    public List<AuthTrustedDevice> listActiveByEmployee(Long employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<AuthTrustedDevice>()
                .eq(AuthTrustedDevice::getEmployeeId, employeeId)
                .eq(AuthTrustedDevice::getRevoked, 0)
                .orderByDesc(AuthTrustedDevice::getLastSeenTime));
    }

    /**
     * 撤销本人某台设备（按前端 deviceId 弱信号定位，归属限定 employeeId）。
     *
     * @return 命中并撤销返回 {@code true}；无匹配或已撤销返回 {@code false}
     */
    public boolean revokeByDeviceId(Long employeeId, String deviceId, LocalDateTime now) {
        if (employeeId == null || deviceId == null || deviceId.isBlank()) {
            return false;
        }
        int affected = mapper.update(null, new LambdaUpdateWrapper<AuthTrustedDevice>()
                .eq(AuthTrustedDevice::getEmployeeId, employeeId)
                .eq(AuthTrustedDevice::getDeviceId, deviceId)
                .eq(AuthTrustedDevice::getRevoked, 0)
                .set(AuthTrustedDevice::getRevoked, 1)
                .set(AuthTrustedDevice::getRevokedAt, now)
                .set(AuthTrustedDevice::getUpdateTime, now));
        return affected > 0;
    }

    /**
     * 使该员工<b>全部</b>受信设备失效（改密 / 禁用 / 删除 / 重置密码等安全事件，「改密即失效」）。
     *
     * @return 受影响行数
     */
    public int revokeAllOfEmployee(Long employeeId) {
        if (employeeId == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        return mapper.update(null, new LambdaUpdateWrapper<AuthTrustedDevice>()
                .eq(AuthTrustedDevice::getEmployeeId, employeeId)
                .eq(AuthTrustedDevice::getRevoked, 0)
                .set(AuthTrustedDevice::getRevoked, 1)
                .set(AuthTrustedDevice::getRevokedAt, now)
                .set(AuthTrustedDevice::getUpdateTime, now));
    }

    // ==================== 内部 ====================

    /** 可变更字段统一赋值（insert 与 update 共用，避免两处漏字段漂移） */
    private void fillMutable(AuthTrustedDevice row, String tokenHash, String deviceId, String platform,
                             String model, String osVersion, String appVersion, String lastIp,
                             LocalDateTime now, LocalDateTime expiresAt) {
        row.setDeviceTokenHash(tokenHash);
        row.setDeviceId(deviceId);
        row.setPlatform(platform);
        row.setModel(model);
        row.setOsVersion(osVersion);
        row.setAppVersion(appVersion);
        row.setLastIp(lastIp);
        row.setLastSeenTime(now);
        row.setExpiresAt(expiresAt);
        row.setTrusted(1);
        row.setRevoked(0);
        row.setUpdateTime(now);
    }

    /** 超上限淘汰：撤销最久未活跃的受信设备（保留审计行；幂等 upsert 保证重信可复用） */
    private void evictOverflow(Long employeeId, int maxDevices, LocalDateTime now) {
        if (maxDevices <= 0) {
            return;
        }
        List<AuthTrustedDevice> active = mapper.selectList(new LambdaQueryWrapper<AuthTrustedDevice>()
                .eq(AuthTrustedDevice::getEmployeeId, employeeId)
                .eq(AuthTrustedDevice::getRevoked, 0)
                .orderByAsc(AuthTrustedDevice::getLastSeenTime));
        if (active == null || active.size() <= maxDevices) {
            return;
        }
        int overflow = active.size() - maxDevices;
        for (int i = 0; i < overflow; i++) {
            AuthTrustedDevice victim = active.get(i);
            mapper.update(null, new LambdaUpdateWrapper<AuthTrustedDevice>()
                    .eq(AuthTrustedDevice::getId, victim.getId())
                    .set(AuthTrustedDevice::getRevoked, 1)
                    .set(AuthTrustedDevice::getRevokedAt, now)
                    .set(AuthTrustedDevice::getUpdateTime, now));
            log.info("受信设备超出上限({})，已撤销最久未活跃设备，employeeId={}, deviceRowId={}",
                    maxDevices, employeeId, victim.getId());
        }
    }

    /** LocalDateTime → epoch 秒（null 安全） */
    private Long toEpochSeconds(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }
}
