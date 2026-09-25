# 安全技术评估报告：ADR B7「发布切换」上线前（P0.5 / L7 闸门）

- 评估对象：`hrm-dev/docs/adr-structure-migration.md` **§3.7 批次 B7「发布切换（★ 唯一切换点）」** 与其上线产物
- 任务来源：项目规则 §8 审批纪律 / 调度规则 **P0.5 安全评估先行**、**L7 安全评估 → 授权 → 实现**（C 档授权前置闸门）
- 评估方：网络安全工程师 `express-station-security-engineer`（评估方与决策方分离）
- 评估方式：**纯静态形式与技术评估**——源码/配置只读检索 + 对已构建 `build:prod` 产物做静态检索（Node/PowerShell 文本扫描）
- 环境：本机**无 JDK / Maven / MySQL / Redis，无服务器访问**；未做任何运行期 / 渗透 / 扫描 / 现网核查
- 权限档位：A 档（只读检索 + 新建本报告）
- 报告内**不含真实凭据 / 生产域名 / 服务器 IP**；命中证据一律截断或标注「值按纪律不落本文」
- **结论等级：有条件放行** —— 一句话：本批「只增不改」思路与三端产物在**静态维度未发现凭据/数据/域名泄露**，但新增 location **必须内置 `.map→404`、缺失资源 404、`index.html` no-store、独立 `root`、安全响应头 5 项硬约束**并经运维实测；其中 `.map` 若不能在本批落地或验证，即触发 §10.4 **D 档红线**，**须升级用户裁定、主智能体不得直接放行**。

---

## 1. 评估范围与本报告边界声明

**评估范围（仅此）**
1. `.map` 公开下载风险与 §10.4 红线判定（ADR §9 SP2、B7 验收 ④）；
2. 三端 `build:prod` 产物在**无鉴权静态站**下的信息泄露（演示口令/账号、Mock 数据/种子、真实域名/IP/凭据）；
3. 新增 3 条 location 与既有 `location /` 的路径隔离与 `..`/编码穿越面；
4. 传输与安全响应头（HTTPS 强制、混合内容、HSTS、nosniff、Referrer-Policy）是否本批闭环；
5. 前端写死 API 基址 `/hrm-api/v1` 的新增暴露面判定；
6. 「零中断」前提（R新-1）成立性与未登记风险；
7. B7 回滚面可逆性与留痕；
8. 必须由运维核实的闭环项（载体路径 / 脚本 / location 现状 / `.map` 现状 / 证书与 TLS）；
9. 顺带：ADR v2 §9 SP7「真实域名入库」形式核对复验。

**边界声明（本报告不含）**
- **不含可执行验证**：无渗透 / 无扫描 / 无现网 curl / 无门禁实跑；一切「现网实际配置」按**待运维核实项**处理，不假定；
- **不代改配置**：本报告一字未改源码、`.env*`、`deploy/**`、ADR 与其它 `docs/**`；Nginx 落地建议仅为建议，执行归运维；
- **不代改漏洞**：只出结论 + 修复建议 + 验收标准，返工归对应实现角色（反模式 A17）；
- **不代授权**：不代主智能体拍板；仅出技术结论、风险等级与是否可放行（§8 审批纪律 / §10.3）；
- **未调用 MCP、未执行 git 操作**；未读取任何 `.env.demo` 内容（按「不读凭据文件」纪律，仅核其被 `!` 放行的事实）。

---

## 2. 结论等级

**有条件放行（Conditional Go）。**

- **可放行前提（全部满足方可授权执行）**：§4 **M1–M5** 必改项纳入本批变更，且 §4 验收命令由运维实测通过；§6 **V1–V6** 载体待核实项闭环。
- **放行后仍受约束**：本批仅「新增 3 条 location」，**不得改动既有 location**；HTTPS 强制与 HSTS 若需改既有 server 级配置，则**本批不做**（§3-4）。
- **高风险升级点**：`M1`（`.map` 公开）命中 §10.4 D 档红线。**若本批无法落地 `.map` 拒绝或无法实测验证，则不得判定上线成功，须升级用户裁定**（冲突裁决表「安全风险判定 vs 进度」/ A16）。

---

## 3. 逐条结论表

