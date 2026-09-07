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
 * 驿站表，对应 station 表，见 db.md 3.2。code 为二期爬虫对接键，活跃唯一。
 */
@Data
@TableName("station")
public class Station {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站编码，活跃唯一，格式 ^[A-Za-z0-9_-]{2,50}$ */
    private String code;

    private String stationName;

    private String contactPerson;

    /** 联系电话（出参脱敏，规则同手机号） */
    private String contactPhone;

    private String address;

    /** 状态：0=停用，1=启用 */
    private Integer status;

    private String remark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
