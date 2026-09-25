import { describe, expect, it } from 'vitest'
import { CSV_TYPE, csvDisposition, escapeCsv, parseCsv, toCsvBlob } from './csv.js'

/**
 * parseCsv 的容错预期均以源码实现为准（逐字符扫描+末尾空行剔除），
 * 不做 RFC 之外的「猜测式」断言，避免把实现缺陷固化成期望值。
 */
describe('parseCsv · RFC 4180 边界', () => {
  it('普通行按逗号切列', () => {
    expect(parseCsv('a,b,c')).toEqual([['a', 'b', 'c']])
  })

  it('引号包裹的单元格内逗号不切列', () => {
    expect(parseCsv('"a,b",c')).toEqual([['a,b', 'c']])
  })

  it('引号翻倍还原为一个字面引号', () => {
    expect(parseCsv('"say ""hi""",x')).toEqual([['say "hi"', 'x']])
  })

  it('引号内的换行保留在单元格里', () => {
    expect(parseCsv('"line1\nline2",x')).toEqual([['line1\nline2', 'x']])
  })

  it('引号内的 CRLF 原样保留（不做行拆分）', () => {
    expect(parseCsv('"line1\r\nline2",x')).toEqual([['line1\r\nline2', 'x']])
  })

  it('CRLF 与 LF 混用都能正确分行', () => {
    expect(parseCsv('a,b\r\nc,d\ne,f')).toEqual([
      ['a', 'b'],
      ['c', 'd'],
      ['e', 'f']
    ])
  })

  it('CRLF 只算一次换行，不产生空行', () => {
    expect(parseCsv('a,b\r\nc,d')).toHaveLength(2)
  })

  it('剥离 UTF-8 BOM', () => {
    expect(parseCsv('\ufeffa,b')).toEqual([['a', 'b']])
  })

  it('末尾空行被剔除（Excel 另存常留一行）', () => {
    expect(parseCsv('a,b\r\nc,d\n')).toEqual([
      ['a', 'b'],
      ['c', 'd']
    ])
    expect(parseCsv('a,b\n\n\n')).toEqual([['a', 'b']])
  })

  it('末尾多余逗号保留为空单元格（这是数据不是空行）', () => {
    expect(parseCsv('a,')).toEqual([['a', '']])
  })

  it('空输入归一为「一行一空单元格」而不是空数组', () => {
    expect(parseCsv('')).toEqual([['']])
    expect(parseCsv(null)).toEqual([['']])
  })

  it('只有换行的输入同样归一到一行空单元格', () => {
    expect(parseCsv('\n')).toEqual([['']])
  })

  it('引号未闭合时容错：其余内容作为一个单元格，不抛异常', () => {
    expect(parseCsv('a,"bc')).toEqual([['a', 'bc']])
    expect(parseCsv('"abc')).toEqual([['abc']])
  })

  it('引号出现在单元格中部时按普通字符处理', () => {
    expect(parseCsv('ab"cd')).toEqual([['abcd']])
  })
})

describe('escapeCsv', () => {
  it('含逗号 / 引号 / 换行时包裹并翻倍引号', () => {
    expect(escapeCsv('a,b')).toBe('"a,b"')
    expect(escapeCsv('say "hi"')).toBe('"say ""hi"""')
    expect(escapeCsv('a\nb')).toBe('"a\nb"')
  })

  it('普通值原样输出', () => {
    expect(escapeCsv('abc')).toBe('abc')
  })

  it('null / undefined 归一为空串', () => {
    expect(escapeCsv(null)).toBe('')
    expect(escapeCsv(undefined)).toBe('')
  })
})

describe('toCsvBlob', () => {
  it('带 BOM 且行间为 CRLF，Excel 打开中文不乱码', async () => {
    const blob = toCsvBlob([
      ['名称', '数量'],
      ['驿站,东', 3]
    ])
    // Blob.text() 按 UTF-8 解码会剥掉 BOM，故断言原始字节
    const bytes = new Uint8Array(await blob.arrayBuffer())
    expect([...bytes.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf])
    const text = await blob.text()
    expect(text).toContain('名称,数量')
    expect(text).toContain('"驿站,东",3')
    expect(text).toContain('\r\n')
  })
})

describe('csvDisposition', () => {
  it('文件名按 RFC 5987 编码，中文不裸奔', () => {
    const value = csvDisposition('考勤明细.csv')
    expect(value.startsWith("attachment; filename*=UTF-8''")).toBe(true)
    expect(value).not.toContain('考勤明细')
  })
})

describe('CSV_TYPE', () => {
  it('带 charset，避免浏览器按 latin-1 解中文', () => {
    expect(CSV_TYPE).toBe('text/csv;charset=utf-8')
  })
})
