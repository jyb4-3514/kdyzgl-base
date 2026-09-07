package com.qiujie.vo;

import lombok.Data;

import java.util.List;

/**
 * 导入结果（api.md 4.3.9）。
 */
@Data
public class ImportResultVO {

    /** 数据行总数（不含表头与全空行） */
    private Integer total;

    private Integer successCount;
    private Integer failCount;

    /** 行级错误明细（仅失败时返回） */
    private List<ImportErrorVO> errors;
}
