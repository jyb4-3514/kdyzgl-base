package com.qiujie.service.parcel.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.common.LoginUser;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.parcel.ParcelQuery;
import com.qiujie.dto.parcel.ParcelRankingQuery;
import com.qiujie.dto.parcel.ParcelTrendQuery;
import com.qiujie.entity.Parcel;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.ParcelMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.parcel.ParcelService;
import com.qiujie.service.parcel.support.ParcelCapacityAnalyzer;
import com.qiujie.service.parcel.support.ParcelConstants;
import com.qiujie.service.parcel.support.ParcelCursorCodec;
import com.qiujie.service.parcel.support.ParcelDailyCount;
import com.qiujie.service.parcel.support.ParcelForecaster;
import com.qiujie.service.parcel.support.ParcelMetrics;
import com.qiujie.service.parcel.support.ParcelStationStat;
import com.qiujie.service.parcel.support.ParcelStatusMachine;
import com.qiujie.service.parcel.support.ParcelSummaryRow;
import com.qiujie.util.DataScopeContext;
import com.qiujie.util.DesensitizeUtil;
import com.qiujie.util.UserContext;
import com.qiujie.vo.parcel.ParcelPageVO;
import com.qiujie.vo.parcel.ParcelRankingVO;
import com.qiujie.vo.parcel.ParcelSummaryVO;
import com.qiujie.vo.parcel.ParcelTrendPointVO;
import com.qiujie.vo.parcel.ParcelVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 包裹服务实现（M10，6 接口，Mock {@code routes/parcel.js}，架构 §6.2 P10）。
 * <p>
 * 关键口径（逐条对齐 Mock，契约不可破坏）：
 * <ul>
 *   <li><b>数据范围</b>：列表用 L1 收敛后的 {@code stationId}；summary/trend/ranking 按
 *       {@code scopedStation}（ADMIN=全站、非 ADMIN=本人驿站，忽略 stationId 入参）；</li>
 *   <li><b>越权</b>：详情跨站 → HTTP 404「包裹不存在」；取件跨站 → 业务码 7001；</li>
 *   <li><b>取件状态机</b>（{@link ParcelStatusMachine}）：非可取 7002、已被他人取 7003；
 *       并发安全由 {@code ParcelMapper#updatePickup} 的条件更新（{@code WHERE status = 1}）保证；</li>
 *   <li><b>S7-3 分页</b>：默认 {@code CURSOR}（首页 keyset，深页 OFFSET 兜底并限 {@code maxOffsetDepth}）；
 *       显式 `cursor` 入参 + `exposeCursor` 开关启用完整游标翻页（opt-in 扩展）；</li>
 *   <li><b>S7-1/S7-2</b>：预测/容量经配置开关以**可选附加字段**输出，默认不改变既有出参结构；</li>
 *   <li><b>脱敏</b>：{@code receiverName/receiverPhone} 出参走 {@link DesensitizeUtil}。</li>
 * </ul>
 * 空表（真实采集未上线）时 summary/trend/ranking 返回 0/空数组，不报错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelServiceImpl implements ParcelService {

    private final ParcelMapper parcelMapper;
    private final StationMapper stationMapper;
    private final AlgoProperties algoProperties;

    // ==================== 查询：列表 ====================

    @Override
    @Transactional(readOnly = true)
    public ParcelPageVO page(ParcelQuery query) {
        ParcelQuery q = query == null ? new ParcelQuery() : query;
        int pageNum = q.getPageNum() == null ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null ? 10 : q.getPageSize();
        Long stationId = q.getStationId(); // 已由 L1 收敛（非 ADMIN 强制本人驿站）
        Integer status = q.getStatus();
        String waybillNo = trimToNull(q.getWaybillNo());
        LocalDateTime startTime = parseFlexibleTime(q.getStartTime());
        LocalDateTime endTime = parseFlexibleTime(q.getEndTime());

        AlgoProperties.ParcelPage pageCfg = algoProperties.getParcel().getPage();
        // 游标仅在本模式生效：OFFSET 模式下忽略 cursor（保持 OFFSET 语义纯粹）
        boolean cursorMode = isCursorMode(pageCfg);
        ParcelCursorCodec.Cursor cursor = cursorMode ? decodeCursor(q.getCursor()) : null;

        long total = parcelMapper.countPage(stationId, status, waybillNo, startTime, endTime);

        LocalDateTime cursorTime = null;
        Long cursorId = null;
        long offset = 0L;
        if (cursor != null) {
            // keyset：直接定位上一页末行之后，扫描行恒定 O(log N + pageSize)
            cursorTime = cursor.inboundTime();
            cursorId = cursor.id();
        } else if (pageNum > 1) {
            long skipPages = pageNum - 1L;
            if (skipPages > pageCfg.getMaxOffsetDepth()) {
                // 深分页保护：超上限不执行 OFFSET 扫描（避免 20 万级扫 20020 行，架构 R-6）
                // TODO(扩展): 超限返回空 list（正确 total）为契约外行为，是否改为显式业务码需主智能体裁定。
                log.warn("包裹列表深分页超上限，已跳过执行：pageNum={}, maxOffsetDepth={}",
                        pageNum, pageCfg.getMaxOffsetDepth());
                return ParcelPageVO.of(total, pageNum, pageSize, List.of(), null);
            }
            offset = skipPages * pageSize;
        }

        List<Parcel> rows = parcelMapper.selectPage(stationId, status, waybillNo, startTime, endTime,
                cursorTime, cursorId, offset, pageSize);

        Map<Long, String> stationNames = stationNames(collectStationIds(rows));
        List<ParcelVO> list = new ArrayList<>(rows.size());
        for (Parcel row : rows) {
            list.add(toVO(row, stationNames));
        }

        String nextCursor = null;
        if (cursorMode && pageCfg.isExposeCursor() && rows.size() == pageSize && !rows.isEmpty()) {
            Parcel last = rows.get(rows.size() - 1);
            nextCursor = ParcelCursorCodec.encode(last.getInboundTime(), last.getId());
        }
        return ParcelPageVO.of(total, pageNum, pageSize, list, nextCursor);
    }

    // ==================== 查询：看板 ====================

    @Override
    @Transactional(readOnly = true)
    public ParcelSummaryVO summary() {
        Long scopeId = resolveReadScope();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        ParcelSummaryRow row = parcelMapper.selectSummary(scopeId, todayStart);

        ParcelSummaryVO vo = new ParcelSummaryVO();
        long total = row == null ? 0L : nz(row.getParcelTotal());
        long todayInbound = row == null ? 0L : nz(row.getTodayInbound());
        long todayPickup = row == null ? 0L : nz(row.getTodayPickup());
        vo.setParcelTotal(total);
        vo.setTodayInbound(todayInbound);
        vo.setTodayPickup(todayPickup);
        vo.setPendingPickup(row == null ? 0L : nz(row.getPendingPickup()));
        vo.setAbnormalCount(row == null ? 0L : nz(row.getAbnormalCount()));
        // pickupRate = todayPickup / todayInbound（分母 0 → 0），与 Mock 同口径
        vo.setPickupRate(ParcelMetrics.rate(todayPickup, todayInbound));
        return vo;
    }

    // ==================== 查询：趋势（S7-1） ====================

    @Override
    @Transactional(readOnly = true)
    public List<ParcelTrendPointVO> trend(ParcelTrendQuery query) {
        ParcelTrendQuery q = query == null ? new ParcelTrendQuery() : query;
        AlgoProperties.ParcelTrend trendCfg = algoProperties.getParcel().getTrend();
        int days = normalizeDays(q.getDays(), trendCfg);
        Long scopeId = resolveReadScope();

        LocalDate today = LocalDate.now();
        LocalDate windowStartDate = today.minusDays(days - 1L);
        LocalDateTime windowStart = windowStartDate.atStartOfDay();
        Map<LocalDate, Long> inboundMap = toDayMap(parcelMapper.countInboundByDay(scopeId, windowStart));
        Map<LocalDate, Long> pickupMap = toDayMap(parcelMapper.countPickupByDay(scopeId, windowStart));

        // 历史点（口径与 Mock 一致：窗口内按天分桶，缺失日补 0）
        double[] inboundSeries = new double[days];
        List<ParcelTrendPointVO> points = new ArrayList<>(days);
        for (int d = 0; d < days; d++) {
            LocalDate date = windowStartDate.plusDays(d);
            long inbound = inboundMap.getOrDefault(date, 0L);
            long pickup = pickupMap.getOrDefault(date, 0L);
            inboundSeries[d] = inbound;
            ParcelTrendPointVO point = new ParcelTrendPointVO();
            point.setDate(date.format(ParcelConstants.DATE_FMT));
            point.setInbound(inbound);
            point.setPickup(pickup);
            points.add(point);
        }

        // S7-1 预测（可选叠加）：默认关闭，不改变既有出参结构（契约扩展开关）
        AlgoProperties.Forecast forecastCfg = algoProperties.getParcel().getForecast();
        if (forecastCfg.isAppendForecast()) {
            ParcelForecaster.ForecastResult result = ParcelForecaster.forecast(inboundSeries, forecastCfg);
            double[] fitted = result.fitted();
            for (int d = 0; d < days && d < fitted.length; d++) {
                points.get(d).setForecastInbound(ParcelMetrics.round4(fitted[d]));
            }
            // 未来步长预测仅作观测（未来点出参需契约裁定，见 TODO）
            log.info("S7-1 包裹入库量预测：model={}, days={}, horizon={}", result.model(), days,
                    result.horizon().length);
            // TODO(扩展): 将 horizon 未来预测点作为独立出参暴露（需契约裁定，默认不改 trend 结构）。
        }
        return points;
    }

    // ==================== 查询：排行（S7-2） ====================

    @Override
    @Transactional(readOnly = true)
    public List<ParcelRankingVO> ranking(ParcelRankingQuery query) {
        ParcelRankingQuery q = query == null ? new ParcelRankingQuery() : query;
        // 驿站全集（含 0 包裹驿站，含停用驿站），对齐 Mock 遍历 db.stations
        List<Station> stations = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                .select(Station::getId, Station::getStationName)
                .orderByAsc(Station::getId));
        Map<Long, ParcelStationStat> stats = toStationStatMap(parcelMapper.selectStationStats());

        List<ParcelRankingVO> list = new ArrayList<>(stations.size());
        for (Station station : stations) {
            ParcelStationStat stat = stats.get(station.getId());
            long parcelTotal = stat == null ? 0L : nz(stat.getParcelTotal());
            long picked = stat == null ? 0L : nz(stat.getPicked());
            long abnormal = stat == null ? 0L : nz(stat.getAbnormal());

            ParcelRankingVO vo = new ParcelRankingVO();
            vo.setStationId(station.getId());
            vo.setStationName(station.getStationName());
            vo.setParcelTotal(parcelTotal);
            vo.setPickupRate(ParcelMetrics.rate(picked, parcelTotal));
            vo.setAbnormalRate(ParcelMetrics.rate(abnormal, parcelTotal));
            list.add(vo);
        }

        // 基础排序：包裹量降序（并列按 stationId 升序，等价 Mock 稳定排序）
        list.sort((a, b) -> {
            int byTotal = Long.compare(b.getParcelTotal(), a.getParcelTotal());
            return byTotal != 0 ? byTotal : Long.compare(a.getStationId(), b.getStationId());
        });

        // S7-2 容量/热力（可选叠加，默认关闭）：在「全站集合」上计算，使异常驿站识别不受查看范围影响
        if (algoProperties.getParcel().getCapacity().isAppendToRanking()) {
            appendCapacity(list, stats);
        }

        // 非 ADMIN 只保留本人驿站（Mock：先全量排行再 filter）
        Long scopeId = resolveReadScope();
        if (scopeId != null) {
            list.removeIf(vo -> !scopeId.equals(vo.getStationId()));
        }

        // 排序切换（受支持指标，其余回退默认）；stable sort 保留基础排序作为并列次序
        AlgoProperties.ParcelRanking rankCfg = algoProperties.getParcel().getRanking();
        String sort = q.getSort() == null ? "" : q.getSort().trim();
        if (rankCfg.getSortableMetrics() != null && rankCfg.getSortableMetrics().contains(sort)) {
            switch (sort) {
                case ParcelConstants.RANK_METRIC_PICKUP_RATE ->
                        list.sort((a, b) -> Double.compare(b.getPickupRate(), a.getPickupRate()));
                case ParcelConstants.RANK_METRIC_ABNORMAL_RATE ->
                        list.sort((a, b) -> Double.compare(b.getAbnormalRate(), a.getAbnormalRate()));
                default -> {
                    // 不可达：受支持集合已限定
                }
            }
        }
        return list;
    }

    // ==================== 查询：详情 / 取件核销 ====================

    @Override
    @Transactional(readOnly = true)
    public ParcelVO detail(Long id) {
        Parcel parcel = id == null ? null : parcelMapper.selectById(id);
        if (parcel == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "包裹不存在");
        }
        LoginUser user = currentUser();
        Long ownStation = parseStationId(user.getStationId());
        // 跨站按「不存在」返回，避免暴露他人驿站包裹的存在性（对齐 Mock detail）
        if (!RoleEnum.isAdmin(user.getRole()) && !Objects.equals(parcel.getStationId(), ownStation)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "包裹不存在");
        }
        return toVO(parcel, stationNames(Collections.singletonList(parcel.getStationId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParcelVO pickup(Long id) {
        Parcel parcel = id == null ? null : parcelMapper.selectById(id);
        // 不存在 / 跨站越权统一 7001（对齐 Mock：越权按不存在处理）
        if (parcel == null) {
            throw new BusinessException(ErrorCode.PARCEL_NOT_EXISTS);
        }
        LoginUser user = currentUser();
        Long ownStation = parseStationId(user.getStationId());
        if (!RoleEnum.isAdmin(user.getRole()) && !Objects.equals(parcel.getStationId(), ownStation)) {
            throw new BusinessException(ErrorCode.PARCEL_NOT_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now();
        // 并发安全：条件更新（WHERE status = 1）保证同一包裹仅一次成功，等价乐观锁（以 status 为版本位）
        int affected = parcelMapper.updatePickup(parcel.getId(), user.getUserId(), now);
        if (affected == 1) {
            Parcel updated = parcelMapper.selectById(parcel.getId());
            if (updated == null) {
                throw new BusinessException(ErrorCode.PARCEL_NOT_EXISTS);
            }
            return toVO(updated, stationNames(Collections.singletonList(updated.getStationId())));
        }

        // 更新失败：重新读取判定归因（7003 已被他人取 / 7002 状态不允许）
        Parcel latest = parcelMapper.selectById(parcel.getId());
        if (latest == null) {
            throw new BusinessException(ErrorCode.PARCEL_NOT_EXISTS);
        }
        ParcelStatusMachine.PickupOutcome outcome =
                ParcelStatusMachine.decide(latest.getStatus(), latest.getPickupEmployeeId(), user.getUserId());
        if (outcome == ParcelStatusMachine.PickupOutcome.PICKED_BY_OTHER) {
            throw new BusinessException(ErrorCode.PARCEL_PICKED_BY_OTHER);
        }
        throw new BusinessException(ErrorCode.PARCEL_STATUS_INVALID);
    }

    // ==================== 内部：S7-2 容量/热力回填 ====================

    /**
     * S7-2：按全站集合计算利用率与 IQR 离群，回填到扩展字段。
     * 为什么用「全站」集合算 IQR：离群是相对全体驿站的统计量，不能因前端只看本站而改变口径。
     */
    private void appendCapacity(List<ParcelRankingVO> list, Map<Long, ParcelStationStat> stats) {
        AlgoProperties.Capacity capCfg = algoProperties.getParcel().getCapacity();
        double k = algoProperties.getParcel().getOutlier().getIqrK();
        int shelfCapacity = capCfg.getShelfCapacity();

        double[] utilizations = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            ParcelRankingVO vo = list.get(i);
            ParcelStationStat stat = stats.get(vo.getStationId());
            long pending = stat == null ? 0L : nz(stat.getPending());
            vo.setPendingPickup(pending);
            double utilization = ParcelCapacityAnalyzer.utilization(pending, shelfCapacity);
            vo.setUtilization(ParcelMetrics.round4(utilization));
            vo.setCapacityLevel(ParcelCapacityAnalyzer.level(utilization, capCfg.getUtilWarn(),
                    capCfg.getUtilCritical()));
            utilizations[i] = utilization;
        }
        boolean[] outliers = ParcelCapacityAnalyzer.iqr(utilizations, k).outlier();
        for (int i = 0; i < list.size() && i < outliers.length; i++) {
            list.get(i).setOutlier(outliers[i]);
        }
    }

    // ==================== 内部：作用域与转换 ====================

    /**
     * summary/trend/ranking 的读作用域（对齐 Mock {@code scopedStation}）：
     * ADMIN → null（全站）；非 ADMIN → 本人驿站（无归属收敛为哨兵 → 空数据）。
     */
    private Long resolveReadScope() {
        LoginUser user = currentUser();
        if (RoleEnum.isAdmin(user.getRole())) {
            return null;
        }
        Long own = parseStationId(user.getStationId());
        return own != null ? own : DataScopeContext.NO_DATA_STATION_ID;
    }

    private ParcelVO toVO(Parcel parcel, Map<Long, String> stationNames) {
        ParcelVO vo = new ParcelVO();
        vo.setId(parcel.getId());
        vo.setStationId(parcel.getStationId());
        vo.setStationName(stationNames.get(parcel.getStationId()));
        vo.setWaybillNo(parcel.getWaybillNo());
        vo.setStatus(parcel.getStatus());
        // 出参脱敏（C-07）：库中存完整值，返回前统一遮蔽
        vo.setReceiverName(DesensitizeUtil.maskName(parcel.getReceiverName()));
        vo.setReceiverPhone(DesensitizeUtil.maskPhone(parcel.getReceiverPhone()));
        vo.setShelfCode(parcel.getShelfCode());
        vo.setInboundTime(parcel.getInboundTime());
        vo.setPickupEmployeeId(parcel.getPickupEmployeeId());
        vo.setPickupTime(parcel.getPickupTime());
        vo.setSyncBatchNo(parcel.getSyncBatchNo());
        vo.setRemark(parcel.getRemark());
        vo.setCreateTime(parcel.getCreateTime());
        vo.setUpdateTime(parcel.getUpdateTime());
        return vo;
    }

    private Map<Long, String> stationNames(Collection<Long> ids) {
        Set<Long> nonNull = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                nonNull.add(id);
            }
        }
        if (nonNull.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Station> rows = stationMapper.selectList(new LambdaQueryWrapper<Station>()
                .select(Station::getId, Station::getStationName)
                .in(Station::getId, nonNull));
        Map<Long, String> map = new LinkedHashMap<>();
        for (Station station : rows) {
            map.put(station.getId(), station.getStationName());
        }
        return map;
    }

    private List<Long> collectStationIds(List<Parcel> rows) {
        List<Long> ids = new ArrayList<>(rows.size());
        for (Parcel row : rows) {
            ids.add(row.getStationId());
        }
        return ids;
    }

    private Map<LocalDate, Long> toDayMap(List<ParcelDailyCount> rows) {
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        for (ParcelDailyCount row : rows) {
            if (row.getStatDate() != null) {
                map.put(row.getStatDate(), row.getCnt() == null ? 0L : row.getCnt());
            }
        }
        return map;
    }

    private Map<Long, ParcelStationStat> toStationStatMap(List<ParcelStationStat> rows) {
        Map<Long, ParcelStationStat> map = new LinkedHashMap<>();
        for (ParcelStationStat row : rows) {
            if (row.getStationId() != null) {
                map.put(row.getStationId(), row);
            }
        }
        return map;
    }

    // ==================== 内部：小工具 ====================

    private boolean isCursorMode(AlgoProperties.ParcelPage cfg) {
        return cfg.getMode() == null
                || ParcelConstants.PAGE_MODE_CURSOR.equalsIgnoreCase(cfg.getMode().trim());
    }

    /** 天数归一：缺省/0 → 7；其余收敛到 [minDays, maxDays]（对齐 Mock 的默认与钳制） */
    private int normalizeDays(Integer raw, AlgoProperties.ParcelTrend cfg) {
        int value = (raw == null || raw == 0) ? 7 : raw;
        int min = Math.max(1, cfg.getMinDays());
        int max = Math.max(min, cfg.getMaxDays());
        return Math.min(max, Math.max(min, value));
    }

    private ParcelCursorCodec.Cursor decodeCursor(String cursor) {
        try {
            return ParcelCursorCodec.decode(cursor);
        } catch (IllegalArgumentException e) {
            // 非法游标显式报错，避免静默当作首页导致翻页错位
            throw new BusinessException(ErrorCode.BAD_REQUEST, "游标格式不正确");
        }
    }

    /** 宽松时间解析：支持 yyyy-MM-dd HH:mm:ss 与 yyyy-MM-dd；空白或非法返回 null（忽略该筛选） */
    private LocalDateTime parseFlexibleTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDateTime.parse(value, ParcelConstants.DATE_TIME_FMT);
            }
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value).atStartOfDay();
            }
        } catch (DateTimeParseException e) {
            log.warn("包裹时间筛选参数解析失败，已忽略：{}", text);
        }
        return null;
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

    private long nz(Long value) {
        return value == null ? 0L : value;
    }
}
