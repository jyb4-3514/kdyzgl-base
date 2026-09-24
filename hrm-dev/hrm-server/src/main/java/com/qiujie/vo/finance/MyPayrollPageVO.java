package com.qiujie.vo.finance;

import lombok.Data;

import java.util.List;

/**
 * 我的工资单分页出参（对齐 Mock {@code myPayrolls}）：带 {@code employeeId} 明示过滤身份。
 */
@Data
public class MyPayrollPageVO {

    private long total;

    private long pageNum;

    private long pageSize;

    private List<PayrollVO> list;

    /** 当前登录员工 id（服务端按登录身份过滤，不接受前端传参） */
    private Long employeeId;
}
