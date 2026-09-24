package com.qiujie.dto.station;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 驿站新增/编辑请求（api.md 4.5.2 / 4.5.3，一期允许修改 code，唯一校验生效）。
 */
@Data
public class StationRequest {

    @NotBlank(message = "驿站编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$", message = "驿站编码须为2-50位字母、数字、下划线或短横线")
    private String code;

    @NotBlank(message = "驿站名称不能为空")
    @Size(min = 1, max = 50, message = "驿站名称须为1-50个字符")
    private String stationName;

    @Size(max = 50, message = "负责人姓名不能超过50个字符")
    private String contactPerson;

    /** 手机号格式（非必填，@Pattern 对 null 不校验） */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确，须为11位有效手机号")
    private String contactPhone;

    @Size(max = 255, message = "地址不能超过255个字符")
    private String address;

    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;
}
