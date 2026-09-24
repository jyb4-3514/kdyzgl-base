package com.qiujie.handler;

import com.fasterxml.jackson.databind.JavaType;
import com.qiujie.entity.CheckPeriod;
import com.qiujie.util.JsonUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * {@code attendance_rule.check_periods} JSON 列 ↔ {@code List<CheckPeriod>} 类型处理器。
 * <p>
 * 为什么自写而不直接用 MyBatis-Plus 的 {@code JacksonTypeHandler}：泛型 {@code List<T>} 在部分 MP 版本下
 * 因类型擦除会被反序列化成 {@code List<LinkedHashMap>}，赋值给强类型字段时抛 {@code IllegalArgumentException}。
 * 自写处理器显式声明 {@link JavaType}（{@link JsonUtil#listType}），行为确定、可离线静态审查；
 * 代价仅为约 30 行样板代码。
 * <p>
 * 必须配合实体上的 {@code @TableName(autoResultMap = true)}，否则 SELECT 不会走本处理器。
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class CheckPeriodListTypeHandler extends BaseTypeHandler<List<CheckPeriod>> {

    /** 显式泛型：避免 List 类型擦除导致的反序列化错型 */
    private static final JavaType TYPE = JsonUtil.listType(CheckPeriod.class);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<CheckPeriod> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JsonUtil.write(parameter));
    }

    @Override
    public List<CheckPeriod> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return JsonUtil.read(rs.getString(columnName), TYPE);
    }

    @Override
    public List<CheckPeriod> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JsonUtil.read(rs.getString(columnIndex), TYPE);
    }

    @Override
    public List<CheckPeriod> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return JsonUtil.read(cs.getString(columnIndex), TYPE);
    }
}
