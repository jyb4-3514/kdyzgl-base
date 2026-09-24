package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程步骤（子表，随主表，无独立删除，db.md §8.5，DDL: V7__hr.sql）。
 * <p>
 * 无 {@code is_deleted}：步骤随流程主表逻辑删除，子表不做独立软删（避免「流程在、步骤缺」的破窗）。
 */
@Data
@TableName("hr_flow_step")
public class HrFlowStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程（逻辑外键 hr_flow.id） */
    private Long flowId;

    /** 步骤键 */
    private String stepKey;

    /** 步骤名 */
    private String stepName;

    /** 步骤顺序（升序） */
    private Integer stepOrder;

    /** 步骤状态：PENDING=待办理，DONE=已完成 */
    private String status;

    /** 办理人（逻辑外键 employee.id） */
    private Long operatorId;

    /** 办理人姓名快照 */
    private String operatorName;

    /** 办理时间 */
    private LocalDateTime operateTime;

    /** 办理备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
