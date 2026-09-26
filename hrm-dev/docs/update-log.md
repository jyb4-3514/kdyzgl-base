# 变更日志

## 2026-09-26 · 驿站精灵可编辑 WiFi 白名单 + 新增站点管理页（UI/UX + 前端 + B 档部署）

**一、需求（用户）**：① 移动端**驿站精灵可以设置 WiFi**（现场维护白名单，用于指定员工在该 WiFi 打卡）；② **新增站点管理**，给员工设置站点（本轮先做只读骨架）。

**二、关键事实核查（推翻两处既有认知）**：
- **驿站精灵是 ADMIN 专用端**（`apps/boss-h5` 的 `BOSS_ROLES = [ROLE.ADMIN]`，端准入 fail-closed 拒其它角色；站长实际走 `apps/staff-h5`）→ 后端 `PUT /attendance/rule` **仅 ADMIN 可写**与端准入**天然一致**，本批**不涉权限放宽、无需安全评估**。截图中「改白名单请走 PC 端」是**旧文案**而非权限限制。
- 后端 `wifiList` 校验**极宽松**（仅"数组 + `ssid` 非空"，见 `AttendanceRuleServiceImpl.java:185-188`，**无长度/MAC/重复校验**）→ 前端校验属**先行约束**，与后端**不对称**，已登记遗留。

**三、设计交付**（UI/UX 设计师）：`docs/boss-wifi-and-station-design.md`（702 行，commit `cbd02f2`）—— 含入口决策（「我的·管理与配置」，人事管理之后）、权限分支、**降级三件套**（「读取当前 WiFi」可点 + 明确拒绝 + 不预填）、四态矩阵、**Tokens 零新增**、文案 49 条、可访问性 11 项对比度实测、视觉走查 45 项。

**四、实现**（前端工程师，commit `81e2eb0`，7 文件）：
- **打卡规则页白名单改为可编辑**（增/删/改 SSID+BSSID，单行编辑态）；**关键修复：原 payload 不含 `wifiList`**，不提交则保存后不生效 —— 现显式提交（本地 `key` 不外发）。
- 校验纯函数抽离 `utils/wifiWhitelist.js`（`ssid` 必填/≤32、`bssid` 选填验 MAC、**重复判定区分大小写**、`isWifiEditable`、`canAutoFillWifi` 判据为 `getWifiInfo().mock === false`）+ **17 条单测**锁口径。
- **降级态不得伪装能力**：按钮可点即明确告知需手动输入，**不发起读取、不预填**（沿用项目硬约束）。
- **新增站点管理页** `station.vue`（只读骨架）：`StationPicker` + `GET /employees?stationId=`（`pageNum/pageSize` + `van-list` 触底加载）+ `PageState` 四态；**零写控件**，写操作与前置条件标注 `TODO(扩展)`；入口落「我的·管理与配置」；路由 `/boss/station`。
- 主智能体裁定三项：名册数据源取 `/employees?stationId=`；**取消"20 条上限"**（设计稿取值，无后端依据）；降级形态采纳「可点+明确拒绝」。

**五、验证**：`apps/boss-h5` → `lint` 0 error、`test` **125 passed**（基线 108，+17 全为新增）、`build:prod` ✓10.5s、`e2e` **11 passed**；根门禁 `verify:mock` **942/942**、`verify:tokens` 6/6、`verify:mobile -w @dyzgl/boss-h5` 20/0；产物无下架模块 chunk。**主智能体独立复跑**：`test` 125、`build` ✓、并确认 `wifiList` 已在提交体（`attendanceRule.vue:149-152`）。

**六、部署（B 档·幂等替换静态产物，主智能体自决）**：仅替换 `/data/www/download/hrm-clients/boss`（旧目录 `mv` 至 `/data/backup/hrm-clients-20260926035313/boss`，**未用 `rm`**）；**未改 Nginx**。线上 `boss` 79 文件、**`.map` = 0**；`/boss/` `/boss/station` `/boss/attendance/rule` 均 **200**，`/web/` `/staff/` 不受影响 200；`.map` 404；入口 asset `index-B0lDrafA.js` 与本地构建**逐字一致**。**回滚**：把备份目录换回同名目录即可。

**七、遗留**：① 安卓壳 `HrmBridge.getWifiInfo` 未实现 → 「读取当前 WiFi」恒降级（判据已落为纯函数+单测，壳实现后启用）；② 站点管理写操作待口径 + 端侧写接口 + 算法方案 + 技术评审；③ 后端未补 `wifiList` 校验（长度/MAC/重复）→ PC 端或直调 API 可绕过前端约束；④ `/employees` 缺 `hasProfile`，无档案员工点开落 9301。

## 2026-09-26 · MVP 裁剪（下架 KPI / 包裹族 / 同步 / 占位页）+ 三端重新部署上线（前端 + C 档部署，主智能体执行）

**一、需求裁定（用户）**：先做**最小可用版本**——砍掉 **KPI 模块**、**包裹族整体**（`/web/parcel`、`/web/parcel/sync`、`/boss/trend`、`/boss/parcel/:id`、`/staff/parcel`、`/staff/parcel/:id`、`/staff/pickup`、`/staff/sync`）与 `/web/` 5 个疑似占位页（`performance` / `money` / `permission` / `system` / `knowledge`）；**砍掉的模块前端完全不展示**。未答复项按激进裁法执行：`/boss/rank`（驿站排行）随 KPI 族下架；`/boss/alerts`（异常预警）**保留**。

**二、实现**（前端工程师，commit `96b2064`，39 文件：web 16 / boss 13 / staff 10）：
- 三端 `src/router/index.js` 共删 **18 条**路由记录；新增断言「被砍路径一律 404」「保留路径仍可达」。
- **不删源文件**：被砍页面 `.vue` 与 api **保留磁盘、零引用**（构建产物内 `kpi|parcel|pickup|sync|rank|trend|performance|knowledge` chunk 命中 **0**）；后期迭代放开路由即可恢复。
- 入口与展示同步收起：web 菜单 `config/menu.js`（分组「包裹作业→作业管理」）、看板由 6 块收敛为 2 块（工单指标 + 组织规模）、员工档案删 KPI 卡、系统设置删「已预置包裹总数」、通知 `BIZ_ROUTE` 删 parcel/sync_task；boss 宫格删「包裹趋势/驿站排行」、待办组删「采集异常」、首页删 4 包裹指标卡与趋势/同步健康度/驿站 TOP3、「我的」删 KPI cell、`alerts` 由四组收敛为「超时未处理工单」一组；staff 宫格删「本站包裹/取件核销/我的 KPI」、首页删包裹指标卡与 KPI 取数、「我的数据」删 KPI 与同步状态入口。
- **隐性依赖修复**：web `ROLE_LANDING.STATION_ADMIN` 原为 `/parcel`（下架后会 404）→ 改为 `/attendance`；员工档案 `activeMenu` 与 breadcrumb 同步去 KPI。

**三、验收基线变更（需求裁剪，非迁就代码）**：删 / 改写用例 **16 条**并逐条登记——web 看板 7 条（`dayOverDay` 环比 3 + `buildRankRows` 3 + 取数 4）与 e2e `A5-1`；boss e2e `05-render.spec.js` **整文件**（唯一用例指向 `/boss/trend`）；staff e2e `A5-7`；boss `todo.spec.js` 4 处改写（改以零值组做对照）。**保留页断言未弱化**（守卫 4 条、Tabbar、宫格项数原样通过）。

**四、独立复核（主智能体，不采信自述）**：三端 `npm run test` = **102 / 108 / 234 全过**；`npm run build:prod` 三端 EXIT=0（32.7s / 14.7s / 14.2s）；本地产物被砍 chunk 命中 0；e2e（子智能体实跑）19 / 11 / 17 passed。

**五、部署（C 档，用户指令「先达到上线前我在域名预览」）**：`build:prod` → 打 tar（**上传时排除 `*.map`**）→ 上传 `/data/hrm-tmp/{web,staff,boss}.tar.gz` → 服务器以 `mv` 将旧目录移入 `/data/backup/hrm-clients-20260926032430/` 后解压新产物（**未用 `rm`，旧版完整保留**）。**未改任何 Nginx 配置**。

**六、线上验证**：`/web/ /staff/ /boss/` **200**；深链 `/web/employee` `/staff/attendance` `/boss/payroll` **200**；`*.map` **404**；`/` `/health` `/apk/` **200**；80 → **301**；线上文件数 web **140** / staff **68** / boss **77**，**`.map` = 0**；**入口 asset 哈希与本地构建逐字一致**（`index-CbvobW6q.js`、`index-BhuARSKO.js`）→ 证明确为本次构建产物。

**七、回滚**：把 `/data/backup/hrm-clients-20260926032430/{web,staff,boss}` 换回 `/data/www/download/hrm-clients/` 同名目录即可（无数据变更、不重建容器）。

**八、事实性纠正（下游纠正上游）**：三端生产构建为 `sourcemap:'hidden'`——**本地 dist 确实产出 map**（web 85 / boss 40 / staff 36）；此前「build 零 map」表述不准确，**准确口径：构建产 map，部署上传时排除，故线上 0 map**，且 Nginx 对 `.map` 另返回 404（双保险）。

**九、遗留**：① 财务计薪规则 `PayrollRuleEditor.vue` / `PayrollDetailTable.vue` 仍含「KPI 得分」计薪来源项（属计薪契约与 Mock 断言范围，**保留未动**，待口径确认）；② 被砍模块的休眠单测（web `sync/*.spec.js`）仍通过但无引用，`TODO(扩展): 二期恢复包裹/同步时统一启用或清理`；③ `/web/` 看板如后续要恢复指标需待包裹族解冻。

## 2026-09-25 · 两处既有死链修复 + U-A/U-B 落地 + ADR v3 回填与复评（C 档修复，主智能体执行；含 R1 口径回填）

**一、既有死链修复（C 档，用户授权「全部修」；非 B7/B8 引入）**：① **`/admin/` 由恒 500 改为 `return 301 /web/`** —— 原 `alias /usr/share/nginx/html/admin/`（容器内该目录**不存在**）+ `try_files $uri $uri/ /admin/index.html` 形成 `rewrite or internal redirection cycle`；一期 PC 端已由 `apps/web` 承接（含只读引用的 9 张一期页面），故 301 至 `/web/`。载体：宿主 `/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`；**改动 1 个既有 location（6 行 → 6 行）**，备份 `nginx.conf.20260925123334`，`nginx -t` 通过后 `-s reload`；**原地改写（`cat >`）保 inode**。② **`/download` 由 404 改为 200** —— **只增宿主 `/data/www/download/index.html`（1773 字节）**：客户端/入口下载页（三端入口 + APK 待发布说明），**未改任何 Nginx 配置**。**验证**：`/admin/`、`/admin/employee`、`/admin/index.html` 均 **301**；`/download` **200**；**回归全不变** —— `/` 200、`/web/` `/staff/` `/boss/` 200、`/health` 200、`/apk/` 200、`/.well-known/` 200、`/api/actuator/health` 403、`/hrm-api/v1/auth/login` 200、**`.map` 仍 404**。**执行期注意**：reload 后新旧 worker 并存，需**复测一次**（首次测得的 `/admin/` 500 为瞬态，日志确认最终为 301）。

**二、U-A 主色达标（前端）**：`hrm-admin` 采纳候选①（对齐 700 档），`tokens.scss` 覆盖 Element 语义色（primary `#0958d9`、success `#237804`、warning `#b45309`、danger `#cf1322`、info `#4b5563` + 30 项浅/深色阶）；`src/**` 字面量**全部收敛**为 `var(--el-*)` / `var(--c-*)` / 真源 `--text-3`（次级文字 `#909399` 3.08:1 → `#6b7280` 4.83:1）；**`index.scss` 承载色值归 0**（R5-3 唯一口径）。**对比度实测全部 PASS**：主按钮 6.16:1、success 5.59、warning 5.02、danger 5.57、info 7.56、次级文字 4.83、dark-2 hover 8.41（均 ≥4.5）；修复前主按钮 2.78:1 FAIL。**A5 断言机器化**：新增 `hrm-admin/scripts/verify-a5.mjs` + `npm run verify:a5` → 白名单 49 值 / 扫描 24 文件 / **未登记字面量 = 0** / `index.scss` 色值 = 0。**`verify:tokens` 现 6 目标全绿**，且 hrm-admin 目标由「0 命中跳过」变为 **30 项实校验**（断言只增不减）。

**三、U-B 最小门禁（前端）**：新增 `hrm-admin/eslint.config.js`（ESLint 9 flat config）+ `package.json` 的 `lint` 与 `verify:a5` 脚本 + 6 项 devDeps 声明（`dev/build/preview` 未动）。**主智能体装依赖后实跑：`npm run lint` = 0 error（无输出即通过）**、`verify:a5` 通过、`npm run build` ✓。口径边界（避免误读）：ADR §7.2 **A-2「不改 `hrm-admin/package.json`」约束的是 Token 消费方式**（相对路径 `@use`、不引 npm 依赖），与 U-B 的门禁投入**不冲突**，本次**无一条依赖与 Token 消费相关**。

**四、ADR v3 回填与复评**：架构师按非主干修订补全 **§3.7 B3/B4/B5/B6 四处不完整回滚点**、回填执行期事实（站点根实落 `/data/www/download/hrm-clients/*` 及其原因、变更载体最终答案 + bind mount 保 inode、`portal.html:31-33` / `portal/main.js:28/35/42` 行号纠正、B8 判定期起点 = 2026-09-25、两处死链处置）、新增「v3 修订记录」。技术评审按 §8④ 复评：**结论等级「有条件通过」**（可报主智能体审批），唯一必改项 **R1 = 口径矛盾**（ADR §6.2 记「本轮已修」而 `deploy.md` §11.5 记「待修」、本条 update-log 原记「未修」）——**本行即为 R1 的事实回填**：两处死链**已修复并验证**（见上「一」），后续 `deploy.md` §11.5 同步为「已修」。

**五、`/web/` 重新部署（C 档）**：因 `hrm-admin/src/styles/**` 变更（`apps/web` 只读引用 `@admin` 样式），按「服务器产物须与工作区一致」重新构建 `apps/web` 并替换线上 `/web/`（剔除 `*.map`，含备份与逐条 curl 验证）。

**遗留**：① 技术评审建议对 ADR v3 新增的**服务器绝对路径 / bind mount 机制**由**网络安全工程师形式核对暴露面**（§9 SP7 口径）——**未派发**，已登记待办（判断依据：均为内网路径、无凭据/域名/IP，且早前形式核对结论为「公开可得信息不构成新增暴露面」，可随时按需执行）；② ADR §7.1 **D5 仍标「待定」**（`update-log` 已登记 D5 = 两 APK），属状态滞后，待下次 ADR 修订一并回填；③ `hrm-admin` 未纳入 CI；`verify:a5` 未接入 workspace 根脚本（结构变更待裁定）；④ 必做-5.2 焦点环（`layout/index.vue:229` 仍 `outline: none`）与四态接入**未在本轮授权范围**，`TODO(扩展)`；⑤ 体验优化批次 A–D 未开工；⑥ **配置与现状缺口（本轮实测登记）**：`/data/www/hrm-config/application-prod.yml` 指向库 **`kdyzgl`**，但宿主 MySQL（`127.0.0.1:3307`，datadir `/data/mysql-host`）**该库不存在**——`show databases` 仅 `information_schema` / `kdyzgl_test` / `performance_schema`；且当前进程以 `--spring.profiles.active=dev` 运行（连 `kdyzgl_test`）。**结论：切 prod profile 前必须先建 `kdyzgl` 库并执行迁移与灌数，否则连接失败**。另：3306 无监听（该机 MySQL 实例唯一，端口 3307）；`/data/mysql`（属主 `lxd`）为**未在运行的旧数据目录**；Redis 为 `courier-redis` 容器（`127.0.0.1:6379`）。

## 2026-09-25 · B8 演示站处置（门户收敛 + 冻结发布纪律 + 文档回填；**旧入口按条件保留**）

**范围裁定**：依 **D1（`hrm-demo` 退役归档）** 与 ADR §3.3 **三项退役触发条件**——① B7 上线后新子路径入口连续 **1 个发布周期**无 P0 回滚；② 旧入口访问量归零或低于运维阈值；③ 旧壳 APK 完成一个发布周期（**任一不满足即顺延，不得静默下线**）。**起点 = B7 上线日 2026-09-25**，当前 **①③ 均未满足** → 本批**不下线**旧入口与 `as` 兼容读，只做：**门户收敛 + 冻结发布 + 文档回填 + 判定期登记**。

**一、门户收敛（用户报障驱动）**：域名根 `/` 由演示容器 `hrm-demo-static` 提供**端选择页**，其按钮原硬编码指向演示站旧入口（`/pc.html`、`/mobile.html?as=station|boss`），致「点驿站精灵却进演示站员工端」。已改两处并保持一致：① 仓库 `hrm-dev/deploy/docker-demo/portal.html`（提交 `04975fd`）→ `/web/` `/staff/` `/boss/`；② `hrm-dev/hrm-demo/src/portal/main.js` **L28/L35/L42** → 同上，同步剔除「已预填账号」的失效 note 文案（`name` 字段逐字未变，e2e 断言对象不受影响）。**线上同步**：宿主 `/data/www/hrm-demo/dist/index.html` 原地覆盖（备份 `index.html.bak.20260925121819`）→ `docker cp` 进演示容器；公网根 `/` 三个 `href` 实测已改，三入口 curl 均 **200**。**持久性提醒**：演示容器**无任何挂载**、门户为**镜像内置**，容器重建会回退，宿主 `dist` 已同步更新（重建即生效）。

**二、冻结发布纪律**：`hrm-dev/deploy/docker-demo/deploy-demo.sh` **头部追加 20 行注释 banner**（`git diff` 实测**仅新增 `+#` 行、未改任何既有逻辑、未加交互确认**）：迁移期冻结演示站发布；根因 = ADR §6.1 **R新-1**（B7「只增不改→零中断」依赖旧入口由演示容器旧产物继续提供，覆盖旧 `dist` 即失效）；例外 = 仅 **P0 修复**且发布后**必须回归** `/mobile.html?as=boss`、`?as=station`；产物快照 = 宿主 `/data/www/hrm-demo/dist`（含 `dist.bak.*`），未重建镜像前不得删。

**三、文档回填**：`hrm-dev/docs/deploy.md` **新增 §11「现网实况与 B7 三端入口」**（约 90 行：三端入口与站点根、**为何是 `/data/www/download/hrm-clients`**、变更载体 + **载体核实方法**（`docker inspect` / `nginx -T`）、**单文件 bind mount 必须原地改写保 inode**、全站 `.map` 404（D 档红线）与缓存头纪律、一键回滚、既有缺陷、B8 判定期），并在 §0.1 加**矛盾标注**（不改写历史结论）；`hrm-dev/docs/project-tree.md` 新增 **§2.1 `hrm-clients/` 多端 workspace 树**与端口/base 表（5191`/web/`、5189`/staff/`、5190`/boss/`）。**均只追加/补表，未重写既有内容。**

**验证（子智能体实跑，主智能体复核关键项）**：`hrm-demo` `verify:mock` **942/942**、`verify:mobile` **48/48**、`build` ✓；三端公网可用（B7 已验）；**Mock 能力由构建模式承接**已确认（三端 `.env.demo` `VITE_MOCK_ENABLED=true` → `build` 含 Mock；`build:prod` 产物无 Mock chunk，B3–B5 逐端验证）。**`npm run test` = 429/430**：1 项失败系**既有墙钟相关 flaky**（`src/mobile/views/staff/attendance/composables/useAttendanceStatus.spec.js:212` 未固定 `att.now`，默认 `windowEnd: '19:00'`，实跑时刻 20:22 已过窗口 → 判 `missed`），**与本次 `portal/main.js` 改动无调用关系**；登记为待测试工程师修（`TODO(扩展)`）。

**事实性纠正（本轮）**：① ADR §5.2 关于 `portal.html` 的记载（「内含硬编码 `/pc.html`、`/mobile.html?as=*`（L30–32）」）**已滞后**，现状已为新入口，行号亦需回填；② ADR §5.4 站点根示例 `/www/wwwroot/hrm-*` 与实落 `/data/www/download/hrm-clients/*` 不符；③ `deploy.md:26`（`/` → `/www/wwwroot/hrm-admin`）与 §0.2/§5.2 同源记载与现网（`/` 由演示容器提供门户）矛盾，已加矛盾标注。

**遗留**：① **规则层 6 文件仍未提交**（`.trae/rules/{项目规则1,智能体调度规则}.md`、`AGENTS.md`、`SESSION-STATE.md`、`全局规则.md`、`智能体配置.md`、`.github/CONTRIBUTING.md`）与 **`.trae/agents/express-station-tech-reviewer/`、`.trae/skills/tech-evaluation/` 未入库**——非本批改动，待用户裁定；② `/admin/` 500 与 `/download` 404 两处**既有死链**未修（载体为 `courier-nginx`，修复属 C 档需授权）；③ ADR §3.7 **B3/B4/B5/B6 四处回滚点均不完整**（漏 `hrm-clients/package.json`、`package-lock.json`、`hrm-demo` 5 文件、`e2e-utils`、flavor 源集、`local.properties.example`），待架构师回填；④ `apps/web` 与 `hrm-demo/src/pc/**` 存在「源-新」同构双份漂移风险，迁移期只改 `apps/web`；⑤ `hrm-demo/src/mobile/**` 员工端与 boss 侧代码仍在，随 `hrm-demo` 终态退役处置。

## 2026-09-25 · B7 发布切换执行（C 档，主智能体执行；三端上线生产主域名 + 预置审核账号）

**授权链**：用户指令「执行 B7，并预留登录账号」；P0.5 安全结论已得（`security-release-switch-review.md`：**有条件放行**，M1 `.map` 为高风险项）；运维手册已出（`deploy-b7-release-switch.md`）；主智能体三步授权表单已出（影响范围 / 可逆性 / 回滚与验证）。

**产物与落点**：三端 `build:prod`（`apps/{web,staff-h5,boss-h5}`）本地重建 → 剔除 `*.map` → 打包 1.02MB → 上传解包至 **宿主 `/data/www/download/hrm-clients/{web,staff,boss}`**（文件数 **167 / 80 / 97**，`map=0`）。**执行期偏差（重要）**：原计划落 `/data/www/hrm-clients`，但实测该路径**在 `courier-nginx` 容器内不可见**（容器仅挂载 `/data/photos`、`/data/www/apk`、`/data/www/download`，**不是** `/data/www` 整树）→ 按手册 V4 预案**不新建挂载**（新建挂载须重建容器、会中断 80/443），改用**已挂载且路径一致**的 `/data/www/download/hrm-clients`；该挂载为 `ro`，只读服务静态资源无影响，且仅 `location = /download` 精确匹配会触达该目录，**不新增暴露面**。

**Nginx 变更（只增不改，载体 = 必改项 6 答案）**：宿主 `/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`（`ro` 挂载进容器）**追加 98 行**（server 级 `location ~* \.map$ { return 404; }` + 三端 `location ^~ /web|/staff|/boss/` 各含 `.map` 局部正则、`^/…/assets/` 长缓存、`\.html$` `no-store`、通用扩展名 `=404` 不回退 HTML、SPA 回退 `/<prefix>/index.html`）；插入锚点 = `location = /hrm-api/v1/work-orders/auto-dispatch` 之前；**未改动任何既有行**。备份 `nginx.conf.20260925121157`。**关键技术点**：单文件 bind mount **必须原地改写（`cat >`）保 inode**，用 `mv` 换文件容器看不到。

**首发缺陷与修正（自捕获）**：首次应用片段时 `.map` 在新前缀内**返回 200**（M1 失效）——根因是嵌套 `location ^~ /<prefix>/assets/` 为**前缀匹配**，命中后**跳过嵌套正则**，`.map` 正则不生效。已按备份还原后用修正版重插（`^/…/assets/` 改为**正则** location，并把 `.map` 正则置于其**之前**）→ 复验三前缀 `.map` 与 `/admin/*.map` **全 404**。

**上线后验证（主智能体实测，双视角）**：服务器侧与**公网侧**均 `/web/` `/staff/` `/boss/` = **200**；`/web/index.html` = 200 且 `Cache-Control: no-store`；`/web/assets/*.js` 真产物 = 200 且 `public, max-age=31536000, immutable`；history 深链 `/web/employee/1`、`/boss/hr/1` = 200；缺失资源 `/web/assets/__nope__.js` = **404（不回退 HTML）**；**.map 一律 404**（含 dummy 文件实测，证明规则对真实存在的 `.map` 亦拦）；80 → **301** HTTPS；**既有 location 逐条不变**（`/` 200 演示站、`/health` 200、`/apk/` 200、`/.well-known/acme-challenge/` 200）；路径穿越实测 `..` 与 `%2e%2e` 均被规范化后由既有 `location /` 接手，**未越界读取站点根外文件**。公网 API 链路 `POST https://kongzhen1.com/hrm-api/v1/auth/login` = 200 且返回业务码 1001（路由与白名单通）。

**预置审核账号（用户要求「预留登录账号」；口令服务端生成，不落仓库/文档/对话）**：写入服务器 **`/data/hrm-tmp/review-accounts-20260925121508.txt`（`chmod 600`, `root:root`）**，请用户自行 SSH/宝塔查看。账号：`16626369983`（ADMIN → `/web/` 与 `/boss/`）、`st001_staff`（STAFF → `/staff/`）；两者口令已重置为服务端随机值，`pwd_changed=1`（免首登强制改密）。**登录实测**：ADMIN×WEB / ADMIN×BOSS / STAFF×STAFF 均 **200 + token**；**STAFF×WEB = 1110「该账号无权登录此端」**（端准入 fail-closed 生效）。

**既有缺陷登记（非本次引入）**：① `/admin/` = **500**，根因 `alias /usr/share/nginx/html/admin/` + `try_files` 形成内部重定向环，且容器内 `/usr/share/nginx/html/admin` **目录不存在**（`docker logs courier-nginx` 报 `rewrite or internal redirection cycle`）；② `/download` = 404（`/data/www/download/index.html` 不存在）；③ 全站 `.map` **原本无任何拦截**（本次已补）。**变更载体与 V4 处置已闭环，可供 ADR §11 回填。**

**回滚（一键，已验证备份可用）**：`cat /data/www/kdyzzhxt/courier-server/nginx/nginx.conf.20260925121157 > /data/www/kdyzzhxt/courier-server/nginx/nginx.conf && docker exec courier-nginx nginx -t && docker exec courier-nginx nginx -s reload`，随后删除 `/data/www/download/hrm-clients` 即回到变更前状态（无数据变更、无镜像/容器重建）。

**遗留**：① **B2–B6 源码仍未提交**（`apps/*`、`hrm-android-shell`、docs 等），**上线产物当前无源码锚点**，回滚缺 git 依据——待用户授权即分组提交；② `deploy.md` 中 `/` → `/www/wwwroot/hrm-admin` 的记载与现网（`/` → 演示容器）矛盾，且未记 B7 新入口，待同步；③ ADR §3.7 B3/B4/B5/B6 的「回滚点」四处均不完整（漏 `hrm-clients/package.json`、`package-lock.json`、`hrm-demo` 5 文件、`e2e-utils`、flavor 源集、`local.properties.example` 等），待架构师回填；④ `/admin/` 与 `/download` 两处既有死链待修（未变更载体）；⑤ **B8 演示站处置**未开工（当前域名根 `/` 仍为演示站）。

## 2026-09-25 · B6 安卓壳「两个 APK」改造（前端，静态审查，未编译未运行）

**产出（仅改 `hrm-dev/hrm-android-shell/`，7 文件）**：`app/build.gradle`（48→99 行）新增 `flavorDimensions 'brand'` + `productFlavors{staff,boss}`，**移除 `buildTypes` 内硬编码 `H5_URL`**，改由 `androidComponents.onVariants` 单点注入（避免 flavor/buildType 同名 `buildConfigField` 的覆盖语义不确定性）；**新增** `app/src/staff/res/values/strings.xml`（应用名「驿站助手」）与 `app/src/boss/res/values/strings.xml`（「驿站精灵」）；`local.properties.example` 增 4 个 `h5Url*` 占位样例；`README.md`/`BUILD.md` 同步两 APK 形态与四值表。**取「同一 app module + 双 flavor」**（非两 module）：两壳共用同一份 Java（`MainActivity`/`HrmJsBridge`），桥接契约天然一致；`2 flavor × 2 buildType` = **4 个构建变体**，但**交付物仅 2 个** —— `app-staff-release.apk`（驿站助手壳）/ `app-boss-release.apk`（驿站精灵壳）；另 2 个 debug 变体（`app-{staff,boss}-debug.apk`，H5 指向模拟器地址）**仅本地联调、不进入分发**。两壳 `applicationId` 分别为 `com.example.hrmwebview.staff` / `.boss`（**占位**，同机可并存）。

**主智能体独立静态复核**：`hrm-android-shell` 改动集仅上述 7 文件（`git status` 实测，**Java 侧 `HrmJsBridge.java`/`MainActivity.java`/`AndroidManifest.xml`/`themes.xml` 均未改动** → 桥接方法名逐字未变）；`applicationId` 覆盖行与 `app_name` 分端取值已复现；全壳检索 `kongzhen1` = **0 命中**（真实域名/IP 零入库）；`local.properties` **不存在于仓库**（仅 `local.properties.example`，且值全为占位）。

**`H5_URL` 四取值表（B-5 统一子路径）**：`staffDebug` = `http://10.0.2.2:5189/staff/`、`staffRelease` = `https://example.invalid/staff/`、`bossDebug` = `http://10.0.2.2:5190/boss/`、`bossRelease` = `https://example.invalid/boss/`；优先级 `-P 构建参数 > local.properties（不入库）> 占位默认`；键名 `h5Url{Staff,Boss}{Debug,Release}`。

**B6 验收（静态，逐条达标）**：① `H5_URL` 无硬编码真实域名（14 处 URL 命中全为 `example.invalid` 占位 / `10.0.2.2` 模拟器约定地址 / `maven.google.com` 官方源引用 / `schemas.android.com` XML 命名空间，**真实域名=0**）；② 应用名与包名区分为**占位**（`build.gradle:59/63`、两端 `strings.xml:4`）；③ 桥接方法名未变（对照清单：`HrmBridge` 注入名 + `getDeviceInfo`/`setStatusBarStyle`/`toast`/`close` + `window.HrmShell.onBackPressed`/`onResume`/`setStatusBarHeight`，**无新增/无删除/无改名**）。

**运行期验收声明**：壳加载 / 桥接通 / HTTPS / 混合内容 / WebView 缩放缓存 / 4 变体是否真出 4 APK —— **全部未运行**（本机无 JDK / Android SDK / Gradle），**收敛到具备 Android SDK 的构建环境**；**本批次不声称可交付**。

**回滚点（ADR §3.7 B6 原写「还原 `build.gradle` 与壳配置（反向 diff）」，实测不完整——已补全，与 B3/B4 同源缺陷）**：还原 `app/build.gradle`（恢复 `buildTypes` 内两处 `H5_URL`）→ **删除 `app/src/staff/`、`app/src/boss/`** → 还原 `app/src/main/res/values/strings.xml`、`local.properties.example`、`README.md`、`BUILD.md`。完全可逆、未发布、无线上影响。

**遗留与登记**：① **桥接契约缺 `getWifiInfo`**——三端 `bridge.js:52` 均 `call('getWifiInfo')`，但 `HrmJsBridge.java` 仅暴露 4 个方法（`:55/70/86/92`）**无此方法** → 壳内 WiFi 读取亦恒走 `mock:true`，与 ADR §3.4 隐含语义不符；本批按「桥接零改动」**未补**，登记遗留；② **待用户裁定**：真实包名（Q13）、两壳图标（Q14，当前用系统默认，U-A 未裁定 → 禁视觉分叉）、`setStatusBarStyle` 的 `dark` 语义（Q11）；③ **AGP `onVariants` 注入语法未编译验证**（Q15）；④ **U-3 未闭环**（`MainActivity` 未显式配置 WebView 缩放/缓存，保持系统默认）。

## 2026-09-25 · B5 拆出 `apps/web`（网页端/PC 端）落地（前端，未触生产）

