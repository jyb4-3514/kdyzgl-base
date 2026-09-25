/**
 * latest-wins 请求序号守卫（修 G2）
 *
 * 移动端列表页原来只有 `busy` 单飞：在途期间切换筛选/Tab，新请求被 `if (busy) return` 直接丢弃，
 * 新筛选永远加载不出来。本 composable 用「发起时自增 + 回来时比对」让后发请求胜出，
 * 过期响应连数据、错误与 loading 收尾一并丢弃。
 *
 * 与 useListPager 的单飞互补、不可互相顶替：单飞防重复请求，序号守卫防过期覆盖。
 */
export function useLatestRequest() {
  /** 最新一次请求的序号。只在 run 发起时自增，不在筛选变化时自增 —— 否则会漏掉首屏与筛选之间的竞争 */
  let latest = 0

  /**
   * 发起一次受保护的请求
   * @param {(token: number) => Promise<any>} task 收到本次序号，写状态前先用 isLatest 判断
   */
  function run(task) {
    latest += 1
    const token = latest
    return Promise.resolve().then(() => task(token))
  }

  /** 本次序号是否仍是最新：过期请求据此丢弃数据、错误，以及 loading 的关闭 */
  function isLatest(token) {
    return token === latest
  }

  /** 作废在途请求（reset / 组件卸载）：自增序号即可让旧响应全部过期 */
  function cancel() {
    latest += 1
  }

  return { run, isLatest, cancel }
}
