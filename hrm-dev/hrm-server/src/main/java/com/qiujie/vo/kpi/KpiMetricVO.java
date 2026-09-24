package com.qiujie.vo.kpi;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * KPI 指标出参（对齐 Mock {@code toMetricVO}）。
 * <p>
 * 在配置快照基础上补齐三个展示标签（类型/方向/评分模式），等级色由前端按取值映射，服务端不掺视觉决策。
 * {@code createTime / updateTime} 以 {@code yyyy-MM-dd HH:mm:ss} 返回（与其它域 VO 一致）。
 */
@Data
public class KpiMetricVO {

    private Long id;
    private String metricKey;
    private String metricName;
    private String metricType;
    /** 类型中文标签（如「派件量」） */
    private String metricTypeLabel;
    private Integer weight;
    private BigDecimal targetValue;
    private String unit;
    private String direction;
    /** 方向中文标签（「越高越好」/「越低越好」） */
    private String directionLabel;
    /** 评分规则（mode + fullScore） */
    private KpiScoreRuleVO scoreRule;
    /** 评分模式中文标签（「线性折算」/「阶梯评分」/「达标即满分」） */
    private String scoreModeLabel;
    /** 适用角色数组（null=全员适用） */
    private List<String> roleScope;
    private Integer enabled;
    private Integer sortOrder;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
