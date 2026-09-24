package com.qiujie.vo.workorder;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动派单规则出参（对齐 Mock {@code dispatchRuleVO}）：{@code enabled} 布尔化（Mock {@code rule.enabled === 1}）。
 */
@Data
public class DispatchRuleVO {

    private Long id;

    private String keyword;

    private Integer workOrderType;

    private Integer priority;

    private Long defaultAssigneeId;

    private boolean enabled;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
