package com.qiujie.service.finance.support;

import com.qiujie.exception.BusinessException;
import com.qiujie.service.support.ClientLogSanitizer;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

/**
 * 自动算薪失败原因构造（脱敏 + 截断；安全 M-2③/M-5 / 算法 §1.6）。
 * <p>
 * 为什么统一收口：{@code fail_reason} 会落库并进 I-5 查询，若直接拼 SQL 异常原文 / 参数值 / 业务数据，
 * 等于把敏感信息写进可查的审计面。本类只输出「异常类名 + 白名单化文案」，数据库类异常进一步只留类名，
 * 完整堆栈仍只进应用 error 日志。
 */
public final class PayrollRunFailureSupport {

    /** 僵死 RUNNING 被回收的机器可辨识标记（失败原因白名单常量，非敏感信息；算法 §1.5.3 / R-C3） */
    public static final String STALE_RECLAIMED = "STALE_RECLAIMED";

    /** fail_reason 列长上限（VARCHAR(500)），按字符安全截断 */
    private static final int MAX_LENGTH = 500;

    private PayrollRunFailureSupport() {
    }

    /**
     * 由异常构造可落库的失败原因。
     * <p>数据库类异常（{@link DataAccessException}/{@link SQLException}）只输出类名，避免 SQL 原文与外泄风险。
     */
    public static String describe(Throwable e) {
        if (e == null) {
            return null;
        }
        String raw;
        if (e instanceof BusinessException be) {
            raw = e.getClass().getSimpleName() + "(code=" + be.getCode() + "): " + be.getMessage();
        } else if (e instanceof DataAccessException || e instanceof SQLException) {
            raw = e.getClass().getSimpleName() + ": 数据访问异常（详见应用日志）";
        } else {
            raw = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
        // 去控制字符（换行/制表），防日志/展示注入
        String cleaned = raw.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").trim();
        // 二次兜底（M-5）：擦除 token=/password= 等凭据形态串；白名单构造仍是主口径（scrub 不覆盖证件号/卡号）
        String scrubbed = ClientLogSanitizer.scrub(cleaned);
        return scrubbed.length() <= MAX_LENGTH ? scrubbed : scrubbed.substring(0, MAX_LENGTH);
    }
}
