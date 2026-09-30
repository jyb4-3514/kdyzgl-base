import { ROLE_LABEL } from '@kdyzgl/shared/constants/role.js'

/**
 * 管理端账号相关常量（非演示资产）· 驿站精灵
 * 演示账号清单与密码已放到 src/demo/accounts.js（仅 Mock 态加载，生产构建剔除），此处只留业务常量。
 * 端固定化：本端只服务 ADMIN，故 HOME_BY_ROLE 只登记管理端首页。
 */

/** 角色 → 登录后首页（管理端） */
export const HOME_BY_ROLE = {
  ADMIN: '/boss/home'
}

/** 角色 → 中文名（与 PC 端口径一致，直接复用角色常量表） */
export const roleLabel = (role) => ROLE_LABEL[role] || role || '-'

/**
 * 员工在职状态字典（1=在职，0=已停用）。
 * 为什么上提到 constants（原本在 station.vue 页内）：驿站详情账号列表与账号表单两处消费，
 * 加上原页面共 3 处，满足「三次抽取」前置（设计 ⑪#17）。shared/constants/dict.js 无此字典（属组织域）。
 */
export const EMPLOYEE_STATUS = {
  1: { label: '在职', type: 'success' },
  0: { label: '已停用', type: 'info', variant: 'outline' }
}

/**
 * 员工账号「身份」选项（驿站账号维护范围内）：员工 / 站长两项。
 *
 * 口径（用户澄清）：站长（STATION_ADMIN）只是员工账号上的一个身份，不是独立账号体系；
 * 账号只有管理员账号（登录驿站精灵 /boss/）与员工账号（登录驿站助手 /staff/）两类。
 * 后端 DTO 白名单为 `^(ADMIN|STATION_ADMIN|STAFF)$`，其中 ADMIN 属系统管理员账号，
 * 不在驿站账号维护范围，故此处只列员工账号可选的两种身份。
 */
export const ASSIGNABLE_ROLE_OPTIONS = [
  { value: 'STAFF', label: '员工' },
  { value: 'STATION_ADMIN', label: '站长' }
]
