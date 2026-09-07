package com.qiujie.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码编码器：仅引入 spring-security-crypto 使用 BCrypt（决策 D10），
 * 不引入完整 Spring Security 过滤链。散列格式 $2a$、cost=10，与种子数据一致。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
