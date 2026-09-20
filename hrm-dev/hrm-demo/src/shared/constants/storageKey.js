/**
 * 移动端登录态存储键（单一真源）
 *
 * 为什么抽到 shared：演示剧本 demo/scenario.js 要在端选择页预置/清理登录态，
 * 但它属「端选择层」，按依赖边界不得反向依赖 mobile 具体端代码；
 * 键名是两端共同的存储契约，收敛到 shared/constants 供 mobile 与 demo 同时引用。
 *
 * 为什么移动端另起一套 key（demo-design.md 6.2）：PC 端用 @admin/stores/auth 的 key，
 * 同一浏览器可并存两套登录态，演示 S13「三端数据一致性」需要双标签同时登录。
 */
export const MOBILE_TOKEN_KEY = 'hrm_demo_mobile_token'
export const MOBILE_USER_KEY = 'hrm_demo_mobile_user'
