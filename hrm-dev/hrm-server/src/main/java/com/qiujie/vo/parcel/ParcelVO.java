package com.qiujie.vo.parcel;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 包裹出参（对齐 Mock {@code parcelStore.js#hydrate}，字段集与语义逐位一致）。
 * <p>
 * {@code receiverName / receiverPhone} 已由 Service 用 {@code DesensitizeUtil} 脱敏（C-07）。
 * {@code null} 字段（如未取件的 {@code pickupTime}）须**照常输出**（Mock 返回 null），故显式 ALWAYS。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ParcelVO {

    private Long id;

    private Long stationId;

    private String stationName;

    private String waybillNo;

    /** 状态：0=待入库 1=在库待取 2=已取件 3=异常 4=已退回 */
    private Integer status;

    /** 收件人姓名（脱敏：保留首字） */
    private String receiverName;

    /** 收件人手机号（脱敏：前 3 后 4） */
    private String receiverPhone;

    private String shelfCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime inboundTime;

    private Long pickupEmployeeId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime pickupTime;

    private String syncBatchNo;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
