import { ref, watch } from 'vue'

/**
 * Tab 重复点击的信号通道（C5-2）
 *
 * 为什么不用路由 watch：重复点击当前 Tab 时路径不变，`watch(route.path)` 根本不触发。
 * Vant 的 `van-tabbar` 在点击已选中项时不会 emit `change`（源码 TabbarItem.mjs 有 `if (!active.value)` 守卫），
 * 因此只能由 `van-tabbar-item` 的 `@click` 判定「点的是当前项」后，用模块级 ref 通知当前 Tab 页回到根视图。
 */
const tick = ref(0)

export function notifyReselect() {
  tick.value += 1
}

/** 在 Tab 根页中注册重复点击回调（必须在组件 setup 内调用，内部使用 watch） */
export function useReselect(handler) {
  watch(tick, handler)
}
