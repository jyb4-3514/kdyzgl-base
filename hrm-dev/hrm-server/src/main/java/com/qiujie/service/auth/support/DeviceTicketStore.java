package com.qiujie.service.auth.support;

import com.qiujie.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 二次验证票据存储（Redis；不落库，架构 §4.3.2）。
 * <p>
 * 键：{@code hrm:auth:device-ticket:<ticket>}（值 = {@link DeviceTicket} JSON；TTL = 票据有效期）。
 * <ul>
 *   <li>票据由服务端用 SecureRandom 生成的高熵串（{@code ticket.<hex>}），客户端不可预测；</li>
 *   <li><b>双重到期判定</b>：Redis TTL 兜底 + 载荷内 {@code expireAtEpochMillis} 显式判定（防 TTL 配置被改大）；</li>
 *   <li>校验成功即删（一次性），失败/过期同样清理，避免脏票据堆积。</li>
 * </ul>
 * 使用全局 {@code RedisTemplate<String,Object>}（JSON 序列化）；载荷为扁平 POJO，无嵌套自定义类型，
 * 反序列化不依赖类型信息（规避默认类型策略对 final 类型的限制）。
 */
@Component
@RequiredArgsConstructor
public class DeviceTicketStore {

    /** 票据键前缀 */
    public static final String KEY_PREFIX = "hrm:auth:device-ticket:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AuthProperties authProperties;

    /**
     * 生成并存储票据，返回票据串（明文仅此处出现一次）。
     *
     * @param deviceSnapshot 登录时采集的设备弱信号快照（二次验证通过后据此复算同一指纹，保证幂等 upsert）
     */
    public String issue(Long employeeId, DeviceSnapshot deviceSnapshot, String clientType, String entryAs,
                        long nowEpochMillis) {
        String ticket = "ticket." + DeviceTokenCodec.newToken();
        long ttlSeconds = Math.max(1, authProperties.getDeviceTicketTtlSeconds());
        DeviceTicket payload = new DeviceTicket();
        payload.setEmployeeId(employeeId);
        payload.setClientType(clientType);
        payload.setEntryAs(entryAs);
        payload.setExpireAtEpochMillis(nowEpochMillis + ttlSeconds * 1000L);
        if (deviceSnapshot != null) {
            payload.setDeviceId(deviceSnapshot.deviceId());
            payload.setPlatform(deviceSnapshot.platform());
            payload.setModel(deviceSnapshot.model());
            payload.setOsVersion(deviceSnapshot.osVersion());
            payload.setAppVersion(deviceSnapshot.appVersion());
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, payload, Duration.ofSeconds(ttlSeconds));
        return ticket;
    }

    /** 读取票据（不存在 / 已过期 / 类型异常返回 null，并顺手清理已过期票据） */
    public DeviceTicket find(String ticket, long nowEpochMillis) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        Object value = redisTemplate.opsForValue().get(KEY_PREFIX + ticket);
        if (!(value instanceof DeviceTicket payload)) {
            return null;
        }
        if (payload.isExpired(nowEpochMillis)) {
            delete(ticket);
            return null;
        }
        return payload;
    }

    /** 删除票据（校验成功 / 失效清理） */
    public void delete(String ticket) {
        if (ticket != null && !ticket.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + ticket);
        }
    }
}
