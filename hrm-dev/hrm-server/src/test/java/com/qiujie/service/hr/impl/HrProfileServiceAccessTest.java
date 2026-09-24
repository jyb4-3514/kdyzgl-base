package com.qiujie.service.hr.impl;

import com.qiujie.common.LoginUser;
import com.qiujie.entity.Employee;
import com.qiujie.entity.HrProfile;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.HrProfileMapper;
import com.qiujie.mapper.HrSalaryLogMapper;
import com.qiujie.mapper.HrSalaryMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.util.UserContext;
import com.qiujie.vo.hr.HrProfileDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 人事档案/定薪详情越权判定单测（对齐 Mock {@code canAccessEmployee}：ADMIN 或本人，其余 403）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HrProfileServiceAccessTest {

    private EmployeeMapper employeeMapper;
    private HrProfileMapper hrProfileMapper;
    private HrSalaryWriter hrSalaryWriter;
    private HrProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        employeeMapper = mock(EmployeeMapper.class);
        hrProfileMapper = mock(HrProfileMapper.class);
        hrSalaryWriter = mock(HrSalaryWriter.class);
        service = new HrProfileServiceImpl(employeeMapper, mock(DepartmentMapper.class), mock(StationMapper.class),
                hrProfileMapper, mock(HrSalaryMapper.class), mock(HrSalaryLogMapper.class), hrSalaryWriter);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 登录身份：员工（STAFF）id=5 */
    private void loginAsStaff() {
        UserContext.set(new LoginUser(5L, "staff", "STAFF", "jti", "1"));
    }

    private void loginAsAdmin() {
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));
    }

    private HrProfile profileOf(Long employeeId) {
        HrProfile profile = new HrProfile();
        profile.setId(employeeId);
        profile.setEmployeeId(employeeId);
        return profile;
    }

    @Test
    @DisplayName("员工查看他人档案 → 403")
    void staffCannotViewOthersProfile() {
        loginAsStaff();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.profileDetail(9L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("无权查看他人人事档案", ex.getMessage());
    }

    @Test
    @DisplayName("员工查看他人定薪 → 403")
    void staffCannotViewOthersSalary() {
        loginAsStaff();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.salaryDetail(9L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("无权查看他人定薪档案", ex.getMessage());
    }

    @Test
    @DisplayName("员工查看本人档案 → 放行")
    void staffCanViewOwnProfile() {
        loginAsStaff();
        when(hrProfileMapper.selectList(any())).thenReturn(List.of(profileOf(5L)));
        Employee self = new Employee();
        self.setId(5L);
        self.setRealName("本人");
        when(employeeMapper.selectById(5L)).thenReturn(self);
        when(hrSalaryWriter.selectByEmployeeId(5L)).thenReturn(null);

        HrProfileDetailVO vo = service.profileDetail(5L);
        assertNotNull(vo);
        assertEquals(5L, vo.getEmployeeId());
    }

    @Test
    @DisplayName("ADMIN 查看他人档案 → 放行")
    void adminCanViewOthersProfile() {
        loginAsAdmin();
        when(hrProfileMapper.selectList(any())).thenReturn(List.of(profileOf(9L)));
        Employee other = new Employee();
        other.setId(9L);
        other.setRealName("他人");
        when(employeeMapper.selectById(9L)).thenReturn(other);
        when(hrSalaryWriter.selectByEmployeeId(9L)).thenReturn(null);

        HrProfileDetailVO vo = service.profileDetail(9L);
        assertNotNull(vo);
        assertEquals(9L, vo.getEmployeeId());
    }
}
