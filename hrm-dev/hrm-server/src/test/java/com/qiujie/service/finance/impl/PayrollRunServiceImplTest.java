package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.config.PayrollScheduleProperties;
import com.qiujie.dto.finance.PayrollRunQuery;
import com.qiujie.dto.finance.PayrollRunTriggerRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Payroll;
import com.qiujie.entity.PayrollRun;
import com.qiujie.entity.Station;
import com.qiujie.entity.StationPayrollSetting;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.PayrollMapper;
import com.qiujie.mapper.PayrollRunMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.StationPayrollSettingMapper;
import com.qiujie.service.finance.port.PayrollRunNotifier;
import com.qiujie.service.finance.support.PayrollRunSkipCode;
import com.qiujie.service.finance.support.PayrollRunStatus;
import com.qiujie.service.finance.support.PayrollRunTriggerType;
import com.qiujie.vo.finance.PayrollGenerateVO;
import com.qiujie.vo.finance.PayrollRunVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动算薪运行服务单测（Mockito，无 DB）：I-4 触发约束（9410/9415/9405→SKIPPED）、
 * 到点判定短路（未到算薪日 / 当日钟点未到 / 已占位 / 当日已尝试）、claim 冲突、僵死回收、I-5 的 9409。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollRunServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Long STATION_ID = 8L;
    private static final String MONTH = YearMonth.now(ZONE).toString();

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PayrollRun.class);
        TableInfoHelper.initTableInfo(assistant, StationPayrollSetting.class);
        TableInfoHelper.initTableInfo(assistant, Station.class);
        TableInfoHelper.initTableInfo(assistant, Payroll.class);
        TableInfoHelper.initTableInfo(assistant, Employee.class);
    }

    private PayrollRunMapper payrollRunMapper;
    private StationPayrollSettingMapper settingMapper;
    private StationMapper stationMapper;
    private PayrollMapper payrollMapper;
    private EmployeeMapper employeeMapper;
    private PayrollRunTxHandler txHandler;
    private PayrollRunNotifier notifier;
    private PayrollScheduleProperties scheduleProperties;
    private PayrollRunServiceImpl service;

    @BeforeEach
    void setUp() {
        payrollRunMapper = mock(PayrollRunMapper.class);
        settingMapper = mock(StationPayrollSettingMapper.class);
        stationMapper = mock(StationMapper.class);
        payrollMapper = mock(PayrollMapper.class);
        employeeMapper = mock(EmployeeMapper.class);
        txHandler = mock(PayrollRunTxHandler.class);
        notifier = mock(PayrollRunNotifier.class);
        scheduleProperties = new PayrollScheduleProperties();
        scheduleProperties.setZone("Asia/Shanghai");
        service = new PayrollRunServiceImpl(payrollRunMapper, settingMapper, stationMapper, payrollMapper,
                employeeMapper, txHandler, scheduleProperties, notifier);

        when(stationMapper.selectById(anyLong())).thenReturn(station());
        when(payrollRunMapper.selectCount(any())).thenReturn(0L);
        when(payrollMapper.selectCount(any())).thenReturn(0L);
        when(payrollRunMapper.insert(any(PayrollRun.class))).thenAnswer(inv -> {
            PayrollRun run = inv.getArgument(0);
            run.setId(1L);
            return 1;
        });
        when(txHandler.claim(any())).thenAnswer(inv -> {
            PayrollRun run = inv.getArgument(0);
            run.setId(1L);
            return run;
        });
    }

    // ==================== I-4 触发约束 ====================

    @Test
    @DisplayName("I-4 驿站不存在 → 4001")
    void trigger_stationMissing_throws4001() {
        when(stationMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.trigger(triggerRequest()));
        assertEquals(ErrorCode.STATION_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("I-4 未配置 / 未启用 → 9415")
    void trigger_disabled_throws9415() {
        when(settingMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.trigger(triggerRequest()));
        assertEquals(ErrorCode.FINANCE_PAYROLL_RUN_DISABLED.getCode(), ex.getCode());

        when(settingMapper.selectList(any())).thenReturn(List.of(setting(0, 1, "00:00")));
        BusinessException ex2 = assertThrows(BusinessException.class, () -> service.trigger(triggerRequest()));
        assertEquals(ErrorCode.FINANCE_PAYROLL_RUN_DISABLED.getCode(), ex2.getCode());
    }

    @Test
    @DisplayName("I-4 已占位（claim_key=month）→ 9410")
    void trigger_occupied_throws9410() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1, 1, "00:00")));
        when(payrollRunMapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.trigger(triggerRequest()));
        assertEquals(ErrorCode.FINANCE_PAYROLL_RUN_IN_PROGRESS.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("I-4 当日已尝试 → 9410（uk_attempt 语义）")
    void trigger_attemptedSameDay_throws9410() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1, 1, "00:00")));
        // 第 1 次 selectCount（占位）= 0，第 2 次（当日已尝试）= 1
        when(payrollRunMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.trigger(triggerRequest()));
        assertEquals(ErrorCode.FINANCE_PAYROLL_RUN_IN_PROGRESS.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("I-4 认领撞唯一键（并发/跨实例）→ 9410")
    void trigger_claimConflict_throws9410() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1, 1, "00:00")));
        // 用 doThrow 而非 when().thenThrow()：后者在 stubbing 时会真实调用 mock 方法，
        // 从而命中 setUp 中 claim 的 thenAnswer（实参 any() 为 null）导致 NPE
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_attempt")).when(txHandler).claim(any());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.trigger(triggerRequest()));
        assertEquals(ErrorCode.FINANCE_PAYROLL_RUN_IN_PROGRESS.getCode(), ex.getCode());
    }

    // ==================== I-4 结果分类 ====================

    @Test
    @DisplayName("I-4 成功 → SUCCESS + generatedCount，终态占位 claim_key=month，提交后触发成功钩子")
    void trigger_success() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1, 1, "00:00")));
        when(txHandler.executeGenerate(STATION_ID, MONTH)).thenReturn(generated(3));

        PayrollRunVO vo = service.trigger(triggerRequest());

        assertEquals(PayrollRunStatus.SUCCESS.name(), vo.getStatus());
        assertEquals(3, vo.getGeneratedCount());
        verify(txHandler).finish(eq(1L), eq(PayrollRunStatus.SUCCESS.name()), eq(MONTH),
                isNull(), isNull(), eq(3), isNull(), any());
        verify(notifier).onSucceeded(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Q6 自动提交：生成后逐单 submit（落 PENDING_APPROVAL），type 7 仅推已提交单据")
    void trigger_success_autoSubmitsGeneratedBills() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1, 1, "00:00")));
        when(txHandler.executeGenerate(STATION_ID, MONTH)).thenReturn(generatedWithIds(101L, 102L));

        PayrollRunVO vo = service.trigger(triggerRequest());

        assertEquals(PayrollRunStatus.SUCCESS.name(), vo.getStatus());
        // 逐单独立事务提交（复用 PayrollService.submit → PENDING_APPROVAL）
        verify(txHandler).submitOne(101L);
        verify(txHandler).submitOne(102L);
        assertEquals(2, vo.getSubmittedCount());
        assertEquals(0, vo.getSkippedCount());
        // type 7 只覆盖确已待审的单据
        verify(notifier).onSucceeded(any(), eq(List.of(101L, 102L)), eq(2));
    }

    @Test
    @DisplayName("Q6 部分失败：失败单保持 DRAFT、写跳过失痕、计入 skippedCount，且不回滚已生成/已提交单据")
    void trigger_partialSubmitFailure_isolatedPerBill() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1, 1, "00:00")));
        when(txHandler.executeGenerate(STATION_ID, MONTH)).thenReturn(generatedWithIds(101L, 102L));
        // 102 提交失败（状态不允许等）
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID,
                "当前状态不允许该操作")).when(txHandler).submitOne(102L);

        PayrollRunVO vo = service.trigger(triggerRequest());

        // 生成不回滚：仍为 SUCCESS 且 generatedCount 保持
        assertEquals(PayrollRunStatus.SUCCESS.name(), vo.getStatus());
        assertEquals(2, vo.getGeneratedCount());
        // 已成功的 101 不被连累
        verify(txHandler).submitOne(101L);
        assertEquals(1, vo.getSubmittedCount());
        assertEquals(1, vo.getSkippedCount());
        // 失败单留痕（SYSTEM 主体），type 7 只推 101
        verify(txHandler).recordSubmitSkipped(eq(102L), anyString());
        verify(notifier).onSucceeded(any(), eq(List.of(101L)), eq(1));
    }

    @Test
    @DisplayName("I-4 命中 9405 → 映射为 SKIPPED(BLOCKED_9405) 结果（非错误）")
    void trigger_9405_skipped() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1, 1, "00:00")));
        when(txHandler.executeGenerate(STATION_ID, MONTH))
                .thenThrow(new BusinessException(ErrorCode.FINANCE_PAYROLL_GENERATED, "该月工资单已提交审核或已发布（PY001）"));

        PayrollRunVO vo = service.trigger(triggerRequest());

        assertEquals(PayrollRunStatus.SKIPPED.name(), vo.getStatus());
        assertEquals(PayrollRunSkipCode.BLOCKED_9405, vo.getSkipCode());
        verify(txHandler).finish(eq(1L), eq(PayrollRunStatus.SKIPPED.name()), eq(MONTH),
                eq(PayrollRunSkipCode.BLOCKED_9405), anyString(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("I-4 配置非法（时间格式）→ SKIPPED(CONFIG_INVALID)")
    void trigger_configInvalid_skipped() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1, 1, "9:00")));

        PayrollRunVO vo = service.trigger(triggerRequest());

        assertEquals(PayrollRunStatus.SKIPPED.name(), vo.getStatus());
        assertEquals(PayrollRunSkipCode.CONFIG_INVALID, vo.getSkipCode());
    }

    @Test
    @DisplayName("I-4 生成失败 → FAILED，释放跨日占位（claim_key=NULL），触发失败钩子")
    void trigger_failure_releasesClaim() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1, 1, "00:00")));
        when(txHandler.executeGenerate(STATION_ID, MONTH)).thenThrow(new IllegalStateException("boom"));

        PayrollRunVO vo = service.trigger(triggerRequest());

        assertEquals(PayrollRunStatus.FAILED.name(), vo.getStatus());
        assertTrue(vo.getFailReason().contains("IllegalStateException"));
        verify(txHandler).finish(eq(1L), eq(PayrollRunStatus.FAILED.name()), isNull(),
                isNull(), isNull(), isNull(), anyString(), any());
        verify(notifier).onFailed(any());
    }

    // ==================== executeIfDue 判定链 ====================

    @Test
    @DisplayName("到点：未到算薪日 → 不产生运行记录（返回 null，零 claim）")
    void executeIfDue_beforeDueDay_returnsNull() {
        PayrollRunVO vo = service.executeIfDue(setting(1, 15, "09:00"),
                ZonedDateTime.of(2026, 9, 10, 10, 0, 0, 0, ZONE));
        assertNull(vo);
        verify(txHandler, never()).claim(any());
    }

    @Test
    @DisplayName("到点：当日尝试钟点未到 → 返回 null（复-3 统一 catch-up-time-of-day）")
    void executeIfDue_beforeAttemptTime_returnsNull() {
        PayrollRunVO vo = service.executeIfDue(setting(1, 10, "23:00"),
                ZonedDateTime.of(2026, 9, 10, 9, 0, 0, 0, ZONE));
        assertNull(vo);
        verify(txHandler, never()).claim(any());
    }

    @Test
    @DisplayName("到点：已占位（claim 短路）→ 返回 null")
    void executeIfDue_occupied_returnsNull() {
        when(payrollRunMapper.selectCount(any())).thenReturn(1L);
        PayrollRunVO vo = service.executeIfDue(setting(1, 1, "00:00"),
                ZonedDateTime.of(2026, 9, 2, 9, 0, 0, 0, ZONE));
        assertNull(vo);
        verify(txHandler, never()).claim(any());
    }

    @Test
    @DisplayName("到点：当日已尝试 → 返回 null（uk_attempt）")
    void executeIfDue_attempted_returnsNull() {
        when(payrollRunMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);
        PayrollRunVO vo = service.executeIfDue(setting(1, 1, "00:00"),
                ZonedDateTime.of(2026, 9, 2, 9, 0, 0, 0, ZONE));
        assertNull(vo);
        verify(txHandler, never()).claim(any());
    }

    @Test
    @DisplayName("到点：满足全部条件 → 执行；晚于 dueAt 日标记 CATCH_UP")
    void executeIfDue_due_runsCatchUp() {
        when(txHandler.executeGenerate(STATION_ID, "2026-09")).thenReturn(generated(1));
        PayrollRunVO vo = service.executeIfDue(setting(1, 1, "00:00"),
                ZonedDateTime.of(2026, 9, 2, 9, 0, 0, 0, ZONE));
        assertNotNull(vo);
        assertEquals(PayrollRunStatus.SUCCESS.name(), vo.getStatus());
        assertEquals(PayrollRunTriggerType.CATCH_UP.name(), vo.getTriggerType());
        assertEquals(LocalDate.of(2026, 9, 2), vo.getAttemptDate());
    }

    // ==================== 僵死回收 ====================

    @Test
    @DisplayName("僵死回收：开关关闭 → 不扫描、不告警")
    void reclaimStale_disabled() {
        scheduleProperties.setStaleReclaimEnabled(false);
        assertEquals(0, service.reclaimStale(ZonedDateTime.now(ZONE)));
        verify(txHandler, never()).reclaimStale(any(), any());
        verify(notifier, never()).onStaleReclaimed(any());
    }

    @Test
    @DisplayName("僵死回收：命中即触发失败告警钩子")
    void reclaimStale_enabled_triggersNotifier() {
        scheduleProperties.setStaleReclaimEnabled(true);
        PayrollRun stale = new PayrollRun();
        stale.setId(7L);
        stale.setStationId(STATION_ID);
        stale.setTargetMonth("2026-08");
        when(txHandler.reclaimStale(any(), any())).thenReturn(List.of(stale));
        assertEquals(1, service.reclaimStale(ZonedDateTime.now(ZONE)));
        verify(notifier).onStaleReclaimed(any());
    }

    // ==================== I-5 ====================

    @Test
    @DisplayName("I-5 按 id 精确查询未命中 → 9409")
    void list_idMissing_throws9409() {
        when(payrollRunMapper.selectById(anyLong())).thenReturn(null);
        PayrollRunQuery query = new PayrollRunQuery();
        query.setId(999L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.list(query));
        assertEquals(ErrorCode.FINANCE_PAYROLL_RUN_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("I-5 分页查询 → 补 operatorName")
    void list_mapsOperatorName() {
        PayrollRun run = new PayrollRun();
        run.setId(1L);
        run.setStationId(STATION_ID);
        run.setTargetMonth(MONTH);
        run.setStatus(PayrollRunStatus.SUCCESS.name());
        run.setTriggerType(PayrollRunTriggerType.MANUAL.name());
        run.setOperatorId(11L);
        when(payrollRunMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<PayrollRun> page = inv.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(run));
            return page;
        });
        Employee operator = new Employee();
        operator.setId(11L);
        operator.setRealName("张三");
        when(employeeMapper.selectBatchIds(any())).thenReturn(List.of(operator));

        var result = service.list(new PayrollRunQuery());

        assertEquals(1, result.getList().size());
        assertEquals("张三", result.getList().get(0).getOperatorName());
    }

    // ==================== helpers ====================

    private PayrollRunTriggerRequest triggerRequest() {
        PayrollRunTriggerRequest request = new PayrollRunTriggerRequest();
        request.setStationId(STATION_ID);
        request.setMonth(MONTH);
        return request;
    }

    private Station station() {
        Station station = new Station();
        station.setId(STATION_ID);
        station.setStationName("一号驿站");
        station.setStatus(1);
        return station;
    }

    private StationPayrollSetting setting(int enabled, int day, String time) {
        StationPayrollSetting setting = new StationPayrollSetting();
        setting.setId(1L);
        setting.setStationId(STATION_ID);
        setting.setEnabled(enabled);
        setting.setPayrollDay(day);
        setting.setPayrollTime(time);
        setting.setNotifyEnabled(1);
        return setting;
    }

    private PayrollGenerateVO generated(int created) {
        PayrollGenerateVO vo = new PayrollGenerateVO();
        vo.setCreated(created);
        return vo;
    }

    /** 生成结果含单据 id 列表（供自动提交断言使用） */
    private PayrollGenerateVO generatedWithIds(Long... payrollIds) {
        PayrollGenerateVO vo = new PayrollGenerateVO();
        vo.setCreated(payrollIds.length);
        vo.setPayrollIds(List.of(payrollIds));
        return vo;
    }
}
