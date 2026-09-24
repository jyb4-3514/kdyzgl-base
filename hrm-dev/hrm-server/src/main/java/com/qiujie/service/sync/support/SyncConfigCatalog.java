package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigItem;
import com.qiujie.entity.SyncConfigOption;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置中心只读快照视图（导入 / 导出 / 校验共用的数据面）。
 * <p>
 * 为什么抽接口而不直接依赖 Mapper：CSV 导入导出与四层合并都是「纯逻辑」，把它们绑死在 MyBatis 上就无法单测；
 * 本接口是纯逻辑与持久层之间的唯一缝，生产实现为 {@code SyncConfigCatalogSnapshot}（由 Service 从库中装配），
 * 单测用同一实现手工装填即可。
 */
public interface SyncConfigCatalog {

    /** 全部启用态的配置项（按 sort 升序） */
    List<SyncConfigItem> items();

    /** 按 Key 取配置项（不存在返回 null） */
    SyncConfigItem findItem(String itemKey);

    /** 全部选项集（含集合层元数据与选项） */
    List<SyncOptionSet> optionSets();

    /** 按 setKey 取选项集（不存在返回 null） */
    SyncOptionSet findOptionSet(String setKey);

    /**
     * 某配置项关联选项集的<b>可选项</b>：选项集停用或不存在时返回空列表（编码「暂无可选项」语义）。
     */
    List<SyncConfigOption> optionsOfSet(String setKey);

    /** 全局默认值（类型化，按 itemKey 索引；含无库行时回落到配置项默认值） */
    Map<String, Object> globalValues();

    /** 某驿站的覆盖键集合 */
    Set<String> overrideKeys(Long stationId);

    /** 某驿站的覆盖值（类型化，按 itemKey 索引） */
    Map<String, Object> overridesOf(Long stationId);

    /** 按驿站名称解析 id（不存在返回 null） */
    Long stationIdByName(String stationName);

    /** 按 id 取驿站名称（不存在返回 null；导出展示用） */
    String stationName(Long stationId);

    /** 全部驿站 id（升序，导出 ALL 范围遍历用） */
    List<Long> stationIds();
}
