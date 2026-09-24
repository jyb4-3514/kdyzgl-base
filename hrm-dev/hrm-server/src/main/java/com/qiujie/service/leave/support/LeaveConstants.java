package com.qiujie.service.leave.support;

import java.util.List;
import java.util.Map;

/**
 * 请假域常量与字典（真源：Mock {@code constants/dict.js} 第七节 + {@code leaveStore.js}）。
 * <p>
 * 为什么集中：状态名 / 假别 / 半天粒度散落为字符串字面量时拼写漂移会导致状态机静默失效（放行非法流转），
 * 故状态与动作、假别 countMode、文案标签全部收口本类；阈值类（占用状态集 / 单次上限）另走
 * {@code hrm.algo.leave.*}（AlgoProperties），不在此硬编码。
 */
public final class LeaveConstants {

    private LeaveConstants() {
    }

    // ==================== 状态（6 态） ====================

    public static final String STATUS_PENDING_STATION = "PENDING_STATION";
    public static final String STATUS_PENDING_BOSS = "PENDING_BOSS";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REVOKED = "REVOKED";

    /** 申请人可自行撤销的状态（对齐 Mock {@code CANCELABLE_STATUS}） */
    public static final List<String> CANCELABLE_STATUS =
            List.of(STATUS_PENDING_STATION, STATUS_PENDING_BOSS);

    /** 列表可精确筛选的状态（'PENDING' 为聚合虚拟值，单独处理） */
    public static final List<String> QUERY_STATUSES = List.of(
            STATUS_PENDING_STATION, STATUS_PENDING_BOSS, STATUS_APPROVED,
            STATUS_REJECTED, STATUS_CANCELLED, STATUS_REVOKED);

    /** 聚合虚拟值：服务端展开为 PENDING_STATION + PENDING_BOSS */
    public static final String AGGREGATE_PENDING = "PENDING";

    /** 驳回阶段（仅 REJECTED 有值） */
    public static final String STAGE_STATION = "STATION";
    public static final String STAGE_BOSS = "BOSS";

    // ==================== 半天粒度 / 假别 ====================

    public static final String PERIOD_AM = "AM";
    public static final String PERIOD_PM = "PM";

    /** 计薪口径：按自然日连续计 */
    public static final String COUNT_MODE_NATURAL = "NATURAL";
    /** 计薪口径：逐日查排班矩阵（自动排除轮休日） */
    public static final String COUNT_MODE_SCHEDULED = "SCHEDULED";

    /**
     * 合法假别集合（对齐 Mock {@code LEAVE_TYPE} 键）。
     * <p>
     * 假别 → apply 计薪口径的映射<b>不在本类</b>：它是可配超参，唯一运行时真源为
     * {@code hrm.algo.leave.countModeMap}（默认值逐位等于本字典），避免双来源漂移。
     */
    public static final List<String> LEAVE_TYPES = List.of(
            "ANNUAL", "PERSONAL", "SICK", "COMPENSATORY", "MARRIAGE",
            "MATERNITY", "PATERNITY", "BEREAVEMENT", "OTHER");

    /** 假别 → 文案（重叠/通知消息用） */
    public static final Map<String, String> LEAVE_TYPE_LABEL = Map.of(
            "ANNUAL", "年假",
            "PERSONAL", "事假",
            "SICK", "病假",
            "COMPENSATORY", "调休",
            "MARRIAGE", "婚假",
            "MATERNITY", "产假",
            "PATERNITY", "陪产假",
            "BEREAVEMENT", "丧假",
            "OTHER", "其他");

    /** 半天粒度 → 文案 */
    public static final Map<String, String> HALF_DAY_LABEL = Map.of(PERIOD_AM, "上午", PERIOD_PM, "下午");

    /** 状态 → 文案（重叠提示用） */
    public static final Map<String, String> STATUS_LABEL = Map.of(
            STATUS_PENDING_STATION, "待站长初审",
            STATUS_PENDING_BOSS, "待老板终审",
            STATUS_APPROVED, "已通过",
            STATUS_REJECTED, "已驳回",
            STATUS_CANCELLED, "已撤销",
            STATUS_REVOKED, "已撤回");

    // ==================== 留痕动作（对齐 dict.LEAVE_LOG_ACTION） ====================

    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_RESUBMIT = "RESUBMIT";
    public static final String ACTION_CANCEL = "CANCEL";
    public static final String ACTION_STATION_APPROVE = "STATION_APPROVE";
    public static final String ACTION_STATION_REJECT = "STATION_REJECT";
    public static final String ACTION_FINAL_APPROVE = "FINAL_APPROVE";
    public static final String ACTION_FINAL_REJECT = "FINAL_REJECT";
    public static final String ACTION_REVOKE = "REVOKE";
    public static final String ACTION_NOTIFY_SKIP = "NOTIFY_SKIP";

    // ==================== 通知（api.md §7.3） ====================

    /** 通知类型：请假申请（发给审批人） */
    public static final int NOTIFY_APPLY = 5;
    /** 通知类型：请假结果（发给申请人） */
    public static final int NOTIFY_RESULT = 6;
    /** 请假通知跳转类型 */
    public static final String BIZ_TYPE = "leave";

    /** 假别文案（不存在时回退原值） */
    public static String typeLabel(String leaveType) {
        String label = LEAVE_TYPE_LABEL.get(leaveType);
        return label == null ? leaveType : label;
    }

    /** 状态文案（不存在时回退原值） */
    public static String statusLabel(String status) {
        String label = STATUS_LABEL.get(status);
        return label == null ? status : label;
    }
}
