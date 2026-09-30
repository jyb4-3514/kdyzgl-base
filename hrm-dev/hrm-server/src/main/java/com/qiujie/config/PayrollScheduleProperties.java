package com.qiujie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 薪资自动算薪调度参数（{@code hrm.payroll.schedule.*}）。
 * <p>
 * 为什么单独命名空间、不并入 {@link AlgoProperties}：{@code hrm.algo.*} 承载「规则/阈值/权重」等算法超参，
 * 本组是<b>调度基础设施</b>参数（轮询间隔、回收阈值、节流、告警节奏），生命周期与运维侧重不同；
 * 按算法四件套 §2「单一真源」约定使用 {@code hrm.payroll.schedule.*}，与架构 ADR-01 一致。
 * <p>
 * 键名 / 默认值 / 取值范围逐键对齐 {@code algorithm-payroll-scheduling.md} v1.3 §2（11 项）。
 * 代码内严禁内联阈值（规则 §11.4、反模式 A03）。
 * <p>
 * {@code enabled} 默认 {@code false}（安全：上线不自动跑数）；{@code staleReclaimEnabled} 默认 {@code true}
 * （单实例 {@code fixedDelay} 不重叠 → 超时即真僵死，闭环保守）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hrm.payroll.schedule")
public class PayrollScheduleProperties {

    /** 自动算薪总开关（默认关，部署后由管理员显式启用；ADR-01） */
    private boolean enabled = false;

    /** 判定时区（同时派生 attempt_date）；默认与数据源 serverTimezone 一致 */
    private String zone = "Asia/Shanghai";

    /** 轮询间隔（fixedDelay，毫秒）；= 到点 / 补跑钟点 / 回收扫描的检测精度（非重试间隔） */
    private long tickIntervalMs = 600000L;

    /** 补跑日每日尝试钟点（HH:mm）；空 = 继承该站 payroll_time */
    private String catchUpTimeOfDay = "";

    /** 单 tick 串行处理驿站上限（0 = 不限，剩余顺延下轮） */
    private int maxStationsPerTick = 0;

    /** 驿站间串行间隔（毫秒，错峰节流） */
    private long interStationDelayMs = 200L;

    /** 僵死 RUNNING 回收阈值（分钟）；须 ≥ 最坏单驿站耗时 */
    private int runningTimeoutMinutes = 30;

    /** 僵死回收总开关（单实例默认开） */
    private boolean staleReclaimEnabled = true;

    /** 连续失败天数告警阈值（只告警、不停止重试；B4a 消费） */
    private int alertAfterConsecutiveFailDays = 3;

    /** 告警重复间隔天数（0 = 仅首次达阈值时告警；B4a 消费） */
    private int alertRepeatIntervalDays = 0;

    /** 失败 / 连续失败 / 僵死回收告警总开关（B4a 投递时消费） */
    private boolean notifyOnFail = true;
}