> 证据列给出「文件:行号」或「检索命令 + 命中数」；报告不落真实值。

| # | 评估项 | 结论 | 依据（文件:行号 / 检索命令 + 命中数） | 风险级 | 阻塞上线 |
| - | --- | --- | --- | - | - |
| 1 | **`.map` 公开下载** | **命中 §10.4 红线风险（须修）**：产物内 `.map` 与 `.js` **一一对应**且 **全部含 `sourcesContent`（完整原始源码）**；`build:prod` 的 `sourcemap:'hidden'` 仅去掉 JS 内 `sourceMappingURL` 注释（实测 `sourceMappingURL` 命中 = 0），**不阻止 `.map` 按同路径 + `.map` 被直接猜中下载**。新增 location 走**新站点根**（绕过旧演示容器内既有的 `.map` 拦截），故**若新 location 不显式拒绝，`.map` 即可公开** | 计数：web `map=104 js=104`、staff-h5 `map=43 js=43`、boss-h5 `map=51 js=51`；含 `sourcesContent` 计数：`104/43/51`（全部命中）；`sourceMappingURL` 命中 = 0；配置依据 [staff vite.config.js:17,73](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/vite.config.js#L17-L74)、[boss vite.config.js:17,74](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/boss-h5/vite.config.js#L17-L74)；旧演示容器已有 `.map→404` 先例见 [docker-demo/nginx.conf:55-57](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/docker-demo/nginx.conf#L51-L57)（**非本批载体**）；规则 §10.4「把生产 `*.map` 部署为可公开访问」、§7.1 L182「生产构建 `*.map` 不得公开」 | **高** | **是**（M1） |
| 2 | **静态站无鉴权信息泄露** | **不泄露（凭据/数据/域名三项均 0 命中，无需修）**。① 演示口令 `demo1234` 命中 = 0；演示账号 `st001_admin`/`st001_staff` 命中 = 0（`st001` 唯一命中为**大小写不敏感**误报的占位文案 `SSID，如 ST001-Express`）；演示手机号命中 = 0。② Mock 代码/数据未进产物：`installMock`/`@kdyzgl/mock`/`MockAdapter`/`createMock` 命中 = 0，文件名 `install|db-|mock|seed|faker` 命中 = 0（**独立复核主智能体"=0"结论成立**）。③ 真实域名/公网 IP：`kongzhen` 命中 = 0，staff/boss IPv4 命中 = 0（web 6 处 IPv4 均为 Element Plus **IPv6 校验正则内的注释示例 `1.2.3.4`**，非真实 IP）；`http://` 命中全部为 W3C 命名空间与 axios 平台垫片 `window.location.href||"http://localhost"`，**无混合内容**。⚠️ 残留 3 处**演示文案**（低，非泄露，见 §7-1）：boss `演示账号` tooltip、staff/boss `演示身份`（prod 占位报错串，功能已被编译为抛错 stub）、`城东驿站`（表单占位示例与说明文案） | 命中表见附录 A；prod 桩 [staff dist/index-DeQxGynU.js] 「当前环境未开启演示身份切换」；演示常量真源 [staff-h5/src/demo/accounts.js:9-14](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/staff-h5/src/demo/accounts.js#L9-L14)、[packages/mock/src/db.js:28](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/packages/mock/src/db.js#L28)（**均未进 prod 产物**） | **低** | 否 |
| 3 | **路径隔离与穿越** | **有条件通过**：3 条前缀 location（最长前缀优先）不会抢占既有 `/`、`/api/`、`/admin/`、`/hrm-api/`，与既有 root **隔离可行**（B7 验收 ⑧）。**但两个前置风险必须处理**：① 若现网存在 server 级**正则 location**（如 `~* \.(js|css)$`），正则优先于普通前缀 → 会用**既有 root** 捕获 `/web/assets/*`，破坏隔离；对策：新 location 用 **`^~`** 修饰符（仍属"只增"）。② `alias` 尾斜杠错配会引入经典 alias 穿越；对策：**用 `root` + 独立目录**（或严格尾斜杠一致的 `alias`）。`..`/`%2e%2e` 由 Nginx URI 规范化处理（`merge_slashes on` 默认），**须以 curl 实测为准**（无服务器访问，本报告不出运行期结论） | ADR [§5.4 L322-324](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L322-L324)、B7 验收 ⑧ [L258](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L258)；先例 [docker-demo/nginx.conf:44-63](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/docker-demo/nginx.conf#L43-L63)（正则顺序纪律） | 中 | 否（条件：V2/M2/M3 闭环） |
| 4 | **传输与安全响应头** | **分类结论**：`index.html no-store`、缺失资源 **404 不回退 HTML**、`X-Content-Type-Options`、`Referrer-Policy` 属**新增 location 内局部配置 → 本批必做**（"只增"合规，M3/M5）。**HSTS 与 HTTP→HTTPS 跳转属 server 级（改既有行为）→ 本批不做，登记遗留**（与「只增不改」冲突）。HTTPS 强制：若现网 443 server 块已全局强制（证书已挂载），新 location 加在 443 块内即**自动继承，无需改既有配置 → 满足 B7 验收 ⑤**；否则 ⑤ 本批不可达。**混合内容：无**（产物资源为同源绝对路径 `/web|/staff|/boss/assets/...`，随页面 scheme）。**模板缺口**：仓库模板 [nginx.conf.example](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/deploy/nginx.conf.example#L44-L78) **无 `.map` 拒绝、且 `location /` 用 `try_files ... /index.html`（会把缺失资源回退成 HTML）** → 该模板**不可直接照搬**到新 location | [ADR §3.7 L258](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L258) 验收 ⑤、[§3.3 L185](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L184-L185) 缓存纪律；引依据 [web/dist/index.html:13-14](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/hrm-clients/apps/web/dist/index.html#L13-L14) | 中 | 否（M3/M5 必做；HSTS 遗留） |
| 5 | **API 基址暴露** | **不构成新增风险，无需动作**：`/hrm-api/v1` 为**同源相对路径**（非绝对 URL、无跨域放大），三端 `VITE_API_BASE` 明示值（[web/staff/boss `.env.production`:3-5]）；该前缀在既有**公开** `hrm-demo/.env.production` 已存在，且前端基址非凭据；后端鉴权仍在服务端（JWT）。产物侧 `clientType`（WEB/STAFF/BOSS）可见亦非秘密 | 产物命中 `/hrm-api` = 各端 1 文件（`baseURL:"/hrm-api/v1"`）；[ADR D8 L370](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L366-L370)（已闭环） | 低 | 否 |
| 6 | **零中断前提成立性** | **前提成立但依赖流程控制，存在 2 项未登记风险**：① 风险已登记：`R新-1`「演示站产物被覆盖 → 旧入口失效」（[ADR L351](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L347-L352)），缓解=迁移期冻结 `hrm-demo` 发布 + 旧产物只读快照 + 冻结镜像 tag。② **未登记风险 A**：现网 `location /` 在演示部署中被改为 **proxy 到演示容器**（[demo-docker-deploy.md:89-111]），故旧入口可用性同时依赖**演示容器存活**；容器重建/拉新产物同样会破坏前提，冻结范围须含"容器镜像 tag + 容器不重建"。③ **未登记风险 B**：仓库 `deploy.sh` 的前端同步默认目标是既有站点（[deploy.md:49](/d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/deploy.md)），若迁移期误对其跑发布流程，可能覆盖旧产物 → 须在变更窗口内显式禁用/圈定范围。④ 仍存**待证实假设 H5**（现网 `location /` 由演示站提供 vs [deploy.md:26](/d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/deploy.md) 记 `/www/wwwroot/hrm-admin`）——两文档口径矛盾，须运维定论 | [ADR H5 L438](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L438)、[R新-1 L351](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L349-L351)；[demo-docker-deploy.md:19,41-42,56-57,89-111,161-173] | 中 | 否（M4/缓解项 + V3） |
| 7 | **回滚面** | **可逆、可留痕（结论：可接受）**：动作 = 删新增 location + 还原 `nginx.conf.<时间戳>` + `nginx -t` + reload（[ADR §6 L342](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L334-L343)）。仅新入口消失，既有 location 未受影响。**必要条件（须写入变更单）**：改前备份到固定备份目录 + `nginx -t` 通过方可 reload + 因现网为**容器**，reload 须在容器内执行（`docker exec ... nginx -s reload`）；回滚后须登记 `update-log.md` / `SESSION-STATE.md`。禁用 `reset --hard`/`clean -f`（D 档） | [ADR §6 L342](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L342)、[§5.4 L326](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L326)；现网容器 reload 先例 [demo-docker-deploy.md:75-76] | 低 | 否 |
| 8 | **运维待核实闭环项** | **未闭环（6 项），其中 V1–V4 阻塞实施**（细节见 §6）：载体路径/管理脚本、既有 location 全量（含正则）、`location /` 现状与站点根落点（容器内 vs 宿主）、443/80 块现状与证书/HTTP 强制、现有安全头与 `.map` 现状、Nginx 版本 | §6；载体真源**不在仓库**（[ADR §5.4 L323](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L318-L326)、[§10.2 U-5 L448](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L448)） | 高（阻塞） | **是**（V1–V6） |
| 9 | **SP7 形式复验（真实域名入库）** | **通过**：ADR v2 全文 `kongzhen|\.com|https?://|IPv4` 命中 = **0**（独立复现，非采信自述）；与既有 [security-structure-migration-review.md](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/security-structure-migration-review.md) 结论一致。**本项不阻塞** | Grep `adr-structure-migration.md` 上述模式 → 0 | 低 | 否 |

---

## 4. 必须闭环项清单（含可判定验收标准）

> 均为"只增不改"可落地项；执行归运维，返工归对应实现角色；**验收由运维实测并回填证据**。

| ID | 必改/必做项 | 落地要求 | 验收标准（可判定） |
| - | --- | --- | --- |
| **M1** | **`.map` 拒绝（高风险，§10.4 红线）** | 三条新增 location **各自内部**加 `.map → 404`（局部正则，**仅作用于新前缀**，不在 server 级加通用正则以免改既有行为）；须置于该前缀内通用扩展名规则**之前** | 对三端各取一真实 `.js` 名替换扩展名探测：`curl -s -o /dev/null -w '%{http_code}\n' https://<占位>/web/assets/index-8uc64syh.js.map` → **403/404**；staff/boss 同法；三者**均非 200** |
| **M2** | **独立站点根 + `root`（隔离与穿越）** | 三条 location 用**独立目录**作 `root`（不与既有 `location /` 的 root/alias 共用）；新 location 用 **`^~`** 前缀修饰符防既有正则 location 抢占；优先 `root`，如用 `alias` 须尾斜杠口径一致 | ① `curl -s https://<占位>/web/assets/index-8uc64syh.js | head -c 20` 返回 **JS 内容**（非 HTML、非 404）；② `curl -s -o /dev/null -w '%{http_code}\n' https://<占位>/web/不存在.png` → **404**；③ 三端 `index.html` 内资源路径前缀分别为 `/web/`、`/staff/`、`/boss/` 且能取到 |
| **M3** | **缺失资源 404 不回退 HTML + 缓存纪律** | 新 location **禁止**照搬模板的 `try_files $uri $uri/ /index.html` 用于带扩展名资源；带扩展名缺失必须 404；`index.html` 加 `Cache-Control: no-store`；带 hash 的 `assets/*` 长缓存 | ① `curl -s -o /dev/null -w '%{http_code}\n' https://<占位>/staff/assets/nonexist-abc123.js` → **404**（**不得 200**）；② `curl -sI https://<占位>/staff/ | grep -i cache-control` → 含 `no-store`；③ 触发刷新深链 `https://<占位>/staff/#/home` 手测不白屏 |
| **M4** | **零中断冻结与快照（流程控制）** | 迁移期：①**冻结 `hrm-demo` 发布**（仅 P0 修复且须回归旧入口）；②对旧产物做**只读快照**（`dist` + 冻结镜像 tag）；③变更窗口内**禁用/圈定** `deploy.sh` 前端同步目标，避免覆盖旧产物；④现网 `location /` 保持 proxy 到演示容器**不动**、且**容器不重建** | 变更前后 `curl -s -o /dev/null -w '%{http_code}\n' https://<占位>/mobile.html` 与 `https://<占位>/pc.html` 均 **200**（旧入口并存可达，B7 验收 ⑦）；快照目录与镜像 tag 有登记记录 |
| **M5** | **安全响应头（局部，只增）** | 三条新 location 内 `add_header X-Content-Type-Options nosniff always;`、`add_header Referrer-Policy strict-origin-when-cross-origin always;` | `curl -sI https://<占位>/boss/` → 响应头含上述两项 |
| **M6** | **穿越/编码绕过实测** | 无配置项，纯验证 | ① `curl -s -o /dev/null -w '%{http_code}\n' --path-as-is 'https://<占位>/staff/../index.html'` → **400/403/404**（不得 200 越权重定向内容）；② `curl -s -o /dev/null -w '%{http_code}\n' --path-as-is 'https://<占位>/staff/%2e%2e/'` → **400/403/404**；③ `curl -s -o /dev/null -w '%{http_code}\n' --path-as-is 'https://<占位>/staff/..%2f..%2fetc/passwd'` → 非 200 |
| **M7** | **HTTPS 强制（继承式，不改既有）** | 新 location 加入**443 server 块**；**不得**为此新增 server 级 HSTS / 80→443 跳转（属改既有行为 → 本批不做，登记遗留） | `curl -sI https://<占位>/web/` → **200**；若现网已有 80→443 强制，则 `curl -sI http://<占位>/web/` → 301/308（**此条依赖 V5 定论**；若现网无强制，本项**标未闭环**并登记遗留，不阻塞 B7 但计残余风险） |

---

## 5. C 档授权建议

> 本角色**只出技术结论与是否可放行，不代授权**（§8；评估方与决策方分离）。

1. **是否具备进入主智能体三步授权的条件**：**形式与静态维度已具备**——评估已出、9 项结论明确、高风险项与可逆性/回滚面清楚、必改项可判定。
2. **但不得在闭环前授权执行**：`M1–M7` 未纳入变更单、`V1–V6` 未闭环前，**无足够依据判定"可放行"**；主智能体**不得凭经验放行**（A16）。
3. **三步授权表单须含（缺一不可，§10.3）**：
   - ① **影响范围与是否涉生产数据**：仅现网 `courier-nginx` 新增 3 条 location；**不涉生产数据**（无 DB/无数据面改动）；新站点根为静态文件目录；
   - ② **是否可逆**：可逆（删新 location + 还原 `nginx.conf.<时间戳>`）；
   - ③ **回滚步骤与验证方法**：容器内 `cp 备份 → nginx -t → nginx -s reload` → 复测旧入口 200 + 新入口消失；登记 `update-log.md`。
4. **高风险项措辞（须原文写入授权单）**：
   > **`M1`（`.map` 公开）命中项目规则 §10.4「把生产 `*.map` 部署为可公开访问」D 档红线。若本批无法落地并实测 `.map` 拒绝，则不得判定上线成功；此情形下不得由主智能体直接放行，须升级用户裁定。**
5. **不得放行的情形（阻断）**：`V1–V4` 任一未闭环（尤其 V2 现网正则 location 未清、V4 站点根落点未定）→ 无法写出正确 location，**不得实施**；`M1` 未落地/未验证 → 高风险升级用户裁定。

---

## 6. 待运维核实项清单

> 「现网实际配置」一律不得假定；以下为**阻塞实施的显式假设清除项**。核实结果须登记 `update-log.md` 并回填本报告（由主智能体执行）。

| ID | 待核实项 | 核实方法 | 为何阻塞 |
| - | --- | - | - |
| **V1** | 现网 Nginx **配置载体路径 / 管理脚本** | 读现网 `courier-nginx` 容器内挂载的 `nginx.conf` 对应**宿主路径**与生效方式（宝塔 / compose 挂载 / include 目录）；登记备份路径 | 决定"改哪个文件、备份到哪、如何回滚"；未定则变更不可控 |
| **V2** | 既有 **location 全量清单**（**尤重是否存在 server 级/站点级正则 location** 如 `~* \.(js|css|map)$`） | `nginx -T` 或读配置文件，逐条列出 | 若存在正则 location，会**抢占**新前缀资源 → 必须改用 `^~`；未清则隔离不成立 |
| **V3** | 现网 `location /` 与 `location = /` **现状**（是否仍 proxy 到演示容器） | 读配置 + `curl` 现状对比 | 决定零中断前提是否成立（H5）；并解决与 [deploy.md:26](/d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/deploy.md) 的口径矛盾 |
| **V4** | **新站点根落点**：容器内路径 vs 宿主 bind mount；3 个目录的具体值、权限/属主 | 读容器挂载与目录 `ls -ld` | 直接决定 `root/alias` 写法与 `deploy` 同步目标；**现网为容器**，`/www/wwwroot/...`（ADR 示例）**未必适用** |
| **V5** | **443 与 80 server 块现状**：证书路径、`server_name`、**是否有 HTTP→HTTPS 强制**、新 location 是否需同时加入 80 块 | 读配置 | 决定 B7 验收 ⑤ 本批可否达成；决定 HSTS/跳转是否只能登记遗留 |
| **V6** | 现有**安全响应头**与 **`.map` 处理现状**（server 级 `add_header` 是否有；旧路径 `.map` 现是否已被拦） | 读配置 + `curl -sI` 现状 | 决定 M5 是否会与既有 `add_header` **重复/覆盖**（Nginx `add_header` 在子级会清空父级同名头，须按现网口径设计）；决定 M1 是"新增"还是"需与既有并存" |

---

## 7. 未覆盖项与已知不足

1. **产物内残留演示文案（低，非泄露，建议但非阻塞）**：boss 首页 tooltip「当前演示账号固定为全域口径…」、staff/boss `演示身份`（prod 桩抛错串）、`城东驿站`（表单占位示例与说明）。**均不含凭据/数据，纯 UX 文案**；生产站点出现"演示"字样属品牌/文案问题，建议交前端工程师按需去 demo 化（**不在本安全闸门的阻塞项内**）。
2. **无运行期证据**：未做 curl/渗透/扫描/现网核查；第 3 项（穿越）与第 4 项（HTTPS）的最终判定**必须由运维实测回填**。
3. **未审后端鉴权/越权（SP5）**：端准入与多端登录态并存归 `security-auth-review.md` / `security-client-admission-review.md`，本报告不含。
4. **未审共享包供应链（SP6）** 与 **安卓壳 B6**（不在 B7 范围）。
5. **未读 `.env.demo` 内容**（纪律：不读凭据文件）；未验证 `hrm-demo` 旧产物自身是否含演示口令（本批不发布该产物；若后续 B8 处置需评估，另立任务）。
6. **未逐文件枚举 `.map` 内的源码明文敏感项**：已证"全部含 `sourcesContent`"（全量源码可还原），但未逐条判定其中是否含硬编码敏感值（当前证据显示无凭据）。
7. **未验证构建可重现性**：未复跑 `build:prod` 核对产物与源工程一致（本机可运行 Node，但复跑属实现角色门禁范围）。

---

## 附录 A · 检索命令与命中数（供独立复核）

> 说明：`dist/**` 属构建产物、被 `.gitignore` 忽略，**内置 Grep（ripgrep 默认遵循 ignore）不命中**，故产物检索用 PowerShell `Get-ChildItem` + `Select-String`（`Select-String -SimpleMatch` **默认大小写不敏感**，报告已标注误报）。

| # | 检索对象 | 命令要点 | 命中数 |
| - | --- | --- | --- |
| A1 | 产物文件计数 | `Get-ChildItem -Recurse -Filter *.map/.js` | web `map=104 / js=104`；staff `map=43 / js=43`；boss `map=51 / js=51` |
| A2 | `.map` 含 `sourcesContent` | 逐 map `Contains('sourcesContent')` | web `104/104`；staff `43/43`；boss `51/51`（**全含**） |
| A3 | `sourceMappingURL` | `Select-String 'sourceMappingURL'` | 三端 **0** |
| A4 | 演示口令/账号/手机号 | `demo1234` / `st001_admin` / `st001_staff` / `16626369983` | **0 / 0 / 0 / 0**（`st001` 命中 1 文件，实为占位 `ST001-Express`，大小写误报） |
| A5 | Mock 代码/标记 | `installMock` / `@kdyzgl/mock` / `MockAdapter` / `createMock` / `mock/db` | 三端 **0** |
| A6 | Mock/种子文件名 | 文件名匹配 `install|db-|mock|seed|faker` | 三端 **0** |
| A7 | 真实域名/IP | `kongzhen` / IPv4 字面量 | `kongzhen` **0**；staff/boss IPv4 **0**；web IPv4 6 处 = Element Plus IPv6 校验正则内**注释示例** `1.2.3.4` |
| A8 | 协议头 | `http://` / `https://` | `http://` 全为 W3C 命名空间 + axios 垫片 `window.location.href||"http://localhost"`；**无混合内容** |
| A9 | API 基址 | `/hrm-api` | 各端 **1 文件**（`baseURL:"/hrm-api/v1"`） |
| A10 | 演示文案残留 | `演示账号` / `演示身份` / `城东驿站` | boss `1/1/2`；staff `0/1/0`；web `0/0/2`（均**非凭据**） |
| A11 | ADR SP7 复验 | `kongzhen|\.com|https?://|IPv4` on `adr-structure-migration.md` | **0** |
| A12 | 模板缺口 | 读 `deploy/nginx.conf.example` | **无 `.map` 拒绝**；`location /` 用 `try_files ... /index.html`（**不可照搬**） |

---

## 附录 B · 事实性纠正（上游材料与实测不符）

| # | 上游材料（文件:行号） | 其陈述 | 实测 | 纠正 |
| - | --- | --- | --- | --- |
| B-1 | [deploy.md:26,49](/d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/deploy.md) | `/` → `/www/wwwroot/hrm-admin`（宝塔站点根） | 演示部署记录（[demo-docker-deploy.md:89-111]）显示现网 `location = /` 与 `location /` 已改为 **proxy 到演示容器 `hrm-demo-static`** | 两文档口径**矛盾**；ADR H5 采信后者。**须运维定论**（V3） |
| B-2 | [ADR §5.4 L324](file:///d:/Users/16626/Desktop/kdyzgl-base/hrm-dev/docs/adr-structure-migration.md#L324) | 新站点根示例 `/www/wwwroot/hrm-web|hrm-staff|hrm-boss` | 现网 Nginx 为 **容器 `courier-nginx`**（[demo-docker-deploy.md:41-42,56-57]） | 宿主路径示例**未必适用**；站点根落点须按容器内/挂载重新核实（V4）。ADR 已标"如"，属需澄清非硬错 |
| B-3 | 主智能体实测结论 | 「三者 `dist/assets` 内 `install|db-|mock` 命中 = 0」 | **独立复核成立**（文件名 + 内容双查均 0） | 结论正确；**补充**：三端全部 `.map`（104/43/51）均含 `sourcesContent`（ADR 未量化此项，为 M1 依据） |
| B-4 | [项目规则1.md:47](file:///d:/Users/16626/Desktop/kdyzgl-base/.trae/rules/项目规则1.md#L47) | `hrm-demo` 三入口 `index/pc/mobile` | 本次结构变更新增 `hrm-clients/apps/{web,staff-h5,boss-h5}` | §12 与工程矩阵**尚未同步**（ADR §11 已列为待回填），**非阻塞**，提示主智能体同步口径 |

---

## 附录 C · 检查点（M02）

- **2026-09-25**：产出 `hrm-dev/docs/security-release-switch-review.md`（**新增**）。对 ADR B7 上线前做 P0.5/L7 静态安全评估：结论**有条件放行**，必改项 M1–M7（其中 M1 `.map` 拒绝为高风险、命中 §10.4 红线）、待运维核实项 V1–V6。
- **影响文件**：仅本文件（1 个，新建）。**未改动任何源码、配置、`.env*`、`deploy/**`、ADR 与其它 `docs/**`**；未调用 MCP；未执行 git 操作；**未触达生产**。
- **证据来源**：`hrm-clients/apps/{web,staff-h5,boss-h5}/dist/**`（产物静态检索）、三端 `vite.config.js` / `.env.production`、`deploy/nginx.conf.example`、`deploy/docker-demo/nginx.conf`、`adr-structure-migration.md`、`demo-docker-deploy.md`、`deploy.md`、`packages/mock/src/db.js`、项目规则 / 调度规则。
- **未运行**：任何构建 / lint / test / 扫描 / 渗透 / 现网 curl（本机无运行环境、无服务器访问）。
- **回滚**：删除本文件即可（纯新增文档）。