**产出**：新建独立工程 `hrm-dev/hrm-clients/apps/web/`（**191 文件 / 26861 行**；dev 端口 **5191**，`base:'/web/'` + `createWebHistory(import.meta.env.BASE_URL)`；`src/{api(16),components(12),config/menu,constants,brand,layout,router(仅 PC 路由),stores(auth 端固定 WEB + org),styles(tokens.scss + element-overrides),utils(csv/department/format/payrollPreview),views(15 域)}`）。**请求层统一**：自有 `src/api/**` 全部经 `@/utils/http.js` → `@kdyzgl/api-client` 的 `createHttp`（消解「两套请求实现」）；`@admin` 引用仅 5 类只读（`styles/index.scss`、`utils/request`（仅 Mock 装配）、`utils/download`、`views/**` 9 张一期页面路由、单测 mock），**零修改 `hrm-admin`**（29 文件 mtime 早于本批开工）。**改动（apps 外）**：`hrm-clients/package.json`（`verify:tokens` +web 目标、新增 `e2e:web`）+ `package-lock.json`（+44 包，element-plus 等）。

**主智能体独立复跑（非采信回报）**：`apps/web` **191 文件**、`build:prod` ✓ 且 `dist/assets` 内 `install|db-|mock` 命中 **0**（**无 Mock chunk**）；workspace `verify:tokens` **6 目标全绿**、`verify:mock` **942/942**；**跨端源码复制 = 0**（`apps/web/src` 检索 `views/staff|modules/boss|apps/staff-h5|apps/boss-h5` 的 import → **0 命中**）。子智能体另报：`build` ✓ / `lint` 0 error/69 warn / `lint:style` 0 / `test` 105/105 / **`e2e` 20 passed**（含 `02-pc-nav` 全量、`01-load` A1-2/A1-3/A1-7、`04-forms` A4-4、`05-render` A5-1~4、`07-network` B2-1/B2-2、`00-dev-server`、`06-viewport` B1-1）—— **未由主智能体亲跑**。ADR §3.7 B5 五条验收断言逐条达标；现有工程回归未破（`hrm-demo` 942/48、staff 36、boss 20）。

**回滚点（ADR §3.7 B5 原写「删除 `apps/web`」，实测不完整——已纠正）**：① 删除 `apps/web/`；② **还原 `hrm-clients/package.json`**（去 web 目标与 `e2e:web`）；③ **还原 `hrm-clients/package-lock.json`**（+44 包）；④ 可选 `npm ci` 清理 workspace 依赖。无需还原 `hrm-demo`/`hrm-admin`/Nginx（零改动、线上零影响）。

**遗留与登记**：① `apps/web` 与 `hrm-demo/src/pc/**` 形成**「源-新」同构双份**（R10 漂移风险）——迁移期须**只改 `apps/web`**，`hrm-demo` 仅 P0 修复，待 **B8** 退役；② `@admin/views/**` 9 张一期页面为**只读引用**，其落点收敛须**另立 ADR**（D2 长期方案）；③ `src/utils/authStorage.js` 键名切 `hrm:web:*` 顺延至 D2 收敛（**§3.5 #2「键名规范化」与 D2「保只读引用」存在冲突**，登记为需架构裁定项）；④ `TODO(扩展)`：`vite.config.js` 的 `optimizeDeps.include` 增量维护、存量「组件直连 api」由 warn 升 error、`src/api/*` 二期契约对齐；⑤ **事实性纠正**：`07-network` B2-1 被测对象原属**演示站端选择页**，`apps/web` 无此页，已按「被测端归位」改为网页端官方入口 `/web/`（断言口径不变）；`verify:tokens` 目标数由文档所记 2 已增至 **6**（基线未随 B3/B4/B5 更新，非弱化）；`split-plan §5.1 S2` 记 `base '/'` 与 ADR `B-5` 子路径裁定**冲突**，本批按 ADR 取 `/web/`，split-plan S2 措辞应作废。

## 2026-09-25 · B4 拆出 `apps/boss-h5`（驿站精灵 · 管理端 H5）落地 + `/boss/kpi` 跨域直引消解（前端，未触生产）

**产出**：新建独立工程 `hrm-dev/hrm-clients/apps/boss-h5/`（**134 文件 / 19047 行**；dev 端口 **5190**，`base:'/boss/'`；`src/{api(13 端点壳),components(29),composables,constants,demo,layout,modules/boss(28 页),router,stores(端固定 BOSS),styles,utils,views}`；`index.html` 独立入口 + `<title>驿站精灵</title>` + 独立 favicon）。**真消费共享包**：`@kdyzgl/shared`（47 处 import）、`@kdyzgl/api-client`（`createHttp({clientType:'BOSS'})`）、`@kdyzgl/mock`（动态 install）、`@kdyzgl/tokens`（`tokens.scss` 相对 `@use` 真源）；**无 `src/shared/**` 副本**。**B-3 核心落地**：`/boss/kpi/:employeeId` → `views/kpi/index.vue` 薄容器 → `@kdyzgl/shared/ui/KpiDetail.vue` + **props 注入**（`detail/month/loading/error/title/is-boss-view`），中立页不 import `stores/`/`api`/`mock`。**改动（apps 外）**：`hrm-clients/package.json`（`verify:tokens` 目标 +`apps/boss-h5/src/styles/tokens.scss`；新增 `e2e:boss`）+ `package-lock.json`。

**主智能体独立复跑（非采信回报）**：`apps/boss-h5` `verify:mobile` **20/20**（= 裁定下限 20）、`build:prod` ✓ 且 `dist/assets` 内 `install|db-|mock` 命中 **0**（**无 Mock chunk**）；workspace `verify:tokens` **扫描 5 个目标全部通过**；**跨域直引残留 = 0**（`apps/boss-h5/src` 检索 `from '...views/staff|modules/staff|src/shared|@admin|hrm-demo'` → **0 命中**）；`hrm-demo` **未被改坏**（`verify:mobile` **48/48**）。子智能体另报：boss `lint` 0 error/27 warn、`lint:style` 0、`test` 107/107、`e2e` **12 passed**、`build` ✓、端语义矩阵 `BOSS+as=boss→200` / `H5+as=boss→200`（兼容读）/ 缺省→`1110` / 异端→`1110` —— **未由主智能体亲跑**。**两端合计 36 + 20 = 56 ≥ 48**（冻结基线满足）。三条验收断言（ADR §3.7 B4 ①②③）逐条达标。

**回滚点（ADR §3.7 B4 仅写「删除 `apps/boss-h5`」，实测不完整——已纠正，与 B3 同源缺陷）**：① 删除 `apps/boss-h5/`；② **还原 `hrm-clients/package.json`**（去掉 verify:tokens 的 boss 目标与 `e2e:boss`）+ `package-lock.json`。本批**未改** `hrm-demo` / `apps/staff-h5` / `e2e-utils`（与 B3 不同，无 hrm-demo 反向 diff）。**线上零影响**。

**遗留与登记**：① `hrm-demo/src/mobile/modules/boss/**`（28 文件）与 `mobile.html#/login?as=boss` 旧入口**原样保留**，待 **B8** 与 `hrm-demo` 一并退役；② 新增 3 个 boss 自有页（`workorderDetail` / `parcelDetail` / `password`），为消除 `/staff/*` 路由串而各持一份，`TODO(扩展)` 是否上移中立包待 UI/UX + 架构裁定；③ **共享 UI 清单双向漂移仍未冻结**（ADR §3.5 #16，须 UI/UX 先行）；④ `tokens.scss` 仍相对路径 `@use`（同 staff 遗留）；⑤ **U-A 未裁定 → 未做视觉分叉**（两端共用同源 Token，仅品牌常量/应用名/favicon/`<title>` 独立）；⑥ `dist/*.map` 为 `sourcemap:'hidden'` 产物（51 个），**B7 上线须在 Nginx 侧拒绝公开下载**；⑦ **B1 验收② 仍未闭环**（`hrm-demo` 不消费 `@kdyzgl/*`）；⑧ 对照表 `A3-5` 在管理端语义退化，已等价改写为「管理端只列管理员」并注明原因（**非弱化**）。

## 2026-09-25 · 用户授权推进 ADR 全量 + 三项裁定补登（主智能体）

**用户指令（2026-09-25）**：确认**无其它并发会话**在本工作区写入（5189 残留 dev server 与既有 B3 主体均判为历史遗留，非并发写者）；授权**继续推进并完成 ADR 全部批次**，**完成后部署到生产主域名供用户审核**。

**据此补登三项裁定（采 ADR §7.1「本 ADR 建议」口径，用户「按你的建议」授权）**：**D5 安卓壳产物形态 = 两个 APK**（`com.example.hrmwebview.boss` / `.staff` 占位包名；解锁 **B6**）；**D2 `apps/web` 与一期 `hrm-admin` 关系 = 短期保留 `@admin` 只读引用、长期收敛另立 ADR**（解锁 **B5**）；**D1 `hrm-demo` 迁移后处置 = 退役归档、演示能力由构建模式承接**（解锁 **B8**）。

**仍待用户裁定（未裁定前对应影响面不得实施）**：**U-A**（两端品牌视觉是否分叉，含 `hrm-admin` 生产端主色变更——本批上线**不含**该换色，保持现状）、**U-B**（`hrm-admin` 是否补最小门禁）。

**B7 上线路径（C 档，用户已授权目标但未授权执行）**：按 §3.7 B7 与 P0.5/L7 —— ① **网络安全工程师先出结论**（必改项 6 的现网 Nginx 变更载体须由**运维核实并写明**）；② 主智能体 **三步授权表单**（影响范围与是否涉生产数据 / 是否可逆 / 回滚步骤与验证方法）；③ 运维工程师执行，策略**只增不改**（新增 `location /web/` `/staff/` `/boss/`，不动 `location = /` 与 `location /` 及既有全部 location）；④ 切换期观测判据任一命中即回滚；⑤ 上线后交**用户审核**。**禁**在服务器改业务源码、**禁** `push --force`/`reset --hard`/`clean -f`。

## 2026-09-25 · B3 拆出 `apps/staff-h5`（驿站助手 · 员工端）落地 + 中立共享页提升（前端，未触生产）

**产出**：新建独立工程 `hrm-dev/hrm-clients/apps/staff-h5/`（**交付源文件 155 个**，含 `package.json` / `index.html` / `vite.config.js` / `vitest.config.mjs` / `eslint.config.js` / `stylelint.config.cjs` / `.env.demo` / `.env.production` / `scripts/verify-mobile.mjs` / `e2e/{00-dev-server,01-load,03-mobile-nav,04-forms,05-render,06-viewport,07-network}` + `src/{api(13 端点壳),components,composables,constants,demo,layout,router(仅员工域),stores(auth 端固定 STAFF),styles,utils,views(21 页)}`）；dev 端口 **5189**。**中立共享页提升（B-3，本批落地）**：`packages/shared/src/ui/`（13 文件，含 `MessagePage.vue` / `NoticeReader.vue` / `KpiDetail.vue`，数据一律 props 注入、不 import `stores/`/`api/`/mock）；`hrm-demo` 侧**最小改动** 5 文件 + `vite.config.js` 别名改引中立页（`src/mobile/views/staff/kpi.vue` 已删除并改为 `src/mobile/views/kpi/`）。**e2e 单点**：新建 `hrm-clients/e2e-utils/harness.js`（workspace 根单点，D10 口径）。

**主智能体独立复跑（非采信回报）**：`apps/staff-h5` `verify:mobile` **36/36**（= 裁定下限 36）、`build:prod` ✓ 且 `dist/assets` 内 `install|db-|mock` 命中 **0**（**无 Mock chunk**）；`hrm-demo` **未被改坏** —— `verify:mock` **942/942**、`verify:mobile` **48/48**。子智能体另报：staff `lint` 0 error/15 warn、`lint:style` 0、`test` 232/232、`e2e` **18 passed**（修复前 3 passed/15 failed）、`build` ✓、workspace `verify:mock` 942、全仓 `tokens.base.scss` 计数 = 1 —— 上述**未由主智能体亲跑**（标注为子智能体实跑证据）。五条验收断言（ADR §3.7 B3 ①–⑤）逐条达标，其中 ③ 端语义矩阵实跑（`STAFF→200`、`H5+as=station→200`、缺省→1110、`ADMIN` 登员工端→1110）、④ 静态检索（`apps/staff-h5` 无 `/boss` 路由分支、无 `src/shared` 副本、`packages/**` 不依赖任何端且无 element-plus/vant）、⑤ 并存不互踢（`hrm:staff:*` 与 `hrm_demo_mobile_*` **token/user 键零交集**，双向重载均未被踢）。

**回滚点（ADR §3.7 仅写「删除 `apps/staff-h5`」，实测不完整——已纠正）**：① 删除 `apps/staff-h5/`；② **还原 `hrm-demo` 5 个文件**（`src/mobile/views/message/{MessagePage,NoticeReader}.vue`、`src/mobile/router/index.js`、`vite.config.js`、`vitest.config.mjs`）+ 复原 `src/mobile/views/staff/kpi.vue`；③ 还原 `hrm-clients/e2e-utils/harness.js`。**线上零影响**（Nginx 未改、`mobile.html` 旧入口未动、无发布动作）；禁 `reset --hard`/`clean -f`/`push --force`。

**遗留与登记**：① **并发写入风险（M04 / A18 / A21）**——接手时工作区**已存在 B3 主体**（含 `dist/` 与一轮 `3 passed / 15 failed` 的 e2e 证据），且 **5189 端口存在 18:08 起的遗留 dev server（PID 5200）**，研判为**另一并发会话在同一工作区写入**；按 M04 单一写者纪律，主智能体已**停手回报用户**确认，**未提交任何内容**；② 因同一原因 **`SESSION-STATE.md` 检查点未追加**（该文件在并发会话在途改动中，避免互相覆盖）；③ `hrm-demo` 侧 e2e 登录系用例（A1-4/A1-5 等）**同因「新设备二次验证」改造而失败**，属**跨端既有缺陷**（非 B3 引入），本批按禁令未动其 spec，建议单独立项或随 B8 退役处置；④ 共享 UI 清单双向漂移（`TodoGroup`/`TodoList` 已在共享包而 split-plan §216 列为端专属；`Badge`/`Chip`/`MiniChip`/`ListItemCard`/`StatCard` 列为候选共享却仍在端内）→ **须 UI/UX 先冻结清单**（ADR §3.5 #16），本批未动；⑤ `apps/staff-h5/src/styles/tokens.scss` 仍用**相对路径** `@use` 真源（未改包名消费，未验证 Vite+Sass 解析行为）；⑥ `apps/staff-h5/e2e/_debug-login.mjs` 为排障残留（删文件属 C 档，**未删**，待裁定）；⑦ `hrm-demo` 仍**未消费 `@kdyzgl/*`**（B1 验收② 未闭环，B3 经 `vite.config.js` 只读别名消费真源），不阻塞门禁但属契约性缺口。

## 2026-09-25 · B2 R-0 真源上移落地 + 断言清单对照表 + 三项裁定（前端 / 测试 / 主智能体）

**B2 执行（只改 `hrm-clients` + `hrm-demo` + `hrm-admin`，未触生产）**：①**真源唯一化**——`tokens.base.scss` 全仓计数 **= 1**（仅 `hrm-clients/packages/tokens/src/`），删 `hrm-demo/src/shared/styles/tokens.base.scss` 副本（删除前与真源 SHA256 逐字一致）；②**脚本提升**——`hrm-demo/scripts/gen-element-tokens.mjs` 删除、收敛为 `packages/tokens/scripts/gen-element-tokens.mjs`（单目标 → `--targets` **多目标数组**，新增「Token 真源 / 扫描目标[N] 绝对路径」表头以满足 A6），`hrm-clients` 与 `hrm-demo` 的 `verify:tokens` 分别改三目标 / 双目标；③**`hrm-admin` 接入（A-2 取 ①）**——**新建** `hrm-admin/src/styles/tokens.scss`（`@use` 相对路径消费真源 + `:root` 登记 18 个现状字面量白名单），`src/styles/index.scss` 加 `@use './tokens.scss'`，**未改 `package.json`、未引 npm 依赖、未改任何现有视觉取值**（A5：字面量 35 处 / 白名单 18 值 / **未登记 = 0**）；④**A1 = 0 / A2 = 0**（A2 原 5 处命中系 `packages/*` 内注释含 `hrm-demo`/`@admin` 字样，已改注释、语义不变）。

**A4 删除演练（四步，主智能体授权窗口内）**：`hrm-demo` 移出工作区 → `hrm-clients` / `hrm-admin` 清 `node_modules` + 清 `dist` 后重装并 `build` / `build:prod` → **产物级断言**：`hrm-admin/dist` 内 `hrm-demo` 命中 **0**、Mock 命名产物 **0**，真源消费证据（admin CSS 含 `--c-blue-700`）成立 → **目录恢复**（文件数 **34225 = 演练前**，`package.json` / `pc/tokens.scss` / `mobile/tokens.scss` 关键哈希逐位一致）。`apps/*` 尚未存在，A4(b)(c) 的 apps 部分与「admin 无 build:prod/无 Mock」**收敛到 B3/B4/B5**；演练期间未提交任何中间态。**遗留（非仓库内）**：演练副本残留 `D:\kdyzgl-b2-drill\hrm-demo`（34225 文件），工具沙箱**禁止对工作区外路径执行删除**，须由用户手工 `Remove-Item 'D:\kdyzgl-b2-drill' -Recurse -Force`；此处不清理存在被误当工作区打开、致 TRAE 记忆分桶漂移的风险。

**门禁实跑（Node 可用，均已真跑）**：`hrm-demo` — `verify:mock` **942/942**、`verify:mobile` **48/48**、`verify:tokens` 绿（2 目标）、`build` ✓、`build:prod` ✓、`lint` 0 error/41 warn（既有）、`test` 430/430；`lint:style` **2 error 为既有问题**（`mobile/views/login/index.vue:475`、`pc/views/login/index.vue:425`，规则 `comment-empty-line-before`），按 A06 **归因登记、未改断言**。`hrm-clients` — `verify:mock` 942/942、`verify:tokens` 绿（3 目标）；该工程本无 build/lint/test/verify:mobile 脚本（既有）。`hrm-admin` — `build` ✓（该工程无 build:prod）。

**对照表（B3/B4 开工硬门禁，已解除）**：`test-cases.md` **追加 130 行**新增「拆分前后断言清单对照表」，**48 条逐条**列归属端 + 新计数 + 脚本行号定位。结论：`apps/staff-h5`（员工端）自有 28 + 共用 8；`apps/boss-h5`（管理端）自有 12 + 共用 8；**合计 56 ≥ 48**。

**主智能体裁定（补 U-2 / U-4 / U-6 书面载体）**：`verify:mock` 冻结基线 **= 942**（split-plan 所记 919 为**过时值**，以实测为准）；`verify:mobile` 冻结基线 **= 48**；**分端下限：`apps/staff-h5` ≥ 36、`apps/boss-h5` ≥ 20，两端合计 ≥ 48**（实测 56，**只增不减**）；8 条端无关断言（Mock 数据层类）按「**两端各持一份**」计；PC 用例（`01-load` A1-2/A1-3/A1-7、`04-forms` A4-4、`05-render` A5-1~A5-4、`07-network` B2-1/B2-2）**归 `apps/web`（B5）**、不计入 staff/boss 下限；`e2e/utils` 落点取 **workspace 根单点**（符合 D10「跨端门禁单点」）；**U-4 端口实测 5189（staff）/ 5190（boss）均空闲**，沿用 ADR 建议。另观察：本机 `8081` / `3307` 当前**无监听**（后端与宿主 MySQL 未在跑）。

**事实性纠正（重要）**：**B1 验收②未闭环**——`hrm-demo/package.json` **无任何 `@kdyzgl/*` 依赖**、`hrm-demo/src` 零 `@kdyzgl` 引用，且 `hrm-demo/src/shared/{mock,domain,constants}` 副本仍在，即 **B1 只建包、未接线**（B1 的门禁全绿是在「hrm-demo 未消费共享包」前提下取得的）。B2 已用与 A-2 同构的**相对路径 `@use`** 完成 Token 侧接线；**`packages/{shared,api-client,mock}` 的真正消费须在 B3/B4 建端时落实**，否则共享包形同虚设。B2 另纠正两处任务描述与实测不符：`hrm-admin/src` 十六进制字面量为 **35 处 / 18 个唯一值**（非任务所列 10 处）；`hrm-clients` 无 build/lint/test 脚本、`hrm-admin` 无 `build:prod`。**环境事实**：存在僵尸 `vite --mode demo`（起于 2026-09-23）占用 `hrm-demo`，已停止（**如需本地预览须重跑 `npm run dev`**）。

## 2026-09-25 · B0 裁定冻结登记（主智能体）——结构迁移批次开工前置

按 `adr-structure-migration.md` §3.7 **B0** 与 §7.2 要求登记裁定结论，**B0 冻结即日生效**；未裁定项对应批次**不得开工**。

**已裁定（主智能体，依据 §7.2）**：D3 仓库粒度＝方案 A（workspace 单仓多工程）；D6 共享包边界＝`tokens`+`shared`+`api-client`+`mock` 四包；**A-2** `hrm-admin` 消费真源取 **① 相对路径 `@use`**（不改 `package.json`、不引 npm 依赖）；**A-4** ③（admin 局部覆盖）→② 收敛期限**不得晚于 B2 完成**；**B-3** `MessagePage` / `NoticeReader` / `views/staff/kpi.vue` **提升为 `packages/shared/ui` 中立页**（**B3 落地**；`/boss/kpi` 改引中立页 + **props 注入**，**B4 验收「跨域直引残留 = 0」**）；**B-4** `as` 参数**保留 ≥1 个发布周期**，按 §3.3 三项触发条件于 **B8** 判定退役（不得无限期保留）；**B-5** 迁移期与终态**统一子路径** `/web/` `/staff/` `/boss/`。**已闭环**：D8（生产 API 基址 `/hrm-api/v1`，仅需运维核实现网 Nginx 前缀一致）、D9（后端多端会话已实现，**B3 硬前置撤除**，B3 仍须回归「两端并存不互踢」）、D10（e2e/verify 按端各持一份 + 跨端门禁单点）。

**仍待用户（非主智能体）裁定 —— 未裁定前对应影响面不得实施**：**D5**（两 APK vs 单壳双入口 → 阻塞 **B6**）、**D1**（`hrm-demo` 处置 → 阻塞 **B8**）、**D2**（`apps/web` 与一期 `hrm-admin` 关系 → 阻塞 **B5** 终态收敛）、**U-A**（两端品牌视觉是否分叉，含 `hrm-admin` 生产端主色变更 → 阻塞**批次 A 的 admin 换色**）、**U-B**（`hrm-admin` 是否补最小门禁 → 工程投入决策）。

**冻结后的开工顺序**（§3.7）：B2（R-0 真源上移）→ B3（拆 `apps/staff-h5` 驿站助手）→ B4（拆 `apps/boss-h5` 驿站精灵）→ B5/B6/B7/B8。**B3/B4 另受 ADR §3.6 硬门禁约束：「拆分前后断言清单对照表」未出表不得开工**（必改项 11）。

## 2026-09-25 · 结构迁移 ADR v2 复评「通过」与真实域名形式核对（评估类，无业务代码改动）

对 `adr-structure-migration.md` **v2** 执行 P0.6 / L8 复评（技术评审工程师，与产出方分离），**只追加**一节至 `tech-review-structure-migration.md`（+53 行，L317–366），既有内容零改动。**复评范围冻结**：上一轮报告 §7 所列必改项 **2 / 3 / 4 / 5 / 7 / 8 / 9 / 11 逐条核对**，每条给出「验收标准要点 → ADR v2 行号 + 原文摘录 → 独立重跑静态检索」三段证据，**8/8 已闭环**，未命中打回红线（A4 已升级为四步可复核断言、真实域名已处置并转交、B7 变更载体已三分、版本绑定声明齐备）→ **结论等级：通过，具备报主智能体审批资格**。必改项 **1 / 6 / 10** 已改为**可判定表述**，仍为 **B2 / B7 / B6 批次开工前置**（执行阶段动作，不纳入本次报审判定）。**本轮自我更正 1 处**：上一轮报告称「`hrm-admin/src` 无 `#409EFF`」**实测不符**——实有 `#409eff` 4 处、`#909399` 6 处；ADR v2 L34 计数正确，已以本轮实测为准。

同批按必改项 2 第二半**转交网络安全工程师**形式核对，**新建** `security-structure-migration-review.md`（183 行）：ADR v2 全文 `kongzhen1` / `.com` / `http://` / IPv4 / `password|secret` **命中 = 0**，`https://` 唯一命中为 `example.invalid` 占位；**结论：文档层出现生产域名不构成新增暴露面（域名公开可得、不与凭据组合、无攻击链），风险低，安全维度不阻塞报审**；报告内不落任何真实值。**登记上游一致性问题 3 项（不挂本 ADR 报审闸门，另立任务）**：① 技术评审报告自身（L20/L129/L269）含真实域名；② `multi-client-split-plan.md` §2.4 称「域名/IP 不落本文」而同文件 L80/L91/L103 却落；③ 必改项 2 所引 v1 行号已漂移（v2 为 L264/L439）。全仓另有 9 份文档含真实域名、2 份含生产 IP（既有状态，非本 ADR 引入）。**本批仅 3 个文档文件变动**（评审报告追加、安全核对报告新建、`ui-experience-optimization.md` v1.1），**未改任何源码 / 契约 / 规则 / 迁移脚本**，未执行 git 操作。

## 2026-09-25 · 修复「同账期重复生成工资单不幂等」缺陷（后端，只改 hrm-server + 本行）

**缺陷**：同账期连调两次 `POST /api/v1/finance/payrolls/generate`，`payroll` 物理行翻倍（2026-10 两次 → 126 行 = 63 × 2，`COUNT(DISTINCT employee_id)=63`），即「DRAFT 覆盖重建」未生效；非草稿路径 9405 正常。

**根因**：`PayrollServiceImpl.deleteExisting`（原 `:602-617`）用 `payrollMapper.delete(wrapper)` / `payrollItemMapper.delete(wrapper)` 删除旧单，而 `Payroll`/`PayrollItem` 均标注 `@TableLogic`（`Payroll.java:106`、`PayrollItem.java:51`）且 `application.yml:65-67` 全局开启 `logic-delete-field=is_deleted` → 该删除实为 `UPDATE ... SET is_deleted=1`，**旧行物理残留**，故重跑只增不减，与方案 §8.2 N2「覆盖重建同结果」不符。另 `idx_payroll_payroll_no` 为非唯一索引（`V8__payroll.sql:84`），故同号重复插入不报错、缺陷被掩盖。

**修复**（不改 HTTP 契约、不动 9405 语义）：新增物理删除与覆盖范围查询的自定义 SQL（单表 DELETE 文法，规避 MySQL 多表 DELETE 的 `No database selected`）——`PayrollMapper.selectRebuildTargetIds`（含绕过逻辑删除的残留行，**仅取 DRAFT/REJECTED**）、`PayrollMapper.deletePhysicallyByIds`、`PayrollItemMapper.deletePhysicallyByPayrollIds`；`PayrollServiceImpl.deleteExisting` 改为「先物理删明细、再物理删主单」，范围与 `PayrollGenerateGuard.editableStatuses()` 一致（已提交/已发布单据不取不删），同范围历史逻辑删除残留一并清除，使重跑后 `COUNT(*) == COUNT(DISTINCT employee_id)`。**未触碰**已发布路径、`PayrollLockQueryService` 账期锁交互、旧按天路径（`shiftModelFromMonth` 之前账期走 `PayrollContextProvider` 旧分支，本改不涉）、迁移脚本/种子脚本、`hrm-admin`/`hrm-demo`/`deploy`/其它 `docs/` 正文/`.trae/rules/`/`SESSION-STATE.md`。

**单测**（新增 `PayrollServiceImplGenerateIdempotencyTest`，Mockito + 带逻辑删除语义的内存仓库）：①连续两次生成 → 物理行数 == 员工数、(employee_id,month) 唯一、两次指纹（净额+明细 key/金额/文案）逐位一致、每单明细数==启用项数、无孤儿；②PENDING_APPROVAL/APPROVED/PUBLISHED/CONFIRMED → 9405 且零删除零新增（不回归）；③混合场景（部分员工有旧草稿、部分无）→ 旧草稿被物理替换、旧明细不残留；④逻辑删除残留被清理（复现缺陷现场）；⑤逻辑删除的 PUBLISHED 残留不被误删（范围仅 DRAFT/REJECTED）。

**本机无 JDK/Maven，未编译未单测**，静态自审（导入/注解/构造器参数序/类型推断）通过，**收敛到服务器阶段**（验证命令见回报）。**事实性纠正**：方案 §8.2 N2 引用行号 `PayrollServiceImpl.java:171-176` 与 v2.0 重评核对的 `:154-163` 均随本次改行号漂移，正文未改（属既有正文，另需主智能体回填）。**建议（交数据库工程师，非本次）**：`payroll` 的「活跃唯一」仅靠 Service 查重，`idx_payroll_payroll_no` 非唯一；是否加「活跃唯一」约束属表结构变更（C 档），登记不实施。

## 2026-09-25 · S2b 班次制算薪实现（后端，缺勤/请假粒度「天 → 班次」）

按 `algo-payroll-shift.md` v2.0（技术评审「有条件通过」+ 主智能体批准）落地，**只改 `hrm-server`** + 本行 + `db.md` 取值集合同步。**新增 2 类**：`service/finance/support/ShiftPayrollPolicy`（纯逻辑：班次单元 `epochDay×2+序号`、`R/A/L` 集合运算、三态优先级、折算与逐级封顶链）、`service/finance/support/ProratedItemResolver`（新来源 `PRORATED`：`basicSalary × |A∩R| ÷ |R|`，`|R|=0` 走 `zeroSchedulePolicy=FULL_BASIC`）。**改动**：`PayrollSource`（+`PRORATED("出勤折算")`）、`PayrollRuleValidator`（白名单与错误文案 +PRORATED，不改则规则项保存 400）、`AttendanceStat`（+`requiredShifts/attendedShifts/leaveShifts/absentShifts/absentOrLeaveCount` + `ofShift` 工厂 + `byField` 表驱动扩展）、`PayrollContextProvider`（班次路径/旧按天路径按账期开关分流；取数补 `period_index/period_name/shift_id` 并 join `attendance_shift.start_time`；`leaveCount` 新路径=请假班次）、`AttendanceItemResolver`（T1 全勤奖取 `fullAttendMetric=ABSENT_OR_LEAVE`；封顶链级 1 `params.cap` → 级 2 `absentFineCapRatio×折算基本`；`absentFinePerShift/Cap` 为 params 缺省时兜底）、`AlgoProperties.Payroll`（+10 键）、`ApprovedLeaveDaysPort`（+`approvedLeaveShiftUnits`，含 `ApprovedLeaveDaysPortImpl` 实现与 `UnavailableApprovedLeaveDaysPort` 降级空集）、`application.yml`（payroll 段 + 键 + `ABSENT_OR_LEAVE` 映射 + `allowNegativeNet: false`）。**口径（用户已裁定，未改）**：2 班/天、只折算 basicSalary、已批请假只折算不罚款、旷工 100 元/班次、请假算缺勤破全勤、实发不低于 0、迟到按次。**历史兼容双保险**：账期开关 `shiftModelFromMonth=2026-10`（旧账期走旧按天路径，`requiredShifts=attendedShifts=应到天数` ⇒ 折算比例恒 1、`absentOrLeaveCount=absentCount` 沿用旧全勤口径）+ 记录级哨兵 `legacyPeriodSentinel=全天班`（覆盖当日全部排班班次）。**参数外置**（`hrm.algo.payroll.*`，全 ASCII 键，默认值照抄方案 §7）：`shiftModelFromMonth/middayBoundaryMinute/legacyPeriodSentinel/proratedFields/absentFinePerShift/absentFineCap/absentFineCapRatio/zeroSchedulePolicy/fullAttendMetric/lateGranularity`。**单测**：新增 `PayrollShiftModelTest`（5 验收数字 1500/1475/1375/1450/1250、空排班/无打卡封顶 0/整月全假/跨日单/非法区间/ABNORMAL/空 period_name 双班不克扣+告警 30/单班制哨兵 ratio=1/重叠不双扣、恒等式 500 人 0 违例、幂等）；`PayrollItemResolverTest` 适配（AttendanceStat 新签名夹具 + PRORATED/T1/封顶链级 2 用例 + 注册表 4→5）；`PayrollRuleValidatorTest` 适配文案 + PRORATED 合法用例。**离线对照**：`node hrm-dev/docs/algo-scripts/s2b-payroll-shift.mjs` 实跑 5 数字逐位一致。**文档登记**：`db.md` §8.6.2/§8.6.4 的 `source` 取值集合追加 `PRORATED`（文档枚举，非 DDL，真源 `PayrollSource`）。**未触碰**：HTTP 契约（URL/入参/出参/错误码）、`hrm-admin`/`hrm-demo`/`deploy`/迁移脚本/`.trae/rules/`/`SESSION-STATE.md`/其它 `docs/` 正文。**本机无 JDK/Maven，未编译未单测**，静态自审（导入/注解/配置键↔强类型类逐键对应）通过，**收敛到服务器阶段**（`mvn -q test`）；**偏差登记**：方案 §9.3 建议项 S2「`detail` 单位『次→班次』」**未实施**（解析器不感知账期，旧按天路径下会误标「班次」，故保留「次」并记 `TODO(扩展)`）；`api.md` 一期无工资单契约（方案 C9），故取值集合登记落 `db.md`。**事实性纠正**：`kdyzgl_test_seed.sql:1363` 的 `ABSENT_FINE.params.amount` 现为 **100**（方案 §0.1 C4 记 150 与现状不符，早前数据修正脚本已改）。

