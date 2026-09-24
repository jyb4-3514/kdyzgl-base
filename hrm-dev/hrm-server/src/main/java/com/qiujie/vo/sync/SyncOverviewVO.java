package com.qiujie.vo.sync;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 采集状态总览出参（Mock {@code routes/syncConfig.js#overview}：{ total, counts, stations }）。
 */
@Data
public class SyncOverviewVO {

    private int total;
    private SyncOverviewCountsVO counts = new SyncOverviewCountsVO();
    private List<SyncStationStateVO> stations = new ArrayList<>();
}
