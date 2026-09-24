package com.qiujie.dto.sync;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 驿站采集配置保存入参（Mock {@code routes/syncConfig.js#save}）。
 * <p>
 * 四类入参落到同一处：① 布尔位 {@code enabled}/{@code status}；② 旧字段别名 {@code frequency}/{@code dataSource}/
 * {@code collectStartTime}/{@code collectEndTime}（兼容层）；③ 新模型 {@code overrides}/{@code resetKeys}。
 * {@code enabled}/{@code status} 用 {@code Object} 承接：Mock 同时接受 0/1 与 true/false，归一在 Service。
 */
@Data
public class StationSyncConfigSaveRequest {

    /** 采集开关：0/1 或 true/false（仅 ADMIN） */
    private Object enabled;

    /** 配置行状态：0/1 或 true/false */
    private Object status;

    /** 旧字段别名：采集频率（兼容旧码 HOURLY / EVERY_2H / EVERY_4H / DAILY） */
    private String frequency;

    /** 旧字段别名：数据源名称（空串表示重置为继承全局默认） */
    private String dataSource;

    /** 旧字段别名：采集开始时间 HH:mm */
    private String collectStartTime;

    /** 旧字段别名：采集结束时间 HH:mm（可 24:00） */
    private String collectEndTime;

    /** 新模型：itemKey → 覆盖值 */
    private Map<String, Object> overrides;

    /** 新模型：重置回全局默认的配置项 Key 列表 */
    private List<String> resetKeys;
}