## 2026-09-25 · 班次制算薪口径数据修正脚本 + 修复两处种子错配（数据库，仅数据无 DDL）

按 `algo-payroll-shift.md` v2.0（§1 B5/T3、§7.1 封顶链、§6 数据侧处置）产出班次制算薪口径的**数据修正脚本**并同步长期种子。**新增** `hrm-dev/sql/seed/fix-shift-and-payroll-20260925.sql`（纯数据、幂等、带 `kdyzgl_test` 目标库断言与 §E 回滚；分节 A/B/C/D/E：**A** 删中班——先只读核对 `attendance_schedule.shift_id` 引用，改派到同驿站晚班后再删 8 条；**B** `ABSENT_FINE.params.amount` 150→100（`mode=PER_COUNT` 不变、`cap` 保持 `0`＝不封顶，与 §7.1 级1 `itemCapSemantics=ZERO_MEANS_NO_CAP` 一致，级2/级3 封顶不入库）；**C** 规则 id 错配——`payroll_rule.id` 3→1、4→2，对齐 `payroll_rule_item.rule_id=1/2`、`payroll.rule_id=1`、`rule_snapshot.ruleId=1` 三处引用点（选「重排规则 id」而非「搬规则项」，故不触碰任何历史单据/快照）；**D** `leave_log.leave_id` +12 对齐现有 `leave_request.id=13–24`）。**同步长期种子** `kdyzgl_test_seed.sql`：删 8 条中班、96 条中班排班改派晚班（`shift_id 2→3`，依据 §7 `middayBoundaryMinute=720`）、罚款 100、并把 `payroll_rule`/`leave_request` 改为**显式 id**（1/2 与 1–12；根因＝`DELETE` 不重置自增、子表硬编码父 id 致漂移）。**发现同类潜伏缺陷（未修，登记遗留）**：`payroll`↔`payroll_item.payroll_id`、`work_order`/`sync_task`↔`notification.biz_id` 同属「父表隐式 id 漂移」类，重灌会错配。**未触碰** `hrm-server`/`hrm-admin`/`hrm-demo` 源码、`docs/` 既有正文、`.trae/rules/`、`SESSION-STATE.md`、V1–V15 迁移脚本与 `sql/schema/mysql/init.sql` 快照；本机无 MySQL，脚本**未实跑**，静态自检（列名/JSON/幂等/引用完整性）**收敛到服务器阶段**；执行属 C 档，由主智能体授权。

## 2026-09-25 · 端准入收紧实现 L7 安全复验（网络安全工程师，结论「有条件放行」/ 残余风险低）

对 `security-client-admission-review.md` 7 条必改的实现做 L7 复验，追加「## 复验（实现完成后）」章节（保留上一轮内容）。逐条取证：**7/7 已落实**——单一真源 `ClientAdmissionPolicy`（唯一归一入口 `resolveEnd`）+ `ClientType.parse` 严格归一，旧类 `ClientRolePolicy`/`EndAdmissionPolicy` 及其测试已删除、全仓源码零残留（仅命中历史文档）；缺省/未知/非法端 fail-closed（`isLoginAllowed` 的 `end==null→false`）且不再回落 WEB；`WEB` 纳入 `hrm.auth.pc-allowed-roles`（默认 ADMIN）；管理端 `boss-allowed-roles` 仅 ADMIN；员工端 `staff-allowed-roles=STAFF,STATION_ADMIN`（拒 ADMIN）；三登录路径 + 票据复判（发码/验证）共用同一策略对象；`api.md` 新增 4.1.5 与 1110 明细。**残余风险由「中」降为「低」**（“缺省静默放行 / `WEB` 不受约束 / 双口径分流”绕过路径已在实现层消除；端类型“客户端自称、非鉴权边界”的固有属性仍存但不产生任何权限提升）。**新引入 1 项交付阻断级兼容回归**：`hrm-admin` 登录（`hrm-admin/src/views/login/index.vue:101-104` 仅提交 username/password、`hrm-admin/src/utils/request.js:23-32` 只加 Authorization）不上报任何端类型 → fail-closed 下 ADMIN 被 1110 拒登；该前端系 deploy 目标站点（`deploy.sh:68/74`、`nginx.conf.example:28`），且健康检查仅取 HTTP 码（1110 仍 HTTP 200，`deploy.sh:313`）不会自动拦下 → **部署门禁 = 有条件放行，放行前须闭环该项**（`hrm-admin` 登录补 `clientType:'WEB'` 或 `X-Client-Type:ADMIN`，或书面声明其不在本次部署范围），并给出只读复现命令与验收标准。另有 4 项低风险观察：互踢粒度细化与遗留 `H5` 会话不互相覆盖（受并发上限兜底、3 天 TTL 自然消解）、`multi-client-architecture.md:93`「缺失默认 web」未同步、`api.md:337`「前端须上报 X-Client-Type」与 Demo 实走请求体 `clientType` 不符、短信路径端准入先于验证码校验的既有信息面（改造前后一致，非本次引入）。**未触碰**任何被复验代码/配置/契约与 `hrm-admin`/`deploy`；本机无 JDK/Maven/Redis，未独立复跑服务器单测（结论基于源码逐条取证）；**未声称「端准入已全量生效/已合规」**（须先闭环 `hrm-admin` 项）。

## 2026-09-25 · Mock 端准入同步后端 fail-closed 三端互斥矩阵（前端）

将 `hrm-demo` Mock 端准入对齐后端 `ClientAdmissionPolicy`（`security-client-admission-review.md` 必改 1–7 的 Demo 侧同步）。**新矩阵（与后端逐格一致）**：PC 网页端（`WEB`/`ADMIN`）仅 `ADMIN`、管理端 H5（`as=boss`）仅 `ADMIN`、员工端 H5（`as=station|staff|缺省`）仅 `STAFF`+`STATION_ADMIN`、**端类型缺省/未知/非法一律 1110（fail-closed，删除「缺省即不校验」放行分支）**。**实现**：`src/shared/mock/routes/auth.js` 以 `resolveEnd`（唯一归一入口，镜像后端：`ADMIN/WEB→PC`、`BOSS`、`STAFF`、旧值 `H5` 按 `as` 派生、其余 `null`）+ `END_ALLOWED_ROLES` 表驱动替换旧 if 链；三条登录路径（密码 / 短信 / 设备二次验证）共用；`DEVICE_VERIFY` 发码补端准入复判（对齐后端 `sendDeviceVerifyCode`，防票据跨端复用，端取票据存值不回头信客户端）；设备平台归类改按端判定（`PC→网页端`，其余 `H5`）。**演示数据**：`src/demo/accounts.js` 每个身份新增 `end` 归属；`mobile/stores/auth.js#switchTo` 按目标身份带 `as`（否则员工端↔管理端切换必 1110）；`mobile/views/login/index.vue` 「一键体验」按当前入口端过滤，不列异端账号。**测试**：`verify-mock` 登录助手按角色补端（ADMIN→WEB、其余→H5+station），直连登录点补端，端准入段改为 4 端×3 角色逐格 + 缺省/未知/非法 12 项 + H5 派生 3 项并移至段末（避免顶掉 C1/C2 会话），**919 → 942 项全过**；`verify-mobile` 登录助手同步补端（48/48）；`e2e` harness `mobileLoginAs` 按账号选入口（管理员 `as=boss`）、`01-load` A1-5 改 `?as=boss`。**未触碰** `hrm-admin`/`hrm-server`/`deploy`、Mock 装配、`docs/` 既有正文、规则与 `SESSION-STATE.md`。门禁（`hrm-dev/hrm-demo` 实跑）：`lint` 0 error/41 warning（既有）、`test` 430/430（`useAttendanceStatus` 时间依赖用例本次通过）、`build`/`build:prod` 成功、`verify:mock` 942/942、`verify:mobile` 48/48、`verify:tokens` 通过。

## 2026-09-25 · 三端互斥准入（fail-closed）实现（后端，落实安全评估 7 条必改）

按 `security-client-admission-review.md` 必改 1–7 落地「账号不能混登」：员工+站长仅员工端、管理员仅管理端+PC、缺省/未知/非法端 fail-closed（1110）。**单一真源收敛**：端类型归一并入 `ClientType.parse`（严格归一，**删除未知回落 WEB**），端 → 角色策略收敛为新类 `ClientAdmissionPolicy`（唯一策略 + 唯一归一入口），**删除**旧类 `ClientRolePolicy`、`EndAdmissionPolicy`（三套口径 → 一套）。**配置外置**（全 ASCII 键，逗号分隔）：`hrm.auth.pc-allowed-roles`（端 ADMIN/WEB，默认 `ADMIN`）、`hrm.auth.boss-allowed-roles`（端 BOSS，默认 `ADMIN`）、`hrm.auth.staff-allowed-roles`（端 STAFF，默认 `STAFF,STATION_ADMIN`）。**入参优先级**：请求头 `X-Client-Type`（新契约 `ADMIN/BOSS/STAFF`）优先、请求体 `clientType` 回退（旧值 `H5` 按 `as` 派生 BOSS/STAFF）。**四条路径共用同一策略**：密码登录、短信登录、设备二次验证复判、DEVICE_VERIFY 复判（`AuthServiceImpl` 各调用点 + `smsLogin` 增 `headerClientType` 入参、`AuthController` 传入该头）。**未新增错误码**（复用 1110）；`api.md` 首次登记端准入契约（新增 4.1.5、11xx 段与 1101–1110 明细、4.1.1 入参与错误码）；`application.yml` 修正与新行为不符的旧注释。**单测**：新增 `ClientAdmissionPolicyTest`（12 组合矩阵 + 缺省/未知/非法 fail-closed + 旧端 H5 派生 + 头优先 + 三路径一致 + 空配置 fail-closed + 解析口径）；更新 `ClientTypeTest`（断言的「回落 WEB」显式改为「返回 null」，附变更理由）；删除 `ClientRolePolicyTest`/`EndAdmissionPolicyTest`；`AuthServiceImplUniversalCodeTest` 适配 `smsLogin` 新签名（旧端 H5→员工端，断言不变）。改动文件：`ClientType`、新增 `ClientAdmissionPolicy`、删除 `ClientRolePolicy`/`EndAdmissionPolicy`、`AuthProperties`、`AuthServiceImpl`、`AuthController`、`AuthService`、`ErrorCode`（注释）、`LoginRequest`/`SmsLoginRequest`/`SmsSendRequest`/`DeviceTicket`（注释）、`application.yml`、`api.md`、本行。**未触碰** `hrm-admin`/`hrm-demo`/其它 `docs/` 正文/规则/迁移脚本/评估报告。本机无 JDK/Maven，**未编译未单测**，收敛到服务器阶段（命令见回报）；**未声称端准入已合规**（须安全工程师 L7 复验）。

## 2026-09-25 · 端准入规则收紧技术安全评估（网络安全工程师，**只出结论未改代码**）

对 2026-09-25 用户需求「员工+站长仅员工端、管理员仅管理端+PC」出**静态安全评估**，**新增** `hrm-dev/docs/security-client-admission-review.md`。**风险分级：中**（产品/审计约束可被静默绕过、需求收敛目标不成立；**非数据越权**）。**核心事实（与任务描述 3 处出入）**：① 实际生效类为 `EndAdmissionPolicy`（口径 `WEB`/`H5`），非任务所述 `ClientRolePolicy`（口径 `ADMIN/BOSS/STAFF/WEB`）——**两套口径经 `isEndAllowed` 分流并存**（`AuthServiceImpl.java:563-569`），架构 §2.1.3 又为第三套取值域；② `api.md` **零记载** `X-Client-Type`/端类型/`1110`（任务假设有现行约定，不成立）；③ `RoleEnum` **无 `BOSS` 角色**（仅 `ADMIN/STATION_ADMIN/STAFF`），"BOSS"仅为端类型/入口视角。**七项结论**：绕过获额外权限=不能（权限恒由会话 role + `@RequireRoles` fail-closed + 数据范围）；收紧 fail-closed=否（缺省/未知端静默放行，`WEB` 在回退策略下不受约束 → 绕过口子）；存量会话 3 天窗口内不失效、端准入**仅约束登录**（`JwtAuthFilter` 不校验端）；管理端越权面=不能触达 ADMIN-only 接口（403，举例 dashboard/employees/stations/departments/kpi-metrics/hr-flows/payroll-rules/sync-config-center）、无数据越权；管理员禁登员工端 **安全无副作用**（无"仅员工端可用"接口），仅入口/UX 影响；不触 §7.2 红线；**必改 7 条**（统一端类型单一真源 / 缺省端 fail-closed / `WEB` 纳入受约束端 / 管理端收紧为仅 ADMIN / 员工端拒绝 ADMIN / 三路径共用策略 / `api.md` 登记 1110 契约），**C 档技术输入：暂不放行**。仅新增 1 报告 + 本行；**未改** `ClientType`/`ClientRolePolicy`/`EndAdmissionPolicy`/`AuthServiceImpl`/`JwtAuthFilter`/`application.yml`/`api.md` 等被评估对象与任何业务代码；本机无 JDK/Maven/Redis，**未编译未运行**（U1–U6 未验证项见报告 §五）。

## 2026-09-25 · S2b 班次制算薪方案 v2.0 技术重评（技术评审，结论「有条件通过」，无业务代码改动）

对 `algo-payroll-shift.md` **v2.0** + `algo-scripts/s2b-payroll-shift.mjs` 出**重评**，追加 `hrm-dev/docs/tech-review-payroll-shift.md`「## 重评（v2.0）」章节（保留上一轮内容）。**逐条取证核验（不看修订记录表放行）：上一轮 7 条必改项 B1–B7 全部「已落实」**——B1 迟到粒度事实已更正为「按有效 ON 卡计数」且新论证与源码自洽（`PayrollContextProvider.java:94-98`、`AttendanceConstants.java:75-76`）、默认改 `PER_CARD`、补双班算例；B2 `PRORATED` 改动面 6 项逐文件:行号核对准确（`PayrollSource`/`PayrollRuleValidator:23,77-79`/`AttendanceStat:15-19`/`PayrollContextProvider:76-79,106-110`/`sourceLabel`/演示端字典与编辑器）；B3 查证 `V8__payroll.sql:41,97` 确为 `VARCHAR(16)`+COMMENT、**无 CHECK/ENUM**（文档枚举），撤回「无冲突」并给同步位置；B4 三态优先级唯一化、原型新用例实跑「空 `period_name`+双班 → 实出 60/旷工 0/实得 1500/告警 30」；B5 新增假设 5 条 + 风险 5 类（含 −6000/−360 出处）；B6 逐级 min 封顶链与代码执行顺序一致、正面裁定 `leave_deduct_enabled` 新路径 no-op（终结悬空）；B7 新增幂等/试算 N1–N4（`PayrollController.java:61`、`PayrollServiceImpl.java:154-163/171-176`、`PayrollLockQueryService.java:27` 均核对成立）。建议项 S1–S6 亦逐项采纳。**原型实跑两次逐位一致**：5 个验收数字 1500/1475/1375/1450/1250、整月无打卡实得 0（钳制）、T1 请假态全勤奖 0（旧 200）、500 人恒等式 0 违例/负净额钳制 0。**结论：有条件通过**，必改项 **1 条（B8，v2.0 新引入内部矛盾）**：`§2.4.2`（L193）「取实测库开关值=1」与 `§0.1 C3`（L47）「实测播种值=0」互斥，须二者取值一致（按 =0 重算或显式标注为假设场景），属「表述/依据」类，作者修订后按条核对、无须再次重评；修正 `§9.5` 9606 行号 L683→`api.md:685`。**未改**方案、原型、评审报告正文、任何业务源码与迁移脚本；本批仅改 2 文件（评审报告追加 + 本行）。

## 2026-09-25 · S2b 班次制算薪方案按评审必改项修订（算法，无业务代码改动）

按技术评审报告（`tech-review-payroll-shift.md`，结论「打回」）逐条修订 **`hrm-dev/docs/algo-payroll-shift.md`（v1.0 → v2.0）** 与 **`hrm-dev/docs/algo-scripts/s2b-payroll-shift.mjs`**，并落实用户新裁定 4 项口径。**7 条必改项全部落地**：B1 更正「`LATE_FINE` 现状按天计一次」事实错误（实为按有效 ON 迟到卡计数，`PayrollContextProvider.java:94-98`、`attendanceStore.js:893`），默认改 `lateGranularity=PER_CARD`（按次=现状，行为不变），`PER_DAY` 才属口径变更（未采纳），并给出双班切换前后差异算例（单班 20/20 一致；双班现状 20 次 vs 按天 10 次，差 −200 元）；B2 补全 `PRORATED` 改动面 6 项（`PayrollSource` 枚举、`PayrollRuleValidator` 白名单、`AttendanceStat` 字段、`PayrollContextProvider` 取数、`sourceLabel`、演示端字典与 `PayrollRuleEditor` 分支）；B3 查证 `db.md:812/860` 的 `source` 取值集合为**文档枚举**（`VARCHAR(16)`+列 COMMENT，无 CHECK/ENUM 约束），登记 `db.md` §8.6.2/§8.6.4 同步与 DDL 注释属 C 档；B4 定死「记录→班次」唯一三态优先级（哨兵 / `period_name` 空→不克扣+告警 / `period_index`），补用例（空 `period_name`+双班 → 应出 60、实出 60、实得 1500、告警 30）；B5 新增「假设与风险登记」（假设 5 条 + 风险 5 类，含 −6000 与 −360 出处）；B6 给出罚款封顶优先级链（规则项 `params.cap` → 配置 `absentFineCapRatio` → 合计层 `allowNegativeNet`，逐级取 min）并正面裁定 `leave_deduct_enabled` **冻结保留、新路径 no-op**（终结 C3 悬空）；B7 新增幂等/试算 NFR 4 条断言（试算=`generate` 造草稿 / DRAFT 覆盖重建同结果 / 非 DRAFT·REJECTED → 9405 整批拒绝 / 与账期锁无交互）。**用户 4 项裁定**：T1 `fullAttendMetric=ABSENT_OR_LEAVE`（请假者不发全勤奖）、T2 `proratedFields=[basicSalary]`、T3 `allowNegativeNet=false`（实发不低于 0，原型 −6000 已消除为 0）、T5 `lateGranularity=PER_CARD`。**原型实跑**：5 个验收数字 1500/1475/1375/1450/1250 **逐位一致**；500 人基准恒等式 0 违例、单人 0.2585 ms；新用例「整月无打卡」实得 0（钳制）、「空 period_name 双班」不克扣、T1 全勤奖请假态 0（旧 200）。**未改** `hrm-server`/`hrm-admin`/`hrm-demo` 源码、评审报告、其它 `docs/` 正文、规则与迁移脚本；本批仅改 2 文件（方案+原型）+ 本行。**本版为待重评稿，未声称通过评审。**

## 2026-09-25 · S2b 班次制算薪方案技术评审（技术评审，结论「打回」，无业务代码改动）

对 `algo-payroll-shift.md` v1.0 + `algo-scripts/s2b-payroll-shift.mjs` 出六维独立评审报告 **新增** `hrm-dev/docs/tech-review-payroll-shift.md`。**实跑原型复核：5 个验收数字 1500/1475/1375/1450/1250 逐位一致、500 人基准与 9 个边界用例全部吻合**（耗时列环境相关，方案已预声明）；法规引用（人社部发〔2025〕2号 废止 劳社部发〔2008〕3号、20.67/21.75）经原文核对**无误**。**结论：打回**，7 条必改项：B1 `LATE_FINE`「现状按天计一次」与源码不符（实为按有效 ON 卡计数，双班制下即班次粒度）→「默认 DAY = 行为不变」不成立、属未经裁定的口径变更；B2 §9 漏登新增 `PRORATED` 的全部改动面（`PayrollSource` 枚举、`PayrollRuleValidator` 白名单、`AttendanceStat`/`PayrollContextProvider` 字段与查询、`sourceLabel`、演示端字典与 `PayrollRuleEditor` 分支）；B3 `db.md:812/860` 的 `source` 取值集合扩展未声明处置；B4 「一天两班 + `period_name` 为空」兜底歧义（可致克扣）；B5 缺假设与风险登记（−6000 负实发等 4 类）；B6 `absentFineCapRatio` 与既有 `params.cap`/`itemCapSemantics`/`allowNegativeNet` 优先级未定（含 `leave_deduct_enabled` 去留与 C3 悬空引用）；B7 幂等/试算路径未覆盖。另核 4 项待裁定口径（T1/T2/T3/T5）：**均不引发架构变更**，T1/T5 需局部统计实现扩展、T3 需定优先级，**本次打回与它们无关**。仅新增 1 报告 + 本行；未改方案、原型与任何业务源码。

## 2026-09-25 · 算薪缺勤/请假粒度「天 → 班次」算法方案与离线原型（算法，无业务代码改动）

按用户 2026-09-25 已裁定口径（每天 2 班次 / 基本工资按班次折算 / 已批请假只折算不罚款 / 旷工 100 元/班次 / 删中班 / 5 个验收算例）重设计「S2 算薪引擎」。**新增** `hrm-dev/docs/algo-payroll-shift.md`（四件套：问题建模 / 算法选型与复杂度 / 依据 / 可验证指标与基准数据）与 `hrm-dev/docs/algo-scripts/s2b-payroll-shift.mjs`（Node 离线原型，固定种子，实跑）。核心：以「排班行」为唯一计量单位、半天单元（`epochDay×2+(AM?0:1)`，1 单元 = 1 班次）做集合运算，`折算基本 = basic×|A|/|R|`、`旷工 = |R\(A∪L)|`。**5 个验收数字 1500/1475/1375/1450/1250 逐位复现通过**；500 人基准恒等式 0 违例、单人 0.27 ms。**事实性纠正**：劳社部发〔2008〕3号已被人社部发〔2025〕2号废止；`AttendanceSchedule` Service 查重键仍为 `employeeId+workDate`（需改三元键）；`payroll_rule_item.source VARCHAR(16)` 决定新 source 命名取 `PRORATED`。**接线改动仅登记**（删中班/改罚款 params 属数据变更、端口增方法属代码改动），交后端/数据库工程师；`db.md` 表结构、`api.md` 出参字段**无需变更**。登记 5 项待用户裁定（全勤奖含否请假、岗/津贴是否折算、罚款是否封顶、迟到粒度）与 3 项 `TODO(扩展)`。本批**只读源码、零改业务代码**，仅新增 2 文件 + 本行。

## 2026-09-25 · 移动端「调薪调整」津贴明细编辑实现（前端，仅 1 文件）

按冻结规范 `hrm-dev/docs/design-mobile-allowance-edit.md` 落地津贴明细增删改，**仅改** `hrm-dev/hrm-demo/src/mobile/modules/boss/views/hrDetail.vue`（+199/−4，无新文件、无新组件、无新 Token）。逐条落 4 条纠正：① 提交 payload **补传 `allowances`**（`key||null` / name trim / `Number(amount||0)`），修「缺省即保留原值」的静默失败；② 「津贴合计」与 `nextTotal`/`diff` 改由 **`form.allowances` 求和派生**（离职只读态取当前档案值）；③ 校验取 **PC 更严口径**（名称非空且 ≤20 字、金额非负整数且整数部分 ≤6 位），金额错误失焦落本行下 `role="alert"`，名称留空提交时收口到既有 `.form-error`；④ 有内容的行删除走 **1 次 `bossConfirm`**（空行豁免）。行结构 `3fr / 2fr / var(--touch-min)` 网格、行高 `--row-h-1`、分隔线仅行与行之间；仅覆盖 `.allowance-row .van-field` 的水平内边距（唯一 Vant 覆盖点）。`hrm-admin`/`hrm-server`、Mock 装配与断言、冻结规范文档均零触碰。

### 门禁（在 `hrm-dev/hrm-demo` 实跑）
- `npm run lint`：**0 error** / 41 warning（含本文件 3 条：2 条 `label-has-for` 属规范 §5.5 指定的「外部 `<label for>` + van-field 内部 input」结构所致、1 条 `max-lines`）。
- `npm run test`：**429 通过 / 1 失败**（总数 430）。失败项 `useAttendanceStatus.spec.js`「今日待审批槽位」为**既有、与本变更无关**（该用例读真实时钟，本机 07:2x 落在打卡窗口开放前 → `wait` 而非 `todo`），未改任何既有断言。
- `npm run build`（演示态）与 `npm run build:prod`（生产态）：**均成功**（各 ~1m40s）。
- `npm run verify:mock` 919/919、`npm run verify:mobile` 48/48、`npm run verify:tokens` 通过。
- `npx stylelint src/**`：本文件 0 问题；既有失败 2 处（`mobile/views/login/index.vue`、`pc/views/login/index.vue` 的 `comment-empty-line-before`）由并发会话改动引入，非本变更。

### 事实性纠正
- 任务给定 `npm run test` 基线「509 通过 / 0 失败」与实测不符：当前工作区实为 **430 用例（429 通过 / 1 失败）**，差异应来自并发会话改动或基线采集时点（失败项依赖真实时钟）。
- `mobile/api/hr.js` 的 `updateHrSalary` 本身是纯透传，缺 `allowances` 的根因在调用方 payload；故**未改该文件**，修正落在 `hrDetail.vue` 的 submit。

### 遗留与 `TODO(扩展)`
- 本文件因承载完整明细区由 296 → 428 行（非空非注释），触发 `max-lines`（warn，非 error）。规范「对接点清单」要求全部落在 `hrDetail.vue`，故本轮不拆子组件；如后续拆分须先经设计与评审确认。
- 未新增自动化用例（组件挂载需覆盖 router/api/vant 多重桩），四态与像素级项（热区、`inputmode` 唤起键盘、320/375 无横向滚动）收敛到部署后浏览器实测。

## 2026-09-25 · 移动端「调薪调整」津贴区交互与视觉规范（纯设计交付，无代码改动）

出 `hrm-dev/docs/design-mobile-allowance-edit.md`：把 `hrDetail.vue` 第 212 行只读的「津贴合计」`van-cell` 升级为可增删的津贴明细子区块。**布局结论**：每行 `van-field ×2`（名称 `3fr` / 金额 `2fr`）+ 44px 图标删除按钮构成的 3 列网格，行高 48、随页面滚动不内滚；节点仍是同一张 `.card`、同一左边界，**不新增卡片、不新增组件、不新增 Token**。删除需 1 次 `bossConfirm`（空行豁免）；合计改为 `form.allowances` 求和派生、**不防抖**；校验按 PC 客户端更严口径（整数 + 6 位上限）+ 契约（名称 1–20），文案逐字对齐 PC。**硬性对接点**：移动端 `updateHrSalary` 现未传 `allowances`，改为可编辑后必须补传，否则 UI 有反馈、数据无变化（静默失败）；契约与 Mock 无需变更（PC 已在用同一形状）。**未改任何代码**（`hrm-admin`/`hrm-server`/Demo Mock 装配与断言零触碰）；本批仅新增 1 份文档 + 本行日志。

### 事实性纠正
- `demo-boss-ui-spec.md` §6.3 引用的 `mobile/views/boss/hrDetail.vue` 路径已失效，实际在 `mobile/modules/boss/views/hrDetail.vue`。
- 问题描述将 PC 口径概括为「金额为非负数」，实测 PC 客户端更严：要求整数且整数部分 ≤6 位（契约/Mock 只校验非负数字）；本规范按更严者执行。

### 遗留（已登记到规范 §10）
- `--text-placeholder` 对白底约 2.5:1，低于 AA 4.5:1，属全站 `van-field` 既有口径，需设计系统层统一裁决，不在本区块单点修改。
- PC 端删除津贴项无二次确认，与移动端不一致，建议后续对齐评估。

## 2026-09-25 · 新增第 10 角色「技术评审工程师」+ 方案报审硬性闸门（规则与配置，无代码改动）

### 概述
解决「方案产出方与方案评审方为同一主体」的独立性缺口（架构师既写 ADR 又出自评审报告，与 v2.2 新增网络安全工程师的根因同构）。新增第 10 角色 **技术评审工程师**（`express-station-tech-reviewer`），只做**方案阶段产物的技术质量独立评估**（六维：依据充分性 / 架构与模块边界 / NFR 覆盖度 / 复杂度与可行性论证 / 假设与风险登记 / 与 `api.md`·`db.md` 契约一致性），出结论等级 + 必改项 + 验收标准，**不产出方案、不代改、不代放行**。新增**方案报审硬性闸门**：**所有方案必须经该角色评估通过（结论「通过」或「有条件通过」）后，才能报主智能体审批**；结论「打回」不得报审，退回原作者修订后重评；主智能体**不得受理未评审方案**；结论绑定方案版本。本批**全部为 A 档本地文件改动，未提交、未执行任何生产动作**。

