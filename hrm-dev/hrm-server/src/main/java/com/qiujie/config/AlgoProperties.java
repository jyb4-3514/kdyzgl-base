package com.qiujie.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 算法参数强类型配置（{@code hrm.algo.*}，键名与默认值逐键对齐 algo-hrm-server.md §11）。
 * <p>
 * 为什么必须外置：算法阈值/权重属工程与性能超参，须可配可审计；代码内严禁内联常量（规则 §11.4、反模式 A03）。
 * 业务口径（KPI 指标、计薪规则、SLA、派单规则、考勤规则、请假开关）走数据库热改，不在本类（ADR-05）。
 * 本类仅承载配置，本批次**不实现任何算法逻辑**（P1~P10 批次填充）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hrm.algo")
public class AlgoProperties {

    private Kpi kpi = new Kpi();
    private Payroll payroll = new Payroll();
    private Schedule schedule = new Schedule();
    private Attendance attendance = new Attendance();
    private Leave leave = new Leave();
    private Dispatch dispatch = new Dispatch();
    private Parcel parcel = new Parcel();
    private RateLimit ratelimit = new RateLimit();
    private Log log = new Log();
    private Sync sync = new Sync();

    // ==================== §11.1 KPI ====================

    @Data
    public static class Kpi {
        /** LINEAR 模式达成率封顶系数 */
        private double linearCapRatio = 1.0;
        /** TIERED 阶梯（minAchievement→ratio，降序） */
        private List<Tier> tieredTiers = new ArrayList<>(List.of(
                new Tier(1.0, 1.0), new Tier(0.9, 0.9), new Tier(0.8, 0.8),
                new Tier(0.6, 0.6), new Tier(0.0, 0.0)));
        /** 等级阈值（降序） */
        private List<Level> levels = new ArrayList<>(List.of(
                new Level(90, "EXCELLENT"), new Level(80, "GOOD"),
                new Level(70, "PASS"), new Level(0, "IMPROVE")));
        private Quantile quantile = new Quantile();
        /** 启用权重合计目标（固定 100） */
        private int weightSumTarget = 100;
        /** 权重合计容差 */
        private int weightSumTolerance = 0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tier {
        private double minAchievement;
        private double ratio;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Level {
        private int min;
        private String level;
    }

    @Data
    public static class Quantile {
        /** 是否启用分位映射（未裁定前保持 false，见架构 Q2） */
        private boolean enabled = false;
        /** 并列处理策略：MID_RANK / MIN / MAX */
        private String tiePolicy = "MID_RANK";
        /** 启用分位的最小样本量（不足则降级） */
        private int minSamples = 20;
    }

    // ==================== §11.2 工资 ====================

    @Data
    public static class Payroll {
        /** 考勤指标 → 统计字段映射（{@code ABSENT_OR_LEAVE} 为 T1 组合指标：旷工 + 请假班次） */
        private Map<String, String> attendanceFieldMap = new LinkedHashMap<>(Map.of(
                "LATE", "lateCount", "EARLY_LEAVE", "earlyLeaveCount", "ABSENT", "absentCount",
                "ABNORMAL", "abnormalCount", "LEAVE", "leaveCount",
                "ABSENT_OR_LEAVE", "absentOrLeaveCount"));
        /** 未显式配 capRatio 时的默认上限 */
        private double defaultCapRatio = 1.0;
        /** 未知 source 处理：ZERO / THROW */
        private String unknownSourcePolicy = "ZERO";
        /**
         * 是否允许负净额（Q3 口径已由 T3 裁定：<b>实发不低于 0</b>）。
         * <p>
         * 默认 {@code false}：净额下限置 0（{@link com.qiujie.service.finance.support.PayrollTotalsPolicy}），
         * 封顶链级 3（方案 §7.1）。T3 前默认 true 与 Mock 等价（允许负净额，实测最小 −360 元），
         * T3 裁定后改为 false，使「整月无打卡」由原型 −6000 钳到 0。
         */
        private boolean allowNegativeNet = false;
        /** cap<=0 语义：ZERO_MEANS_NO_CAP / ZERO_MEANS_ZERO */
        private String itemCapSemantics = "ZERO_MEANS_NO_CAP";

        // ==================== S2b 班次制算薪（方案 algo-payroll-shift.md v2.0 §7，键名与默认值逐键照抄） ====================

        /**
         * 班次制生效账期（{@code yyyy-MM}）：{@code month < 该值} 走旧「按天」统计路径，
         * 保证已出账/历史账期零语义突变（方案 §6 主保险）。上线月份由发布计划确定。
         */
        private String shiftModelFromMonth = "2026-10";
        /**
         * 班次序号判定界值（分钟）：{@code start_time} 分钟数 &lt; 该值 = 早班(0)，否则晚班(1)。
         * <p>
         * 注：恰为 12:00 起始的班次会被判为晚班（与晚班键冲突）；删中班后须校验无此类数据（方案 §7 建议项 S6）。
         */
        private int middayBoundaryMinute = 720;
        /** 单班制历史记录哨兵：{@code period_name} 命中则覆盖当日全部排班班次（方案 §6 次保险） */
        private String legacyPeriodSentinel = "全天班";
        /** 需按出勤班次折算的定薪字段（T2 已裁定仅 basicSalary；扩充须用户再裁定） */
        private List<String> proratedFields = new ArrayList<>(List.of("basicSalary"));
        /** 旷工罚款单价（元/班次）默认值；实际以规则项 {@code params.amount} 为准 */
        private BigDecimal absentFinePerShift = new BigDecimal("100");
        /** 规则项级绝对额封顶默认值（对应 {@code ABSENT_FINE.params.cap}）；&lt;=0 按 itemCapSemantics 解读 */
        private BigDecimal absentFineCap = BigDecimal.ZERO;
        /** 配置级比例封顶：罚款上限 = 折算后基本工资 × 该系数；&lt;=0 = 本级不封顶（默认关闭） */
        private BigDecimal absentFineCapRatio = BigDecimal.ZERO;
        /** 应出班次为 0 时兜底：FULL_BASIC（默认，不因数据缺失克扣）/ ZERO */
        private String zeroSchedulePolicy = "FULL_BASIC";
        /** 全勤奖指标（T1 已裁定）：ABSENT_OR_LEAVE（旷工+请假，请假算缺勤）/ ABSENT（仅旷工，旧口径） */
        private String fullAttendMetric = "ABSENT_OR_LEAVE";
        /** 迟到计数粒度（T5 已裁定保持按次）：PER_CARD（每张有效 ON 迟到卡计 1 次，= 现状）/ PER_DAY（按日去重，口径变更） */
        private String lateGranularity = "PER_CARD";
    }

    // ==================== §11.3 排班 ====================

    @Data
    public static class Schedule {
        /** 每班每日最少在岗 */
        private int minPerShift = 2;
        /** 连续工作上限（天） */
        private int maxConsecutiveWork = 5;
        /** 轮休周期（天） */
        private int restCycleDays = 6;
        private ScheduleWeights weights = new ScheduleWeights();
        /** 模拟退火超参 */
        private Sa sa = new Sa();
    }

    @Data
    public static class ScheduleWeights {
        private double minStaff = 1000;
        private double consecutive = 1000;
        private double coverageDeficit = 10;
        private double shiftBalance = 1;
        private double restSpread = 0.1;
    }

    @Data
    public static class Sa {
        private int iterations = 6000;
        private double initialTemp = 8;
        private double cooling = 0.9995;
    }

    // ==================== §11.4 异常检测 ====================

    @Data
    public static class Attendance {
        private Anomaly anomaly = new Anomaly();

        // ==================== 多班次排班（ARCH-C-2~5；algorithm-multi-shift-scheduling.md §2 参数表） ====================
        // 键名遵循既有前缀体例 hrm.algo.attendance.*；禁止内联阈值（规则 §11.4、反模式 A03）。

        /** 是否允许同员工同天班次时间重叠（默认 false：重叠提交被拒） */
        private boolean allowShiftOverlap = false;
        /** 单日班次上限（默认 2，与计薪序号编码 {@code epochDay×2+ordinal} 绑定；>2 须先做计薪编码扩展） */
        private int maxShiftsPerDay = 2;
        /** 重叠判定容差（分钟；0=严格半开区间，>0 容忍端点轻微重叠） */
        private int overlapToleranceMinutes = 0;
        /** {@code end_time ≤ start_time} 是否视为跨零点顺延（true：e += 1440 归一；false：判脏数据拒绝） */
        private boolean crossMidnightAsNextDay = true;
        /** 同员工同天重复排同一班次的处置：IDEMPOTENT=视为 no-op（默认）/ REJECT=报错 */
        private String duplicateShiftPolicy = "IDEMPOTENT";
        /** 同日同员工各排班次 {@code ordinal} 是否必须互异（默认 true；关闭将导致计薪静默少算） */
        private boolean requireDistinctOrdinalPerDay = true;
        /**
         * 应到/缺卡粒度：{@code PER_SHIFT}（默认，B7b 用户已裁定：按班次统计）/
         * {@code PER_DAY}（旧按人去重口径，回落开关）。算法 §2 / §8-4。
         */
        private String absentGranularity = "PER_SHIFT";
    }

    @Data
    public static class Anomaly {
        /** 用 median/MAD（true）或 mean/std（false） */
        private boolean useRobust = true;
        /** 迟到频次 z 告警阈值 */
        private double lateWarn = 3.5;
        /** 迟到频次 z 严重阈值 */
        private double lateCritical = 5.0;
        /** 连续缺卡预警阈值（天，待 Q6 确认，未裁定前 3） */
        private int consecutiveAbsent = 3;
        /** 最小样本量（不足则不出结论） */
        private int minSamples = 5;
        /** 观察窗口（天） */
        private int windowDays = 30;
    }

    // ==================== §11.5 请假 ====================

    @Data
    public static class Leave {
        /** 单次连续跨度上限（自然天） */
        private int maxLeaveDays = 30;
        /** 占用额度的状态集 */
        private List<String> occupiedStatus = new ArrayList<>(List.of(
                "PENDING_STATION", "PENDING_BOSS", "APPROVED"));
        /** 账期锁判定：非 DRAFT/REJECTED 即锁 */
        private String lockStatusPolicy = "NON_DRAFT_REJECTED";
        /**
         * 假别 → 计薪口径（NATURAL 按自然日 / SCHEDULED 逐日查排班）。
         * <p>
         * 默认值与 Mock {@code dict.LEAVE_TYPE[*].countMode} 逐位一致；作为唯一运行时真源，
         * 避免「字典一份、配置一份」双来源漂移。
         */
        private Map<String, String> countModeMap = defaultCountModeMap();
        /** 扣款开关默认值（{@code leave_setting} 无行时的兜底，Q6 未裁定前保持 false 与 Mock 一致） */
        private boolean deductEnabledDefault = false;
        /** 通知开关：关闭后不再投递请假申请/结果通知（默认 true，与 Mock 行为一致） */
        private boolean notifyEnabled = true;

        // TODO(扩展): countModeMap / deductEnabledDefault / notifyEnabled 三键为 P7 新增，
        //   algo-hrm-server.md §11.5 与 server-architecture.md §5.1 的 leave 段尚未登记，待主智能体回填文档。

        /** 假别计薪口径默认映射（对齐 Mock {@code dict.LEAVE_TYPE}） */
        private static Map<String, String> defaultCountModeMap() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("ANNUAL", "SCHEDULED");
            map.put("PERSONAL", "SCHEDULED");
            map.put("SICK", "SCHEDULED");
            map.put("COMPENSATORY", "SCHEDULED");
            map.put("MARRIAGE", "NATURAL");
            map.put("MATERNITY", "NATURAL");
            map.put("PATERNITY", "NATURAL");
            map.put("BEREAVEMENT", "NATURAL");
            map.put("OTHER", "SCHEDULED");
            return map;
        }
    }

