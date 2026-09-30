package com.qiujie.service.employee.impl;

import com.qiujie.dto.employee.EmployeeCreateRequest;
import com.qiujie.dto.employee.EmployeeUpdateRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.audit.OperationAuditWriter;
import com.qiujie.service.auth.support.TrustedDeviceRegistry;
import com.qiujie.util.SessionUtil;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ARCH-C-1 角色白名单放开单测：DTO 取值白名单真源 + 站长归属启用驿站条件校验。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class EmployeeRoleWhitelistTest {

    private EmployeeMapper employeeMapper;
    private StationMapper stationMapper;
    private OperationAuditWriter auditWriter;
    private EmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        employeeMapper = mock(EmployeeMapper.class);
        stationMapper = mock(StationMapper.class);
        auditWriter = mock(OperationAuditWriter.class);
        BCryptPasswordEncoder passwordEncoder = mock(BCryptPasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(employeeMapper.selectCount(any())).thenReturn(0L);
        when(employeeMapper.insert(any(Employee.class))).thenAnswer(inv -> {
            inv.getArgument(0, Employee.class).setId(9L);
            return 1;
        });
        service = new EmployeeServiceImpl(employeeMapper, mock(DepartmentMapper.class), stationMapper,
                passwordEncoder, mock(SessionUtil.class), mock(TrustedDeviceRegistry.class), auditWriter);
    }

    @Test
    @DisplayName("DTO role 白名单含 STATION_ADMIN（新增/编辑两处正则一致）")
    void dtoRoleWhitelistIncludesStationAdmin() throws Exception {
        String createRegex = EmployeeCreateRequest.class.getDeclaredField("role")
                .getAnnotation(Pattern.class).regexp();
        String updateRegex = EmployeeUpdateRequest.class.getDeclaredField("role")
                .getAnnotation(Pattern.class).regexp();
        assertEquals(createRegex, updateRegex);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(createRegex);
        assertTrue(p.matcher("ADMIN").matches());
        assertTrue(p.matcher("STAFF").matches());
        assertTrue(p.matcher("STATION_ADMIN").matches());
        assertFalse(p.matcher("SUPER_ADMIN").matches());
        assertFalse(p.matcher("").matches());
    }

    @Test
    @DisplayName("新增站长未归属驿站 → 2004")
    void createStationAdminWithoutStationRejected() {
        EmployeeCreateRequest request = newRequest("STATION_ADMIN", null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertEquals(ErrorCode.STATION_ADMIN_STATION_REQUIRED.getCode(), ex.getCode());
        verify(employeeMapper, never()).insert(any(Employee.class));
        verify(auditWriter, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("新增站长归属停用驿站 → 4004")
    void createStationAdminOnDisabledStationRejected() {
        when(stationMapper.selectById(5L)).thenReturn(station(5L, 0));
        EmployeeCreateRequest request = newRequest("STATION_ADMIN", 5L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertEquals(ErrorCode.STATION_DISABLED.getCode(), ex.getCode());
        verify(employeeMapper, never()).insert(any(Employee.class));
    }

    @Test
    @DisplayName("新增站长归属启用驿站 → 成功且记 1 条审计（口令只记布尔标记）")
    void createStationAdminOnEnabledStationSucceeds() {
        when(stationMapper.selectById(5L)).thenReturn(station(5L, 1));
        EmployeeCreateRequest request = newRequest("STATION_ADMIN", 5L);
        assertEquals(9L, service.create(request).getId().longValue());
        verify(employeeMapper, times(1)).insert(any(Employee.class));
        verify(auditWriter, times(1)).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("编辑站长置空归属驿站 → 2004（UPDATE 亦禁止）")
    void updateStationAdminCannotClearStation() {
        Employee exist = new Employee();
        exist.setId(2L);
        exist.setRole("STATION_ADMIN");
        exist.setStatus(1);
        exist.setStationId(5L);
        when(employeeMapper.selectById(2L)).thenReturn(exist);

        EmployeeUpdateRequest request = new EmployeeUpdateRequest();
        request.setRealName("李四");
        request.setPhone("13812345678");
        request.setRole("STATION_ADMIN");
        request.setStationId(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(2L, request));
        assertEquals(ErrorCode.STATION_ADMIN_STATION_REQUIRED.getCode(), ex.getCode());
        verify(employeeMapper, never()).update(any(Employee.class), any());
        verify(stationMapper, never()).selectById(anyLong());
    }

    private EmployeeCreateRequest newRequest(String role, Long stationId) {
        EmployeeCreateRequest request = new EmployeeCreateRequest();
        request.setUsername("zhangsan");
        request.setPassword("Abcd1234");
        request.setRealName("张三");
        request.setPhone("13812345678");
        request.setRole(role);
        request.setStationId(stationId);
        return request;
    }

    private Station station(Long id, int status) {
        Station station = new Station();
        station.setId(id);
        station.setStatus(status);
        return station;
    }
}