### 产物（文件清单与改动摘要）
| 文件 | 说明 |
| ---- | ---- |
| **新增** `.trae/skills/tech-evaluation/SKILL.md` | 领域核心技能：六步流程（受理→六维评估→结论分级→必改项与验收标准→复评→输出）、三级结论表与分级红线、必改项格式模板、报告模板、7 条评估方反模式 |
| **新增** `.trae/agents/express-station-tech-reviewer/SKILL.md` | 第 10 角色定义：唯一职责域 + 8 条「明确不做」+ 硬性闸门 + 输入输出契约 + 六阶段工作流 + 项目专项评估要点 |
| `.trae/rules/项目规则1.md` | v2.3 → **v2.4**：§8 角色表加行、角色数 9→10、§8 新增**第 8 条「方案报审硬性闸门」**（5 子项）、§9.2 技能矩阵新增 `tech-evaluation`、文末新增 B5 修订小节 |
| `.trae/rules/智能体调度规则.md` | v1.0 → **v1.1**：§2 编制 1+10、§3 新增路由 **R25**、§4 新增组合优先级 **P0.6（技术评审先行）**、§5 新增链路 **L8（方案→技术评审→报审）**、§6.2 不可并行反例加行、§9 冲突裁决加 2 行、§10 反模式新增 **A22/A23**、§11 对齐检查表加行 |
| `.github/CONTRIBUTING.md` | 重写为 `项目规则1.md` v2.4 **逐字镜像**（正文逐行一致，仅头部多一行镜像说明；已用 Compare-Object 校验正文差异为 0） |
| `全局规则.md` | §6 智能体调用：角色编制改 10（含技术评审工程师）；新增**方案报审硬性闸门**段；头部补 2026-09-25 修订说明 |
| `智能体配置.md` | 编制 9→10；主智能体段新增「方案报审硬性闸门」；§1 架构师输出产物区分 `review-{主题}.md`（自检）与 `tech-review-{主题}.md`（独立评估）；**新增「## 10. 技术评审工程师」章节**（身份 / 明确不做 8 条 / 硬性闸门 / 输入输出 / 硬红线 / 升级路径 / 加载技能） |
| `hrm-dev/docs/agent-team-design.md` | v1.1 → **v1.2**：§0 结论、§3.1 编制表与 §3.2 编制图（新增评审链路子图）、**新增 §3.4 论证**、§4 契约卡 9→10 张并**新增 §4.10 契约卡**、§5 台账加 2 行、§6.1 路由 R25 / §6.2 P0.6 / §6.3 L8、§6.4 反例、§6.6 交接包附加要求、§6.7 打回处置、§6.8 冲突裁决 2 行、§7 权限绑定加闸门行、§8 反模式 A18–A20、§9 检查表、§10 存疑 9–11 条 |
| `AGENTS.md` | 硬红线区新增一行：方案阶段产物须先过技术评审闸门才能报审 |
| `hrm-dev/docs/project-tree.md` | `.trae/agents/` 9→10、`.trae/skills/` 13→14 |
| 桌面交付包 `d:\Users\16626\Desktop\快递驿站智能体团队配置\` | 同步 `rules/`(2)、`agents/express-station-tech-reviewer/`、`skills/tech-evaluation/`、`docs/`(3)、`智能体配置.md`、`AGENTS.md`、`配置说明.md`(v1.1→v1.2)；**9 个同步文件 SHA256 全部逐字节 MATCH** |

### 三类评估边界（并行不互替）
| 维度 | 评估方 | 依据 |
| ---- | ----- | ---- |
| 工程面（方案技术质量） | 技术评审工程师 | 项目规则 §8 第 8 条；调度规则 P0.6 / R25 / L8 |
| 安全面（漏洞 / 攻击面 / 依赖供应链 / 合规） | 网络安全工程师 | 项目规则 §8 审批纪律；调度规则 P0.5 / L7 |
| 操作安全评估与 C 档授权 | 主智能体 | 项目规则 §10.3 |

### 未验证项与遗留
- **第 10 角色需在 TraeCode CN 界面手工创建**（智能体定义不落文件系统，无法脚本化）；未创建前 R25 路由无人承接、闸门无法闭环。创建名须为 `express-station-tech-reviewer`。
- `全局规则.md` 仅同步仓库与交付包副本；**本机实际加载的 `.trae-cn\user_rules\rule-<时间戳>.md` 需另行同步并新开对话生效**。
- 本批未提交（`git` 工作区存在并发会话在途改动）；如提交须用 `git commit -- <本批路径>` 指定路径，并先看 `git diff --cached --name-status` 全量（反模式 A21）。

## 2026-09-25 · 测试环境「万能验证码」+ 生产 fail-fast（`hrm.sms.dev-universal-code`）— 只出源码与单测，未执行

### 概述
解决用户「设备验证步骤不点『获取验证码』直接输 `000000` 登不上（1102）」的体验问题：新增配置键 **`hrm.sms.dev-universal-code`**（默认**空串 = 关闭**）。三条件同时满足（**配置非空** + **非生产环境** + **请求 `code` 与该值恒等**）时，在 `/auth/device/verify`、`/auth/sms/login` 两处校验入口**直接放行**——**跳过 Redis 取码与比对，且不消耗任何验证码/尝试计数**；设备信任签发、会话建立、端准入（1110）、账号状态（1002）等授权判定与登录链路**一律照常**。生产 profile 下该键非空即 **fail-fast 启动失败**。**未改任何 HTTP 契约（URL/入参/出参/错误码）**；未改 `hrm-demo`/`hrm-admin`、未改 `api.md`/`db.md`/`plan.md` 等受限文档、未改迁移脚本与 `.trae/rules/`。**本机无 JDK/Maven，未编译未跑测，收敛到服务器阶段。**

### 产物（文件清单与改动摘要）
| 文件 | 说明 |
| ---- | ---- |
| `hrm-server/src/main/java/com/qiujie/config/SmsProperties.java` | 新增字段 `devUniversalCode = ""`（`hrm.sms.*` 命名空间内，默认关闭） |
| **新增** `hrm-server/src/main/java/com/qiujie/service/support/sms/SmsUniversalCodePolicy.java` | 万能码判定纯逻辑：三条件、恒定时间精确比较（区分大小写）、无副作用、不打印任何内容 |
| `hrm-server/src/main/java/com/qiujie/service/support/sms/SmsConfigGuard.java` | 新增 `isProd` / `validateNoUniversalCode` / `isUniversalCodeConfigured`；prod 非空即 fail-fast；非生产非空打启动 WARN（不回显码值） |
| `hrm-server/src/main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java` | 注入 `Environment`；`verifySmsCode` 顶部新增短路分支（两处校验入口共用收口点） |
| `hrm-server/src/main/resources/application.yml` | 新增 ASCII 键 `dev-universal-code: ""`（中文仅注释） |
| **新增** `hrm-server/src/test/java/com/qiujie/service/support/sms/SmsUniversalCodePolicyTest.java` | 判定逻辑边界单测（空配置/生产/相等/不等/null 与空串/大小写） |
| `hrm-server/src/test/java/com/qiujie/service/support/sms/SmsConfigGuardTest.java` | 追加：万能码配置判定、prod fail-fast（且异常信息不含码值）、`isProd` 口径 |
| **新增** `hrm-server/src/test/java/com/qiujie/service/auth/impl/AuthServiceImplUniversalCodeTest.java` | 服务层短路单测：生效时**零触达** `SmsCodeStore` 且仍签发会话；配置空/不相等/prod 时回原逻辑 |

### 配置键清单
| 键名 | 默认值 | 生效条件 | 生产是否禁止 |
| ---- | ----- | -------- | ------------ |
| `hrm.sms.dev-universal-code` | `""`（关闭） | 三者同时满足：① 配置非空；② 非生产（`SmsConfigGuard.isProd`：prod profile）；③ 请求 `code` 去空白后与配置值**恒等**（区分大小写） | **是**：prod profile 下非空即启动失败（`IllegalStateException`） |

### 短路分支位置与范围
- **位置**：`AuthServiceImpl#verifySmsCode` 方法**首行**——该方法同时是 `/auth/sms/login`（场景 `LOGIN`）与 `/auth/device/verify`（场景 `DEVICE_VERIFY`）唯一的验证码校验收口点，一条分支覆盖两处入口。
- **跳过**：`SmsCodeStore.getCode`（Redis 取码）、`attempts`（读计数）、`incrementAttempts`（自增计数）、`clearCode`（码作废）；即**不读取、不消耗任何验证码与尝试计数**。
- **不跳过**：进入 `verifySmsCode` **之前**的入参与授权判定（短信登录：手机号格式、账号存在性 1001、账号状态 1002、端准入 1110；设备验证：票据校验、账号状态 1002、端准入 1110）；以及**返回之后**的登录链路（`establishDeviceTrust` 设备令牌签发 + `auth_trusted_device` 落库、`issueLoginSession` 会话建立、登录日志）。短路仅记录场景名，不回显码值/手机号/员工标识。

### 生产 fail-fast 实现
`SmsConfigGuard` 构造期（Bean 创建 = 启动）在 `isProd` 为真时：先 `validateProd`（既有：凭据未配即失败），再 `validateNoUniversalCode`——`hrm.sms.dev-universal-code` 非空抛 `IllegalStateException(ERROR_PROD_UNIVERSAL_CODE)`，与「生产未配短信凭据即失败」**同等强度**；错误信息只说明「该键仅供测试环境」，**不回显码值**。服务层 `SmsUniversalCodePolicy` 再判 `production` → 生产恒不生效，构成**双保险**。

### 静态自审与未验证项
- 静态自审（本机无 JDK/Maven）：配置键 `dev-universal-code` 与强类型类 `SmsProperties.devUniversalCode` 逐键对应（Spring relaxed binding）；新增 import/签名核对（`Environment`、`SmsConfigGuard`、`SmsUniversalCodePolicy`）；`AuthServiceImpl` 新增 `final Environment` 由 `@RequiredArgsConstructor` 纳入构造器（**字段顺序已与单测构造调用逐位核对**）；无不可变集合传 null 之 NPE 风险（`List.of` 未涉及）；YAML 键全 ASCII、中文仅注释。
- **未验证项（收敛服务器）**：`mvn -q test` 未实跑；Spring 容器启动期 fail-fast 的真机触发、非生产 WARN 日志实际输出、`/auth/device/verify` 与 `/auth/sms/login` 携万能码的端到端 200 均**未实跑**。

### 事实性纠正
- **`hrm.sms.dev-fixed-code` 不是万能码**：其语义仅为「降级通道**生成**的验证码固定为 `000000`」，仍须**先发码**落 Redis 再比对——故「不点获取验证码直接输 `000000`」必失败（1102）。本次新增的 `dev-universal-code` 才是「免发码放行」，二者语义不同，已在 `SmsProperties` 中并列注释区分。
- **环境判定为 profile 驱动（沿用现有口径）**：`SmsConfigGuard.isProd` = `prod` profile 处于激活态。若生产服务器未激活 `prod` profile，则既有「未配凭据 fail-fast」与本「万能码 fail-fast」**均不触发**——此为既有口径的固有边界，本批未扩大判定方式（避免与 `SmsConfigGuard` 既有口径分叉）。

### TODO(扩展) 与遗留
- `TODO(扩展): 若产品要求万能码仅对特定场景（如仅 DEVICE_VERIFY 而不含短信登录）生效，可在 SmsUniversalCodePolicy 增加场景维度入参；本批按需求对两处入口统一生效。`
- 遗留：服务器阶段需补跑 `mvn test` 与两条端点的真机验证；生产外置配置 `application-prod.yml` 须确认未写入该键（主智能体核查，本角色不下发/不接触生产配置）。

## 2026-09-25 · 补齐联调/演示身份账号：标准化演示账号 + 驿站 8 补齐（种子脚本 §2.1 新增 8 行）+ 账号清单文档

### 概述
按用户要求「预留所有的身份账号并输入好」，在**测试库种子脚本** `hrm-dev/sql/seed/kdyzgl_test_seed.sql` 中**新增 8 个可直接登录的账号**（id 57–64），并新增交付文档 `hrm-dev/docs/demo-accounts.md`（完整账号清单 + 各端准入规则）。**既有 56 行 id 与内容零改动**（避免打断 `attendance_schedule.employee_id` 等既有引用）；新增行落在第 0 节清理之后的 §2.1 独立 INSERT 块，保持幂等。**仍只针对 `kdyzgl_test`**；`kdyzgl`（结构库）/`courier_station`（现网）绝不触碰。**未改 V1–V15、`init.sql` 快照、三端源码、`.trae/rules/`、其他文档正文**。**本轮只出脚本与文档，SQL 实跑收敛到服务器阶段。**

### 产物
| 文件 | 说明 |
| ---- | ---- |
| `hrm-dev/sql/seed/kdyzgl_test_seed.sql` | 第 §2 节表头注释更新为「既有 56；补充 8 见 §2.1，共 64」；**新增 §2.1 块（行 149–168）**：8 个 `employee` 行（id 57–64） |
| **新增** `hrm-dev/docs/demo-accounts.md` | 账号清单：按端速查 / 按角色汇总 / 全量 64 行表 / 各端准入规则 / 登录前置（双通道 + 固定码 `000000`）/ 来源与重灌命令 / 数据覆盖缺口 |

### 新增账号（8）
| id | username | 角色 | 驿站 | 用途 |
| -- | -------- | ---- | ---- | ---- |
| 57 | `boss` | ADMIN | — | 网页端/PC + 移动端管理视角（唯一可登 PC 端） |
| 58 | `manager` | STATION_ADMIN | 1 城东 | 移动端管理视角；兼验 PC 端 1110 拦截 |
| 59 | `staff` | STAFF | 1 城东 | 移动端员工端主用 |
| 60 | `staff2` | STAFF | 2 城西 | 备用员工账号 |
| 61 | `st008_admin` | STATION_ADMIN | 8 开发区分站 | 补齐驿站 8 站长 |
| 62 | `st008_staff` | STAFF | 8 开发区分站 | 补齐驿站 8 员工 |
| 63 | `st008_staff2` | STAFF | 8 开发区分站 | 补齐驿站 8 员工 |
| 64 | `st008_staff3` | STAFF | 8 开发区分站 | 补齐驿站 8 员工 |

口令：复用脚本既有 BCrypt 散列（对应公开约定 `demo1234`），**脚本仍只写散列、不写明文**；全部 `status=1`、`pwd_changed=1`、`is_deleted=0`。

### 决策与事实性纠正
- **`boss` 而非新增 `demo_admin`**：既有 `ADMIN` 账号已有 4 个（`admin`/`admin_pwd0`/`16626369983`/`admin2`），再叠「演示管理者」易混淆；改以易记名 `boss` 作「网页端唯一可登角色（ADMIN）」的代表账号，语义清晰且易口头讲解。
- **`staff2` 命名保留用户建议**：`staff2`（驿站 2）与既有 `staffNN`（数字后缀=id）形似，已在 `remark` 与文档中明确其含义为「驿站 2 的备用员工」，`demo-accounts.md` §1.3/§3.3 标注用途，避免误读为「第 2 号员工」。
- **禁用样例不再另加**：既有 `staff56`（id 56，`status=0`）已覆盖「含禁用」筛选与看板口径，按要求**不重复添加**。
- **新增账号仅落 `employee`**：登录与端准入只依赖 `employee`；新账号的考勤/工资/档案等业务数据有意留空（避免种子体积与维护成本膨胀），已在文档 §7 明示为「正常空态」而非缺陷。

### 静态自检与未验证项
- 已做静态自检（本机无 MySQL，用脚本解析 + 逐项比对）：新增 8 行**每行单元格数 = 列数 = 17**（括号深度感知的逗号计数，全通过）；全 `employee` 解析得 **64 行**，`id` 连续 1–64、**username 唯一**（含与既有 56 比对无冲突）；枚举合法（`role`∈{ADMIN/STATION_ADMIN/STAFF}、`gender`∈{1,2}、`status`∈{0,1}、`pwd_changed=1`、`is_deleted=0`）；逻辑外键指向存在（`dept_id`∈{1,2}⊂已建 1–6，`station_id`∈{1,2,8} 或 `NULL`⊂已建 1–8）；`phone` 为占位号 `13900000057–13900000064`。
- **幂等性推演**：`employee` 在第 0 节被整表 `DELETE`，§2.1 与 §2 同一轮次执行且**不含 `IF NOT EXISTS`/无重复键风险**（同一 INSERT 批次内 id/username 唯一）；重跑脚本 = 先清后插，结果与首次执行一致。
- **未验证项**：本机无 MySQL，**未实跑**；`DATE_ADD(@base, INTERVAL …)` 相对日期表达式、`NULL` 落 `last_login_time`（可空列）、以及 64 行整体 INSERT 的实跑结果**收敛到服务器阶段**由主智能体在 `kdyzgl_test` 执行并核对 `SELECT COUNT(*) FROM employee` = 64。

### 执行与回滚
- 执行（C 档，须主智能体三步授权）：`mysql --default-character-set=utf8mb4 kdyzgl_test < hrm-dev/sql/seed/kdyzgl_test_seed.sql`。
- 回滚：反向 diff 删除 §2.1 块（或直接重跑旧版脚本）；种子为重置型，重跑即回种子态。
- 仅补新增账号：见 `docs/demo-accounts.md` §6（单跑 §2.1 INSERT 的替代方案）。

### 联动项（非本角色改动）
- 脚本实跑与行数核对、`demo-accounts.md` 的对外发布口径（C 档，主智能体授权后在 `kdyzgl_test` 执行）。

## 2026-09-24 · 测试库联调/演示种子数据（`hrm-dev/sql/seed/kdyzgl_test_seed.sql`）— 只出脚本与文档，未执行

### 概述
为「前端演示站切换为直连真实后端」提供可见数据：产出**独立种子脚本** `hrm-dev/sql/seed/kdyzgl_test_seed.sql`（约 2692 行，32 张表、约 2200 行数据）。**只针对测试库 `kdyzgl_test`**，走人工执行、**不走 Flyway**、不入 `sql/schema` 快照；`courier_station`（现网）与 `kdyzgl`（结构库）绝不触碰（脚本头部有醒目拦截注释）。**未改动 V1–V15、`init.sql` 快照、`hrm-server`/`hrm-demo`/`hrm-admin` 源码与规则文件**；结构/枚举以 `docs/db.md` 与 V1–V15 为准、数据形态参照 Mock 真源（规模裁剪）。**本轮只出脚本与文档，SQL 实跑收敛到服务器阶段。**

### 产物
| 文件 | 说明 |
| ---- | ---- |
| **新增** `hrm-dev/sql/seed/kdyzgl_test_seed.sql` | 14 个分节（0 清理 → 1 组织 → 2 账号 → 3 日志 → 4 考勤 → 5 请假 → 6 KPI → 7 人事 → 8 财务 → 9 工单 → 10 通知 → 11 同步 → 12 包裹 → 13 校验），每节含「为什么播这些」 |
| `hrm-dev/docs/deploy.md` | 新增 §3.6「测试库 `kdyzgl_test` 与联调种子数据」：定位/隔离边界/执行与回滚命令/密码哈希生成与校验/与 Seeder 关系/收敛说明 |

### 表 → 行数（生效于脚本末尾校验块）
`department` 6、`station` 8、`employee` 56、`login_log` 38、`client_log` 8、`attendance_rule` 8、`attendance_shift` 24、`attendance_schedule` 252、`attendance_record` 386、`attendance_makeup` 10、`leave_setting` 1、`leave_request` 12、`leave_log` 27、`kpi_metric` 7、`kpi_score` 110、`hr_profile` 56、`hr_salary` 56、`hr_salary_log` 75、`hr_flow` 9、`hr_flow_step` 54、`payroll_rule` 2、`payroll_rule_item` 9、`payroll` 67、`payroll_item` 536、`work_order` 40、`work_order_timeline` 93、`work_order_transfer` 8、`work_order_dispatch_rule` 5、`notification` 99、`sync_task` 56、`sync_task_log` 166、`parcel` 210。

枚举覆盖：员工三角色齐全（ADMIN / STATION_ADMIN / STAFF）；考勤四态（NORMAL/LATE/EARLY_LEAVE/ABNORMAL）；请假 6 态全；工资单 6 态全；工单 0–3；包裹 0–4（含已退回）；通知类型 1–6。

### 账号与口令
统一演示口令 `demo1234`，`employee.password` 只落 BCrypt 散列（`$2a$10$TVkFYxOLLRGLQ47IMUPhVuLHJW3dDUhDqEx0PSa7bs0tq38jQhjl2`，bcryptjs 生成并 `compareSync` 回环校验通过），**脚本不含明文**。账号：`admin`/`admin_pwd0`/`admin2`/`16626369983`（ADMIN，`station_id` 空）；`st001_admin`、`st002_admin`…`st007_admin`（STATION_ADMIN，各绑 1–7 号站）；其余 STAFF（含 `st001_staff`）。全部 `status=1`、`pwd_changed=1`（免首登强制改密）；末位 `staff56` 置 `status=0` 覆盖「含禁用」口径。

### 决策与事实性纠正
- **不触碰同步配置 5 表**：任务只点名「配置中心四层」需避让，但实测 `SyncConfigSeeder#seedStationsAndOverrides` **同时播种 `sync_station_config` 与覆盖值**（并会为「丰巢智能柜」自动纳管 `MIGRATED_*` 选项）。若预置该表，Seeder 的 `fresh=count==0` 判定会跳过、导致覆盖值缺失。故脚本**对 `sync_station_config` 与四层表一并跳过**，交由 Seeder 幂等播种。
- **`bcryptjs` 非本仓库依赖**：任务称「`hrm-dev/hrm-demo` 有该依赖」不成立（`package.json` 无 bcryptjs、`node_modules` 无）。已改为临时目录安装生成，不改仓库依赖清单（见 deploy.md §3.6）。
- **工单派单规则按 Mock 实际 5 条**（任务写「4 条」）——Mock `db.js#DISPATCH_RULE_SEED` 实为 5 条，脚本按 5 条落。
- **时间锚点用 `@base := CURDATE()` 而非写死日期**：演示站直连后端需「今日/近 7 天/近 30 天」持续可见，写死日期会让数日后执行时页面全空；单锚点保证同日可复现。
- 考勤排班/打卡/补卡**只投城东驿站**（对齐 Mock 的 `STATION_ID=1`），避免 8 站满量拖慢首屏；非城东驿站请假样本的 `counted_days` 为近似值（无排班真源）。

### 静态自检与未验证项
- 已做静态自检：列名存在性（逐表比对 V1–V15 DDL）、类型匹配、枚举取值合法性、非空约束（`NULL` 仅落在可空列）、逻辑外键指向的记录确实存在（如 `shift_id`∈1–24、`flow_id`∈1–9、`payroll_id`∈1–67、`task_id`∈1–56、`leave_id`∈1–12）、JSON 列内容合法、**每行单元格数 = 列数**（生成器内置断言，全表通过）、无未加引号的字符串/枚举字面量。
- **未验证项**：本机无 MySQL，**未实跑**；`@base` 相对日期表达式、JSON 列的类型转换、以及 MySQL 8 对 `VALUES` 中表达式/`CONCAT` 的接受度**收敛到服务器阶段**由主智能体在 `kdyzgl_test` 实跑核对。

### 执行与回滚
- 执行：`mysql -uroot -p kdyzgl_test < hrm-dev/sql/seed/kdyzgl_test_seed.sql`（执行属 C 档，须主智能体三步授权）。
- 幂等：先按「子表→父表」整表 `DELETE` 再 `INSERT`，可重复执行；**重跑即回滚到种子态**。
- 不影响结构：脚本无 DDL、不 `DROP`/不 `TRUNCATE`；`auth_trusted_device` 与同步配置 5 表不在清理范围内。

### 联动项（非本角色改动）
- 脚本实跑与行数核对（C 档，主智能体授权后在 `kdyzgl_test` 执行）。

## 2026-09-24 · 后端 M4 批（登录契约改造）：短信验证码登录 + 设备信任 + 3 天到期强制重登 — 与前端 Mock 逐条对齐

