package com.qiujie.handler;

import com.fasterxml.jackson.databind.JavaType;
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
 * {@code sync_config_option.legacy_codes} JSON 列 ↔ {@code List<String>} 类型处理器。
 * <p>
 * 旧码与新码的映射关系放数据里，代码侧不硬编码映射表：增删档位只改数据。
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class SyncConfigLegacyCodesTypeHandler extends BaseTypeHandler<List<String>> {

    private static final JavaType TYPE = JsonUtil.listType(String.class);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JsonUtil.write(parameter));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return JsonUtil.read(rs.getString(columnName), TYPE);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JsonUtil.read(rs.getString(columnIndex), TYPE);
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return JsonUtil.read(cs.getString(columnIndex), TYPE);
    }
}
