package com.qiujie.vo.hr;

import lombok.Data;

import java.util.List;

/**
 * 定薪详情出参（对齐 Mock {@code findSalary}：{@code {current, histories}}）。
 */
@Data
public class HrSalaryDetailVO {

    /** 当前定薪 */
    private HrSalaryVO current;

    /** 调薪留痕（按生效日期降序） */
    private List<HrSalaryLogVO> histories;
}
