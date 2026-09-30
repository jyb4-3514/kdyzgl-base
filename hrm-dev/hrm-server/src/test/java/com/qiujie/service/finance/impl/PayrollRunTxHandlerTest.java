package com.qiujie.service.finance.impl;

import com.qiujie.mapper.PayrollRunMapper;
import com.qiujie.service.finance.PayrollService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 自动算薪事务分段处理器单测（Mockito，无 DB）：自动提交（Tx2b）与跳过留痕均<b>复用</b>既有
 * {@link PayrollService} 入口，不在本类另写状态流转 / 留痕口径。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollRunTxHandlerTest {

    private static final Long PAYROLL_ID = 700L;

    private PayrollService payrollService;
    private PayrollRunTxHandler txHandler;

    @BeforeEach
    void setUp() {
        payrollService = mock(PayrollService.class);
        txHandler = new PayrollRunTxHandler(mock(PayrollRunMapper.class), payrollService);
    }

    @Test
    @DisplayName("Tx2b：submitOne → 复用 PayrollService.submit(单元素)（落 PENDING_APPROVAL 由其口径保证）")
    void submitOne_delegatesToSubmit() {
        txHandler.submitOne(PAYROLL_ID);

        verify(payrollService).submit(List.of(PAYROLL_ID));
    }

    @Test
    @DisplayName("跳过失痕：recordSubmitSkipped → 复用 PayrollService.recordAutoSubmitSkipped")
    void recordSubmitSkipped_delegates() {
        txHandler.recordSubmitSkipped(PAYROLL_ID, "BusinessException(code=9403): 状态不允许");

        verify(payrollService).recordAutoSubmitSkipped(PAYROLL_ID, "BusinessException(code=9403): 状态不允许");
    }
}
