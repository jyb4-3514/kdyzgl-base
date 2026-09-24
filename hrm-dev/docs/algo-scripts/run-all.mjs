// 一键复现：依次运行 S1–S8 原型脚本，输出各自指标与总耗时。
// 运行：node run-all.mjs
// 说明：每个子脚本均使用固定随机种子与固定基准数据集，重复运行结果逐位一致（除耗时列）。

import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const scripts = [
  ['S1 KPI 评分与权重', 's1-kpi.mjs'],
  ['S2 工资试算口径引擎', 's2-payroll.mjs'],
  ['S3 考勤排班生成', 's3-schedule.mjs'],
  ['S4 考勤异常检测', 's4-anomaly.mjs'],
  ['S5 请假计薪天数与重叠', 's5-leave.mjs'],
  ['S6 工单多目标派单', 's6-dispatch.mjs'],
  ['S7 包裹预测/容量/性能', 's7-parcel.mjs'],
  ['S8 通知限频与日志聚合', 's8-ratelimit.mjs']
]

const results = []
for (const [title, file] of scripts) {
  const t0 = performance.now()
  try {
    const stdout = execFileSync(process.execPath, [join(here, file)], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
    const ms = Number((performance.now() - t0).toFixed(1))
    results.push({ 场景: title, 脚本: file, 状态: 'OK', 输出字节: stdout.length, 耗时ms: ms })
  } catch (err) {
    results.push({ 场景: title, 脚本: file, 状态: `FAILED: ${err.message}`, 耗时ms: Number((performance.now() - t0).toFixed(1)) })
  }
}

console.log('=== algo-scripts 一键复现结果 ===')
console.table ? console.table(results) : console.log(results)
console.log(JSON.stringify(results, null, 2))
const failed = results.filter((r) => r.状态 !== 'OK')
console.log(`总计 ${results.length} 个场景，失败 ${failed.length} 个`)
process.exitCode = failed.length ? 1 : 0