### 概述
落地 `hrm-server` 登录契约改造 **M4**：新增 A1 `/auth/sms/send`、A2 `/auth/sms/login`、B2 `/auth/device/verify`、C1/C2 `/auth/devices`、D1 `/auth/captcha`；改造 B1 `/auth/login`（仅追加**可选**入参与可选出参，既有字段零变更）。会话时效统一为 **3 天**（`jwt.expire` 86400→**259200**，与 `hrm.auth.session-ttl-seconds` 双控）；到期按用户裁定**强制重新登录**，返回**专用码 1108（HTTP 200 + code=1108）**，与前端 Mock `engine.js#resolveUser` 完全一致。设备信任改为**服务端签发 `device_token`**（HttpOnly + Secure Cookie 下发，库中只存 SHA-256 摘要），落实 `security-auth-review.md` §3/§4 六条加固建议。**B3 `/auth/session/renew` 不实现**（用户裁定不做短信续期）。未改任何迁移脚本、`api.md`/`db.md` 与被保护文档。

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| **新增** `enums`·ErrorCode 11xx 段 | 补 1101–1109（1101 频控/1102 验证码错/1103 尝试超限/1104 分流码/1105 通道不可用/1106 图形码错/1107 设备撤销/1108 到期/**1109 未绑手机号**），文案逐字对齐 Mock `CODE_MESSAGE` |
| **新增** `service/auth/support/` 七个支撑类 | `DeviceFingerprint`（HMAC-SHA256 指纹 + 生产空盐 fail-fast）、`DeviceTokenCodec`（256bit 令牌 + SHA-256 摘要 + 恒定时间比较）、`TrustedDevicePolicy`、`SmsCodeStore`（验证码/失败计数/全维频控 Redis）、`DeviceTicketStore` + `DeviceTicket` + `DeviceSnapshot`、`CaptchaStore`、`DeviceCookieSupport`、`TrustedDeviceRegistry`、`AuthRequestContext`、`SessionExpiryPolicy`、`EndAdmissionPolicy` |
| **新增** `entity/AuthTrustedDevice` + `mapper/AuthTrustedDeviceMapper` | 映射 V15 已建表（18 列），只存令牌摘要与 HMAC 指纹；撤销软标志 `revoked/revoked_at` |
| **新增** DTO/VO | `DeviceInfo`、`SmsSendRequest`、`SmsLoginRequest`、`DeviceVerifyRequest`；`SmsSendVO`、`TrustedDeviceVO`、`CaptchaVO` |
| `controller/auth/AuthController` | 新增 6 端点 + 3 处设备令牌 Cookie 下发（登录/短信登录/设备验证）；`/auth/devices*` 显式 `@RequireRoles` |
| `service/auth/AuthService(+Impl)` | 新增 sendSms/smsLogin/deviceVerify/listDevices/revokeDevice/captcha；`login` 追加可选能力（端准入/设备信任分流）；`changePassword` 追加「改密即失效」 |
| `filter/JwtAuthFilter` | 新增**重认证窗口判定**：超 `hrm.auth.periodic-reauth-seconds` 返回 **HTTP 200 + code 1108**（会话在→取会话认证时刻；会话已随 TTL 消失→取已验签 JWT `iat`） |
| `util/JwtUtil` | 新增 `expiry-grace-seconds` 校验宽限（`setAllowedClockSkewSeconds`，仅使「刚过期」可被解析以返回 1108，**不延长可用期**）；保留 2 参构造器（单测兼容） |
| `util/DesensitizeUtil` | 新增 `maskIp`（末段打码，对齐 Mock） |
| `common/PublicEndpoints` | 白名单 2 → **6**（新增 A1/A2/B2/D1；`/auth/devices*` 不入白名单） |
| `common/SessionInfo` | 新增 `loginEpochSeconds`（服务端权威 `lastAuthAt`） |
| `config/AuthProperties` | 新增 `expiry-grace-seconds`/`captcha-ttl-seconds`/`device-ticket-ttl-seconds`/`max-trusted-devices-per-employee`/`device-fingerprint-salt` |
| `config/SmsProperties` | 新增 `ip-hourly-limit`/`device-hourly-limit`/`account-daily-limit`/`dev-fixed-code`（降级通道联调用，生产不可达） |
| `resources/application.yml` | `jwt.expire: 259200`；`hrm.auth.*`/`hrm.sms.*` 新增键逐键注释（**全 ASCII 键**，规避 YAML 非 ASCII 键被 Spring 静默归属） |
| `entity/HrFlow` | **移除** `operatorId`/`operatorName` 的 `@TableField(exist = false)`（V14 已补列，含 2 处 `TODO(扩展)` 已消解） |
| `service/employee/impl/EmployeeServiceImpl` | `forceOffline` 追加「使该员工已信任设备全部失效」（禁用/删除/重置密码/角色降级同口径） |
| 单测 | 新增 10 个测试类（见下）；`PublicEndpointsTest` 白名单断言由 2 更新为 6 |

### 关键设计取舍
- **1108 与 401 必须区分**：`HTTP 200 + code=1108` 为「正常安全生命周期到期」，前端据此显示「登录已到期」提示条并带 `redirect` 回跳；`401` 为「登出/被顶下线/被强制下线」（静默回登录页）。与前端 Mock `engine.js` 口径逐字一致。
- **到期判定用服务端单调时钟**：优先取会话内 `loginEpochSeconds`；会话键已随 Redis TTL 消失时，退回取**已验签 JWT** 的 `iat`（客户端不可篡改）。
- **`expiry-grace-seconds`（默认 300s）的引入理由**：`jwt.expire` 与窗口均为 259200s 时，两者在同一瞬间到期，JWT 解析会先抛过期异常 → 只能给出裸 401，前端拿不到 1108。该宽限**仅放宽 JWT 校验**，使服务端能在超窗后仍识别请求并返回 1108；**鉴权窗口恒为 3 天，宽限期内不存在任何「可用」请求**（超窗即 1108 拒登）。
- **设备信任凭据换代**（安全报告 §4.2 高危项整改）：Mock 以「前端 deviceId 命中受信记录」判定放行，属可伪造路径；后端改为**服务端签发 `device_token`**（HttpOnly + Secure + SameSite=Lax，限 Path=/api/v1/auth）+ **摘要匹配**，前端 deviceId/platform/model 等仅作弱信号（指纹/审计/展示），**不作放行依据**。前端无需改代码（同源请求自动携带 Cookie；令牌对 JS 不可见）。
- **验证码一次性 + 全维限频 + 恒定时间比较**：`SecureRandom` 生成、TTL 走配置、失败计数达标即作废；同号间隔/同号日限/同 IP 时限/同设备时限/同账号日限五维；比较用 `MessageDigest.isEqual`。
- **改密/禁用等安全事件**：既删该员工全部会话，又使其全部受信设备失效（否则旧设备凭旧令牌免二次验证）。
- **降级通道固定码**：仅当未配置厂商凭据（非生产；生产 `SmsConfigGuard` fail-fast）时使用 `hrm.sms.dev-fixed-code`（默认 `000000`，与前端演示资产一致），绝不入日志/响应体。

### 事实性纠正
- 任务称「`jwt.expire` 仍是 86400，本批统一为 259200，并同步核对 `expiresIn` 出参语义变化」——已核对：`expiresIn` 语义仍为「Token 有效期（秒）」，**仅时长由 1 天改为 3 天**，字段名/类型/位置未变，不构成契约变更。
- 任务称设备信任表为「18 列 / 3 索引」——与 V15 实测一致（唯一键 `uk_auth_trusted_device_emp_fp` + 2 普通索引）。
- 任务附表 2 `auth_sms_log`（架构「可选但建议」）**未随 V15 建表**，故本批不写短信审计表；短信审计仅落应用日志（**不含验证码**），如需落表须由主智能体排期另立迁移（属 C 档）。
- 任务称「前端短信场景仅需 `twoFactorTicket`」——实测前端 `sendSms({scene:'DEVICE_VERIFY', twoFactorTicket, deviceId})` **确实不传手机号**，与 Mock 一致，故 A1 设备场景以票据定位员工（验证码按 employeeId 暂存）。

### 验证状态
本机无 JDK/Maven：**未编译、未执行任何单测**。静态自审通过（导入/注解/签名/分层/参数化 SQL/配置键与强类型类逐键对应/Lombok getter/无密钥硬编码）。新增 JUnit 覆盖：会话 3 天与小时窗边界、端准入（WEB→1110、H5+boss、空值/非法值）、设备令牌熵与摘要、受信设备有效期与撤销、票据到期、指纹 HMAC 与生产空盐 fail-fast、验证码 TTL/尝试上限/恒定时间比较、全维频控窗口、受信设备 upsert/撤销/全量失效、11xx 码值与文案。**编译与单测执行、Flyway 与联调实测收敛到服务器阶段**（`mvn -B clean test` + 接口联调）。

### 联动项（非本角色改动）
- `api.md` 需补 11xx 段与 A1/A2/B2/C1/C2/D1 端点（**本批禁改该文档**，须主智能体排期）。
- 生产外置配置需补齐：`jwt.secret`、`hrm.auth.device-fingerprint-salt`、`hrm.sms.aliyun.*`（真实值由主智能体托管下发，仓库只留 `change_me_*`）。
- 新增 4 个公开端点（A1/A2/B2/D1）使外部攻击面扩大，**公网暴露前须再过 P0.5 安全评估**（security-auth-review §5-R9）。

### 回滚
- 代码回滚：反向 diff 还原本批新增/修改的 `hrm-server` 文件（无迁移、无数据变更，回滚无数据丢失）。
- 兼容性：既有 4 端点出入参与错误码未变，老前端不受影响；`PublicEndpoints` 若回滚则新增端点随之失效（前端调用返回 401）。
- 会话时效：`jwt.expire` 回滚为 86400 后，存量 3 天 Token 仍有效至其自身 `exp`。

## 2026-09-24 · 服务端数据库 V14 + V15（`hr_flow` 补创建人两列 + 新建设备信任表 `auth_trusted_device`）— 只出脚本与文档，未执行迁移

### 概述
登录体系改造批次 **M3** 的结构先行（P4）产出：① **V14** 修复 `hr_flow` 缺失创建人列（V7 建表缺口，曾致 `HrFlow.operatorId/operatorName` 只能 `@TableField(exist = false)`）；② **V15** 新建 `auth_trusted_device`（服务端持有设备信任态）。同步刷新快照 `sql/schema/mysql/init.sql` 与文档 `docs/db.md`（新增第 10 章）。**本轮只出脚本与文档，不执行任何迁移（执行属 C 档，须主智能体三步授权后由运维执行）**；V1~V13 未改动，表总数 37 → 38。

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| **新增** `hrm-server/src/main/resources/db/migration/mysql/V14__hr_flow_operator_columns.sql` | `ALTER TABLE hr_flow ADD COLUMN operator_id BIGINT / operator_name VARCHAR(50)`（均可空、列尾追加、INSTANT 无锁无重建）；自带 `DROP COLUMN` 回滚段 |
| **新增** `hrm-server/src/main/resources/db/migration/mysql/V15__auth_trusted_device.sql` | 新建 `auth_trusted_device`（18 列；唯一键 `uk_auth_trusted_device_emp_fp` + 2 普通索引）；自带 `DROP TABLE` 回滚段 |
| `sql/schema/mysql/init.sql`（快照） | 头部版本清单更新至 V3..V15；`hr_flow` 追加两列；末尾追加 `auth_trusted_device` 建表 |
| `docs/db.md` | 文档版本 v2.0 → v2.1；§8.5.4 补 `hr_flow` V14 字段与联动项；§8.13 补 V14/V15；新增 **§10 登录体系改造表结构设计**（字段/索引/枚举/逻辑关系/不建表取舍/一致性核对）；§9.1 增 Q-DB-5/6、§9.3 计数与检查项、§9.4 增 U-11/12 |

### 关键设计取舍
- **设备信任态由服务端持有**（`security-auth-review.md` §4.2 高危缺陷整改）：库中只存服务端签发 `device_token` 的 **SHA-256 摘要** `device_token_hash`，**绝不存明文**；前端设备属性经服务端外置盐 HMAC 后落 `device_fingerprint`，**仅弱信号/审计用，不作放行依据**。
- **唯一索引为决策 D7 的显式例外**：`uk_auth_trusted_device_emp_fp (employee_id, device_fingerprint)` 支持幂等 upsert；本表撤销用业务标志 `revoked`/`revoked_at`（**不用 `is_deleted`**），撤销后复用同一行重信，规避 D7 担心的「逻辑删除后唯一键阻止复用」场景。已登记为 §9.1 Q-DB-5，如相关方要求严格回 D7 则另立版本。
- **敏感/短生命周期数据不建表**：短信验证码、验证码失败计数、二次验证票据、`device_token` 明文、登录会话、指纹盐一律走 Redis/外置配置（db.md §10.4），守「验证码绝不入日志/审计」红线。
- **索引命名纠正**：架构 §4.2.1 用缩写前缀 `auth_device`，与 db.md「索引名带完整表名前缀（双库可对照）」冲突，改按表名 `auth_trusted_device` 前缀（`uk_auth_trusted_device_emp_fp` 等），语义不变。

### 事实性纠正
- 任务背景称设备信任模型在 `multi-client-architecture.md` **§3.2**；实测该文档 **§3 为「服务器拆分方案」**，设备信任模型实为 **§4.2**（§4.2.1 表清单）。V15 与 db.md §10 依据按 §4.2 落地。
- 任务称快照为「37 张业务表」；本批后为 **38 张**（+`auth_trusted_device`）。
- 任务称「唯一性沿用 D7 … 除非架构明确要求唯一索引」：架构 §4.2.1 确对设备信任表**明确要求唯一键**，故按唯一索引落地并登记为 D7 例外（非疏漏）。

### 联动项（非本角色改动）
- 补列完成后，由**后端工程师**移除 `entity/HrFlow.java` 中 `operatorId` / `operatorName` 两字段的 `@TableField(exist = false)`（含 2 处 `TODO(扩展)` 注释），使创建人可持久化并回填 `HrFlowVO` 出参。
- 若纳入 `auth_sms_log`（架构 §4.2.1 表2「可选但建议」）须由主智能体排期另立 V16（见 db.md §9.1 Q-DB-6）。

### 验证状态
本机无 MySQL：**未实跑迁移**。静态自检通过（语法/索引名全局唯一/表名与列名无冲突/新列可空/快照与迁移逐表一致/无 `SELECT *`/无存储过程·触发器·物理外键/无真实数据与凭据）。**DDL 实跑与 `SHOW CREATE TABLE` 比对、Flyway `flyway_schema_history` 校验、唯一键 upsert 行为验证收敛到服务器阶段**（db.md §9.4 U-1/U-2/U-11/U-12）。

### 回滚
- 结构回滚：按各脚本尾部 `-- 回滚:` 段人工执行（V14 `DROP COLUMN` 两列；V15 `DROP TABLE auth_trusted_device`）。
- 文档/快照回滚：反向 diff 还原 `init.sql` 与 `db.md` 本章改动即可（不涉生产数据）。
- 迁移**执行**属 C 档：须主智能体三步授权（影响范围/可逆性/回滚步骤）+ 上线前备份库，由运维执行。

## 2026-09-24 · PC 端仅 ADMIN 可登录（服务端强制校验）— 新增可选头约束 + 新错误码 1110，零既有出入参变更

### 概述
落地用户需求「员工和站长无权限登录 PC 端，只有管理员（ADMIN）账号可登录」，并落实 `security-auth-review.md` §4.5「三端应各自适用不同角色口径」的加固建议。服务端在 `POST /api/v1/auth/login` 登录链路中新增**端维度角色强制校验**：当端类型归一为 PC 管理端（`ClientType.ADMIN`）时，仅允许角色在 `hrm.auth.pc-allowed-roles` 集合内（默认 `ADMIN`）；站长/员工声明该端登录一律拒绝。

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| **新增** `main/java/com/qiujie/service/auth/support/ClientRolePolicy.java` | 纯逻辑类：端类型归一判定 + 角色-端允许集合判定 + 逗号分隔配置解析（去空白/忽略大小写/丢弃非法角色名），无 Spring/Redis 依赖，可离线单测 |
| `main/java/com/qiujie/config/AuthProperties.java` | 新增 `hrm.auth.pc-allowed-roles`（`List<String>`，默认 `["ADMIN"]`） |
| `main/java/com/qiujie/service/auth/impl/AuthServiceImpl.java` | 注入 `AuthProperties`；在「密码 + 状态校验通过之后、签发 Token 之前」新增端维度角色校验，不通过则写失败登录日志（原因「该账号无权登录此端」）并抛 `ErrorCode.LOGIN_CLIENT_NOT_ALLOWED(1110)` |
| `main/resources/application.yml` | `hrm.auth` 下新增 `pc-allowed-roles: ADMIN`（标量、全 ASCII），并补逐键注释 |
| **新增** `test/java/com/qiujie/service/auth/support/ClientRolePolicyTest.java` | 覆盖端类型缺失/非法/大小写混合、三角色 × 各端、配置空/含非法角色名/含空格/null 的边界 |
| **新增** `test/java/com/qiujie/enums/ClientTypeTest.java` | 端类型归一单测（缺失/非法/大小写/含空白） |
| `test/java/com/qiujie/enums/ErrorCodeSegmentTest.java` | 补 `LOGIN_CLIENT_NOT_ALLOWED(1110)` 码值与文案断言（防回归） |

### 契约影响（逐项）
- **入参**：`/auth/login` JSON 入参结构**不变**；仅既有可选请求头 `X-Client-Type` 从「仅归一存储」升级为「参与登录角色约束」（缺失/非法仍回落 WEB，不施加约束）。
- **出参**：`LoginVO` 结构**不变**。
- **错误码**：新增可能返回 `1110 该账号无权登录此端`（HTTP 200 + `{code:1110}`，与 10xx 业务码同映射）；未命中该场景时行为与改造前逐位一致。
- **未改** `/auth/logout`、`/auth/me`、`/auth/password` 及任何其他接口；未实现 M3/M4 内容（设备信任表、短信端点、3 天重认证状态机）。
- **`api.md` 未同步**（本批属禁改清单文件），须由主智能体后续回填：`/auth/login` 的可选头语义与 11xx 段 1110。

### 安全定位（为什么不是安全边界）
端类型来自客户端可伪造/可省略的 `X-Client-Type` 头，故本约束是**产品/审计约束**而非鉴权边界；真正的安全边界恒为「角色 + 数据范围」——授权以会话中的真实 `role` 为准。伪造端类型（如声明 `H5` → 归一为 `WEB`）绕过本校验者，其权限仍由 `role` 决定，**不会获得任何额外权限**。

### 事实性纠正
- 任务要求「新增专用错误码」，但工作区 `ErrorCode.java`（未提交改动）**已存在** `LOGIN_CLIENT_NOT_ALLOWED(1110, "该账号无权登录此端")` 且编号不与 M4 规划的 1101–1109 冲突，故**复用不重复新增**（重复定义将导致枚举常量重复编译错误）。
- 任务「§3 加固建议第 5 条」在 `security-auth-review.md` 中无逐字对应；实际依据为 §4.5「三端独立客户端共用同一后端」条目中「三端应各自适用不同角色口径（员工端禁管理端点等）」及 §1.1 高风险清单，本批即该建议在**登录入口**侧的最小落地。
- 需求「端类型为 PC（网页端/PC 管理端）」与「不传 `X-Client-Type` 按 WEB 且不限制角色」需调和：本批按**仅 `ClientType.ADMIN`（PC 管理端）受约束**实现，`WEB`（缺省回落）不施加约束以保持改造前行为；已在 `ClientRolePolicy` 标注 `TODO(扩展)`（若产品要求 WEB 同样收紧，属契约/口径变更，须先经用户确认）。

### 验证状态
本机无 JDK/Maven：**未编译未实跑**，IDE 诊断 0 报错，静态自审通过（包引用/方法签名/注解/Lombok getter/配置键与强类型逐键对应/未对不可变集合传入 null），**收敛到服务器阶段**由主智能体 `mvn package` + `mvn test` + 启动复验（重点：`pc-allowed-roles` 绑定成功；`X-Client-Type: ADMIN` + STAFF 账号返回 1110；不传该头行为与改造前一致）。

### 回滚
反向 diff 还原上述 6 个改动文件 + 删除 2 个新增测试类即可（不涉 DB、不涉既有出入参结构；默认值 `ADMIN` 生效后 PC 端行为仅对显式声明 `X-Client-Type: ADMIN` 的客户端变化）。

## 2026-09-24 · 服务端首次启动配置绑定失败修复（`hrm.algo.dispatch.keyword-weights`）— 零契约/零口径变更

### 概述
服务器首次启动 `hrm-server.jar` 报 `Failed to bind properties under 'hrm.algo.dispatch.keyword-weights' to java.util.Map<String,Double>`（`ConverterNotFoundException: Double → Map`）。编译与 Flyway 均已通过，仅此一项阻塞启动。已排除 YAML 语法与文件编码（`file -i` = utf-8，`od -c` 中文为正确 UTF-8 字节），属**框架行为边界**。改 2 个文件（Java + YAML）。

### 根因（框架层）
Spring Boot 绑定前将属性名经 `ConfigurationPropertyName.adapt()` 规范化，该方法**静默丢弃非 ASCII 字符**。YAML 拍平后的子键 `hrm.algo.dispatch.keyword-weights.破损`（及 丢失/故障/投诉）被归并为同一父名 `hrm.algo.dispatch.keyword-weights`，Binder 遂把该父名的**首个值**（`1.0`）当作整张 Map 的值 → 目标类型 `Map<String,Double>` 找不到 `Double→Map` 转换器而失败。这解释了「两次报错 `Value` 都是 `1.0`、`Origin` 随首个值移动」的现场特征。官方 Externalized Configuration 亦载明「Map 键含非小写字母数字须用 `[key]` 记法」。故非编码/语法问题，flow 与 block 两种写法均无效。

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| `main/resources/application.yml` | `dispatch.keywordWeights`（嵌套 Map + 中文键）→ `dispatch.keywordWeightList`（列表，中文只作**值**）；订正原「block mapping 可修复」的误导注释 |
| `main/java/com/qiujie/config/AlgoProperties.java` | `Dispatch.keywordWeights` 字段（`Map<String,Double>`）→ `keywordWeightList`（`List<KeywordWeight>`，ASCII 字段名）；新增派生只读 `getKeywordWeights()`（消费方签名不变）；新增 `KeywordWeight` 内部类 |

### 取舍
选「列表承载 + 派生 Map」（方案 A）：中文关键词作**值**不触发框架限制；ASCII 字段名可正常绑定；新增任意中文关键词无需改代码（优于方案 B 的 ASCII 键映射表，无需维护映射常量）；消费方 `WorkOrderServiceImpl:466` 与 `WorkOrderKeywordClassifier.classifyByScore` 签名不变。方案 C（自定义 Converter）复杂度高且仍绕不开非 ASCII 名，不选。

### 同类风险排查
逐项核对 `hrm.algo.*` 全部嵌套 Map / 复杂集合：`kpi.tieredTiers`、`kpi.levels`、`kpi.quantile`、`payroll.attendanceFieldMap`、`leave.countModeMap`、`schedule.weights`/`sa`、`attendance.anomaly`、`dispatch.slaHours`（数字键，"0"/"1"/"2" 属合法名字符）、`dispatch.weights`、`parcel.forecast`/`capacity`/`outlier`/`page`、`ratelimit`、`log`、`sync` —— **键均为 ASCII，无风险**；全库 yml 仅 `keywordWeights` 一处非 ASCII 键（其余 CJK 均出现在注释或值中），已修复。

### 事实性纠正
任务提示「block mapping + 键加引号可稳定解析」不成立：报错 `Origin` 随首个值行号移动（189:15 → 新位置）即证明换写法无效，根因在属性名规范化而非 YAML 形态。

### 验证状态
本机无 JDK/Maven：**未编译未实跑**，IDE 诊断 0 报错，静态自审通过，**收敛到服务器阶段**由主智能体 `mvn package` + 启动复验（重点：启动不再报 keyword-weights 绑定失败；`keywordWeighted=false` 时派单行为与现状逐位一致）。

### 遗留与 TODO(扩展)
- `TODO(扩展)`：`algo-hrm-server.md §11.6`、`server-architecture.md §5.1` 仍登记旧键 `hrm.algo.dispatch.keywordWeights`（属禁改清单文件），待主智能体回填为 `keywordWeightList`。
- 若外置 `application-prod.yml` 曾覆盖 `hrm.algo.dispatch.keyword-weights.*`，该覆盖已失效，须改用 `keywordWeightList` 列表形式（当前仓库无此类外置配置）。

### 回滚
反向 diff 还原上述 2 个文件即可（不涉 DB、不涉 HTTP 契约与业务口径；`keywordWeighted` 默认 false、权重组默认值逐位不变）。

## 2026-09-24 · 服务端单元测试修复（`mvn test` 441 run / 8F / 10E）— 零 HTTP 契约与口径变更

### 概述
服务器首次实跑 `mvn test`：441 用例，8 failures + 10 errors，集中在 6 个类。逐条比对 Mock（行为规格）/ `demo-sync-config-design.md` 后判定：**2 处实现缺陷、6 处测试/夹具缺陷**。修复后应全绿。改 8 个文件（2 实现 + 5 测试 + 1 夹具），不动 `pom.xml`、不删用例、不弱化断言、不加 `@Disabled`。

### 逐条判定
| # | 测试类.方法 | 判定 | 契约依据 | 修复动作 |
| - | ---- | ---- | ---- | ---- |
| 9–17 | `ClientLogSanitizerTest`（9 用例 NPE） | 实现 bug | 白名单 `LEVELS/SOURCES` 为 `List.of` 不可变列表，`contains(null)` 抛 NPE | `ClientLogSanitizer.sanitize` 对 level/source 先判空再白名单命中 |
| 5 | `SyncConfigValidatorTest.timeRangeBoundary` | 实现 bug | `demo-sync-config-design.md:87`「结束允许 24:00」；`SyncClock.isEndClock` 及 `normalizeExtraAttrs` 同口径 | `validateTimeRange` 结束位改判 `isEndClock` |
| 1 | `AttendanceDetailPolicyTest.otherDimensions` | 测试 bug | Mock `attendanceStore.detailMembers`「按卡状态过滤 + 去重」→ NORMAL 命中 {1,3} | NORMAL 期望 1 → 2 |
| 2 | `HrProfileValidatorTest.invalidDictionaryRejected` | 测试 bug | Mock `routes/hr.js:74/76` 拼的是 `EDUCATION_KEYS/CONTRACT_TYPE_KEYS`（枚举键），非 `*_LABEL`（中文标签） | 期望文案改英文枚举键 |
| 3 | `LeaveIntervalPolicyTest.countedDaysExcludesRestDays` | 测试 bug | Mock `leaveStore.halfUnitsOf/countedDaysOf`；起始日 PM 仅计 0.5，10-03 计 1.0 | 期望 2.0 → 1.5 |
| 4 | `SyncConfigValidatorTest.normalizeConstraints` | 测试 bug | Mock `syncConfigStore.js:996` `minLen` 缺省归一为 0 | 删除误置的 `text.put("minLen", 1)` 夹具行，期望 0 不变 |
| 6/7/8 | `SyncCsvImporterTest.planAllUpdate/planAllSkip`、`SyncCsvRoundTripTest.semanticRoundTrip` | 夹具 bug | Mock 种子 `syncConfigStore.js:111` `timeout_minutes` 约束 `{min:1,max:120}`（非 0-10） | `SyncTestFixtures.items()` 给 `timeout_minutes` 独立约束 1-120 |
| 18 | `SessionUtilTest.saveBySidEvictsSameClientSameDevice` | 测试 bug | Mockito 约束：matcher 与字面量不可混用 | 字面量参数改用 `eq()` |

### 改动
| 文件 | 改动摘要 |
| ---- | ---- |
| `main/.../service/support/ClientLogSanitizer.java` | level/source 白名单命中前判空（+2 行注释） |
| `main/.../service/sync/support/SyncConfigValidator.java` | `validateTimeRange` 格式判定 `isClock(end)` → `isEndClock(end)` |
| `test/.../service/attendance/support/AttendanceDetailPolicyTest.java` | NORMAL 期望 2（+ 契约注释） |
| `test/.../service/hr/support/HrProfileValidatorTest.java` | 字典非法文案改枚举键（+ 契约注释） |
| `test/.../service/leave/support/LeaveIntervalPolicyTest.java` | 期望 1.5（+ 注释订正） |
| `test/.../service/sync/support/SyncConfigValidatorTest.java` | 去掉误置 minLen 夹具行（+ 契约注释） |
| `test/.../service/sync/support/SyncTestFixtures.java` | `timeout_minutes` 独立约束 1-120 |
| `test/.../util/SessionUtilTest.java` | 第 97 行改 `eq()` matcher（+ import） |

### 事实性纠正
- 任务提示「#2 属实现缺陷、Mock 用中文标签」不成立：中文标签是**出参 `educationLabel`** 用（`hrStore.js:384`），校验文案仍是枚举键（`routes/hr.js:74`）。实现与 Mock 一致，故改测试。
- Mock `syncConfigStore.js:535` 的 `validateTimeRange` 用 `isClock(end)`，与 `demo-sync-config-design.md:87`、`syncConfig.js`/`syncConfigCsv.js` 的 `isEndClock` 口径**自相矛盾**（`allowEnd2400` 分支在此变为死代码）。禁改 `hrm-demo`，故按设计文档口径修 Java 实现；Mock 该行为偏差登记为遗留（交主智能体裁定是否单独修演示工程）。

### 验证状态
本机无 JDK/Maven（`java` 未识别）：**未编译未实跑**，静态自审 + IDE 诊断 0 报错，**收敛到服务器阶段**由主智能体 `mvn test` 复验。

### 遗留与 TODO(扩展)
- `TODO(扩展)`：Mock `syncConfigStore.js:535` 与设计文档口径不一致，待主智能体裁定是否修正演示工程（本批不动）。
- 无产品/口径待裁定项（本次修复均不作口径变更）。

### 回滚
反向 diff 还原上述 8 个文件即可（不涉 DB、不涉前端、不涉 HTTP 契约与业务口径）。

## 2026-09-24 · 服务端编译修复（HrFlow 创建人字段 / KPI 排名承载类型）— 零契约变更

### 概述
首次在服务器（JDK 17 + Maven 3.6.3）执行 `mvn -B -DskipTests package`，EXIT=1；450+ 源文件中**仅 2 个文件 10 处错误**。按「修根因、最小改动、不改 HTTP 契约与业务口径」修复，共改 2 个文件。

### 改动
| 文件 | 改动摘要 | 依据 |
| ---- | ---- | ---- |
| `entity/HrFlow.java` | 新增 `operatorId` / `operatorName` 两字段，标注 `@TableField(exist = false)`（非持久化） | hr_flow 表无 `operator_id` / `operator_name` 列（db.md §8.5.4、V7__hr.sql），不能映射为持久化列 |
| `service/kpi/impl/KpiScoreServiceImpl.java` | `detail()` 内 `entry` 承载类型由 `KpiRankingPolicy.Entry` 改回本类私有 `RankedEntry`（1 行 + 注释） | `KpiRankingPolicy.Entry` 是「排名输入行」（3 参，被 `competitionRanks` 消费、被 `KpiRankingPolicyTest` 固定），不承载 rank/level |

### 根因
1. **HrFlow**：Service 按 Mock `toFlowVO.operatorId/operatorName`（流程**创建人**）在实体上 `setXxx/getXxx`，但 `hr_flow` 建表**缺**这两列（其余含操作人的表——`hr_flow_step`/`hr_salary_log`/`leave_log`/`work_order_timeline` 等——均已落列）。属**表结构与出参契约的缺口**，非实体漏写。为不生成不存在列导致运行时 SQL 报错，标 `exist = false` 暂不持久化。
2. **KPI**：`detail()` 的局部变量类型被写成 `KpiRankingPolicy.Entry`，而 `rankedEntry()` 实际返回私有 `RankedEntry`——一次改名/抽取后的**类型注解残留**。二者非重复定义：`Entry`（id + 总分 + 达成率，排名输入）≠ `RankedEntry`（rank + 汇总 + 等级，明细页结果承载）。

### 同类隐患排查（方法 + 结论）
1. **实体缺 getter/setter**：javac 本次仅报 10 处错误（远低于默认 `-Xmaxerrs=100` 上限）⇒ 报错清单完整；且若 Lombok 未生效会对全部实体报同类错。**结论：无其他实体成员缺失**（P4/P5 的 `KpiScore`/`KpiMetric`/`HrFlowStep`/`HrSalary` 等本次服务端调用点均解析通过）。
2. **support 纯逻辑类与内部类同名/同义重复**：枚举 `service/**` 全部内部 `record`/`class`（`PayrollServiceImpl.Operator`、`LeaveServiceImpl.LeaveForm`、`SyncConfigCenterServiceImpl.ItemSet`、`SyncCsvImporter.RowResult/ExtraParse`、`LogFingerprintAggregator.Entry`、`KpiScoreServiceImpl.RankedEntry`），并对 `Operator/LeaveForm/ItemSet/Entry/RankedEntry` 做定义处检索。**结论：除 KPI 外无同名/同义重复；`KpiRankingPolicy.Entry` 命名空间内唯一，KPI 一例经判定亦非重复**。
3. **类型混用（BigDecimal ↔ int）**：核对 P7 `AttendanceStat`（`int,int,BigDecimal,int,BigDecimal`）的构造点 `PayrollContextProvider` 与取值口 `byField()`（全 BigDecimal）以及财务消费点。**结论：无 int/BigDecimal 混用；`KpiScoreServiceImpl` 内 int 得分 / BigDecimal 汇总亦一致**。

### 验证状态
本机无 JDK/Maven：**未编译**（静态自审 + IDE 诊断 0 报错），**收敛到服务器阶段**由主智能体 `mvn package` 复验。

### 变更清单（表结构，交数据库工程师；执行属 C 档）
`hr_flow` 补 `operator_id BIGINT NULL COMMENT '创建人（逻辑外键 employee.id）'`、`operator_name VARCHAR(50) NULL COMMENT '创建人姓名快照'`；同步 `init.sql` 快照与 `db.md §8.5.4`，用**新版本号**迁移（禁改历史脚本）。补列后移除 `HrFlow` 两字段的 `exist = false` 即持久化。可逆（DROP COLUMN）。不补列时出参 `operatorId/operatorName` 仅创建响应有值、读取路径为 null（前端未消费，无功能影响）。

### 遗留与 TODO(扩展)
- `HrFlow`：`TODO(扩展): hr_flow 补列后去掉 exist = false`（已就地标注）。
- `RankedEntry` 与 `KpiScoreVO` 字段部分重叠，可在后续批次合并（本批不做，避免超范围）。

### 回滚
反向 diff 还原上述 2 个文件即可（不涉 DB、不涉前端、不涉契约与口径）。

## 2026-09-24 · 前端演示工程（hrm-demo）角色称呼「老板」统一为「管理员 / 管理端」— 纯文案变更

### 概述
按口径「前端的老板称呼全部改为管理员」统一演示工程对外称呼：指「人」用「管理员」，指「端」用「管理端」。
系统角色标识仍为 `ADMIN`；**不含任何标识符 / 路由 / 字段 / 接口 / 装配规则变更**（`boss`、`BOSS`、`bossRoutes`、`/boss/**`、`data-testid`、CSS 类名一律未动）。

### 交付
| 范围 | 内容 |
| ---- | ---- |
| 展示文案 | 角色显示名（`constants/dict.js` KPI 适用角色、`demo/accounts.js` 演示身份 label）、登录品牌副标题、终审/审批状态与驳回阶段文案、空态与提示语等 |
| 代码注释 | 三端源码内中文注释同步 |
| 测试与脚本 | `e2e/*.spec.js` 选择器/断言文本、`scripts/verify-mock.mjs` 与 `verify-mobile-t13-t16.mjs` 的 check 标签（仅改字面量，断言强度未弱化） |
| 文档 | `hrm-dev/docs/demo-*.md` 12 篇术语同步 |

### 边界与未动项
- `e2e` 中 `shot()` 截图名实参（如 `A3-2-老板端首页`）对应已入库产物文件名，按「文件名不动」保留。
- 禁改清单文档（`algo-hrm-server.md` / `multi-client-architecture.md` / `project-tree.md` / `security-auth-review.md`）与本文历史条目未回改（M03 只追加不重写）。
- 环境开关与 Mock 装配（`installMock`、`VITE_MOCK_ENABLED` fail-safe、`resolve.dedupe`）零改动。

### 验证（hrm-demo 实跑）
`verify:mock` 892/892、`verify:mobile` 48/48、`lint` 0 error、`build` EXIT=0；`test` 429 通过 / 1 既有失败（`useAttendanceStatus.spec.js:228`，时间相关，未改其断言）。

### 回滚
按反向 diff 还原受影响文件，或整体丢弃本次未提交改动（不涉生产工程、不涉 `hrm-admin` / `hrm-server`）。

## 2026-09-24 · 服务端 M1 + M2（会话多端化地基 + 配置命名空间 + 短信/地理适配器端口）— 零契约变更

### 概述
按架构师方案 `multi-client-architecture.md` §4 与网络安全评估 `security-auth-review.md` §3/§4 落地 **M1（会话多端化地基）** 与 **M2（配置命名空间 + 适配器端口与降级）** 两批。
**两批均不改变任何 HTTP 契约**（URL / 入参 JSON / 出参 / 错误码一律不动，不新增对外端点，不改 `jwt.expire`）；**不执行任何 DB 迁移**（M3 的设备信任表不在本批）；不实现 M3/M4/M5（设备信任表、登录新端点、11xx 错误码、三端拆分）。

### 交付（M1 · 会话多端化地基）
| 项 | 载体 |
| ---- | ---- |
| sid 生成（SecureRandom 256 bit / 64 位十六进制，不承载可推导信息） | 新增 `util/SessionIdGenerator` |
| 多会话键规范 `hrm:session:{sid}` + 员工索引 `hrm:session:idx:{employeeId}`；互踢粒度降为「端 + 设备」；并发上限淘汰最旧 | 改写 `util/SessionUtil`（新增 `saveBySid/getBySid/deleteBySid/updateBySid/listSids/deleteAllOfEmployee`；旧 `save/get/delete(Long)` 保留兼容） |
| 淘汰决策纯逻辑（同端同设备覆盖 + 最大并发数淘汰最旧，确定性排序） | 新增 `util/SessionEvictionPolicy` |
| 会话解析（sid 优先 + 旧 token 回退 + jti 一致性） | 新增 `util/SessionResolution`；改写 `filter/JwtAuthFilter` |
| 会话/上下文扩字段（只增不减，旧会话反序列化兼容） | `common/SessionInfo`（+sid/employeeId/clientType/deviceId）、`common/LoginUser`（+clientType/deviceId）；`util/UserContext` 增 `getJti()` |
| 端类型归一（客户端自称、不参与鉴权；未知回落 WEB） | 新增 `enums/ClientType` |
| 强制下线语义保持（禁用/删除/改密/重置密码删全部会话） | `util/SessionUtil.deleteAllOfEmployee`；接入 `EmployeeServiceImpl.forceOffline`、`AuthServiceImpl.changePassword` |
| 登录按端+设备写会话；登出按 sid 精确注销 | `service/auth/AuthService(+Impl)`、`controller/auth/AuthController`（读可选头 `X-Client-Type`/`X-Device-Id`） |

### 交付（M2 · 配置命名空间 + 适配器端口与降级）
| 命名空间 | 强类型类 | 键（默认值） |
| ---- | ---- | ---- |
| `hrm.auth.*` | 新增 `config/AuthProperties` | `session-ttl-seconds(259200)`、`max-sessions-per-employee(5)`、`device-trust-enabled(true)`、`device-trust-ttl-seconds(2592000)`、`periodic-reauth-seconds(259200)`、`captcha-enabled(false)` |
| `hrm.sms.*` | 新增 `config/SmsProperties` | `provider(none)`、`aliyun.access-key-id/secret/sign-name/template-code-login/device/reauth(placeholders)`、`code-length(6)`、`code-ttl-seconds(300)`、`send-interval-seconds(60)`、`daily-limit-per-phone(10)`、`max-verify-attempts(5)` |
| `hrm.geo.*` | 新增 `config/GeoProperties` | `provider(haversine)`、`amap.key/security-code(placeholders)`、`fence-radius-meters(200)`、`timeout-millis(3000)` |

- 短信：端口 `service/support/sms/SmsSender` + 枚举 `SmsScene(LOGIN/DEVICE_VERIFY/PERIODIC_REAUTH)` + 结果 `SmsSendResult`；实现 `impl/AliyunSmsSender`（`@Primary`，仅 Key 有效时装配，SDK/HTTP 调用留 `TODO(扩展)`）、`impl/LoggingSmsSender`（降级，只记脱敏日志、绝不打码）；纯逻辑 `SmsCodeGenerator`（SecureRandom + 恒定时间比较）、`SmsThrottlePolicy`（频控窗口/TTL/尝试上限）；守卫 `SmsConfigGuard`（**生产 prod 未配置 Key 启动 fail-fast**）。
- 地理：端口 `service/support/geo/GeoService` + `GeoAddress`；实现 `impl/HaversineGeoService`（默认/降级，逐位等价现有 `HaversineCalculator`）、`impl/AmapGeoService`（`@Primary`，Key 未配置时**不被装配**，逆地理编码留 `TODO(扩展)`）。
- 装配条件：`config/condition/ExternalAdapterConditions`（provider 开关 + 凭据非占位判定）。
- 考勤围栏改走端口：`AttendanceCheckPolicy` 增 `evaluate(ClockFacts, GeoService)` 重载（默认 1 参重载保持原 Haversine 路径），`AttendanceRecordServiceImpl` 注入 `GeoService` 调用；**判定链顺序与 9104 语义不变**。

### 契约不变证明（本批零契约变更）
- 4 个 auth 端点逐项核对：`POST /api/v1/auth/login`（URL/入参 JSON/出参 `LoginVO`/错误码 1001/1002 不变；仅**新增可选请求头** `X-Client-Type`/`X-Device-Id`，缺失即回落 WEB/空，不影响任何既有客户端）；`POST /auth/logout`（200/401 不变，仅注销粒度由「账号」变为「当前 sid」）；`GET /auth/me`；`PUT /auth/password`（成功后仍全端下线）。
- **未改** `jwt.expire`（仍 86400）→ `/auth/login` 出参 `expiresIn` 取值不变；Redis 会话 TTL 取 `hrm.auth.session-ttl-seconds(259200)`，与 JWT exp 双控、取较短者生效（当前有效上限仍为 1 天）。
- 未新增/删除任何端点，未改 `PublicEndpoints`，未改 `ErrorCode`（11xx 属 M4）。

### 安全加固落实（对照 `security-auth-review.md` §3 第 1–5 条）
1. **§4.5①② 会话键可枚举 → 多会话 + 索引**：已采纳——sid 为 256 bit 高熵串，键 `hrm:session:{sid}` 不可枚举；索引 `hrm:session:idx:{employeeId}` 支撑精准/全量吊销。
2. **§4.5③ 精准吊销**：已采纳——登出按 sid；改密/禁用/删除/重置密码按索引删全部。
3. **§4.5④ 单账号最大并发设备数**：已采纳——`hrm.auth.max-sessions-per-employee`（默认 5），超限淘汰最旧。
4. **§4.5⑤ 端维度授权（防低权端冒充高权端）**：**部分采纳**——端类型仅归一存储、**不参与任何鉴权**（权限恒取服务端会话 role），杜绝「自称高权限端」提权；端→角色集合的显式约束属 M5，已标 `TODO(扩展)`。
5. **§4.4④ 短信未配置 Key 的弱兜底**：已采纳——降级仅日志（非生产），**生产 fail-fast**；验证码 SecureRandom、恒定时间比较、频控/尝试上限走配置；日志只打印脱敏手机号与位数，**绝不打码**。
6. **§4.4⑦ 验证码不入日志/异常/响应**：已采纳（本批）——`SmsSender` 端口不落码；`LoggingSmsSender`/`AliyunSmsSender` 日志均不含码。`ClientLogSanitizer` 正则补充 `smsCode/verifyCode/code/captcha` 形态属 P1 日志域改造，**未采纳（本轮不做，见遗留）**。

### 单测（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`util/{SessionIdGeneratorTest, SessionEvictionPolicyTest, SessionResolutionTest, SessionUtilTest(Mockito)}`、`service/support/sms/{SmsCodeGeneratorTest, SmsThrottlePolicyTest, SmsConfigGuardTest}`、`service/support/sms/impl/LoggingSmsSenderTest`、`config/condition/ExternalAdapterConditionsTest`、`service/support/geo/impl/HaversineGeoServiceTest`、`service/attendance/support/AttendanceCheckPolicyPortTest`。
覆盖：sid 格式/高熵/isValid 边界、并发上限淘汰最旧与同端同设备互踢与去重、旧 token 回退与 jti 不一致、索引增删与强制下线、频控窗口/TTL/尝试上限边界、恒定时间比较全分支、生产 fail-fast、日志不含码、端口与 Haversine 等价且确被消费。

### 未验证项（收敛到服务器阶段）
编译打包；`hrm.auth/sms/geo` 属性绑定；Spring 上下文（双 Bean @Primary 解析、`@Conditional` 装配、prod profile fail-fast）；Redis 多会话读写与 TTL；端到端：登录（新 token 走 sid）→ 多端并存 → 单端登出 → 改密全端下线 → 旧 token 回退仍可访问；考勤打卡 9104 行为回归。

### 事实性纠正
1. 架构 §3.5 示例把 `hrm.sms.provider` 写为 `aliyun`——本批仓库内取 **`none`**（无 Key 也可启动，降级明确；生产由 `SmsConfigGuard` fail-fast 强制为 aliyun+Key）。若照抄 `aliyun`，在未配 Key 的开发/联调环境会出现「生产实现被装配但无凭据」的误导态。
2. 架构 §3.5 的 `hrm.geo.provider` 示例为 `amap`——本批仓库内取 **`haversine`**（Key 未配置时 amap 实现不被装配，避免无 Key 空转）。
3. 架构 M1 验收④要求把会话键规范「同步写入 `server-architecture.md`」——与本次任务禁改清单（禁改 `server-architecture.md`）冲突；本批**未改该文档**，键规范已写入 `SessionUtil`/`SessionInfo` 类注释与本文，文档同步按 `multi-client-architecture.md` §8.3 待主智能体排期。

### TODO(扩展)
- 旧 token 回退分支移除条件与路径：`JwtAuthFilter` 已标注（待存量旧会话自然过期且确认无旧键写入方后移除回退 + `SessionUtil` 旧方法）。
- 兼容窗口说明：升级后新登不再写旧键、亦不删旧键，故**升级前签发的旧 token 在新登录后仍可用至其 JWT 到期**（≤ `jwt.expire` 1 天）；如需即时失效须改 `jwt.expire` 或清理旧键（属 M4 契约/运维范畴）。
- `AliyunSmsSender` 接入 SDK/HTTP（`com.aliyun:dysmsapi20170525` 或 HMAC-SHA1 签名）与失败归类 1105；`AmapGeoService.regeo` 接入高德 Web 服务；均须服务器端到端验证后再启用。
- 端 → 允许角色集合的显式约束（M5，防低权端冒充高权端的功能性兜底）。
- `hrm.auth` / `hrm.sms` / `hrm.geo` 三命名空间与新增键待回填 `server-architecture.md §5.1`（受禁改约束，本轮未改）。
- `ClientLogSanitizer` 凭据正则补充 `smsCode/verifyCode/code/captcha`（M4 落地短信端点时同步）。

---

## 2026-09-24 · 服务端 P10（包裹域 6 接口 · 算法 S7 趋势预测/容量热力/游标分页）— 145 接口收官

### 概述
新增 M10 parcel（6 接口：列表/看板/趋势/排行/详情/取件核销），145 接口**最后一批**。算法 **S7** 三项落地：
**S7-1 趋势预测**（Holt-Winters 加性季节 + 降级链）、**S7-2 容量与热力**（利用率预警 + IQR 离群）、
**S7-3 查询性能**（默认游标分页 + 覆盖索引 + 禁止 `SELECT *`）。
**未执行任何 DB 迁移**（`V13__parcel.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
**契约不可破坏**：6 接口入参/出参字段集与结构默认与 Mock 逐位一致；S7 的预测/容量热力以**配置开关控制的可选附加字段**输出（默认关闭），
游标翻页以**可选 `cursor` 入参 + `nextCursor` 出参** opt-in 提供——三项均登记为契约扩展（见下）。

### 交付
| 域 | 接口（6） | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P10 parcel（6） | `GET /parcels`、`GET /parcels/summary`、`GET /parcels/trend`、`GET /parcels/ranking`、`GET /parcels/{id}`、`PUT /parcels/{id}/pickup` | `controller/parcel/ParcelController`、`service/parcel/{ParcelService}+impl/ParcelServiceImpl`、`mapper/ParcelMapper`、`entity/Parcel`、`dto/parcel/*`、`vo/parcel/*` |

实体/映射：`entity/Parcel` + `mapper/ParcelMapper`（显式列 SQL：分页/计数/看板聚合/趋势分桶/驿站聚合/条件更新）。
纯逻辑支撑（可离线单测，`service/parcel/support/`）：`ParcelConstants`、`ParcelMetrics`、`ParcelForecaster`、`ParcelCapacityAnalyzer`、`ParcelCursorCodec`、`ParcelStatusMachine`、`ParcelSummaryRow`、`ParcelDailyCount`、`ParcelStationStat`。
参数外置：`AlgoProperties.Parcel` 扩展（`page.maxOffsetDepth/exposeCursor`、`forecast.minSamples/maWindow/appendForecast`、`capacity.appendToRanking`、`trend.minDays/maxDays`、`ranking.defaultMetric/sortableMetrics`）默认值与 Mock 等价。

### S7 落地要点
- **S7-1 预测**：Holt-Winters 加性季节公式 1:1 对照离线原型 `docs/algo-scripts/s7-parcel.mjs`（同参数 14 点周期序列未来 3 步 = 10/20/30，与原型一致；原型回测 MAPE **5.34%**）。降级链：历史点 < `minSamples`（默认 14 = 2×季节周期）→ MA(7) → 再不足 → 均值恒定预测；空序列/单点/全零均不抛错。
- **S7-2 容量**：`utilization = pendingPickup / shelfCapacity`；`WARN ≥ utilWarn(0.8)`、`CRITICAL ≥ utilCritical(0.95)`；IQR（Tukey 1.5×）识别异常驿站（全站集合计算，与原型 `iqrOutliers` 同口径）。
- **S7-3 分页**：默认 `page.mode=CURSOR`。首页 keyset（`ORDER BY inbound_time DESC, id ASC LIMIT ? OFFSET 0`）；深页 OFFSET 兜底并以 `page.maxOffsetDepth`（默认 1000）限深；显式 `cursor` 入参启用完整 keyset（扫描行恒定 `O(log N + pageSize)`，算法 S7：20 万级 **38 行** vs 深分页 **20020 行**）。
- **索引预期**（服务器实测收敛，db.md U-3）：按驿站+状态 → `idx_parcel_station_status_inbound`、`type=ref`、**无 `Using filesort`**（InnoDB 二级索引隐式追加主键升序，恰配 `inbound_time DESC, id ASC`）；仅按驿站 → `idx_parcel_station_inbound`；运单号精确查 → `idx_parcel_station_waybill`。无 station 维度的全量/仅状态筛选无适配索引，会有 filesort（大表纪律见架构 §4.3）。
- **容量预期**：20 万行约 **105.83MB**（算法 S7），落 `/data` 不足 0.3%（本批不播种数据，运行时空表返回 0/空数组）。

### 契约扩展登记（默认关闭 / opt-in，不改变既有字段语义）
| 扩展 | 载体 | 开关 | 说明 |
| ---- | ---- | ---- | ---- |
| 趋势预测叠加 | `GET /parcels/trend` 出参每点新增 `forecastInbound` | `hrm.algo.parcel.forecast.appendForecast`（默认 false） | 数组长度与既有字段不变；仅附加字段 |
| 容量/热力叠加 | `GET /parcels/ranking` 出参每行新增 `pendingPickup/utilization/capacityLevel/outlier` | `hrm.algo.parcel.capacity.appendToRanking`（默认 false） | 既有 5 字段语义不变 |
| 游标翻页 | 请求可选 `cursor`；响应可选 `nextCursor` | `hrm.algo.parcel.page.exposeCursor`（默认 false） | 默认不出参 `nextCursor`，结构同 Mock |

### 单测（本机无 JDK/Maven，收敛到服务器阶段）
`service/parcel/support/{ParcelForecasterTest, ParcelCapacityAnalyzerTest, ParcelCursorCodecTest, ParcelMetricsTest, ParcelStatusMachineTest}` + `controller/parcel/ParcelRouteOrderTest`。
覆盖边界：预测空序列/单点/全零/样本不足降级/未知模型回退、IQR 四分位边界/全同值/单站/空集合、游标往返/空白/非法 Base64/结构错误/非法 id、比率空态归零、MAE/MAPE 跳过零值、取件状态迁移全分支、字面量路由（summary/trend/ranking）优先于 `{id}`。

### 未验证项（收敛到服务器阶段）
编译打包；Flyway 迁移与快照比对；Redis 会话下端到端越权冒烟（详情跨站 404、取件跨站 7001、非 ADMIN 列表静默收敛）；`EXPLAIN` 索引命中与 `Using filesort` 复核；游标翻页与深分页上限的真实表现；20 万级列表/看板/趋势/排行耗时与落盘容量（`SHOW TABLE STATUS`）。

### TODO(扩展)
- 深分页超 `maxOffsetDepth` 返回空 list（正确 total）为契约外行为，是否改为显式业务码待主智能体裁定。
- 未来 `horizonDays` 预测点作为独立出参暴露需契约裁定；节假日因子（算法 S7 TODO-7）。
- `AlgoProperties.Parcel` 新增键待回填 `algo-hrm-server.md §11.7` 与 `server-architecture.md §5.1`。

---

## 2026-09-24 · 服务端 P9（同步域 22 接口 · 同步任务 5 + 驿站采集配置 4 + 配置中心 13）

### 概述
新增 M9 sync（22 接口）：**同步任务**（列表/日志/详情/触发/重试，状态机四态）、**驿站采集配置**（总览/列表/单站读/单站写，采集状态四态派生）、**配置中心**（配置项与选项集 CRUD + 影响面 + 全局默认读写 + CSV 导入导出）。
**未执行任何 DB 迁移**（`V11__sync_task.sql` / `V12__sync_config_center.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
配置中心实现 **四层模型**：配置项定义 / 选项集（元数据层）→ 全局默认 → 驿站覆盖；生效值 = `覆盖 ?? 全局默认`，并派生 `OVERRIDE/INHERIT` 来源。
**路由顺序复核结论**：Spring Boot 3 `PathPatternParser` 按模式特异性排序（字面量段 > 变量段），`/sync/configs/global|export|import` 天然优先于 `/sync/configs/{stationId}`，**与控制器注册先后无关、无需 @Order**（不沿用 Mock 的注册顺序依赖）。

### 交付
| 域 | 接口（22） | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| 同步任务（5） | `GET /sync-tasks`、`GET /sync-tasks/{id}/logs`、`GET /sync-tasks/{id}`、`POST /sync-tasks/{id}/trigger`、`POST /sync-tasks/{id}/retry` | `controller/sync/SyncTaskController`、`service/sync/{SyncTaskService}+impl/SyncTaskServiceImpl` |
| 驿站采集配置（4） | `GET /sync/overview`、`GET /sync/configs`、`GET /sync/configs/{stationId}`、`PUT /sync/configs/{stationId}` | `controller/sync/SyncConfigController`、`service/sync/{StationSyncConfigService}+impl/StationSyncConfigServiceImpl` |
| 配置中心（13） | `GET/POST /sync/config-items`、`PUT/DELETE /sync/config-items/{itemKey}`、`GET /sync/config-items/{itemKey}/impact`、`POST /sync/config-items/{itemKey}/options`、`PUT/DELETE /sync/config-items/{itemKey}/options/{optionKey}`、`GET .../impact`、`GET/PUT /sync/configs/global`、`GET /sync/configs/export`、`POST /sync/configs/import` | `controller/sync/SyncConfigCenterController`、`service/sync/{SyncConfigCenterService}+impl/SyncConfigCenterServiceImpl` |

实体/映射（同步 7 表）：`entity/{SyncTask, SyncTaskLog, SyncStationConfig, SyncConfigItem, SyncConfigOption, SyncConfigGlobal, SyncConfigStationOverride}` + `mapper/*` 7 个 + JSON 类型处理器 `handler/SyncConfig{Constraints,ExtraAttrs,LegacyCodes}TypeHandler`（复用/扩展 `util/JsonUtil#mapType`）。
纯逻辑支撑（可离线单测，`service/sync/support/`）：`SyncConstants`、`SyncClock`、`SyncValueCodec`、`SyncConfigValidator`、`SyncConfigMerger`、`SyncConfigImpactCalculator`、`SyncConfigStore`、`SyncOptionSet`、`SyncConfigCatalog(+Snapshot)`、`SyncCsvCodec`、`SyncCsvExporter`、`SyncCsvImporter`。
内建种子（幂等 ApplicationRunner，替代种子迁移）：`service/sync/impl/SyncConfigSeeder`。
参数外置：`AlgoProperties.Sync`（`hrm.algo.sync.importRowLimit=1000`、`csvColumnCount=16`，默认与 Mock 等价）；导出走内存流，落盘目录预留 `hrm.storage.export-dir`（数据盘）。

### 单测（本机无 JDK/Maven，收敛到服务器阶段）
`service/sync/support/{SyncConfigMergerTest, SyncConfigValidatorTest, SyncConfigImpactCalculatorTest, SyncCsvCodecTest, SyncCsvExporterTest, SyncCsvImporterTest, SyncCsvRoundTripTest}` + `controller/sync/SyncRouteOrderTest`（路由顺序回归）。
覆盖边界：空配置项 / 无覆盖 INHERIT / 有覆盖 OVERRIDE / 重置键 / CSV 缺列与多列 / 非法记录类型 / 未知冲突策略 / dryRun 不落库 / 内置项删除被拒（9510）/ NUMBER 与 TIME_RANGE 约束边界 / **导出即导入往返一致（文本层 + 语义层双重断言）**。

### 未验证项（收敛到服务器阶段）
编译打包、Flyway 迁移与快照比对、Redis 会话下的端到端越权口径（详情/logs 404、trigger/retry 403、configs/{stationId} 404）冒烟、CSV 大文件导出内存占用与真实落盘目录可用性、ApplicationRunner 播种幂等性（重启二次启动无重复行）。

---

## 2026-09-24 · 服务端 P8（工单 9 接口 · 状态机 4 态 + 算法 S6 多目标派单）

### 概述
新增 M8 workorder（9 接口：列表/新建/派单规则读改/企微自动派单/详情/指派/流转/转派）；算法 **S6 工单多目标派单**落地为**加权标量化排序内核**（SLA 紧迫度 × 技能匹配 × 负载均衡），用于 `auto-dispatch` 与 `assign` 的默认处理人推导。
**未执行任何 DB 迁移**（`V10__work_order.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
复用 P2 `NotificationService`（指派/转派 type 1、解决 type 2，`biz_type=work_order`）。**未启用 `auto-dispatch` 公网暴露**（仅实现接口逻辑；公网可达须先经网络安全工程师评估，P0.5）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P8 workorder（9） | `GET/POST /work-orders`、`GET /work-orders/dispatch-rules`、`PUT /work-orders/dispatch-rules/{id}`、`POST /work-orders/auto-dispatch`、`GET /work-orders/{id}`、`PUT /work-orders/{id}/assign`、`PUT /work-orders/{id}/status`、`POST /work-orders/{id}/transfer` | `controller/workorder/WorkOrderController`、`service/workorder/{WorkOrderService}+impl/WorkOrderServiceImpl`、`mapper/{WorkOrderMapper,WorkOrderTimelineMapper,WorkOrderTransferMapper,WorkOrderDispatchRuleMapper}`、`entity/{WorkOrder,WorkOrderTimeline,WorkOrderTransfer,WorkOrderDispatchRule}`、`dto/workorder/*`、`vo/workorder/*` |

纯逻辑支撑（可离线单测）：`service/workorder/support/{WorkOrderConstants, WorkOrderStateMachine, WorkOrderSlaPolicy, WorkOrderAccessPolicy, WorkOrderKeywordClassifier, WorkOrderDispatchScorer}`。

### 关键实现口径（对齐 Mock `routes/workOrder.js` / `db.js` / `dict.js`）
- **状态机 4 态**：`TRANSITIONS={0:[1,3],1:[2],2:[1,3],3:[]}` 集中于 `WorkOrderStateMachine`（表驱动）；非法流转 8001；动作名 `3→close`/`2→resolve`/`1→accept`（严格对齐 Mock 三元表达式）。
- **SLA（Q4 未裁定）**：阈值外置 `hrm.algo.dispatch.slaHours`（默认 `{0:48,1:24,2:8}` 与现状逐位等价）+ 键缺失兜底 `defaultSlaHours=24`；「超时未处理」= **仅未处理完（0/1）且 now 严格晚于 deadline**（终态不计，恰好到点不算）；出参同给 `overdueUnhandled` 与旧别名 `overSla`。
- **S6 多目标派单**：`score(e)=wU·urgency·readiness + wS·skill + wL·load + wSp·readiness`（与原型逐式一致）；权重外置 `hrm.algo.dispatch.weights.*`（默认 4.0/2.5/1.5/2.0）；信号来源：技能画像（`skillWindowDays` 内已了结工单类型占比，样本 < `minSkillSamples` 按 0）、负载（`loadWindowDays` 内接单量归一）、就绪度（未完成量 × `estimatedServiceHours` 相对 SLA）。**并列保序取先出现者**；**权重为负拒绝**；**无候选 → 不指派（转人工），不阻塞建单**。
- **关键词规则与多目标排序的关系**：`dispatchRules` 仍负责「关键词 → 类型/优先级」判定（默认**顺序命中**，与 Mock 逐位等价；特异度加权（12 条歧义样本 0.8333→1.0000）由 `hrm.algo.dispatch.keywordWeighted` 开关控制，默认关）；S6 多目标排序只作**处理人选择**内核，规则未命中仍走兜底 `{type:4, priority:1}`。
- **越权口径逐端点**（架构 §6.2 P8，不得统一）：`GET /{id}` 跨站 → **404**；`assign`/`status` 无权 → **8002**；`transfer` 无权 → **8003**；`transfer` 对象非法（不存在/停用/转自己/**非 ADMIN 跨站**）→ **8004**；规则不存在 → **8005**；群消息空 → **8006**。
- **时间线**：`work_order_timeline` 记录 `create/assign/accept/resolve/close/transfer/auto_dispatch`，出参按 `time ASC, id ASC` 组装回 `handleLog[]`（替代 Mock 的 `handle_log` 内嵌 JSON，顺序与 Mock 一致）；转单另落 `work_order_transfer`（详情 `transfers[]` 按转单时间倒序）。
- **单号**：`WO-yyyyMMdd-####`；服务端以「插入取得自增 id 后回填单号」等价 Mock 的内存序号拼号（order_no 无唯一索引）。
- **通知联动**：建单指派/指派/转单 → type 1「工单指派 / 工单转单」；解决 → type 2「工单流转」通知上报人（无上报人则跳过）。
- **路由字面量优先**：`/work-orders/dispatch-rules`、`/work-orders/auto-dispatch` 优先于 `/work-orders/{id}`（Spring `PathPatternParser` 天然满足，回归断言见 `WorkOrderRouteOrderTest`）。

### 与 Mock 的差异（契约出参/错误码不变，均为服务端必要修正）
1. **`hrm.algo.dispatch.*` 新增 6 键**：`defaultSlaHours` / `loadWindowDays` / `skillWindowDays` / `minSkillSamples` / `estimatedServiceHours` / `keywordWeighted`（默认值取算法方案默认组，与 Mock 状态等价）；`algo-hrm-server.md §11.6` 与 `server-architecture.md §5.1` 的 dispatch 段尚未登记，待回填。
2. **`auto-dispatch` 处理人**：Mock 规则 `default_assignee_id` 全空 → 现状「只定类型/优先级、不指派」；本批规则为空时改由 S6 多目标排序推导（无候选仍为空）。
3. **`assign` 缺省处理人**：Mock 在 `assigneeId` 缺失时回 400；本批非空入参行为不变，缺失时先按 S6 推导，推导无果仍回 400（新增能力，不影响既有路径）。
4. **`auto-dispatch` 时间字段**：`create_time` 以消息发送时间为准（SLA 起算口径一致）；`update_time` 由框架自动填充刷新为入库时间（与 Mock 的 sendTime 有毫秒级差异，不影响任何判定）。
5. **`PUT dispatch-rules/{id}` 清空 `defaultAssigneeId`**：受 JSON「字段缺失 vs 显式 null」无法区分限制，本端点 null 视为「不改」（种子规则本为 null，实际影响可忽略）——`TODO(扩展)` 待前端约定清空哨兵值。

### 边界声明
- 本轮仅改 `hrm-dev/hrm-server/**`（新增 workorder 域 30 个主源码 + 6 个单测，改 `config/AlgoProperties`、`resources/application.yml` 的 `hrm.algo.dispatch` 段）。
- `hrm-demo/**`、`hrm-admin/**`、`hrm-android-shell/**` 零改动；`api.md`/`db.md`/`plan.md`/`server-architecture.md`/`algo-hrm-server.md`/迁移脚本/`.trae/rules/` 零改动。
- 未运行项（须服务器复核）：编译、Flyway V10 执行、9 接口端到端冒烟、S6 基准复现（原型 78.3%→100% SLA / 9.447h→1.504h）、6 个单测执行。
- 待裁决：`algo-hrm-server.md §2` 的 **Q4（SLA 阈值 48/24/8）与 Q5（三目标权重）**；裁定前默认值与现状等价。


## 2026-09-24 · 服务端 P7（请假 13 接口 · 两级审批 6 态 + 算法 S5 半天单元区间）

### 概述
新增 M7 leave（13 接口：申请/试算/我的/列表/设置/详情/编辑/撤销/重提/初审/终审/撤回）；算法 **S5 请假计薪天数与重叠判定**落地为**半天单元整数区间 + 二分**（逐日扫描 → 区间查询）。
**未执行任何 DB 迁移**（`V9__leave.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
接入 P6 账期锁只读端口 `PayrollLockQueryService`（撤回校验 9606）；向 P6 财务域暴露只读端口 `ApprovedLeaveDaysPort`（已批请假天数 + 扣款开关），替换 `PayrollContextProvider` 预留的 `TODO(扩展)`。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P7 leave（13） | `POST /leave`、`POST /leave/preview`、`GET /leave/my`、`GET /leave/list`、`GET/PUT /leave/settings`、`GET /leave/{id}`、`PUT /leave/{id}`、`POST /leave/{id}/cancel`、`POST /leave/{id}/resubmit`、`POST /leave/{id}/station-approve`、`POST /leave/{id}/final-approve`、`POST /leave/{id}/revoke` | `controller/leave/LeaveController`、`service/leave/{LeaveService}+impl/LeaveServiceImpl`、`service/leave/port/{ScheduledDatesQuery}+impl/{LeaveScheduledDatesQueryImpl,ApprovedLeaveDaysPortImpl}`、`mapper/{LeaveRequestMapper,LeaveLogMapper,LeaveSettingMapper}`、`entity/{LeaveRequest,LeaveLog,LeaveSetting}`、`dto/leave/*`、`vo/leave/*` |

纯逻辑支撑（可离线单测）：`service/leave/support/{LeaveConstants, LeaveIntervalPolicy（S5 算法）, LeaveUnitRange, OccupiedLeaveInterval, LeaveStateMachine, LeaveAccessPolicy}`。

### 关键实现口径（对齐 Mock `leaveStore.js` / `routes/leave.js`）
- **6 态状态机**：`SUBMIT/UPDATE/RESUBMIT/CANCEL/STATION_APPROVE|REJECT/FINAL_APPROVE|REJECT/REVOKE` 迁移矩阵集中 `LeaveStateMachine`；初始态判定 = STAFF 有站长 → 待初审、站长本人或无站长 → 待终审（T1 + Q4 降级共用一条判定）。
- **S5 区间算法**：`unitIndex(date, period)=epochDay×2+(AM?0:1)`；自然天数 O(1)；计薪天数在「排班单元升序数组」上两次二分 O(log m)；重叠用区间相交（有序集合上 O(log k)）；账期跨度 O(1)。**与逐日法 0 结果差异**（单测等价性断言 + S5 原型对照）。
- **计薪天数**：`SCHEDULED` 逐日查排班（复用 `attendance_schedule`），`NATURAL` 与排班无关；假别 → countMode 唯一真源 `hrm.algo.leave.countModeMap`（默认逐位等于 Mock `dict.LEAVE_TYPE`）。
- **重叠校验**：日级粗筛（走 `idx_leave_request_date`，同员工 + 占用态 + 区间相交）→ 半天单元精确判定；占用态集 `hrm.algo.leave.occupiedStatus`。
- **越权口径**：跨站/审自己/ADMIN 提交 → 9605；仅状态不匹配 → 9602；编辑非待初审 → 9607；列表/我的/详情按角色收敛（ADMIN 全量 / 站长本站 / 本人）。
- **账期锁（9606）**：撤回前逐月问 `PayrollLockQueryService`（口径 `NON_DRAFT_REJECTED`），命中即以「{月} 工资单已生成」回 9606。
- **留痕**：`leave_log` 记录 action/operator/fromStatus/toStatus/before/after/remark，出参 `handleLog[]` 与 Mock 同序；通知目标缺失留 `NOTIFY_SKIP`。
- **通知**：申请/结果走 P2 `NotificationService.sendSystem`（type 5/6、`biz_type=leave`），开关 `hrm.algo.leave.notifyEnabled`（默认 true）。
- **路由字面量优先**：`/leave/preview|my|list|settings` 优先于 `/leave/{id}`（Spring `PathPatternParser` 天然满足，回归断言见 `LeaveRouteOrderTest`）。

### 跨域端口（入 + 出）
- **入（P6 → P7）**：复用 P6 的 `PayrollLockQueryService`（只读），用于撤回账期锁判定。
- **出（P7 → P6）**：`service/finance/port/ApprovedLeaveDaysPort`（财务域声明接口、请假域实现，依赖倒置断环）：
  `approvedLeaveDays(employeeId, startDate, endDate)`（已批请假计薪天数，半天粒度，跨月由调用方按账期切分）与 `leaveDeductEnabled()`。
  请假域实现 `service/leave/port/impl/ApprovedLeaveDaysPortImpl`；财务域降级兜底 `service/finance/port/impl/{UnavailableApprovedLeaveDaysPort,FinanceLeavePortConfig}`（`@ConditionalOnMissingBean`，同 P5 手法）。
- **财务域最小接线（改动点）**：`service/finance/impl/PayrollContextProvider` 接入端口，`absentCount = max(0, 应到 − 实到 − excludeLeaveDays)`、`excludeLeaveDays = leaveDeductEnabled ? 0 : leaveDays`；`service/finance/support/AttendanceStat` 的 `absentCount`/`leaveCount` 由 `int` 扩为 `BigDecimal`（半天粒度无法用整数无损承载），`AttendanceItemResolver` 随之改用 `BigDecimal` + `PayrollNumberFormat.plain`（文案不变）。**财务域其余逻辑零改动。**

### 与 Mock 的差异（契约出参/错误码不变，均为服务端必要修正）
1. **`GET /leave/settings` 角色口径**：Mock 限 ADMIN；`api.md §7.1` 记「不限角色」。按主智能体裁决**以 Mock（ADMIN）为准**，待 api.md 收口（已登记 §待收口）。
2. **`hrm.algo.leave.*` 新增 3 键**：`countModeMap` / `deductEnabledDefault` / `notifyEnabled`（默认与 Mock 逐位等价）；`algo-hrm-server.md §11.5` 与 `server-architecture.md §5.1` 的 leave 段尚未登记，待回填。
3. **扣款开关落库**：`leave_setting` 单行表首次写入才插入；无行时按 `deductEnabledDefault`（=false，Q6 未裁定）。V9 未种入初始行，行为与 Mock 默认一致。

### 边界声明
- 本轮仅改 `hrm-dev/hrm-server/**`：新增 leave 域（controller/service/mapper/entity/dto/vo/support + 2 个端口实现 + 4 个单测），改 `config/AlgoProperties`、`resources/application.yml`、`service/finance/**` 3 文件 + `PayrollItemResolverTest` 夹具 1 处。
- `hrm-demo/**`、`hrm-admin/**`、`hrm-android-shell/**` 零改动；`api.md`/`db.md`/`plan.md`/`server-architecture.md`/`algo-hrm-server.md`/迁移脚本/`.trae/rules/` 零改动。
- 未运行项（须服务器复核）：编译、Flyway V9 执行、13 接口端到端冒烟、S5 基准复现。
- 待裁决：`algo-hrm-server.md §2` 的 Q6（请假扣款开关 + 连续缺卡阈值）。

## 2026-09-24 · 服务端 P6（财务 15 接口 · 工资单六态 + 算法 S2 注册表 + P5 端口接入）

### 概述
新增 M6 finance（15 接口：计薪规则 5 / 工资单 10）；算法 **S2 工资试算口径引擎**落地为**来源解析器注册表**（FIXED/ATTENDANCE/KPI/MANUAL，表驱动，新增来源不改核心代码）。
**未执行任何 DB 迁移**（`V8__payroll.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
接入 P5 人事域跨域端口 `PayrollSettlementPort`（提供真实 Bean，使降级实现自动失效）；向 P7 暴露账期锁只读端口 `PayrollLockQueryService`。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P6 finance（15） | 规则 `GET/POST /finance/payroll-rules`、`GET/PUT/DELETE /finance/payroll-rules/{id}`；工资单 `GET /finance/payrolls/my`、`POST .../generate|submit|publish`、`GET /finance/payrolls`、`GET /finance/payrolls/{id}`、`PUT .../{id}/items`、`POST .../{id}/approve|confirm|objection` | `controller/finance/{PayrollRuleController,PayrollController}`、`service/finance/{PayrollRuleService,PayrollService,PayrollLockQueryService}+impl/{PayrollRuleServiceImpl,PayrollServiceImpl,PayrollLockQueryServiceImpl,PayrollContextProvider}`、`service/finance/port/PayrollSettlementAdapter`、`mapper/{PayrollRuleMapper,PayrollRuleItemMapper,PayrollMapper,PayrollItemMapper}`、`entity/{PayrollRule,PayrollRuleItem,Payroll,PayrollItem}`、`handler/PayrollItemParamsTypeHandler`、`dto/finance/*`、`vo/finance/*` |

纯逻辑支撑（可离线单测）：`service/finance/support/{PayrollItemResolver + FixedItemResolver/AttendanceItemResolver/KpiItemResolver/ManualItemResolver/PayrollResolverRegistry, PayrollItemParamAccessor, PayrollCalcContext, AttendanceStat, PayrollSalaryField, PayrollTotals(+Policy), PayrollItemDraft, PayrollStateMachine, PayrollGenerateGuard, PayrollLockPolicy, PayrollRuleValidator, PayrollNoGenerator, PayrollSnapshotBuilder, PayrollNumberFormat, PayrollStatus/PayrollSource/PayrollItemType/PayrollBillType}`。

### 关键实现口径（对齐 Mock `financeStore.js` / `routes/finance.js`）
- **S2 注册表**：构造时收集容器内全部 `PayrollItemResolver` 注册为 `Map<source, resolver>`；未知来源按 `hrm.algo.payroll.unknownSourcePolicy`（默认 ZERO → 按 0 计并 warn，不中断整批）。
- **四类来源公式**与 Mock 逐式一致：FIXED（定薪字段/津贴合计/指定津贴项）、ATTENDANCE（PER_COUNT×单价+`cap>0` 封顶；BONUS_IF_ZERO 次数为 0 才发）、KPI（`round(绩效基数 × clamp(KPI/100, 0, capRatio))`）、MANUAL（`defaultValue`）；解释文案逐字对齐 Mock。
- **合计**：`grossAmount = 增项合计`；`netAmount = 增项 − 扣项`。**Q3 未裁定** → 默认与 Mock 一致（**允许负净额、不钳制**）。
- **参数外置**：`hrm.algo.payroll.*`（`defaultCapRatio=1.0`、`unknownSourcePolicy=ZERO`、`allowNegativeNet`、`itemCapSemantics=ZERO_MEANS_NO_CAP`、`attendanceFieldMap`）。**种子 `capRatio=1.2` 来自规则数据，代码不写死**。
- **幂等（9405）**：同月任一月度单为非 DRAFT/REJECTED → **整批拒绝**（回填阻断单据号）；草稿/驳回 → 覆盖重建（删旧单+明细再生成）。
- **六态状态机**：动作矩阵与 Mock `PAYROLL_ACTIONS` 逐条一致；`submit/approve/reject/publish/confirm/objection` 非法流转 → 9403（文案含状态中文）。
- **数据隔离**：`payrolls/my` 只返回本人且仅 PUBLISHED/CONFIRMED；`payrolls/{id}` 非 ADMIN 仅本人（他人 9404、未发布 9403）。
- **规则快照**：`payroll.rule_snapshot` 存算薪时的规则与规则项（含停用项），保证历史可解释；**不出现在 API 出参**（与 Mock 一致）。
- **路由字面量优先**：`payrolls/my|generate|submit|publish` 优先于 `payrolls/{id}`（Spring `PathPatternParser` 天然满足，回归断言见 `PayrollRouteOrderTest`）。

### 跨域端口（入 + 出）
- **入（P5 → finance）**：`service/finance/port/PayrollSettlementAdapter` 实现人事域 `PayrollSettlementPort`，`createSettlement` 委托财务服务创建 `billType=SETTLEMENT` 草稿单（同员工同月幂等），返回 `{payrollId, payrollNo, amount=netAmount}`；注册后 `HrPortConfig` 的降级 Bean 经 `@ConditionalOnMissingBean` 自动失效，**人事域代码零改动**。
- **出（finance → P7）**：`PayrollLockQueryService`（`isMonthLocked(employeeId, month)` / `findLockingPayroll(...)`），口径 `NON_DRAFT_REJECTED`（走 `hrm.algo.leave.lockStatusPolicy`），与 Mock `findLockingPayroll` 一致（不限单据类型）。
- **上游只读**：`PayrollContextProvider` 经 P5 服务层 `HrSalaryWriter.selectByEmployeeId` 读定薪；读 P3 `attendance_record/schedule`（复用 `AttendanceConstants.isValidCard`）与 P4 `kpi_score.total_score`。依赖方向恒为 `finance → hr/attendance/kpi`，无环。

### 与 Mock 的差异（契约出参/错误码不变，均为服务端必要修正）
1. **列表计数的员工姓名字段**：Mock 单表内嵌 `employeeName`；服务端 JOIN 员工表组装，`keyword` 命中姓名时先查员工 id 集再过滤（出参同形）。
2. **`amount` 宽松强转**：Mock `Number(item.amount)` 对 `undefined`/非数字给业务文案；服务端 `PayrollItemAdjustRequest.amount` 声明为 `Object` 并按 JS 语义强转（`null`/空串→0，非数字→「金额须为数字」）。缺失字段（Java 无法区分「未传」与「显式 null」）按 0 处理，属低优先差异。
3. **`rule_snapshot`**：Mock 无该字段，为 db.md §8.6.3 新增（历史可解释），仅内部留存、不出参。
4. **规则项 `id`**：Mock 为「每规则内 index+1」，服务端为全局自增；前端编辑整体覆盖、不依赖其稳定。

### 配置修正（1 处，见返回摘要「事实性纠正」）
`hrm.algo.payroll.allowNegativeNet` 默认由 `false` 改为 **`true`**（`AlgoProperties` + `application.yml`）：`algo-hrm-server.md §11.2` 记 false（标注待 Q3 确认）与 P6 验收「默认与 Mock 一致（允许）」冲突；按后者落实为 Mock 等价行为，Q3 裁定后改回 false 即切换、无需改算力代码。

### 验证状态
本机无 JDK/Maven：**未编译、未跑测**。纯逻辑单测已就位（解析器/合计/状态机/幂等与账期锁/校验器/路由顺序），**收敛到服务器阶段执行**，并与 `docs/algo-scripts/s2-payroll.mjs` 逐例对照（公式与边界一致；原型与 Mock 的说明文案本就不同，本批以 **Mock 契约**为准）。

## 2026-09-24 · 服务端 P5（人事 16 接口 · 入离职流程 + 跨域断环端口）

### 概述
新增 M5 hr（16 接口：档案 3 / 定薪 3 / 入职 5 / 离职 5）；复用 P0 地基（`@RequireRoles` / `PageQuery` / `ErrorCode` 93xx / `FieldValidator` / `PasswordUtil` / `DesensitizeUtil`）。
**未执行任何 DB 迁移**（`V7__hr.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。
离职 `SETTLEMENT` 步骤经**跨域端口**（依赖倒置断环）创建财务结算单，P6 未就绪时降级为业务异常（9306）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P5 hr（16） | 档案 `GET/PUT /hr/profiles[/{employeeId}]`、定薪 `GET/PUT /hr/salary-structures[/{employeeId}]`、入职 `GET/POST /hr/onboarding`、`GET /hr/onboarding/{id}`、`POST .../{id}/steps/{key}/complete`、`POST .../{id}/reject`、离职同构 5 个 | `controller/hr/{HrProfileController,HrFlowController}`、`service/hr/{HrProfileService,HrFlowService}+impl/{HrProfileServiceImpl,HrFlowServiceImpl,HrSalaryWriter}`、`mapper/{HrProfileMapper,HrSalaryMapper,HrSalaryLogMapper,HrFlowMapper,HrFlowStepMapper}`、`entity/{HrProfile,HrSalary,HrSalaryLog,HrFlow,HrFlowStep,HrAllowance}`、`handler/HrAllowanceListTypeHandler`、`config/HrProperties`、`dto/hr/*`、`vo/hr/*` |

纯逻辑支撑（可离线单测）：`service/hr/support/{HrConstants,HrFlowStepGuard,HrProfileValidator,HrSalaryValidator,HrSalaryCalculator,HrValidateSupport}`。

### 关键实现口径（对齐 Mock）
- **步骤序真源**：`HrConstants.ONBOARDING_STEPS` = SUBMIT_MATERIALS→HR_REVIEW→CREATE_ACCOUNT→ASSIGN_STATION→SET_SALARY→DONE；`OFFBOARDING_STEPS` = MANAGER_APPROVE→HR_APPROVE→HANDOVER→ASSET_RETURN→SETTLEMENT→LEAVE，与 Mock 常量逐条一致。
- **按序守卫**：`HrFlowStepGuard.checkOrder` 逐条复刻 Mock `completeStep` 判序与文案（非进行中 / 步骤不存在 / 重复办理 / 跳步），**跳步一律拒绝**，错误码 9303/9304。
- **建档**：账号正则 + 强密码（`FieldValidator`）；部门不存在 3001、驿站不存在 4001、停用 4004；员工先落 `status=0`、`pwd_changed=0`、`entry_date=预计入职日期`；同步落 0 值定薪行（否则卡在定薪步骤）；**仅 DONE 步骤置 `status=1`**；驳回时已建档员工一并禁用。
- **离职判定真源**：`hr_profile.leave_date`（非 `employee.status`）；`HrSalaryWriter.isResigned` 与建档/定薪复用同一判据。
- **离职流程**：发起须在职且无进行中流程（`status!=1` → 9302；重复 → 9304）；`SETTLEMENT` 经端口创建结算单并回填引用；**无结算单不得离岗**（9306）；离岗后员工 `status=0` + 写 `profile.leave_date`。
- **定薪**：保存 = 覆盖当前档案 + 追加调薪留痕（只增不改），`total = basic + post + performanceBase + allowancesTotal`；已离职拒 9302、无档案 9305。**调薪属计费口径**，本批仅沿用 Mock 口径，未引入新规则。
- **越权**：档案详情/定薪详情非本人非 ADMIN → 403（对齐 Mock `canAccessEmployee`）。
- **参数外置**：试用期默认月数、默认合同期限走 `hrm.hr.*`（`HrProperties`，默认 3/3，与 Mock 一致），未硬编码。

### 跨域端口设计（ADR-03 断环，P6 接入点）
- `service/hr/port/PayrollSettlementPort`（`createSettlement(PayrollSettlementCommand) → PayrollSettlementRef`），人事域只依赖自身 port 包，财务域（P6）注册实现即完成接入，依赖方向恒为 `finance → hr`，无环。
- P6 未就绪时 `HrPortConfig` 以 `@ConditionalOnMissingBean` 装配降级实现 `UnavailablePayrollSettlementPort`（抛 9306「财务域未就绪」）；P6 提供真实 Bean 后降级自动失效，**无需改人事域代码**。
- `TODO(扩展)` 标注：P6 落地后可另加 `OffboardingSettlementRequired` 领域事件（AFTER_COMMIT）做补偿/重试。

### 与 Mock 的差异（契约出参/错误码不变，均为服务端必要修正）
1. **密码存储**：Mock 存明文，服务端一律 BCrypt（`PasswordUtil.hash`）。
2. **定薪操作人**：Mock `salaryForFlow` 误用「流程创建人」为定薪留痕操作人；服务端改为**当前办理人**（谁办理谁留痕）。
3. **档案编辑 null 语义**：Mock 可用显式 `null` 清空字段；Java 无法区分「未传」与「显式 null」，改为**仅写非 null 字段**（需清空传空串），空请求体不会误清空档案（与 P4 同类处理一致）。
4. **列表不加 status 过滤**：对齐 Mock `activeEmployees`（仅排除逻辑删除），禁用/在册员工档案仍可管理；列表返回不含 `salary` 键（详情才带）。

### 跨域：离职 SETTLEMENT 策略选择（待主智能体裁定，见返回摘要「事实性纠正」）
架构 §2.3 该行记「领域事件」，但 Mock 契约要求 SETTLEMENT 步骤**同步**回填 `settlementPayrollId/No/Amount`；`@TransactionalEventListener(AFTER_COMMIT)` 为提交后异步，无法满足。本批以「跨域端口 + 服务层直调」实现同等断环且保持同步语义。

### 测试（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`HrFlowStepGuardTest`（空流程/跳步/重复办理/步骤不存在/非进行中/离职离岗首步）、`HrProfileValidatorTest`（字典/日期先后/试用期边界/金额与敏感字段）、`HrSalarySupportTest`（津贴合计与定薪合计口径、保存 vs 步骤两态校验）、`HrSalaryWriterTest`（无档案 9305 / 已离职 9302 / 覆盖+留痕合计）、`HrProfileServiceAccessTest`（员工查他人档案/定薪 403、本人与 ADMIN 放行）、`HrFlowServiceStepTest`（入职跳步 9303 / 离职跳步 9304 / 无结算离岗 9306 / 结算端口未就绪 9306 / 流程不存在 404 文案）、`HrRouteOrderTest`（静态段与变量段互不遮蔽）。

### 事实性纠正 / 遗留
- `api.md` 无人事章（P5 契约真源为 Mock + 架构 §6.2 P5）；本批按指令**未改 `api.md`**，契约更新待主智能体授权。
- `V7__hr.sql` **无人事种子数据**（Mock 有档案/定薪/流程种子），服务端上线后列表为空，需数据库工程师补数据迁移或经流程/建档产生数据。
- 通知联动：Mock `hrStore/routes` **未接入通知域**，故本批不调用 `NotificationService`（避免无依据的过度实现）。

## 2026-09-24 · 服务端 P4（KPI 考核 9 接口 · 算法 S1 落地）

### 概述
新增 M4 kpi（9 接口：指标配置 5 / 算分与查询 4）；落地算法 S1（达成率 + 三模式单项得分 + 加权归一总分 + 竞赛排名 + 等级映射 + 权重守卫 + 可选分位映射）；
复用 P0 地基（`@RequireRoles` / `StationScopedQuery` / `PageQuery` / `ErrorCode` 92xx / `AlgoProperties`）与 P3 考勤能力，**未新增依赖**。
**未执行任何 DB 迁移**（`V6__kpi.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P4 kpi（9） | `GET /kpi/metrics`（ADMIN）/ `POST /kpi/metrics`（ADMIN）/ `PUT /kpi/metrics/batch`（ADMIN）/ `PUT /kpi/metrics/{id}`（ADMIN）/ `DELETE /kpi/metrics/{id}`（ADMIN）/ `POST /kpi/scores/calculate`（ADMIN）/ `GET /kpi/scores/ranking`（ADMIN,STATION_ADMIN）/ `GET /kpi/scores`（同）/ `GET /kpi/scores/{employeeId}`（ALL，越权 403） | `controller/kpi/{KpiMetricController,KpiScoreController}`、`service/kpi/{KpiMetricService,KpiScoreService,KpiActualValueSource}+impl`、`mapper/{KpiMetricMapper,KpiScoreMapper}`、`entity/{KpiMetric,KpiScore}`、`handler/KpiMetricDetailListTypeHandler`、`dto/kpi/*`、`vo/kpi/*` |

纯逻辑支撑（可离线单测）：`service/kpi/support/{KpiConstants,KpiAchievementPolicy,KpiScorePolicy,KpiRankingPolicy,KpiWeightGuard,KpiQuantileMapper,KpiSimulatedData}`。

### S1 算法落地（参数全外置，禁硬编码）
- 参数键全部取 `hrm.algo.kpi.*`（`linearCapRatio` / `tieredTiers` / `levels` / `quantile.{enabled,tiePolicy,minSamples}` / `weightSumTarget` / `weightSumTolerance`），与 `algo-hrm-server.md` §11.1 **逐键一致，未新增键**。
- **默认值逐位等价现状**（因 Q1/Q2 未裁定）：`tieredTiers`={1,0.9,0.8,0.6,0}、`levels`=90/80/70/0、`linearCapRatio`=1.0、`quantile.enabled`=**false**、`weightSumTarget`=100、`weightSumTolerance`=0。故「上线即无行为变化」。
- **阶梯参数化 + 分位开关**：TIERED 改为查 `tieredTiers`（降序，首个 `达成率 ≥ minAchievement`），分位 QUANTILE 为**全局开关**（非单指标模式，与 V6 列枚举 `LINEAR/TIERED/BINARY` 一致），默认关闭；开启且样本量 ≥ `minSamples` 时按同 scope 员工分布取百分位，否则回落绝对评分。
- **Node 原型对照（本机实跑 `node s1-kpi.mjs`）**：等价性回归 **1200 项 0 不一致**；边界实测 LINEAR(cap=1) 500%→100、LINEAR(cap=1.2)→120、TIERED→100；`target=0` 四例（DOWN 0/0→1、DOWN 0/2→0、UP 0/0→0、UP 0/5→1）；全同值分位 MID_RANK=0.5 / MIN=0 / MAX=1；权重守卫 100 通过、105 拒绝、空集不校验。Java 侧 `KpiScorePolicyTest`/`KpiAchievementPolicyTest`/`KpiQuantileMapperTest`/`KpiWeightGuardTest` 以此为期望值断言。
- **模拟数据源逐位等价**：`KpiSimulatedData` 复刻 Mock `kpiStore` 的 FNV-1a + mulberry32 + `actualValueOf`；本机实跑 Node 参考脚本得 `PARCEL#1#2026-09=919`、`PICKUP_TIMELY#1#2026-09=89.1`、`COMPLAINT#1#2026-09=0`、`ATTENDANCE#1#2026-09=92.4`、`SERVICE#1#2026-09=5`、`WORK_ORDER#2#2026-09=19`，Java 单测逐一断言。

### 与 Mock 的有意差异（契约出参/错误码不变）
1. **考勤类指标取数改走 P3 真实明细**（主智能体 P4 指令第 8 条）：`metricType=ATTENDANCE` 的 `actualValue` 由 `KpiAttendanceMetricSource` 以「实到天数/应到天数×100」统计（复用 P3 的 `AttendanceConstants.isValidCard` 有效卡判据，不重写考勤统计），**不再走哈希模拟**；故 ATTENDANCE 指标得分与 Mock 演示值不逐位相等。其余类型（PARCEL/PICKUP/…）业务表未接，仍走确定性模拟（标 `TODO(扩展)`，入口收敛在 `KpiActualValueResolver`）。
2. **算分幂等=覆盖重建**：`kpi_score` 一员工一账期一行（架构 §4.2），重算整行替换 `metric_detail` 快照，等价 Mock 的「更新适用指标行 + 删除已停用指标旧行」，重复算分结果恒定。
3. **批量保存原子化**：`PUT /kpi/metrics/batch` 一次校验、全部成功或全部失败（错误码与文案逐条对齐 Mock）。
4. **`month` 收紧为非空**：`calculate` 要求 `month` 非空（Mock `isMonth` 对空值放行会落 `month=null` 脏数据，属潜在缺陷）；查询类 `month` 空值回落当月（对齐 Mock `monthOf`）。
5. **JSON `null` 语义差异**：Mock 可用显式 `null` 清空 `roleScope`/`remark`；Java POJO 无法区分「未传」与「显式 null」，故编辑时二者均按「不改」处理（低频边角，见返回摘要遗留项）。

### 测试（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`KpiAchievementPolicyTest`（UP/DOWN、target=0、target 缺失、>1 与 =1）、`KpiScorePolicyTest`（三模式、线性封顶、单指标、加权归一、等级阈值）、`KpiWeightGuardTest`（空集/99/100/101/容差）、`KpiRankingPolicyTest`（竞赛排名 1-1-3 / 1-2-2、排序键）、`KpiQuantileMapperTest`（全同值三策略 0.5/0/1、n=1、混合并列平均秩）、`KpiSimulatedDataTest`（六项 Node 参考值逐位等价、确定性、值域）、`KpiRouteOrderTest`（`metrics/batch` 与 `scores/ranking` 字面量优先）。

### 事实性纠正 / 遗留
- `api.md` 无 KPI 章（P4 契约真源为 Mock + 架构 §6.2 P4）；本批按指令**未改 `api.md`**，契约更新待主智能体授权。
- `V6__kpi.sql` **无 `kpi_metric` 种子数据**（Mock 有 7 项），服务端上线后指标列表为空、算分回 9203，需数据库工程师补数据迁移或 ADMIN 手工新增。

## 2026-09-24 · 服务端 P3（考勤与排班 22 接口 · 算法 S3/S4 落地）

### 概述
新增 M3 attendance（22 接口：规则 3 / 状态+打卡 2 / 记录 4 / 我的 1 / 补卡 4 / 排班 4 / 班次 4）；
落地算法 S3（排班生成：贪心构造 + 模拟退火）与 S4（异常检测：稳健 z + 连缺游程）；
复用 P0 地基（`@RequireRoles` / `StationScopeQuery(StationScopedQuery)` / `PageQuery` / `ErrorCode` 91xx / `AlgoProperties`），**未新增依赖**。
**未执行任何 DB 迁移**（`V5__attendance.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P3 attendance（10） | `GET /attendance/rule`（ALL）/ `GET /rule/list`（ADMIN）/ `PUT /rule`（ADMIN）/ `GET /status`（ALL）/ `POST /check-in`（ALL）/ `GET /records`（ADMIN,STATION_ADMIN）/ `GET /export`（同）/ `GET /summary`（同）/ `GET /detail`（同）/ `GET /my`（ALL） | `controller/attendance/AttendanceController`、`service/attendance/{AttendanceRuleService,AttendanceRecordService}+impl`、`mapper/Attendance{Rule,Shift,Schedule,Record,Makeup}Mapper`、`entity/{AttendanceRule,AttendanceShift,AttendanceSchedule,AttendanceRecord,AttendanceMakeup,CheckPeriod,WifiEntry}`、`handler/{CheckPeriodListTypeHandler,WifiEntryListTypeHandler}`、`util/JsonUtil`、`dto/attendance/*`、`vo/attendance/*` |
| P3 makeup（4） | `GET /attendance/makeup/my`（ALL）/ `GET /makeup/list`（ADMIN）/ `POST /attendance/makeup`（ALL）/ `POST /makeup/{id}/approve`（ADMIN） | `controller/attendance/AttendanceMakeupController`、`service/attendance/AttendanceMakeupService+impl` |
| P3 schedule（4） | `GET /schedules`（ADMIN,STATION_ADMIN）/ `GET /schedules/my`（ALL）/ `POST /schedules/batch`（ADMIN）/ `POST /schedules/batch-by-station`（ADMIN） | `controller/attendance/ScheduleController`、`service/attendance/AttendanceScheduleService+impl` |
| P3 shift（4） | `GET /shifts`（ALL）/ `POST /shifts`（ADMIN）/ `PUT /shifts/{id}`（ADMIN）/ `DELETE /shifts/{id}`（ADMIN） | `controller/attendance/ShiftController`、`service/attendance/AttendanceShiftService+impl` |

纯逻辑支撑（可离线单测）：`service/attendance/support/{HaversineCalculator,AttendancePeriodResolver,AttendanceCheckPolicy,AttendanceSummaryPolicy,AttendanceDetailPolicy,SchedulePlanner,AttendanceAnomalyDetector,CsvSupport,AttendanceSupport,AttendanceConstants,AttendanceCard}`。

### S3 算法落地（参数全外置，禁硬编码）
- 选型：**贪心构造（轮休错峰 + 最短缺优先） + 模拟退火（同日班次互换 / 在岗↔轮休对调）**；固定迭代数与固定种子（可复现，禁用「时间到即停」）。
- 参数全部取 `hrm.algo.schedule.*`（`minPerShift / maxConsecutiveWork / restCycleDays / weights.{minStaff,consecutive,coverageDeficit,shiftBalance,restSpread} / sa.{iterations,initialTemp,cooling}`），与 `algo-hrm-server.md` §11.3 逐键一致，未新增键。
- **失败降级**：无可行解/算法异常 → 返回贪心解 + 违规清单（`MIN_STAFF` / `CONSECUTIVE`），**不失败**（`SchedulePlanner.Plan.fallback`）。
- Node 原型实测（`node s3-schedule.mjs`，本机实跑）：基线朴素轮转 覆盖率 **0.7778**、最少在岗违规 **20/90**、J=20266.667；贪心 覆盖率 **1.0**、违规 **0**、J=8.667；贪心+退火 覆盖率 **1.0**、违规 **0**、J=**2.667**。Java 侧 `SchedulePlannerTest` 断言「贪心覆盖率 1.0 且违规 0」「退火 J ≤ 贪心 J」「同种子逐位一致」。

### S4 算法落地（参数全外置）
- 选型：**稳健 z（median/MAD，1.4826 归一） + 连缺游程**；参数取 `hrm.algo.attendance.anomaly.*`（`useRobust/lateWarn/lateCritical/consecutiveAbsent/minSamples/windowDays`），与 §11.4 逐键一致。
- 降级：样本量 < `minSamples` → 整体空集；稳健尺度（1.4826·MAD）=0 → 「迟到频次」检测器空集（连缺为阈值型检测器，不受 MAD 影响）。
- Node 原型实测（`node s4-anomaly.mjs`，本机实跑）：稳健 z F1 **0.8889**（P 1.0 / R 0.8）；普通 z F1 0.6667（漏报一半）；连缺阈值 2 天 F1 0.4545、阈值 3 天 F1 1.0。
- **接口承载**：Mock 的 `summary/detail` 出参无异常字段，契约为唯一真源 → 本能力以内部服务 `AttendanceAnomalyService` 形态提供（不新增接口、不改出参），待 `api.md` 定义后接入（已标 `TODO(扩展)`）。

### 与 Mock 的有意差异（契约出参/错误码不变）
1. **`POST /schedules/batch-by-station` 智能排班模式**：`shiftId` 缺省时由 S3 生成（S3 的接口承载），响应追加 `violations/fallback` 且仅在智能模式返回；`shiftId` 给定时与 Mock **逐位一致**。**属对既有端点的可选扩展，需主智能体确认是否固化进 `api.md`**（已标 `TODO(扩展)`）。
2. **写操作原子化**：`POST /schedules/batch` 由 Mock 的「逐条应用、中途报错留部分改动」改为 `@Transactional` 原子提交（错误码与文案逐条一致）。
3. **`check-in` 不加事务**（有意）：校验未通过的尝试必须落 ABNORMAL 留痕，包事务会随异常回滚而丢失留痕。
4. 收班 `'24:00'`、围栏距离 `<= radius`、ABNORMAL 不计入实到/正常/迟到/早退、缺卡 = 应到−实到，均与 Mock 同口径。

### 测试（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`HaversineCalculatorTest`（同点/量级/null→NaN/对称）、`AttendancePeriodResolverTest`（24:00、非法串 NaN、截断、跨天进位、兜底）、`AttendanceCheckPolicyTest`（时段/单班次两套窗口闭区间两端、ALL/ANY、围栏半径 0 边界、迟到/早退阈值 ±1 分钟、首失败码 9103/9104）、`AttendanceSummaryPolicyTest`（口径 + 缺卡不为负）、`AttendanceDetailPolicyTest`（六维名单、到达态优先级、最早卡、风险序）、`SchedulePlannerTest`（§5.4 十项边界）、`AttendanceAnomalyDetectorTest`（§6.4 六项边界 + 稳健 z 12.005）。

### 未验证项（收敛到服务器阶段）
编译打包、Flyway 迁移与快照比对、22 接口端到端冒烟、JSON 列类型处理器（`attendance_rule.wifi_list/check_periods`）真实读写、字面量路由回归、越权口径回归（非 ADMIN 静默收敛 / 403）、CSV 导出流与 `Content-Disposition`、`hrm.algo.schedule.* / hrm.algo.attendance.anomaly.*` 属性绑定校验、S3 退火真实耗时。

### 事实性纠正（不擅自改语义，交主智能体裁定）
1. **导出临时目录配置键命名冲突**：任务书写 `hrm.storage.export-path`，架构 §5.1 写 `hrm.storage.export-dir`（`application.yml` 已落后者）。本批导出走**内存流不落盘**，未消费任一键；建议以架构为准并同步任务书。
2. `OPEN_AHEAD_MIN/CLOSE_DELAY_MIN/围栏默认 300` 为单班次模型/默认规则的等价常量；因 `hrm.algo.*` 须与 §11 逐键一致，**未新增配置键**，已标 `TODO(扩展)`。
3. `pageNum < 1` 服务端按 `@Min(1)` 报 400，而 Mock `paginate` 静默钳制为 1 —— 属 **P0 `PageQuery` 既有口径**（非本批引入），登记待裁定。
4. 非法数值入参（如 `radius:"abc"`）由 Spring 绑定报 400「参数格式不正确」，Mock 报 400「围栏半径须大于 0」：**同为 400、文案不同**（前端不发非法类型，低优先）。

## 2026-09-24 · 服务端 P1+P2（运行日志 3 + 通知 6 = 9 接口 · S8 指纹聚合落地）

### 概述
新增 M1 systemlog（3 接口）与 M2 notification（6 接口）真实后端；落 S8（日志指纹去重 + 时间窗口聚合）；
复用 P0 地基（`@RequireRoles` / `PageQuery` / `ErrorCode` / `AlgoProperties`），未新增依赖。
**未执行任何 DB 迁移**（`V3__client_log.sql` / `V4__notification.sql` 已存在，执行属 C 档，由主智能体授权后运维执行）。

### 交付
| 域 | 接口 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| P1 systemlog（3） | `POST /system/client-logs`（ALL）/ `GET`（ADMIN）/ `POST /clear`（ADMIN） | `controller/systemlog/ClientLogController`、`service/systemlog/{ClientLogService,impl/ClientLogServiceImpl}`、`service/support/{ClientLogSanitizer,ClientLogQueryValidator,LogFingerprintAggregator}`、`mapper/ClientLogMapper`、`entity/ClientLog`、`dto/systemlog/*`、`vo/systemlog/*` |
| P2 notification（6） | `GET /notifications`（本人）/ `GET /unread-count` / `GET /{id}` / `PUT /read-all` / `PUT /{id}/read` / `POST /publish`（ADMIN） | `controller/notification/NotificationController`、`service/notification/{NotificationService,impl/NotificationServiceImpl}`、`mapper/NotificationMapper`、`entity/Notification`、`dto/notification/*`、`vo/notification/*` |

### S8 算法落地（阈值全外置，禁硬编码）
- 日志指纹 = `message|route|code`；去重窗口判定用 `hrm.algo.log.dedupeWindowSeconds`（窗口内同指纹只累加 `count`、刷新 `last_time`）；
- 内存指纹索引（Map + 惰性清扫 + 容量兜底）用 `hrm.algo.log.sweepEvery` / `hrm.algo.log.ringBufferCap`；文本截断用 `hrm.algo.log.textMax`；
- 服务端持久化（`client_log.count/first_time/last_time`）+ 回库兜底（跨实例/重启仍不重复建行）；
- 通知限频令牌桶（`hrm.algo.ratelimit.*`）**本批不适用**（站内信为库内扇出，非外部通道限频；S8 限频对象是「机器人/员工维度的发送请求流」，对应三期企微/短信通道），保留键位待三期接入。

### 测试（本机无 JDK/Maven，未执行，收敛到服务器阶段）
`ClientLogSanitizerTest`（白名单/截断/凭据擦除/path 去 query/指纹）、`LogFingerprintAggregatorTest`（窗口边界/乱序/时钟回拨/清扫/容量）、`ClientLogQueryValidatorTest`、`NotificationRouteOrderTest`（字面量优先于 `{id}`）。

### 未验证项（收敛到服务器阶段）
编译打包、Flyway 迁移与快照比对、9 接口端到端冒烟、字面量路由回归实跑、越权口径回归（通知 9001 / 角色 403）、`hrm.algo.log.*` 属性绑定校验。

## 2026-09-24 · 服务端 P0 地基批次（24 接口兼容性改造 · C-01~C-11 · 不新增业务接口）

### 概述

按 [server-architecture.md](server-architecture.md) §3.2 / §1.4 / §5.1 / §6.2 P0 段落地 P0 地基批次：
将既有 24 接口的横切能力对齐 145 接口新契约（三角色模型、会话 stationId、数据范围收敛三层、统一分页校验、
错误码补齐、登录出参对齐、脱敏扩展、公开端点白名单、路由回归、包结构统一、配置外置）。**不新增任何业务接口**，
**不改 `api.md` / `db.md` / `plan.md` / `server-architecture.md` / `algo-hrm-server.md` / `V1`/`V2` 迁移脚本 / `.trae/rules/`**，
**不改 `hrm-demo` / `hrm-admin` 源码**，**不执行任何迁移或部署**。本机无 JDK/Maven → 仅静态自审，编译与端到端冒烟收敛服务器阶段。

### 交付（C-01~C-11）

| ID | 改造点 | 主要文件（`hrm-server/src/main/java/com/qiujie/`） |
| ---- | ---- | ---- |
| C-01 | 三角色 + 逐端点白名单 + fail-closed | `enums/RoleEnum`（新）、`annotation/RequireRoles`（新）、`annotation/RequireAdmin`（废弃保留）、`config/RequireRolesInterceptor`（新，替代 `RequireAdminInterceptor`）、`config/WebConfig`、5 个控制器注解迁移 |
| C-02 | 会话/上下文补 `stationId` | `common/SessionInfo`、`common/LoginUser`、`util/UserContext#getStationId`、`filter/JwtAuthFilter`（旧会话回查员工表补齐并回写）、`service/auth/impl/AuthServiceImpl` |
| C-03 | 数据范围收敛三层 | `util/DataScopeContext`（新）、`config/QueryDataScopeInterceptor`（新）、`dto/support/StationScopedQuery`（新）、`annotation/DataScope` + `annotation/DataScopeStationId`（新）、`aspect/DataScopeAspect`（新）、`service/support/ResourceAccessChecker`（新） |
| C-04 | 统一分页校验（越界 400） | `dto/support/PageQuery`（新）、`dto/employee/EmployeeQuery`（继承）、`controller/employee/EmployeeController#page`（`@Valid`）、`service/employee/impl/EmployeeServiceImpl#page`（去钳制） |
| C-05 | 错误码补齐至附录 B | `enums/ErrorCode`（补 60xx~96xx，文案逐条对齐 Mock `CODE_MESSAGE`） |
| C-06 | 登录出参字段集扩展 | `vo/auth/LoginEmployeeVO`、`vo/employee/EmployeeVO`（补 `pwdChanged`）、`AuthServiceImpl`、`EmployeeServiceImpl#toVOList` |
| C-07 | 脱敏扩展 | `util/DesensitizeUtil`（新增 `maskName` / `maskBankAccount`） |
| C-08 | 公开端点白名单 | `common/PublicEndpoints`（新，单一真源）、`filter/JwtAuthFilter` |
| C-09 | 路由顺序复核 + 回归清单 | `src/test/java/com/qiujie/architecture/RouteOrderRegressionTest`（新，复核结论：Spring 字面量优先，无需改代码） |
| C-10 | 包结构统一（纯移动） | `controller/service/dto/vo` 下新增域子包，既有 5 域 37 个类迁入（`git mv`，无逻辑变更） |
| C-11 | 配置外置与临时目录骨架 | `config/AlgoProperties`（新，强类型绑定 `hrm.algo.*`）、`application.yml`（补 `hrm.algo.*` / `hrm.storage.*` / `spring.servlet.multipart.location`） |

### 新增依赖（唯一）

- `org.springframework.boot:spring-boot-starter-aop`：C-03 L3 的 `@Aspect` 切面需 aspectjweaver，Spring Boot 未默认引入。
  仅用于横切能力，未引入完整 Spring Security 等重型组件（决策 D10）。

### 有意变更清单（相对改造前 24 接口行为差异，仅此 5 类）

1. **分页越界改 400**：`GET /employees` 的 `pageSize>100 / <1 / 非数字` 由「钳制」改为 HTTP 200 + code 400（文案「每页条数须为 1-100」，与 Mock 一致）；`pageNum<1` 由「钳制为 1」改为 400（同时消除负 OFFSET 风险）。`GET /employees/export` 不加 `@Valid`，与 Mock 一致（导出不校验分页）。
2. **错误码扩展**：`ErrorCode` 新增 60xx~96xx 段，既有 24 接口可用码值/文案零变化。
3. **脱敏范围扩展**：`DesensitizeUtil` 新增姓名/银行卡脱敏，24 接口既有手机号脱敏口径零变化。
4. **登录出参新增 `stationId` 等字段**：`POST /auth/login` 的 `data.employee` 只增不减（新增 `gender/deptId/deptName/stationId/stationName/status/entryDate/remark/lastLoginTime/createTime`）；`GET /employees` 出参新增 `pwdChanged`。既有字段名/类型/含义不变。
5. **角色注解迁移**：`@RequireAdmin` → `@RequireRoles({"ADMIN"})`，语义等价；`/auth/logout|me|password` 显式声明「任意登录角色」，行为等价（原为无注解放行）。**新增 fail-closed 纪律**：非公开端点若未声明角色门槛一律 403（当前 24 接口全部已声明，无行为变化）。

### 未验证项（收敛到服务器阶段）

- 编译打包、应用启动、`hrm.algo.*` 属性绑定校验、既有 24 接口端到端冒烟、`spring.servlet.multipart.location` 目录预创建。
- 单测（新增 6 个测试类：`RoleEnumTest` / `PublicEndpointsTest` / `PageQueryConstraintTest` / `ResourceAccessCheckerTest` / `ErrorCodeSegmentTest` / `RouteOrderRegressionTest`，并扩展 `DesensitizeUtilTest`）本机无法执行。

### 事实性纠正（架构文档 vs 源码实际）

- 架构 §3.2 C-03 记 `interceptor/QueryDataScopeInterceptor`，但 §1.2 包结构与既有实现均无 `interceptor` 包 → 实装于 `config/`（与既有 `RequireAdminInterceptor` 位置一致），避免新增孤立包。
- `HandlerInterceptor` 无法替换交给 handler 的 `HttpServletRequest`，故 L1 以「`QueryDataScopeInterceptor` 写入 `DataScopeContext` + 统一 Query 基类读取」等价实现（原文档仅写「拦截器覆盖 query stationId」）。

### 遗留与 `TODO(扩展)`

- 数据范围兜底：非 ADMIN 无归属时按架构 8-2 推荐项 B 收敛为「无数据」（`stationId=-1` 哨兵）；若用户裁定为 A（拒绝 403）需改 `QueryDataScopeInterceptor`（已标注 TODO）。
- 公开端点 `/api/v1/work-orders/auto-dispatch`：仅白名单就位，企微签名/解密与安全评审前置（P8）。
- `@DataScope` L3 资源归属解析器注册表（`resourceKey → resolver`）待后续批次接入。
- `@RequireAdmin` 及拦截器兼容分支待下一批次确认无引用后删除。
- `logging.file.path` 未写入仓库 yml（注释形式）：避免开发机无 `/data` 目录时启动写日志失败，生产由外置配置打开。
- **文档待同步（交测试工程师）**：`test-cases.md` TC-E01-019「员工分页-pageSize 上限（≤100）」预期仍为「钳制为 100」，与 C-04「越界 → 400」冲突，需按有意变更清单同步修订。

---

## 2026-09-24 · 服务端 24 → 145 接口 · 数据库设计（P1~P10 共 33 张新表）

### 概述

按 [server-architecture.md](server-architecture.md) §4 与 [algo-hrm-server.md](algo-hrm-server.md) §11/§13，
将数据库从一期 4 表扩展至 **37 表**（既有 4 + 新增 33）；产出 B 档 Flyway 迁移脚本 V3~V13（**未执行**，
执行属 C 档，待主智能体三步授权）、全量快照与设计文档。**本轮不写 Java 业务代码**。

### 数据库工程师交付

| 变更 | 文件 |
| ---- | ---- |
| 新增 P1 运行日志迁移 | `hrm-server/src/main/resources/db/migration/mysql/V3__client_log.sql` |
| 新增 P2 通知迁移 | `.../mysql/V4__notification.sql` |
| 新增 P3 考勤 5 表迁移 | `.../mysql/V5__attendance.sql` |
| 新增 P4 KPI 2 表迁移 | `.../mysql/V6__kpi.sql` |
| 新增 P5 人事 5 表迁移 | `.../mysql/V7__hr.sql` |
| 新增 P6 财务 4 表迁移 | `.../mysql/V8__payroll.sql` |
| 新增 P7 请假 3 表迁移 | `.../mysql/V9__leave.sql` |
| 新增 P8 工单 4 表迁移 | `.../mysql/V10__work_order.sql` |
| 新增 P9 同步运行态 2 表迁移 | `.../mysql/V11__sync_task.sql` |
| 新增 P9 配置中心 5 表迁移 | `.../mysql/V12__sync_config_center.sql` |
| 新增 P10 parcel 大表迁移 | `.../mysql/V13__parcel.sql`（CREATE TABLE + 独立 CREATE INDEX，含 DROP INDEX 回滚） |
| 刷新 MySQL 全量快照 | `sql/schema/mysql/init.sql`（== V1+V3..V13，37 表） |
| 更新数据库设计文档 | `docs/db.md` v2.0（新增第 8 章表结构设计 + 第 9 章静态自检/待确认/未验证项） |
| 变更日志 | `docs/update-log.md`（本条） |

- **批次与版本 1:1**：P1→V3 … P10→V13；版本号单调，**未发生顺延**（`employee` 经核对无需补索引，理由见 db.md §9.2）。
- **历史脚本未改**：`V1__init_schema.sql` / `V2__init_data.sql` 零改动；`postgresql/` 目录冻结不再维护。
- **未实跑**：本机无 JDK/MySQL/Redis，仅静态自检；DDL 实跑与 EXPLAIN 验证收敛服务器阶段（db.md §9.4）。
- **待确认**：`parcel` 查重索引命名（`idx_` vs 架构 §4.3 的 `uk_`）、配置中心 2 个 JSON 列、`hr_flow.type` 命名（db.md §9.1）。
- **事实性纠正**：架构 §4.2 `parcel.shelf_no/batch_no` 与 Mock 真源不一致，按 Mock 落为 `shelf_code/sync_batch_no`；`payroll.total_amount` 按 Mock 落为 `gross_amount`（db.md §8.12）。

### 表清单（新增 33 张）

| 批次 | 版本 | 表（索引数） |
| ---- | ---- | ---- |
| P1 | V3 | client_log(2) |
| P2 | V4 | notification(2) |
| P3 | V5 | attendance_rule(1) / attendance_shift(1) / attendance_schedule(2) / attendance_record(2) / attendance_makeup(2) |
| P4 | V6 | kpi_metric(2) / kpi_score(3) |
| P5 | V7 | hr_profile(1) / hr_salary(1) / hr_salary_log(1) / hr_flow(3) / hr_flow_step(1) |
| P6 | V8 | payroll_rule(1) / payroll_rule_item(1) / payroll(4) / payroll_item(1) |
| P7 | V9 | leave_request(3) / leave_log(1) / leave_setting(0) |
| P8 | V10 | work_order(4) / work_order_timeline(1) / work_order_transfer(1) / work_order_dispatch_rule(1) |
| P9 | V11 | sync_task(3) / sync_task_log(1) |
| P9 | V12 | sync_station_config(1) / sync_config_item(2) / sync_config_option(1) / sync_config_global(1) / sync_config_station_override(1) |
| P10 | V13 | parcel(4) |

---

## 2026-09-06 ~ 2026-09-07 · 一期员工管理 · 初始交付

### 概述

从空骨架（仅目录结构 + 规范文档 + 配置模板）完成一期"员工管理"全量代码与文档交付。
采用智能体流水线：架构师 → 数据库工程师 → 后端工程师 → 前端工程师 → 运维工程师 → 测试工程师，主智能体编排调度与 Review。

### A 阶段 · 数据库

| 变更 | 文件 |
| ---- | ---- |
| 新增 MySQL 建表脚本（4 表 11 索引） | `hrm-server/src/main/resources/db/migration/mysql/V1__init_schema.sql` |
| 新增 MySQL 种子数据（admin + 总公司部门） | `hrm-server/src/main/resources/db/migration/mysql/V2__init_data.sql` |
| 新增 PostgreSQL 建表脚本 | `hrm-server/src/main/resources/db/migration/postgresql/V1__init_schema.sql` |
| 新增 PostgreSQL 种子数据（含 setval 序列重置） | `hrm-server/src/main/resources/db/migration/postgresql/V2__init_data.sql` |
| 新增 MySQL 结构快照 | `sql/schema/mysql/init.sql` |
| 新增 PostgreSQL 结构快照 | `sql/schema/postgresql/init.sql` |

- BCrypt 哈希经 bcryptjs 双版本回环验证（$2a$ cost=10，与 Spring BCryptPasswordEncoder 兼容）
- 双库表结构逐列一致，种子数据一致

### B 阶段 · 后端（Spring Boot）

| 变更 | 说明 |
| ---- | ---- |
| 新增 pom.xml | Spring Boot 3.3.4 / Java 17 / MyBatis-Plus 3.5.7 / jjwt 0.11.5 / EasyExcel 3.3.4 / Flyway / Lombok / spring-security-crypto |
| 新增 application.yml | 端口 8080，全部配置 change_me 占位，无敏感信息 |
| 新增公共设施 | Result/PageResult/BusinessException/GlobalExceptionHandler/ErrorCode(26 码)/JwtUtil/JwtAuthFilter/RequireAdmin 拦截器/UserContext/SessionUtil/DesensitizeUtil/IpUtil/PasswordUtil |
| 新增 MyBatis-Plus 配置 | 分页插件 + 逻辑删除 + 时间填充 + MapperScan |
| 新增 Redis 配置 | RedisTemplate(Jackson 序列化) + 会话工具(key=hrm:session:{employeeId}) |
| 新增 4 实体 + 4 Mapper | Department/Station/Employee/LoginLog |
| 新增 12 DTO + 10 VO | 请求/响应对象，@Valid 校验中文文案 |
| 新增 5 Service + 5 Impl | Auth/Employee/Department/Station/Dashboard |
| 新增 ImportRowValidator | 行级校验器（纯逻辑，离线可测） |
| 新增 5 Controller | 24 接口 100% 对齐 api.md |
| 新增 4 离线单测 | JwtUtilTest/DesensitizeUtilTest/FieldValidatorTest/ImportRowValidatorTest |

- 偏离 api.md 2 处（从严实现）：上传超 10MB → code 400；启用自己 → 2001
- 编译验证收敛到部署阶段（本机无 JDK）

### C 阶段 · 前端（Vue 3 + Vite）

| 变更 | 说明 |
| ---- | ---- |
| 新增工程基础 | package.json / vite.config.js / index.html / main.js / App.vue |
| 新增请求封装 | request.js（Bearer 注入、code 分发、401/403 处理、blob 解析） |
| 新增下载工具 | download.js（Content-Disposition 解析 + 中文文件名） |
| 新增 Pinia store | auth.js（token/user 持久化 localStorage） |
| 新增路由 + 守卫 | 4 条守卫规则（白名单/未登录/首登锁定/STAFF 越权） |
| 新增 5 API 模块 | auth/dashboard/employee/department/station（24 接口封装） |
| 新增布局 | layout/index.vue（侧边菜单按角色渲染） |
| 新增 7 页面 | login/dashboard/employee/department/station/profile + 5 占位保留 |

- `npm install` 成功（96 packages）
- `npm run build` 成功（1696 modules，dist 产物 21 文件）
- 24 接口封装与 api.md 100% 对齐，无偏离

### D 阶段 · 部署

| 变更 | 文件 |
| ---- | ---- |
| 新增部署脚本 | `deploy/deploy.sh`（幂等，set -e，双模式 source/jar，安全防呆三处） |
| 新增 systemd 单元 | `deploy/hrm-server.service` |
| 新增 Nginx 配置样例 | `deploy/nginx.conf.example`（SPA 回退 + /api 反代 + 透传 IP） |
| 新增部署手册 | `docs/deploy.md`（D01-D05 全覆盖 + 回滚 + FAQ + 附录） |

- bash -n 语法检查通过
- 全部占位符，无真实 IP/密钥

### E 阶段 · 测试

| 变更 | 文件 |
| ---- | ---- |
| 新增验收测试用例 | `docs/test-cases.md`（167 条用例 + 8 条设计疑问） |

- E01 接口冒烟 83 条 / E02 前端走查 23 条 / E03 权限越权 19 条 / E04 导入导出 17 条 / E05 PG 双库 10 条 / E06 文档复核 15 条
- 实际执行需部署后进行

### 设计文档

| 变更 | 文件 |
| ---- | ---- |
| 新增需求文档 | `docs/requirement.md`（21 项功能 + 13 项决策 + 验收标准） |
| 新增数据库设计 | `docs/db.md`（4 表 ER + 字段定义 + 索引设计 + 双库映射） |
| 新增接口文档 | `docs/api.md`（24 接口 + 26 错误码 + 登录态机制） |
| 新增迭代规划 | `docs/plan.md`（三期里程碑 + 风险登记） |
| 新增任务清单 | `TASK.md`（44 项原子任务，A/B/C/D/E 分组） |

### TASK.md 状态汇总

| 阶段 | 任务数 | 已完成 | 收敛项 |
| ---- | ---- | ---- | ---- |
| A 数据库 | 5 | 5 | — |
| B 后端 | 17 | 16 | B17 编译验证收敛到 D03 |
| C 前端 | 11 | 10 | C11 联调冒烟收敛到 E01/E02 |
| D 部署 | 5 | 0 | 文档/脚本已就绪，实际执行需服务器 |
| E 测试 | 6 | 0 | 用例已就绪，实际执行需部署后 |

### 遗留风险

1. **本机无 JDK/Maven**：后端编译验证未执行，收敛到服务器部署阶段
2. **本机无 MySQL/Redis**：Flyway 执行与接口冒烟未执行
3. **8 条设计疑问**（见 test-cases.md §10）：需人工确认后决定是否调整接口/文档
4. **2 处从严偏离**：上传超限→400、启用自己→2001，需确认是否符合预期
5. **前端脱敏字段编辑体验**：手机号脱敏导致编辑时需重新输入完整号码（契约必然结果）
6. **宝塔 Java 项目管理器**：deploy.sh bt 模式不含自动重启（面板无稳定 CLI），需人工点击

---

## 2026-09-24 · 三端演示 Demo · 移动端通知阅读页（NoticeReader）设计规范

### 概述

缺陷：移动端「消息」的通知项点击后打不开、读不全（用户原话「消息里的所有文件应该是可以打开的，现在打不开，点一下就确认了」）。
本轮**只产出设计规范**，零源码改动。产物为 `docs/demo-ux-improvement.md` 新增 D 章（D1–D14）。

### 变更

| 变更 | 文件 |
| ---- | ---- |
| 新增 D 章「移动端通知阅读页（NoticeReader）」共 14 节 | `docs/demo-ux-improvement.md`（第 1584–2143 行） |
| 登记本次新增 | `docs/update-log.md`（本节） |

### 缺陷定位（全量代码核查结论）

| # | 根因 | 位置 |
| --- | --- | --- |
| R-1 | 点击处理函数无兜底分支：按 `bizType` 分流到第 152 行即结束，第 153 行只有 `TODO`，**无 `else`** | `hrm-demo/src/mobile/components/NoticeList.vue:105-154` |
| R-2 | 全工程不存在通知详情/阅读页（移动端路由表无 noticeDetail / notificationDetail） | `src/mobile/router/index.js`、`src/mobile/modules/boss/router.js` |
| R-3 | 列表正文只渲染一行且无 `line-clamp`，长正文把行撑到十几行 | `NoticeList.vue:228` + `src/mobile/styles/mobile.scss:269-274` |
| R-4 | 既有缺陷：`payroll` 通知固定跳 `/staff/payroll/:id`（`meta.roles` 为 `STAFF_ROLES`），老板端（ADMIN）点击必被守卫拦下 | `NoticeList.vue:137-140` vs `router/index.js:190-194` |

**术语校正：** 用户说的「文件」= 通知/公告条目本身（公文语义），不是附件。全工程无附件数据概念；附件能力登记为 `TODO(扩展): 通知附件字段与预览能力`，本轮不做。

### 关键设计结论

| 项 | 结论 |
| ---- | ---- |
| 路由契约 | **两条新增路由**指向同一新组件：`/staff/message/notice`（`staffNoticeReader`，`roles: STAFF_ROLES`）+ `/boss/message/notice`（`bossNoticeReader`，`roles: [ADMIN]`），均 `title: '通知详情'`、无 `tabbar`；参数用 **`?id=`**（缺参可自渲染错误态，不落全局 404）。`/boss/*` 既有 path/name/meta 一字未动 |
| 组件落点 | `src/mobile/views/message/NoticeReader.vue`（跨端共用，不进任何一端域目录） |
| 点击行为 | **一律先进阅读页**，业务动作降为页内底部「去处理」（不保留直接跳转）；标记已读的发起**从列表移到阅读页**（详情取回成功后），失败**不阻断**阅读，仅给提示条 |
| 动作区 | 复用 `ActionBar`；`work_order`/`parcel`/`payroll`/`sync_task`/`flow`/`leave` 逐格给出「`bizType` × 角色」目标表；`payroll` 老板端改落 `/boss/payroll/{bizId}`（关闭 R-4）；`sync_task` STAFF 禁用 + note 说明；无 `bizType` 时 `ActionBar` 整块隐藏 |
| 返回路径 | 列表进入走 `router.back()` 并恢复原 Tab 与滚动位置 + `is-highlight` 高亮；深链走 `router.replace` 兜底（`PageNav` 新增可选 prop `backFallback`，默认 `''` 保持原行为） |
| 状态最小集 | 7 态逐项落点；**不单独做「无权限」态**（路由级由守卫拦截；数据级非本人统一回 9001，不泄露存在性）；阅读页**不做已读/未读视觉区分**，仅失败时给提示条 |
| 复用与新增 | 复用 `PageNav`/`PageState`/`StatusTag`/`ActionBar`/`van-notice-bar`；**新增页面 1 个**（`NoticeReader.vue`）、**新增通用子组件 0 个**、**新增 Token 0 个**；明确不复用 `ListItemCard`/`BossScopeNote` 并给出理由 |
| Mock 契约 | **需要新增 `GET /notifications/:id`**（`roles: ALL_ROLES`，出参复用 `toNotificationVO`，失败统一 9001）。列表 VO 已含全部展示字段，**不新增 VO 字段、不改种子、不改 db 结构**；新路由须注册在 `/notifications/unread-count` 之后（引擎取首个命中） |
| 无障碍 | 对比度**实算**：给出公式 + 11 个 L 中间量 + 8 组比值（正文 14.68:1、元信息 4.83:1、主按钮 6.16:1、提示条 4.83:1、chevron 3.24:1）；登记 1 处反例——`--text-placeholder` 对白底仅 **2.52:1**，禁止用于图标；正文/元信息**强制落在 `--surface-card`**（落页面底色仅 4.50:1 无余量）；触控逐元素 ≥44px |

### 验收

- D12.1 静态审查 12 条（含「零十六进制色值」「零新增依赖」「`/boss/**` 零改动」）
- D12.2 e2e 可断言 15 条（含深链 9001 态、老板端 payroll 回归用例、无横向滚动、`role=toolbar` 计数）
- D14 规范自身自检 10 项全通过

### 遗留与待裁决

见 D 章 D13，共 10 条开放问题，其中涉安全/数据面 3 条（已读回执 #1、正文 URL 外跳 #8、附件能力 #9），需主智能体或用户裁决后方可进入实现阶段。

### 边界声明

- 本轮**零源码改动**：仅新增/修改 `hrm-dev/docs/` 下两个文档
- `hrm-admin/**`、`hrm-server/**`、`hrm-android-shell/**` 零改动
- 未引入新第三方依赖、未使用图片资源

---

## 2026-09-24 · 服务端算法方案（algo-hrm-server）

### 概述

为 `hrm-server` 将 Mock 行为规格算法化，覆盖 8 个场景，均按「①问题建模 ②算法选型与复杂度 ③依据来源 ④可验证指标与基准」四件套交付。
本轮**只出算法方案与离线原型**，不写 Java 业务代码、不改 `hrm-demo`/`hrm-server` 源码、不改 `api.md`/`db.md`。

### 变更

| 变更 | 文件 |
| ---- | ---- |
| 新增算法方案主文档（15 节，含 6 条待裁定口径 + 完整 `hrm.algo.*` 参数表） | `hrm-dev/docs/algo-hrm-server.md` |
| 新增离线原型脚本（无第三方依赖，`.mjs`，固定种子） | `hrm-dev/docs/algo-scripts/{lib/rng.mjs, lib/stats.mjs, s1-kpi.mjs, s2-payroll.mjs, s3-schedule.mjs, s4-anomaly.mjs, s5-leave.mjs, s6-dispatch.mjs, s7-parcel.mjs, s8-ratelimit.mjs, run-all.mjs}` |
| 登记本次新增 | `hrm-dev/docs/update-log.md`（本节） |

### 关键结论（离线实测）

| 场景 | 基线 → 算法化 |
| ---- | ------------- |
| S1 KPI | 阶梯参数化等价性 1200 项 **0 不一致**；新增分位映射可选（等级阈值须同步重标定，已列 Q2） |
| S2 工资 | 来源解析器注册表，**新增计薪项不改核心代码**；同月重复生成 **0 净额差异** |
| S3 排班 | 覆盖率 0.7778 → **1.0**，最少在岗违规 20 → **0** 格；发现 Mock 朴素轮休 `%6==5` 蕴含 `%3==2` 导致晚班人次仅一半（80/80/40） |
| S4 异常检测 | 稳健 z F1 **0.889**（普通 z 0.667）；连缺阈值 3 天 F1 **1.0** |
| S5 请假 | 半天单元区间算法：计薪天数 **18.5×**、重叠判定 k=2000 时 **6147×**，**0 结果差异** |
| S6 派单 | 平均完成时长 9.447h → **1.504h**，SLA 78.3% → **100%**，技能匹配 89% → **100%**（代价：负载 Jain 1.000→0.978） |
| S7 包裹 | Holt-Winters MAPE **5.34%**（朴素 6.10%）；20 万级保守落盘 **105.83 MB = 数据盘 46G 的 0.22%**；游标分页扫描 38 行 vs 深分页 20020 行 |
| S8 限频/日志 | 峰值 23→**14**/s；10 万条日志聚合 **114×**、压缩比 **199×** |

