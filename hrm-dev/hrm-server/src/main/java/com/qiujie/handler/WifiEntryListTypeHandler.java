package com.qiujie.handler;

import com.fasterxml.jackson.databind.JavaType;
import com.qiujie.entity.WifiEntry;
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
 * {@code attendance_rule.wifi_list} JSON 列 ↔ {@code List<WifiEntry>} 类型处理器（理由同
 * {@link CheckPeriodListTypeHandler}：显式泛型，规避 List 擦除）。
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class WifiEntryListTypeHandler extends BaseTypeHandler<List<WifiEntry>> {

    private static final JavaType TYPE = JsonUtil.listType(WifiEntry.class);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<WifiEntry> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JsonUtil.write(parameter));
    }

    @Override
    public List<WifiEntry> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return JsonUtil.read(rs.getString(columnName), TYPE);
    }

    @Override
    public List<WifiEntry> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JsonUtil.read(rs.getString(columnIndex), TYPE);
    }

    @Override
    public List<WifiEntry> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return JsonUtil.read(cs.getString(columnIndex), TYPE);
    }
}
