package com.qiujie.service.kpi.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.kpi.KpiCalculateRequest;
import com.qiujie.dto.kpi.KpiMetricDetailItem;
import com.qiujie.dto.kpi.KpiRankingQuery;
import com.qiujie.dto.kpi.KpiScoreQuery;
import com.qiujie.entity.Employee;
import com.qiujie.entity.KpiMetric;
import com.qiujie.entity.KpiScore;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.KpiMetricMapper;
import com.qiujie.mapper.KpiScoreMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.kpi.KpiActualValueSource;
import com.qiujie.service.kpi.KpiScoreService;
import com.qiujie.service.kpi.support.KpiAchievementPolicy;
import com.qiujie.service.kpi.support.KpiConstants;
import com.qiujie.service.kpi.support.KpiQuantileMapper;
import com.qiujie.service.kpi.support.KpiRankingPolicy;
import com.qiujie.service.kpi.support.KpiScorePolicy;
import com.qiujie.util.UserContext;
import com.qiujie.vo.kpi.KpiCalculateResultVO;
import com.qiujie.vo.kpi.KpiRankingPageVO;
import com.qiujie.vo.kpi.KpiScoreDetailVO;
import com.qiujie.vo.kpi.KpiScoreItemVO;
import com.qiujie.vo.kpi.KpiScorePageVO;
import com.qiujie.vo.kpi.KpiScoreRuleVO;
import com.qiujie.vo.kpi.KpiScoreVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * KPI 算分与评分查询服务实现（对齐 Mock {@code kpiStore} 算分段 + {@code routes/kpi.js}）。
 * <p>
 * 算分口径（与 Mock 逐式对齐）：单项得分 {@link KpiScorePolicy#itemScore}、达成率 {@link KpiAchievementPolicy}、
 * 总分按「实际适用指标权重」归一 {@link KpiScorePolicy#totalScore}、排名竞赛法 {@link KpiRankingPolicy}。
 * 参数（阶梯/等级阈值/分位开关/权重守卫）全部取 {@code hrm.algo.kpi.*}，禁硬编码。
 * <p>
 * 幂等：同月同员工**覆盖重建**（重算即覆盖，含清除已停用指标的历史权重），重复算分结果恒定（对齐 Mock）。
 * 分位模式（默认关闭）在「样本量 ≥ minSamples」时按同 scope 员工分布取百分位；否则回落绝对评分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiScoreServiceImpl implements KpiScoreService {

    /** 考核月份格式：yyyy-MM（先过正则，Mock {@code isMonth} 仅校验格式，不校验真实月份） */
    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final KpiMetricMapper kpiMetricMapper;
    private final KpiScoreMapper kpiScoreMapper;
    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;
    private final KpiActualValueSource actualValueSource;
    private final AlgoProperties algoProperties;

    // ==================== 算分 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KpiCalculateResultVO calculate(KpiCalculateRequest request) {
        KpiCalculateRequest safe = request == null ? new KpiCalculateRequest() : request;
        String month = safe.getMonth();
        // month 必填：Mock 注释即要求「算哪个月由调用方明确」；且 kpi_score.month 为 NOT NULL，
        // 若放任空值会落脏数据（Mock 的 isMonth 对空值放行属潜在缺陷，此处收紧为非空）
        if (month == null || month.isBlank() || !MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        List<KpiMetric> metrics = enabledMetrics();
        if (metrics.isEmpty()) {
            // 无启用指标：列表空、不报错（降级），对应 9203
            throw new BusinessException(ErrorCode.KPI_NO_METRIC);
        }

        List<Employee> employees = activeEmployees(safe.getStationId(), safe.getEmployeeIds());
        AlgoProperties.Kpi cfg = algoProperties.getKpi();
        Map<String, Map<Long, BigDecimal>> quantileContext = buildQuantileContext(metrics, employees, month, cfg);

        LocalDateTime calculateTime = LocalDateTime.now();
        int employeeCount = 0;
        int scoreCount = 0;
        for (Employee employee : employees) {
            List<KpiMetric> usable = new ArrayList<>();
            for (KpiMetric metric : metrics) {
                if (appliesTo(metric, employee)) {
                    usable.add(metric);
                }
            }
            // 该员工无适用指标（roleScope 全排除）：跳过，不产生评分行（与 Mock 一致）
            if (usable.isEmpty()) {
                continue;
            }

            List<KpiMetricDetailItem> items = new ArrayList<>(usable.size());
            List<KpiScorePolicy.ScoreItem> scoreItems = new ArrayList<>(usable.size());
            BigDecimal rateSum = BigDecimal.ZERO;
            int weightSum = 0;
            for (KpiMetric metric : usable) {
                BigDecimal actual = actualValueSource.actualValue(metric, employee.getId(), month);
                BigDecimal rate = KpiAchievementPolicy.achievement(actual, metric.getTargetValue(), metric.getDirection());
                int score = itemScoreOf(metric, rate, employee.getId(), quantileContext, cfg);
                items.add(toDetailItem(metric, actual, rate, score));
                scoreItems.add(new KpiScorePolicy.ScoreItem(score, nzWeight(metric)));
                weightSum += nzWeight(metric);
                rateSum = rateSum.add(rate);
            }
            BigDecimal total = KpiScorePolicy.totalScore(scoreItems);
            BigDecimal achievementRate = rateSum.divide(BigDecimal.valueOf(items.size()), 4, RoundingMode.HALF_UP);
            String level = KpiScorePolicy.level(total, cfg.getLevels());

            upsertScore(employee, month, total, achievementRate, level, items, weightSum, calculateTime);
            employeeCount++;
            scoreCount += items.size();
        }

        KpiCalculateResultVO vo = new KpiCalculateResultVO();
        vo.setMonth(month);
        vo.setEmployeeCount(employeeCount);
        vo.setMetricCount(metrics.size());
        vo.setScoreCount(scoreCount);
        return vo;
    }

    /** 单项得分：分位模式（有上下文命中）走百分位，否则走绝对评分 */
    private int itemScoreOf(KpiMetric metric, BigDecimal rate, Long employeeId,
                            Map<String, Map<Long, BigDecimal>> quantileContext, AlgoProperties.Kpi cfg) {
        if (cfg.getQuantile().isEnabled()) {
            Map<Long, BigDecimal> byEmployee = quantileContext.get(metric.getMetricKey());
            if (byEmployee != null && byEmployee.containsKey(employeeId)) {
                return KpiScorePolicy.scoreFromPercentile(byEmployee.get(employeeId), metric.getFullScore());
            }
        }
        return KpiScorePolicy.itemScore(metric.getScoreMode(), rate, metric.getFullScore(), cfg);
    }

    private void upsertScore(Employee employee, String month, BigDecimal total, BigDecimal achievementRate,
                             String level, List<KpiMetricDetailItem> items, int weightSum, LocalDateTime calculateTime) {
        KpiScore existing = findScoreRow(employee.getId(), month);
        KpiScore row = existing == null ? new KpiScore() : existing;
        row.setEmployeeId(employee.getId());
        row.setStationId(employee.getStationId());
        row.setMonth(month);
        row.setTotalScore(total);
        row.setAchievementRate(achievementRate);
        row.setLevel(level);
        row.setMetricCount(items.size());
        row.setWeightSum(weightSum);
        // 覆盖重建：整行快照替换，已停用/已删除指标不会残留（避免幽灵权重）
        row.setMetricDetail(items);
        row.setCalculateTime(calculateTime);
        if (existing == null) {
            kpiScoreMapper.insert(row);
        } else {
            kpiScoreMapper.updateById(row);
        }
    }

    // ==================== 排名 / 列表 ====================

    @Override
    @Transactional(readOnly = true)
    public KpiRankingPageVO ranking(KpiRankingQuery query) {
        KpiRankingQuery safe = query == null ? new KpiRankingQuery() : query;
        String month = parseQueryMonth(safe.getMonth());
        List<KpiScoreVO> ranked = rankedScores(month, safe.getStationId());

        int pageNum = pageNum(safe.getPageNum());
        int pageSize = pageSize(safe.getPageSize());
        KpiRankingPageVO vo = new KpiRankingPageVO();
        vo.setTotal(ranked.size());
        vo.setPageNum(pageNum);
        vo.setPageSize(pageSize);
        vo.setMonth(month);
        vo.setList(slice(ranked, pageNum, pageSize));
        vo.setCount(ranked.size());
        vo.setAvgScore(average(ranked));
        vo.setTopScore(ranked.isEmpty() ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP) : ranked.get(0).getTotalScore());
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public KpiScorePageVO list(KpiScoreQuery query) {
        KpiScoreQuery safe = query == null ? new KpiScoreQuery() : query;
        String month = parseQueryMonth(safe.getMonth());
        List<KpiScoreVO> ranked = rankedScores(month, safe.getStationId());

        List<KpiScoreVO> filtered = new ArrayList<>();
        for (KpiScoreVO row : ranked) {
            if (safe.getEmployeeId() == null || Objects.equals(row.getEmployeeId(), safe.getEmployeeId())) {
                filtered.add(row);
            }
        }
        // 列表按员工 id 升序展示（排名字段已在全量集上算好，与 Mock queryScores 一致）
        filtered.sort((a, b) -> Long.compare(a.getEmployeeId(), b.getEmployeeId()));

        int pageNum = pageNum(safe.getPageNum());
        int pageSize = pageSize(safe.getPageSize());
        KpiScorePageVO vo = new KpiScorePageVO();
        vo.setTotal(filtered.size());
        vo.setPageNum(pageNum);
        vo.setPageSize(pageSize);
        vo.setMonth(month);
        vo.setList(slice(filtered, pageNum, pageSize));
        return vo;
    }

    // ==================== 明细 ====================

    @Override
    @Transactional(readOnly = true)
    public KpiScoreDetailVO detail(Long employeeId, String month) {
        if (employeeId == null || employeeId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "employeeId 非法");
        }
        String normalizedMonth = parseQueryMonth(month);
        String role = UserContext.getRole();
        // 员工只能看本人（越权 403），与 Mock 判序一致：先本人才看是否存在
        if (RoleEnum.STAFF.name().equals(role) && !Objects.equals(UserContext.getUserId(), employeeId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看他人考核结果");
        }
        KpiScore row = findScoreRow(employeeId, normalizedMonth);
        if (row == null) {
            throw new BusinessException(ErrorCode.KPI_SCORE_NOT_EXISTS);
        }

        Map<Long, Employee> employees = loadEmployees(List.of(row.getEmployeeId()));
        // 归属驿站可能为空（历史/无归属），List.of 不接受 null，故按存在性构造
        Map<Long, Station> stations = row.getStationId() == null ? Map.of() : loadStations(List.of(row.getStationId()));
        KpiScoreDetailVO vo = toDetailVO(row, normalizedMonth, employees, stations);
        // 排名与等级取同月全站口径（员工可见自己在全员中的位置）
        // 承载类型用本类私有 RankedEntry（rank + 汇总 + 等级），而非 KpiRankingPolicy.Entry：
        // 后者是「排名输入行」（id + 总分 + 达成率，见其 javadoc 与 KpiRankingPolicyTest 的 3 参构造），
        // 由 competitionRanks 消费并另返回 int[] 名次，语义上不承载 rank/level，二者非重复定义。
        RankedEntry entry = rankedEntry(normalizedMonth, employeeId);
        if (entry != null) {
            vo.setRank(entry.rank());
            vo.setTotalScore(entry.totalScore());
            vo.setAchievementRate(entry.achievementRate());
            vo.setLevel(entry.level());
            vo.setLevelLabel(KpiConstants.levelLabel(entry.level()));
        }

        // 站长跨站（含无归属）→ 403；ADMIN 放行
        if (RoleEnum.STATION_ADMIN.name().equals(role) && !Objects.equals(row.getStationId(), currentUserStationId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看其他驿站的考核结果");
        }
        return vo;
    }

    // ==================== 私有：查询 ====================

    private List<KpiMetric> enabledMetrics() {
        LambdaQueryWrapper<KpiMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KpiMetric::getEnabled, 1)
                .orderByAsc(KpiMetric::getSortOrder).orderByAsc(KpiMetric::getId);
        return kpiMetricMapper.selectList(wrapper);
    }

    private List<Employee> activeEmployees(Long stationId, List<Long> employeeIds) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId, Employee::getRealName, Employee::getStationId, Employee::getRole)
                .eq(Employee::getStatus, 1);
        if (stationId != null) {
            wrapper.eq(Employee::getStationId, stationId);
        }
        if (employeeIds != null && !employeeIds.isEmpty()) {
            wrapper.in(Employee::getId, employeeIds);
        }
        wrapper.orderByAsc(Employee::getId);
        return employeeMapper.selectList(wrapper);
    }

    /** 评分行（轻量列，不含 JSON 明细） */
    private List<KpiScore> selectScoreRows(String month, Long stationId) {
        LambdaQueryWrapper<KpiScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(KpiScore::getId, KpiScore::getEmployeeId, KpiScore::getStationId, KpiScore::getMonth,
                        KpiScore::getTotalScore, KpiScore::getAchievementRate, KpiScore::getLevel,
                        KpiScore::getMetricCount, KpiScore::getWeightSum, KpiScore::getCalculateTime)
                .eq(KpiScore::getMonth, month);
        if (stationId != null) {
            wrapper.eq(KpiScore::getStationId, stationId);
        }
        return kpiScoreMapper.selectList(wrapper);
    }

    private KpiScore findScoreRow(Long employeeId, String month) {
        LambdaQueryWrapper<KpiScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KpiScore::getEmployeeId, employeeId).eq(KpiScore::getMonth, month);
        List<KpiScore> rows = kpiScoreMapper.selectList(wrapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 全站排名（stationId=null）→ 复用同一套聚合，避免排名/明细两处各写一套口径 */
    private List<KpiScoreVO> rankedScores(String month, Long stationId) {
        List<KpiScore> rows = selectScoreRows(month, stationId);
        List<Long> employeeIds = new ArrayList<>();
        for (KpiScore row : rows) {
            employeeIds.add(row.getEmployeeId());
        }
        Map<Long, Employee> employees = loadEmployees(employeeIds);
        List<Long> stationIds = new ArrayList<>();
        for (KpiScore row : rows) {
            stationIds.add(row.getStationId());
        }
        Map<Long, Station> stations = loadStations(stationIds);

        List<KpiScoreVO> list = new ArrayList<>(rows.size());
        for (KpiScore row : rows) {
            list.add(toScoreVO(row, employees, stations));
        }
        list.sort((a, b) -> KpiRankingPolicy.compare(a.getEmployeeId(), a.getTotalScore(), a.getAchievementRate(),
                b.getEmployeeId(), b.getTotalScore(), b.getAchievementRate()));
        assignRanks(list);
        return list;
    }

    private void assignRanks(List<KpiScoreVO> ranked) {
        List<KpiRankingPolicy.Entry> entries = new ArrayList<>(ranked.size());
        for (KpiScoreVO vo : ranked) {
            entries.add(new KpiRankingPolicy.Entry(vo.getEmployeeId(), vo.getTotalScore(), vo.getAchievementRate()));
        }
        int[] ranks = KpiRankingPolicy.competitionRanks(entries);
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).setRank(ranks[i]);
        }
    }

    /** 取某员工在同月全站的排名与汇总（明细页用） */
    private RankedEntry rankedEntry(String month, Long employeeId) {
        List<KpiScoreVO> ranked = rankedScores(month, null);
        for (KpiScoreVO vo : ranked) {
            if (Objects.equals(vo.getEmployeeId(), employeeId)) {
                return new RankedEntry(vo.getRank(), vo.getTotalScore(), vo.getAchievementRate(), vo.getLevel());
            }
        }
        return null;
    }

    // ==================== 私有：分位 ====================

    private Map<String, Map<Long, BigDecimal>> buildQuantileContext(List<KpiMetric> metrics, List<Employee> employees,
                                                                    String month, AlgoProperties.Kpi cfg) {
        if (!cfg.getQuantile().isEnabled() || employees.size() < cfg.getQuantile().getMinSamples()) {
            return Map.of();
        }
        Map<String, Map<Long, BigDecimal>> context = new HashMap<>();
        for (KpiMetric metric : metrics) {
            List<Employee> pool = new ArrayList<>();
            List<BigDecimal> rates = new ArrayList<>();
            for (Employee employee : employees) {
                if (!appliesTo(metric, employee)) {
                    continue;
                }
                BigDecimal actual = actualValueSource.actualValue(metric, employee.getId(), month);
                rates.add(KpiAchievementPolicy.achievement(actual, metric.getTargetValue(), metric.getDirection()));
                pool.add(employee);
            }
            List<BigDecimal> percentiles = KpiQuantileMapper.percentiles(rates, cfg.getQuantile().getTiePolicy());
            Map<Long, BigDecimal> byEmployee = new LinkedHashMap<>();
            for (int i = 0; i < pool.size(); i++) {
                byEmployee.put(pool.get(i).getId(), percentiles.get(i));
            }
            context.put(metric.getMetricKey(), byEmployee);
        }
        return context;
    }

    // ==================== 私有：装配 ====================

    private Map<Long, Employee> loadEmployees(List<Long> ids) {
        Map<Long, Employee> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId, Employee::getRealName, Employee::getStationId)
                .in(Employee::getId, dedup(ids));
        for (Employee employee : employeeMapper.selectList(wrapper)) {
            map.put(employee.getId(), employee);
        }
        return map;
    }

    private Map<Long, Station> loadStations(List<Long> ids) {
        Map<Long, Station> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<Long> valid = new ArrayList<>();
        for (Long id : dedup(ids)) {
            if (id != null) {
                valid.add(id);
            }
        }
        if (valid.isEmpty()) {
            return map;
        }
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Station::getId, Station::getStationName).in(Station::getId, valid);
        for (Station station : stationMapper.selectList(wrapper)) {
            map.put(station.getId(), station);
        }
        return map;
    }

    private KpiScoreVO toScoreVO(KpiScore row, Map<Long, Employee> employees, Map<Long, Station> stations) {
        Employee employee = employees.get(row.getEmployeeId());
        Station station = row.getStationId() == null ? null : stations.get(row.getStationId());
        KpiScoreVO vo = new KpiScoreVO();
        vo.setEmployeeId(row.getEmployeeId());
        vo.setEmployeeName(employee == null || employee.getRealName() == null
                ? "员工" + row.getEmployeeId() : employee.getRealName());
        vo.setStationId(row.getStationId());
        vo.setStationName(station == null ? null : station.getStationName());
        vo.setMonth(row.getMonth());
        vo.setTotalScore(row.getTotalScore());
        vo.setAchievementRate(row.getAchievementRate());
        vo.setLevel(row.getLevel());
        vo.setLevelLabel(KpiConstants.levelLabel(row.getLevel()));
        vo.setMetricCount(row.getMetricCount());
        vo.setCalculateTime(row.getCalculateTime());
        return vo;
    }

    private KpiScoreDetailVO toDetailVO(KpiScore row, String month,
                                        Map<Long, Employee> employees, Map<Long, Station> stations) {
        Employee employee = employees.get(row.getEmployeeId());
        Station station = row.getStationId() == null ? null : stations.get(row.getStationId());
        KpiScoreDetailVO vo = new KpiScoreDetailVO();
        vo.setEmployeeId(row.getEmployeeId());
        vo.setEmployeeName(employee == null || employee.getRealName() == null
                ? "员工" + row.getEmployeeId() : employee.getRealName());
        vo.setStationId(row.getStationId());
        vo.setStationName(station == null ? null : station.getStationName());
        vo.setMonth(month);
        vo.setTotalScore(row.getTotalScore());
        vo.setAchievementRate(row.getAchievementRate());
        vo.setLevel(row.getLevel());
        vo.setLevelLabel(KpiConstants.levelLabel(row.getLevel()));
        vo.setMetricCount(row.getMetricCount());
        vo.setWeightSum(row.getWeightSum());
        vo.setCalculateTime(row.getCalculateTime());

        List<KpiMetricDetailItem> detail = row.getMetricDetail() == null ? List.of() : new ArrayList<>(row.getMetricDetail());
        // 明细排序：权重倒序、指标 id 升序（对齐 Mock scoreDetail）
        detail.sort((a, b) -> {
            int byWeight = Integer.compare(nz(a.getWeight()), nz(b.getWeight())) * -1;
            if (byWeight != 0) {
                return byWeight;
            }
            return Long.compare(a.getMetricId() == null ? 0 : a.getMetricId(),
                    b.getMetricId() == null ? 0 : b.getMetricId());
        });
        List<KpiScoreItemVO> items = new ArrayList<>(detail.size());
        for (KpiMetricDetailItem item : detail) {
            items.add(toItemVO(item));
        }
        vo.setItems(items);
        return vo;
    }

    private KpiScoreItemVO toItemVO(KpiMetricDetailItem item) {
        KpiScoreItemVO vo = new KpiScoreItemVO();
        vo.setMetricId(item.getMetricId());
        vo.setMetricKey(item.getMetricKey());
        vo.setMetricName(item.getMetricName());
        vo.setMetricType(item.getMetricType());
        vo.setMetricTypeLabel(KpiConstants.metricTypeLabel(item.getMetricType()));
        vo.setWeight(item.getWeight());
        vo.setTargetValue(item.getTargetValue());
        vo.setUnit(item.getUnit());
        vo.setDirection(item.getDirection());
        vo.setActualValue(item.getActualValue());
        vo.setAchievementRate(item.getAchievementRate());
        vo.setScore(item.getScore());
        vo.setWeightedScore(item.getWeightedScore());
        vo.setScoreModeLabel(KpiConstants.scoreModeLabel(item.getScoreMode()));
        KpiScoreRuleVO rule = new KpiScoreRuleVO();
        rule.setMode(item.getScoreMode());
        rule.setFullScore(item.getFullScore());
        vo.setScoreRule(rule);
        return vo;
    }

    private KpiMetricDetailItem toDetailItem(KpiMetric metric, BigDecimal actual, BigDecimal rate, int score) {
        KpiMetricDetailItem item = new KpiMetricDetailItem();
        item.setMetricId(metric.getId());
        item.setMetricKey(metric.getMetricKey());
        item.setMetricName(metric.getMetricName());
        item.setMetricType(metric.getMetricType());
        item.setWeight(metric.getWeight());
        item.setTargetValue(metric.getTargetValue());
        item.setUnit(metric.getUnit());
        item.setDirection(metric.getDirection());
        item.setActualValue(actual);
        item.setAchievementRate(rate);
        item.setScore(score);
        item.setWeightedScore(KpiScorePolicy.weightedScore(score, nzWeight(metric)));
        item.setScoreMode(metric.getScoreMode());
        item.setFullScore(metric.getFullScore());
        return item;
    }

    // ==================== 私有：工具 ====================

    /** 指标是否适用于该员工：roleScope 为空（null/空串）表示全员适用 */
    private boolean appliesTo(KpiMetric metric, Employee employee) {
        String roleScope = metric.getRoleScope();
        if (roleScope == null || roleScope.isBlank()) {
            return true;
        }
        String role = employee.getRole();
        for (String scope : roleScope.split(",")) {
            if (scope.trim().equals(role)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal average(List<KpiScoreVO> rows) {
        if (rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (KpiScoreVO row : rows) {
            sum = sum.add(row.getTotalScore() == null ? BigDecimal.ZERO : row.getTotalScore());
        }
        return sum.divide(BigDecimal.valueOf(rows.size()), 1, RoundingMode.HALF_UP);
    }

    private List<KpiScoreVO> slice(List<KpiScoreVO> list, int pageNum, int pageSize) {
        int from = (pageNum - 1) * pageSize;
        if (from >= list.size()) {
            return new ArrayList<>();
        }
        int to = Math.min(list.size(), from + pageSize);
        return new ArrayList<>(list.subList(from, to));
    }

    private int pageNum(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private int pageSize(Integer value) {
        return value == null || value < 1 || value > 100 ? 10 : value;
    }

    private int nzWeight(KpiMetric metric) {
        return metric.getWeight() == null ? 0 : metric.getWeight();
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private List<Long> dedup(List<Long> ids) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(ids));
    }

    /** 查询类 month：空 → 当月（对齐 Mock {@code monthOf}）；非法格式 → 400 */
    private String parseQueryMonth(String month) {
        if (month == null || month.isBlank()) {
            return LocalDate.now().format(MONTH_FORMAT);
        }
        if (!MONTH_PATTERN.matcher(month).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        return month;
    }

    private Long currentUserStationId() {
        String stationId = UserContext.getStationId();
        if (stationId == null || stationId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(stationId.trim());
        } catch (NumberFormatException e) {
            log.error("会话中的 stationId 非法，按无归属处理：stationId={}", stationId);
            return null;
        }
    }

    /** 明细页排名的内部承载（截取自全站汇总） */
    private record RankedEntry(int rank, BigDecimal totalScore, BigDecimal achievementRate, String level) {
    }
}