### 边界声明

- 本轮**零源码改动**：仅新增 `hrm-dev/docs/algo-hrm-server.md` 与 `hrm-dev/docs/algo-scripts/`（11 文件）
- `hrm-demo/**`、`hrm-server/**`、`hrm-admin/**`、`hrm-android-shell/**` 零改动；`api.md`/`db.md` 零改动
- 基准数据全部由固定种子合成，**不含任何真实业务数据**
- 未运行项（须服务器复核）：MySQL 真实耗时、InnoDB 真实落盘字节、Redis 令牌桶并发
- 待裁决：`algo-hrm-server.md` §2 的 Q1–Q6 共 6 条口径（KPI 等级阈值与模式、绩效 capRatio 与负净额、SLA 阈值、派单权重、请假扣款开关与连缺阈值）

---

## 2026-09-24 · M4 服务器实跑单元测试 5 项失败修复（后端工程师）

### 缺陷逐条判定

| # | 用例 | 判定 | 契约依据 | 修复动作 |
| --- | --- | --- | --- | --- |
| 1 | `EndAdmissionPolicyTest.h5BossView` | **实现 bug** | `hrm-demo/src/shared/mock/routes/auth.js:62-69`（`clientType==='H5' && as==='boss'` 仅 ADMIN/STATION_ADMIN） | `AS_BOSS` 常量为小写 `"boss"`，与 `normalizeToken(as)`（已转大写）直接 `equals` 永不命中 → boss 视图角色约束被静默跳过；改 `equalsIgnoreCase` |
| 2 | `SmsCodeStoreTest.saveCodeSetsTtlAndClearsAttempts` | **测试 bug** | `docs/db.md:1210-1211`（码 TTL 300s 校验成功即删；失败计数 TTL 300s） | 断言误期望连 `CODE_KEY` 一并删除（会删掉刚 `set` 写入的码，与口径矛盾）→ 改为精确断言只清 `ATTEMPT_KEY` |
| 3-5 | `TrustedDeviceRegistryTest.upsertReactivatesExistingRow / revokeByDeviceId / revokeAllOfEmployee` | **测试 bug** | MyBatis-Plus 3.5.7 `LambdaUpdateWrapper.set(...)` 即时解析列名，需 `TableInfo` 缓存（`TableInfoHelper.initTableInfo` → `LambdaUtils.installCache`） | 纯单测无 Spring/MP 上下文 → 测试加 `@BeforeAll TableInfoHelper.initTableInfo(...)` 注册 `AuthTrustedDevice` 元数据 |

