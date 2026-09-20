/**
 * Element Plus 浅色阶「离线生成 + 校验」脚本（ADR-A26）
 *
 * 算法依据（已由官方源码核实）：
 *   element-plus/theme-chalk/src/common/var.scss 的 @mixin set-color-mix-level
 *   light-N = mix($color-white, base, N * 10%)  → base * (1 - N/10) + white * (N/10)，逐通道线性插值
 *   dark-2  = mix($color-black, base, 20%)
 *
 * 为什么是离线脚本而不是引 theme-chalk 的 SCSS 生成链：
 *   引它会将 Element 的 SCSS 源码纳入本项目编译图，触碰 resolve.dedupe 白屏护城河。
 *
 * ============================ 两段式校验设计 ============================
 * 30 个字面量来自两条完全不同的来源，用同一把尺子量必然长期红灯，
 * 而长期红灯的门禁等于没有门禁（团队会直接绕过）。故拆成两类分别校验：
 *
 *   A 类（算法可复现族，primary）：现值必须能被 round 逐通道混色精确复现，不一致即失败。
 *      这类值改错了会被算法立刻抓出来，属于「可推导、必须推导」的部分。
 *
 *   B 类（手写族，success / warning / danger / info）：这几族的浅色阶当年是按设计文档
 *      给定的字面量写死的（docs/demo-ui-redesign.md 2.8 / U2），并非由基色 mix 推导，
 *      现值与算法值本就不该相等。对它们改用「已确认的手写值基线快照」比对 ——
 *      只有偏离基线（有人手滑改了某个浅色阶）才失败。
 *
 * 基线维护：B 类色值若确需调整，必须同步改 HANDWRITTEN_BASELINE，
 * 让「改色」成为一次显式评审，而不是悄悄生效。新增 B 类字面量而未登记基线同样会失败。
 *
 * 注意：hex 一律按小写归一后再比对 —— 文件里是小写、算法输出是大写，
 * 原先大小写敏感的比较会把 primary 的 6 个正确值全部误报为不一致。
 * ======================================================================
 */
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const TOKENS_DIR = fileURLToPath(new URL('.', import.meta.url))
const TOKENS_FILE = resolve(TOKENS_DIR, '../src/pc/styles/tokens.scss')
// 跨端 Token 真源（P1-2）：平台差异层只引用不定义 primitive 时，从这里兜底解析基色
const BASE_FILE = resolve(TOKENS_DIR, '../src/shared/styles/tokens.base.scss')
const COLORS = ['primary', 'success', 'warning', 'danger', 'info']
const LIGHT_LEVELS = [3, 5, 7, 8, 9]
const DARK_LEVELS = [2]
const WHITE = [255, 255, 255]
const BLACK = [0, 0, 0]

/** A 类：算法可复现族，现值必须能被 round 混色精确复现 */
const ALGORITHMIC_FAMILIES = ['primary']

/**
 * B 类：已确认的手写值基线（与 tokens.scss 现值一一对应，改动色值须同步改这里）
 * 键为 CSS 变量名，值为小写 hex。
 */
const HANDWRITTEN_BASELINE = {
  '--el-color-success-light-3': '#6aa84f',
  '--el-color-success-light-5': '#9dc98a',
  '--el-color-success-light-7': '#cde5c1',
  '--el-color-success-light-8': '#def0d6',
  '--el-color-success-light-9': '#eef9e8',
  '--el-color-success-dark-2': '#1c6003',
  '--el-color-warning-light-3': '#d08a50',
  '--el-color-warning-light-5': '#e0ad84',
  '--el-color-warning-light-7': '#efd2bc',
  '--el-color-warning-light-8': '#f4e0d2',
  '--el-color-warning-light-9': '#fef7e8',
  '--el-color-warning-dark-2': '#903f04',
  '--el-color-danger-light-3': '#dd5862',
  '--el-color-danger-light-5': '#e78990',
  '--el-color-danger-light-7': '#f1b7bc',
  '--el-color-danger-light-8': '#f6ced1',
  '--el-color-danger-light-9': '#ffeded',
  '--el-color-danger-dark-2': '#a60f1b',
  '--el-color-info-light-3': '#8b9199',
  '--el-color-info-light-5': '#aeb2b8',
  '--el-color-info-light-7': '#d1d3d7',
  '--el-color-info-light-8': '#e3e4e7',
  '--el-color-info-light-9': '#edeeef',
  '--el-color-info-dark-2': '#3c444f'
}

const platformText = readFileSync(TOKENS_FILE, 'utf8')
const baseText = existsSync(BASE_FILE) ? readFileSync(BASE_FILE, 'utf8') : ''
// 平台层排在前面，同名 Token 以平台层为准
const text = platformText + '\n' + baseText

/** 基色可能直接写 hex，也可能引用 primitive 变量（可能在平台层，也可能已移到共享真源） */
function resolveBase(raw) {
  const ref = raw.match(/var\(--([\w-]+)\)/)
  if (!ref) return raw
  const hit = text.match(new RegExp(`--${ref[1]}:\\s*(#[0-9A-Fa-f]{6})`))
  if (!hit) throw new Error(`基色引用的变量 --${ref[1]} 在 tokens.scss / tokens.base.scss 中均找不到，无法比对`)
  return hit[1]
}

const hexToRgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
const rgbToHex = (rgb) => '#' + rgb.map((v) => v.toString(16).padStart(2, '0').toUpperCase()).join('')
const normalize = (hex) => String(hex).trim().toLowerCase()

/** 逐通道线性插值；mode 决定取整方式，用来复现项目口径 */
function mix(hex, target, weight, mode) {
  const round = mode === 'floor' ? Math.floor : Math.round
  const base = hexToRgb(hex)
  return rgbToHex(base.map((v, i) => round(v * (1 - weight) + target[i] * weight)))
}

/** 取值可能是 hex 字面量，也可能是 var(--x) 引用，先原样取出再交由 resolveBase 处理 */
const readToken = (key) => {
  const hit = text.match(new RegExp(`--${key}:\\s*([^;]+);`))
  return hit ? hit[1].trim() : null
}

const rows = []
for (const name of COLORS) {
  const rawBase = readToken(`el-color-${name}`)
  if (!rawBase) continue
  const base = resolveBase(rawBase)
  for (const level of LIGHT_LEVELS) {
    const actual = readToken(`el-color-${name}-light-${level}`)
    if (!actual) continue
    rows.push({
      family: name,
      key: `--el-color-${name}-light-${level}`,
      base,
      actual,
      round: mix(base, WHITE, level / 10, 'round'),
      floor: mix(base, WHITE, level / 10, 'floor')
    })
  }
  for (const level of DARK_LEVELS) {
    const actual = readToken(`el-color-${name}-dark-${level}`)
    if (!actual) continue
    rows.push({
      family: name,
      key: `--el-color-${name}-dark-${level}`,
      base,
      actual,
      round: mix(base, BLACK, level / 10, 'round'),
      floor: mix(base, BLACK, level / 10, 'floor')
    })
  }
}

const hits = (mode) => rows.filter((r) => normalize(r[mode]) === normalize(r.actual))
const roundHits = hits('round').length
const floorHits = hits('floor').length
const mode = roundHits >= floorHits ? 'round' : 'floor'

// ---- 两段式校验 ----
const problems = []
const algorithmRows = rows.filter((r) => ALGORITHMIC_FAMILIES.includes(r.family))
const handwrittenRows = rows.filter((r) => !ALGORITHMIC_FAMILIES.includes(r.family))

algorithmRows.forEach((r) => {
  if (normalize(r.round) !== normalize(r.actual)) {
    problems.push(`[A 类·算法不符] ${r.key}  现值 ${r.actual}  应为 ${r.round}`)
  }
})
handwrittenRows.forEach((r) => {
  const baseline = HANDWRITTEN_BASELINE[r.key]
  if (!baseline) problems.push(`[B 类·基线缺失] ${r.key}  现值 ${r.actual}  未登记进 HANDWRITTEN_BASELINE`)
  else if (normalize(baseline) !== normalize(r.actual)) {
    problems.push(`[B 类·偏离基线] ${r.key}  现值 ${r.actual}  基线 ${baseline}`)
  }
})
// 基线里登记了、但 tokens 里已不存在的键，同样要提醒（避免基线腐化）
const missingTokens = Object.keys(HANDWRITTEN_BASELINE).filter((key) => !rows.some((r) => r.key === key))
missingTokens.forEach((key) => problems.push(`[B 类·基线多余] ${key}  tokens.scss 中已不存在该字面量`))

const checkOnly = process.argv.includes('--check')
const lines = [
  `令牌文件：${TOKENS_FILE}`,
  `基色：${COLORS.map((n) => `${n} ${resolveBase(readToken(`el-color-${n}`))}`).join(' / ')}`,
  `待校验字面量：${rows.length} 个 —— A 类（算法可复现：${ALGORITHMIC_FAMILIES.join('/')}）${algorithmRows.length} 个` +
    ` / B 类（手写基线）${handwrittenRows.length} 个`,
  `算法命中参考：${mode} 命中 ${hits(mode).length}/${rows.length}（round ${roundHits} / floor ${floorHits}）`
]

if (problems.length) {
  lines.push(`校验结果：失败 ${problems.length} 项`)
  problems.forEach((line) => lines.push(`  ${line}`))
} else {
  lines.push(
    `校验结果：通过 —— A 类 ${algorithmRows.length} 个与 ${mode} 算法一致；B 类 ${handwrittenRows.length} 个与手写基线一致`
  )
}

if (checkOnly) {
  console.log(lines.join('\n'))
  process.exitCode = problems.length ? 1 : 0
} else {
  // 生成模式：只按算法生成 A 类（B 类的手写值有意不参与生成，避免覆盖设计确认过的色值）
  const block = algorithmRows.map((r) => `  ${r.key}: ${r[mode]};`).join('\n')
  console.log(
    `${lines.join('\n')}\n\n按 ${mode} 生成 A 类（仅供参考，脚本不写入文件；B 类为手写族，不生成）：\n${block}`
  )
}
