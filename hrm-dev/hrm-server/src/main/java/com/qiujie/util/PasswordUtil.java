package com.qiujie.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 散列工具（$2a$，cost=10，与 employee.password 存储格式一致）。
 *
 * 另提供 main 方法用于生成种子数据（V2__init_data.sql）所需散列：
 * IDE 直接运行并传入明文初始密码，将输出值固化进种子脚本（散列单向，不构成泄露）。
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    /** 明文 → BCrypt 散列 */
    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** 明文与散列比对 */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    /** 生成种子数据散列（供任务 A03 使用） */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法：运行本类并传入明文密码作为参数，输出生成的 BCrypt 散列");
            return;
        }
        System.out.println(hash(args[0]));
    }
}
