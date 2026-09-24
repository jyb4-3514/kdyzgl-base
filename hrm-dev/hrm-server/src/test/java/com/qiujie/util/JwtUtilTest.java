package com.qiujie.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JwtUtil 离线单测（不依赖 Spring 容器与外部服务）。
 */
class JwtUtilTest {

    /** 仅测试用密钥（≥32 字节），非真实生产密钥 */
    private static final String SECRET = "unit-test-secret-key-0123456789abcdef";
    private static final String OTHER_SECRET = "another-secret-key-0123456789abcdef";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 3600);
    }

    @Test
    void generateAndParseRoundTrip() {
        String token = jwtUtil.generate(42L, "zhangsan", "ADMIN", "jti-abc");

        Claims claims = jwtUtil.parse(token);
        assertEquals("zhangsan", claims.getSubject());
        assertEquals("jti-abc", claims.getId());
        assertEquals(42L, jwtUtil.getUserId(claims));
        assertEquals("ADMIN", claims.get("role"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void parseExpiredTokenThrows() {
        // 用相同密钥手工构造已过期 Token（签发与过期时间均在过去）
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .setSubject("zhangsan")
                .claim("userId", 42L)
                .claim("role", "STAFF")
                .setId("jti-expired")
                .setIssuedAt(new Date(System.currentTimeMillis() - 7200_000L))
                .setExpiration(new Date(System.currentTimeMillis() - 3600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.parse(expiredToken));
    }

    @Test
    void parseTamperedTokenThrows() {
        String token = jwtUtil.generate(1L, "admin", "ADMIN", "jti-1");
        assertThrows(JwtException.class, () -> jwtUtil.parse(token + "x"));
    }

    @Test
    void parseTokenWithWrongSecretThrows() {
        JwtUtil other = new JwtUtil(OTHER_SECRET, 3600);
        String token = other.generate(1L, "admin", "ADMIN", "jti-2");
        assertThrows(JwtException.class, () -> jwtUtil.parse(token));
    }

    @Test
    void parseMalformedTokenThrows() {
        assertThrows(JwtException.class, () -> jwtUtil.parse("not-a-jwt-token"));
    }

    @Test
    void shortSecretRejectedAtConstruction() {
        // HS256 要求 ≥32 字节，启动期直接失败（防带病运行）
        assertThrows(IllegalStateException.class, () -> new JwtUtil("short-secret", 3600));
    }

    @Test
    void getUserIdReturnsNullWhenClaimMissing() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .setSubject("nouser")
                .setId("jti-3")
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        Claims claims = jwtUtil.parse(token);
        assertNull(jwtUtil.getUserId(claims));
    }
}
