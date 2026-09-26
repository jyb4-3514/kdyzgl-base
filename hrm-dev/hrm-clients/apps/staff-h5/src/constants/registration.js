/**
 * 注册页静态配置（registration-design.md §11.4 U-04 定稿）
 *
 * 意向驿站选项为什么静态：一期无公开驿站端点（GET /stations 仅 ADMIN），且公开白名单仅
 * 发码（R-1）与提交（R-2）两条，不新增公开端点；故选项由前端构建期静态配置提供，仅用于填单（非事实），
 * 服务端 R-2 再审 intentStationId 存在 + 启用（4001/4004）兜底静态配置与 station 表的漂移。
 * 列表仅含启用驿站（停用站不在候选内）。
 *
 * TODO(扩展): 意向驿站选项由构建期静态配置切 API —— 届时启用 StationPicker 的 loading/error 态
 */
export const REGISTRATION_STATIONS = [
  { id: 1, stationName: '城东驿站' },
  { id: 2, stationName: '城西驿站' },
  { id: 3, stationName: '城南驿站' },
  { id: 4, stationName: '城北驿站' },
  { id: 5, stationName: '高新驿站' },
  { id: 6, stationName: '大学城驿站' },
  { id: 7, stationName: '老城驿站' }
]

/** 服务条款版本（合规留痕，契约 @Size(max=20)）；条款文本变更时同步升版 */
export const REGISTRATION_AGREEMENT_VERSION = 'v1.0'
