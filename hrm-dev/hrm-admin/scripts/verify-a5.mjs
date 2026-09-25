#!/usr/bin/env node
/**
 * hrm-admin A5 断言校验（U-B 最小门禁之一）
 *
 * 依据：
 *   - ADR-structure-migration.md §2.3 A5：`hrm-admin` Token 无游离字面量
 *     → 「在 hrm-admin/src 检索十六进制色值字面量，与 hrm-admin/src/styles/tokens.scss 白名单比对，
 *        未登记字面量 = 0」
 *   - ui-experience-optimization.md §1.5 R5-3①（机器化载体）与 A5-5
 *     → 「`hrm-admin/src/styles/index.scss` 内十六进制色值 = 0」
 *
 * 口径：
 *   1) 白名单 = `src/styles/tokens.scss` 内的十六进制字面量集合；
 *   2) 两侧均**先剥离注释**再取值 —— 注释不是「承载色值」，不应参与白名单；
 *   3) 归一到「6 位小写」后比较（#fff ≡ #ffffff），与 tokens.scss 头部声明一致；
 *   4) 扫描范围 = `src/**`，排除 tokens.scss 自身与 node_modules/dist。
 *
 * 无第三方依赖（纯 Node），可在未安装 eslint 的环境独立运行。
 * 退出码：0 = 达标；1 = 存在未登记字面量（门禁红）。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = fileURLToPath(new URL('.', import.meta.url))
const PKG_ROOT = resolve(SCRIPT_DIR, '..')
const SRC_DIR = join(PKG_ROOT, 'src')
const TOKENS_FILE = join(SRC_DIR, 'styles', 'tokens.scss')
const INDEX_SCSS = join(SRC_DIR, 'styles', 'index.scss')

const SCAN_EXT = /\.(vue|js|mjs|cjs|ts|scss|css|html)$/
const SKIP_DIRS = new Set(['node_modules', 'dist', '.git'])
const HEX_RE = /#[0-9a-fA-F]{3,8}\b/g

/** 剥离注释：/* ... *\/ 与 // 行注释（用 [^:] 前缀避免误伤 http:// 等协议头） */
function stripComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:])\/\/[^\n]*/g, '$1')
}

/** 归一到 6 位小写；3 位简写按 CSS 规则展开；4/8 位含 alpha 原样保留（本项目约定不使用） */
function normalize(hex) {
  let h = hex.slice(1).toLowerCase()
  if (h.length === 3) h = h.split('').map((c) => c + c).join('')
  return '#' + h
}

const hexIn = (file) => {
  const text = stripComments(readFileSync(file, 'utf8'))
  return (text.match(HEX_RE) || []).map(normalize)
}

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (SKIP_DIRS.has(name)) continue
    const full = join(dir, name)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (SCAN_EXT.test(name)) out.push(full)
  }
  return out
}

const rel = (p) => relative(PKG_ROOT, p).split(sep).join('/')

// 1) 白名单
const whitelist = new Set(hexIn(TOKENS_FILE))

// 2) 扫描
const files = walk(SRC_DIR).filter((f) => resolve(f) !== resolve(TOKENS_FILE))
const violations = []
const indexScssHits = []
let scanned = 0

for (const file of files) {
  const hits = hexIn(file)
  if (!hits.length) continue
  scanned += hits.length
  for (const hex of hits) {
    if (!whitelist.has(hex)) violations.push({ file: rel(file), hex })
    if (file === INDEX_SCSS) indexScssHits.push(hex)
  }
}

// 3) 输出
console.log('A5 断言 · hrm-admin Token 无游离字面量')
console.log(`  白名单来源：${rel(TOKENS_FILE)}（${whitelist.size} 个登记字面量）`)
console.log(`  扫描文件：src/**（${files.length} 个，已排除 tokens.scss 与 node_modules/dist）`)
console.log(`  命中字面量：${scanned} 个`)
console.log(`  未登记字面量：${violations.length} 个（要求 = 0）`)
violations.forEach((v) => console.log(`    × ${v.file}  ${v.hex}`))
console.log(`  index.scss 十六进制色值：${indexScssHits.length} 个（要求 = 0）`)

if (violations.length === 0 && indexScssHits.length === 0) {
  console.log('校验结果：通过')
  process.exitCode = 0
} else {
  console.log('校验结果：失败（见上方 × 项）')
  process.exitCode = 1
}
