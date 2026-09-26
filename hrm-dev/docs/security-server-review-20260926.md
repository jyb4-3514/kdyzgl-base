# 生产服务器安全实测评审报告（2026-09-26）

> 评估方：主智能体（TRAE solo）｜方式：SSH/宝塔 MCP **只读**实测（未做任何变更）
> 配套报告：[security-full-review-20260926.md](security-full-review-20260926.md)（仓库侧静态审计，网络安全工程师）、[tech-review-full-20260926.md](tech-review-full-20260926.md)（技术质量评审，架构师）
> 口径声明：本报告为**安全技术输入**，不构成放行决定；涉 C 档/红线的整改须由主智能体按项目规则 §10.3 三步授权后执行，高风险项须升级用户裁定。**服务器公网 IP 按 §7.2 / §10.4 不落文档**（以 `<prod-host>` 代指）。

---

## 1. 结论摘要（先行）

| 项 | 结论 |
| --- | --- |
| 服务器整体 | 主机为 Ubuntu 22.04，运行 23 天；UFW 默认 deny、Fail2Ban 生效、TLS 与证书链合格 —— **基础加固已做，但暴露面存在 4 处「本不该对公网开放」的端口** |
| 最严重 3 项 | ① 后端以 **root** 运行（D 档红线）② 后端 **8081 端口对公网开放**，可绕过 Nginx 全部防护（含 auto-dispatch 403 与 `.map` 404）③ 该 root 进程以 **`profile=dev` 连测试库 `kdyzgl_test`**，与容器 `profile=prod`（`kdyzgl`）双后端双库并存 |
| 发现数量 | 18 项：**严重 3 / 高 5 / 中 6 / 低 4**（均为实测，非推测；无法证实的已单列 §7） |
| 已合规项 | UFW 默认拒绝、Fail2Ban（sshd/ftpd，bantime 86400）、`.map` 404、`auto-dispatch` 403（Nginx 层）、80→301、TLS1.2/1.3 + ECDHE、证书 5 层链（到期 2026-12-21）、Redis 仅回环、MySQL 3307 仅容器网段、容器非 privileged、`courier-app` 以 `courier` 非 root 用户运行、宝塔面板 HTTPS + 隐藏路径 |
| 优先级建议 | **P0（立即）**：关闭 8081 公网放行 / 下线冗余后端实例、后端去 root、8765 与 8888 加白名单、SSH 收口；**P1**：HSTS 与安全头、权限收敛、冗余防火墙规则清理；**P2**：apt 升级、`/apk/` 目录列表、临时文件权限 |

---

## 2. 评审范围与方法

- **覆盖**：主机身份与内核、监听端口、UFW/iptables、SSH 配置与爆破态势、SSH 用户与 sudoers、Docker 容器与镜像、Java 进程属主与启动方式、Nginx 实际生效配置（`nginx -T`）、TLS 证书链与有效期、站点可达性（80/443/`/api/v1` 探针）、数据库与 Redis 监听绑定、备份目录、磁盘/内存、apt 待升级数、Fail2Ban jail。
- **方法**：全部只读命令（`ss`/`ufw status`/`systemctl`/`docker inspect`/`nginx -T`/`openssl s_client`/`curl` 探针/`apt-get -s`）+ 从外部网络对公网端口的 TCP 连通性实测。
- **未覆盖（收敛说明）**：未做主动渗透/漏洞利用验证；未读取任何明文凭据（凭据归主智能体托管，不入报告与对话）；MySQL 用户与授权表未查（需凭据）；应用层业务越权验证属仓库侧报告范围（本机无 JDK/MySQL，动态验证未做）。

---

## 3. 发现清单

### 严重

**SRV-01 · 后端应用以 root 运行（D 档红线）**
- 证据：`ps` 显示 `root PID 986928 java -Xms256m -Xmx768m -jar hrm-server.jar --spring.profiles.active=dev --spring.config.additional-location=file:/data/www/hrm-config/`，工作目录 `/data/www/hrm-server`，**无 systemd 托管**（`systemctl is-enabled hrm-server` → 单元不存在），即手动/nohup 启动，父进程为 1。
- 影响：任何一个应用层 RCE / 依赖漏洞 / 反序列化问题都直接等价于 **root 取得**；与项目规则 §10.4「不得以 root 运行应用进程」直接冲突。
- 验证：`ps -eo user,pid,args | grep java`；`ls /etc/systemd/system | grep -i hrm`。
- 修复建议：以专用低权用户（服务器已有 `springboot` uid=1001）运行；改用 systemd 单元托管（仓库已有 `hrm-dev/deploy/hrm-server.service` 模板）；限制 jar/配置目录属主。
- 验收标准：`ps -o user= -p <pid>` 非 root；`systemctl is-enabled hrm-server` = enabled；重启后自动拉起。

