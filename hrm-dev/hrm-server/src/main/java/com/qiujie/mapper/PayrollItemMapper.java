package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.PayrollItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工资单明细 Mapper（db.md §8.6.4）。按 (payroll_id, sort_order) 取明细。
 */
public interface PayrollItemMapper extends BaseMapper<PayrollItem> {

    /**
     * 覆盖重建专用：按工资单 id <b>物理</b>删除明细（绕过 {@code @TableLogic}）。
     * <p>
     * 与 {@code PayrollMapper#deletePhysicallyByIds} 配对使用，且必须先删明细再删主单，
     * 避免 {@code payroll_item} 残留孤儿（该表无物理外键，靠删除顺序与范围保证一致）。
     * 单表 DELETE 文法，规避 MySQL 多表 DELETE 的 {@code No database selected}。
     */
    @Delete("<script>"
            + "DELETE FROM payroll_item WHERE payroll_id IN"
            + " <foreach collection='payrollIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    int deletePhysicallyByPayrollIds(@Param("payrollIds") List<Long> payrollIds);
}