    // ==================== §11.6 派单 ====================

    @Data
    public static class Dispatch {
        /** 按优先级 SLA（小时）：0=低 1=中 2=高（待 Q4 确认，未裁定前 48/24/8） */
        private Map<Integer, Integer> slaHours = new LinkedHashMap<>(Map.of(0, 48, 1, 24, 2, 8));
        private DispatchWeights weights = new DispatchWeights();
        /**
         * 关键词特异度权重（<b>列表</b>承载，元素键为业务中文关键词）。
         * <p>
         * 为什么不用 {@code Map<String,Double>} 直接绑定：Spring Boot 绑定前会把属性名经
         * {@code ConfigurationPropertyName.adapt()} 规范化，该方法会<b>静默丢弃非 ASCII 字符</b>。
         * YAML 拍平后的子键 {@code hrm.algo.dispatch.keyword-weights.破损}（及 丢失/故障/投诉）
         * 会被归并为同一个父名 {@code hrm.algo.dispatch.keyword-weights}，Binder 遂把该父名的
         * 首个值（{@code 1.0}）当作整张 Map 的值 → 启动报
         * {@code ConverterNotFoundException: Double → Map}。这是框架行为边界（flow/block 写法、
         * 文件编码均无关，官方 externalized-configuration 亦记载「Map 键含非小写字母数字须用
         * {@code [key]} 记法」）；故改为 ASCII 字段名的列表承载，再派生为 Map。
         * TODO(扩展): 承载形式由 Map 改为 List，algo-hrm-server.md §11.6 与 server-architecture.md
         *   §5.1 的 dispatch 段需主智能体回填登记。
         */
        private List<KeywordWeight> keywordWeightList = defaultKeywordWeightList();

