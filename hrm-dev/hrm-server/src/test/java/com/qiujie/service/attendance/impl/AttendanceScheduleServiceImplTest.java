package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.attendance.ScheduleBatchRequest;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.Employee;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.mapper.AttendanceShiftMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceShiftService;
import com.qiujie.vo.attendance.ScheduleMatrixVO;
import com.qiujie.vo.attendance.ScheduleSaveResultVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排班多班次（ARCH-C-2/3/4/5）单测：集合覆盖（新增/软删/幂等/清空）、重叠拒绝、上限 2、ordinal 互异拒绝、矩阵出参。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceScheduleServiceImplTest {

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 依赖实体 TableInfo 缓存；纯单测无 Spring 容器，
     * 须手动注册，否则运行期抛 MybatisPlusException（照搬项目既有手法）。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AttendanceSchedule.class);
        TableInfoHelper.initTableInfo(assistant, AttendanceShift.class);
        TableInfoHelper.initTableInfo(assistant, Employee.class);
    }

    private static final long STATION = 3L;
    private static final String DATE = "2026-09-28";

    private AttendanceScheduleMapper scheduleMapper;
    private EmployeeMapper employeeMapper;
    private AttendanceShiftService shiftService;
    private AttendanceScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        scheduleMapper = mock(AttendanceScheduleMapper.class);
        shiftService = mock(AttendanceShiftService.class);
        employeeMapper = mock(EmployeeMapper.class);
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee(1L)));
        service = new AttendanceScheduleServiceImpl(scheduleMapper, mock(AttendanceShiftMapper.class),
                employeeMapper, mock(StationMapper.class), shiftService, new AlgoProperties());
    }

    @Test
    @DisplayName("新增两班（覆盖式）：saved=2、removed=0，插入 2 行")
    void addTwoShifts() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(shiftService.findEntity(10L)).thenReturn(shift(10L, "早班", "09:00", "13:00"));
        when(shiftService.findEntity(11L)).thenReturn(shift(11L, "晚班", "17:00", "21:00"));

        ScheduleSaveResultVO result = service.saveBatch(batch(item(1L, List.of(10L, 11L))));

        assertEquals(2, result.getSaved());
        assertEquals(0, result.getRemoved());
        verify(scheduleMapper, times(2)).insert(any(AttendanceSchedule.class));
        verify(scheduleMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("差量改派（10 → 11）：软删 1 行（C\\T）、新增 1 行（T\\C），saved=1、removed=1")
    void diffReplaceSoftDeletesOldShift() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule(100L, 10L)));
        when(shiftService.findEntity(11L)).thenReturn(shift(11L, "晚班", "17:00", "21:00"));

        ScheduleSaveResultVO result = service.saveBatch(batch(item(1L, List.of(11L))));

        assertEquals(1, result.getSaved());
        assertEquals(1, result.getRemoved());
        verify(scheduleMapper).deleteById(100L);
        verify(scheduleMapper, times(1)).insert(any(AttendanceSchedule.class));
    }

    @Test
    @DisplayName("幂等（重复排同一班次）：T∩C no-op，saved=1、removed=0，零写")
    void idempotentDuplicate() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule(100L, 10L)));
        when(shiftService.findEntity(10L)).thenReturn(shift(10L, "早班", "09:00", "13:00"));

        ScheduleSaveResultVO result = service.saveBatch(batch(item(1L, List.of(10L))));

        assertEquals(1, result.getSaved());
        assertEquals(0, result.getRemoved());
        verify(scheduleMapper, never()).insert(any(AttendanceSchedule.class));
        verify(scheduleMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("空集合 = 清空该天：saved=0、removed=1，软删 1 行")
    void emptySetClearsDay() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule(100L, 10L)));

        ScheduleSaveResultVO result = service.saveBatch(batch(item(1L, List.of())));

        assertEquals(0, result.getSaved());
        assertEquals(1, result.getRemoved());
        verify(scheduleMapper).deleteById(100L);
        verify(scheduleMapper, never()).insert(any(AttendanceSchedule.class));
    }

    @Test
    @DisplayName("兼容旧客户端：仅传单值 shiftId → 视为单元素集合")
    void legacySingleShiftId() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(shiftService.findEntity(10L)).thenReturn(shift(10L, "早班", "09:00", "13:00"));

        ScheduleBatchRequest.Item item = new ScheduleBatchRequest.Item();
        item.setEmployeeId(1L);
        item.setWorkDate(DATE);
        item.setShiftId(10L);
        ScheduleSaveResultVO result = service.saveBatch(batch(item));

        assertEquals(1, result.getSaved());
        verify(scheduleMapper, times(1)).insert(any(AttendanceSchedule.class));
    }

    @Test
    @DisplayName("时间重叠拒绝 → 9110")
    void overlappingShiftsRejected() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(shiftService.findEntity(10L)).thenReturn(shift(10L, "早班", "09:00", "13:00"));
        when(shiftService.findEntity(11L)).thenReturn(shift(11L, "中班", "12:00", "16:00"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveBatch(batch(item(1L, List.of(10L, 11L)))));
        assertEquals(ErrorCode.ATTENDANCE_SHIFT_TIME_OVERLAP.getCode(), ex.getCode());
        verify(scheduleMapper, never()).insert(any(AttendanceSchedule.class));
    }

    @Test
    @DisplayName("相邻不重叠（e₁==s₂）允许 → saved=2")
    void adjacentShiftsAllowed() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(shiftService.findEntity(10L)).thenReturn(shift(10L, "早班", "09:00", "13:00"));
        when(shiftService.findEntity(11L)).thenReturn(shift(11L, "中班", "13:00", "17:00"));

        assertEquals(2, service.saveBatch(batch(item(1L, List.of(10L, 11L)))).getSaved());
    }

    @Test
    @DisplayName("单日超过上限 2 → 9111")
    void dailyLimitExceeded() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(shiftService.findEntity(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            return shift(id, "班次" + id, "06:00", "09:00");
        });

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveBatch(batch(item(1L, List.of(10L, 11L, 12L)))));
        assertEquals(ErrorCode.ATTENDANCE_SHIFT_DAILY_LIMIT_EXCEEDED.getCode(), ex.getCode());
        verify(scheduleMapper, never()).insert(any(AttendanceSchedule.class));
    }

    @Test
    @DisplayName("同日两班同半天（ordinal 相同）→ 9112 拒绝整批")
    void ordinalConflictRejected() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(shiftService.findEntity(10L)).thenReturn(shift(10L, "班次A", "06:00", "09:00"));
        when(shiftService.findEntity(11L)).thenReturn(shift(11L, "班次B", "09:00", "11:00"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveBatch(batch(item(1L, List.of(10L, 11L)))));
        assertEquals(ErrorCode.ATTENDANCE_SHIFT_ORDINAL_CONFLICT.getCode(), ex.getCode());
        verify(scheduleMapper, never()).insert(any(AttendanceSchedule.class));
    }

    @Test
    @DisplayName("矩阵出参：多班次 shiftIds 含全部班次，scheduleId/shiftId 取首条")
    void matrixExposesAllShiftIds() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule(100L, 10L), schedule(101L, 11L)));
        when(shiftService.list(STATION)).thenReturn(List.of());

        ScheduleMatrixVO vo = service.matrix(STATION, DATE);

        ScheduleMatrixVO.DayCell cell = vo.getEmployees().get(0).getDays().stream()
                .filter(d -> d.getWorkDate().equals(LocalDate.parse(DATE)))
                .findFirst().orElseThrow();
        assertEquals(List.of(10L, 11L), cell.getShiftIds());
        assertEquals(100L, cell.getScheduleId().longValue());
        assertEquals(10L, cell.getShiftId().longValue());
    }

    // ==================== 构造工具 ====================

    private ScheduleBatchRequest batch(ScheduleBatchRequest.Item item) {
        ScheduleBatchRequest request = new ScheduleBatchRequest();
        request.setStationId(STATION);
        request.setItems(List.of(item));
        return request;
    }

    private ScheduleBatchRequest.Item item(Long employeeId, List<Long> shiftIds) {
        ScheduleBatchRequest.Item item = new ScheduleBatchRequest.Item();
        item.setEmployeeId(employeeId);
        item.setWorkDate(DATE);
        item.setShiftIds(shiftIds);
        return item;
    }

    private Employee employee(Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setRealName("员工" + id);
        employee.setStationId(STATION);
        return employee;
    }

    private AttendanceShift shift(Long id, String name, String start, String end) {
        AttendanceShift shift = new AttendanceShift();
        shift.setId(id);
        shift.setStationId(STATION);
        shift.setShiftName(name);
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setStatus(1);
        return shift;
    }

    private AttendanceSchedule schedule(Long id, Long shiftId) {
        AttendanceSchedule schedule = new AttendanceSchedule();
        schedule.setId(id);
        schedule.setEmployeeId(1L);
        schedule.setStationId(STATION);
        schedule.setWorkDate(LocalDate.parse(DATE));
        schedule.setShiftId(shiftId);
        return schedule;
    }
}
