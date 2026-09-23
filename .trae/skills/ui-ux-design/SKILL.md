---
name: "ui-ux-design"
description: "UI/UX 设计专业技能。覆盖设计系统、Design Tokens、色彩体系、字体配对、布局模式、响应式、可访问性、动效设计。源自 nextlevelbuilder/ui-ux-pro-max-skill(62.6K Stars) + plugin87/ux-ui-agent-skills(Atomic Design) + superdesigndev/superdesign-skill。触发：UI/UX/设计/界面/样式/配色/字体/布局/响应式/动效/设计系统。"
---

# UI/UX 设计系统

> 综合 [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)（62.6K Stars，240+ 风格/127 字体配对/99 UX 指南） +
> [plugin87/ux-ui-agent-skills](https://github.com/plugin87/ux-ui-agent-skills)（Atomic Design + Design Tokens + 组件设计） +
> [superdesigndev/superdesign-skill](https://github.com/superdesigndev/superdesign-skill)（AI 产品设计智能体）。

## 设计流程：探索 → 系统 → 组件 → 验证

### 阶段 1：设计探索（Design Direction）
- 分析业务领域，确定设计方向（非 AI 默认风格）
- 从 67+ UI 风格中匹配：极简/玻璃态/新粗野/赛博朋克/暗黑/柔和/企业级
- 产出：Mood Board + 设计方向说明
- 禁止：Inter 字体 + 紫色渐变 + 圆角卡片（AI 默认三板斧）

### 阶段 2：设计系统（Design System）
按 Atomic Design 三层架构建立：

```
Primitive Tokens（基础层）
├── 色彩：品牌色/功能色/中性色，含 WCAG 对比度
├── 字体：标题/正文/辅助，配对规则
├── 间距：4px 基准，8px 步进
└── 圆角：sm(4) / md(8) / lg(12) / full(999)

Semantic Tokens（语义层）
├── surface/background/text/border 等语义变量
└── 浅色/深色主题映射

Component Tokens（组件层）
├── button/input/card/table/modal 等组件变量
└── 状态：default/hover/active/disabled/focus
```

### 阶段 3：组件设计（Component Design）
- 原子（Atoms）：按钮/输入框/图标/标签
- 分子（Molecules）：搜索框/表单组/导航项
- 有机体（Organisms）：表格/卡片组/导航栏/侧边栏
- 模板（Templates）：页面布局骨架
- 页面（Pages）：完整页面实例

每个组件覆盖：**anatomy + variants + states + token mapping + accessibility**

### 阶段 4：设计验证（Design Review）
- [ ] 色彩对比度 ≥ 4.5:1（WCAG AA）
- [ ] 字体层级清晰（3 级以内）
- [ ] 间距一致（8px 网格）
- [ ] 交互状态完整（hover/focus/active/disabled）
- [ ] 键盘导航可用
- [ ] 响应式断点合理（mobile/tablet/desktop）

## 色彩体系

### 配色方法论
- 60-30-10 法则：主色 60% / 辅助色 30% / 强调色 10%
- 品牌色提取：从 Logo/业务属性推导
- 功能色：成功(绿) / 警告(橙) / 危险(红) / 信息(蓝)
- 中性色：至少 8 级灰度（50~950）

### 本项目配色（快递驿站）
```
主色：蓝 #1890FF（信赖/效率）
辅助色：深蓝灰 #1F2937
强调色：橙 #FA8C16（包裹/物流）
成功：绿 #52C41A
警告：橙 #FAAD14
危险：红 #FF4D4F
背景：白 #FFFFFF / 浅灰 #F5F5F5
文字：深灰 #262626 / 中灰 #595959 / 浅灰 #8C8C8C
```

## 字体配对

| 层级 | 字体 | 字号 | 字重 | 用途 |
|------|------|------|------|------|
| H1 | 系统默认 | 24px | 600 | 页面标题 |
| H2 | 系统默认 | 20px | 600 | 区块标题 |
| H3 | 系统默认 | 16px | 500 | 卡片标题 |
| Body | 系统默认 | 14px | 400 | 正文 |
| Caption | 系统默认 | 12px | 400 | 辅助文字 |

## 响应式断点（本项目）

| 断点 | 宽度 | 用途 |
|------|------|------|
| xs | < 768px | 手机（H5 壳） |
| sm | 768px ~ 992px | 平板 |
| md | 992px ~ 1200px | 小桌面 |
| lg | 1200px ~ 1600px | 大桌面 |
| xl | > 1600px | 超大屏 |

## 布局模式

| 布局 | 适用场景 |
|------|---------|
| 侧边栏 + 内容 | 管理后台（本项目 hrm-admin） |
| 顶部导航 + 内容 | 简单页面 |
| 栅格卡片 | 仪表盘/概览页 |
| 主从布局 | 列表-详情页 |
| 单列居中 | 登录/表单页 |

## 动效设计

- 微交互：按钮 hover/click 反馈，< 200ms
- 页面过渡：fade/slide，200-300ms
- 加载态：骨架屏（优于转圈）
- 空状态：引导插图 + 操作按钮
- 禁止：过度动画、自动播放

## UX 反模式（99 条精选）

| 反模式 | 正确做法 |
|--------|---------|
| 无 loading 态 | 骨架屏/进度条，3 秒内出反馈 |
| 无 empty 态 | 空状态插图 + 引导操作 |
| 无 error 态 | 错误信息 + 重试/回退按钮 |
| 表单无校验 | 实时校验 + 友好提示 |
| 删除无确认 | 二次确认弹窗 |
| 长列表无分页 | 分页/虚拟滚动 |
| 无响应式 | 移动端适配 |
| 点击区域太小 | 最小 44x44px |

## 参考来源

- [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) — 62.6K Stars，240+ 风格
- [plugin87/ux-ui-agent-skills](https://github.com/plugin87/ux-ui-agent-skills) — Atomic Design + Design Tokens
- [superdesigndev/superdesign-skill](https://github.com/superdesigndev/superdesign-skill) — AI 产品设计智能体
- [master5d/claude-design-skills](https://github.com/master5d/claude-design-skills) — 3 层设计体系