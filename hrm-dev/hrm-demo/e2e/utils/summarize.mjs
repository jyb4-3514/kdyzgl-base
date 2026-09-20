import fs from 'node:fs'
// 汇总 Playwright JSON 报告中的失败用例与原因（用于报告问题列表）
const report = JSON.parse(fs.readFileSync(new URL('../evidence/pw-results.json', import.meta.url), 'utf8'))

function walk(suites, out) {
  for (const s of suites || []) {
    for (const spec of s.specs || []) {
      for (const t of spec.tests || []) {
        for (const r of t.results || []) {
          out.push({ title: spec.title, status: r.status, ms: r.duration, error: (r.error && r.error.message) || '' })
        }
      }
    }
    walk(s.suites, out)
  }
}
const all = []
walk(report.suites, all)

const failed = all.filter((x) => x.status !== 'passed')
console.log(`total=${all.length} passed=${all.filter((x) => x.status === 'passed').length} failed=${failed.length}\n`)
for (const f of failed) {
  console.log('### ' + f.title)
  console.log('  status=' + f.status + ' ms=' + f.ms)
  console.log('  ' + f.error.replace(/\n/g, '\n  ').slice(0, 700))
  console.log()
}
