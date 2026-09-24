package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.ClientLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 运行日志 Mapper（S8 指纹聚合的持久化出口）。
 * <p>
 * 为什么用注解 SQL 而非条件更新：{@code count} 是 MySQL 非保留关键字（列名需引用），且「计数自增」无法用
 * MyBatis-Plus 条件更新表达（{@code set} 只接受字面值）。此处表名/列名均为固定字面量，值一律走 {@code #{}} 参数化，
 * 无任何字符串拼接（规则 §4 SQL 参数化）。
 * TODO(扩展): 切换 PostgreSQL 时 {@code count} 需改用双引号引用（方言差异），并复核 LIMIT 语法。
 */
public interface ClientLogMapper extends BaseMapper<ClientLog> {

    /** 同指纹命中：原子累加计数并刷新最近出现时间（避免读改写丢更新） */
    @Update("UPDATE client_log SET `count` = `count` + 1, last_time = #{lastTime} WHERE id = #{id}")
    int incrementCount(@Param("id") Long id, @Param("lastTime") LocalDateTime lastTime);

    /** 清空运行日志（物理删除，仅 ADMIN；返回删除条数） */
    @Delete("DELETE FROM client_log")
    int deleteAll();
}
