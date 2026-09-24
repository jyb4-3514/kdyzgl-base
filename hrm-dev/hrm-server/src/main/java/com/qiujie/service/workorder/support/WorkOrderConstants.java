package com.qiujie.service.workorder.support;

import java.util.List;
import java.util.Set;

/**
 * 工单域常量与字典（真源：Mock {@code constants/dict.js} 工单段 + {@code routes/workOrder.js}）。
 * <p>
 * 为什么集中：状态码 / 类型码 / 时间线动作名散落为字面量时拼写漂移会让状态机与出参组装静默失真，
 * 故全部收口本类；阈值类（SLA 小时 / 派单权重 / 关键词权重）另走 {@code hrm.algo.dispatch.*}
 * （AlgoProperties），不在此硬编码（规则 §11.4、反模式 A03）。
 */
public final class WorkOrderConstants {

    private WorkOrderConstants() {
    }

    // ==================== 状态（4 态，数值与 DDL/Mock 一致） ====================

    /** 待处理 */
    public static final int STATUS_PENDING = 0;
    /** 处理中 */
    public static final int STATUS_PROCESSING = 1;
    /** 已解决（终态） */
    public static final int STATUS_RESOLVED = 2;
    /** 已关闭（终态） */
    public static final int STATUS_CLOSED = 3;

    /** 终态集合：超 SLA 判定不计入（对齐 Mock {@code isOverdueUnhandled}） */
    public static final List<Integer> TERMINAL_STATUSES = List.of(STATUS_RESOLVED, STATUS_CLOSED);
    /** 未处理完集合：超 SLA 判定只在此集合内成立 */
    public static final List<Integer> OPEN_STATUSES = List.of(STATUS_PENDING, STATUS_PROCESSING);

    // ==================== 类型 / 优先级 ====================

    /** 类型：1=包裹异常 2=设备故障 3=客户投诉 4=其他（兜底类型 4 由 hrm.algo.dispatch.defaultType 配置） */
    public static final Set<Integer> TYPES = Set.of(1, 2, 3, 4);
    /** 优先级：0=低 1=中 2=高（兜底优先级 1 由 hrm.algo.dispatch.defaultPriority 配置） */
    public static final Set<Integer> PRIORITIES = Set.of(0, 1, 2);

    // ==================== 来源 ====================

    /** 手工新建 */
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 企业微信群消息自动派发 */
    public static final String SOURCE_AUTO_WECHAT = "AUTO_WECHAT";
    /** 企微自动派发的时间线操作人姓名快照（系统来源，无真实操作人） */
    public static final String AUTO_OPERATOR_NAME = "企业微信采集";
    /** 未命中规则时标题前缀（对齐 Mock） */
    public static final String GROUP_MSG_LABEL = "群消息";

    // ==================== 时间线动作（对齐 DDL work_order_timeline.action 注释） ====================

    public static final String ACTION_CREATE = "create";
    public static final String ACTION_ACCEPT = "accept";
    public static final String ACTION_RESOLVE = "resolve";
    public static final String ACTION_CLOSE = "close";
    public static final String ACTION_REOPEN = "reopen";
    public static final String ACTION_ASSIGN = "assign";
    public static final String ACTION_TRANSFER = "transfer";
    public static final String ACTION_AUTO_DISPATCH = "auto_dispatch";

    // ==================== 通知（api.md §7.3 / db.md notification.type） ====================

    /** 通知类型：工单指派（指派 / 转单） */
    public static final int NOTIFY_ASSIGN = 1;
    /** 通知类型：工单流转（解决） */
    public static final int NOTIFY_FLOW = 2;
    /** 通知跳转类型 */
    public static final String BIZ_TYPE = "work_order";

    // ==================== 文案长度（对齐 Mock validate.textLen 口径） ====================

    public static final int TITLE_MIN = 1;
    public static final int TITLE_MAX = 100;
    public static final int CONTENT_MAX = 500;
    public static final int KEYWORD_MIN = 1;
    public static final int KEYWORD_MAX = 20;
    public static final int REASON_MIN = 2;
    public static final int REASON_MAX = 100;
    /** 群消息标题截断阈值（Mock {@code titleFromContent}） */
    public static final int GROUP_TITLE_MAX = 40;
    /** 单号占位前缀（插入取得自增 id 后回填最终单号，见 Service 说明） */
    public static final String ORDER_NO_PLACEHOLDER_PREFIX = "TMP-WO-";
}