        /** 由 {@link #keywordWeightList} 派生的「关键词 → 权重」映射，供派单分类器消费（签名保持不变） */
        public Map<String, Double> getKeywordWeights() {
            Map<String, Double> map = new LinkedHashMap<>();
            if (keywordWeightList != null) {
                for (KeywordWeight item : keywordWeightList) {
                    if (item != null && item.getKeyword() != null && !item.getKeyword().isEmpty()) {
                        map.put(item.getKeyword(), item.getWeight());
                    }
                }
            }
            return map;
        }

        /** 关键词权重默认项（与 Mock/algo-hrm-server.md §11.6 逐位一致） */
        private static List<KeywordWeight> defaultKeywordWeightList() {
            return new ArrayList<>(List.of(
                    new KeywordWeight("破损", 1.0), new KeywordWeight("丢失", 3.0),
                    new KeywordWeight("故障", 1.0), new KeywordWeight("投诉", 2.5)));
        }

        /** 无关键词兜底类型 */
        private int defaultType = 4;
        /** 无关键词兜底优先级 */
        private int defaultPriority = 1;
        /** SLA 兜底小时数：{@link #slaHours} 缺失某优先级键时的回落值（取中优先级口径） */
        private int defaultSlaHours = 24;
        /** 负载统计窗口（天）：S6「负载均衡」项取窗口内接单量的归一 */
        private int loadWindowDays = 7;
        /** 技能画像统计窗口（天）：S6「技能匹配」项取窗口内已了结工单的类型占比 */
        private int skillWindowDays = 90;
        /** 技能画像最小样本量：窗口内已了结工单不足此数则技能分按 0（不臆测无历史员工的特长） */
        private int minSkillSamples = 1;
        /** 单件预估处理时长（小时）：由未完成量推算就绪度等待代理（wait = openLoad × 本值） */
        private double estimatedServiceHours = 1.0;
        /**
         * 是否启用关键词特异度加权判定（算法 S6 第一处改造）。
         * <p>
         * 默认 {@code false}：保持与 Mock「顺序首个命中」逐位等价（上线即无行为变化，规则 §11.4 / Q5 未裁定）；
         * 裁定后置 {@code true} 即启用加权打分（12 条歧义样本准确率 0.8333 → 1.0000），无需改代码。
         * TODO(扩展): 本键与 defaultSlaHours / loadWindowDays / skillWindowDays / minSkillSamples /
         *   estimatedServiceHours 为 P8 新增，algo-hrm-server.md §11.6 与 server-architecture.md §5.1 的
         *   dispatch 段尚未登记，待主智能体回填文档。
         */
        private boolean keywordWeighted = false;
    }

