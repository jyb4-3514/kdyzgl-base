package com.qiujie.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.common.PageResult;
import com.qiujie.dto.hr.HrEmployeeQuery;
import com.qiujie.entity.Employee;
import com.qiujie.entity.HrProfile;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.HrProfileMapper;
import com.qiujie.mapper.HrSalaryLogMapper;
import com.qiujie.mapper.HrSalaryMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.auth.support.TrustedDeviceRegistry;
import com.qiujie.service.audit.OperationAuditWriter;
import com.qiujie.service.employee.impl.EmployeeServiceImpl;
import com.qiujie.service.hr.impl.HrProfileServiceImpl;
import com.qiujie.service.hr.impl.HrSalaryWriter;
import com.qiujie.util.SessionUtil;
import com.qiujie.vo.employee.EmployeeVO;
import com.qiujie.vo.hr.HrProfileVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 岗位出参补齐单测：确认 {@code EmployeeVO} / {@code HrProfileVO} 真实映射出 {@code position}，
 * 而非仅声明字段（前端恒显示「—」的根因即映射缺失）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PositionOutParamTest {

    /**
     * 纯单测无 Spring / MyBatis-Plus 引导上下文：{@code HrProfileServiceImpl.listProfiles} 内
     * {@code LambdaQueryWrapper.select(Employee::...)} 会即时解析列名，未注册实体 TableInfo（含 lambda 缓存）
     * 即抛 {@code MybatisPlusException: can not find lambda cache for this entity}。故用例前主动注册元数据
     * （与 {@code TrustedDeviceRegistryTest} / {@code PayrollServiceImplGenerateIdempotencyTest} 同一手法）。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Employee.class);
        // HrProfile 同在本路径上（loadProfiles 条件），一并注册避免后续补漏
        TableInfoHelper.initTableInfo(assistant, HrProfile.class);
    }

    @Test
    @DisplayName("EmployeeVO 出参含 position（实体 position → VO 映射不丢）")
    void employeeVoExposesPosition() {
        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        Employee employee = new Employee();
        employee.setId(2L);
        employee.setUsername("zhangsan");
        employee.setRealName("张三");
        employee.setPhone("13912345678");
        employee.setPosition("站长");
        when(employeeMapper.selectById(2L)).thenReturn(employee);

        EmployeeServiceImpl service = new EmployeeServiceImpl(employeeMapper, mock(DepartmentMapper.class),
                mock(StationMapper.class), mock(BCryptPasswordEncoder.class), mock(SessionUtil.class),
                mock(TrustedDeviceRegistry.class), mock(OperationAuditWriter.class));

        EmployeeVO vo = service.detail(2L);

        assertEquals("站长", vo.getPosition());
    }

    @Test
    @DisplayName("HrProfileVO 出参含 position（体检：投影须含 position 列，否则取不到值）")
    void hrProfileVoExposesPosition() {
        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        HrProfileMapper hrProfileMapper = mock(HrProfileMapper.class);
        Employee employee = new Employee();
        employee.setId(2L);
        employee.setUsername("lisi");
        employee.setRealName("李四");
        employee.setPhone("13912345678");
        employee.setPosition("管理员");
        HrProfile profile = new HrProfile();
        profile.setEmployeeId(2L);
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee));
        when(hrProfileMapper.selectList(any())).thenReturn(List.of(profile));

        HrProfileServiceImpl service = new HrProfileServiceImpl(employeeMapper, mock(DepartmentMapper.class),
                mock(StationMapper.class), hrProfileMapper, mock(HrSalaryMapper.class),
                mock(HrSalaryLogMapper.class), mock(HrSalaryWriter.class));

        PageResult<HrProfileVO> result = service.listProfiles(new HrEmployeeQuery());

        assertEquals(1, result.getList().size());
        assertEquals("管理员", result.getList().get(0).getPosition());
    }
}
