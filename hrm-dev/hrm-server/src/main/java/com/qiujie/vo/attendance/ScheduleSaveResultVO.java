package com.qiujie.vo.attendance;

import lombok.Data;

/**
 * 手动批量排班结果（ARCH-C-2b：{@code {saved, removed}} 语义重定义，评审 M-8）。
 * <p>
 * 保存为「当天班次集合整体覆盖」（A-④）后，计数单位由「条数」升级为「<b>班次行数</b>」：
 * {@code saved} = 覆盖后该批目标集合的班次行数（含未变化的 {@code T∩C}）；
 * {@code removed} = 本次被差量移除（逻辑删）的班次行数（{@code C\T}）。
 * <p>
 * 注意口径：{@code saved + removed} <b>不等于</b>差量总数（{@code T∩C} no-op 计入 {@code saved} 但不计入 {@code removed}）；
 * 旧客户端仅传单值 {@code shiftId}（单元素集合）时，两值均 ∈ {0,1}，与旧「新增 1 / 清空 1」可观察等价。
 */
@Data
public class ScheduleSaveResultVO {

    /** 覆盖后该批目标集合的班次行数（|T| 口径，含未变化的交集） */
    private int saved;

    /** 本次被差量移除的班次行数（|C\T| 口径） */
    private int removed;
}
