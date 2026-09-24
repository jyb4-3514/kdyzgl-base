package com.qiujie.vo.hr;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 人事档案详情出参（对齐 Mock {@code findProfile}：{@code {...toProfileVO(profile), salary}}）。
 * <p>
 * 单独定义详情 VO 而非给列表 VO 加字段：列表不返回 {@code salary} 键，避免与 Mock 出参形状产生差异。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HrProfileDetailVO extends HrProfileVO {

    /** 定薪摘要（无定薪档案时为 null） */
    private HrSalaryVO salary;
}
