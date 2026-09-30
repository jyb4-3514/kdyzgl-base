package com.qiujie.service.finance.port.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.qiujie.config.PayrollScheduleProperties;
import com.qiujie.entity.PayrollRun;
import com.qiujie.entity.StationPayrollSetting;
import com.qiujie.mapper.StationPayrollSettingMapper;
import com.qiujie.service.finance.support.PayrollNotifySupport;
import com.qiujie.service.finance.support.PayrollRunFailureSupport;
import com.qiujie.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 算薪运行通知真实实现单测（Mockito，无 DB）：类型 7 受 {@code notify_enabled} 控制、
 * 失败/僵死回收告警受 {@code notify-on-fail} 控制、通知异常不阻断、NOTIFY_SKIP 留痕。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class RealPayrollRunNotifierTest {

    private static final Long STATION_ID = 8L;
    private static final String MONTH = "2026-09";

    private NotificationService notificationService;
    private StationPayrollSettingMapper settingMapper;
    private PayrollScheduleProperties scheduleProperties;
    private RealPayrollRunNotifier notifier;

    private Logger supportLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        settingMapper = mock(StationPayrollSettingMapper.class);
        scheduleProperties = new PayrollScheduleProperties();
        // 默认 notifyOnFail=true（出厂口径）
        PayrollNotifySupport support = new PayrollNotifySupport(notificationService, settingMapper, scheduleProperties);
        notifier = new RealPayrollRunNotifier(support);

        supportLogger = (Logger) LoggerFactory.getLogger(PayrollNotifySupport.class);
        appender = new ListAppender<>();
        appender.start();
        supportLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        supportLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("类型 7：notify_enabled=0 → 不投递且留 NOTIFY_SKIP")
    void pendingApproval_skipWhenNotifyDisabled() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(0)));
        when(notificationService.findAdminEmployeeIds()).thenReturn(List.of(1L));

        notifier.onSucceeded(run(), List.of(100L), 1);

        verify(notificationService, never()).sendSystem(any(), anyInt(), anyString(), anyString(), anyString(), any());
        assertTrue(skipLogged(), "未投递须留 NOTIFY_SKIP 排障痕迹");
    }

    @Test
    @DisplayName("类型 7：notify_enabled=1 → 逐单逐管理员投递（biz_type=payroll、biz_id=payroll.id）")
    void pendingApproval_sendsToAdminsPerPayroll() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1)));
        when(notificationService.findAdminEmployeeIds()).thenReturn(List.of(1L, 2L));

        notifier.onSucceeded(run(), List.of(100L, 101L), 2);

        verify(notificationService).sendSystem(eq(1L), eq(7), anyString(), anyString(), eq("payroll"), eq(100L));
        verify(notificationService).sendSystem(eq(1L), eq(7), anyString(), anyString(), eq("payroll"), eq(101L));
        verify(notificationService).sendSystem(eq(2L), eq(7), anyString(), anyString(), eq("payroll"), eq(100L));
        verify(notificationService).sendSystem(eq(2L), eq(7), anyString(), anyString(), eq("payroll"), eq(101L));
    }

    @Test
    @DisplayName("类型 7：无在职管理员 → 不投递且留 NOTIFY_SKIP")
    void pendingApproval_skipWhenNoAdmin() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1)));
        when(notificationService.findAdminEmployeeIds()).thenReturn(List.of());

        notifier.onSucceeded(run(), List.of(100L), 1);

        verify(notificationService, never()).sendSystem(any(), anyInt(), anyString(), anyString(), anyString(), any());
        assertTrue(skipLogged());
    }

    @Test
    @DisplayName("失败告警：notify-on-fail=false → 不投递且留 NOTIFY_SKIP")
    void failed_skipWhenNotifyOnFailDisabled() {
        scheduleProperties.setNotifyOnFail(false);
        when(notificationService.findAdminEmployeeIds()).thenReturn(List.of(1L));

        notifier.onFailed(run());

        verify(notificationService, never()).sendSystem(any(), anyInt(), anyString(), anyString(), any(), any());
        assertTrue(skipLogged());
    }

    @Test
    @DisplayName("失败告警：notify-on-fail=true → 投递类型 10 给管理员")
    void failed_sendsAlert() {
        when(notificationService.findAdminEmployeeIds()).thenReturn(List.of(1L, 2L));

        notifier.onFailed(run());

        verify(notificationService).sendSystem(eq(1L), eq(10), anyString(), anyString(), eq("payroll"), isNull());
        verify(notificationService).sendSystem(eq(2L), eq(10), anyString(), anyString(), eq("payroll"), isNull());
    }

    @Test
    @DisplayName("僵死回收：命中即逐条告警（类型 10），消除静默失效")
    void staleReclaimed_alertPerRow() {
        when(notificationService.findAdminEmployeeIds()).thenReturn(List.of(1L));

        notifier.onStaleReclaimed(List.of(run()));

        verify(notificationService).sendSystem(eq(1L), eq(10), anyString(), anyString(), eq("payroll"), isNull());
    }

    @Test
    @DisplayName("通知异常不阻断：接收人解析抛错时业务流程不中断")
    void notificationFailureNotPropagated() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1)));
        when(notificationService.findAdminEmployeeIds()).thenThrow(new IllegalStateException("boom"));

        assertDoesNotThrow(() -> notifier.onSucceeded(run(), List.of(100L), 1));
    }

    private boolean skipLogged() {
        return appender.list.stream()
                .anyMatch(event -> event.getFormattedMessage() != null
                        && event.getFormattedMessage().contains(PayrollNotifySupport.NOTIFY_SKIP));
    }

    private PayrollRun run() {
        PayrollRun run = new PayrollRun();
        run.setId(1L);
        run.setStationId(STATION_ID);
        run.setTargetMonth(MONTH);
        run.setFailReason(PayrollRunFailureSupport.STALE_RECLAIMED);
        return run;
    }

    private StationPayrollSetting setting(int notifyEnabled) {
        StationPayrollSetting setting = new StationPayrollSetting();
        setting.setId(1L);
        setting.setStationId(STATION_ID);
        setting.setNotifyEnabled(notifyEnabled);
        return setting;
    }
}
