/**
 * SLA 临近阈值比例（2.7 三端统一）
 *
 * 阈值 = 该优先级 SLA 总时长 × 本比例（低 48h / 中 24h / 高 8h → 12h / 6h / 2h）。
 * PC 的 SlaCountdown 与移动端 SlaTag 原本各写一份 0.25，一旦分叉「临近」态就会两端不一致，故收口到这里。
 * 放 shared/domain 而非某一端：两端都要用，且 PC 不应反向依赖移动端。
 * TODO(扩展): 若后续支持按优先级配置独立比例，改为从配置读取，本值作默认兜底。
 */
export const SLA_THRESHOLD_RATIO = 0.25
