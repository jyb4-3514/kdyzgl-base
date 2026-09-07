package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.Station;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 驿站 Mapper。
 */
public interface StationMapper extends BaseMapper<Station> {

    /**
     * 各驿站归属员工数（含禁用员工、不含已删除，供驿站列表 employeeCount 使用）。
     * 注：@Select 原生 SQL 需显式带 is_deleted = 0；列别名小写下划线，双库一致。
     */
    @Select("SELECT station_id, COUNT(*) AS cnt FROM employee "
            + "WHERE is_deleted = 0 AND station_id IS NOT NULL GROUP BY station_id")
    List<Map<String, Object>> countEmployeesByStation();
}
