package com.qiujie.handler;

import com.fasterxml.jackson.core.type.TypeReference;
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
 * {@code payroll_rule_item.params} JSON 列 ↔ {@code Map<String,Object>} 类型处理器。
 * <p>
 * 为什么用 Map 而非强类型：params 结构随来源而变（FIXED/ATTENDANCE/KPI/MANUAL 各不同），
 * 强类型会造成「新增来源需改实体」；读侧由注册表按来源解释（算法 S2 表驱动）。
 * 理由同 {@link HrAllowanceListTypeHandler}：显式泛型，规避 Map 擦除。
 */
@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class PayrollItemParamsTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<Map<String, Object>>() {
    };

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
