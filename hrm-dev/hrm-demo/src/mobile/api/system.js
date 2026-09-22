import http from '../utils/http.js'

/** 运行日志上报（D6，不限角色）：失败丢弃该批，不重试、不弹提示 */
export const reportClientLogs = (logs) => http.post('/system/client-logs', { logs }, { silent: true })
