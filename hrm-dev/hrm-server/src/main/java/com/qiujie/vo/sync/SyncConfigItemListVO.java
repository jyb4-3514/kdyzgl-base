package com.qiujie.vo.sync;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置项列表出参（Mock {@code syncConfigCenter.js#listConfigItems}：{ items, optionSets }，一次取全）。
 */
@Data
public class SyncConfigItemListVO {

    private List<SyncConfigItemVO> items = new ArrayList<>();
    private List<SyncConfigOptionSetVO> optionSets = new ArrayList<>();
}
