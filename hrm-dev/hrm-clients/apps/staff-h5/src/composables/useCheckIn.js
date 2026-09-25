import { ref } from 'vue'
import { showSuccessToast } from 'vant'
import { ATTENDANCE_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { ATTENDANCE_STATUS, CHECK_TYPE, dictLabel } from '@kdyzgl/shared/constants/dict.js'
import { checkIn } from '../api/attendance.js'
import { getWifiInfo } from '../utils/bridge.js'
import { checkErrorHint, distanceText, haversine, periodWindowText } from '../utils/attendance.js'

/**
 * 打卡提交（首页一键打卡 + 打卡页共用一份实现）
 *
 * 为什么抽出来：同一段「取 WiFi → 取定位 → 提交 → 按错误码给针对性提示」的逻辑，
 * 若首页与打卡页各写一遍，改一个错误码话术就会出现两处不一致。
 * 定位在浏览器环境必然落在围栏外（Mock 围栏坐标是虚构值），故失败时不在首页塞演示开关，
 * 而是给出「去打卡页开启演示辅助」的引导，保持首页不承载演示手段。
 *
 * 打卡页（时段多卡）的差异能力以可选参数表达，不另写实现：
 *   - options.key        提交中的槽位键（页面按格给 loading，避免整列转圈）
 *   - options.coordinate 提交坐标覆盖（页面已在自查卡里取过定位 / 开了演示辅助，不再重复取）
 *   - options.demoHint   错误话术是否引导开演示辅助（首页无开关时为 false）
 */
export function useCheckIn() {
  const submitting = ref(false)
  /** 正在提交的槽位键（可选）：首页不传则恒为空串，行为与改造前一致 */
  const submittingKey = ref('')
  /** 最近一次提交结果：{ ok, periodIndex, periodName, checkType, status, checkTime, code, hint } */
  const result = ref(null)

  /** 定位失败不阻塞提交：坐标取不到就传 null，由服务端按「未通过」判定（服务端是唯一准绳） */
  function locate() {
    return new Promise((resolve) => {
      if (!navigator.geolocation) return resolve({ longitude: null, latitude: null })
      navigator.geolocation.getCurrentPosition(
        (pos) =>
          resolve({
            longitude: Number(pos.coords.longitude.toFixed(6)),
            latitude: Number(pos.coords.latitude.toFixed(6))
          }),
        () => resolve({ longitude: null, latitude: null }),
        { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 }
      )
    })
  }

  /**
   * @param {object} period  `/attendance/status` 下发的时段对象
   * @param {'ON'|'OFF'} checkType
   * @param {object} rule    打卡规则（取白名单与时间窗余量，用于错误话术）
   * @param {object} [options] 打卡页差异能力（见文件头注释），省略即首页既有行为
   */
  async function submit(period, checkType, rule, options = {}) {
    if (submitting.value) return null
    submitting.value = true
    submittingKey.value = options.key || ''
    result.value = null
    const wifi = getWifiInfo(rule && rule.wifiList && rule.wifiList.length ? rule.wifiList[0].ssid : '')
    // 有覆盖坐标（页面已自查过）就直接用，避免再触发一次系统定位；
    // 覆盖坐标为「全 null」也走同一条路径 —— 那是页面明确表达「取不到，交服务端判定」
    const coord = options.coordinate || (await locate())
    try {
      const vo = await checkIn({ checkType, periodIndex: period.periodIndex, wifiSsid: wifi.ssid || null, ...coord })
      result.value = {
        ok: true,
        periodIndex: period.periodIndex,
        periodName: vo.periodName || period.name,
        checkType,
        status: vo.status,
        checkTime: vo.checkTime,
        checkMode: vo.checkMode,
        distance: vo.distance,
        remark: vo.remark
      }
      showSuccessToast(
        vo.status === 'NORMAL'
          ? `${result.value.periodName}打卡成功`
          : `打卡成功（${dictLabel(ATTENDANCE_STATUS, vo.status)}）`
      )
    } catch (e) {
      // 打卡失败话术按错误码给出（checkIn 走 silent），比通用 Toast 更能指导下一步；
      // 距离用「本次提交坐标 vs 规则围栏中心」现算，只说「距围栏中心 120 米」才让员工知道往哪走
      const distance =
        rule && rule.longitude != null && coord.longitude != null
          ? haversine(rule.longitude, rule.latitude, coord.longitude, coord.latitude)
          : null
      const hint = checkErrorHint(e.code, {
        checkTypeLabel: dictLabel(CHECK_TYPE, checkType),
        periodName: period.name,
        window: periodWindowText(period, rule),
        ssid: wifi.ssid,
        distanceText: distanceText(distance),
        demoHint: options.demoHint === true,
        message: e.message
      })
      result.value = {
        ok: false,
        periodIndex: period.periodIndex,
        periodName: period.name,
        checkType,
        code: e.code,
        // 首页没有演示开关，定位类失败直接指向打卡页，这是唯一能让演示跑通的路径
        demoGuide: e.code === ATTENDANCE_CODE.LOCATION_MISMATCH,
        hint
      }
    } finally {
      submittingKey.value = ''
      submitting.value = false
    }
    return result.value
  }

  return { submitting, submittingKey, result, submit }
}
