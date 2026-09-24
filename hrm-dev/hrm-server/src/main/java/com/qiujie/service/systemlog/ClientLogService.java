package com.qiujie.service.systemlog;

import com.qiujie.dto.systemlog.ClientLogIngestRequest;
import com.qiujie.dto.systemlog.ClientLogQuery;
import com.qiujie.vo.systemlog.ClientLogClearVO;
import com.qiujie.vo.systemlog.ClientLogIngestVO;
import com.qiujie.vo.systemlog.ClientLogPageVO;

/**
 * 运行日志服务（M1 systemlog，3 接口，S8 指纹聚合）。
 */
public interface ClientLogService {

    /** 批量上报（白名单脱敏入库 + 指纹去重聚合），返回接收条数 */
    ClientLogIngestVO ingest(ClientLogIngestRequest request);

    /** 分页查询 + counts（仅 ADMIN） */
    ClientLogPageVO page(ClientLogQuery query);

    /** 清空（物理删除，仅 ADMIN），返回清空条数 */
    ClientLogClearVO clear();
}
