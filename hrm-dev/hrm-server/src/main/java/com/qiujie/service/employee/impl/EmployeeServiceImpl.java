package com.qiujie.service.employee.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.common.PageResult;
import com.qiujie.dto.employee.EmployeeCreateRequest;
import com.qiujie.dto.employee.EmployeeImportRow;
import com.qiujie.dto.employee.EmployeeQuery;
import com.qiujie.dto.employee.EmployeeStatusRequest;
import com.qiujie.dto.employee.EmployeeUpdateRequest;
import com.qiujie.dto.employee.PasswordResetRequest;
import com.qiujie.entity.Department;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.auth.support.TrustedDeviceRegistry;
import com.qiujie.service.employee.EmployeeService;
import com.qiujie.service.support.ImportRowValidator;
import com.qiujie.util.DesensitizeUtil;
import com.qiujie.util.SessionUtil;
import com.qiujie.util.UserContext;
import com.qiujie.util.excel.ImportTemplateStyleHandler;
import com.qiujie.vo.employee.EmployeeVO;
import com.qiujie.vo.common.IdVO;
import com.qiujie.vo.employee.ImportErrorVO;
import com.qiujie.vo.employee.ImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 员工服务实现（api.md 4.3）。
 * 唯一性/归属校验均为 Service 层「活跃数据查重」（决策 D7）；
 * 禁用/删除/重置密码均强制下线（requirement.md 5.4）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    /** 导入数据行上限（api.md 4.3.9） */
    private static final int IMPORT_MAX_ROWS = 1000;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 导入模板列（api.md 5.1） */
    private static final List<String> TEMPLATE_HEADERS = List.of(
            "姓名", "登录账号", "手机号", "性别", "部门名称", "驿站名称", "入职日期", "备注");

    /** 导出列（api.md 第 6 章） */
    private static final List<String> EXPORT_HEADERS = List.of(
            "登录账号", "姓名", "手机号", "性别", "部门", "驿站", "角色", "状态",
            "入职日期", "最后登录时间", "创建时间", "备注");

    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final StationMapper stationMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SessionUtil sessionUtil;
    /** 受信设备登记（M4）：禁用 / 删除 / 重置密码属安全事件，须一并使其已信任设备失效 */
    private final TrustedDeviceRegistry trustedDeviceRegistry;

    /** 导入账号统一初始密码（首登强制改密兜底） */
    @Value("${hrm.employee-init-password}")
    private String employeeInitPassword;

    // ==================== 查询 ====================

    @Override
    public PageResult<EmployeeVO> page(EmployeeQuery query) {
        // C-04：分页参数缺省补齐；越界/非法值由 PageQuery 的 @Min/@Max 在绑定阶段拦截为 400，
        // 此处不再钳制（钳制会让前端拿到与契约不符的静默结果）
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        checkStatusParam(query.getStatus());

        Page<Employee> page = employeeMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(query));
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords()));
    }

    @Override
    public EmployeeVO detail(Long id) {
        Employee employee = employeeMapper.selectById(id);
        // selectById 已拼接逻辑删除条件，已删除员工视为不存在
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return toVOList(List.of(employee)).get(0);
    }

    // ==================== 写操作 ====================

    @Override
    public IdVO create(EmployeeCreateRequest request) {
        // 活跃数据查重：账号（1003）/ 手机号（2003）
        if (existsActiveUsername(request.getUsername(), null)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (existsActivePhone(request.getPhone(), null)) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }
        validateDeptAndStation(request.getDeptId(), request.getStationId());
        LocalDate entryDate = parseEntryDate(request.getEntryDate());

        Employee employee = new Employee();
        employee.setUsername(request.getUsername());
        employee.setPassword(passwordEncoder.encode(request.getPassword()));
        employee.setRealName(request.getRealName());
        employee.setPhone(request.getPhone());
        employee.setGender(request.getGender() == null ? 0 : request.getGender());
        employee.setDeptId(request.getDeptId());
        employee.setStationId(request.getStationId());
        employee.setRole(request.getRole());
        employee.setStatus(1);
        employee.setPwdChanged(0); // 首登强制改密
        employee.setEntryDate(entryDate);
        employee.setRemark(request.getRemark());
        employeeMapper.insert(employee);
        return new IdVO(employee.getId());
    }

    @Override
    public void update(Long id, EmployeeUpdateRequest request) {
        Employee exist = employeeMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        // 自我保护：不允许修改自己的角色（2001）
        boolean roleChanged = !exist.getRole().equals(request.getRole());
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id) && roleChanged) {
            throw new BusinessException(ErrorCode.SELF_OPERATION_FORBIDDEN);
        }
        // 最后管理员保护：最后一个可用 ADMIN 不允许角色降级（2002）
        if (roleChanged && isLastAvailableAdmin(exist)) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
        if (existsActivePhone(request.getPhone(), id)) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }
        validateDeptAndStation(request.getDeptId(), request.getStationId());
        LocalDate entryDate = parseEntryDate(request.getEntryDate());

        // 可选字段以请求体为准（null 即清空归属/备注），UpdateWrapper 显式 set 以支持置空
        LambdaUpdateWrapper<Employee> wrapper = new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, id)
                .set(Employee::getRealName, request.getRealName())
                .set(Employee::getPhone, request.getPhone())
                .set(Employee::getGender, request.getGender() == null ? 0 : request.getGender())
                .set(Employee::getDeptId, request.getDeptId())
                .set(Employee::getStationId, request.getStationId())
                .set(Employee::getRole, request.getRole())
                .set(Employee::getEntryDate, entryDate)
                .set(Employee::getRemark, request.getRemark())
                // wrapper 更新不走实体自动填充，手动维护 update_time（决策 D8）
                .set(Employee::getUpdateTime, LocalDateTime.now());
        employeeMapper.update(null, wrapper);
    }

    @Override
    public void delete(Long id) {
        Employee exist = employeeMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException(ErrorCode.SELF_OPERATION_FORBIDDEN);
        }
        if (isLastAvailableAdmin(exist)) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
        employeeMapper.deleteById(id); // 逻辑删除
        forceOffline(id);              // 强制下线
    }

    @Override
    public void changeStatus(Long id, EmployeeStatusRequest request) {
        Employee exist = employeeMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException(ErrorCode.SELF_OPERATION_FORBIDDEN);
        }
        Integer status = request.getStatus();
        // 禁用最后一个可用管理员保护（2002）；启用无此约束
        if (status == 0 && isLastAvailableAdmin(exist)) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
        Employee update = new Employee();
        update.setId(id);
        update.setStatus(status);
        employeeMapper.updateById(update);
        if (status == 0) {
            forceOffline(id); // 禁用即强制下线，存量 Token 立即 401
        }
    }

    @Override
    public void resetPassword(Long id, PasswordResetRequest request) {
        Employee exist = employeeMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            // 重置自己请走「修改本人密码」接口（api.md 4.3.7）
            throw new BusinessException(ErrorCode.SELF_OPERATION_FORBIDDEN);
        }
        Employee update = new Employee();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        update.setPwdChanged(0); // 下次登录强制改密
        employeeMapper.updateById(update);
        forceOffline(id);
    }

    // ==================== Excel 模板 / 导入 / 导出 ====================

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        try {
            setDownloadHeaders(response, "员工导入模板.xlsx");
            List<List<String>> head = TEMPLATE_HEADERS.stream()
                    .map(List::of)
                    .toList();
            Map<Integer, String> comments = new HashMap<>();
            comments.put(0, "必填。员工真实姓名，1-50个字符");
            comments.put(1, "必填。登录账号：以字母开头，4-30位，仅可含字母/数字/下划线；创建后不可修改，不允许与已有账号重复");
            comments.put(2, "必填。11位有效手机号，不允许与已有员工重复。请保持单元格为文本格式填写");
            comments.put(3, "选填。仅支持 男 / 女，留空表示未知");
            comments.put(4, "选填。须为系统中已存在的部门名称（精确匹配）；同名部门不唯一时该行会校验失败");
            comments.put(5, "选填。须为系统中已启用驿站的名称（精确匹配）；驿站不存在或已停用时该行会校验失败");
            comments.put(6, "选填。格式 yyyy-MM-dd（如 2026-09-01）。请保持单元格为文本格式填写");
            comments.put(7, "选填。不超过255个字符");
            // 模板不含示例数据行（api.md 4.3.8）：仅表头 + 批注；批注需 inMemory 完整 POI 支持
            EasyExcel.write(response.getOutputStream())
                    .inMemory(true)
                    .head(head)
                    .registerWriteHandler(new ImportTemplateStyleHandler(comments))
                    .sheet("员工导入")
                    .doWrite(List.of());
        } catch (IOException e) {
            log.error("生成员工导入模板失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importEmployees(MultipartFile file) {
        // 1. 文件级校验：空文件 / 非 .xlsx → 5001（大小超限由 multipart 配置拦截 → 400）
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_INVALID);
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_INVALID);
        }

        List<EmployeeImportRow> rows = readImportRows(file);
        // 过滤全空行（Excel 幽灵行不计入数据）
        rows.removeIf(this::isBlankRow);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_INVALID);
        }
        // 2. 数据行数上限 → 5002
        if (rows.size() > IMPORT_MAX_ROWS) {
            throw new BusinessException(ErrorCode.IMPORT_TOO_MANY_ROWS);
        }

        // 3. 库内活跃数据一次性载入，构造行级校验器
        ImportRowValidator validator = buildValidator();

        // 4. 逐行逐字段校验，收集行级错误（row 为 Excel 真实行号，表头=1）
        List<ImportErrorVO> errors = new ArrayList<>();
        Set<Integer> failedRowNumbers = new LinkedHashSet<>();
        for (EmployeeImportRow row : rows) {
            List<ImportErrorVO> rowErrors = validator.validate(row);
            if (!rowErrors.isEmpty()) {
                failedRowNumbers.add(row.getRowNumber());
                errors.addAll(rowErrors);
            }
        }
        if (!errors.isEmpty()) {
            // 决策 D4：存在任意错误则整体不入库（此时尚未执行任何插入）
            ImportResultVO result = new ImportResultVO();
            result.setTotal(rows.size());
            result.setSuccessCount(0);
            result.setFailCount(failedRowNumbers.size());
            result.setErrors(errors);
            throw new BusinessException(ErrorCode.IMPORT_DATA_ERROR,
                    "导入数据存在校验错误，共 " + failedRowNumbers.size() + " 行失败，全部数据未入库", result);
        }

        // 5. 全部通过 → 单事务批量入库；初始密码统一 → BCrypt 散列只计算 1 次复用（决策 D4 性能要求）
        String initPasswordHash = passwordEncoder.encode(employeeInitPassword);
        for (EmployeeImportRow row : rows) {
            Employee employee = new Employee();
            employee.setUsername(row.getUsername());
            employee.setPassword(initPasswordHash);
            employee.setRealName(row.getRealName());
            employee.setPhone(row.getPhone());
            employee.setGender(row.getGender() == null ? 0 : row.getGender());
            employee.setDeptId(row.getDeptId());
            employee.setStationId(row.getStationId());
            employee.setRole("STAFF");  // 导入默认值：角色由管理员后续在页面调整
            employee.setStatus(1);
            employee.setPwdChanged(0);  // 首登强制改密
            employee.setEntryDate(row.getEntryDate());
            employee.setRemark(row.getRemark());
            employeeMapper.insert(employee);
        }

        ImportResultVO result = new ImportResultVO();
        result.setTotal(rows.size());
        result.setSuccessCount(rows.size());
        result.setFailCount(0);
        return result;
    }

    @Override
    public void export(EmployeeQuery query, HttpServletResponse response) {
        checkStatusParam(query.getStatus());
        // 复用列表筛选条件，全量导出（不分页）
        List<Employee> employees = employeeMapper.selectList(buildQueryWrapper(query));

        Set<Long> deptIds = new HashSet<>();
        Set<Long> stationIds = new HashSet<>();
        for (Employee employee : employees) {
            if (employee.getDeptId() != null) {
                deptIds.add(employee.getDeptId());
            }
            if (employee.getStationId() != null) {
                stationIds.add(employee.getStationId());
            }
        }
        Map<Long, String> deptNames = loadDeptNames(deptIds);
        Map<Long, String> stationNames = loadStationNames(stationIds);

        List<List<Object>> rows = new ArrayList<>(employees.size());
        for (Employee employee : employees) {
            List<Object> row = new ArrayList<>(EXPORT_HEADERS.size());
            row.add(employee.getUsername());
            row.add(employee.getRealName());
            row.add(employee.getPhone()); // 导出完整手机号（决策 D5 例外）
            row.add(genderText(employee.getGender()));
            row.add(employee.getDeptId() == null ? "" : deptNames.getOrDefault(employee.getDeptId(), ""));
            row.add(employee.getStationId() == null ? "" : stationNames.getOrDefault(employee.getStationId(), ""));
            row.add(roleText(employee.getRole()));
            row.add(employee.getStatus() != null && employee.getStatus() == 1 ? "启用" : "禁用");
            row.add(employee.getEntryDate() == null ? "" : employee.getEntryDate().format(DATE_FORMATTER));
            row.add(employee.getLastLoginTime() == null ? "" : employee.getLastLoginTime().format(DATETIME_FORMATTER));
            row.add(employee.getCreateTime() == null ? "" : employee.getCreateTime().format(DATETIME_FORMATTER));
            row.add(employee.getRemark() == null ? "" : employee.getRemark());
            rows.add(row);
        }

        try {
            String fileName = "员工数据_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
            setDownloadHeaders(response, fileName);
            List<List<String>> head = EXPORT_HEADERS.stream()
                    .map(List::of)
                    .toList();
            // EasyExcel 流式写出（一期量级数千行无压力）
            EasyExcel.write(response.getOutputStream())
                    .head(head)
                    .sheet("员工数据")
                    .doWrite(rows);
        } catch (IOException e) {
            log.error("导出员工数据失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    // ==================== 私有方法 ====================

    /** 构造查询条件：keyword 三字段模糊 / deptId 含子部门 / stationId / status，create_time 倒序 */
    private LambdaQueryWrapper<Employee> buildQueryWrapper(EmployeeQuery query) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        if (!keyword.isEmpty()) {
            // 姓名 / 登录账号 / 手机号 任一命中；LIKE 通配符转义防全表匹配
            String escaped = escapeLike(keyword);
            wrapper.and(w -> w.like(Employee::getRealName, escaped)
                    .or().like(Employee::getUsername, escaped)
                    .or().like(Employee::getPhone, escaped));
        }
        if (query.getDeptId() != null) {
            // 部门筛选含全部子部门：部门 < 200，内存 BFS 递归展开（db.md 3.1 取舍）
            wrapper.in(Employee::getDeptId, expandDeptIdsWithChildren(query.getDeptId()));
        }
        if (query.getStationId() != null) {
            wrapper.eq(Employee::getStationId, query.getStationId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Employee::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Employee::getCreateTime);
        return wrapper;
    }

    /** BFS 展开部门及其全部子孙部门 id（visited 防御脏数据成环） */
    private List<Long> expandDeptIdsWithChildren(Long deptId) {
        List<Department> departments = departmentMapper.selectList(null);
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (Department department : departments) {
            childrenMap.computeIfAbsent(department.getParentId(), k -> new ArrayList<>())
                    .add(department.getId());
        }
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        visited.add(deptId);
        queue.add(deptId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            result.add(current);
            for (Long child : childrenMap.getOrDefault(current, List.of())) {
                if (visited.add(child)) {
                    queue.add(child);
                }
            }
        }
        return result;
    }

    /** LIKE 通配符转义（反斜杠 / % / _），MySQL 与 PostgreSQL 均以反斜杠为默认转义符 */
    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 校验筛选状态参数取值 */
    private void checkStatusParam(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态取值仅支持 0=禁用/1=启用");
        }
    }

    private List<EmployeeVO> toVOList(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) {
            return List.of();
        }
        Set<Long> deptIds = new HashSet<>();
        Set<Long> stationIds = new HashSet<>();
        for (Employee employee : employees) {
            if (employee.getDeptId() != null) {
                deptIds.add(employee.getDeptId());
            }
            if (employee.getStationId() != null) {
                stationIds.add(employee.getStationId());
            }
        }
        Map<Long, String> deptNames = loadDeptNames(deptIds);
        Map<Long, String> stationNames = loadStationNames(stationIds);

        List<EmployeeVO> vos = new ArrayList<>(employees.size());
        for (Employee employee : employees) {
            EmployeeVO vo = new EmployeeVO();
            vo.setId(employee.getId());
            vo.setUsername(employee.getUsername());
            vo.setRealName(employee.getRealName());
            vo.setPhone(DesensitizeUtil.maskPhone(employee.getPhone()));
            vo.setGender(employee.getGender());
            vo.setDeptId(employee.getDeptId());
            vo.setDeptName(employee.getDeptId() == null ? null : deptNames.get(employee.getDeptId()));
            vo.setStationId(employee.getStationId());
            vo.setStationName(employee.getStationId() == null ? null : stationNames.get(employee.getStationId()));
            vo.setRole(employee.getRole());
            vo.setStatus(employee.getStatus());
            // C-06：补 pwdChanged，与 Mock toEmployeeVO 字段集对齐
            vo.setPwdChanged(employee.getPwdChanged() != null && employee.getPwdChanged() == 1);
            vo.setEntryDate(employee.getEntryDate());
            vo.setRemark(employee.getRemark());
            vo.setLastLoginTime(employee.getLastLoginTime());
            vo.setCreateTime(employee.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }

    private Map<Long, String> loadDeptNames(Set<Long> deptIds) {
        Map<Long, String> names = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (Department department : departmentMapper.selectBatchIds(deptIds)) {
                names.put(department.getId(), department.getDeptName());
            }
        }
        return names;
    }

    private Map<Long, String> loadStationNames(Set<Long> stationIds) {
        Map<Long, String> names = new HashMap<>();
        if (!stationIds.isEmpty()) {
            for (Station station : stationMapper.selectBatchIds(stationIds)) {
                names.put(station.getId(), station.getStationName());
            }
        }
        return names;
    }

    /** 账号活跃查重（编辑时排除自身） */
    private boolean existsActiveUsername(String username, Long excludeId) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .eq(Employee::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(Employee::getId, excludeId);
        }
        Long count = employeeMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /** 手机号活跃查重（编辑时排除自身） */
    private boolean existsActivePhone(String phone, Long excludeId) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .eq(Employee::getPhone, phone);
        if (excludeId != null) {
            wrapper.ne(Employee::getId, excludeId);
        }
        Long count = employeeMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /** 归属校验：部门须存在未删除（3001）；驿站须存在未删除（4001）且启用（4004） */
    private void validateDeptAndStation(Long deptId, Long stationId) {
        if (deptId != null && departmentMapper.selectById(deptId) == null) {
            throw new BusinessException(ErrorCode.DEPT_NOT_FOUND);
        }
        if (stationId != null) {
            Station station = stationMapper.selectById(stationId);
            if (station == null) {
                throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
            }
            if (station.getStatus() == null || station.getStatus() != 1) {
                throw new BusinessException(ErrorCode.STATION_DISABLED);
            }
        }
    }

    /** 入职日期严格解析（yyyy-MM-dd 且为真实日期） */
    private LocalDate parseEntryDate(String entryDate) {
        if (entryDate == null || entryDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(entryDate.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "入职日期格式不正确，须为 yyyy-MM-dd");
        }
    }

    /**
     * 判断目标员工是否为最后一个可用管理员（可用 = 未删除且启用且角色 ADMIN）。
     * 目标自身不可用（已禁用/非管理员）时不触发保护。
     */
    private boolean isLastAvailableAdmin(Employee target) {
        if (!"ADMIN".equals(target.getRole()) || target.getStatus() == null || target.getStatus() != 1) {
            return false;
        }
        Long availableAdminCount = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getRole, "ADMIN")
                .eq(Employee::getStatus, 1));
        return availableAdminCount != null && availableAdminCount <= 1;
    }

    /**
     * 强制下线（禁用 / 删除 / 重置密码 / 角色降级等安全事件）：
     * ① 删除该员工<b>全部</b> Redis 会话（按索引遍历所有端/设备，多端会话语义）；
     * ② <b>使其已信任设备全部失效</b>（M4 安全加固「改密即失效」的同类事件口径）——
     * 否则旧设备仍可凭旧 {@code device_token} 免短信二次验证。
     * 失败仅记日志（会话有 TTL 兜底），不回滚业务操作。
     */
    private void forceOffline(Long employeeId) {
        try {
            sessionUtil.deleteAllOfEmployee(employeeId);
        } catch (Exception e) {
            log.error("强制下线删除会话失败，employeeId={}", employeeId, e);
        }
        try {
            trustedDeviceRegistry.revokeAllOfEmployee(employeeId);
        } catch (Exception e) {
            log.error("强制下线撤销受信设备失败，employeeId={}", employeeId, e);
        }
    }

    /** 读取导入文件数据行（以 Map 读取，携带 Excel 真实行号） */
    private List<EmployeeImportRow> readImportRows(MultipartFile file) {
        List<EmployeeImportRow> rows = new ArrayList<>();
        try {
            EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    EmployeeImportRow row = new EmployeeImportRow();
                    // readRowHolder 行号 0 基，转 Excel 1 基行号（表头=1，首条数据=2）
                    row.setRowNumber(context.readRowHolder().getRowIndex() + 1);
                    row.setRealName(cell(data, 0));
                    row.setUsername(cell(data, 1));
                    row.setPhone(cell(data, 2));
                    row.setGenderText(cell(data, 3));
                    row.setDeptName(cell(data, 4));
                    row.setStationName(cell(data, 5));
                    row.setEntryDateText(cell(data, 6));
                    row.setRemark(cell(data, 7));
                    rows.add(row);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 读取完成
                }
            }).sheet(0).headRowNumber(1).doRead();
        } catch (IOException | RuntimeException e) {
            // 文件损坏 / 格式非法统一按 5001 处理（EasyExcel 解析失败多抛运行时异常）
            log.warn("导入文件解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.IMPORT_FILE_INVALID);
        }
        return rows;
    }

    private String cell(Map<Integer, String> data, int index) {
        String value = data.get(index);
        return value == null ? null : value.trim();
    }

    /** 全空行判断（所有列均空白） */
    private boolean isBlankRow(EmployeeImportRow row) {
        return isBlank(row.getRealName()) && isBlank(row.getUsername()) && isBlank(row.getPhone())
                && isBlank(row.getGenderText()) && isBlank(row.getDeptName()) && isBlank(row.getStationName())
                && isBlank(row.getEntryDateText()) && isBlank(row.getRemark());
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** 载入库内活跃数据，构造行级校验器 */
    private ImportRowValidator buildValidator() {
        Set<String> activeUsernames = new HashSet<>();
        Set<String> activePhones = new HashSet<>();
        for (Employee employee : employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .select(Employee::getUsername, Employee::getPhone))) {
            activeUsernames.add(employee.getUsername());
            activePhones.add(employee.getPhone());
        }
        Map<String, List<Long>> deptIdsByName = new HashMap<>();
        for (Department department : departmentMapper.selectList(null)) {
            deptIdsByName.computeIfAbsent(department.getDeptName(), k -> new ArrayList<>())
                    .add(department.getId());
        }
        Map<String, List<Long>> allStationIdsByName = new HashMap<>();
        Map<String, List<Long>> enabledStationIdsByName = new HashMap<>();
        for (Station station : stationMapper.selectList(null)) {
            allStationIdsByName.computeIfAbsent(station.getStationName(), k -> new ArrayList<>())
                    .add(station.getId());
            if (station.getStatus() != null && station.getStatus() == 1) {
                enabledStationIdsByName.computeIfAbsent(station.getStationName(), k -> new ArrayList<>())
                        .add(station.getId());
            }
        }
        return new ImportRowValidator(activeUsernames, activePhones,
                deptIdsByName, allStationIdsByName, enabledStationIdsByName);
    }

    /** 文件下载响应头：UTF-8 文件名（RFC 5987 filename* 编码） */
    private void setDownloadHeaders(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=utf-8''" + encoded);
    }

    private String genderText(Integer gender) {
        if (gender == null) {
            return "未知";
        }
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }

    /** 角色中文映射（api.md 第 6 章：管理员/站长(二期)/员工） */
    private String roleText(String role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case "ADMIN" -> "管理员";
            case "STATION_ADMIN" -> "站长(二期)";
            case "STAFF" -> "员工";
            default -> role;
        };
    }
}