### 变更文件

| 文件 | 说明 |
| ---- | ---- |
| `hrm-server/src/main/java/com/qiujie/service/auth/support/EndAdmissionPolicy.java` | `as` 归一后忽略大小写比较，修复 boss 视图约束失效 |
| `hrm-server/src/test/java/com/qiujie/service/auth/support/SmsCodeStoreTest.java` | 修正 `saveCode` 删除断言；移除未用 `import java.util.List` |
| `hrm-server/src/test/java/com/qiujie/service/auth/support/TrustedDeviceRegistryTest.java` | 新增 `@BeforeAll` 初始化 MyBatis-Plus `TableInfo` |

### 未验证 / 收敛与纪律

- 本机无 JDK/Maven，**仅静态自审**（诊断零告警；`TableInfoHelper.initTableInfo` 签名、`GlobalConfig` 默认处理器非空、`MybatisConfiguration` 无参构造器均按 3.5.7 源码核对）；`mvn test` 复验收敛服务器阶段，目标 509 用例全绿。
- HTTP 契约、业务口径、迁移脚本、`hrm-demo`/`hrm-admin` 零改动；断言未弱化（#2 属纠错且更精确），未加 `@Disabled`、未改 `pom.xml`。
- 观察项（低风险，无需裁定）：`as` 比较改为大小写不敏感，较 Mock 的精确 `=== 'boss'` 略宽松；与既有 `clientType` 归一（`EndAdmissionPolicyTest.webIsCaseInsensitive` 已确立）一致，方向上更偏 fail-closed。