    @Data
    public static class DispatchWeights {
        private double urgency = 4.0;
        private double skill = 2.5;
        private double load = 1.5;
        private double speed = 2.0;
    }

    /** 关键词权重项（列表承载，绕开框架对非 ASCII Map 键的丢弃，见 {@link Dispatch#keywordWeightList}） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordWeight {
        private String keyword;
        private double weight;
    }

    // ==================== §11.7 包裹 ====================

    @Data
    public static class Parcel {
        private Forecast forecast = new Forecast();
        private Capacity capacity = new Capacity();
        private Outlier outlier = new Outlier();
        private ParcelPage page = new ParcelPage();
        private ParcelTrend trend = new ParcelTrend();
        private ParcelRanking ranking = new ParcelRanking();
    }

    @Data
    public static class Forecast {
        /** MA / HOLT / HOLT_WINTERS / SEASONAL_NAIVE */
        private String model = "HOLT_WINTERS";
        private double alpha = 0.5;
        private double beta = 0.2;
        private double gamma = 0.4;
        /** 季节周期（天） */
        private int seasonPeriod = 7;
        /** 预测步长（天） */
        private int horizonDays = 7;
        /**
         * 采用 Holt-Winters 的最小历史点数（不足则降级 MA）。
         * <p>
         * 默认 14 = 2×季节周期，与架构 §6.2 P10 验收⑤「历史点 &lt; 2×季节周期 → 回落 MA(7)」等价。
         */
        private int minSamples = 14;
        /** MA 降级窗口（天）：样本仍不足该值时再回落「有效点均值」恒定预测 */
        private int maWindow = 7;
        /**
         * 是否在 {@code GET /parcels/trend} 出参**附加**预测字段（契约扩展开关）。
         * <p>
         * 默认 {@code false}：出参与 Mock 逐位一致（{@code [{date,inbound,pickup}]}），**不破坏既有契约**；
         * 置 {@code true} 后每个点附加 {@code forecastInbound}（模型拟合/一步预测值），数组长度与既有字段不变。
         */
        private boolean appendForecast = false;
    }

