package com.qiujie.enums;

import lombok.Getter;

/**
 * 业务错误码枚举（与 api.md 第 2 章错误码表完全一致）。
 * HTTP 状态映射规则（api.md 1.3）：401/403/404 同步 HTTP 状态码，其余业务错误 HTTP 200。
 */
@Getter
public enum ErrorCode {

    /** 成功 */
    SUCCESS(200, "success"),
    /** 参数校验失败（message 为具体字段说明） */
    BAD_REQUEST(400, "参数校验失败"),
    /** 未登录 / Token 失效（含被顶下线、被强制下线） */
    UNAUTHORIZED(401, "未登录或登录态已失效"),
    /** 已登录但无权限（STAFF 访问 ADMIN 接口） */
    FORBIDDEN(403, "无权限访问该资源"),
    /** 路径资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 系统内部错误（堆栈仅记日志） */
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),

    /** 登录失败（不区分账号不存在与密码错误，防账号探测） */
    LOGIN_FAILED(1001, "用户名或密码错误"),
    /** 登录账号处于禁用状态 */
    ACCOUNT_DISABLED(1002, "账号已禁用，请联系管理员"),
    /** 新增员工时 username 与活跃数据重复 */
    USERNAME_EXISTS(1003, "登录账号已存在"),
    /** 修改本人密码时原密码校验失败 */
    OLD_PASSWORD_ERROR(1004, "原密码错误"),

    /** 禁用/删除/重置密码/角色降级作用于自身 */
    SELF_OPERATION_FORBIDDEN(2001, "不允许对当前登录账号执行该操作"),
    /** 最后管理员保护 */
    LAST_ADMIN_PROTECTED(2002, "不允许对最后一个可用管理员执行该操作"),
    /** phone 与活跃数据重复 */
    PHONE_EXISTS(2003, "手机号已被其他员工使用"),

    /** 入参引用的 deptId / parentId 无效 */
    DEPT_NOT_FOUND(3001, "指定的部门不存在"),
    /** 部门删除前校验失败：存在子部门 */
    DEPT_HAS_CHILDREN(3002, "存在子部门，不允许删除"),
    /** 部门删除前校验失败：部门下存在员工 */
    DEPT_HAS_EMPLOYEES(3003, "部门下存在员工，不允许删除"),
    /** 同一父部门下重名 */
    DEPT_NAME_EXISTS(3004, "同级部门名称已存在"),

    /** 入参引用的 stationId 无效 */
    STATION_NOT_FOUND(4001, "指定的驿站不存在"),
    /** station.code 与活跃数据重复 */
    STATION_CODE_EXISTS(4002, "驿站编码已存在"),
    /** 驿站删除前校验失败：驿站下存在员工 */
    STATION_HAS_EMPLOYEES(4003, "驿站下存在员工，不允许删除"),
    /** 新增/编辑员工时选择了停用驿站 */
    STATION_DISABLED(4004, "驿站已停用，不能归属员工"),

    /** 导入文件为空或格式不正确 */
    IMPORT_FILE_INVALID(5001, "导入文件为空或格式不正确（仅支持 .xlsx）"),
    /** 导入数据行数超过单次上限 */
    IMPORT_TOO_MANY_ROWS(5002, "导入数据超过单次上限（1000 行）"),
    /** 导入数据存在校验错误（行级明细见 data.errors） */
    IMPORT_DATA_ERROR(5003, "导入数据存在校验错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