**SRV-02 · 后端 8081 对公网开放，可绕过 Nginx 全部防护**
- 证据：UFW 规则 `[66] 8081/tcp ALLOW IN Anywhere`（另有 `[69] 8081/tcp ALLOW IN 172.19.0.0/16 # hrm-server from courier-net`）；`ss` 显示 `*:8081` 监听（IPv6 全接口）；从**外部网络**实测 `8081 = OPEN`。
- 影响：Nginx 层已配置 `location = /hrm-api/v1/work-orders/auto-dispatch { return 403; }` 与 `location ~* \.map$ { return 404; }`，但**直连 8081 可完整绕过**这两道防线；实测 8081 上 `/` → 401、`/api/v1/auth/login` → 200（登录接口公网可达，Nginx 侧防护与限流均失效）。
- 与仓库侧交叉：`PublicEndpoints.java` 将 `/api/v1/work-orders/auto-dispatch` 列为**公开白名单**（注释自称"预留，验签落地前不得公网暴露"），故 **「代码白名单 + 8081 直达」构成可达路径**；本报告**未做写入型实测**（会污染数据），故标注为「路径成立、未实测利用」。
- 修复建议：① 直接下线 8081 旧实例（业务已由容器 `courier-app` 8080 承载）或改绑 `127.0.0.1`；② 删除 UFW `[66]/[79]` 公网规则，仅保留容器网段规则 `[69]`；③ 在验签落地前，后端侧对 auto-dispatch 增加**源码级**拒绝（不能只靠 Nginx）。
- 验收标准：外部对 8081 的 TCP 探测为 filtered/closed；`curl -X POST https://<prod-host>/api/v1/work-orders/auto-dispatch` 与 `http://<内网>:8081/...` 均非 2xx。

**SRV-03 · 运行实例为 `profile=dev`，连测试库 `kdyzgl_test`；与容器 `prod` 双后端双库并存**
- 证据：`/data/www/hrm-config/application-dev.yml` → `kdyzgl_test`；`application-prod.yml` → `kdyzgl`；8081 进程启动参数 `--spring.profiles.active=dev`；容器 `courier-app` 环境 `SPRING_PROFILES_ACTIVE=prod`；Nginx 分流：`location /hrm-api/ → http://172.19.0.1:8081/api/`（宿主 8081，dev/test），`location /api/ → http://app:8080/api/`（容器，prod）。
- 前端实测基线：三端 `src/utils/http.js` 的 `baseURL` 为 `/api/v1` → 即线上流量走 **容器 prod → `kdyzgl`**（生产库）✅。
- 影响：同一主机上并存两套后端与两个库，`/hrm-api/` 前缀通向**测试库**且实例运行在 root 下；一旦被误用或误配（改前缀、改上游）将出现「生产站点写测试库」的数据事故；也与项目规则「服务器代码与本地一致、单一托管方式」相悖。
- 修复建议：确认 `/hrm-api/` 是否仍有消费者；无则下线 8081 实例并移除该 location；有则统一到容器 prod 实例。
- 验收标准：`ps` 中仅保留 1 个后端 java 进程；Nginx 仅 1 个 `/api/` 上游指向容器；进程 profile = prod。

### 高

**SRV-04 · SSH 允许 root 密码登录且公网 22 开放，正被持续爆破**
- 证据：`sshd -T` → `permitrootlogin yes` / `passwordauthentication yes` / `maxauthtries 6` / `logingracetime 120`；UFW `[59] 22/tcp ALLOW IN Anywhere`；`grep -c 'Failed password' /var/log/auth.log` = **5524**；`lastb` 当日（09-26 07:20–07:25）仍有同一来源 IP 连续尝试；Fail2Ban `[sshd] enabled, maxretry=5, bantime=86400`，UFW 中已封 **56** 个 IP。
- 影响：公网爆破持续命中；root + 密码 = 单点失守即全失守；Fail2Ban 仅按 IP 封禁，分布式爆破可绕过。
- 修复建议：`PermitRootLogin prohibit-password`（或 `no`）、`PasswordAuthentication no`（仅密钥）、改非标端口并配合 Fail2Ban；运维账号纳入 `%sudo`（当前 sudoers 已有 `%admin/%sudo`）。
- 验收标准：`sshd -T` 两项均为禁用态；密钥登录可用；UFW 22 规则收窄到管理来源。

