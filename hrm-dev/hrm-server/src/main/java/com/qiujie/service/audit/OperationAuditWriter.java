package com.qiujie.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiujie.common.LoginUser;
import com.qiujie.entity.Employee;
import com.qiujie.entity.OperationAuditLog;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.OperationAuditLogMapper;
import com.qiujie.util.IpUtil;
import com.qiujie.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 操作审计留痕写入出口（ARCH-S-2 / §3.3；评审 T9 定稿）。
 * <p>
 * <b>写入方式（T9 定稿）= Service 内显式调用 + 与业务写同事务</b>：
 * <ul>
 *   <li>选显式而非 AOP 切面：切面难以在拦截点拿到「目标名称快照 + before/after 白名单」，
 *       且表达式取业务参数会产生编译期脆弱耦合；显式调用落点清晰、可审、可断言（单测友好）。</li>
 *   <li>选同事务而非失败补偿：本批 12 个写入点均为低频后台操作，单行 append 代价可忽略；
 *       同事务保证「业务成功 ↔ 审计必在；业务回滚 ↔ 审计同回滚」，不产生审计与事实不一致的窗口。</li>
 * </ul>
 * <b>口令脱敏</b>：调用方只传白名单键；涉及口令一律传布尔标记（{@link #PASSWORD_FLAG_KEY}），
 * 本类不做任何「值透传」，全表检索无明文 / 散列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationAuditWriter {

    // ==================== 目标类型 / 动作常量（与 V23 DDL 注释取值一致） ====================

    public static final String TARGET_EMPLOYEE = "EMPLOYEE";
    public static final String TARGET_STATION = "STATION";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_CHANGE_STATUS = "CHANGE_STATUS";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_RESET_PASSWORD = "RESET_PASSWORD";

    /** 口令布尔标记键（值仅允许 SET / RESET，绝不落口令本身） */
    public static final String PASSWORD_FLAG_KEY = "password";

    private static final String OPERATOR_TYPE_USER = "USER";
    private static final String OPERATOR_TYPE_SYSTEM = "SYSTEM";
    private static final String RESULT_SUCCESS = "SUCCESS";

    private final OperationAuditLogMapper operationAuditLogMapper;
    private final EmployeeMapper employeeMapper;
    private final ObjectMapper objectMapper;

    /**
     * 记一条审计留痕（只增）。
     *
     * @param targetType EMPLOYEE / STATION
     * @param targetId   目标主键
     * @param targetName 目标名称快照（员工姓名 / 驿站名）
     * @param action     CREATE / UPDATE / CHANGE_STATUS / DELETE / RESET_PASSWORD
     * @param before     变更前快照（白名单键，可空；口令只放布尔标记）
     * @param after      变更后快照（白名单键，可空；口令只放布尔标记）
     */
    public void record(String targetType, Long targetId, String targetName, String action,
                       Map<String, Object> before, Map<String, Object> after) {
        OperationAuditLog entry = new OperationAuditLog();
        fillOperator(entry);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setTargetName(truncate(targetName, 64));
        entry.setAction(action);
        entry.setBefore(toJson(before));
        entry.setAfter(toJson(after));
        entry.setChangedFields(toJson(changedFields(before, after)));
        entry.setClientIp(currentClientIp());
        entry.setResult(RESULT_SUCCESS);
        entry.setTime(LocalDateTime.now());
        operationAuditLogMapper.insert(entry);
    }

    // ==================== 内部 ====================

    /** 操作人取自登录上下文；姓名快照回查员工表（对齐 {@code currentOperatorName} 口径），失败降级为用户名 */
    private void fillOperator(OperationAuditLog entry) {
        LoginUser user = UserContext.get();
        Long operatorId = user == null ? null : user.getUserId();
        entry.setOperatorId(operatorId);
        entry.setOperatorRole(user == null ? null : user.getRole());
        entry.setOperatorType(operatorId == null ? OPERATOR_TYPE_SYSTEM : OPERATOR_TYPE_USER);
        String name = null;
        if (operatorId != null) {
            Employee operator = employeeMapper.selectById(operatorId);
            name = operator == null ? null : operator.getRealName();
        }
        if (name == null && user != null) {
            name = user.getUsername();
        }
        entry.setOperatorName(truncate(name, 50));
    }

    /** 变更字段白名单：合并 before/after 键，仅列出值发生变化的字段名 */
    private List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> safeBefore = before == null ? Map.of() : before;
        Map<String, Object> safeAfter = after == null ? Map.of() : after;
        List<String> fields = new ArrayList<>();
        for (Map.Entry<String, Object> e : safeAfter.entrySet()) {
            if (!Objects.equals(safeBefore.get(e.getKey()), e.getValue())) {
                fields.add(e.getKey());
            }
        }
        for (Map.Entry<String, Object> e : safeBefore.entrySet()) {
            if (!safeAfter.containsKey(e.getKey())) {
                fields.add(e.getKey());
            }
        }
        return fields;
    }

    /** 快照序列化：同时服务 before/after（Map）与 changedFields（字段名列表）两种入参 */
    private String toJson(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        if (snapshot instanceof java.util.Collection<?> collection && collection.isEmpty()) {
            return null;
        }
        try {
            Object payload = snapshot instanceof Map<?, ?> map ? new LinkedHashMap<>(map) : snapshot;
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 审计不可静默丢失；序列化失败视为系统错误，与业务写同事务一并回滚（T9 同事务口径）
            log.error("审计快照序列化失败，已中止事务", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 客户端 IP：Nginx 透传 X-Forwarded-For 首个；非 Web 上下文（如定时任务）返回 null */
    private String currentClientIp() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                return truncate(IpUtil.getClientIp(request), 50);
            }
        } catch (Exception e) {
            log.warn("读取客户端 IP 失败，审计留痕 clientIp 置空：{}", e.getMessage());
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
