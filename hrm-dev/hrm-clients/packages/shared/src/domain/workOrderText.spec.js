import { describe, expect, it } from 'vitest'
import { buildWorkOrderText } from './workOrderText.js'

const fullOrder = {
  id: 9,
  stationId: 1,
  parcelId: 2,
  reporterId: 3,
  assigneeId: 4,
  orderNo: 'WO20260920001',
  type: 2,
  priority: 2,
  status: 1,
  stationName: '城东驿站',
  assigneeName: '李四',
  reporterName: '张三',
  createTime: '2026-09-20 08:00:00',
  slaDeadline: '2026-09-21 08:00:00',
  waybillNo: 'YT1234567890',
  resolvedTime: '',
  closedTime: '',
  content: '设备故障',
  handleLog: [{ action: '转单' }],
  overdueUnhandled: false,
  updateTime: '2026-09-20 09:00:00'
}

describe('buildWorkOrderText', () => {
  it('字段顺序与标签固定，三端复制文本才能对得上', () => {
    expect(buildWorkOrderText(fullOrder).split('\n')).toEqual([
      '【工单详情】',
      '工单号：WO20260920001',
      '类型：设备故障',
      '优先级：高',
      '状态：处理中',
      '归属驿站：城东驿站',
      '处理人：李四',
      '上报人：张三',
      '创建时间：2026-09-20 08:00:00',
      'SLA 截止：2026-09-21 08:00:00',
      '关联运单：YT1234567890',
      '工单描述：设备故障'
    ])
  })

  it('空值行省略（不出现「解决时间：」这种空行）', () => {
    const text = buildWorkOrderText(fullOrder)
    expect(text).not.toContain('解决时间：')
    expect(text).not.toContain('关闭时间：')
  })

  it('工单号与状态即使为空也保留整行，便于识别是哪一单', () => {
    const text = buildWorkOrderText({ orderNo: '', status: 0 })
    const lines = text.split('\n')
    expect(lines).toContain('工单号：')
    expect(lines).toContain('状态：待处理')
  })

  it('内部技术字段不进复制文本', () => {
    const text = buildWorkOrderText(fullOrder)
    for (const field of [
      'id：',
      'stationId',
      'parcelId',
      'reporterId',
      'assigneeId',
      'handleLog',
      'overdueUnhandled'
    ]) {
      expect(text).not.toContain(field)
    }
  })

  it('字典未命中时该行按空值处理而被省略（不输出 - 占位）', () => {
    const text = buildWorkOrderText({ orderNo: 'WO1', status: 1, type: 99 })
    expect(text).not.toContain('类型：')
  })

  it('纯空白字段同样按空值省略', () => {
    expect(buildWorkOrderText({ orderNo: 'WO1', status: 1, stationName: '   ' })).not.toContain('归属驿站：')
  })

  it('入参为空返回空串，由调用方按复制失败处理', () => {
    expect(buildWorkOrderText(null)).toBe('')
    expect(buildWorkOrderText(undefined)).toBe('')
  })
})
