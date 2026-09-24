package com.qiujie.vo.sync;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 驿站采集配置出参（Mock {@code routes/syncConfig.js#toConfigVO}）。
 * <p>
 * 同时含「旧字段生效值」（frequency/dataSource/collectStartTime/collectEndTime，保证既有 PC 抽屉与移动端零改动）
 * 与「新模型四层视图」（values/sources/overrides，层级来源可解释）。
 */
@Data
public class SyncStationConfigVO {

    private Long id;
    private Long stationId;
    private String stationName;
    private Boolean enabled;
    /** 旧字段：频率旧码（能反向映射时回旧码） */
    private String frequency;
    /** 旧字段：数据源显示名 */
    private String dataSource;
    private String collectStartTime;
    private String collectEndTime;
    private LocalDateTime lastCollectTime;
    /** SUCCESS / FAILED / NEVER */
    private String lastCollectStatus;
    /** 配置行状态：0=停用，1=启用 */
    private Integer status;
    /** 派生采集状态：NORMAL / ABNORMAL / UNCONFIGURED / DISABLED */
    private String collectState;
    private String collectStateLabel;
    private SyncLastBatchVO lastBatch;
    private LocalDateTime updateTime;
    /** 各配置项生效值（类型化） */
    private Map<String, Object> values = new LinkedHashMap<>();
    /** 各配置项来源：OVERRIDE / INHERIT */
    private Map<String, String> sources = new LinkedHashMap<>();
    /** 覆盖明细（类型化） */
    private Map<String, Object> overrides = new LinkedHashMap<>();
}