---

## 2026-09-25 · 修复 leave_log 保留字列名导致的 500（全量同类隐患扫描）

### 概述

服务器 dev（库 `kdyzgl_test`）实测 500：`BadSqlGrammarException ... near 'before,after,remark FROM leave_log'`
（工作台刷新 → `/leave/my?status=PENDING`、`/leave/list?status=PENDING_STATION` 关联查 `leave_log`）。
根因：`leave_log.before` 未转义，`BEFORE` 为 MySQL 8.0 保留字（官方关键字表标记 (R)），MyBatis-Plus 生成
SQL 不自动加引号 → 1064 语法错误。本机无 JDK/Maven，仅静态自审，测试收敛到服务器阶段。

### 后端工程师交付

| 变更 | 文件 |
| ---- | ---- |
| 实体列名转义 | `hrm-server/src/main/java/com/qiujie/entity/LeaveLog.java`（`before`/`after` 加 `@TableField` 反引号列名，导入 `TableField`） |
| 新增回归单测 | `hrm-server/src/test/java/com/qiujie/entity/LeaveLogReservedWordMappingTest.java`（新） |
| 新增解析/契约单测 | `hrm-server/src/test/java/com/qiujie/service/leave/support/LeaveLogSnapshotJsonTest.java`（新） |

**保留字扫描（38 表全量）**：基准 MySQL 8.0 关键字表逐一比对 `init.sql` 列定义，**唯一保留字列 = `leave_log.before`**；
`leave_log.after` 为非保留关键字（一并转义，防御性）；`client_log.count` 非保留（原生 SQL 已转义，保留不动）；
`HrAllowance.key` 为 `allowances` JSON 数组内嵌字段、非表列（无风险）。除上述外全量核对无命中。
未改 `init.sql` / V1–V15 迁移 / HTTP 契约（URL、入参、出参、错误码零变化）。

### 有意变更清单

- 仅 SQL 层列名转义（反引号包裹 `before`/`after`）；HTTP 出参字段名仍为 `before`/`after`。

### 未验证项（收敛到服务器阶段）

- 编译打包、单测实跑（本机无 JDK/Maven）；`leave_log` 真实读写在 dev 库的端到端验证。
- `mvn -q -Dtest=LeaveLogReservedWordMappingTest,LeaveLogSnapshotJsonTest test` 与工作台刷新冒烟未执行。

### 事实性纠正

- 任务描述称 `before`/`after` **均**为 MySQL 保留字：据官方关键字表，仅 `BEFORE` 标 (R) 为保留字；
  `AFTER` 为非保留关键字（可裸用）。报错行 `'before,after,remark'` 由 `before` 触发，二者一并转义属防御性加固。
- PostgreSQL 快照（`sql/schema/postgresql/init.sql`）仅含 4 表（department/station/employee/login_log），
  无 `leave_log`；跨库引用需另议（本次 MySQL 单库锁定）。

### 遗留与 `TODO(扩展)`

- 建议（仅建议，未动 DDL）：`leave_log.before/after` 语义弱且 `before` 命中保留字，长期可考虑重命名为
  `before_snapshot`/`after_snapshot`（需新迁移版本号 + 快照同步 + 数据库工程师评估，属 C 档）。
- `TODO(扩展)`：切换 PostgreSQL 时反引号需改双引号（方言差异），同 `ClientLogMapper` 既有标注。

