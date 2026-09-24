package com.qiujie.service.sync.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.common.LoginUser;
import com.qiujie.common.PageResult;
import com.qiujie.dto.sync.SyncTaskQuery;
import com.qiujie.entity.Station;
import com.qiujie.entity.SyncTask;
import com.qiujie.entity.SyncTaskLog;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.SyncTaskLogMapper;
import com.qiujie.mapper.SyncTaskMapper;
import com.qiujie.service.sync.SyncTaskService;
import com.qiujie.service.sync.support.SyncConstants;
import com.qiujie.util.UserContext;
import com.qiujie.vo.sync.SyncTaskLogVO;
import com.qiujie.vo.sync.SyncTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同步任务服务实现（M9，5 接口，Mock {@code routes/syncTask.js}，架构 §6.2 P9）。
 * <p>
 * 关键口径：
 * <ul>
 *   <li><b>状态机</b>：0 待领取 → 1 执行中 → 2 成功 / 3 失败；{@code trigger} 仅 0 可用、{@code retry} 仅 3 可用，
 *       违反 → 6001；重试回到 0 并 retry_count+1；</li>
 *   <li><b>越权逐端点</b>：详情 / 日志跨站 → 404（不暴露他人资源存在性）；trigger / retry 跨站 → 403；列表静默收敛；</li>
 *   <li><b>通知</b>：Mock 的 trigger 同步跑完即成功，无失败路径，故本批不投递「同步失败」通知。
 *       TODO(扩展): 接通真实采集端后，任务落 3（失败）时经 {@code NotificationService.sendSystem(type=3)}
 *       投递失败通知（架构 §2.3 统一走 notification 出口）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTaskServiceImpl implements SyncTaskService {

    private final SyncTaskMapper taskMapper;
    private final SyncTaskLogMapper logMapper;
    private final StationMapper stationMapper;

    // ==================== 查询 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<SyncTaskVO> page(SyncTaskQuery query) {
        SyncTaskQuery q = query == null ? new SyncTaskQuery() : query;
        int pageNum = q.getPageNum() == null ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null ? 10 : q.getPageSize();

        LambdaQueryWrapper<SyncTask> wrapper = new LambdaQueryWrapper<>();
        // stationId 已由 L1 数据范围静默收敛（非 ADMIN 强制本人驿站）
        wrapper.eq(q.getStationId() != null, SyncTask::getStationId, q.getStationId())
                .eq(q.getStatus() != null, SyncTask::getStatus, q.getStatus());
        String keyword = trimToNull(q.getKeyword());
        if (keyword != null) {
            // 批次号包含匹配（对齐 Mock includes 语义 → LIKE %kw%）
            wrapper.like(SyncTask::getBatchNo, keyword);
        }
        // create_time 倒序；同刻以 id 倒序稳定
        wrapper.orderByDesc(SyncTask::getCreateTime).orderByDesc(SyncTask::getId);

        Page<SyncTask> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<Long, String> stationNames = stationNames(page.getRecords());
        List<SyncTaskVO> list = new ArrayList<>(page.getRecords().size());
        for (SyncTask task : page.getRecords()) {
            list.add(toVO(task, stationNames.get(task.getStationId())));
        }
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Override
    @Transactional(readOnly = true)
    public SyncTaskVO detail(Long id) {
        SyncTask task = findTaskOr404(id);
        assertVisible(task, ErrorCode.NOT_FOUND, "同步任务不存在");
        return toVO(task, stationName(task.getStationId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyncTaskLogVO> logs(Long id) {
        SyncTask task = findTaskOr404(id);
        assertVisible(task, ErrorCode.NOT_FOUND, "同步任务不存在");
        List<SyncTaskLog> rows = logMapper.selectList(new LambdaQueryWrapper<SyncTaskLog>()
                .eq(SyncTaskLog::getTaskId, task.getId()).orderByAsc(SyncTaskLog::getId));
        List<SyncTaskLogVO> list = new ArrayList<>(rows.size());
        for (SyncTaskLog row : rows) {
            SyncTaskLogVO vo = new SyncTaskLogVO();
            vo.setId(row.getId());
            vo.setTaskId(row.getTaskId());
            vo.setBatchNo(row.getBatchNo());
            vo.setLevel(row.getLevel());
            vo.setMessage(row.getMessage());
            vo.setLogTime(row.getLogTime());
            list.add(vo);
        }
        return list;
    }

    // ==================== 写操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncTaskVO trigger(Long id) {
        SyncTask task = findTaskOr404(id);
        assertVisible(task, ErrorCode.FORBIDDEN, null);
        if (!Integer.valueOf(SyncConstants.TASK_PENDING).equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.SYNC_RETRY_NOT_ALLOWED, "仅待领取状态可触发执行");
        }
        LocalDateTime now = LocalDateTime.now();
        // 领取 → 执行中
        task.setStatus(SyncConstants.TASK_RUNNING);
        task.setStartTime(now);
        task.setUpdateTime(now);
        taskMapper.updateById(task);
        pushLog(task, SyncConstants.LOG_INFO, "开始执行同步", now);
        // 执行中 → 成功（Mock：手动触发模拟一次完整同步）
        LocalDateTime finish = LocalDateTime.now();
        int total = task.getParcelTotal() == null ? 0 : task.getParcelTotal();
        task.setStatus(SyncConstants.TASK_SUCCESS);
        task.setSuccessCount(total);
        task.setFailCount(0);
        task.setFinishTime(finish);
        task.setUpdateTime(finish);
        taskMapper.updateById(task);
        pushLog(task, SyncConstants.LOG_INFO, "同步完成，成功 " + total + " 条", finish);
        return toVO(task, stationName(task.getStationId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncTaskVO retry(Long id) {
        SyncTask task = findTaskOr404(id);
        assertVisible(task, ErrorCode.FORBIDDEN, null);
        if (!Integer.valueOf(SyncConstants.TASK_FAILED).equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.SYNC_RETRY_NOT_ALLOWED);
        }
        int nextRetry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(SyncConstants.TASK_PENDING);
        task.setRetryCount(nextRetry);
        task.setErrorMsg(null);
        task.setStartTime(null);
        task.setFinishTime(null);
        task.setUpdateTime(now);
        taskMapper.updateById(task);
        pushLog(task, SyncConstants.LOG_WARN, "重试第 " + nextRetry + " 次，重新进入待领取队列", now);
        return toVO(task, stationName(task.getStationId()));
    }

    // ==================== 内部 ====================

    private SyncTask findTaskOr404(Long id) {
        SyncTask task = id == null ? null : taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "同步任务不存在");
        }
        return task;
    }

    /**
     * 可见性校验：ADMIN 跨站可见；其余角色仅本站。
     * {@code policy} 传 NOT_FOUND → 跨站按「不存在」返回；传 FORBIDDEN → 403（错误码不得统一，架构 §6.2 P9）。
     */
    private void assertVisible(SyncTask task, ErrorCode policy, String notFoundMessage) {
        LoginUser user = currentUser();
        if (RoleEnum.isAdmin(user.getRole())) {
            return;
        }
        if (task.getStationId() != null && task.getStationId().equals(parseStationId(user.getStationId()))) {
            return;
        }
        if (policy == ErrorCode.FORBIDDEN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage);
    }

    private void pushLog(SyncTask task, int level, String message, LocalDateTime time) {
        SyncTaskLog row = new SyncTaskLog();
        row.setTaskId(task.getId());
        row.setBatchNo(task.getBatchNo());
        row.setLevel(level);
        row.setMessage(message);
        row.setLogTime(time);
        logMapper.insert(row);
    }

    private SyncTaskVO toVO(SyncTask task, String stationName) {
        SyncTaskVO vo = new SyncTaskVO();
        vo.setId(task.getId());
        vo.setStationId(task.getStationId());
        vo.setStationName(stationName);
        vo.setBatchNo(task.getBatchNo());
        vo.setStatus(task.getStatus());
        vo.setParcelTotal(task.getParcelTotal());
        vo.setSuccessCount(task.getSuccessCount());
        vo.setFailCount(task.getFailCount());
        vo.setRetryCount(task.getRetryCount());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setAssignTime(task.getAssignTime());
        vo.setStartTime(task.getStartTime());
        vo.setFinishTime(task.getFinishTime());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private Map<Long, String> stationNames(Collection<SyncTask> tasks) {
        Set<Long> ids = new LinkedHashSet<>();
        for (SyncTask task : tasks) {
            if (task.getStationId() != null) {
                ids.add(task.getStationId());
            }
        }
        return stationNames(ids);
    }

    private Map<Long, String> stationNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Station> stations = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                .select(Station::getId, Station::getStationName).in(Station::getId, ids));
        Map<Long, String> map = new LinkedHashMap<>();
        for (Station station : stations) {
            map.put(station.getId(), station.getStationName());
        }
        return map;
    }

    private String stationName(Long stationId) {
        if (stationId == null) {
            return null;
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? null : station.getStationName();
    }

    private LoginUser currentUser() {
        LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private Long parseStationId(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(stationId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
