package com.qiujie.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 注册错误码单测（B3，api.md §2.2 / registration-design §3.3）。
 * <p>覆盖：9307/9308/9309 码值与文案；<b>9310 已废弃</b>（M-2 严格形态，不得存在）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class RegistrationErrorCodeTest {

    @Test
    @DisplayName("9307/9308/9309 码值与文案")
    void registrationCodes() {
        assertEquals(9307, ErrorCode.REGISTRATION_DUPLICATE.getCode());
        assertEquals("该手机号已有进行中的入职申请，请勿重复提交", ErrorCode.REGISTRATION_DUPLICATE.getMessage());
        assertEquals(9308, ErrorCode.REGISTRATION_NOT_EXISTS.getCode());
        assertEquals("入职申请不存在", ErrorCode.REGISTRATION_NOT_EXISTS.getMessage());
        assertEquals(9309, ErrorCode.REGISTRATION_STATUS_INVALID.getCode());
        assertEquals("申请状态不允许该操作", ErrorCode.REGISTRATION_STATUS_INVALID.getMessage());
    }

    @Test
    @DisplayName("9310（REGISTRATION_PHONE_TAKEN）已废弃：错误码表中不得存在 9310")
    void deprecated9310MustNotExist() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertNotEquals(9310, errorCode.getCode(), "9310 应废弃，不得新增常量");
        }
    }
}
