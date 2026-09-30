package com.qiujie.service.hr.support;

import java.util.List;

/**
 * 岗位取值单一真源（用户裁定：{@code 店员} / {@code 站长} / {@code 管理员}）。
 * <p>
 * 为什么用常量收敛：岗位当前既无字典也无 DB 约束，取值只在应用层把关；
 * 若各处（审批入参 / 分配步骤 / 发起流程）各写各的中文串，取值一旦漂移就会
 * 出现「写进库的是合法值、出参却是另一个词」这类不可解释的数据。集中一处后，
 * 新增 / 调整取值只改本类，校验与错误文案自动一致。
 * <p>
 * 注意：本类只做「取值白名单」，不承载角色语义（岗位 {@code 管理员} ≠ 角色 {@code ADMIN}）。
 * <p>
 * TODO(扩展): 二期若引入岗位字典表 / DB 约束，将本类降级为字典缓存读取。
 */
public final class PositionConstants {

    private PositionConstants() {
    }

    /** 合法岗位（顺序即展示顺序，勿改顺序以免前端下拉项跳动） */
    public static final List<String> POSITIONS = List.of("店员", "站长", "管理员");

    /** 取值是否合法：null / 空白 / 非白名单值一律视为非法 */
    public static boolean isValid(String position) {
        return position != null && POSITIONS.contains(position.trim());
    }

    /** 统一错误文案，保证各入口提示一致（沿用 400，不新增码值） */
    public static String invalidMessage() {
        return "岗位仅支持 " + String.join(" / ", POSITIONS);
    }
}
