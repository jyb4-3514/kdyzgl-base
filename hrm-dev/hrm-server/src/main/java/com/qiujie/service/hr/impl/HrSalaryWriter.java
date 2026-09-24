package com.qiujie.service.hr.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.entity.HrAllowance;
import com.qiujie.entity.HrProfile;
import com.qiujie.entity.HrSalary;
import com.qiujie.entity.HrSalaryLog;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.HrProfileMapper;
import com.qiujie.mapper.HrSalaryLogMapper;
import com.qiujie.mapper.HrSalaryMapper;
import com.qiujie.service.hr.support.HrConstants;
import com.qiujie.service.hr.support.HrSalaryCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 定薪写入器（档案定薪保存 + 入职定薪步骤共用）。
 * <p>
 * 为什么抽成独立 Bean 而非各 Service 各写一遍：Mock {@code saveSalary} 被
 * {@code salaryUpdate}（调薪）与 {@code salaryForFlow}（入职定薪）两处复用，一份口径（覆盖当前 + 追加留痕）。
 * 集中一处可保证「计费口径」只落一份实现（口径红线：不得出现第二份合计公式）。
 * <p>
 * 校验与 Mock 一致：薪资档案缺失 → 9305；员工已离职 → 9302。
 */
@Service
@RequiredArgsConstructor
public class HrSalaryWriter {

    private final HrSalaryMapper hrSalaryMapper;
    private final HrSalaryLogMapper hrSalaryLogMapper;
    private final HrProfileMapper hrProfileMapper;

    /**
     * 定薪载荷（null 字段 = 保持现值，对齐 Mock {@code payload[key] !== undefined} 语义）。
     */
    public record Payload(BigDecimal basicSalary,
                          BigDecimal postSalary,
                          BigDecimal performanceBase,
                          List<HrAllowance> allowances,
                          LocalDate effectiveDate,
                          String reason) {
    }

    /**
     * 覆盖当前定薪并追加一条留痕（只增不改）。
     *
     * @param changeType {@link HrConstants#CHANGE_TYPE_ENTRY} 或 {@link HrConstants#CHANGE_TYPE_ADJUST}
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(Long employeeId, Payload payload, Long operatorId, String operatorName, String changeType) {
        HrSalary salary = selectByEmployeeId(employeeId);
        if (salary == null) {
            throw new BusinessException(ErrorCode.HR_SALARY_NOT_EXISTS);
        }
        if (isResigned(employeeId)) {
            throw new BusinessException(ErrorCode.HR_EMPLOYEE_RESIGNED);
        }

        BigDecimal basic = payload.basicSalary() != null ? payload.basicSalary() : salary.getBasicSalary();
        BigDecimal post = payload.postSalary() != null ? payload.postSalary() : salary.getPostSalary();
        BigDecimal performance = payload.performanceBase() != null
                ? payload.performanceBase() : salary.getPerformanceBase();
        List<HrAllowance> allowances = payload.allowances() != null
                ? copyAllowances(payload.allowances()) : copyAllowances(salary.getAllowances());

        BigDecimal allowancesTotal = HrSalaryCalculator.allowancesTotal(allowances);
        BigDecimal totalSalary = HrSalaryCalculator.total(basic, post, performance, allowancesTotal);
        LocalDate effectiveDate = payload.effectiveDate() != null ? payload.effectiveDate() : LocalDate.now();

        HrSalary update = new HrSalary();
        update.setId(salary.getId());
        update.setBasicSalary(basic);
        update.setPostSalary(post);
        update.setPerformanceBase(performance);
        update.setAllowances(allowances);
        update.setAllowancesTotal(allowancesTotal);
        update.setTotalSalary(totalSalary);
        update.setEffectiveDate(effectiveDate);
        hrSalaryMapper.updateById(update);

        HrSalaryLog log = new HrSalaryLog();
        log.setEmployeeId(employeeId);
        log.setChangeType(changeType);
        log.setBasicSalary(basic);
        log.setPostSalary(post);
        log.setPerformanceBase(performance);
        log.setAllowances(copyAllowances(allowances));
        log.setAllowancesTotal(allowancesTotal);
        log.setTotalSalary(totalSalary);
        log.setEffectiveDate(effectiveDate);
        log.setReason(payload.reason() != null
                ? payload.reason()
                : (HrConstants.CHANGE_TYPE_ENTRY.equals(changeType) ? "入职定薪" : "定薪调整"));
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        hrSalaryLogMapper.insert(log);
    }

    /** 离职判定真源：hr_profile.leave_date（非 employee.status） */
    public boolean isResigned(Long employeeId) {
        HrProfile profile = selectProfile(employeeId);
        return profile != null && profile.getLeaveDate() != null;
    }

    /** 按员工取当前定薪（1:1，脏数据取首条） */
    public HrSalary selectByEmployeeId(Long employeeId) {
        List<HrSalary> list = hrSalaryMapper.selectList(
                new LambdaQueryWrapper<HrSalary>().eq(HrSalary::getEmployeeId, employeeId));
        return list.isEmpty() ? null : list.get(0);
    }

    private HrProfile selectProfile(Long employeeId) {
        List<HrProfile> list = hrProfileMapper.selectList(
                new LambdaQueryWrapper<HrProfile>().eq(HrProfile::getEmployeeId, employeeId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 深拷贝津贴项：JSON 列元素变异会影响入参对象，留痕需独立快照 */
    private List<HrAllowance> copyAllowances(List<HrAllowance> allowances) {
        List<HrAllowance> copy = new ArrayList<>();
        if (allowances != null) {
            for (HrAllowance item : allowances) {
                if (item != null) {
                    copy.add(new HrAllowance(item.getKey(), item.getName(), item.getAmount()));
                }
            }
        }
        return copy;
    }
}
