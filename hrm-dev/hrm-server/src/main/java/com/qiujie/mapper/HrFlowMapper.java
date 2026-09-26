package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.HrFlow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 入职/离职流程 Mapper（db.md §8.5）。按 (flow_type, status) 列表过滤、flow_no 查重。
 */
public interface HrFlowMapper extends BaseMapper<HrFlow> {

    /**
     * 按主键加行锁读取（{@code SELECT ... FOR UPDATE}），供审批/驳回并发守卫使用（§11.7）。
     * <p>
     * <b>加锁顺序固定</b>：调用方须在事务内<b>首条</b>执行本查询锁 {@code hr_flow} 单行，再读取/加锁
     * {@code employee_registration}（防死锁）；逻辑删除行不可锁（{@code is_deleted = 0}）。
     * SQL 参数化（{@code #{id}}），禁拼接。
     */
    @Select("SELECT * FROM hr_flow WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    HrFlow selectByIdForUpdate(@Param("id") Long id);
}
