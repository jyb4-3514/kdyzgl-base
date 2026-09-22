import http from '../utils/http.js'

/** 看板总览（一期 4.2）：老板端首页的多路聚合取数由页面自行编排 */
export const getDashboardSummary = () => http.get('/dashboard/summary')
