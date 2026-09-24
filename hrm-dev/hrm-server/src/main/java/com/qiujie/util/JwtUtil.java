package com.qiujie.util;

import com.qiujie.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具（HS256，见 api.md 第 3 章）：
 * - claims：sub=username, userId, role, jti, iat, exp；
 * - 密钥与有效期读配置（jwt.secret / jwt.expire），无任何硬编码密钥；
 * - 解析失败（签名错误/过期/格式错误）抛 JwtException，由调用方转 401 语义（到期语义见 {@code expiryGraceSeconds}）。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireSeconds;
    /**
     * 校验宽限（秒，{@code hrm.auth.expiry-grace-seconds}）。
     * <p>
     * <b>只放宽校验、不改变签发</b>：{@code exp} 仍为 {@code now + jwt.expire}，本值让「刚过期」的 Token 仍可被解析，
     * 使 {@code JwtAuthFilter} 能把「重认证窗口已过」判定为专用码 1108（前端据此跳 {@code /login?expired=1}），
     * 而不是解析期直接抛过期异常 → 裸 401。鉴权窗口恒由 {@code hrm.auth.periodic-reauth-seconds} 决定：
     * 超窗请求一律被拒（1108），宽限期内不存在任何「可用」请求。
     */
    private final long expiryGraceSeconds;

    /** 生产构造器（Spring 注入；宽限取强类型配置 {@code hrm.auth.expiry-grace-seconds}） */
    @Autowired
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire:259200}") long expireSeconds,
                   AuthProperties authProperties) {
        this(secret, expireSeconds, authProperties == null ? 0L : authProperties.getExpiryGraceSeconds());
    }

    /** 兼容构造器（离线单测用；宽限为 0，行为与 M1 一致） */
    public JwtUtil(String secret, long expireSeconds) {
        this(secret, expireSeconds, 0L);
    }

    private JwtUtil(String secret, long expireSeconds, long expiryGraceSeconds) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥至少 256 位（32 字节），启动期直接失败，避免带病运行
        if (secretBytes.length < 32) {
            throw new IllegalStateException("jwt.secret 长度不足：HS256 要求至少 32 字节随机串");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expireSeconds = expireSeconds;
        this.expiryGraceSeconds = Math.max(0L, expiryGraceSeconds);
    }

    /** Token 有效期（秒），供登录响应 expiresIn 与 Redis 会话 TTL 复用 */
    public long getExpireSeconds() {
        return expireSeconds;
    }

    /**
     * 生成 Token。
     *
     * @param jti 会话唯一标识（由调用方生成并同步写入 Redis 会话，用于互踢判断）
     */
    public String generate(Long userId, String username, String role, String jti) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setId(jti == null ? UUID.randomUUID().toString() : jti)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析并校验 Token（签名 + 过期）。
     *
     * @throws io.jsonwebtoken.JwtException Token 无效或已过期
     */
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                // 到期宽限：仅让「刚过期」的 Token 仍可被解析，以便上层返回专用码 1108（见字段注释）
                .setAllowedClockSkewSeconds(expiryGraceSeconds)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** 从 claims 提取 userId（JSON 数字可能反序列化为 Integer/Long，统一按 Number 取值） */
    public Long getUserId(Claims claims) {
        Object value = claims.get("userId");
        return value instanceof Number number ? number.longValue() : null;
    }
}
