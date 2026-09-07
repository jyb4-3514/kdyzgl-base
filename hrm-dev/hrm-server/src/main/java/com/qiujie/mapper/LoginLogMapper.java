package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.LoginLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 登录日志 Mapper（只插不改不删）。
 */
public interface LoginLogMapper extends BaseMapper<LoginLog> {

    /**
     * 今日登录去重员工数（看板口径，api.md 4.2：DISTINCT employee_id，走 idx_login_log_login_time 索引）。
     * 入参为今日 00:00:00，等价于 MySQL CURDATE() / PostgreSQL CURRENT_DATE，双库通用。
     */
    @Select("SELECT COUNT(DISTINCT employee_id) FROM login_log "
            + "WHERE login_result = 1 AND login_time >= #{startTime}")
    long countTodayLoginDistinct(@Param("startTime") LocalDateTime startTime);
}
