package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.EmployeeRegistration;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 员工自助注册申请单 Mapper（db.md / registration-design §2.2）。
 * <p>
 * 查询条件一律走 {@code LambdaQueryWrapper}（SQL 参数化，禁拼接）；行锁按 {@code flow_id}/{@code id}
 * 由 Service 固定加锁顺序调用（§11.7）。
 */
public interface EmployeeRegistrationMapper extends BaseMapper<EmployeeRegistration> {

    /**
     * 按流程 id 加行锁读取（{@code SELECT ... FOR UPDATE}）。
     * <p>
     * <b>加锁顺序</b>：须在 {@code hr_flow} 行锁之后调用（§11.7 固定顺序 hr_flow → registration），避免死锁；
     * 逻辑删除行不参与加锁。SQL 参数化（{@code #{flowId}}）。
     */
    @Select("SELECT * FROM employee_registration WHERE flow_id = #{flowId} AND is_deleted = 0 FOR UPDATE")
    EmployeeRegistration selectByFlowIdForUpdate(@Param("flowId") Long flowId);
}
