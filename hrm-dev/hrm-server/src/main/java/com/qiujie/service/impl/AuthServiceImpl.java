package com.qiujie.service.impl;

import com.qiujie.common.SessionInfo;
import com.qiujie.dto.ChangePasswordRequest;
import com.qiujie.dto.LoginRequest;
import com.qiujie.entity.Department;
import com.qiujie.entity.Employee;
import com.qiujie.entity.LoginLog;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.LoginLogMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.AuthService;
import com.qiujie.util.DesensitizeUtil;
import com.qiujie.util.JwtUtil;
import com.qiujie.util.SessionUtil;
import com.qiujie.util.UserContext;
import com.qiujie.vo.LoginEmployeeVO;
import com.qiujie.vo.LoginVO;
import com.qiujie.vo.MeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 认证服务实现（api.md 4.1 / 第 3 章）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EmployeeMapper employeeMapper;
    private final LoginLogMapper loginLogMapper;
    private final DepartmentMapper departmentMapper;
    private final StationMapper stationMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SessionUtil sessionUtil;

    @Override
    public LoginVO login(LoginRequest request, String loginIp, String userAgent) {
        Employee employee = employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>().eq(Employee::getUsername, request.getUsername()));

        // 账号不存在与密码错误统一 1001（防账号探测）；失败均写登录日志
        if (employee == null) {
            recordLoginLog(request.getUsername(), null, false, "账号密码错误", loginIp, userAgent);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            recordLoginLog(employee.getUsername(), employee.getId(), false, "账号密码错误", loginIp, userAgent);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (employee.getStatus() == null || employee.getStatus() != 1) {
            recordLoginLog(employee.getUsername(), employee.getId(), false, "账号已禁用", loginIp, userAgent);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 签发 Token：jti 随机，写 Redis 会话比对实现互踢/强制下线（决策 D3）
        String jti = UUID.randomUUID().toString();
        String token = jwtUtil.generate(employee.getId(), employee.getUsername(), employee.getRole(), jti);
        sessionUtil.save(employee.getId(), new SessionInfo(
                jti, employee.getUsername(), employee.getRole(),
                loginIp == null ? "" : loginIp, LocalDateTime.now().format(TIME_FORMATTER)));

        // 更新最后登录时间
        Employee update = new Employee();
        update.setId(employee.getId());
        update.setLastLoginTime(LocalDateTime.now());
        employeeMapper.updateById(update);

        recordLoginLog(employee.getUsername(), employee.getId(), true, null, loginIp, userAgent);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setExpiresIn(jwtUtil.getExpireSeconds());
        LoginEmployeeVO employeeVO = new LoginEmployeeVO();
        employeeVO.setId(employee.getId());
        employeeVO.setUsername(employee.getUsername());
        employeeVO.setRealName(employee.getRealName());
        employeeVO.setPhone(DesensitizeUtil.maskPhone(employee.getPhone()));
        employeeVO.setRole(employee.getRole());
        // pwd_changed=0 时前端强制进入改密流程（requirement.md 5.3）
        employeeVO.setPwdChanged(employee.getPwdChanged() != null && employee.getPwdChanged() == 1);
        vo.setEmployee(employeeVO);
        return vo;
    }

    @Override
    public void logout() {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            // 幂等：会话不存在也返回成功
            sessionUtil.delete(userId);
        }
    }

    @Override
    public MeVO me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Employee employee = employeeMapper.selectById(userId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        MeVO vo = new MeVO();
        vo.setId(employee.getId());
        vo.setUsername(employee.getUsername());
        vo.setRealName(employee.getRealName());
        vo.setPhone(DesensitizeUtil.maskPhone(employee.getPhone()));
        vo.setGender(employee.getGender());
        vo.setRole(employee.getRole());
        vo.setDeptId(employee.getDeptId());
        vo.setStationId(employee.getStationId());
        vo.setEntryDate(employee.getEntryDate());
        vo.setPwdChanged(employee.getPwdChanged() != null && employee.getPwdChanged() == 1);
        vo.setLastLoginTime(employee.getLastLoginTime());
        if (employee.getDeptId() != null) {
            Department department = departmentMapper.selectById(employee.getDeptId());
            vo.setDeptName(department == null ? null : department.getDeptName());
        }
        if (employee.getStationId() != null) {
            Station station = stationMapper.selectById(employee.getStationId());
            vo.setStationName(station == null ? null : station.getStationName());
        }
        return vo;
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Employee employee = employeeMapper.selectById(userId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        // 原密码校验失败 → 1004（新密码强度已由 DTO 校验）
        if (!passwordEncoder.matches(request.getOldPassword(), employee.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        Employee update = new Employee();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        update.setPwdChanged(1);
        employeeMapper.updateById(update);
        // 改密后删除会话，旧 Token 立即失效（需重新登录）
        sessionUtil.delete(userId);
    }

    /**
     * 写登录日志（成功/失败均记录，含 IP 与 UA）。
     * 审计数据写入失败不阻断登录主流程，仅记错误日志。
     */
    private void recordLoginLog(String username, Long employeeId, boolean success,
                                String failReason, String loginIp, String userAgent) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUsername(username);
            loginLog.setEmployeeId(employeeId);
            loginLog.setLoginIp(loginIp == null ? "" : loginIp);
            loginLog.setLoginResult(success ? 1 : 0);
            loginLog.setFailReason(failReason);
            // UA 截断至 255（表字段长度）
            loginLog.setUserAgent(userAgent == null ? null
                    : userAgent.substring(0, Math.min(userAgent.length(), 255)));
            loginLog.setLoginTime(LocalDateTime.now());
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("写入登录日志失败，username={}", username, e);
        }
    }
}
