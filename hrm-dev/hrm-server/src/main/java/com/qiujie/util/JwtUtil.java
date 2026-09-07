package com.qiujie.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
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
 * - 解析失败（签名错误/过期/格式错误）抛 JwtException，由调用方转 401 语义。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire:86400}") long expireSeconds) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥至少 256 位（32 字节），启动期直接失败，避免带病运行
        if (secretBytes.length < 32) {
            throw new IllegalStateException("jwt.secret 长度不足：HS256 要求至少 32 字节随机串");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expireSeconds = expireSeconds;
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
