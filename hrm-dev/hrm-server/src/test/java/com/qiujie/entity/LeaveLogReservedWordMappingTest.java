package com.qiujie.entity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LeaveLog 保留字列名转义单测（回归 BUG：{@code leave_log.before} 未转义 → MySQL 1064 语法错误）。
 * <p>
 * 断言依据：MyBatis-Plus {@link TableFieldInfo#getColumn()} 返回的字符串即 MP 注入<b>所有</b>生成 SQL
 * 的列名（{@code selectList} 的 SELECT 列清单、{@code LambdaQueryWrapper} 的 WHERE / ORDER BY、
 * {@code insert} 的列清单、{@code updateById} 的 SET 子句），故断言其为带反引号的 {@code `before`} /
 * {@code `after`} 即等价于断言生成 SQL 已转义。全程仅需反射建表元数据，无需数据库与 Spring 上下文。
 * <p>边界：本测试验证「列名元数据」层，不构造真实 SQL 字符串、不连库；SQL 实跑收敛到服务器阶段。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class LeaveLogReservedWordMappingTest {

    private static TableInfo tableInfo;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测无 Spring/MyBatis-Plus 上下文：主动注册实体元数据，等价于应用启动时 MP 的扫描
        tableInfo = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), LeaveLog.class);
    }

    private Map<String, String> columnByProperty() {
        // before/after 之外字段无 @TableField(value)，column 由驼峰转下划线得到，用于验证未被误伤
        return tableInfo.getFieldList().stream()
                .collect(Collectors.toMap(TableFieldInfo::getProperty, TableFieldInfo::getColumn));
    }

    @Test
    @DisplayName("回归 BUG：before/after 列名必须带反引号（BEFORE 为 MySQL 8.0 保留字，未转义报 1064）")
    void beforeAfterColumnsQuoted() {
        Map<String, String> columns = columnByProperty();
        assertEquals("`before`", columns.get("before"));
        assertEquals("`after`", columns.get("after"));
    }

    @Test
    @DisplayName("其余列名保持原样（转义仅限 before/after，避免误伤其它列）")
    void otherColumnsUnchanged() {
        Map<String, String> columns = columnByProperty();
        assertEquals("leave_id", columns.get("leaveId"));
        assertEquals("action", columns.get("action"));
        assertEquals("operator_name", columns.get("operatorName"));
        assertEquals("from_status", columns.get("fromStatus"));
        assertEquals("to_status", columns.get("toStatus"));
        assertEquals("remark", columns.get("remark"));
    }

    @Test
    @DisplayName("表名与主键列名为 leave_log / id（转义不改表结构映射）")
    void tableNameAndKeyColumn() {
        assertEquals("leave_log", tableInfo.getTableName());
        assertEquals("id", tableInfo.getKeyColumn());
    }
}
