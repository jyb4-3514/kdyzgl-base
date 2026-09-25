package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.Payroll;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工资单 Mapper（db.md §8.6.3）。
 * <p>
 * 列表按 (month, status) / (month, station_id)、详情与幂等按 (employee_id, month, bill_type) 走索引；
 * 计数走 {@code selectMaps} + {@code group by status}（显式列，禁止 SELECT *）。
 */
public interface PayrollMapper extends BaseMapper<Payroll> {

    /**
     * 覆盖重建专用：取同员工同月同类型「可覆盖」旧单 id（含已被逻辑删除的残留行）。
     * <p>
     * 为什么绕过 {@code @TableLogic}：{@code BaseMapper#delete} 受逻辑删除影响只把 {@code is_deleted} 置 1，
     * 物理行仍在；重复生成则不断堆积（本缺陷实测：2026-10 连跑两次 → 物理 126 行 = 63 × 2）。
     * 覆盖重建必须真正清掉旧草稿，故此处用自定义 SQL 读取（自定义语句不受 MyBatis-Plus 逻辑删除改写）。
     * 仅取「可覆盖」状态（DRAFT/REJECTED），已提交/已发布单据一律不取（9405 保护语义不变）。
     */
    @Select("<script>"
            + "SELECT id FROM payroll"
            + " WHERE employee_id = #{employeeId} AND month = #{month} AND bill_type = #{billType}"
            + " AND status IN <foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach>"
            + "</script>")
    List<Long> selectRebuildTargetIds(@Param("employeeId") Long employeeId,
                                      @Param("month") String month,
                                      @Param("billType") String billType,
                                      @Param("statuses") List<String> statuses);

    /**
     * 覆盖重建专用：按 id 物理删除工资单（绕过逻辑删除）。
     * <p>
     * 单表 DELETE 文法：MySQL 多表 DELETE（{@code DELETE p FROM payroll p JOIN ...}）要求显式选库或表名带库名，
     * 否则报 {@code No database selected}；此处不 JOIN，规避该限制。明细由调用方先删，避免孤儿。
     */
    @Delete("<script>"
            + "DELETE FROM payroll WHERE id IN"
            + " <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int deletePhysicallyByIds(@Param("ids") List<Long> ids);
}
