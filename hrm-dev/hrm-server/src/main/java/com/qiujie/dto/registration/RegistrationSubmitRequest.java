package com.qiujie.dto.registration;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * R-2 提交注册申请入参（公开端点 {@code POST /api/v1/registration}）。
 * <p>
 * <b>字段白名单（M-3/S-3，registration-design §11.5 表 A）</b>：本 DTO 编译期<b>不含</b>
 * {@code role/deptId/stationId(事实)/薪资/pwdChanged/status/username} 等审批侧字段，
 * 从类型层面切断「注册注入」；意向字段以 {@code intent*} 命名表达「仅意向」。
 * <p>
 * <b>未知字段拒绝（M-3/S-3，实现说明）</b>：Spring Boot 的 {@code Jackson2ObjectMapperBuilder}
 * 默认<b>关闭</b> {@code FAIL_ON_UNKNOWN_PROPERTIES}，且 {@code @JsonIgnoreProperties(ignoreUnknown=false)}
 * 无法覆盖全局关闭；故本 DTO 以 {@link JsonAnySetter} <b>在类级别</b>兜住所有未声明字段并直接抛错
 * （夹带 {@code role}/{@code basicSalary} 等 → 反序列化失败 → {@code GlobalExceptionHandler} 统一 400），
 * <b>不改全局 Jackson 行为</b>（避免波及其余端点的既有宽容契约）。
 * <p>
 * 校验口径对齐 §3.2 R-2：姓名 2-20、手机号 {@code ^1[3-9]\d{9}$}、验证码 6 位、
 * 意向岗位 ≤50、密码选填（8-20 位含字母数字，仅留痕）、条款版本必填。
 */
@Data
public class RegistrationSubmitRequest {

    /** 姓名（2-20 字） */
    @NotBlank(message = "请输入姓名")
    @Size(min = 2, max = 20, message = "姓名长度须为 2-20")
    private String realName;

    /** 手机号 */
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 短信验证码（REGISTER 场景，6 位；校验成功一次性作废） */
    @NotBlank(message = "请输入验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String smsCode;

    /** 意向驿站（仅意向；服务端再审存在 + 启用，4001/4004） */
    @NotNull(message = "请选择意向驿站")
    private Long intentStationId;

    /** 意向岗位（自由文本，≤50 字；仅意向，非档案事实） */
    @Size(max = 50, message = "意向岗位不超过 50 字")
    private String intentPosition;

    /**
     * 注册自设密码（<b>选填</b>；U-02 定稿：<b>不作为员工初始口令</b>，仅合规留痕，终态清散列）。
     * 非空时须 8-20 位且同时含字母与数字；为空则跳过校验。
     */
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$", message = "密码须为 8-20 位且同时包含字母和数字")
    private String password;

    /** 已同意的服务条款版本（合规留痕） */
    @NotBlank(message = "请同意服务条款")
    @Size(max = 20, message = "服务条款版本不合法")
    private String agreementVersion;

    /**
     * 未知字段兜底：任何未在表 A 声明的字段（role / deptId / stationId / salary / username / status 等）
     * 一律拒绝。抛出的异常由 Jackson 包装为 {@code JsonMappingException}，经
     * {@code GlobalExceptionHandler} 转 HTTP 400（M-3/S-3）。
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("不支持的字段：" + name);
    }
}