    @Data
    public static class Capacity {
        /** 站均货架位（件） */
        private int shelfCapacity = 900;
        /** 利用率告警阈值 */
        private double utilWarn = 0.8;
        /** 利用率严重阈值 */
        private double utilCritical = 0.95;
        /**
         * 是否在 {@code GET /parcels/ranking} 出参**附加**容量/热力字段（契约扩展开关）。
         * <p>
         * 默认 {@code false}：出参与 Mock 逐位一致；置 {@code true} 后每行附加
         * {@code pendingPickup / utilization / capacityLevel / outlier}。
         */
        private boolean appendToRanking = false;
    }

    @Data
    public static class Outlier {
        /** IQR 离群系数 */
        private double iqrK = 1.5;
    }

    @Data
    public static class ParcelPage {
        /** 默认页大小 */
        private int pageSize = 20;
        /** 分页模式：CURSOR / OFFSET（ADR-06 默认游标） */
        private String mode = "CURSOR";
        /**
         * OFFSET 兜底模式允许的最大「已跳过页数」（pageNum-1）。
         * <p>
         * 超限不再执行深分页（避免 20 万级 OFFSET 扫描 20020 行，架构 R-6），返回空 list + 正确 total；
         * 契约（Mock 无此上限）与实现差异见 P10 变更日志 TODO(扩展)。
         */
        private int maxOffsetDepth = 1000;
        /**
         * 是否在 {@code GET /parcels} 出参回填 {@code nextCursor}（游标翻页 opt-in 开关）。
         * <p>
         * 默认 {@code false}：出参与 Mock 逐位一致（不含 {@code nextCursor}）；置 {@code true} 后
         * 首页起即返回 {@code nextCursor}，配合请求参数 {@code cursor} 实现 keyset 翻页。
         */
        private boolean exposeCursor = false;
    }

