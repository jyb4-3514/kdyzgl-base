package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiujie.dto.finance.PayrollRuleItemRequest;
import com.qiujie.dto.finance.PayrollRuleRequest;
import com.qiujie.entity.Payroll;
import com.qiujie.entity.PayrollRule;
import com.qiujie.entity.PayrollRuleItem;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.PayrollMapper;
import com.qiujie.mapper.PayrollRuleItemMapper;
import com.qiujie.mapper.PayrollRuleMapper;
import com.qiujie.service.finance.PayrollRuleService;
import com.qiujie.service.finance.support.PayrollItemType;
import com.qiujie.service.finance.support.PayrollRuleValidator;
import com.qiujie.service.finance.support.PayrollSource;
import com.qiujie.vo.finance.PayrollRuleItemVO;
import com.qiujie.vo.finance.PayrollRuleListVO;
import com.qiujie.vo.finance.PayrollRuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计薪规则服务实现（对齐 Mock {@code financeStore} 规则段 + {@code routes/finance.js}）。
 * <p>
 * 编辑时 {@code items} 传入即整体覆盖（旧项逻辑删除 + 新项插入），与 Mock {@code pickRuleItems} 一致；
 * 规则被工资单引用时不可删（9403），因历史单据的 {@code rule_snapshot} 已留存但仍需保留规则可查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollRuleServiceImpl implements PayrollRuleService {

    private final PayrollRuleMapper payrollRuleMapper;
    private final PayrollRuleItemMapper payrollRuleItemMapper;
    private final PayrollMapper payrollMapper;

    // ==================== 查询 ====================

    @Override
    @Transactional(readOnly = true)
    public PayrollRuleListVO listRules() {
        List<PayrollRule> rules = payrollRuleMapper.selectList(new LambdaQueryWrapper<PayrollRule>()
                .orderByDesc(PayrollRule::getStatus).orderByAsc(PayrollRule::getId));
        if (rules.isEmpty()) {
            return new PayrollRuleListVO(List.of());
        }
        Map<Long, List<PayrollRuleItem>> itemsByRule = loadItemsByRule(ids(rules));
        List<PayrollRuleVO> list = new ArrayList<>(rules.size());
        for (PayrollRule rule : rules) {
            list.add(toRuleVO(rule, itemsByRule.getOrDefault(rule.getId(), List.of())));
        }
        return new PayrollRuleListVO(list);
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollRuleVO ruleDetail(Long id) {
        PayrollRule rule = payrollRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.FINANCE_RULE_NOT_EXISTS);
        }
        return toRuleVO(rule, loadItems(id));
    }

    // ==================== 新建 / 编辑 / 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollRuleVO createRule(PayrollRuleRequest request) {
        String error = PayrollRuleValidator.validate(request, true);
        if (error != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error);
        }
        PayrollRule rule = new PayrollRule();
        rule.setRuleName(request.getRuleName().trim());
        rule.setRemark(request.getRemark() == null ? null : request.getRemark().trim());
        rule.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        payrollRuleMapper.insert(rule);

        List<PayrollRuleItem> items = toEntities(rule.getId(), request.getItems());
        for (PayrollRuleItem item : items) {
            payrollRuleItemMapper.insert(item);
        }
        return toRuleVO(rule, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayrollRuleVO updateRule(Long id, PayrollRuleRequest request) {
        PayrollRule rule = payrollRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.FINANCE_RULE_NOT_EXISTS);
        }
        String error = PayrollRuleValidator.validate(request, false);
        if (error != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error);
        }

        LambdaUpdateWrapper<PayrollRule> wrapper = new LambdaUpdateWrapper<PayrollRule>()
                .eq(PayrollRule::getId, id)
                .set(PayrollRule::getUpdateTime, LocalDateTime.now());
        if (request.getRuleName() != null) {
            wrapper.set(PayrollRule::getRuleName, request.getRuleName().trim());
        }
        if (request.getStatus() != null) {
            wrapper.set(PayrollRule::getStatus, request.getStatus());
        }
        // remark：显式 null 视为清空（对齐 Mock body.remark === null ? null : trim）
        if (request.getRemark() != null) {
            wrapper.set(PayrollRule::getRemark, request.getRemark().isBlank() ? null : request.getRemark().trim());
        }
        payrollRuleMapper.update(null, wrapper);

        List<PayrollRuleItem> items;
        if (request.getItems() != null) {
            // 整体覆盖：旧项逻辑删除后重建（Mock pickRuleItems 全量替换语义）
            payrollRuleItemMapper.delete(new LambdaQueryWrapper<PayrollRuleItem>().eq(PayrollRuleItem::getRuleId, id));
            items = toEntities(id, request.getItems());
            for (PayrollRuleItem item : items) {
                payrollRuleItemMapper.insert(item);
            }
        } else {
            items = loadItems(id);
        }
        return toRuleVO(payrollRuleMapper.selectById(id), items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        PayrollRule rule = payrollRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.FINANCE_RULE_NOT_EXISTS);
        }
        Long referenced = payrollMapper.selectCount(new LambdaQueryWrapper<Payroll>().eq(Payroll::getRuleId, id));
        if (referenced != null && referenced > 0) {
            throw new BusinessException(ErrorCode.FINANCE_PAYROLL_STATUS_INVALID,
                    "该规则已被工资单引用，不能删除，可改为停用");
        }
        payrollRuleItemMapper.delete(new LambdaQueryWrapper<PayrollRuleItem>().eq(PayrollRuleItem::getRuleId, id));
        payrollRuleMapper.deleteById(id);
    }

    // ==================== 私有：装配 ====================

    private List<PayrollRuleItem> loadItems(Long ruleId) {
        return payrollRuleItemMapper.selectList(new LambdaQueryWrapper<PayrollRuleItem>()
                .eq(PayrollRuleItem::getRuleId, ruleId)
                .orderByAsc(PayrollRuleItem::getSortOrder).orderByAsc(PayrollRuleItem::getId));
    }

    private Map<Long, List<PayrollRuleItem>> loadItemsByRule(List<Long> ruleIds) {
        List<PayrollRuleItem> items = payrollRuleItemMapper.selectList(new LambdaQueryWrapper<PayrollRuleItem>()
                .in(PayrollRuleItem::getRuleId, ruleIds)
                .orderByAsc(PayrollRuleItem::getSortOrder).orderByAsc(PayrollRuleItem::getId));
        Map<Long, List<PayrollRuleItem>> grouped = new LinkedHashMap<>();
        for (PayrollRuleItem item : items) {
            grouped.computeIfAbsent(item.getRuleId(), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private List<Long> ids(List<PayrollRule> rules) {
        List<Long> ids = new ArrayList<>(rules.size());
        for (PayrollRule rule : rules) {
            ids.add(rule.getId());
        }
        return ids;
    }

    /** 入参 → 实体（对齐 Mock {@code pickRuleItems}：trim、enabled 缺省 1、sortOrder 缺省下标+1、params 缺省 {}） */
    private List<PayrollRuleItem> toEntities(Long ruleId, List<PayrollRuleItemRequest> requests) {
        List<PayrollRuleItem> items = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            PayrollRuleItemRequest request = requests.get(index);
            PayrollRuleItem item = new PayrollRuleItem();
            item.setRuleId(ruleId);
            item.setItemKey(request.getKey().trim());
            item.setItemName(request.getName().trim());
            item.setItemType(request.getType());
            item.setSource(request.getSource());
            item.setParams(request.getParams() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getParams()));
            item.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
            item.setSortOrder(request.getSortOrder() == null ? index + 1 : request.getSortOrder());
            items.add(item);
        }
        return items;
    }

    private PayrollRuleVO toRuleVO(PayrollRule rule, List<PayrollRuleItem> items) {
        PayrollRuleVO vo = new PayrollRuleVO();
        vo.setId(rule.getId());
        vo.setRuleName(rule.getRuleName());
        vo.setRemark(rule.getRemark());
        vo.setStatus(rule.getStatus());
        vo.setStatusLabel(rule.getStatus() != null && rule.getStatus() == 1 ? "启用" : "停用");
        int enabledCount = 0;
        List<PayrollRuleItemVO> itemVOs = new ArrayList<>(items.size());
        for (PayrollRuleItem item : items) {
            if (item.getEnabled() != null && item.getEnabled() == 1) {
                enabledCount++;
            }
            itemVOs.add(toItemVO(item));
        }
        vo.setItemCount(itemVOs.size());
        vo.setEnabledItemCount(enabledCount);
        vo.setItems(itemVOs);
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }

    private PayrollRuleItemVO toItemVO(PayrollRuleItem item) {
        PayrollRuleItemVO vo = new PayrollRuleItemVO();
        vo.setId(item.getId());
        vo.setKey(item.getItemKey());
        vo.setName(item.getItemName());
        vo.setType(item.getItemType());
        vo.setTypeLabel(PayrollItemType.labelOf(item.getItemType()));
        vo.setSource(item.getSource());
        vo.setSourceLabel(PayrollSource.labelOf(item.getSource()));
        vo.setParams(item.getParams() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item.getParams()));
        vo.setEnabled(item.getEnabled());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }
}
