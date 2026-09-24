package com.qiujie.service.hr.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人事域常量（步骤定义 / 状态 / 标签映射）。
 * <p>
 * <b>步骤顺序真源</b>：与 Mock {@code hrStore.ONBOARDING_STEPS / OFFBOARDING_STEPS} 逐条一致（顺序即流程顺序）。
 * 这里的顺序是「按序办理守卫」的唯一依据，任何调整都必须同步 Mock 契约与 V7 迁移脚本说明。
 * <p>
 * 标签映射对齐 Mock（EDUCATION_LABEL / CONTRACT_TYPE_LABEL / FLOW_STATUS_LABEL / STEP_STATUS_LABEL /
 * OFFBOARDING_TYPE_LABEL），出参的 {@code *Label} 字段由此派生。
 */
public final class HrConstants {

    private HrConstants() {
    }

    // ==================== 流程类型 ====================

    public static final String FLOW_TYPE_ONBOARDING = "ONBOARDING";
    public static final String FLOW_TYPE_OFFBOARDING = "OFFBOARDING";

    // ==================== 流程状态 ====================

    public static final String FLOW_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String FLOW_STATUS_COMPLETED = "COMPLETED";
    public static final String FLOW_STATUS_REJECTED = "REJECTED";

    // ==================== 步骤状态 ====================

    public static final String STEP_STATUS_PENDING = "PENDING";
    public static final String STEP_STATUS_DONE = "DONE";

    // ==================== 调薪留痕变更类型 ====================

    public static final String CHANGE_TYPE_ENTRY = "ENTRY";
    public static final String CHANGE_TYPE_ADJUST = "ADJUST";

    // ==================== 关键步骤键（Service 需按 key 分派副作用） ====================

    public static final String ONBOARD_CREATE_ACCOUNT = "CREATE_ACCOUNT";
    public static final String ONBOARD_ASSIGN_STATION = "ASSIGN_STATION";
    public static final String ONBOARD_SET_SALARY = "SET_SALARY";
    public static final String ONBOARD_DONE = "DONE";
    public static final String OFFBOARD_SETTLEMENT = "SETTLEMENT";
    public static final String OFFBOARD_LEAVE = "LEAVE";

    /** 入职可分配角色（Mock：role 仅支持 STATION_ADMIN / STAFF） */
    public static final List<String> ASSIGNABLE_ROLES = List.of("STATION_ADMIN", "STAFF");

    /** 步骤定义（key + name）；顺序即流程顺序 */
    public record StepDef(String key, String name) {
    }

    /** 入职步骤（顺序真源，对齐 Mock ONBOARDING_STEPS） */
    public static final List<StepDef> ONBOARDING_STEPS = List.of(
            new StepDef("SUBMIT_MATERIALS", "提交资料"),
            new StepDef("HR_REVIEW", "人事审核"),
            new StepDef("CREATE_ACCOUNT", "建档并生成员工与账号"),
            new StepDef("ASSIGN_STATION", "分配驿站/岗位"),
            new StepDef("SET_SALARY", "定薪"),
            new StepDef("DONE", "完成"));

    /** 离职步骤（顺序真源，对齐 Mock OFFBOARDING_STEPS） */
    public static final List<StepDef> OFFBOARDING_STEPS = List.of(
            new StepDef("MANAGER_APPROVE", "主管审批"),
            new StepDef("HR_APPROVE", "人事审批"),
            new StepDef("HANDOVER", "工作交接"),
            new StepDef("ASSET_RETURN", "资产归还"),
            new StepDef("SETTLEMENT", "薪资结算"),
            new StepDef("LEAVE", "离岗"));

    // ==================== 字典标签 ====================

    private static final Map<String, String> EDUCATION_LABEL = new LinkedHashMap<>();
    private static final Map<String, String> CONTRACT_TYPE_LABEL = new LinkedHashMap<>();
    private static final Map<String, String> OFFBOARDING_TYPE_LABEL = new LinkedHashMap<>();
    private static final Map<String, String> FLOW_STATUS_LABEL = new LinkedHashMap<>();
    private static final Map<String, String> STEP_STATUS_LABEL = new LinkedHashMap<>();

    static {
        EDUCATION_LABEL.put("MASTER", "硕士");
        EDUCATION_LABEL.put("BACHELOR", "本科");
        EDUCATION_LABEL.put("COLLEGE", "大专");
        EDUCATION_LABEL.put("HIGH_SCHOOL", "高中及以下");

        CONTRACT_TYPE_LABEL.put("FIXED_TERM", "固定期限");
        CONTRACT_TYPE_LABEL.put("NON_FIXED_TERM", "无固定期限");
        CONTRACT_TYPE_LABEL.put("INTERN", "实习协议");
        CONTRACT_TYPE_LABEL.put("DISPATCH", "劳务派遣");

        OFFBOARDING_TYPE_LABEL.put("RESIGN", "辞职");
        OFFBOARDING_TYPE_LABEL.put("DISMISS", "辞退");
        OFFBOARDING_TYPE_LABEL.put("RETIRE", "退休");

        FLOW_STATUS_LABEL.put(FLOW_STATUS_IN_PROGRESS, "进行中");
        FLOW_STATUS_LABEL.put(FLOW_STATUS_COMPLETED, "已完成");
        FLOW_STATUS_LABEL.put(FLOW_STATUS_REJECTED, "已驳回");

        STEP_STATUS_LABEL.put(STEP_STATUS_PENDING, "待办理");
        STEP_STATUS_LABEL.put(STEP_STATUS_DONE, "已完成");
    }

    public static String educationLabel(String key) {
        return EDUCATION_LABEL.get(key);
    }

    public static String contractTypeLabel(String key) {
        return CONTRACT_TYPE_LABEL.get(key);
    }

    public static String offboardingTypeLabel(String key) {
        return OFFBOARDING_TYPE_LABEL.get(key);
    }

    public static String flowStatusLabel(String key) {
        return FLOW_STATUS_LABEL.get(key);
    }

    public static String stepStatusLabel(String key) {
        return STEP_STATUS_LABEL.get(key);
    }

    public static String changeTypeLabel(String key) {
        if (CHANGE_TYPE_ENTRY.equals(key)) {
            return "入职定薪";
        }
        return CHANGE_TYPE_ADJUST.equals(key) ? "调薪" : null;
    }

    /** 合法键集合（用于入参白名单校验） */
    public static List<String> educationKeys() {
        return List.copyOf(EDUCATION_LABEL.keySet());
    }

    public static List<String> contractTypeKeys() {
        return List.copyOf(CONTRACT_TYPE_LABEL.keySet());
    }

    public static List<String> offboardingTypeKeys() {
        return List.copyOf(OFFBOARDING_TYPE_LABEL.keySet());
    }

    public static List<String> flowStatusKeys() {
        return List.copyOf(FLOW_STATUS_LABEL.keySet());
    }
}
