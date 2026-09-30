package com.qiujie.service.finance.support;

import com.qiujie.entity.Payroll;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工资单状态机（纯逻辑，与 Mock {@code PAYROLL_ACTIONS} 逐条对齐；八态）。
 * <p>
 * 流转：
 * <pre>
 * DRAFT ──submit──▶ PENDING_APPROVAL ──approve──▶ APPROVED ──publish──▶ PUBLISHED ──confirm──▶ CONFIRMED ──pay──▶ PAID
 *                        └──reject──▶ REJECTED ──submit──▶ PENDING_APPROVAL
 * PUBLISHED ──objection──▶ OBJECTED ──publish──▶ PUBLISHED（再发布，直发）／──submit──▶ PENDING_APPROVAL（可选二次审批）
 * </pre>
 * 为什么把动作矩阵收在一处：状态守卫若每个动作各写一遍判断，八态 × 多动作会形成数十处口径，早晚漂移；
 * 集中后「当前状态允许哪些动作」只有一份真源，出参与写操作共用。
 * <p>
 * 「可编辑」拆分（方案 §2.3）：{@link #isItemEditable}（改/加明细金额）与 {@link #isOverwritable}
 * （可被 generate 覆盖重建）是两个独立真源——审核中 / 异议退回可改明细，但绝不可被覆盖重建。
 */
public final class PayrollStateMachine {

    /** 动作名（与 Mock 出参 {@code actions} 的小写字符串一致，前端据此渲染按钮） */
    public static final String ACTION_SUBMIT = "submit";
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_PUBLISH = "publish";
    public static final String ACTION_CONFIRM = "confirm";
    public static final String ACTION_OBJECTION = "objection";
    /** 管理员确认发放（I-8；CONFIRMED → PAID） */
    public static final String ACTION_PAY = "pay";
    /**
     * 明细级动作：新增一笔手工加/扣款。
     * <p>为什么不出现在 {@link #actionsOf}：出参 {@code actions[]} 是<b>动作级子集</b>（api.md §4.12.4），
     * 明细级动作（item-add / item-update）由 {@link #isItemEditable} 单独判定，避免前端把两者混为一谈。
     */
    public static final String ACTION_ITEM_ADD = "item-add";
    /** 明细级动作：改已有 MANUAL 项金额 */
    public static final String ACTION_ITEM_UPDATE = "item-update";

    /**
     * 状态 → 允许动作（顺序即出参顺序，与 Mock 逐条一致）。
     * <p>仅收录<b>动作级</b>动作；{@code PAID} 显式空集（终态冻结，不依赖「集合不含即拒绝」的隐式行为）。
     */
    private static final Map<String, List<String>> ACTIONS = buildActions();

    private PayrollStateMachine() {
    }

    private static Map<String, List<String>> buildActions() {
        Map<String, List<String>> actions = new LinkedHashMap<>();
        actions.put(PayrollStatus.DRAFT.name(), List.of(ACTION_SUBMIT));
        actions.put(PayrollStatus.REJECTED.name(), List.of(ACTION_SUBMIT));
        actions.put(PayrollStatus.PENDING_APPROVAL.name(), List.of(ACTION_APPROVE, ACTION_REJECT));
        actions.put(PayrollStatus.APPROVED.name(), List.of(ACTION_PUBLISH));
        actions.put(PayrollStatus.PUBLISHED.name(), List.of(ACTION_CONFIRM, ACTION_OBJECTION));
        actions.put(PayrollStatus.CONFIRMED.name(), List.of(ACTION_PAY));
        // 异议退回：再发布（直发，默认路径）或 submit（供需二次审批的组织口径）
        actions.put(PayrollStatus.OBJECTED.name(), List.of(ACTION_PUBLISH, ACTION_SUBMIT));
        // 终态冻结：显式空集
        actions.put(PayrollStatus.PAID.name(), List.of());
        return actions;
    }

    /** 当前状态允许的动作（未知状态 → 空列表，等价于「任何动作都不允许」） */
    public static List<String> actionsOf(String status) {
        return ACTIONS.getOrDefault(status, List.of());
    }

    /** 当前状态是否允许该动作 */
    public static boolean allows(String status, String action) {
        return actionsOf(status).contains(action);
    }

    /**
     * 是否「可被 generate 覆盖重建」：仅 DRAFT / REJECTED。
     * <p>语义与旧 {@code isEditable} 一致（行为零突变）；消费方为 {@code PayrollGenerateGuard} / {@code deleteExisting}。
     */
    public static boolean isOverwritable(String status) {
        return PayrollStatus.DRAFT.name().equals(status) || PayrollStatus.REJECTED.name().equals(status);
    }

    /**
     * 旧判据别名，保留以兼容既有调用点：与 {@link #isOverwritable} 同义。
     * 注：账期锁（{@code PayrollLockPolicy}）绑定的正是本语义（非 DRAFT/REJECTED 即锁），改名不得改口径。
     */
    public static boolean isEditable(String status) {
        return isOverwritable(status);
    }

    /**
     * 是否「明细可编辑」：能否改/加 MANUAL 明细金额。
     * <p>DRAFT / REJECTED（草稿驳回）+ PENDING_APPROVAL（Q6 审核时可改）+ OBJECTED（Q9 异议退回可改）。
     * 消费方：{@code updateItems}（C-3）与 {@code items/add}（I-6）。<b>不等于</b>可被覆盖重建。
     */
    public static boolean isItemEditable(String status) {
        return PayrollStatus.DRAFT.name().equals(status)
                || PayrollStatus.REJECTED.name().equals(status)
                || PayrollStatus.PENDING_APPROVAL.name().equals(status)
                || PayrollStatus.OBJECTED.name().equals(status);
    }

    /**
     * 统一终态写守卫（方案 §2.9）：{@code PAID} 归档冻结，任何写入口一律 {@code 9413}。
     * <p>为什么收口到一处：守卫若分散在每个写入口内联，扩展期（新增 {@code pay}、扩大 {@code isItemEditable}）
     * 任一入口漏加即绕过；本方法为全部写入口（改明细 / 加扣款 / submit / approve / reject / publish 含再发布 /
     * objection / confirm / pay / generate 覆盖重建）进入业务分支前的<b>单一</b>收口。
     *
     * @param payroll 目标工资单（null 交由调用方各自的「不存在」判定，此处不重复报 9402）
     * @param action  即将执行的动作名；当前 PAID 一票否决、与动作无关，参数为契约签名与排障留痕预留
     */
    public static void assertMutable(Payroll payroll, String action) {
        if (payroll != null && PayrollStatus.PAID.name().equals(payroll.getStatus())) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_ARCHIVED);
        }
    }
}
