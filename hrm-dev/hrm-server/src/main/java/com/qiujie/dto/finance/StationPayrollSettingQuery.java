package com.qiujie.dto.finance;

import lombok.Data;

/**
 * 驿站算薪配置列表查询（I-1；查询参数绑定，仅 ADMIN）。
 * <p>本端点返回「驿站 + 各站算薪配置」，不分页（驿站规模为数十~数百），故不继承 {@code PageQuery}。
 */
@Data
public class StationPayrollSettingQuery {

    /** 驿站筛选（可空；传则仅该站） */
    private Long stationId;

    /** 启用筛选（可空；0/1。未配置驿站按默认 enabled=0 参与判定） */
    private Integer enabled;
}
