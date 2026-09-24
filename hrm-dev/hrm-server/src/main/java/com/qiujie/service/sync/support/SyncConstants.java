package com.qiujie.service.sync.support;

import java.util.List;

/**
 * 同步配置中心结构常量（P9）。
 * <p>
 * 为什么用常量而非配置：CSV 列序、记录类型、值类型、选项集 Key 属<b>契约结构</b>（前端按列下标读写、
 * 校验按枚举分发），改动即破坏契约，不属于「可调超参」；只有行数上限 / 列数这种规模参数才走
 * {@code hrm.algo.sync.*}（见 {@code AlgoProperties.Sync}）。
 * 与 Mock {@code syncConfigCsv.js} / {@code syncConfigStore.js} 逐字对齐。
 */
public final class SyncConstants {

    private SyncConstants() {
    }

    // ==================== 值类型 / 生效范围 ====================

    /** 值类型枚举（顺序即表单渲染与校验分发依据） */
    public static final List<String> VALUE_TYPES = List.of("SINGLE_SELECT", "NUMBER", "TEXT", "TIME", "TIME_RANGE");
    /** 生效范围：仅 STATION 允许按驿站覆盖 */
    public static final List<String> ITEM_SCOPES = List.of("GLOBAL", "STATION");

    // ==================== Key 校验正则 ====================

    /** 配置项 Key：小写字母开头，仅含小写字母/数字/下划线，长度 2-40 */
    public static final String ITEM_KEY_PATTERN = "^[a-z][a-z0-9_]{1,39}$";
    /** 选项 Key：字母开头，仅含字母/数字/下划线/短横线，长度 1-40 */
    public static final String OPTION_KEY_PATTERN = "^[A-Za-z][A-Za-z0-9_-]{0,39}$";

    // ==================== CSV 契约 ====================

    /** 固定 16 列表头（导出即导入的列序真源） */
    public static final List<String> EXPORT_HEADER = List.of(
            "记录类型", "配置项Key", "配置项名称", "值类型", "是否必填", "默认值", "单位", "取值范围或正则",
            "排序", "是否启用", "选项Key", "选项显示名", "附加属性", "驿站", "配置值", "备注");
    /** 记录类型（大小写敏感） */
    public static final List<String> RECORD_TYPES = List.of("ITEM", "OPTION", "GLOBAL", "STATION");
    /** 导出范围 */
    public static final List<String> EXPORT_SCOPES = List.of("ITEMS", "ITEMS_GLOBAL", "ALL", "TEMPLATE");
    /** 冲突策略 */
    public static final List<String> CONFLICT_STRATEGIES = List.of("OVERWRITE", "SKIP", "APPEND");

    /** 导出文件名日期格式 */
    public static final String FILENAME_DATE = "yyyyMMdd";
    /** 模板固定文件名（便于反复下载同名） */
    public static final String TEMPLATE_FILENAME = "同步配置_导入模板.csv";

    // ==================== 同步任务状态机 ====================

    /** 待领取 */
    public static final int TASK_PENDING = 0;
    /** 执行中 */
    public static final int TASK_RUNNING = 1;
    /** 成功 */
    public static final int TASK_SUCCESS = 2;
    /** 失败 */
    public static final int TASK_FAILED = 3;

    /** 日志级别：INFO */
    public static final int LOG_INFO = 0;
    /** 日志级别：WARN */
    public static final int LOG_WARN = 1;
    /** 日志级别：ERROR */
    public static final int LOG_ERROR = 2;

    /** 最近采集状态：从未采集 */
    public static final String COLLECT_NEVER = "NEVER";
    /** 最近采集状态：失败 */
    public static final String COLLECT_FAILED = "FAILED";

    // ==================== 选项集 Key 与集合层元数据 ====================

    public static final String SET_DATA_SOURCE = "data_source";
    public static final String SET_COLLECT_FREQUENCY = "collect_frequency";
    public static final String SET_TIME_TEMPLATE = "time_template";

    /**
     * 选项集集合层元数据（setKey → 名称/说明/内置）。
     * <p>
     * 为什么内置在代码而非表：{@code sync_config_option} 只有选项行，没有「选项集」实体表；
     * 选项集是与配置项 Key 强耦合的结构（单选型不指定选项集时默认与 itemKey 同名），本就属代码约束。
     * TODO(扩展): 若后续需要「管理员新建选项集」，须新增 sync_config_option_set 表并回填，届时迁移本表。
     */
    public static final List<String[]> OPTION_SET_META = List.of(
            new String[]{SET_DATA_SOURCE, "数据源", "采集数据来源候选项"},
            new String[]{SET_COLLECT_FREQUENCY, "采集频率", "采集间隔档位"},
            new String[]{SET_TIME_TEMPLATE, "采集时段模板", "预置采集时段"});

    /** 选项集集合层元数据：名称 */
    public static String optionSetName(String setKey) {
        for (String[] meta : OPTION_SET_META) {
            if (meta[0].equals(setKey)) {
                return meta[1];
            }
        }
        return setKey;
    }

    /** 选项集集合层元数据：说明 */
    public static String optionSetDescription(String setKey) {
        for (String[] meta : OPTION_SET_META) {
            if (meta[0].equals(setKey)) {
                return meta[2];
            }
        }
        return null;
    }

    /** 选项集是否为内置集合（内置集合本身不可删，仅其选项按 builtin 逐项判断） */
    public static boolean isBuiltinSet(String setKey) {
        return SET_DATA_SOURCE.equals(setKey) || SET_COLLECT_FREQUENCY.equals(setKey) || SET_TIME_TEMPLATE.equals(setKey);
    }
}
