package com.qiujie.service.sync.support;

import com.qiujie.entity.SyncConfigOption;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 选项集视图（集合层元数据 + 选项列表），非独立表。
 * <p>
 * 选项集本身无实体表（见 {@code SyncConstants#OPTION_SET_META} 说明），本视图由 Service 组装，
 * 供 CSV 导入导出与配置项校验统一消费。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncOptionSet {

    private String setKey;
    private String name;
    private String description;
    private boolean builtin;
    private boolean enabled;
    private List<SyncConfigOption> options = new ArrayList<>();
}
