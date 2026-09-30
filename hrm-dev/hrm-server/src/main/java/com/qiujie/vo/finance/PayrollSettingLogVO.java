package com.qiujie.vo.finance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 算薪配置变更历史出参（I-9，api.md §4.12.17，仅 ADMIN，无字段裁剪）。
 * <p>{@code before}/{@code after} 存 JSON 文本，出参解析成对象以便前端直接消费；结构为白名单键
 * {@code {enabled,payrollDay,payrollTime,notifyEnabled,remark}}（不含凭据 / 个人信息）。
 */
@Data
public class PayrollSettingLogVO {

    private Long id;

    private Long stationId;

    /** 动作：CREATE/UPDATE/ENABLE/DISABLE */
    private String action;

    private Long operatorId;

    private String operatorName;

    private String operatorRole;

    /** 变更前快照（CREATE 时为 null） */
    private Object before;

    /** 变更后快照 */
    private Object after;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime time;

    private String remark;
}