**SRV-05 · 宝塔面板 8888 对公网开放且无 IP 白名单**
- 证据：UFW `[62]/[75] 8888/tcp ALLOW IN Anywhere`；外部实测 `8888 = OPEN`；`/www/server/panel/data/limitip.conf` 为空（**未设授权 IP 限制**）；正面项：`ssl.pl=True`（面板走 HTTPS）、`admin_path.pl=/f8c15c20`（隐藏路径已开）。
- 影响：面板是最高权限入口，暴露面等于主机 root 面；安全性仅剩「口令强度 + 隐藏路径」。
- 修复建议：面板设置中启用「授权 IP」白名单（绑定管理出口 IP）；或 UFW 侧限制来源；确保面板口令为强口令并开启二次验证（若可用）。
- 验收标准：外部对 8888 探测 filtered；`limitip.conf` 非空。

**SRV-06 · 宝塔 Agent MCP 服务 8765 对公网开放**
- 证据：`ss` → `0.0.0.0:8765`（`/www/server/panel/pyenv/bin/python3 /www/server/panel/plugin/bt_agent_mcp/run_server.py`，**root** 运行）；UFW `[64]/[77] ALLOW IN Anywhere`；外部实测 `8765 = OPEN`；插件目录含 `AUTH_KEY`/`AUTH_SALT`/`allow_ip` 等配置键（未读取值）。
- 影响：该服务具备服务器管理/命令执行能力，公网暴露面属高风险；根路径 TCP 探测返回空回复（非 HTTP 协议），但端口可达即等于暴露。
- 修复建议：改绑 `127.0.0.1` 或启用 `allow_ip` 白名单；UFW 移除公网放行；评估是否随用随开。
- 验收标准：外部探测 filtered；配置中仅本机/白名单可用。

**SRV-07 · 生产源码目录权限 777，任意本机用户可改「线上代码」**
- 证据：`ls -l /data/www/hrm-server` → 目录 `drwxrwxrwx`，`src` → `drwxrwxrwx`，`pom.xml` → `-rw-rw-rw-`。
- 影响：与项目规则「不在生产服务器编辑业务源码、服务器代码只来自 `git pull`」直接冲突；任何本地账号（或入侵后的低权账号）均可篡改随后被加载的代码/配置，破坏「本地—服务器一致性」。
- 修复建议：`chown -R root:root` + 目录 755、文件 644；只读挂载或改为仅解包发布物（jar），不保留可写源码树。
- 验收标准：目录 755、文件 644、属主非 others 可写。

**SRV-08 · HTTPS 无 HSTS，且缺 X-Frame-Options / CSP**
- 证据：`curl -I https://<prod-host>/` 响应头中 `Strict-Transport-Security`、`X-Frame-Options`、`Content-Security-Policy` 命中数 = **0**；仅见 `X-Content-Type-Options: nosniff` 与 `Referrer-Policy`（Nginx `add_header` 行 100/101 等）。
- 影响：明文降级与点击劫持/注入面；与仓库侧 SEC-FULL-03（Nginx 样例缺安全头）一致。
- 修复建议：追加 `Strict-Transport-Security: max-age=31536000; includeSubDomains`（先短 max-age 灰度）、`X-Frame-Options: SAMEORIGIN`、按站点补 CSP。
- 验收标准：三头在 443 响应中可见。

### 中

