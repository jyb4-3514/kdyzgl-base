package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门表（树形结构，parent_id 自关联，0=根节点），对应 department 表，见 db.md 3.1。
 */
@Data
@TableName("department")
public class Department {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父部门 ID，0=根节点 */
    private Long parentId;

    private String deptName;

    /** 同级排序（升序） */
    private Integer sortOrder;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
