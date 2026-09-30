package com.qiujie.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiujie.common.LoginUser;
import com.qiujie.entity.Employee;
import com.qiujie.entity.OperationAuditLog;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.OperationAuditLogMapper;
import com.qiujie.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V23 审计留痕写入出口单测：操作人快照 / 变更字段白名单 / 口令只记布尔标记（不落明文散列）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class OperationAuditWriterTest {

    private OperationAuditLogMapper auditMapper;
    private OperationAuditWriter writer;
    private ArgumentCaptor<OperationAuditLog> captor;

    @BeforeEach
    void setUp() {
        auditMapper = mock(OperationAuditLogMapper.class);
        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        Employee operator = new Employee();
        operator.setId(7L);
        operator.setRealName("超级管理员");
        when(employeeMapper.selectById(7L)).thenReturn(operator);
        writer = new OperationAuditWriter(auditMapper, employeeMapper, new ObjectMapper());
        captor = ArgumentCaptor.forClass(OperationAuditLog.class);

        UserContext.set(new LoginUser(7L, "admin", "ADMIN", "jti-1", "1"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("CREATE：口令只记布尔标记 SET，变更字段含 password 且无明文/散列")
    void createAuditMasksPassword() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("username", "zhangsan");
        after.put("realName", "张三");
        after.put(OperationAuditWriter.PASSWORD_FLAG_KEY, "SET");

        writer.record(OperationAuditWriter.TARGET_EMPLOYEE, 9L, "张三",
                OperationAuditWriter.ACTION_CREATE, null, after);

        verify(auditMapper).insert(captor.capture());
        OperationAuditLog entry = captor.getValue();
        assertEquals(OperationAuditWriter.TARGET_EMPLOYEE, entry.getTargetType());
        assertEquals(9L, entry.getTargetId().longValue());
        assertEquals("张三", entry.getTargetName());
        assertEquals(OperationAuditWriter.ACTION_CREATE, entry.getAction());
        assertEquals("USER", entry.getOperatorType());
        assertEquals(7L, entry.getOperatorId().longValue());
        assertEquals("ADMIN", entry.getOperatorRole());
        assertEquals("超级管理员", entry.getOperatorName());
        assertEquals("SUCCESS", entry.getResult());
        assertNotNull(entry.getTime());
        assertFalse(entry.getAfter().contains("Abcd1234"), "审计不得落口令明文");
        assertFalse(entry.getAfter().contains("$2a$"), "审计不得落 BCrypt 散列");
        assertTrue(entry.getAfter().contains("\"password\":\"SET\""));
        assertTrue(entry.getChangedFields().contains("\"password\""));
        assertTrue(entry.getChangedFields().contains("\"username\""));
    }

    @Test
    @DisplayName("CHANGE_STATUS：仅 status 发生变化 → changed_fields=[\"status\"]")
    void changeStatusAuditFields() {
        writer.record(OperationAuditWriter.TARGET_EMPLOYEE, 9L, "张三",
                OperationAuditWriter.ACTION_CHANGE_STATUS, Map.of("status", 1), Map.of("status", 0));

        verify(auditMapper).insert(captor.capture());
        OperationAuditLog entry = captor.getValue();
        assertEquals("[\"status\"]", entry.getChangedFields());
    }

    @Test
    @DisplayName("RESET_PASSWORD：口令只记布尔标记 RESET")
    void resetPasswordAuditOnlyFlag() {
        writer.record(OperationAuditWriter.TARGET_EMPLOYEE, 9L, "张三",
                OperationAuditWriter.ACTION_RESET_PASSWORD, null,
                Map.of(OperationAuditWriter.PASSWORD_FLAG_KEY, "RESET"));

        verify(auditMapper).insert(captor.capture());
        assertEquals("[\"password\"]", captor.getValue().getChangedFields());
        assertTrue(captor.getValue().getAfter().contains("\"password\":\"RESET\""));
    }

    @Test
    @DisplayName("无登录上下文（SYSTEM）→ operatorType=SYSTEM 且 operatorId 为空")
    void systemOperatorWhenNoUserContext() {
        UserContext.clear();
        writer.record(OperationAuditWriter.TARGET_STATION, 3L, "一号驿站",
                OperationAuditWriter.ACTION_DELETE, Map.of("status", 1), null);

        verify(auditMapper).insert(captor.capture());
        OperationAuditLog entry = captor.getValue();
        assertEquals("SYSTEM", entry.getOperatorType());
        assertEquals(null, entry.getOperatorId());
        assertEquals(OperationAuditWriter.TARGET_STATION, entry.getTargetType());
        assertEquals("[\"status\"]", entry.getChangedFields());
    }

    @Test
    @DisplayName("空快照 → before/after/changed_fields 均不写出（null）")
    void emptySnapshotWritesNull() {
        writer.record(OperationAuditWriter.TARGET_STATION, 3L, "一号驿站",
                OperationAuditWriter.ACTION_DELETE, null, null);

        verify(auditMapper).insert(captor.capture());
        assertEquals(null, captor.getValue().getBefore());
        assertEquals(null, captor.getValue().getAfter());
        assertEquals(null, captor.getValue().getChangedFields());
    }
}
