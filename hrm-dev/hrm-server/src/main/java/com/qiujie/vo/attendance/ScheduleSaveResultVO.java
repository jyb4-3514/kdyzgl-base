package com.qiujie.vo.attendance;

import lombok.Data;

/**
 * 手动批量排班结果（对齐 Mock {@code saveSchedules} 返回 {saved, removed}）。
 */
@Data
public class ScheduleSaveResultVO {

    /** 新增或改派条数 */
    private int saved;

    /** 清空（删除）条数 */
    private int removed;
}
