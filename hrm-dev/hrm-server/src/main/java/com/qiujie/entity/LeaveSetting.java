package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请假全局设置（db.md §8.7.1 / DDL: V9__leave.sql）——单行开关。
 * <p>
 * {@code leaveDeductEnabled}：true=请假按缺勤计（扣款），false=默认（请假不扣，Q6 未裁定前保持不扣）。
 * 单行、无删除语义，故不设 {@code is_deleted}；读取无行时按默认 false 处理（见 Service）。
 */
@Data
@TableName("leave_setting")
public class LeaveSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请假扣款开关：0=不扣（默认），1=请假按缺勤计 */
    private Integer leaveDeductEnabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