    /** 趋势端点参数（P10 新增） */
    @Data
    public static class ParcelTrend {
        /** 趋势天数下限（含） */
        private int minDays = 1;
        /** 趋势天数上限（含），对齐 Mock 的 Math.min(30, ...) */
        private int maxDays = 30;
    }

    /** 排行端点参数（P10 新增） */
    @Data
    public static class ParcelRanking {
        /** 默认排序指标（包裹量降序，对齐 Mock 缺省行为） */
        private String defaultMetric = "parcelTotal";
        /** 允许切换的排序指标（其余值回退默认） */
        private List<String> sortableMetrics = new ArrayList<>(List.of("pickupRate", "abnormalRate"));
    }

    // TODO(扩展): P10 新增键（forecast.minSamples / maWindow / appendForecast、capacity.appendToRanking、
    //   page.maxOffsetDepth、trend.*、ranking.*）尚未登记进 algo-hrm-server.md §11.7 与
    //   server-architecture.md §5.1 的 parcel 段，待主智能体回填文档。

    // ==================== §11.8 限频与日志 ====================

    @Data
    public static class RateLimit {
        /** 桶深（突发上限） */
        private int bucketCapacity = 10;
        /** 补充速率（个/秒） */
        private double refillPerSecond = 5;
        /** 排队等待上限（秒） */
        private int maxQueueWaitSeconds = 30;
        /** 限频维度：BOT / EMPLOYEE / BOT_AND_EMPLOYEE */
        private String dimension = "BOT_AND_EMPLOYEE";
    }

    @Data
    public static class Log {
        /** 指纹去重窗口（秒） */
        private int dedupeWindowSeconds = 10;
        /** 单字段文本截断长度 */
        private int textMax = 2000;
        /** 惰性窗口清扫间隔（条） */
        private int sweepEvery = 2000;
        /** 兜底环缓冲上限（仅降级路径） */
        private int ringBufferCap = 200;
    }

    // ==================== §11.9 同步配置中心（P9） ====================

    /**
     * 同步配置中心参数（P9 新增）。
     * <p>
     * 默认值与 Mock {@code syncConfigCsv.js} 逐位一致（列数 16、导入行上限 1000），
     * 「上线即无行为变化」。CSV 列序与记录类型属契约结构（非可调超参），在
     * {@code SyncConstants} 中固化，不在此类。
     * TODO(扩展): 本键为 P9 新增，algo-hrm-server.md §11 与 server-architecture.md §5.1 的 sync 段尚未登记，
     *   待主智能体回填文档。
     */
    @Data
    public static class Sync {
        /** 导入数据行上限（对齐 Mock IMPORT_CODE.ROW_LIMIT 的 1000 行） */
        private int importRowLimit = 1000;
        /** CSV 固定列数（对齐 Mock EXPORT_HEADER 的 16 列） */
        private int csvColumnCount = 16;
    }
}
