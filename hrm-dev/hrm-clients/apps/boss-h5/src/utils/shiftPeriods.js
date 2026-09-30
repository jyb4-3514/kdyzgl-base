/**
 * 打卡时段 ↔ 班次：打卡规则页「就地改打卡时间」的前置纯逻辑（页面只做取数与渲染）
 *
 * 背景：打卡时段的**唯一真源是该驿站启用班次**，规则页只能读取派生的 checkPeriods
 * （`checkPeriodsReadonly=true`，`PUT /rule` 传 checkPeriods 会 400）。而契约里派的时段
 * `CheckPeriod[]` 只有 `{ name, startTime, endTime }`、**不含班次 id**（api.md §4.6.1），
 * 写入口只有班次（`PUT /shifts/{id}`，api.md §4.8.3）。要把「改时段」落到「改班次」，
 * 必须先按 stationId 取班次再做一次配对——本模块就是这一步，抽为纯函数便于回归。
 */

/** 'HH:mm' → 当日分钟数（`'24:00'` = 1440）；非法输入回 NaN，便于调用方自行判空 */
export function minutesOfDay(text) {
  const [h, m] = String(text || '')
    .split(':')
    .map(Number)
  return Number.isFinite(h) && Number.isFinite(m) ? h * 60 + m : NaN
}

/** 启用班次（`status === 1`）按开始时间升序：与服务端派生时段的取数口径一致（api.md §4.8.1 升序） */
export function enabledShiftsSorted(shifts) {
  if (!Array.isArray(shifts)) return []
  return shifts
    .filter((item) => item && item.status === 1)
    .slice()
    .sort((a, b) => minutesOfDay(a.startTime) - minutesOfDay(b.startTime))
}

/**
 * 把派生时段逐行配对到启用班次（**一行一个班次，不重复占用**）。
 * 命中优先级：① (班次名, 起止) 三元组精确命中；② 两侧数量一致时按升序位置兜底（班次名被改过等）。
 * 配不到 → `shift = null`：调用方据此把该行降级为只读，**不臆造班次 id**（写错站比不改更糟）。
 * @param {Array<{name?:string,startTime:string,endTime:string}>} periods 服务端派生的 checkPeriods
 * @param {Array<object>} shifts 该驿站班次全集（含停用，`GET /shifts?stationId=`）
 * @returns {Array<{index:number,name:string,startTime:string,endTime:string,shift:object|null}>}
 */
export function matchPeriodsToShifts(periods, shifts) {
  const list = Array.isArray(periods) ? periods : []
  const pool = enabledShiftsSorted(shifts)
  const sameCount = pool.length === list.length
  return list.map((period, index) => {
    let at = pool.findIndex(
      (shift) =>
        shift.shiftName === period.name &&
        shift.startTime === period.startTime &&
        shift.endTime === period.endTime
    )
    if (at < 0 && sameCount) at = index
    const shift = at >= 0 ? pool[at] : null
    // 命中后移出池：同名同起止的多条班次不会被同一行复用
    if (at >= 0) pool.splice(at, 1)
    return {
      index,
      name: period.name || '未命名班次',
      startTime: period.startTime,
      endTime: period.endTime,
      shift
    }
  })
}
