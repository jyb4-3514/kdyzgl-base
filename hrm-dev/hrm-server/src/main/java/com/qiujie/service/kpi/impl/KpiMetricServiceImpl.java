package com.qiujie.service.kpi.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.kpi.KpiMetricBatchRequest;
import com.qiujie.dto.kpi.KpiMetricRequest;
import com.qiujie.entity.KpiMetric;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.KpiMetricMapper;
import com.qiujie.service.kpi.KpiMetricService;
import com.qiujie.service.kpi.support.KpiConstants;
import com.qiujie.service.kpi.support.KpiWeightGuard;
import com.qiujie.vo.kpi.KpiMetricBatchResultVO;
import com.qiujie.vo.kpi.KpiMetricListVO;
import com.qiujie.vo.kpi.KpiMetricVO;
import com.qiujie.vo.kpi.KpiScoreRuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * KPI 指标配置服务实现（对齐 Mock {@code kpiStore} 指标段 + {@code routes/kpi.js} 校验）。
 * <p>
 * 校验文案与 Mock 逐字一致（便于前端按文案对齐）；权重守卫命中 → 9202（文案含当前合计）。
 * 编辑仅更新「显式传入」字段（Java 侧 null 视作未传，与 Mock {@code pickWritable} 的 undefined 语义对应；
 * 差异：Mock 可用显式 null 清空 roleScope/remark，本实现无法区分「未传」与「显式 null」，见返回摘要遗留项）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiMetricServiceImpl implements KpiMetricService {

    /** 指标键：以大写字母开头，仅大写字母/数字/下划线 */
    private static final Pattern METRIC_KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private final KpiMetricMapper kpiMetricMapper;
    private final AlgoProperties algoProperties;

    // ==================== 查询 ====================

    @Override
    @Transactional(readOnly = true)
    public KpiMetricListVO list() {
        List<KpiMetric> metrics = selectAllOrdered();
        KpiMetricListVO vo = new KpiMetricListVO();
        List<KpiMetricVO> list = new ArrayList<>(metrics.size());
        for (KpiMetric metric : metrics) {
            list.add(toVO(metric));
        }
        vo.setList(list);
        vo.setWeightSum(weightSum(metrics));
        return vo;
    }

    // ==================== 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KpiMetricVO create(KpiMetricRequest request) {
        KpiMetricRequest safe = request == null ? new KpiMetricRequest() : request;
        String error = validate(safe, true);
        if (error != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error);
        }
        if (metricKeyExists(safe.getMetricKey(), null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "指标标识已存在");
        }

        List<KpiMetric> existing = selectAllOrdered();
        KpiMetric metric = new KpiMetric();
        metric.setEnabled(1);
        metric.setSortOrder(existing.size() + 1);
        metric.setRoleScope(null);
        metric.setRemark(null);
        applyPayload(metric, safe);

        List<KpiMetric> candidate = new ArrayList<>(existing);
        candidate.add(metric);
        guardWeight(candidate);

        kpiMetricMapper.insert(metric);
        return toVO(metric);
    }

    // ==================== 编辑 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KpiMetricVO update(Long id, KpiMetricRequest request) {
        KpiMetricRequest safe = request == null ? new KpiMetricRequest() : request;
        String error = validate(safe, false);
        if (error != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error);
        }
        KpiMetric metric = findMetric(id);
        if (safe.getMetricKey() != null && metricKeyExists(safe.getMetricKey(), metric.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "指标标识已存在");
        }

        // 先在副本上套用变更、整体校验权重，通过后再落到实体（避免校验失败污染实体）
        KpiMetric candidate = new KpiMetric();
        BeanUtils.copyProperties(metric, candidate);
        applyPayload(candidate, safe);
        guardWeight(replaceInList(selectAllOrdered(), metric.getId(), candidate));

        applyPayload(metric, safe);
        metric.setUpdateTime(LocalDateTime.now());
        kpiMetricMapper.updateById(metric);
        return toVO(metric);
    }

    // ==================== 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        KpiMetric metric = findMetric(id);
        List<KpiMetric> remaining = new ArrayList<>();
        for (KpiMetric item : selectAllOrdered()) {
            if (!Objects.equals(item.getId(), metric.getId())) {
                remaining.add(item);
            }
        }
        guardWeight(remaining);
        kpiMetricMapper.deleteById(metric.getId());
    }

    // ==================== 批量保存（原子） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KpiMetricBatchResultVO saveBatch(KpiMetricBatchRequest request) {
        List<KpiMetricBatchRequest.Item> items = request == null ? null : request.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "items 须为非空数组");
        }
        List<KpiMetric> all = selectAllOrdered();
        // id → 变更（同 id 重复项后者覆盖，与 Mock Map 语义一致）
        Map<Long, int[]> patch = new LinkedHashMap<>();

        for (KpiMetricBatchRequest.Item item : items) {
            if (item == null || item.getId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "items 每项 id 不能为空");
            }
            KpiMetric metric = findInList(all, item.getId());
            if (metric == null) {
                throw new BusinessException(ErrorCode.KPI_METRIC_NOT_EXISTS, "指标不存在：" + item.getId());
            }
            int newWeight = metric.getWeight() == null ? 0 : metric.getWeight();
            int newEnabled = metric.getEnabled() == null ? 0 : metric.getEnabled();
            if (item.getWeight() != null) {
                int weight = item.getWeight();
                if (weight < 0 || weight > 100) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "权重须为 0-100 的整数");
                }
                newWeight = weight;
            }
            if (item.getEnabled() != null) {
                Integer enabled = parseEnabled(item.getEnabled());
                if (enabled == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "enabled 仅支持 0 / 1");
                }
                newEnabled = enabled;
            }
            patch.put(metric.getId(), new int[]{newWeight, newEnabled});
        }

        // 一次整体校验（原子：任一失败则全部不落地）
        List<KpiMetric> candidate = new ArrayList<>(all.size());
        for (KpiMetric metric : all) {
            int[] applied = patch.get(metric.getId());
            if (applied == null) {
                candidate.add(metric);
                continue;
            }
            KpiMetric copy = new KpiMetric();
            BeanUtils.copyProperties(metric, copy);
            copy.setWeight(applied[0]);
            copy.setEnabled(applied[1]);
            candidate.add(copy);
        }
        guardWeight(candidate);

        LocalDateTime now = LocalDateTime.now();
        for (KpiMetric metric : all) {
            if (!patch.containsKey(metric.getId())) {
                continue;
            }
            KpiMetric update = new KpiMetric();
            update.setId(metric.getId());
            update.setWeight(patch.get(metric.getId())[0]);
            update.setEnabled(patch.get(metric.getId())[1]);
            update.setUpdateTime(now);
            kpiMetricMapper.updateById(update);
        }

        KpiMetricBatchResultVO vo = new KpiMetricBatchResultVO();
        vo.setUpdated(patch.size());
        vo.setWeightSum(weightSum(candidate));
        return vo;
    }

    // ==================== 私有：校验 ====================

    /** 指标入参校验，返回错误文案（null = 通过）。create=true 时必填项全查；编辑时只查显式传入项。 */
    private String validate(KpiMetricRequest body, boolean create) {
        if (body.getMetricKey() != null || create) {
            if (!textLen(body.getMetricKey(), 2, 50)) {
                return "指标标识长度须为 2-50";
            }
            if (!METRIC_KEY_PATTERN.matcher(body.getMetricKey()).matches()) {
                return "指标标识须为大写字母、数字与下划线";
            }
        }
        if (body.getMetricName() != null || create) {
            if (!textLen(body.getMetricName(), 1, 50)) {
                return "指标名称长度须为 1-50";
            }
        }
        if (body.getMetricType() != null || create) {
            if (!KpiConstants.METRIC_TYPES.contains(body.getMetricType())) {
                return "指标类型仅支持 " + String.join(" / ", KpiConstants.METRIC_TYPES);
            }
        }
        if (body.getWeight() != null || create) {
            Integer weight = body.getWeight();
            if (weight == null || weight < 0 || weight > 100) {
                return "权重须为 0-100 的整数";
            }
        }
        if (body.getTargetValue() != null || create) {
            BigDecimal target = body.getTargetValue();
            if (target == null || target.compareTo(BigDecimal.ZERO) < 0) {
                return "目标值须为不小于 0 的数字";
            }
        }
        if (body.getDirection() != null || create) {
            if (!KpiConstants.DIRECTIONS.contains(body.getDirection())) {
                return "direction 仅支持 " + String.join(" / ", KpiConstants.DIRECTIONS);
            }
        }
        if (body.getScoreRule() != null || create) {
            KpiMetricRequest.ScoreRule rule = body.getScoreRule();
            if (rule == null || !KpiConstants.SCORE_MODES.contains(rule.getMode())) {
                return "scoreRule.mode 仅支持 " + String.join(" / ", KpiConstants.SCORE_MODES);
            }
            BigDecimal full = rule.getFullScore();
            if (full == null || full.compareTo(BigDecimal.ZERO) <= 0
                    || full.compareTo(BigDecimal.valueOf(100)) > 0) {
                return "scoreRule.fullScore 须为 0-100 的数字";
            }
        }
        if (body.getRoleScope() != null) {
            // 空数组合法（Mock：some 对空数组恒 false），语义等同「全员适用」
            for (String role : body.getRoleScope()) {
                if (!KpiConstants.ROLE_SCOPES.contains(role)) {
                    return "roleScope 须为角色数组";
                }
            }
        }
        if (body.getEnabled() != null && parseEnabled(body.getEnabled()) == null) {
            return "enabled 仅支持 0 / 1";
        }
        // sortOrder 声明为 Integer，天然为整数；Mock 仅要求「整数」（允许负值），故此处不再附加范围限制
        return null;
    }

    /** enabled 归一：接受 0/1 与 true/false；非法返回 null */
    private Integer parseEnabled(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (raw instanceof Number number) {
            int value = number.intValue();
            return (value == 0 || value == 1) ? value : null;
        }
        return null;
    }

    // ==================== 私有：载荷与守卫 ====================

    /** 仅套用「显式传入」字段（null = 不改），语义对齐 Mock {@code pickWritable} */
    private void applyPayload(KpiMetric metric, KpiMetricRequest body) {
        if (body.getMetricKey() != null) {
            metric.setMetricKey(body.getMetricKey().trim());
        }
        if (body.getMetricName() != null) {
            metric.setMetricName(body.getMetricName().trim());
        }
        if (body.getMetricType() != null) {
            metric.setMetricType(body.getMetricType());
        }
        if (body.getWeight() != null) {
            metric.setWeight(body.getWeight());
        }
        if (body.getTargetValue() != null) {
            metric.setTargetValue(body.getTargetValue());
        }
        if (body.getUnit() != null) {
            metric.setUnit(body.getUnit().trim());
        }
        if (body.getDirection() != null) {
            metric.setDirection(body.getDirection());
        }
        if (body.getScoreRule() != null) {
            metric.setScoreMode(body.getScoreRule().getMode());
            metric.setFullScore(body.getScoreRule().getFullScore());
        }
        if (body.getRoleScope() != null) {
            metric.setRoleScope(roleScopeToCsv(body.getRoleScope()));
        }
        if (body.getEnabled() != null) {
            metric.setEnabled(parseEnabled(body.getEnabled()));
        }
        if (body.getSortOrder() != null) {
            metric.setSortOrder(body.getSortOrder());
        }
        if (body.getRemark() != null) {
            metric.setRemark(body.getRemark().trim());
        }
    }

    /** 权重守卫：启用指标合计须 = 目标（容差可配），否则 9202 */
    private void guardWeight(List<KpiMetric> metrics) {
        List<Integer> enabledWeights = new ArrayList<>();
        for (KpiMetric metric : metrics) {
            if (metric.getEnabled() != null && metric.getEnabled() == 1) {
                enabledWeights.add(metric.getWeight() == null ? 0 : metric.getWeight());
            }
        }
        AlgoProperties.Kpi cfg = algoProperties.getKpi();
        KpiWeightGuard.Result result = KpiWeightGuard.check(
                enabledWeights, cfg.getWeightSumTarget(), cfg.getWeightSumTolerance());
        if (!result.pass()) {
            throw new BusinessException(ErrorCode.KPI_WEIGHT_SUM_INVALID, result.message());
        }
    }

    // ==================== 私有：查询与转换 ====================

    private List<KpiMetric> selectAllOrdered() {
        LambdaQueryWrapper<KpiMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(KpiMetric::getSortOrder).orderByAsc(KpiMetric::getId);
        return kpiMetricMapper.selectList(wrapper);
    }

    private KpiMetric findMetric(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.KPI_METRIC_NOT_EXISTS);
        }
        KpiMetric metric = kpiMetricMapper.selectById(id);
        if (metric == null) {
            throw new BusinessException(ErrorCode.KPI_METRIC_NOT_EXISTS);
        }
        return metric;
    }

    private KpiMetric findInList(List<KpiMetric> metrics, Long id) {
        for (KpiMetric metric : metrics) {
            if (Objects.equals(metric.getId(), id)) {
                return metric;
            }
        }
        return null;
    }

    /** 用替换项生成新列表（不改原列表） */
    private List<KpiMetric> replaceInList(List<KpiMetric> metrics, Long id, KpiMetric replacement) {
        List<KpiMetric> result = new ArrayList<>(metrics.size());
        for (KpiMetric metric : metrics) {
            result.add(Objects.equals(metric.getId(), id) ? replacement : metric);
        }
        return result;
    }

    /** 指标键活跃唯一（excludeId 为编辑时的自身 id） */
    private boolean metricKeyExists(String metricKey, Long excludeId) {
        LambdaQueryWrapper<KpiMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KpiMetric::getMetricKey, metricKey == null ? null : metricKey.trim());
        wrapper.ne(excludeId != null, KpiMetric::getId, excludeId);
        return kpiMetricMapper.selectCount(wrapper) > 0;
    }

    private Integer weightSum(List<KpiMetric> metrics) {
        int sum = 0;
        for (KpiMetric metric : metrics) {
            if (metric.getEnabled() != null && metric.getEnabled() == 1) {
                sum += metric.getWeight() == null ? 0 : metric.getWeight();
            }
        }
        return sum;
    }

    private KpiMetricVO toVO(KpiMetric metric) {
        KpiMetricVO vo = new KpiMetricVO();
        vo.setId(metric.getId());
        vo.setMetricKey(metric.getMetricKey());
        vo.setMetricName(metric.getMetricName());
        vo.setMetricType(metric.getMetricType());
        vo.setMetricTypeLabel(KpiConstants.metricTypeLabel(metric.getMetricType()));
        vo.setWeight(metric.getWeight());
        vo.setTargetValue(metric.getTargetValue());
        vo.setUnit(metric.getUnit());
        vo.setDirection(metric.getDirection());
        vo.setDirectionLabel(KpiConstants.directionLabel(metric.getDirection()));
        vo.setScoreModeLabel(KpiConstants.scoreModeLabel(metric.getScoreMode()));
        KpiScoreRuleVO rule = new KpiScoreRuleVO();
        rule.setMode(metric.getScoreMode());
        rule.setFullScore(metric.getFullScore());
        vo.setScoreRule(rule);
        vo.setRoleScope(roleScopeFromCsv(metric.getRoleScope()));
        vo.setEnabled(metric.getEnabled());
        vo.setSortOrder(metric.getSortOrder());
        vo.setRemark(metric.getRemark());
        vo.setCreateTime(metric.getCreateTime());
        vo.setUpdateTime(metric.getUpdateTime());
        return vo;
    }

    /** 角色数组 → 逗号串（空数组 = 全员适用 = null） */
    private String roleScopeToCsv(List<String> roleScope) {
        if (roleScope == null || roleScope.isEmpty()) {
            return null;
        }
        return String.join(",", roleScope);
    }

    /** 逗号串 → 角色数组（空 = null，表示全员适用） */
    private List<String> roleScopeFromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        return new ArrayList<>(Arrays.asList(csv.split(",")));
    }

    private boolean textLen(String value, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        return length >= min && length <= max;
    }
}
