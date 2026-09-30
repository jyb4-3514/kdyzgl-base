package com.qiujie.service.station.impl;

import com.qiujie.dto.station.StationRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.audit.OperationAuditWriter;
import com.qiujie.service.station.StationService;
import com.qiujie.util.DesensitizeUtil;
import com.qiujie.vo.common.IdVO;
import com.qiujie.vo.station.StationVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 驿站服务实现（api.md 4.5）。
 * <p>
 * 增改启停删均写审计留痕（ARCH-S-2 / §3.3 #8~#11）：写入方式与事务口径见
 * {@link OperationAuditWriter} 类头（T9 定稿：Service 内显式 + 与业务写同事务）。
 */
@Service
@RequiredArgsConstructor
public class StationServiceImpl implements StationService {

    private final StationMapper stationMapper;
    private final EmployeeMapper employeeMapper;
    /** 操作审计留痕出口（ARCH-S-2 / §3.3） */
    private final OperationAuditWriter operationAuditWriter;

    @Override
    public List<StationVO> list(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态取值仅支持 0=停用/1=启用");
        }
        // 驿站基数 < 100，全量返回（api.md 4.5.1：供管理表格与员工表单下拉共用）
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<Station>()
                .orderByAsc(Station::getId);
        if (status != null) {
            wrapper.eq(Station::getStatus, status);
        }
        List<Station> stations = stationMapper.selectList(wrapper);

        // 各驿站归属员工数（含禁用、不含已删除）
        Map<Long, Long> employeeCountMap = new HashMap<>();
        for (Map<String, Object> row : stationMapper.countEmployeesByStation()) {
            Number stationId = (Number) row.get("station_id");
            Number count = (Number) row.get("cnt");
            if (stationId != null && count != null) {
                employeeCountMap.put(stationId.longValue(), count.longValue());
            }
        }

        List<StationVO> result = new ArrayList<>(stations.size());
        for (Station station : stations) {
            StationVO vo = new StationVO();
            vo.setId(station.getId());
            vo.setCode(station.getCode());
            vo.setStationName(station.getStationName());
            vo.setContactPerson(station.getContactPerson());
            vo.setContactPhone(DesensitizeUtil.maskPhone(station.getContactPhone()));
            vo.setAddress(station.getAddress());
            vo.setStatus(station.getStatus());
            vo.setEmployeeCount(employeeCountMap.getOrDefault(station.getId(), 0L));
            vo.setRemark(station.getRemark());
            vo.setCreateTime(station.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IdVO create(StationRequest request) {
        checkCodeUnique(request.getCode(), null);
        Station station = new Station();
        station.setCode(request.getCode());
        station.setStationName(request.getStationName());
        station.setContactPerson(request.getContactPerson());
        station.setContactPhone(request.getContactPhone());
        station.setAddress(request.getAddress());
        station.setRemark(request.getRemark());
        station.setStatus(1); // 新增默认启用
        stationMapper.insert(station);
        // 审计留痕（§3.3 #8）
        operationAuditWriter.record(OperationAuditWriter.TARGET_STATION, station.getId(), station.getStationName(),
                OperationAuditWriter.ACTION_CREATE, null, stationSnapshot(station));
        return new IdVO(station.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StationRequest request) {
        Station exist = stationMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "驿站不存在");
        }
        // 一期允许修改 code；变更时做唯一校验（二期爬虫对接后编码冻结，见 db.md 3.2）
        if (!exist.getCode().equals(request.getCode())) {
            checkCodeUnique(request.getCode(), id);
        }
        Map<String, Object> before = stationSnapshot(exist);
        // 可选字段以请求体为准（null 即清空），UpdateWrapper 显式 set 以支持置空
        LambdaUpdateWrapper<Station> wrapper = new LambdaUpdateWrapper<Station>()
                .eq(Station::getId, id)
                .set(Station::getCode, request.getCode())
                .set(Station::getStationName, request.getStationName())
                .set(Station::getContactPerson, request.getContactPerson())
                .set(Station::getContactPhone, request.getContactPhone())
                .set(Station::getAddress, request.getAddress())
                .set(Station::getRemark, request.getRemark())
                // wrapper 更新不走实体自动填充，手动维护 update_time（决策 D8）
                .set(Station::getUpdateTime, LocalDateTime.now());
        stationMapper.update(null, wrapper);
        // 审计留痕（§3.3 #9）
        operationAuditWriter.record(OperationAuditWriter.TARGET_STATION, id, request.getStationName(),
                OperationAuditWriter.ACTION_UPDATE, before,
                stationSnapshot(request.getCode(), request.getStationName(), request.getContactPerson(),
                        request.getContactPhone(), request.getAddress(), exist.getStatus(), request.getRemark()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态取值仅支持 0=停用/1=启用");
        }
        Station exist = stationMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "驿站不存在");
        }
        // 停用影响：存量员工归属保留、账号可登录；新增/编辑员工不可再归属（员工侧校验 4004）
        Station update = new Station();
        update.setId(id);
        update.setStatus(status);
        stationMapper.updateById(update);
        // 审计留痕（§3.3 #10）：动作名与 V23 DDL 统一定名 CHANGE_STATUS
        operationAuditWriter.record(OperationAuditWriter.TARGET_STATION, id, exist.getStationName(),
                OperationAuditWriter.ACTION_CHANGE_STATUS,
                Map.of("status", exist.getStatus() == null ? 0 : exist.getStatus()),
                Map.of("status", status));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Station exist = stationMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "驿站不存在");
        }
        // 前置校验：有归属员工拒删（4003）
        Long employeeCount = employeeMapper.selectCount(
                new LambdaQueryWrapper<Employee>().eq(Employee::getStationId, id));
        if (employeeCount != null && employeeCount > 0) {
            throw new BusinessException(ErrorCode.STATION_HAS_EMPLOYEES);
        }
        stationMapper.deleteById(id); // 逻辑删除
        // 审计留痕（§3.3 #11）
        operationAuditWriter.record(OperationAuditWriter.TARGET_STATION, id, exist.getStationName(),
                OperationAuditWriter.ACTION_DELETE, stationSnapshot(exist), null);
    }

    /** 驿站快照（审计白名单键：编码/名称/联系人/电话/地址/状态/备注） */
    private Map<String, Object> stationSnapshot(Station station) {
        return stationSnapshot(station.getCode(), station.getStationName(), station.getContactPerson(),
                station.getContactPhone(), station.getAddress(), station.getStatus(), station.getRemark());
    }

    private Map<String, Object> stationSnapshot(String code, String stationName, String contactPerson,
                                                String contactPhone, String address, Integer status, String remark) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("stationName", stationName);
        map.put("contactPerson", contactPerson);
        map.put("contactPhone", contactPhone);
        map.put("address", address);
        map.put("status", status);
        map.put("remark", remark);
        return map;
    }

    /** 驿站编码活跃唯一校验（决策 D7：Service 查重，不建数据库唯一索引） */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<Station>()
                .eq(Station::getCode, code);
        if (excludeId != null) {
            wrapper.ne(Station::getId, excludeId);
        }
        Long count = stationMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.STATION_CODE_EXISTS);
        }
    }
}
