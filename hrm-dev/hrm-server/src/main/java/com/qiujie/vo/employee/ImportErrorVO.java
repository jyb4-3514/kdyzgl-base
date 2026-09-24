package com.qiujie.vo.employee;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 导入行级错误明细（api.md 4.3.9 / 第 5 章）。
 */
@Data
@AllArgsConstructor
public class ImportErrorVO {

    /** Excel 真实行号（表头=1，首条数据=2） */
    private Integer row;

    /** 字段中文名（如 登录账号 / 手机号） */
    private String field;

    /** 错误说明 */
    private String message;
}
