package com.qiujie.service.hr.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.qiujie.common.PageResult;
import com.qiujie.dto.hr.HrEmployeeQuery;
import com.qiujie.dto.hr.HrProfileUpdateRequest;
import com.qiujie.dto.hr.HrSalaryUpdateRequest;
import com.qiujie.dto.hr.SalaryAllowanceItem;
import com.qiujie.entity.Department;
import com.qiujie.entity.Employee;
import com.qiujie.entity.HrAllowance;
import com.qiujie.entity.HrProfile;
import com.qiujie.entity.HrSalary;
import com.qiujie.entity.HrSalaryLog;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.HrProfileMapper;
import com.qiujie.mapper.HrSalaryLogMapper;
import com.qiujie.mapper.HrSalaryMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.hr.HrProfileService;
import com.qiujie.service.hr.support.HrConstants;
import com.qiujie.service.hr.support.HrProfileValidator;
import com.qiujie.service.hr.support.HrSalaryValidator;
import com.qiujie.util.DesensitizeUtil;
import com.qiujie.util.UserContext;
import com.qiujie.vo.hr.HrProfileDetailVO;
import com.qiujie.vo.hr.HrProfileVO;
import com.qiujie.vo.hr.HrSalaryDetailVO;
import com.qiujie.vo.hr.HrSalaryLogVO;
import com.qiujie.vo.hr.HrSalaryVO;
import com.qiujie.vo.hr.SalaryAllowanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 人事档案与定薪服务实现（对齐 Mock {@code hrStore} 档案段/定薪段 + {@code routes/hr.js} 校验）。
 * <p>
 * 校验文案与 Mock 逐字一致（前端按文案对齐）；出参对 {@code phone/emergencyContactPhone/bankAccount}
 * 一律脱敏（需求 8）。
 * <p>
 * 列表仅返回「已建档/已定薪」的员工，且不按 {@code employee.status} 过滤（对齐 Mock {@code activeEmployees}
 * 只排除逻辑删除的语义：禁用/在册员工其档案仍应可管理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrProfileServiceImpl implements HrProfileService {

    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final StationMapper stationMapper;
    private final HrProfileMapper hrProfileMapper;
    private final HrSalaryMapper hrSalaryMapper;
    private final HrSalaryLogMapper hrSalaryLogMapper;
    private final HrSalaryWriter hrSalaryWriter;

    // ==================== 人事档案 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<HrProfileVO> listProfiles(HrEmployeeQuery query) {
        List<Employee> employees = selectEmployees(query);
        if (employees.isEmpty()) {
            return PageResult.of(0, pageNum(query), pageSize(query), List.of());
        }
        Map<Long, HrProfile> profiles = loadProfiles(employees);
        Map<Long, String> deptNames = loadDeptNames(employees);
        Map<Long, String> stationNames = loadStationNames(employees);
        List<HrProfileVO> rows = new ArrayList<>();
        for (Employee employee : employees) {
            HrProfile profile = profiles.get(employee.getId());
            if (profile != null) {
                rows.add(toProfileVO(profile, employee, deptNames, stationNames));
            }
        }
        return paginate(rows, query);
    }

    @Override
    @Transactional(readOnly = true)
    public HrProfileDetailVO profileDetail(Long employeeId) {
        checkAccess(employeeId, "无权查看他人人事档案");
        HrProfile profile = findProfile(employeeId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.HR_PROFILE_NOT_EXISTS);
        }
        Employee employee = employeeMapper.selectById(employeeId);
        HrProfileDetailVO vo = new HrProfileDetailVO();
        HrProfileVO base = toProfileVO(profile, employee);
        BeanUtils.copyProperties(base, vo);
        HrSalary salary = hrSalaryWriter.selectByEmployeeId(employeeId);
        vo.setSalary(salary == null ? null : toSalaryVO(salary, employeeName(employee)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrProfileVO updateProfile(Long employeeId, HrProfileUpdateRequest request) {
        String error = HrProfileValidator.validate(request);
        if (error != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error);
        }
        HrProfile profile = findProfile(employeeId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.HR_PROFILE_NOT_EXISTS);
        }
        if (profile.getLeaveDate() != null) {
            throw new BusinessException(ErrorCode.HR_EMPLOYEE_RESIGNED);
        }
        // 白名单写入：DTO 不含 employeeId/leaveDate，天然防止越权改归属与伪造离职（对齐 Mock WRITABLE）
        // 仅写「显式传入（非 null）」字段：空请求体不会误清空敏感档案；需清空某字段时前端传空串 ""
        // （差异：Mock 可用显式 null 清空字段，Java 无法区分「未传」与「显式 null」，与 P4 同类处理一致）
        HrProfileUpdateRequest safe = request == null ? new HrProfileUpdateRequest() : request;
        LambdaUpdateWrapper<HrProfile> wrapper = new LambdaUpdateWrapper<HrProfile>()
                .eq(HrProfile::getId, profile.getId());
        setIfPresent(wrapper, HrProfile::getEducation, safe.getEducation());
        setIfPresent(wrapper, HrProfile::getContractType, safe.getContractType());
        setIfPresent(wrapper, HrProfile::getContractStart, parseDate(safe.getContractStart()));
        setIfPresent(wrapper, HrProfile::getContractEnd, parseDate(safe.getContractEnd()));
        setIfPresent(wrapper, HrProfile::getProbationMonths, safe.getProbationMonths());
        setIfPresent(wrapper, HrProfile::getProbationEnd, parseDate(safe.getProbationEnd()));
        setIfPresent(wrapper, HrProfile::getRegularDate, parseDate(safe.getRegularDate()));
        setIfPresent(wrapper, HrProfile::getSocialSecurityBase, safe.getSocialSecurityBase());
        setIfPresent(wrapper, HrProfile::getEmergencyContactName, safe.getEmergencyContactName());
        setIfPresent(wrapper, HrProfile::getEmergencyContactPhone, safe.getEmergencyContactPhone());
        setIfPresent(wrapper, HrProfile::getEmergencyContactRelation, safe.getEmergencyContactRelation());
        setIfPresent(wrapper, HrProfile::getBankName, safe.getBankName());
        setIfPresent(wrapper, HrProfile::getBankAccount, safe.getBankAccount());
        // wrapper 更新不走实体自动填充，手动维护 update_time（决策 D8）
        wrapper.set(HrProfile::getUpdateTime, LocalDateTime.now());
        hrProfileMapper.update(null, wrapper);

        HrProfile updated = findProfile(employeeId);
        return toProfileVO(updated, employeeMapper.selectById(employeeId));
    }

    // ==================== 定薪档案 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<HrSalaryVO> listSalaries(HrEmployeeQuery query) {
        List<Employee> employees = selectEmployees(query);
        if (employees.isEmpty()) {
            return PageResult.of(0, pageNum(query), pageSize(query), List.of());
        }
        Map<Long, HrSalary> salaries = loadSalaries(employees);
        List<HrSalaryVO> rows = new ArrayList<>();
        for (Employee employee : employees) {
            HrSalary salary = salaries.get(employee.getId());
            if (salary != null) {
                rows.add(toSalaryVO(salary, employeeName(employee)));
            }
        }
        return paginate(rows, query);
    }

    @Override
    @Transactional(readOnly = true)
    public HrSalaryDetailVO salaryDetail(Long employeeId) {
        checkAccess(employeeId, "无权查看他人定薪档案");
        return buildSalaryDetail(employeeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrSalaryDetailVO updateSalary(Long employeeId, HrSalaryUpdateRequest request) {
        HrSalaryUpdateRequest safe = request == null ? new HrSalaryUpdateRequest() : request;
        String error = HrSalaryValidator.validate(safe.getBasicSalary(), safe.getPostSalary(),
                safe.getPerformanceBase(), safe.getAllowances(), safe.getEffectiveDate(), safe.getReason(), true);
        if (error != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error);
        }
        // 保存定薪 = 覆盖当前档案 + 追加调薪留痕（HrSalaryWriter 与入职定薪共用同一份口径）
        hrSalaryWriter.save(employeeId,
                new HrSalaryWriter.Payload(safe.getBasicSalary(), safe.getPostSalary(), safe.getPerformanceBase(),
                        toAllowances(safe.getAllowances()), parseDate(safe.getEffectiveDate()), safe.getReason()),
                UserContext.getUserId(), currentOperatorName(), HrConstants.CHANGE_TYPE_ADJUST);
        return buildSalaryDetail(employeeId);
    }

    // ==================== 私有：查询 ====================

    /** 按员工筛选（仅未删除，对齐 Mock {@code activeEmployees}：不按 status 过滤） */
    private List<Employee> selectEmployees(HrEmployeeQuery query) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId, Employee::getRealName, Employee::getUsername, Employee::getPhone,
                Employee::getDeptId, Employee::getStationId, Employee::getPosition, Employee::getEntryDate);
        if (query.getDeptId() != null) {
            wrapper.eq(Employee::getDeptId, query.getDeptId());
        }
        if (query.getStationId() != null) {
            wrapper.eq(Employee::getStationId, query.getStationId());
        }
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        if (!keyword.isEmpty()) {
            String escaped = escapeLike(keyword);
            wrapper.and(w -> w.like(Employee::getRealName, escaped).or().like(Employee::getUsername, escaped));
        }
        wrapper.orderByAsc(Employee::getId);
        return employeeMapper.selectList(wrapper);
    }

    private Map<Long, HrProfile> loadProfiles(List<Employee> employees) {
        Map<Long, HrProfile> result = new HashMap<>();
        for (HrProfile profile : hrProfileMapper.selectList(
                new LambdaQueryWrapper<HrProfile>().in(HrProfile::getEmployeeId, employeeIds(employees)))) {
            result.putIfAbsent(profile.getEmployeeId(), profile);
        }
        return result;
    }

    private Map<Long, HrSalary> loadSalaries(List<Employee> employees) {
        Map<Long, HrSalary> result = new HashMap<>();
        for (HrSalary salary : hrSalaryMapper.selectList(
                new LambdaQueryWrapper<HrSalary>().in(HrSalary::getEmployeeId, employeeIds(employees)))) {
            result.putIfAbsent(salary.getEmployeeId(), salary);
        }
        return result;
    }

    private Map<Long, String> loadDeptNames(List<Employee> employees) {
        Set<Long> ids = new HashSet<>();
        for (Employee employee : employees) {
            if (employee.getDeptId() != null) {
                ids.add(employee.getDeptId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Department department : departmentMapper.selectBatchIds(ids)) {
                names.put(department.getId(), department.getDeptName());
            }
        }
        return names;
    }

    private Map<Long, String> loadStationNames(List<Employee> employees) {
        Set<Long> ids = new HashSet<>();
        for (Employee employee : employees) {
            if (employee.getStationId() != null) {
                ids.add(employee.getStationId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Station station : stationMapper.selectBatchIds(ids)) {
                names.put(station.getId(), station.getStationName());
            }
        }
        return names;
    }

    private Set<Long> employeeIds(List<Employee> employees) {
        Set<Long> ids = new HashSet<>();
        for (Employee employee : employees) {
            ids.add(employee.getId());
        }
        return ids;
    }

    private HrProfile findProfile(Long employeeId) {
        List<HrProfile> list = hrProfileMapper.selectList(
                new LambdaQueryWrapper<HrProfile>().eq(HrProfile::getEmployeeId, employeeId));
        return list.isEmpty() ? null : list.get(0);
    }

    private HrSalaryDetailVO buildSalaryDetail(Long employeeId) {
        HrSalary salary = hrSalaryWriter.selectByEmployeeId(employeeId);
        if (salary == null) {
            throw new BusinessException(ErrorCode.HR_SALARY_NOT_EXISTS);
        }
        Employee employee = employeeMapper.selectById(employeeId);
        HrSalaryDetailVO vo = new HrSalaryDetailVO();
        vo.setCurrent(toSalaryVO(salary, employeeName(employee)));
        vo.setHistories(loadHistories(employeeId));
        return vo;
    }

    /** 调薪留痕按生效日期降序（对齐 Mock），同一日期按 id 倒序稳定 */
    private List<HrSalaryLogVO> loadHistories(Long employeeId) {
        List<HrSalaryLog> logs = hrSalaryLogMapper.selectList(new LambdaQueryWrapper<HrSalaryLog>()
                .eq(HrSalaryLog::getEmployeeId, employeeId)
                .orderByDesc(HrSalaryLog::getEffectiveDate)
                .orderByDesc(HrSalaryLog::getId));
        List<HrSalaryLogVO> list = new ArrayList<>(logs.size());
        for (HrSalaryLog log : logs) {
            list.add(toSalaryLogVO(log));
        }
        return list;
    }

    /** 越权判定（对齐 Mock {@code canAccessEmployee}：ADMIN 或本人，越权 403） */
    private void checkAccess(Long employeeId, String message) {
        boolean admin = RoleEnum.isAdmin(UserContext.getRole());
        boolean self = Objects.equals(UserContext.getUserId(), employeeId);
        if (!admin && !self) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }

    private String currentOperatorName() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        Employee operator = employeeMapper.selectById(userId);
        return operator == null ? null : operator.getRealName();
    }

    private String employeeName(Employee employee) {
        return employee == null ? null : employee.getRealName();
    }

    // ==================== 私有：转换 ====================

    /** 单条场景：按归属 id 直接查名称（列表场景请用预载 map 重载，避免 N+1） */
    private HrProfileVO toProfileVO(HrProfile profile, Employee employee) {
        Map<Long, String> deptNames = new HashMap<>();
        Map<Long, String> stationNames = new HashMap<>();
        if (employee != null) {
            if (employee.getDeptId() != null) {
                Department department = departmentMapper.selectById(employee.getDeptId());
                if (department != null) {
                    deptNames.put(department.getId(), department.getDeptName());
                }
            }
            if (employee.getStationId() != null) {
                Station station = stationMapper.selectById(employee.getStationId());
                if (station != null) {
                    stationNames.put(station.getId(), station.getStationName());
                }
            }
        }
        return toProfileVO(profile, employee, deptNames, stationNames);
    }

    private HrProfileVO toProfileVO(HrProfile profile, Employee employee,
                                    Map<Long, String> deptNames, Map<Long, String> stationNames) {
        HrProfileVO vo = new HrProfileVO();
        vo.setEmployeeId(profile.getEmployeeId());
        vo.setEmployeeName(employeeName(employee));
        vo.setUsername(employee == null ? null : employee.getUsername());
        vo.setPhone(employee == null ? null : DesensitizeUtil.maskPhone(employee.getPhone()));
        Long deptId = employee == null ? null : employee.getDeptId();
        vo.setDeptName(deptId == null ? null : deptNames.get(deptId));
        Long stationId = employee == null ? null : employee.getStationId();
        vo.setStationName(stationId == null ? null : stationNames.get(stationId));
        // 岗位出参补齐（取值 店员 / 站长 / 管理员；未登记为 null）
        vo.setPosition(employee == null ? null : employee.getPosition());
        vo.setEntryDate(employee == null ? null : employee.getEntryDate());
        vo.setEducation(profile.getEducation());
        vo.setEducationLabel(HrConstants.educationLabel(profile.getEducation()));
        vo.setContractType(profile.getContractType());
        vo.setContractTypeLabel(HrConstants.contractTypeLabel(profile.getContractType()));
        vo.setContractStart(profile.getContractStart());
        vo.setContractEnd(profile.getContractEnd());
        vo.setProbationMonths(profile.getProbationMonths());
        vo.setProbationEnd(profile.getProbationEnd());
        vo.setRegularDate(profile.getRegularDate());
        vo.setSocialSecurityBase(profile.getSocialSecurityBase());
        vo.setEmergencyContactName(profile.getEmergencyContactName());
        vo.setEmergencyContactPhone(DesensitizeUtil.maskPhone(profile.getEmergencyContactPhone()));
        vo.setEmergencyContactRelation(profile.getEmergencyContactRelation());
        vo.setBankName(profile.getBankName());
        vo.setBankAccount(DesensitizeUtil.maskBankAccount(profile.getBankAccount()));
        vo.setLeaveDate(profile.getLeaveDate());
        vo.setCreateTime(profile.getCreateTime());
        vo.setUpdateTime(profile.getUpdateTime());
        return vo;
    }

    private HrSalaryVO toSalaryVO(HrSalary salary, String employeeName) {
        HrSalaryVO vo = new HrSalaryVO();
        vo.setEmployeeId(salary.getEmployeeId());
        vo.setEmployeeName(employeeName);
        vo.setBasicSalary(salary.getBasicSalary());
        vo.setPostSalary(salary.getPostSalary());
        vo.setPerformanceBase(salary.getPerformanceBase());
        vo.setAllowances(toAllowanceVOs(salary.getAllowances()));
        vo.setAllowancesTotal(salary.getAllowancesTotal());
        vo.setTotalSalary(salary.getTotalSalary());
        vo.setEffectiveDate(salary.getEffectiveDate());
        vo.setUpdateTime(salary.getUpdateTime());
        return vo;
    }

    private HrSalaryLogVO toSalaryLogVO(HrSalaryLog log) {
        HrSalaryLogVO vo = new HrSalaryLogVO();
        vo.setId(log.getId());
        vo.setEffectiveDate(log.getEffectiveDate());
        vo.setChangeType(log.getChangeType());
        vo.setChangeTypeLabel(HrConstants.changeTypeLabel(log.getChangeType()));
        vo.setBasicSalary(log.getBasicSalary());
        vo.setPostSalary(log.getPostSalary());
        vo.setPerformanceBase(log.getPerformanceBase());
        vo.setAllowances(toAllowanceVOs(log.getAllowances()));
        vo.setAllowancesTotal(log.getAllowancesTotal());
        vo.setTotalSalary(log.getTotalSalary());
        vo.setReason(log.getReason());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(log.getOperatorName());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private List<SalaryAllowanceVO> toAllowanceVOs(List<HrAllowance> allowances) {
        List<SalaryAllowanceVO> list = new ArrayList<>();
        if (allowances != null) {
            for (HrAllowance item : allowances) {
                if (item == null) {
                    continue;
                }
                SalaryAllowanceVO vo = new SalaryAllowanceVO();
                vo.setKey(item.getKey());
                vo.setName(item.getName());
                vo.setAmount(item.getAmount());
                list.add(vo);
            }
        }
        return list;
    }

    /** 入参津贴 → 领域值对象（trim 名称/键，对齐 Mock）；入参为 null 表示「保持现值」 */
    private List<HrAllowance> toAllowances(List<SalaryAllowanceItem> items) {
        if (items == null) {
            return null;
        }
        List<HrAllowance> list = new ArrayList<>(items.size());
        for (SalaryAllowanceItem item : items) {
            String name = item.getName() == null ? null : item.getName().trim();
            String key = item.getKey() == null ? null : item.getKey().trim();
            list.add(new HrAllowance(key, name, item.getAmount()));
        }
        return list;
    }

    // ==================== 私有：通用 ====================

    private int pageNum(HrEmployeeQuery query) {
        return query.getPageNum() == null ? 1 : query.getPageNum();
    }

    private int pageSize(HrEmployeeQuery query) {
        return query.getPageSize() == null ? 10 : query.getPageSize();
    }

    private <T> PageResult<T> paginate(List<T> rows, HrEmployeeQuery query) {
        int pageNum = pageNum(query);
        int pageSize = pageSize(query);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        List<T> slice;
        if (from >= rows.size()) {
            slice = List.of();
        } else {
            slice = new ArrayList<>(rows.subList(from, Math.min(rows.size(), from + pageSize)));
        }
        return PageResult.of(rows.size(), pageNum, pageSize, slice);
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text.trim());
    }

    /** LIKE 通配符转义（反斜杠 / % / _） */
    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 仅当值非 null 时写入该列（null = 未传，不改动；空串 "" = 显式清空） */
    private <T> void setIfPresent(LambdaUpdateWrapper<HrProfile> wrapper,
                                  SFunction<HrProfile, T> column, T value) {
        if (value != null) {
            wrapper.set(column, value);
        }
    }
}
