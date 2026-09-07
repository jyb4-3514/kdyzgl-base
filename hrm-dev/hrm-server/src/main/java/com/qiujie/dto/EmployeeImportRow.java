package com.qiujie.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Excel 导入行数据（原始文本 + 校验通过后回填的归属字段）。
 */
@Data
public class EmployeeImportRow {

    /** Excel 真实行号（表头=1，首条数据=2），便于用户直接定位 */
    private int rowNumber;

    private String realName;
    private String username;
    private String phone;
    /** 性别原文：男/女/空 */
    private String genderText;
    private String deptName;
    private String stationName;
    /** 入职日期原文，格式 yyyy-MM-dd */
    private String entryDateText;
    private String remark;

    // ---- 以下字段由 ImportRowValidator 校验通过后回填 ----
    /** 0=未知，1=男，2=女 */
    private Integer gender;
    private Long deptId;
    private Long stationId;
    private LocalDate entryDate;
}
