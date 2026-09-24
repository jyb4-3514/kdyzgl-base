package com.qiujie.service.sync;

import com.qiujie.dto.sync.SyncConfigGlobalRequest;
import com.qiujie.dto.sync.SyncConfigImportRequest;
import com.qiujie.dto.sync.SyncConfigItemRequest;
import com.qiujie.dto.sync.SyncConfigOptionRequest;
import com.qiujie.vo.sync.SyncConfigItemListVO;
import com.qiujie.vo.sync.SyncConfigItemVO;
import com.qiujie.vo.sync.SyncConfigOptionVO;
import com.qiujie.vo.sync.SyncGlobalConfigVO;
import com.qiujie.vo.sync.SyncImpactVO;
import com.qiujie.vo.sync.SyncImportResultVO;

/**
 * 配置中心服务（M9，13 接口，仅 ADMIN，Mock {@code routes/syncConfigCenter.js} + {@code syncConfigStore.js}）。
 * <p>
 * 四层模型：配置项定义 / 选项集（元数据层）→ 全局默认 → 驿站覆盖；
 * 删除前须先查影响面（内置不可删 9510；被引用需 confirm 9503/9505）。
 */
public interface SyncConfigCenterService {

    /** 配置项定义 + 选项集（一次取全） */
    SyncConfigItemListVO listItems();

    /** 新增配置项（Key 重复 → 9502） */
    SyncConfigItemVO createItem(SyncConfigItemRequest request);

    /** 编辑配置项（不存在 → 9501；值类型锁定） */
    SyncConfigItemVO updateItem(String itemKey, SyncConfigItemRequest request);

    /** 删除配置项（内置 → 9510；被覆盖且未确认 → 9503） */
    void deleteItem(String itemKey, boolean confirm);

    /** 配置项删除影响面 */
    SyncImpactVO itemImpact(String itemKey);

    /** 新增选项（配置项非单选型 → 400；选项集不存在 → 9501） */
    SyncConfigOptionVO createOption(String itemKey, SyncConfigOptionRequest request);

    /** 编辑选项（不存在 → 9504） */
    SyncConfigOptionVO updateOption(String itemKey, String optionKey, SyncConfigOptionRequest request);

    /** 删除选项（内置 → 9510；被全局默认引用 → 9505；被覆盖且未确认 → 9505） */
    void deleteOption(String itemKey, String optionKey, boolean confirm);

    /** 选项删除影响面 */
    SyncImpactVO optionImpact(String itemKey, String optionKey);

    /** 全局默认值（含无库行时的默认回落） */
    SyncGlobalConfigVO globalValues();

    /** 保存全局默认（局部更新，逐项校验 9506/9507） */
    SyncGlobalConfigVO saveGlobalValues(SyncConfigGlobalRequest request);

    /** CSV 导出（scope=ITEMS / ITEMS_GLOBAL / ALL / TEMPLATE） */
    SyncExportFile export(String scope);

    /** CSV 导入（dryRun 预览 / 落库） */
    SyncImportResultVO importConfig(SyncConfigImportRequest request);
}
