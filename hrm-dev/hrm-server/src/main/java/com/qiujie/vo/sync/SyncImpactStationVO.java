package com.qiujie.vo.sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 影响面-受影响驿站（Mock {@code syncConfigStore.js#refStationsOf}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncImpactStationVO {

    private Long stationId;
    private String stationName;
}
