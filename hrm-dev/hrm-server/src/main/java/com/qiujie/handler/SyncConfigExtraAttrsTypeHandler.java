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
import java.util.Map;

/**
 * {@code sync_config_option.extra_attrs} JSON 列 ↔ {@code Map<String,Object>} 类型处理器。
 * <p>
 * 附加属性结构由所属选项集约定（如 {@code {intervalMinutes}} / {@code {startTime,endTime}}），
 * 用 Map 承接；解析与校验集中在 {@code SyncConfigValidator#normalizeExtraAttrs}。
 */
@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class SyncConfigExtraAttrsTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final JavaType TYPE = JsonUtil.mapType();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JsonUtil.write(parameter));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return JsonUtil.read(rs.getString(columnName), TYPE);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JsonUtil.read(rs.getString(columnIndex), TYPE);
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return JsonUtil.read(cs.getString(columnIndex), TYPE);
    }
}
