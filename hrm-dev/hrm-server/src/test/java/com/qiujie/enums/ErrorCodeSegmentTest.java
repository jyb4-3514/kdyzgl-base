package com.qiujie.enums;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 错误码分段与文案单测（C-05），逐条对齐架构附录 B 与 Mock CODE_MESSAGE。
 * 注意：本机无 JDK/Maven，无法执行；收敛到服务器阶段运行。
 */
class ErrorCodeSegmentTest {

    @Test
    void codesAreGloballyUnique() {
        Map<Integer, ErrorCode> seen = new HashMap<>();
        for (ErrorCode errorCode : ErrorCode.values()) {
            ErrorCode previous = seen.put(errorCode.getCode(), errorCode);
            assertNull(previous, "码值重复：" + errorCode.getCode() + " → " + previous + " / " + errorCode);
        }
    }

    @Test
    void segmentAnchorsMatchAppendixB() {
        // 通用段
        assertEquals(200, ErrorCode.SUCCESS.getCode());
        assertEquals(400, ErrorCode.BAD_REQUEST.getCode());
        assertEquals(500, ErrorCode.SYSTEM_ERROR.getCode());
        // 既有 10xx~50xx 边界
        assertEquals(1001, ErrorCode.LOGIN_FAILED.getCode());
        assertEquals(2003, ErrorCode.PHONE_EXISTS.getCode());
        assertEquals(4004, ErrorCode.STATION_DISABLED.getCode());
        assertEquals(5003, ErrorCode.IMPORT_DATA_ERROR.getCode());
        // 认证增强段 11xx（本批仅落 1110：端维度角色约束；1101–1109 由 M4 补齐）
        assertEquals(1110, ErrorCode.LOGIN_CLIENT_NOT_ALLOWED.getCode());
        // 新增段：60xx/70xx/80xx/90xx/91xx/92xx/93xx/94xx/95xx/96xx
        assertEquals(6002, ErrorCode.SYNC_CONFIG_NOT_EXISTS.getCode());
        assertEquals(7003, ErrorCode.PARCEL_PICKED_BY_OTHER.getCode());
        assertEquals(8006, ErrorCode.WORK_ORDER_GROUP_MSG_INVALID.getCode());
        assertEquals(9002, ErrorCode.NOTIFICATION_PUBLISH_SCOPE_INVALID.getCode());
        assertEquals(9101, ErrorCode.ATTENDANCE_RULE_NOT_CONFIGURED.getCode());
        assertEquals(9109, ErrorCode.ATTENDANCE_MAKEUP_STATUS_INVALID.getCode());
        assertEquals(9204, ErrorCode.KPI_SCORE_NOT_EXISTS.getCode());
        assertEquals(9306, ErrorCode.HR_SETTLEMENT_UNFINISHED.getCode());
        assertEquals(9405, ErrorCode.FINANCE_PAYROLL_GENERATED.getCode());
        assertEquals(9510, ErrorCode.SYNC_BUILTIN_NOT_DELETABLE.getCode());
        assertEquals(9607, ErrorCode.LEAVE_EDIT_FORBIDDEN.getCode());
    }

    @Test
    void messagesMatchMockCodeMessage() {
        assertEquals("定位校验未通过，已超出打卡围栏范围", ErrorCode.ATTENDANCE_LOCATION_MISMATCH.getMessage());
        assertEquals("该员工该考核月份暂无评分记录", ErrorCode.KPI_SCORE_NOT_EXISTS.getMessage());
        assertEquals("配置项被驿站覆盖，需确认后删除", ErrorCode.SYNC_ITEM_IN_USE.getMessage());
        assertEquals("该月工资单已提交审核或已发布，不可重复生成", ErrorCode.FINANCE_PAYROLL_GENERATED.getMessage());
        assertEquals("该账期工资单已生成，不可撤回", ErrorCode.LEAVE_PAYROLL_LOCKED.getMessage());
        assertEquals("该驿站尚未配置打卡规则", ErrorCode.ATTENDANCE_RULE_NOT_CONFIGURED.getMessage());
        assertEquals("该账号无权登录此端", ErrorCode.LOGIN_CLIENT_NOT_ALLOWED.getMessage());
    }
}