| 编号 | 发现 | 证据 | 建议 |
| --- | --- | --- | --- |
| SRV-09 | 防火墙存在冗余/历史放行：`20/tcp`、`21/tcp`、`888/tcp`、`39000:40000/tcp` 均 ALLOW Anywhere，但实测 21/888 为 filtered（服务未监听）；FTP 相关规则无实际用途 | `ufw status numbered` `[57][58][61][63]`；外部探测 21/888 = filtered | 删除无用规则，遵循最小放行 |
| SRV-10 | Redis **无口令**（`redis-cli PING` → `PONG` 直接成功）；正面项：仅 `127.0.0.1:6379`（docker-proxy 绑定回环） | `docker ps` 端口 `127.0.0.1:6379->6379`；`redis-cli PING` | 视业务评估是否补 `requirepass`；至少确保无新容器接入同网络 |
| SRV-11 | `/data/hrm-tmp/` 下多个文件权限 666（世界可写），含 `b7-snippet*.conf`、`*.tar.gz` 发布产物；正面项：评审账号明文文件 `review-accounts-20260925121508.txt` 为 600 | `ls -l /data/hrm-tmp/` | 收敛为 600/644；发布产物与临时文件定期清理 |
| SRV-12 | 110 个可升级软件包（`apt list --upgradable` 中 security 分类 1）；主机运行 23 天未重启 | `apt-get -s upgrade`；`uptime` | 先备份后按窗口升级，重启前确认容器自启（`restart=always/unless-stopped`） |
| SRV-13 | `/apk/` 启用 `autoindex on`（目录列表），当前目录为空 | Nginx `location /apk/ { alias /data/www/apk/; autoindex on; }`；`curl https://<prod-host>/apk/` 返回 Index of | 若无需浏览则关闭；保留则明确为公开分发目录 |
| SRV-14 | MySQL 3307 绑定 `172.17.0.1`/`172.19.0.1`/`127.0.0.1`：仅容器网段可达（合规），但绑定**默认 bridge 网关** 172.17.0.1，默认 bridge 上任意容器均可连 3307 | `ss -tulnp`；UFW `[67][68]` | 收敛到专用网络（仅 `172.19.0.0/16`），移除 172.17 绑定 |

### 低

| 编号 | 发现 | 证据 | 建议 |
| --- | --- | --- | --- |
| SRV-15 | 生产源码压缩包常驻服务器：`/data/www/hrm-server-src.tar.gz`（644，555KB） | `ls -l /data/www/` | 移出或删除，源码只保留在仓库 |
| SRV-16 | 未见数据库定时备份任务（宝塔 cron 仅 2 条业务任务 + acme 续期）；备份目录 428M（含 193MB hids 日志包） | `crontab -l`；`ls /data/backup` | 补 MySQL 定时备份 + 异地留存 + 恢复演练 |
| SRV-17 | 证书链与续期合格：5 层链、`notAfter=Dec 21 2026`、acme cron `14 1,7,13,19 * * *` | `openssl s_client -showcerts`；`crontab -l` | 无需变更 |
| SRV-18 | 磁盘余量健康：`/` 36%、`/data` 7%；内存 3.8G / 空闲 1.6G | `df -h`；`free -m` | 持续观察（含两个 java 进程常驻内存） |

---

## 4. 与仓库侧审计的交叉结论

| 交叉点 | 仓库侧（静态） | 服务器侧（实测） | 合并判定 |
| --- | --- | --- | --- |
| auto-dispatch 公开白名单（SEC-FULL-01，严重） | 代码白名单 + 无验签 | Nginx **已 403**，但 8081 公网直达可绕过 | **路径成立，未实测利用**（写入型探针不执行）；须后端源码级拦截或下线 8081 |
| `.map` 泄露（SEC-FULL-04，高） | 需 Nginx 拒绝 | Nginx `location ~* \.map$ → 404` 实测生效；线上 `.map` = 0 | Nginx 侧已闭环；建议保留双保险并纳入发布检查 |
| 以 root 运行（SEC-FULL-05，高） | `deploy.sh` nohup 回退 | **线上确为 root 进程**，且无 systemd 托管 | 确认为**现存问题**（非理论风险） |
| 默认监听 0.0.0.0（SEC-FULL-06，高） | 配置层观察 | `*:8081` + UFW ANYWHERE + 外部 OPEN | 确认为**现存问题**；容器侧 8080 已绑回环 ✅ |
| 缺 HSTS/安全头（SEC-FULL-03，高） | Nginx 样例缺 | 线上实测无 HSTS/XFO/CSP | 确认为**现存问题** |
| 演示/评审账号明文 | 仓库内演示口令风险 | 服务器评审账号文件权限 600 ✅ | 口令未入库 ✅；建议评审结束后删除该文件 |

---

## 5. 整改建议与授权分档

