package com.qiujie.service.systemlog.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.systemlog.ClientLogIngestRequest;
import com.qiujie.dto.systemlog.ClientLogItem;
import com.qiujie.dto.systemlog.ClientLogQuery;
import com.qiujie.entity.ClientLog;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.ClientLogMapper;
import com.qiujie.service.support.ClientLogQueryValidator;
import com.qiujie.service.support.ClientLogSanitizer;
import com.qiujie.service.support.LogFingerprintAggregator;
import com.qiujie.service.systemlog.ClientLogService;
import com.qiujie.util.UserContext;
import com.qiujie.vo.systemlog.ClientLogClearVO;
import com.qiujie.vo.systemlog.ClientLogIngestVO;
import com.qiujie.vo.systemlog.ClientLogPageVO;
import com.qiujie.vo.systemlog.ClientLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 运行日志服务实现（api.md §7.1 #14~#16，架构 §6.2 P1，算法 S8）。
 * <p>
 * 落库字段白名单 = api.md §7.5；指纹去重窗口与文本截断走 {@code hrm.algo.log.*}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientLogServiceImpl implements ClientLogService {

    /**
     * 单批上报上限。这是<b>契约常量</b>（Mock {@code BATCH_MAX}=100，仅挡异常放大，不改变前端 20 条批次大小），
     * 非算法超参，故不入 {@code hrm.algo.*}（algo §11 未为该值定义键）；如需调整须同步 api.md 契约。
     */
    private static final int BATCH_MAX = 100;

    private final ClientLogMapper clientLogMapper;
    private final LogFingerprintAggregator fingerprintAggregator;
    private final AlgoProperties algoProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientLogIngestVO ingest(ClientLogIngestRequest request) {
        List<ClientLogItem> logs = request == null ? null : request.getLogs();
        if (logs == null || logs.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "logs 须为非空数组");
        }
        if (logs.size() > BATCH_MAX) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单批日志不超过 " + BATCH_MAX + " 条");
        }

        int textMax = algoProperties.getLog().getTextMax();
        long windowSeconds = Math.max(1, algoProperties.getLog().getDedupeWindowSeconds());
        LocalDateTime now = LocalDateTime.now();
        // 上报人一律取登录身份：日志行是 ADMIN 排障视图，信任入参 employeeId 会让越权者污染他人日志归属
        Long employeeId = UserContext.getUserId();

        for (ClientLogItem item : logs) {
            ClientLog log = ClientLogSanitizer.sanitize(item, now, textMax);
            log.setEmployeeId(employeeId);
            storeWithFingerprint(log, windowSeconds);
        }
        return new ClientLogIngestVO(logs.size());
    }

    @Override
    @Transactional(readOnly = true)
    public ClientLogPageVO page(ClientLogQuery query) {
        ClientLogQueryValidator.validate(query);
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();

        Page<ClientLog> page = clientLogMapper.selectPage(new Page<>(pageNum, pageSize), pageQueryWrapper(query));

        ClientLogPageVO.Counts counts = new ClientLogPageVO.Counts();
        counts.setTotal(page.getTotal());
        counts.setError(countByLevel(query, "ERROR"));
        counts.setWarn(countByLevel(query, "WARN"));
        counts.setInfo(countByLevel(query, "INFO"));
        counts.setSourceCount(countDistinctSource(query));

        ClientLogPageVO vo = new ClientLogPageVO();
        vo.setTotal(page.getTotal());
        vo.setPageNum(pageNum);
        vo.setPageSize(pageSize);
        vo.setList(toVOList(page.getRecords()));
        vo.setCounts(counts);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientLogClearVO clear() {
        long before = clientLogMapper.selectCount(null);
        clientLogMapper.deleteAll();
        // 索引指向已删除的行：不同步清空会让后续命中回写 0 行（虽可自愈，但会白跑一次更新）
        fingerprintAggregator.clear();
        return new ClientLogClearVO(before);
    }

    // ==================== 指纹聚合写路径 ====================

    /**
     * 单条日志按指纹落库（S8）：内存索引命中 → 原子累加；未命中 → 回库按指纹+窗口兜底；仍无 → 新建行。
     * 三级路径保证「窗口内同指纹只累加 count」（跨实例/重启由回库兜底覆盖）。
     */
    private void storeWithFingerprint(ClientLog log, long windowSeconds) {
        String fingerprint = ClientLogSanitizer.fingerprint(log);

        Long cachedRowId = fingerprintAggregator.findHit(fingerprint, log.getTime());
        if (cachedRowId != null) {
            if (clientLogMapper.incrementCount(cachedRowId, log.getTime()) > 0) {
                return;
            }
            // 行已被清空/删除（如清空端点或回滚）：索引失效，落到回库兜底
            fingerprintAggregator.evict(fingerprint);
        }

        ClientLog existing = findExistingWithinWindow(log, windowSeconds);
        if (existing != null) {
            clientLogMapper.incrementCount(existing.getId(), log.getTime());
            fingerprintAggregator.remember(fingerprint, existing.getId(), log.getTime());
            return;
        }

        log.setCount(1);
        log.setFirstTime(log.getTime());
        log.setLastTime(log.getTime());
        clientLogMapper.insert(log);
        fingerprintAggregator.remember(fingerprint, log.getId(), log.getTime());
    }

    /**
     * 回库兜底：同一指纹、且 last_time 在「本次事件时间 - 窗口」之后的最近一条。
     * 窗口锚点用 last_time（事件时间）而非墙钟，与 S8 离线原型及 client_log.last_time 列语义一致。
     */
    private ClientLog findExistingWithinWindow(ClientLog log, long windowSeconds) {
        LambdaQueryWrapper<ClientLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ClientLog::getId);
        wrapper.eq(ClientLog::getMessage, log.getMessage());
        if (log.getRoute() == null) {
            wrapper.isNull(ClientLog::getRoute);
        } else {
            wrapper.eq(ClientLog::getRoute, log.getRoute());
        }
        if (log.getCode() == null) {
            wrapper.isNull(ClientLog::getCode);
        } else {
            wrapper.eq(ClientLog::getCode, log.getCode());
        }
        wrapper.ge(ClientLog::getLastTime, log.getTime().minusSeconds(windowSeconds));
        wrapper.orderByDesc(ClientLog::getLastTime);
        wrapper.last("LIMIT 1");
        List<ClientLog> rows = clientLogMapper.selectList(wrapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ==================== 查询 ====================

    /** 列表查询：白名单字段 + 过滤 + time 倒序（id 兜底保证深分页稳定） */
    private LambdaQueryWrapper<ClientLog> pageQueryWrapper(ClientLogQuery query) {
        LambdaQueryWrapper<ClientLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ClientLog::getId, ClientLog::getTime, ClientLog::getLevel, ClientLog::getSource,
                ClientLog::getEmployeeId, ClientLog::getRoute, ClientLog::getMessage, ClientLog::getStack,
                ClientLog::getMethod, ClientLog::getPath, ClientLog::getStatus, ClientLog::getCode,
                ClientLog::getDuration, ClientLog::getUa, ClientLog::getCount, ClientLog::getFirstTime,
                ClientLog::getLastTime);
        applyFilters(wrapper, query);
        wrapper.orderByDesc(ClientLog::getTime).orderByDesc(ClientLog::getId);
        return wrapper;
    }

    /** 统计某级别条数（口径 = 当前筛选结果；在筛选之上再叠加级别条件，与 Mock counts 一致） */
    private long countByLevel(ClientLogQuery query, String level) {
        LambdaQueryWrapper<ClientLog> wrapper = new LambdaQueryWrapper<>();
        applyFilters(wrapper, query);
        wrapper.eq(ClientLog::getLevel, level);
        Long count = clientLogMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /** 涉及端数（distinct source，口径 = 当前筛选结果） */
    private long countDistinctSource(ClientLogQuery query) {
        LambdaQueryWrapper<ClientLog> wrapper = new LambdaQueryWrapper<>();
        applyFilters(wrapper, query);
        wrapper.select(ClientLog::getSource).groupBy(ClientLog::getSource);
        return clientLogMapper.selectList(wrapper).size();
    }

    /** 公共过滤条件（列表与统计共用，避免两处口径漂移） */
    private void applyFilters(LambdaQueryWrapper<ClientLog> wrapper, ClientLogQuery query) {
        String level = query.getLevel();
        if (notBlank(level)) {
            wrapper.eq(ClientLog::getLevel, level);
        }
        String source = query.getSource();
        if (notBlank(source)) {
            wrapper.eq(ClientLog::getSource, source);
        }
        if (query.getEmployeeId() != null) {
            wrapper.eq(ClientLog::getEmployeeId, query.getEmployeeId());
        }
        LocalDateTime start = ClientLogSanitizer.parseLenientDateTime(query.getStartTime(), null);
        LocalDateTime end = ClientLogSanitizer.parseLenientDateTime(query.getEndTime(), null);
        if (start != null) {
            wrapper.ge(ClientLog::getTime, start);
        }
        if (end != null) {
            wrapper.le(ClientLog::getTime, end);
        }
        String keyword = query.getKeyword();
        if (notBlank(keyword)) {
            // 模糊包含匹配（Mock 用 includes，不仅前缀）；LIKE 通配符转义防全表匹配
            wrapper.like(ClientLog::getMessage, escapeLike(keyword.trim()));
        }
    }

    private List<ClientLogVO> toVOList(List<ClientLog> records) {
        List<ClientLogVO> list = new ArrayList<>(records.size());
        for (ClientLog record : records) {
            ClientLogVO vo = new ClientLogVO();
            vo.setId(record.getId());
            vo.setTime(record.getTime());
            vo.setLevel(record.getLevel());
            vo.setSource(record.getSource());
            vo.setEmployeeId(record.getEmployeeId());
            vo.setRoute(record.getRoute());
            vo.setMessage(record.getMessage());
            vo.setStack(record.getStack());
            vo.setMethod(record.getMethod());
            vo.setPath(record.getPath());
            vo.setStatus(record.getStatus());
            vo.setCode(record.getCode());
            vo.setDuration(record.getDuration());
            vo.setUa(record.getUa());
            vo.setCount(record.getCount());
            vo.setFirstTime(record.getFirstTime());
            vo.setLastTime(record.getLastTime());
            list.add(vo);
        }
        return list;
    }

    /** LIKE 通配符转义（反斜杠 / % / _），MySQL 与 PostgreSQL 均以反斜杠为默认转义符 */
    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
