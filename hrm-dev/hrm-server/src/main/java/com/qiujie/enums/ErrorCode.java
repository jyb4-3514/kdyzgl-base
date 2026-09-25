package com.qiujie.enums;

import lombok.Getter;

/**
 * 业务错误码枚举（与 api.md 第 2 章 + 附录 B 分段总表一致，文案逐条对齐 Mock {@code constants/errorCode.js#CODE_MESSAGE}）。
 * <p>
 * HTTP 状态映射规则（api.md 1.3）：401/403/404 同步 HTTP 状态码，其余业务错误 HTTP 200。
 * <p>
 * 段位划分（附录 B）：通用 / 10xx 认证 / 20xx 员工 / 30xx 部门 / 40xx 驿站 / 50xx 导入导出 /
 * 60xx 同步采集 / 70xx 包裹 / 80xx 工单 / 90xx 通知 / 91xx 考勤排班补卡 / 92xx KPI /
 * 93xx 人事入离职 / 94xx 财务工资单 / 95xx 同步配置中心 / 96xx 请假。<b>段位不重叠</b>。
 */
@Getter
public enum ErrorCode {

    /** 成功 */
    SUCCESS(200, "success"),
    /** 参数校验失败（message 为具体字段说明） */
    BAD_REQUEST(400, "参数校验失败"),
    /** 未登录 / Token 失效（含被顶下线、被强制下线） */
    UNAUTHORIZED(401, "未登录或登录态已失效"),
    /** 已登录但无权限 */
    FORBIDDEN(403, "无权限访问该资源"),
    /** 路径资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 系统内部错误（堆栈仅记日志） */
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),

    // ==================== 10xx 认证与账号 ====================

    /** 登录失败（不区分账号不存在与密码错误，防账号探测） */
    LOGIN_FAILED(1001, "用户名或密码错误"),
    /** 登录账号处于禁用状态 */
    ACCOUNT_DISABLED(1002, "账号已禁用，请联系管理员"),
    /** 新增员工时 username 与活跃数据重复 */
    USERNAME_EXISTS(1003, "登录账号已存在"),
    /** 修改本人密码时原密码校验失败 */
    OLD_PASSWORD_ERROR(1004, "原密码错误"),

    // ==================== 11xx 认证增强（M4 落地；文案逐条对齐 Mock constants/errorCode.js#CODE_MESSAGE） ====================

    /** 短信发送过于频繁（同手机号最小发送间隔 / 同 IP / 同设备 / 同账号维度限频触发） */
    SMS_RATE_LIMITED(1101, "验证码发送过于频繁，请稍后再试"),
    /** 验证码错误或已过期（比对失败 / TTL 到期 / 未申请即校验） */
    SMS_CODE_INVALID(1102, "验证码错误或已过期，请重新获取"),
    /** 验证码尝试次数达上限（本码已作废，须重新获取） */
    SMS_CODE_ATTEMPTS_EXCEEDED(1103, "验证码尝试次数过多，请重新获取"),
    /**
     * 检测到新设备，需短信验证（<b>业务分流码，非错误</b>）。
     * 契约形态（与前端 Mock 一致）：{@code POST /auth/login} 以 <b>HTTP 200 + 正常响应体</b>承载
     * {@code needDeviceVerify=true} + {@code twoFactorTicket}，本码仅保留给「需要但未提供分流信息」的异常路径与文案真源。
     */
    DEVICE_NEED_VERIFY(1104, "检测到新设备，需短信验证"),
    /** 短信通道异常 / 降级失败（不暴露上游原始报错） */
    SMS_UNAVAILABLE(1105, "短信服务暂不可用，请稍后重试"),
    /** 图形验证码错误或已失效 */
    CAPTCHA_INVALID(1106, "图形验证码错误或已失效，请重新输入"),
    /** 设备已被撤销 / 二次验证票据失效（须重新登录） */
    DEVICE_REVOKED(1107, "该设备已被撤销，请重新登录"),
    /**
     * 登录已到期，需重新验证（用户裁定：3 天到期<b>强制重新登录</b>，不做短信续期）。
     * 契约形态（与前端 Mock 一致）：<b>HTTP 200 + code=1108</b>，由 {@code JwtAuthFilter} 在会话超窗时写出，
     * 前端按业务码分流至 {@code /login?expired=1&redirect=<原路径>}。
     */
    SESSION_EXPIRED(1108, "登录已到期，请重新登录"),
    /** 该账号未绑定手机号，无法短信验证 */
    PHONE_NOT_BOUND(1109, "该账号未绑定手机号，无法短信验证"),
    /**
     * 该账号无权登录此端（端准入约束，fail-closed）。
     * 触发场景（端类型优先取请求头 {@code X-Client-Type}，其次请求体 {@code clientType}；映射见 {@code ClientAdmissionPolicy}）：
     * ① 端 {@code ADMIN}（PC 管理端）/ {@code WEB}（旧网页端）而角色不在 {@code hrm.auth.pc-allowed-roles}（默认 ADMIN）；
     * ② 端 {@code BOSS}（管理端 H5 / {@code ?as=boss}）而角色不在 {@code hrm.auth.boss-allowed-roles}（默认 ADMIN）；
     * ③ 端 {@code STAFF}（员工端 H5）而角色不在 {@code hrm.auth.staff-allowed-roles}（默认 STAFF + STATION_ADMIN）；
     * ④ <b>缺省 / 未知 / 非法端</b>（未上报端类型或取值不在 {@code ADMIN/BOSS/STAFF/WEB} 之内）。
     * <p>
     * 为什么落 11xx 而非 10xx：11xx 是架构为「认证增强」预留的段（multi-client-architecture §4.1.1），
     * 与 10xx（登录/账号基础错误）互不重叠。
     * <p>
     * 注意：本码是<b>产品 / 审计约束</b>而非安全边界——端类型由客户端自称；真正的安全边界恒为「角色 + 数据范围」。
     */
    LOGIN_CLIENT_NOT_ALLOWED(1110, "该账号无权登录此端"),

    // ==================== 20xx 员工 ====================

    /** 禁用/删除/重置密码/角色降级作用于自身 */
    SELF_OPERATION_FORBIDDEN(2001, "不允许对当前登录账号执行该操作"),
    /** 最后管理员保护 */
    LAST_ADMIN_PROTECTED(2002, "不允许对最后一个可用管理员执行该操作"),
    /** phone 与活跃数据重复 */
    PHONE_EXISTS(2003, "手机号已被其他员工使用"),

    // ==================== 30xx 部门 ====================

    /** 入参引用的 deptId / parentId 无效 */
    DEPT_NOT_FOUND(3001, "指定的部门不存在"),
    /** 部门删除前校验失败：存在子部门 */
    DEPT_HAS_CHILDREN(3002, "存在子部门，不允许删除"),
    /** 部门删除前校验失败：部门下存在员工 */
    DEPT_HAS_EMPLOYEES(3003, "部门下存在员工，不允许删除"),
    /** 同一父部门下重名 */
    DEPT_NAME_EXISTS(3004, "同级部门名称已存在"),

    // ==================== 40xx 驿站 ====================

    /** 入参引用的 stationId 无效 */
    STATION_NOT_FOUND(4001, "指定的驿站不存在"),
    /** station.code 与活跃数据重复 */
    STATION_CODE_EXISTS(4002, "驿站编码已存在"),
    /** 驿站删除前校验失败：驿站下存在员工 */
    STATION_HAS_EMPLOYEES(4003, "驿站下存在员工，不允许删除"),
    /** 新增/编辑员工时选择了停用驿站 */
    STATION_DISABLED(4004, "驿站已停用，不能归属员工"),

    // ==================== 50xx 导入导出 ====================

    /** 导入文件为空或格式不正确 */
    IMPORT_FILE_INVALID(5001, "导入文件为空或格式不正确（仅支持 .xlsx）"),
    /** 导入数据行数超过单次上限 */
    IMPORT_TOO_MANY_ROWS(5002, "导入数据超过单次上限（1000 行）"),
    /** 导入数据存在校验错误（行级明细见 data.errors） */
    IMPORT_DATA_ERROR(5003, "导入数据存在校验错误"),

    // ==================== 60xx 同步任务/采集运行态（plan.md 3.4 已定段位） ====================

    /** 任务状态不允许重试 */
    SYNC_RETRY_NOT_ALLOWED(6001, "任务状态不允许重试"),
    /** 该驿站尚未配置采集 */
    SYNC_CONFIG_NOT_EXISTS(6002, "该驿站尚未配置采集"),

    // ==================== 70xx 包裹（plan.md 3.4 已定段位） ====================

    /** 运单号不存在 */
    PARCEL_NOT_EXISTS(7001, "运单号不存在"),
    /** 包裹状态不允许取件 */
    PARCEL_STATUS_INVALID(7002, "包裹状态不允许取件"),
    /** 该包裹已被他人取件 */
    PARCEL_PICKED_BY_OTHER(7003, "该包裹已被他人取件"),

    // ==================== 80xx 工单 ====================

    /** 工单状态流转非法 */
    WORK_ORDER_STATUS_INVALID(8001, "工单状态流转非法"),
    /** 无权操作该工单 */
    WORK_ORDER_NO_PERMISSION(8002, "无权操作该工单"),
    /** 无权转单该工单 */
    WORK_ORDER_TRANSFER_NO_PERMISSION(8003, "无权转单该工单"),
    /** 转单对象不合法 */
    WORK_ORDER_TRANSFER_TARGET_INVALID(8004, "转单对象不合法"),
    /** 自动派单规则不存在 */
    WORK_ORDER_DISPATCH_RULE_NOT_EXISTS(8005, "自动派单规则不存在"),
    /** 群消息内容为空或无法解析 */
    WORK_ORDER_GROUP_MSG_INVALID(8006, "群消息内容为空或无法解析"),

    // ==================== 90xx 通知 ====================

    /** 通知不存在 */
    NOTIFICATION_NOT_EXISTS(9001, "通知不存在"),
    /** 通知发布范围参数不合法 */
    NOTIFICATION_PUBLISH_SCOPE_INVALID(9002, "通知发布范围参数不合法"),

    // ==================== 91xx 考勤/排班/补卡（原契约 9001~9006 与通知冲突，顺延至 91xx） ====================

    /** 打卡规则未配置 */
    ATTENDANCE_RULE_NOT_CONFIGURED(9101, "该驿站尚未配置打卡规则"),
    /** 不在打卡时间窗内 */
    ATTENDANCE_OUT_OF_TIME_WINDOW(9102, "不在打卡时间窗内"),
    /** WiFi 校验未通过 */
    ATTENDANCE_WIFI_MISMATCH(9103, "WiFi 校验未通过"),
    /** 定位校验未通过（超出围栏范围） */
    ATTENDANCE_LOCATION_MISMATCH(9104, "定位校验未通过，已超出打卡围栏范围"),
    /** 今日该类型打卡已完成（重复打卡） */
    ATTENDANCE_DUPLICATE_CHECK(9105, "今日该类型打卡已完成"),
    /** 班次不存在或已停用 */
    ATTENDANCE_SHIFT_UNAVAILABLE(9106, "班次不存在或已停用"),
    /** 打卡时段不存在 / 规则时段配置非法（同码两语义，前端按接口区分） */
    ATTENDANCE_PERIOD_NOT_FOUND(9107, "打卡时段不存在"),
    /** 该时段当日已有补卡申请或已正常打卡 */
    ATTENDANCE_MAKEUP_DUPLICATE(9108, "该时段当日已有补卡申请或已正常打卡"),
    /** 补卡申请状态不允许该操作 */
    ATTENDANCE_MAKEUP_STATUS_INVALID(9109, "补卡申请状态不允许该操作"),

    // ==================== 92xx KPI ====================

    /** KPI 指标不存在 */
    KPI_METRIC_NOT_EXISTS(9201, "KPI 指标不存在"),
    /** 启用指标的权重合计必须为 100% */
    KPI_WEIGHT_SUM_INVALID(9202, "启用指标的权重合计必须为 100%"),
    /** 该考核月份无有效 KPI 指标 */
    KPI_NO_METRIC(9203, "该考核月份无有效 KPI 指标"),
    /** KPI 评分记录不存在（该员工该月尚未算分） */
    KPI_SCORE_NOT_EXISTS(9204, "该员工该考核月份暂无评分记录"),

    // ==================== 93xx 人事/入离职 ====================

    /** 人事档案不存在 */
    HR_PROFILE_NOT_EXISTS(9301, "人事档案不存在"),
    /** 员工已离职，不可操作 */
    HR_EMPLOYEE_RESIGNED(9302, "员工已离职，不可操作"),
    /** 入职流程状态不允许该操作 */
    HR_ONBOARDING_STATUS_INVALID(9303, "入职流程状态不允许该操作"),
    /** 离职流程状态不允许该操作 */
    HR_OFFBOARDING_STATUS_INVALID(9304, "离职流程状态不允许该操作"),
    /** 薪资档案不存在 */
    HR_SALARY_NOT_EXISTS(9305, "薪资档案不存在"),
    /** 离职薪资结算未完成，不可离岗 */
    HR_SETTLEMENT_UNFINISHED(9306, "离职薪资结算未完成，不可离岗"),

    // ==================== 94xx 财务/工资单 ====================

    /** 计薪规则不存在 */
    FINANCE_RULE_NOT_EXISTS(9401, "计薪规则不存在"),
    /** 工资单不存在 */
    FINANCE_PAYROLL_NOT_EXISTS(9402, "工资单不存在"),
    /** 工资单状态不允许该操作 */
    FINANCE_PAYROLL_STATUS_INVALID(9403, "工资单状态不允许该操作"),
    /** 无权查看他人工资单 */
    FINANCE_PAYROLL_NO_PERMISSION(9404, "无权查看他人工资单"),
    /** 该月工资单已提交审核或已发布，不可重复生成 */
    FINANCE_PAYROLL_GENERATED(9405, "该月工资单已提交审核或已发布，不可重复生成"),

    // ==================== 95xx 同步配置中心 ====================

    /** 配置项不存在 */
    SYNC_ITEM_NOT_EXISTS(9501, "配置项不存在"),
    /** 配置项 Key 已存在 */
    SYNC_ITEM_KEY_EXISTS(9502, "配置项 Key 已存在"),
    /** 配置项被驿站覆盖引用，需 confirm 确认后删除 */
    SYNC_ITEM_IN_USE(9503, "配置项被驿站覆盖，需确认后删除"),
    /** 选项不存在 */
    SYNC_OPTION_NOT_EXISTS(9504, "选项不存在"),
    /** 选项被引用（全局默认硬阻断 / 驿站覆盖需 confirm 确认） */
    SYNC_OPTION_IN_USE(9505, "选项被引用，需确认后删除"),
    /** 配置值不符合该配置项的约束 */
    SYNC_VALUE_INVALID(9506, "配置值不符合该配置项的约束"),
    /** 必填配置项不允许置空 */
    SYNC_REQUIRED_EMPTY(9507, "必填配置项不允许置空"),
    /** 导入内容解析失败或格式不正确 */
    SYNC_IMPORT_PARSE_ERROR(9508, "导入内容解析失败或格式不正确"),
    /** 导入冲突策略参数不合法 */
    SYNC_IMPORT_CONFLICT_INVALID(9509, "导入冲突策略参数不合法"),
    /** 内置配置项或选项不允许删除，只能停用 */
    SYNC_BUILTIN_NOT_DELETABLE(9510, "内置配置项或选项不允许删除，只能停用"),

    // ==================== 96xx 请假（api.md §7.2） ====================

    /** 请假申请不存在 */
    LEAVE_NOT_EXISTS(9601, "请假申请不存在"),
    /** 状态不允许该操作 */
    LEAVE_STATUS_INVALID(9602, "该申请当前状态不支持此操作"),
    /** 时间段与已有申请重叠 */
    LEAVE_OVERLAP(9603, "该时间段与已有申请重叠"),
    /** 日期非法（早于今天 / 结束早于开始 / 超单次上限） */
    LEAVE_DATE_INVALID(9604, "请假日期不合法"),
    /** 无权操作（跨站 / 审自己 / ADMIN 提交） */
    LEAVE_NO_PERMISSION(9605, "无权操作该请假申请"),
    /** 账期工资单已生成，不可撤回 */
    LEAVE_PAYROLL_LOCKED(9606, "该账期工资单已生成，不可撤回"),
    /** 当前状态不允许修改 */
    LEAVE_EDIT_FORBIDDEN(9607, "该申请当前状态不允许修改");

    // TODO(扩展): 60xx~96xx 为 Demo 提案段位；三期专项设计定稿后需与后端错误码表对齐并保持文案同源（架构附录 B）。

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
