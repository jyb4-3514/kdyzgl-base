package com.qiujie.service.support.geo;

/**
 * 逆地理编码结果（仅用于展示 / 审计，<b>不参与围栏判定</b>）。
 * <p>
 * 为什么围栏判定不依赖本结果：围栏用两点球面距离比较半径（客户端坐标不可信，定位属辅助信号），
 * 逆编码结果会因第三方数据变动而漂移，若用于判定会导致「同一次打卡结果不可复现」。故坐标→地址只作展示。
 *
 * @param longitude        经度
 * @param latitude         纬度
 * @param formattedAddress 结构化地址（可能为空）
 * @param province         省
 * @param city             市
 * @param district         区/县
 */
public record GeoAddress(Double longitude, Double latitude, String formattedAddress,
                         String province, String city, String district) {
}