| 优先级 | 动作 | 档位 | 是否可逆 | 回滚与验证 |
| --- | --- | --- | --- | --- |
| P0 | 删除 UFW 8081 公网放行（保留容器网段）／或将 8081 改绑 `127.0.0.1` | **C** | 可逆 | `ufw delete <规则号>` 前导出 `ufw status numbered`；验证：外部探测 filtered、站点功能正常 |
| P0 | 下线冗余 8081 后端实例（确认 `/hrm-api/` 无消费者后） | **C** | 可逆 | 保留 jar + 启动命令记录，随时可重启；验证：`/api/` 业务全链路正常 |
| P0 | 后端改以 `springboot` 用户 + systemd 托管 | **C** | 可逆 | 保留旧启动命令；验证：进程属主、`systemctl is-enabled`、重启自愈 |
| P0 | 8765 收口（本机/白名单）＋ 8888 面板授权 IP 白名单 ＋ SSH 禁 root 密码登录 | **C** | 可逆（须先验证密钥可用，避免自锁） | 改前保留可用会话；验证：外部探测、密钥登录成功 |
| P0 | `chmod/chown` 收敛 `/data/www/hrm-server`（755/644） | **C** | 可逆 | 记录原权限；验证：发布流程仍可写、服务正常 |
| P1 | 增加 HSTS + X-Frame-Options + CSP | **C**（改 Nginx 配置） | 可逆 | 备份 `nginx.conf`；灰度 max-age；验证响应头 |
| P1 | 清理 21/888/被动端口冗余规则；MySQL 移除 172.17 绑定 | **C** | 可逆 | 规则快照；验证端口探测 |
| P1 | `/data/hrm-tmp` 权限收敛与临时文件清理 | **B** | 可逆 | 移除前确认无在用引用 |
| P2 | apt 升级（含 1 个 security）；`/apk/` autoindex 决策；源码 tar 移出；补 DB 定时备份 | **B/C** | 可逆 | 升级前快照；重启窗口内验证容器自启 |

> **授权纪律：** 上表 P0/P1 均属 C 档，须由主智能体按 §10.3 输出「影响范围 / 是否可逆 / 回滚步骤与验证方法」三步表单后执行；其中涉公网暴露面（8081/8765/8888/SSH）的变更，**安全结论优先**，高风险项不得直接放行，须升级用户裁定。本报告**未执行任何变更**。

---

## 6. 已确认合规项（正面基线）

1. UFW 默认 `deny (incoming)`，iptables `INPUT DROP`；Fail2Ban 双 jail 生效（sshd/ftpd，bantime 86400），已封 56 个爆破 IP。
2. `.map` → 404（Nginx 三处 location 覆盖 web/staff/boss），线上产物 `.map` = 0。
3. `auto-dispatch` → 403（Nginx 层，后端层缺拦，见 SRV-02）。
4. 80 → 301 跳转 443；TLS 仅 1.2/1.3 且 ECDHE 套件；证书 **5 层链**、有效至 2026-12-21；acme 自动续期 cron 已配。
5. 容器均非 privileged；`courier-app` 以 `courier` 非 root 用户运行；`/var/run/docker.sock` 仅 `root:docker`。
6. Redis 仅回环；MySQL 3307 仅回环 + 容器网段；无 3306 公网暴露。
7. 宝塔面板 HTTPS 开启 + 隐藏路径；评审账号文件 600；备份目录具备多份发布前快照。
8. 磁盘与内存余量健康。

---

## 7. 未验证 / 开放问题（不得当作已结论）

1. **8081 上 auto-dispatch 的实际可利用性**：仅确认端口可达 + 代码白名单，**未发写入型请求**（会建单与触发通知），故标「未实测」。
2. **8765 的鉴权强度**：仅确认端口公网可达与存在 `AUTH_KEY/AUTH_SALT/allow_ip` 配置键，**未读取配置值、未做鉴权绕过测试**。
3. **面板与 SSH 口令强度**：无法在不获取凭据的前提下评估（凭据归主智能体托管）。
4. **MySQL 授权表与账号 host 范围**：未查（需凭据）。
5. **apt 可升级包的实际 CVE 影响面**：未做依赖漏洞扫描（需 `apt` 元数据 + NVD 比对），当前仅计数。
6. **服务器与本地仓库关键文件漂移**：本轮未做 MD5 对照（`pom.xml`/`Dockerfile`/`deploy.sh`/`nginx.conf`/`docker-compose.yml`），建议纳入常规检查。
7. **`/hrm-api/` 前缀是否仍有消费者**：未在代码侧穷尽检索，下线 8081 前必须先确认。
8. **`courier-nginx` 容器以 root 主进程运行**：属 nginx 镜像默认行为，未评估其是否加载非受信配置。

---

## 8. 交付边界

- 本报告由**主智能体**（评估与决策分离要求下的实测方）出具，**不替代**网络安全工程师的漏洞结论，亦不构成授权。
- 仓库侧静态审计结论见 `security-full-review-20260926.md`；工程面技术质量结论（**总体：有条件通过，9 条必改项**）见 `tech-review-full-20260926.md`。
- 所有 C 档整改待用户/主智能体授权后执行，执行方由主智能体指派（运维工程师执行、安全复验）。
