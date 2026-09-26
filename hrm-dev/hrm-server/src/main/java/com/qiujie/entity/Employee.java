package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工表（员工即账号，决策 D1），对应 employee 表，字段见 db.md 3.3。
 */
@Data
@TableName("employee")
public class Employee {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，活跃唯一，创建后不可修改 */
    private String username;

    /** BCrypt 散列（$2a$，cost=10） */
    private String password;

    private String realName;

    /** 手机号，活跃唯一，出参脱敏 */
    private String phone;

    /** 性别：0=未知，1=男，2=女 */
    private Integer gender;

    /** 所属部门（逻辑外键 department.id） */
    private Long deptId;

    /** 归属驿站（逻辑外键 station.id，二期数据同步基础） */
    private Long stationId;

    /**
     * 岗位（员工档案属性，权威事实；V19 方案乙新增，允许 NULL 表示「未登记」）。
     * <p>
     * 与 {@code hr_flow.position} 双写、以本字段为权威；<b>双写点唯一</b>：
     * 仅 {@code HrFlowServiceImpl#assignForFlow}（同方法同事务）可写，禁止他处单独写本列（防岗位注入）。
     */
    private String position;

    /** 角色：ADMIN / STATION_ADMIN(二期) / STAFF */
    private String role;

    /** 状态：0=禁用，1=启用 */
    private Integer status;

    /** 密码是否已由本人修改：0=未修改（首登强制改密），1=已修改 */
    private Integer pwdChanged;

    private LocalDate entryDate;

    private LocalDateTime lastLoginTime;

    private String remark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
