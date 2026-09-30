package com.qiujie.service.notification.impl;

import com.qiujie.common.LoginUser;
import com.qiujie.dto.notification.NotificationPublishRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Notification;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.NotificationMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.util.UserContext;
import com.qiujie.vo.notification.PublishResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知服务单测（Mockito，无 DB）：白名单拆分（公告 1..6 不放宽 / sendSystem 独立放行 7/8/9/10）、
 * 管理员接收人单一真源过滤。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class NotificationServiceImplTest {

    private NotificationMapper notificationMapper;
    private EmployeeMapper employeeMapper;
    private StationMapper stationMapper;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        notificationMapper = mock(NotificationMapper.class);
        employeeMapper = mock(EmployeeMapper.class);
        stationMapper = mock(StationMapper.class);
        service = new NotificationServiceImpl(notificationMapper, employeeMapper, stationMapper);
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        // login state is ThreadLocal; clear to avoid leaking into sibling tests
        UserContext.clear();
    }

    @Test
    @DisplayName("公告白名单未放宽：publish 传 type=7/8/9 → 400，且不落库（防仿冒薪资通知）")
    void publishRejectsPayrollTypes() {
        for (int type : new int[]{7, 8, 9}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.publish(publishRequest(type, "ALL")));
            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    @DisplayName("公告白名单维持 1..6：publish type=4 正常扇出")
    void publishStillAllowsAnnouncement() {
        // publish 内的越权收口（currentUserId / 发布人）要求登录态；此处补造 ADMIN 会话
        UserContext.set(new LoginUser(1L, "admin", "ADMIN", "jti", null));
        when(employeeMapper.selectList(any())).thenReturn(List.of(employee(1L, "STAFF", 1)));

        PublishResultVO result = service.publish(publishRequest(4, "ALL"));

        assertEquals(1, result.getCount());
        verify(notificationMapper, times(1)).insert(any(Notification.class));
    }

    @Test
    @DisplayName("sendSystem 放行 7/8/9：落 is_published=0、带 biz_type/biz_id")
    void sendSystemAllowsPayrollTypes() {
        for (int type : new int[]{7, 8, 9}) {
            service.sendSystem(11L, type, "标题", "内容", "payroll", 99L);
        }
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper, times(3)).insert(captor.capture());
        assertEquals(7, captor.getAllValues().get(0).getType());
        assertEquals(0, captor.getAllValues().get(0).getIsPublished());
        assertEquals("payroll", captor.getAllValues().get(0).getBizType());
        assertEquals(99L, captor.getAllValues().get(0).getBizId());
    }

    @Test
    @DisplayName("sendSystem 仍放行既有系统联动 1..6（工单 1/2、请假 5/6 不回归）")
    void sendSystemAllowsExistingSystemTypes() {
        for (int type : new int[]{1, 2, 3, 4, 5, 6}) {
            service.sendSystem(11L, type, "标题", "内容", "biz", 1L);
        }

        verify(notificationMapper, times(6)).insert(any(Notification.class));
    }

    @Test
    @DisplayName("sendSystem 未定义类型（如 11）→ 400")
    void sendSystemRejectsUnknownType() {
        assertThrows(BusinessException.class,
                () -> service.sendSystem(11L, 11, "标题", "内容", "payroll", 1L));
    }

    @Test
    @DisplayName("findAdminEmployeeIds：仅返回 role=ADMIN 且 status=1（不误推站长/员工/停用号）")
    void findAdminEmployeeIdsFilters() {
        when(employeeMapper.selectList(any())).thenReturn(List.of(
                employee(1L, "ADMIN", 1),
                employee(2L, "ADMIN", 0),
                employee(3L, "STAFF", 1),
                employee(4L, "STATION_ADMIN", 1)));

        assertEquals(List.of(1L), service.findAdminEmployeeIds());
    }

    private NotificationPublishRequest publishRequest(int type, String scope) {
        NotificationPublishRequest request = new NotificationPublishRequest();
        request.setType(type);
        request.setTitle("标题");
        request.setContent("内容");
        request.setScope(scope);
        return request;
    }

    private Employee employee(Long id, String role, int status) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setRole(role);
        employee.setStatus(status);
        return employee;
    }
}
