package com.qiujie.service.finance.support;

import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 失败原因脱敏单测（M-5）：凭据形态二次擦除、数据库异常只留类名、长度截断。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollRunFailureSupportTest {

    @Test
    @DisplayName("凭据形态串被擦除（token=/password=）")
    void scrubsCredentialLikeFragments() {
        String reason = PayrollRunFailureSupport.describe(new RuntimeException("call failed token=abcdef123 pwd=hunter2"));

        assertFalse(reason.contains("abcdef123"), "token 值不得落库");
        assertFalse(reason.contains("hunter2"), "口令值不得落库");
        assertTrue(reason.contains("token=***"));
        assertTrue(reason.contains("pwd=***"));
    }

    @Test
    @DisplayName("BusinessException：保留类名 + 错误码 + 文案，去控制字符")
    void describesBusinessException() {
        String reason = PayrollRunFailureSupport.describe(
                new BusinessException(ErrorCode.FINANCE_PAYROLL_GENERATED, "该月工资单已生成"));

        assertTrue(reason.contains("BusinessException"));
        assertTrue(reason.contains("code=" + ErrorCode.FINANCE_PAYROLL_GENERATED.getCode()));
        assertFalse(reason.contains("\n"));
    }

    @Test
    @DisplayName("数据库异常：只留类名 + 白名单文案（不落 SQL 原文）")
    void hidesDataAccessDetails() {
        DataAccessException e = new QueryTimeoutException("timeout on SELECT * FROM payroll WHERE id=1");

        String reason = PayrollRunFailureSupport.describe(e);

        assertTrue(reason.contains("QueryTimeoutException"));
        assertFalse(reason.contains("SELECT"));
        assertFalse(reason.contains("payroll"));
    }

    @Test
    @DisplayName("超长按字符截断 ≤500")
    void truncatesTo500() {
        String reason = PayrollRunFailureSupport.describe(new RuntimeException("x".repeat(1200)));

        assertTrue(reason.length() <= 500);
    }
}
