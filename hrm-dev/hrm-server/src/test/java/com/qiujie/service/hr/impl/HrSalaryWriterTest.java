package com.qiujie.service.hr.impl;

import com.qiujie.entity.HrAllowance;
import com.qiujie.entity.HrProfile;
import com.qiujie.entity.HrSalary;
import com.qiujie.entity.HrSalaryLog;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.HrProfileMapper;
import com.qiujie.mapper.HrSalaryLogMapper;
import com.qiujie.mapper.HrSalaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定薪写入器单测：覆盖「无档案 9305 / 已离职 9302 / 合计与留痕」边界（Mockito 单测，无需 DB）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HrSalaryWriterTest {

    private HrSalaryMapper salaryMapper;
    private HrSalaryLogMapper logMapper;
    private HrProfileMapper profileMapper;
    private HrSalaryWriter writer;

    @BeforeEach
    void setUp() {
        salaryMapper = mock(HrSalaryMapper.class);
        logMapper = mock(HrSalaryLogMapper.class);
        profileMapper = mock(HrProfileMapper.class);
        writer = new HrSalaryWriter(salaryMapper, logMapper, profileMapper);
    }

    private HrSalary salary() {
        HrSalary salary = new HrSalary();
        salary.setId(1L);
        salary.setEmployeeId(9L);
        salary.setBasicSalary(new BigDecimal("6000"));
        salary.setPostSalary(new BigDecimal("2500"));
        salary.setPerformanceBase(new BigDecimal("1500"));
        salary.setAllowances(List.of(new HrAllowance("MEAL", "餐补", new BigDecimal("300"))));
        salary.setAllowancesTotal(new BigDecimal("300"));
        salary.setTotalSalary(new BigDecimal("10300"));
        return salary;
    }

    private HrProfile profile(LocalDate leaveDate) {
        HrProfile profile = new HrProfile();
        profile.setId(1L);
        profile.setEmployeeId(9L);
        profile.setLeaveDate(leaveDate);
        return profile;
    }

    private HrSalaryWriter.Payload payload() {
        return new HrSalaryWriter.Payload(new BigDecimal("7000"), new BigDecimal("2500"),
                new BigDecimal("1500"), List.of(new HrAllowance("MEAL", "餐补", new BigDecimal("300"))),
                LocalDate.of(2026, 10, 1), "年度调薪");
    }

    @Test
    @DisplayName("薪资档案缺失 → 9305")
    void salaryMissing() {
        when(salaryMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> writer.save(9L, payload(), 1L, "管理员", "ADJUST"));
        assertEquals(ErrorCode.HR_SALARY_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("员工已离职（leave_date 非空）→ 9302")
    void resignedEmployee() {
        when(salaryMapper.selectList(any())).thenReturn(List.of(salary()));
        when(profileMapper.selectList(any())).thenReturn(List.of(profile(LocalDate.of(2026, 9, 1))));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> writer.save(9L, payload(), 1L, "管理员", "ADJUST"));
        assertEquals(ErrorCode.HR_EMPLOYEE_RESIGNED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常保存：覆盖当前定薪 + 追加留痕，合计 = 基本+岗位+绩效+津贴")
    void saveOverwritesAndLogs() {
        when(salaryMapper.selectList(any())).thenReturn(List.of(salary()));
        when(profileMapper.selectList(any())).thenReturn(List.of(profile(null)));

        writer.save(9L, payload(), 7L, "张管理", "ADJUST");

        // 当前定薪被覆盖
        verify(salaryMapper).updateById(any(HrSalary.class));
        ArgumentCaptor<HrSalaryLog> captor = ArgumentCaptor.forClass(HrSalaryLog.class);
        verify(logMapper).insert(captor.capture());
        HrSalaryLog log = captor.getValue();
        assertEquals("ADJUST", log.getChangeType());
        assertEquals(new BigDecimal("7000"), log.getBasicSalary());
        assertEquals(new BigDecimal("300"), log.getAllowancesTotal());
        assertEquals(new BigDecimal("11300"), log.getTotalSalary()); // 7000+2500+1500+300
        assertEquals("年度调薪", log.getReason());
        assertEquals(7L, log.getOperatorId());
        assertEquals("张管理", log.getOperatorName());
        assertEquals(LocalDate.of(2026, 10, 1), log.getEffectiveDate());
    }

    @Test
    @DisplayName("入职定薪（ENTRY）无 reason 时默认「入职定薪」；未传津贴保持现值")
    void entryDefaults() {
        when(salaryMapper.selectList(any())).thenReturn(List.of(salary()));
        when(profileMapper.selectList(any())).thenReturn(List.of(profile(null)));

        writer.save(9L, new HrSalaryWriter.Payload(null, null, null, null, null, null),
                1L, "业务管理员", "ENTRY");

        ArgumentCaptor<HrSalaryLog> captor = ArgumentCaptor.forClass(HrSalaryLog.class);
        verify(logMapper).insert(captor.capture());
        HrSalaryLog log = captor.getValue();
        assertEquals("入职定薪", log.getReason());
        // 未传字段保持现值：basic 6000 + post 2500 + perf 1500 + 津贴 300 = 10300
        assertEquals(new BigDecimal("10300"), log.getTotalSalary());
        assertEquals(1, log.getAllowances().size());
    }
}
