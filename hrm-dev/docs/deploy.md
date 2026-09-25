# 快递驿站智汇系统 · 部署操作手册

| 项目 | 内容 |
| ---- | ---- |
| 文档版本 | v1.0 |
| 编写日期 | 2026-09-06 |
| 适用范围 | 一期员工管理（对应 TASK.md 阶段 D：D01 基础环境 / D02 数据库 / D03 后端 / D04 前端 / D05 初始化与安全加固） |
| 目标环境 | 腾讯云 Linux（CentOS / OpenCloudOS / Ubuntu 通用，命令差异处已分别标注）+ 宝塔面板 |
| 关联文档 | [docs/requirement.md](requirement.md)、[docs/db.md](db.md)、[docs/api.md](api.md)、[env.example](../../env.example) |
| 配套文件 | [hrm-dev/deploy/deploy.sh](../deploy/deploy.sh)、[hrm-dev/deploy/hrm-server.service](../deploy/hrm-server.service)、[hrm-dev/deploy/nginx.conf.example](../deploy/nginx.conf.example) |

> **占位符约定**：本手册所有 `<你的服务器IP>`、`<你的域名>`、`<MySQL应用账号强密码>`、`<Redis密码>`、`<初始密码>`、`change_me_*` 均为占位符，执行时替换为真实值；真实 IP、域名、密钥**严禁写入任何 Git 文档与脚本**（项目规则第 10 章安全红线）。

> **首次部署总顺序**：第 1 章（本地首次推送代码，一次性）→ 第 2 章 D01 环境 → 第 3 章 D02 建库 → 第 4 章 D03 后端 → 第 5 章 D04 前端 → 第 6 章 D05 安全加固。日常更新见第 7 章，出问题见第 8/9 章。

***

## 0. 部署架构与目录规划

### 0.1 架构总览

```text
                  ┌────────────────── 腾讯云 ECS（Linux + 宝塔）──────────────────┐
                  │                                                                   │
浏览器 ─80/443─→  │  Nginx（宝塔管理）                                                 │
                  │   ├─ /           → /www/wwwroot/hrm-admin（Vue3 构建产物，SPA）   │
                  │   └─ /api/       → http://127.0.0.1:8080（仅回环，               │
                  │                     透传 X-Real-IP / X-Forwarded-For）           │
                  │                       │                                           │
                  │                  hrm-server.jar（JDK17，Flyway 启动自动迁移）      │
                  │                       ├─→ MySQL 8   127.0.0.1:3306/kdyzgl        │
                  │                       └─→ Redis     127.0.0.1:6379                │
                  │                                                                   │
                  │  外置配置：/www/wwwroot/hrm-server/config/application-prod.yml     │
                  │  （真实密码只存在服务器磁盘，600 权限，被 .gitignore 忽略不入 Git）  │
                  └───────────────────────────────────────────────────────────────────┘
```

> **矛盾标注（2026-09-25，不改写上文历史结论）：** 本图 `0.1` 所述 `/` → `/www/wwwroot/hrm-admin`（宝塔站点根）与**现网实况不符**——现网 `/` 由容器 `hrm-demo-static`（演示静态站）提供端选择页门户，非 `hrm-admin` 站点根。**以现网为准**，详见本手册新增的 §11「现网实况与 B7 三端入口」。

### 0.2 服务器目录规划

| 路径 | 用途 | 说明 |
| ---- | ---- | ---- |
| `/www/wwwroot/kdyzgl-base` | Git 仓库 | `git clone` 获得，只 pull 不手改（红线：服务器代码只来源于 Git） |
| `/www/wwwroot/hrm-server/` | 后端运行目录 | **不配置为任何 Nginx 站点目录**，公网不可访问 |
| `/www/wwwroot/hrm-server/hrm-server.jar` | 运行 jar（固定文件名） | 宝塔 Java 项目 / systemd 始终指向它，deploy.sh 负责替换 |
| `/www/wwwroot/hrm-server/config/application-prod.yml` | 外置生产配置（真实密码） | `600` 权限、属主 `www:www`，详见 4.2 |
| `/www/wwwroot/hrm-server/logs/` | 部署/Maven/npm/nohup 日志 | deploy.sh 自动写入 |
| `/www/wwwroot/hrm-server/backup/` | 旧 jar 备份（保留最近 5 份） | 回滚用，见第 8 章 |
| `/www/wwwroot/hrm-admin/` | 前端站点根目录 | 宝塔站点根目录，Nginx root |
| `/www/wwwlogs/` | Nginx 访问/错误日志 | 宝塔默认位置 |

### 0.3 端口策略（安全基线）

| 端口 | 用途 | 对公网 |
| ---- | ---- | ---- |
| 22 | SSH | 放行（建议改用密钥登录 + fail2ban，见 2.4） |
| 80 / 443 | HTTP / HTTPS | 放行 |
| 8080 | hrm-server | **禁止放行**（仅 Nginx 本机反代） |
| 3306 | MySQL（容器 `courier-mysql`，2026-09-23 起停用） | **禁止放行**（容器已停，端口不再监听） |
| 3307 | MySQL（宿主实例，**现网生产库**） | **禁止放行**（仅监听 127.0.0.1 / 172.17.0.1 / 172.19.0.1，见 0.5） |
| 6379 | Redis | **禁止放行**（仅 127.0.0.1 访问） |

### 0.4 安全红线（摘自项目规则，部署全程遵守）

1. 配置文件绝不进 Git：`.env*`、`application-*.yml`、密钥证书等已由根目录 `.gitignore` 忽略；
2. 服务器代码只来源于 `git pull`，禁止在生产服务器直接编辑源码；
3. 真实 IP、密钥、密码严禁写入 Git 仓库与文档；
4. AI 生成的默认密钥（`change_me_*`）必须全部替换为自建强密钥后方可上线（D05 验收项）。

### 0.5 存储策略（系统盘 / 数据盘分工）

挂载：系统盘 30G（`/`，`/dev/vda1`）、数据盘 50G（`/data`，`/dev/vdb1`）。
**所有业务数据一律落数据盘 `/data`；系统盘只放操作系统、系统级程序与配置**，避免业务增长把根分区吃满、连带 SSH 与服务异常。

| 存储位置 | 承载内容 | 落点 |
| ---- | ---- | ---- |
| `/data/mysql-host/` | **宿主 MySQL 8 数据目录**（datadir，端口 3307）——**现网生产库**，`courier-app` 经 `host.docker.internal` 连此实例 | 数据盘 |
| `/data/log/mysql/` | 宿主 MySQL 错误日志 | 数据盘 |
| `/data/mysql-tmp/` | 宿主 MySQL 临时文件（大排序等） | 数据盘 |
| `/data/mysql/` | 已停用的容器 MySQL（`courier-mysql`）datadir（2026-09-23 切换后保留，供回滚） | 数据盘 |
| `/data/www/` | 站点与仓库（`kongzhen1`、`hrm-demo`、`kdyzzhxt`） | 数据盘 |
| `/data/ssl/`、`/data/le-challenges/` | 证书与 ACME 验证目录 | 数据盘 |
| `/data/backup/` | 备份归档（数据库 / 配置 / HIDS 日志 / 宝塔旧备份） | 数据盘 |
| `/data/log/` | 运维脚本日志（如 HIDS 轮转） | 数据盘 |
| `/data/swapfile` | 2G swap（宿主内存仅 3.8G，防 OOM 波及容器） | 数据盘 |
| `/www/`、`/var/`、`/usr/` | 宝塔面板、Docker 数据根、系统程序 | 系统盘（系统级，属例外） |

**宿主 MySQL 落盘配置**：`/etc/mysql/mysql.conf.d/zz-kdyzgl-storage.cnf`（仓库留档 `hrm-dev/deploy/scripts/zz-kdyzgl-storage.cnf`），要点 `datadir=/data/mysql-host`、`port=3307`、`log_error`/`tmpdir` 指向 `/data`。Ubuntu 的 apparmor profile 需经 `/etc/apparmor.d/local/usr.sbin.mysqld` 放行 `/data` 路径后重载，否则服务起不来。

**宿主 MySQL 与容器的连通（2026-09-23 切换后新增，缺一即连不上）**：

| 项 | 配置 | 说明 |
| ---- | ---- | ---- |
| 多地址绑定 | `bind-address = 127.0.0.1,172.17.0.1,172.19.0.1` | MySQL 8.0.13+ 支持逗号分隔。`172.17.0.1`（docker0）与 `172.19.0.1`（`courier-net`）供容器访问；实测容器内 `host.docker.internal` 解析为 **172.17.0.1** |
| UFW 定向放行 | `ufw allow from 172.19.0.0/16 to any port 3307 proto tcp`（`172.17.0.0/16` 同） | UFW 的 INPUT 默认 DROP，仅改 bind-address 不够 |
| 账号授权 | `root@'172.19.%'`（`caching_sha2_password`，口令 = `.env` 的 `DB_PASSWORD`） | 容器侧认证；宿主本地管理走 `root@localhost`（unix socket，空口令，用 `mysql -uroot` 不加 `-p`） |
| systemd drop-in | `/etc/systemd/system/mysql.service.d/zz-kdyzgl.conf`：`After/Wants=docker.service` + `StartLimitIntervalSec=0` | 防开机 dockerd 未起时绑定 172.19.0.1 失败即放弃启动 |

**生产库切换事实（2026-09-23）**：生产库已由容器实例（`courier-mysql`，3306）切至宿主实例（3307），容器与 `/data/mysql` 保留供秒级回滚；应用连库串为 `jdbc:mysql://host.docker.internal:3307/courier_station`，`app` 增 `extra_hosts`，`docker-compose.yml` 已移除 `mysql` 服务定义。**注意 `docker compose up -d` 会自动加载 `docker-compose.override.yml`（dev profile），现网必须显式 `-f docker-compose.yml`。** 详见 `KDYZZHXT/update-log.md` 的 2026-09-23 条目。

**已知增长点与治理**：宝塔入侵检测日志 `/www/server/panel/data/hids_data/log` 按天写 JSON（实测单日 240M+）且面板无内置保留策略，曾 19 天累积 4.7G。已部署 `/usr/local/bin/hids-log-rotate.sh`（仓库 `hrm-dev/deploy/scripts/hids-log-rotate.sh`）+ `/etc/cron.d/hids-log-rotate`，**每周日 03:30 轮转，只保留近 7 日**。新机器初始化时必须重复该步骤，否则系统盘会被持续吃满。

***

## 1. 首次 Git 仓库初始化（本地开发机执行，一次性）

> 背景：本仓库尚未 `git init`，远端仓库 `jia-yongbin/yizhan`（Gitee / GitHub）已建好空仓库。本章在**本地开发机**（Windows，使用 Git Bash）执行一次；服务器部署所需的 clone 见 4.1。

### 1.1 初始化并提交

```bash
cd /d/Users/16626/Desktop/kdyzgl-base

# 初始化仓库，主分支命名 main（git < 2.28 时用：git init && git branch -m main）
git init -b main

git add .

# 提交前人工检查：确认没有敏感文件被纳入
git status
# 预期：不含 .env、application-prod.yml、*.jar、*.pem 等文件
# （.gitignore 已配置忽略；examples/ 下的 xlsx 为样例数据，允许提交）

git commit -m "chore: 初始化一期员工管理工程"
```

### 1.2 建立 dev 分支（分支模型：main 生产 / dev 联调）

```bash
git branch dev
```

### 1.3 关联 Gitee / GitHub 双远端并推送

```bash
# HTTPS 方式（推荐先跑通；SSH 方式见 1.5）
git remote add gitee  https://gitee.com/jia-yongbin/yizhan.git
git remote add github https://github.com/jia-yongbin/yizhan.git

git remote -v   # 确认两个远端

# 推送 main 与 dev（首次加 -u 建立跟踪）
git push -u gitee main
git push gitee dev
git push -u github main
git push github dev
```

### 1.4 后续协作纪律

- 日常开发：`feature/*` 分支 → 合并 `dev` 联调 → 验证通过合并 `main` → 打 tag（如 `v1.0.0-一期员工管理`）；
- 服务器只从 `main` 拉取部署（联调环境可拉 `dev`）；
- Commit 信息遵守「类型: 中文描述」规范（详见项目规则第 3 章）。

### 1.5 可选：改用 SSH 远端（免密推送）

```bash
ssh-keygen -t ed25519 -C "change_me_email"
# 公钥 ~/.ssh/id_ed25519.pub 分别添加到 Gitee / GitHub 账号 SSH 公钥设置
git remote set-url gitee  git@gitee.com:jia-yongbin/yizhan.git
git remote set-url github git@github.com:jia-yongbin/yizhan.git
```

***

## 2. D01 · 服务器基础环境

### 2.1 系统初始化（root 登录后）

```bash
# 时区（D01 验收：JVM 与 MySQL serverTimezone 一致，均为 Asia/Shanghai）
timedatectl set-timezone Asia/Shanghai
timedatectl        # 确认输出 Time zone: Asia/Shanghai (CST, +0800)

# 基础工具
yum install -y wget curl vim unzip rsync        # CentOS / OpenCloudOS
apt install -y wget curl vim unzip rsync       # Ubuntu / Debian
```

### 2.2 安装宝塔面板

```bash
# CentOS / OpenCloudOS
yum install -y wget && wget -O install.sh https://download.bt.cn/install/install_6.0.sh && sh install.sh ed8484bec

# Ubuntu / Debian
wget -O install.sh https://download.bt.cn/install/install-ubuntu_6.0.sh && sudo bash install.sh ed8484bec
```

安装完成后记录面板输出的是地址、账号、密码；**立即在面板设置中修改默认账号密码与安全入口**，并按 2.4 收敛面板端口访问。

### 2.3 宝塔软件商店安装 JDK17 / MySQL 8 / Redis

| 软件 | 安装位置 | 配置要点 |
| ---- | ---- | ---- |
| JDK 17 | 软件商店 → Java 项目管理器（或「运行环境」JDK17） | 安装后执行 `java -version` 确认 17.x；确认 `which java` 路径（如 `/usr/bin/java`），非系统路径时记下实际位置（systemd 单元需要） |
| MySQL 8 | 软件商店 → MySQL 8.0 | 安装后设置 root 密码并妥善保存；**不要开启远程访问**（root 只允许本机） |
| Redis | 软件商店 → Redis | 在「性能调整」中设置 `requirepass <Redis密码>`；确认 `bind 127.0.0.1`（只监听本机） |

```bash
# 验证（Redis 密码验证示例）
redis-cli -a '<Redis密码>' --no-auth-warning ping    # 预期 PONG
mysql -uroot -p -e "SELECT VERSION();"               # 预期 8.0.x
java -version                                        # 预期 17.x
```

> MySQL 时区说明：后端连接串已带 `serverTimezone=Asia/Shanghai`（内置 application.yml），配合 2.1 的系统时区即满足 D01 时区一致性要求，无需额外改 my.cnf。

### 2.4 防火墙与安全组（D01 验收关键项）

**第一层：腾讯云安全组**（控制台 → 云服务器 → 安全组），入站规则只保留：

| 协议 | 端口 | 来源 | 用途 |
| ---- | ---- | ---- | ---- |
| TCP | 22 | 你的管理 IP（或 0.0.0.0/0 + fail2ban） | SSH |
| TCP | 80 | 0.0.0.0/0 | HTTP |
| TCP | 443 | 0.0.0.0/0 | HTTPS |

**不放行** 8080、3306、6379、宝塔面板端口（如 8888/888）。

**第二层：宝塔防火墙**（面板 → 安全），同样只放行 22/80/443；面板入口端口建议限制为你的管理 IP，或使用面板的「安全入口」随机路径。

**可选加固：fail2ban 防 SSH 爆破**

```bash
yum install -y epel-release && yum install -y fail2ban     # CentOS / OpenCloudOS
apt install -y fail2ban                                    # Ubuntu / Debian

cat > /etc/fail2ban/jail.local <<'EOF'
[sshd]
enabled = true
maxretry = 5
findtime = 10m
bantime = 1h
EOF

systemctl enable --now fail2ban
fail2ban-client status sshd     # 查看拦截情况
```

### 2.5 D01 验收清单

| D01 验收标准 | 手册覆盖 | 自查方法 |
| ---- | ---- | ---- |
| 宝塔安装 JDK17 / MySQL8 / Redis | 2.3 | `java -version` / `SELECT VERSION();` / `redis-cli ping` |
| 时区 Asia/Shanghai（JVM 与 MySQL 一致） | 2.1 / 2.3 | `timedatectl`；JVM 由启动参数 `-Duser.timezone` 兜底 |
| 防火墙仅放行 22/80/443 | 2.4 | 腾讯云控制台核对安全组；`ss -lntp` 确认 8080/3306/6379 仅 127.0.0.1 监听 |

***

## 3. D02 · 数据库初始化

### 3.1 创建数据库（utf8mb4）

```bash
mysql -uroot -p
```

```sql
-- 建库：字符集与 Flyway V1 建表脚本一致（utf8mb4_0900_ai_ci）
CREATE DATABASE `kdyzgl` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

### 3.2 创建最小权限应用账号

后端不用 root 连库，单独建 `hrm_app` 账号，**只授权 `kdyzgl` 一个库**：

```sql
-- 密码用强密码（生成示例：openssl rand -base64 24），线下妥善保存
CREATE USER 'hrm_app'@'127.0.0.1' IDENTIFIED BY '<MySQL应用账号强密码>';
CREATE USER 'hrm_app'@'localhost' IDENTIFIED BY '<MySQL应用账号强密码>';

-- 权限说明：
--  DML（SELECT/INSERT/UPDATE/DELETE）：业务读写 + flyway_schema_history 维护；
--  DDL（CREATE/ALTER/INDEX/REFERENCES）：Flyway 迁移期建表/建索引需要；
--  不授予 DROP：防止误删表（V1/V2 脚本不含 DROP）；
--  不授权其他库，不授权 '%' 任意主机（仅 127.0.0.1/localhost 两个主机名）
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON `kdyzgl`.* TO 'hrm_app'@'127.0.0.1', 'hrm_app'@'localhost';

FLUSH PRIVILEGES;

-- 验证权限
SHOW GRANTS FOR 'hrm_app'@'127.0.0.1';
```

> **上线稳定后的权限收紧**（可选，建议执行）：确认不再有新增迁移后，回收 DDL 权限，只留 DML：
>
> ```sql
> REVOKE CREATE, ALTER, INDEX, REFERENCES ON `kdyzgl`.* FROM 'hrm_app'@'127.0.0.1', 'hrm_app'@'localhost';
> ```

### 3.3 凭据存放约定（不入 Git）

| 凭据 | 存放位置 | 禁止出现的位置 |
| ---- | ---- | ---- |
| MySQL 应用账号密码 | `/www/wwwroot/hrm-server/config/application-prod.yml`（600 权限） | Git 仓库、部署脚本、命令行参数、聊天/文档 |
| Redis 密码 | 同上 | 同上 |
| JWT 密钥 | 同上 | 同上 |
| admin 初始密码 / 员工导入初始密码 | 线下密码管理器（首登改密后即失效） | 任何电子文档 |

`.gitignore` 已忽略 `application-*.yml` 与 `.env*`，仓库中只有 `env.example` 模板（参数名 + `change_me_*` 占位符）。

### 3.4 结构比对说明

- **不需要**手工导入 `sql/schema/` 下的建表脚本：后端首次启动时 Flyway 会自动执行 `db/migration/mysql/V1__init_schema.sql` 与 `V2__init_data.sql`（见 4.6 验证）；
- `hrm-dev/sql/schema/{mysql,postgresql}/init.sql` 是结构快照，仅用于人工比对库结构一致性（D02 验收项），比对方法：

```bash
# 服务器导出实际结构，与仓库快照 hrm-dev/sql/schema/mysql/init.sql 人工比对
mysqldump -uroot -p --no-data --skip-comments kdyzgl | sed 's/AUTO_INCREMENT=[0-9]*//' > /tmp/schema_dump.sql
diff <(grep -v '^/\*\|^--\|^$' /tmp/schema_dump.sql) \
     <(grep -v '^/\*\|^--\|^$' /www/wwwroot/kdyzgl-base/hrm-dev/sql/schema/mysql/init.sql) \
  && echo '结构一致' || echo '存在差异，请人工核对'
```

### 3.5 D02 验收清单

| D02 验收标准 | 手册覆盖 | 自查方法 |
| ---- | ---- | ---- |
| 建库 kdyzgl（utf8mb4） | 3.1 | `SHOW CREATE DATABASE kdyzgl;` |
| 最小权限账号（仅 kdyzgl 库 DML） | 3.2 | `SHOW GRANTS FOR 'hrm_app'@'127.0.0.1';` |
| 凭据只存服务器配置文件（不入 Git） | 3.3 | `git -C /www/wwwroot/kdyzgl-base status` 确认无配置文件被跟踪 |
| sql/schema 快照与库结构比对一致 | 3.4 | 3.4 的 diff 命令（Flyway 建表后再执行） |

### 3.6 测试库 `kdyzgl_test` 与联调种子数据（独立脚本，非 Flyway）

**定位**：前端演示站即将切换为直连真实后端，页面需要可见数据。为此提供独立种子脚本 `hrm-dev/sql/seed/kdyzgl_test_seed.sql`（约 2690 行），**只对测试库 `kdyzgl_test` 执行**，走人工执行、**不走 Flyway**，也不进 `sql/schema` 快照。

**隔离边界（三库职责，务必分清）**：

| 库 | 用途 | 本脚本是否触碰 |
| ---- | ---- | ---- |
| `courier_station` | 现网快递系统 | **绝对禁止**（脚本头部有醒目注释拦截） |
| `kdyzgl` | 结构库（Flyway v1–v15，保持干净） | 禁止（脚本头部有醒目注释拦截） |
| `kdyzgl_test` | 联调/演示（Flyway 已至 v15） | **仅此库** |

**结构前置**：`kdyzgl_test` 须先由后端启动跑完 Flyway v1–v15（表结构与 `kdyzgl` 一致），本脚本只做 `DELETE` + `INSERT`，**不含任何 DDL**。

**执行命令**（在服务器上，手工指定目标库；密码走本地配置，勿写入命令历史以外的明文）：

```bash
# 目标库显式写死在命令行，避免误连 kdyzgl / courier_station；--default-character-set 保证中文按 utf8mb4 读入
mysql -uroot -p --default-character-set=utf8mb4 kdyzgl_test < /www/wwwroot/kdyzgl-base/hrm-dev/sql/seed/kdyzgl_test_seed.sql

# 执行后一键核对各表行数（脚本末尾已内置同样查询，此处可复查）
mysql -uroot -p kdyzgl_test -e "SELECT 'employee' AS t, COUNT(*) AS c FROM employee UNION ALL SELECT 'parcel', COUNT(*) FROM parcel;"
```

**幂等与回滚**：脚本先按「子表→父表」整表 `DELETE` 再 `INSERT`（本项目无物理外键，D6 逻辑外键；不 `DROP`／不 `TRUNCATE`），可重复执行，**执行两次结果一致**。

- 重跑即「回滚到种子态」：再次执行同一脚本即可。
- 彻底清空联调数据（保留结构）：`DELETE FROM` 脚本覆盖的 32 张表即可（表清单见脚本第 0 节）；**不要在 `kdyzgl` 上执行**。
- 若只想恢复业务表而不动账号：可单独重跑对应分节的 `INSERT`（脚本按域分节，注释齐全）。

**密码哈希的生成与校验**（演示统一口令 `demo1234`，**脚本中只写散列、不写明文**）：

```bash
# 本地生成（bcryptjs 为纯 JS 实现，任选临时目录安装，勿改本仓库依赖清单）
mkdir -p /tmp/bcgen && cd /tmp/bcgen && npm init -y >/dev/null && npm i bcryptjs@2.4.3 --no-audit --no-fund
node -e "const b=require('bcryptjs');const h=b.hashSync('demo1234',b.genSaltSync(10));console.log(h);console.log('verify',b.compareSync('demo1234',h))"

# 校验已入库的散列（拿到某行 password 后）
node -e "const b=require('bcryptjs');console.log(b.compareSync('demo1234',process.argv[1]))" '<库中password值>'
```

本脚本所用散列为 `$2a$10$TVkFYxOLLRGLQ47IMUPhVuLHJW3dDUhDqEx0PSa7bs0tq38jQhjl2`（cost=10、60 字符，`compareSync('demo1234', hash)` 回环校验通过，与 V2 的 `$2a$` 前缀、后端 `BCryptPasswordEncoder` 兼容）。

**与后端自动播种的关系（避免冲突）**：`SyncConfigSeeder`（`ApplicationRunner`，幂等）在每次启动时补齐 5 个配置项 / 11 个选项 / 5 个全局默认值，并播种驿站采集配置与覆盖值。因此**本脚本不触碰** `sync_config_item`／`sync_config_option`／`sync_config_global`／`sync_config_station_override`／`sync_station_config` 五表，也不预置 `auth_trusted_device`（由登录流程写入）。

**执行阶段与收敛**：脚本为静态产物，已做静态自检（列名存在性、类型匹配、枚举取值、非空约束、逻辑外键指向、JSON 合法性、单元格数 = 列数）。**SQL 实跑与行数核对收敛到服务器阶段**，由主智能体在 `kdyzgl_test` 执行并按脚本末尾校验查询核对（数据量与形态以实际执行为准）。

***

## 4. D03 · 后端部署

### 4.1 服务器拉取代码

```bash
mkdir -p /www/wwwroot
cd /www/wwwroot

# 推荐 Gitee（国内速度快）；使用只具备拉取权限的账号，避免服务器持有推送凭据
git clone https://gitee.com/jia-yongbin/yizhan.git kdyzgl-base
cd kdyzgl-base
git branch -a          # 确认 main / dev 分支已存在
```

> 后续更新代码一律 `git pull`（deploy.sh 内置）；**禁止**在服务器上直接修改源码（项目规则红线）。

### 4.2 生成外置生产配置 application-prod.yml

```bash
mkdir -p /www/wwwroot/hrm-server/{config,logs,backup}
vim /www/wwwroot/hrm-server/config/application-prod.yml
```

推荐**最小集**内容如下（只覆盖需要真实值的项，真实密码替换尖括号占位符）：

```yaml
# /www/wwwroot/hrm-server/config/application-prod.yml
# 真实生产配置：仅存服务器磁盘，600 权限，绝不提交 Git
server:
  port: 8080

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    # 注意保留 allowPublicKeyRetrieval=true（MySQL8 caching_sha2_password 非 SSL 连接需要，
    # 仓库内置 application.yml 的 url 已带；env.example 模板中的 url 未带，勿照抄，见 4.2.1 坑二）
    url: jdbc:mysql://127.0.0.1:3306/kdyzgl?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: hrm_app
    password: <MySQL应用账号强密码>          # 对应 env.example 的 change_me_db_password
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: <Redis密码>                  # 对应 env.example 的 change_me_redis_password
      database: 0

jwt:
  secret: <JWT强密钥>                        # 对应 change_me_jwt_secret_*，生成见下表
  expire: 86400                              # 秒，默认 24 小时，可按需调整

hrm:
  # Excel 导入员工的统一初始密码（首登强制改密），对应内置 application.yml 的 change_me_init_password
  employee-init-password: <Excel导入员工统一初始密码>

logging:
  level:
    root: info
    com.qiujie: info
```

配置完成后收紧权限：

```bash
chown www:www /www/wwwroot/hrm-server/config/application-prod.yml
chmod 600 /www/wwwroot/hrm-server/config/application-prod.yml
```

#### 4.2.1 两个必知的坑（照抄 env.example 会踩）

| 坑 | 说明 | 规避方法 |
| ---- | ---- | ---- |
| 坑一：flyway locations | env.example 模板里写的是 `classpath:db/migration`，会**覆盖**内置 application.yml 正确的 `classpath:db/migration/{vendor}`，导致按方言选目录（mysql/、postgresql/）失效、启动报「未找到迁移脚本」 | 外置配置**不要写 spring.flyway 段**（内置已正确配置 `{vendor}`，MySQL/PostgreSQL 自动选择，无需任何修改） |
| 坑二：datasource url | env.example 的 url 缺 `allowPublicKeyRetrieval=true`，MySQL 8 默认认证插件下启动可能报 `Public Key Retrieval is not allowed` | url 按上文最小集书写（已带该参数），或直接不覆盖 url（沿用内置值） |

#### 4.2.2 占位符替换清单（与 env.example 一一对应）

| env.example 占位参数 | 替换值来源 | 生成命令示例 |
| ---- | ---- | ---- |
| `change_me_db_user` / `change_me_db_password` | 3.2 创建的 `hrm_app` 账号及其密码 | 密码：`openssl rand -base64 24` |
| `change_me_redis_password` | 2.3 宝塔 Redis 的 requirepass 值 | 同上 |
| `change_me_jwt_secret_please_generate_a_long_random_string` | 自建 JWT 强密钥（HS256 要求 ≥32 字节） | `openssl rand -base64 48` |
| `change_me_init_password`（内置 application.yml 的 hrm 段） | Excel 导入员工统一初始密码 | 自定强密码，线下记录 |
| `change_me_mail_*` / `change_me_llm_*` / `change_me_oss_*` | 一期未启用邮件/LLM/对象存储 | **整段不写入外置配置**（避免 D05 的 change_me 残留检查报警），二期启用时再补 |
| `spring.flyway.*` | 不覆盖（见坑一） | — |

### 4.3 后端打包（二选一）

**方式 A：服务器打包**（默认，deploy.sh `BACKEND_MODE=source`）

```bash
# 前置：安装 Maven（CentOS/OpenCloudOS 用 yum；Ubuntu 用 apt）
yum install -y maven     # 或：apt install -y maven
mvn -v                   # 确认 Java version 为 17.x；若非 17，见 FAQ Q8
```

**方式 B：本地打包上传**（服务器不装 Maven 时，deploy.sh `BACKEND_MODE=jar`）

```bash
# 本地开发机（项目根目录下）
cd hrm-dev/hrm-server
mvn clean package -DskipTests
# 产物：target/hrm-server-1.0.0.jar

# 上传到服务器 /tmp（Windows PowerShell 也可执行 scp）
scp target/hrm-server-1.0.0.jar root@<你的服务器IP>:/tmp/
```

### 4.4 启动方式（三选一；宝塔为主，systemd 为脚本自动化备选）

> **同一时间只能选一种方式托管进程**，否则会出现双进程抢 8080 端口（见 FAQ Q1）。宝塔托管后，执行 deploy.sh 必须**始终带 `RESTART_MODE=bt`**；选 systemd / nohup 则用默认 `auto`。

#### 方式 A（主推）：宝塔 Java 项目管理器

宝塔面板 → 网站 → Java项目 → 添加Java项目：

| 配置项 | 值 |
| ---- | ---- |
| 项目名称 | `hrm-server` |
| 项目 jar 路径 | `/www/wwwroot/hrm-server/hrm-server.jar`（固定名，deploy.sh 替换的就是它） |
| 项目端口 | `8080` |
| JDK 版本 | 17 |
| 启动参数 | `--spring.profiles.active=prod --spring.config.additional-location=file:/www/wwwroot/hrm-server/config/` |
| 开机自启 | 勾选 |

（面板版本不同字段名略有差异，以实际为准；核心是 jar 路径、端口 8080、JDK17、上述启动参数。）

#### 方式 B：systemd（deploy.sh 自动化的推荐搭档）

```bash
# 安装服务单元（文件随仓库分发）
cp /www/wwwroot/kdyzgl-base/hrm-dev/deploy/hrm-server.service /etc/systemd/system/
# 按需修改：ExecStart 中 java 路径（宝塔 JDK 实际路径用 ls /www/server/java/ 或 which java 确认）
vim /etc/systemd/system/hrm-server.service

systemctl daemon-reload
systemctl enable --now hrm-server
systemctl status hrm-server          # 查看运行状态
journalctl -u hrm-server -f          # 跟踪启动日志（含 Flyway 输出）
```

#### 方式 C：nohup 兜底（未装 systemd 单元也未用宝塔时）

由 deploy.sh 自动完成（`RESTART_MODE` 解析为 `nohup`）：PID 文件 `/www/wwwroot/hrm-server/hrm-server.pid`，控制台输出 `/www/wwwroot/hrm-server/logs/stdout.log`。

### 4.5 执行部署脚本 deploy.sh

**首次部署**（4.2 配置就绪后，一条命令完成 拉代码→打包→替换 jar→重启→健康检查→前端构建→同步站点）：

```bash
bash /www/wwwroot/kdyzgl-base/hrm-dev/deploy/deploy.sh
```

**常用命令速查**：

```bash
cd /www/wwwroot/kdyzgl-base

# 联调环境（拉 dev 分支）
DEPLOY_BRANCH=dev bash hrm-dev/deploy/deploy.sh

# 方式 B 打包：本地 jar 上传后仅部署后端
BACKEND_MODE=jar PREBUILT_JAR=/tmp/hrm-server-1.0.0.jar \
  bash hrm-dev/deploy/deploy.sh --backend-only

# 前端本地构建上传（服务器不装 Node 时）：本地 npm run build 后
#   scp -r hrm-dev/hrm-admin/dist root@<你的服务器IP>:/tmp/hrm-admin-dist
FRONTEND_MODE=dist PREBUILT_DIST=/tmp/hrm-admin-dist \
  bash hrm-dev/deploy/deploy.sh --frontend-only

# 宝塔托管进程时（方式 A），必须显式指定重启模式
RESTART_MODE=bt bash hrm-dev/deploy/deploy.sh

# 回滚上一版后端 jar
bash hrm-dev/deploy/deploy.sh --rollback
```

脚本行为要点：`set -euo pipefail` 任一步失败即中止；部署前自动校验外置配置存在且**无 `change_me_*` 残留**（未替换直接终止）；旧 jar 自动备份到 `backup/`（保留 5 份）；日志见 `/www/wwwroot/hrm-server/logs/deploy-*.log`。完整参数见附录 A。

### 4.6 部署验证（D03 验收核心）

**① Flyway V1/V2 执行确认**（方式 A 宝塔：面板项目日志；方式 B：`journalctl -u hrm-server`；方式 C：`logs/stdout.log`）：

```bash
journalctl -u hrm-server --no-pager | grep -i flyway
# 预期关键字：Migrating schema `kdyzgl` to version "1 - init schema" / "2 - init data"
# 以及 Successfully applied 2 migrations（首次部署）
```

权威验证——查迁移历史表：

```bash
mysql -uroot -p -e "SELECT installed_rank, version, description, success, installed_on FROM kdyzgl.flyway_schema_history ORDER BY installed_rank;"
# 预期：version=1（init schema）与 version=2（init data）两行，success 均为 1
```

**② 种子数据核对**：

```bash
mysql -uroot -p -e "USE kdyzgl; SELECT COUNT(*) AS dept_cnt FROM department; \
SELECT id, username, real_name, role, status, pwd_changed FROM employee;"
# 预期：dept_cnt=1（总公司）；employee 仅 1 行：admin / ADMIN / status=1 / pwd_changed=0
```

**③ 登录接口 curl 冒烟**（127.0.0.1:8080，验证 Web 层→MySQL→Redis 全链路）：

```bash
# 1) 空请求体：验证参数校验链路
curl -s -X POST 'http://127.0.0.1:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' -d '{}'
# 预期：JSON code=400，message 提示用户名/密码校验失败

# 2) 错误密码：验证 MySQL 连通与错误码统一（账号不存在/密码错误统一 1001）
curl -s -X POST 'http://127.0.0.1:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"WrongPassword123"}'
# 预期：{"code":1001,...}

# 3) 正确初始密码（db.md 5.2 部署占位初始密码的线下实际值）：
#    验证 BCrypt 校验 + Redis 会话写入
curl -s -X POST 'http://127.0.0.1:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<初始密码>"}'
# 预期：{"code":200,...,"data":{"token":"<JWT令牌>","pwdChanged":false,...}}

# 4) Redis 会话验证（admin 的 employeeId=1，key=hrm:session:1）
redis-cli -a '<Redis密码>' --no-auth-warning TTL 'hrm:session:1'
# 预期：正整数（剩余有效秒数）
```

> 登录成功即代表 MySQL（查 employee + 写 login_log）、BCrypt、Redis（写会话）全部连通。

### 4.7 D03 验收清单

| D03 验收标准 | 手册覆盖 | 自查方法 |
| ---- | ---- | ---- |
| git pull main → 打包（服务器或本地打包上传） | 4.1 / 4.3 / 4.5 | deploy.sh 日志「当前部署版本」 |
| 按 env.example 生成外置配置，change_me_* 全部替换 | 4.2 | deploy.sh 自动拦截；D05 再复核 |
| flyway locations 已内置 application.yml 无需改 | 4.2.1 坑一 | 外置配置无 spring.flyway 段 |
| 宝塔 Java 项目守护进程启动 | 4.4 | 面板显示运行中 / systemd active |
| 启动日志确认 Flyway V1/V2 执行成功 | 4.6 ① | flyway_schema_history 两行 success=1 |
| 127.0.0.1:8080 登录接口连通验证 | 4.6 ③ | curl 三连 |
| 部署步骤写入 docs/deploy.md | 本手册 | — |

***

## 5. D04 · 前端部署

### 5.1 前端构建（二选一）

**方式 A：服务器构建**（默认，deploy.sh `FRONTEND_MODE=source`）

```bash
# 前置：宝塔软件商店 → Node.js版本管理器 → 安装 20.x（Vite 6 要求 Node ≥ 18）
# 安装后在面板「设为命令行版本」，或手工建立软链后验证：
node -v     # 预期 v18/v20+
npm -v
```

npm 源说明：deploy.sh 已内置 `--registry=https://registry.npmmirror.com`（国内镜像，避免 npmjs 网络超时）；有 lockfile 时自动用 `npm ci`（可复现构建），否则 `npm install`。

**方式 B：本地构建上传**（服务器不装 Node 时，deploy.sh `FRONTEND_MODE=dist`）

```bash
# 本地开发机（项目根目录下）
cd hrm-dev/hrm-admin
npm install
npm run build
# 产物：dist/

# 上传到服务器 /tmp（Windows PowerShell 也可执行 scp）
scp -r dist root@<你的服务器IP>:/tmp/hrm-admin-dist
```

### 5.2 宝塔创建前端站点

面板 → 网站 → 添加站点：

| 配置项 | 值 |
| ---- | ---- |
| 域名 | `<你的域名>`（无域名可填 `<你的服务器IP>`，仅测试用） |
| 根目录 | `/www/wwwroot/hrm-admin` |
| PHP 版本 | 纯静态 |
| 数据库 / FTP | 均不创建 |

> 站点创建后目录里可能有宝塔默认页（index.html、.user.ini 等），deploy.sh 同步 dist 时会自动清理（保留 `.user.ini` 与 `.well-known`）。

### 5.3 Nginx 配置（SPA 回退 + /api 反代）

1. 打开样例文件 `hrm-dev/deploy/nginx.conf.example`（服务器路径 `/www/wwwroot/kdyzgl-base/hrm-dev/deploy/nginx.conf.example`）；
2. 面板 → 网站 → hrm-admin 站点 → 设置 → 配置文件：用样例中 `server{...}` 内容整体替换；
3. 把 `server_name` 改成你的域名/IP；
4. 保存并重载（面板会自动 `nginx -t` 校验；手工方式 `nginx -t && systemctl reload nginx`）。

**不可删改的四个关键项（D04 验收依赖）**：

| 配置 | 作用 |
| ---- | ---- |
| `location / { try_files $uri $uri/ /index.html; }` | 前端 vue-router history 模式：刷新/直连子路由（如 `/employee`）回退入口页，否则 404 |
| `location /api/ { proxy_pass http://127.0.0.1:8080; }` | API 反代本机后端；8080 不对公网开放，公网仅 80/443 |
| `proxy_set_header X-Real-IP / X-Forwarded-For / Host` | **登录日志 login_log.login_ip 取 X-Forwarded-For 首个 IP**，缺失会记录成 127.0.0.1 |
| `client_max_body_size 12m` | 与后端 multipart 上限对齐（Excel 导入单文件 ≤10MB） |

gzip 压缩、`/assets/` 长缓存、index.html 不缓存为性能加分项，随样例自带。

### 5.4 域名与 HTTPS（可选，域名就绪后启用）

1. 域名 A 记录解析到 `<你的服务器IP>`（腾讯云 DNSPod）；
2. 面板 → 站点设置 → SSL → Let's Encrypt 申请（会自动写入 `/.well-known` 验证目录，nginx.conf.example 的同步排除规则已保留该目录）；
3. 按样例文件尾部注释开启 443 监听与 80 → 301 跳转；证书路径以面板实际生成路径为准；
4. 开启后把安全组 443 端口确认放行（2.4 已预留）。

### 5.5 浏览器验证清单

| 步骤 | 操作 | 预期 |
| ---- | ---- | ---- |
| 1 | 浏览器打开 `http://<你的域名或服务器IP>` | 出现登录页（非宝塔默认页） |
| 2 | 登录后按 F5 刷新当前页（如员工管理 `/employee`） | 页面正常，不出现 Nginx 404（验证 try_files） |
| 3 | 输入 admin + 初始密码登录 | 登录成功（或进入强制改密流程，见 6.1） |
| 4 | 浏览器 F12 → Network → 任意 `/api` 请求 | 状态 200（验证反代） |
| 5 | 查登录日志 IP（见 6.3 SQL） | `login_ip` 为你的真实公网 IP，**不是** 127.0.0.1（验证 X-Forwarded-For 透传） |

### 5.6 D04 验收清单

| D04 验收标准 | 手册覆盖 | 自查方法 |
| ---- | ---- | ---- |
| dist 上传宝塔站点根目录 | 5.1 / 5.2 | `ls /www/wwwroot/hrm-admin/` 见 index.html、assets/ |
| Nginx /api 反代 127.0.0.1:8080 + 透传 X-Real-IP / X-Forwarded-For | 5.3 | 浏览器 F12 网络请求 200 + 6.3 登录日志 IP |
| SPA history 模式 try_files 回退 | 5.3 / 5.5 步骤 2 | 刷新子路由不 404 |
| 域名/HTTPS 就绪时配置证书 | 5.4 | 面板 SSL 状态 |
| 浏览器全流程可访问 | 5.5 | 五步全过 |

***

## 6. D05 · 初始化与安全加固

### 6.1 admin 首登强制改密（立即执行，越早越好）

初始账号：用户名 `admin`，初始密码为 db.md 5.2 约定的部署占位初始密码的**线下实际值**（散列已固化在 Flyway V2 种子脚本中，明文不写入任何仓库文件）。

操作流程：

1. 浏览器打开前端 → 登录页输入 `admin` + 初始密码；
2. 系统检测 `pwd_changed=0`，**强制锁定在改密卡片**（不能跳转任何其他页面）；
3. 设置新强密码（强度校验：与 api.md 4.1.4 一致），提交；
4. 改密成功 → 登录态清除 → 回到登录页，用新密码重新登录，正常进入系统；
5. 验证初始密码已失效（D05 验收项）：

```bash
curl -s -X POST 'http://127.0.0.1:8080/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<旧初始密码>"}'
# 预期：{"code":1001,...}（账号或密码错误），同时 login_log 记录一条失败日志
```

### 6.2 确认无 change_me_* 残留（D05 验收项）

```bash
# ① 检查服务器运行目录的配置文件（yml/properties 文本文件）
grep -RIn --include='*.yml' --include='*.properties' 'change_me' /www/wwwroot/hrm-server/ \
  && echo '发现残留，必须处理' || echo 'OK：运行目录无 change_me 残留'

# ② 检查 Java 进程启动参数（确认没有把占位值当参数传入）
ps -ef | grep '[h]rm-server.jar'
# 人工核对输出中不含任何 change_me 字样

# ③ 前端产物无需检查（构建产物不含后端配置）
```

**范围说明**：仓库内的 `env.example`、`hrm-server/src/main/resources/application.yml` 含 `change_me_*` 属正常（前者是模板，后者是被外置配置覆盖的内置默认值，不参与生产生效）；本项检查的对象是**服务器运行目录**与**运行中进程**。

### 6.3 登录日志验证（成功/失败均需正确记录）

```bash
mysql -uroot -p -e "SELECT username, login_result, fail_reason, login_ip, LEFT(user_agent,40) AS ua, login_time FROM kdyzgl.login_log ORDER BY id DESC LIMIT 10;"
```

预期（对照 6.1 操作后）：

| 记录 | login_result | fail_reason | login_ip |
| ---- | ---- | ---- | ---- |
| 旧初始密码尝试 | 0（失败） | 账号密码错误类提示 | 你的真实公网 IP |
| 新密码登录成功 | 1（成功） | NULL | 你的真实公网 IP |

若 `login_ip` 全是 `127.0.0.1` → Nginx 未透传 X-Forwarded-For，回到 5.3 检查；若 UA 为空 → 直连 8080 测试的正常现象（curl 无 UA 场景记录为空或 curl 默认值）。

### 6.4 admin 占位档案修正

种子数据的 admin 手机号为文档约定占位号 `13800000000`（非真实数据），按需修正：

- 登录系统 → 员工管理 → 编辑 admin → 修改手机号等档案信息；
- 或个人中心查看本人信息确认。

### 6.5 安全加固检查清单（上线前逐项打勾）

- [ ] admin 已完成首登强制改密，初始密码已失效（6.1）；
- [ ] 服务器运行目录无 change_me 残留（6.2）；
- [ ] MySQL 仅 127.0.0.1 监听，3306 未在安全组放行（2.3/2.4）；
- [ ] Redis 已设 requirepass，bind 127.0.0.1，6379 未放行（2.3/2.4）；
- [ ] 8080 未放行，后端只接受 Nginx 本机反代（2.4）；
- [ ] 外置配置 600 权限、属主 www（4.2）；
- [ ] fail2ban 已启用（可选但强烈建议，2.4）；
- [ ] SSH 建议改为密钥登录并禁用密码（云控制台/`/etc/ssh/sshd_config`，改前保留会话验证可达）；
- [ ] 宝塔面板已改默认账号密码 + 安全入口，面板端口不暴露公网（2.2/2.4）；
- [ ] 数据库计划备份已配置（8.4）。

### 6.6 D05 验收清单

| D05 验收标准 | 手册覆盖 | 自查方法 |
| ---- | ---- | ---- |
| 使用初始密码登录并完成强制改密 | 6.1 | 前端流程走通 |
| 确认初始密码已失效 | 6.1 步骤 5 | curl 返回 1001 |
| 服务器侧确认无 change_me_* 占位残留 | 6.2 | grep 命令输出 OK |
| 登录日志（成功/失败）有正确记录 | 6.3 | login_log 查询结果 |
| admin 手机号等占位档案按需修正 | 6.4 | 员工管理中核对 |

***

## 7. 日常更新部署流程

### 7.1 代码侧（本地开发机）

```text
feature/* 分支开发 → 自测 → 合并 dev 联调 → 验证通过 → 合并 main → 打 tag → push
```

```bash
# 本地（示例）
git checkout main && git merge dev
git tag v1.0.1-<版本说明>
git push gitee main --tags
git push github main --tags
```

### 7.2 服务器侧（一条命令）

```bash
bash /www/wwwroot/kdyzgl-base/hrm-dev/deploy/deploy.sh
# 宝塔托管进程时：RESTART_MODE=bt bash /www/wwwroot/kdyzgl-base/hrm-dev/deploy/deploy.sh
```

脚本自动完成：拉取 main → mvn 打包 → 备份旧 jar → 替换 → 重启 → 健康检查 → npm 构建 → 同步站点。

### 7.3 数据库变更前置（重要）

- 涉及表结构变更：新迁移脚本以 `V{下一版本号}__描述.sql` 提交到 `hrm-dev/hrm-server/src/main/resources/db/migration/mysql/`（PostgreSQL 同步维护），**严禁修改已执行的历史脚本**；
- 上线前先做数据库备份（见 8.4），再执行部署（应用重启时 Flyway 自动执行新脚本）；
- 后端 jar 回滚时注意结构兼容性（见 8.3）。

***

## 8. 回滚方案

### 8.1 应用快速回滚（jar 级，分钟级）

```bash
# 回滚到上一版（deploy.sh 自动：备份当前 → 恢复最近备份 → 重启 → 健康检查）
bash /www/wwwroot/kdyzgl-base/hrm-dev/deploy/deploy.sh --rollback

# 指定备份文件（ls /www/wwwroot/hrm-server/backup/ 查看可用版本）
bash /www/wwwroot/kdyzgl-base/hrm-dev/deploy/deploy.sh --rollback=hrm-server-20260906-153000.jar

# 纯手工方式（不通过脚本）：
cp /www/wwwroot/hrm-server/backup/hrm-server-<时间戳>.jar /www/wwwroot/hrm-server/hrm-server.jar
systemctl restart hrm-server        # 或宝塔面板重启 / kill 后 nohup 拉起
```

前端回滚：dist 无历史备份（可重新构建），如需回滚前端，`git checkout` 历史版本后重新执行 `--frontend-only`（8.2）。

### 8.2 代码版本回退（切历史 tag 重部署）

```bash
cd /www/wwwroot/kdyzgl-base
git fetch --tags
git checkout main
git reset --hard v1.0.0-一期员工管理      # 切到历史 tag（服务器无本地改动，reset 安全）

# 注意：直接跑 deploy.sh 会重新 checkout main 最新版，因此 tag 回退用以下方式部署
# （jar 模式 + 仅后端，脚本不会碰 git 分支）：
cd hrm-dev/hrm-server && mvn clean package -DskipTests && cd ../..
BACKEND_MODE=jar PREBUILT_JAR=hrm-dev/hrm-server/target/hrm-server-1.0.0.jar \
  bash hrm-dev/deploy/deploy.sh --backend-only
```

> 项目规则第 8 章：`main` 合并出错时用 git 回滚到历史 tag，**不要强行覆盖提交历史**（不用 push --force）；服务器侧本操作只影响部署副本。

### 8.3 数据库回滚注意（Flyway 无自动回滚）

Flyway 社区版只前向迁移（V 脚本只执行、不回退），处理策略：

| 场景 | 处理 |
| ---- | ---- |
| 新迁移有 bug，尚未在生产执行 | 直接修复脚本（未执行过，checksum 未记录），或删掉重新编号 |
| 新迁移已执行且结构有误 | **以新的 V 版本写修复脚本**（如 `V3__fix_xxx.sql`），随下个版本发布；不修改已执行的历史脚本 |
| 迁移后数据错乱（重大事故） | 停应用（`systemctl stop hrm-server` 或面板停止）→ 用最近的 mysqldump 备份恢复 `kdyzgl` 库 → 确认备份时点与 jar 版本匹配 → 重启应用 |
| `flyway_schema_history` 元数据异常（checksum 不匹配） | 见 FAQ Q2；`flyway repair` 只修元数据不动业务数据，谨慎使用 |
| jar 回滚但新迁移已执行 | 回滚 jar 前**必须**确认旧代码兼容新表结构（一期原则：只加列不删列、不改列类型，向后兼容） |

### 8.4 备份策略（回滚的底气）

```bash
# 推荐：宝塔面板 → 计划任务 → 备份数据库（每天凌晨，自动存 /www/backup/database/）

# 手工全量备份（大变更前必做）：
mysqldump -uroot -p --single-transaction --routines kdyzgl > /www/backup/kdyzgl_$(date +%F).sql
```

***

## 9. 常见问题排错（FAQ）

**Q1：启动报 8080 端口被占用（Address already in use）**

```bash
ss -lntp | grep 8080        # 查占用进程
ps -ef | grep '[h]rm-server'
```

常见原因：宝塔 Java 项目与 deploy.sh 的 nohup/systemd **双进程**（4.4 只能选一种托管方式）。解决：停掉多余一方（宝塔面板停止项目，或 `systemctl stop hrm-server` / `kill` nohup 进程），统一用一种方式；宝塔托管时脚本固定加 `RESTART_MODE=bt`。

**Q2：启动报 Flyway 校验失败（Validate failed: Migration checksum mismatch）**

原因：已执行的历史迁移脚本被改动。处理：

- 确认改动是否故意（脚本笔误修正等）：在测试库验证后，对该库执行 `flyway repair`（或按团队流程人工对齐 `flyway_schema_history` 的 checksum）；
- 非故意：`git checkout` 恢复脚本原样，重新部署；
- 预防：红线——**已执行的迁移脚本永不修改**，变更一律写新版本号（8.3）。

**Q3：启动报 Redis 连接失败（Unable to connect to Redis / NOAUTH）**

```bash
redis-cli ping                    # 未授权：NOAUTH Authentication required → 正常，说明有密码
redis-cli -a '<Redis密码>' --no-auth-warning ping   # 预期 PONG
systemctl status redis            # 或宝塔面板查看 Redis 状态
```

排查顺序：Redis 是否运行 → 外置配置 redis.password 是否与宝塔设置的 requirepass 一致 → bind 是否 127.0.0.1、后端配置 host 是否 127.0.0.1。

**Q4：启动报 MySQL 连接失败（Access denied / Public Key Retrieval）**

- `Access denied for user 'hrm_app'`：外置配置账号密码与 3.2 建的不一致，或账号 host 不匹配（JDBC 连的是 `127.0.0.1`，需存在 `'hrm_app'@'127.0.0.1'`）；
- `Public Key Retrieval is not allowed`：url 缺 `allowPublicKeyRetrieval=true`（4.2.1 坑二）；
- `Unknown database 'kdyzgl'`：3.1 建库未执行。

**Q5：页面能开但所有接口 502 Bad Gateway**

后端未启动或端口不对：

```bash
ss -lntp | grep 8080                                # 8080 是否在监听（且为 127.0.0.1）
curl -s -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8080/api/v1/auth/login -d '{}'
systemctl status hrm-server                          # 运行状态；日志 journalctl -u hrm-server -n 100
tail -n 100 /www/wwwroot/hrm-server/logs/stdout.log  # nohup 方式
```

OpenCloudOS/CentOS 且后端正常仍 502：检查 SELinux `getenforce`，若 Enforcing 放行 httpd 网络连接：`setsebool -P httpd_can_network_connect 1`。另确认 Nginx `proxy_pass` 指向 `http://127.0.0.1:8080`。

**Q6：前端页面刷新/直连子路由 404**

Nginx 站点配置缺 SPA 回退：补上 `location / { try_files $uri $uri/ /index.html; }` 后 `nginx -t && systemctl reload nginx`（5.3）。

**Q7：登录日志 IP 全是 127.0.0.1**

Nginx `/api/` 反代缺 `proxy_set_header X-Real-IP / X-Forwarded-For`（登录日志 login_ip 取 X-Forwarded-For 首个），按 5.3 修复后重载 Nginx，用新登录验证。

**Q8：服务器 mvn 打包用的 Java 不是 17**

```bash
mvn -v    # 看 Java version
ls /www/server/java/    # 找宝塔安装的 JDK17 实际路径
# 临时指定：JAVA_HOME=<JDK17路径> bash hrm-dev/deploy/deploy.sh
# 或干脆不用服务器打包：本地打包 → BACKEND_MODE=jar 模式（4.3 方式 B）
```

**Q9：时间显示差 8 小时（时区）**

确认三处一致：系统 `timedatectl`（Asia/Shanghai）、JVM 启动参数 `-Duser.timezone=Asia/Shanghai`（systemd 单元/deploy.sh 已内置，宝塔方式在启动参数追加）、MySQL 连接串 `serverTimezone=Asia/Shanghai`（内置 application.yml 已带，外置配置勿覆盖为其他值）。

**Q10：磁盘/日志治理**

```bash
df -h                                                     # 磁盘水位
du -sh /www/wwwroot/hrm-server/logs/ /www/wwwlogs/ /www/backup/   # 大头排查
ls /www/wwwroot/hrm-server/backup/                        # jar 备份超过 5 份可手工清理旧的
```

***

## 10. 附录

### 附录 A：deploy.sh 参数速查

| 参数/环境变量 | 说明 | 默认值 |
| ---- | ---- | ---- |
| `--branch <分支>` / `DEPLOY_BRANCH` | 部署分支 | main |
| `--mode <source|jar>` / `BACKEND_MODE` | 后端：服务器源码打包 / 预上传 jar | source |
| `PREBUILT_JAR` | jar 模式的预打包 jar 绝对路径 | — |
| `--backend-only` | 只部署后端 | 关 |
| `--frontend-only` | 只部署前端 | 关 |
| `--rollback[=备份文件名]` | 回滚后端 jar | — |
| `FRONTEND_MODE` | source=服务器构建 / dist=预上传 dist | source |
| `PREBUILT_DIST` | dist 模式的预构建目录绝对路径 | — |
| `RESTART_MODE` | auto / systemd / nohup / bt（宝塔托管时必须 bt） | auto |
| `SITE_DIR` | 前端站点根目录 | /www/wwwroot/hrm-admin |
| `JAVA_BIN` / `JAVA_OPTS` | java 路径与 JVM 参数 | java / -Xms512m -Xmx1024m -Duser.timezone=Asia/Shanghai |
| `NPM_REGISTRY` | npm 镜像 | https://registry.npmmirror.com |
| `HEALTH_TIMEOUT` | 健康检查等待秒数 | 180 |

### 附录 B：外置配置参数清单（与 env.example 对应）

| 参数 | env.example 占位 | 外置配置落点 | 备注 |
| ---- | ---- | ---- | ---- |
| 数据源账号 | `change_me_db_user` | spring.datasource.username = hrm_app | 3.2 创建 |
| 数据源密码 | `change_me_db_password` | spring.datasource.password | openssl rand -base64 24 |
| 数据源 url | （模板 url 缺 allowPublicKeyRetrieval） | 保持内置 url 即可 | 4.2.1 坑二 |
| Redis 密码 | `change_me_redis_password` | spring.data.redis.password | 2.3 设置的 requirepass |
| JWT 密钥 | `change_me_jwt_secret_please_generate_a_long_random_string` | jwt.secret | openssl rand -base64 48（≥32 字节） |
| JWT 有效期 | 86400 | jwt.expire | 秒，默认 24h |
| 员工导入初始密码 | （内置 application.yml 的 change_me_init_password） | hrm.employee-init-password | env.example 未列出此参数，勿遗漏 |
| admin 初始密码 | （db.md 5.2 的 change_me_admin_init_password） | 不落配置文件（散列在 V2 种子中） | 线下记录，首登改密后失效（6.1） |
| flyway | locations=classpath:db/migration（勿抄） | **不覆盖**，内置已配置 {vendor} | 4.2.1 坑一 |
| 文件存储 file.* | change_me_oss_* 等 | 一期不写入 | 二期启用再配 |
| 邮件 mail.* | change_me_mail_* | 一期不写入 | 三期启用再配 |
| LLM llm.* | change_me_llm_* | 一期不写入 | 二/三期启用再配 |

### 附录 C：交付文件索引

| 文件 | 说明 |
| ---- | ---- |
| [hrm-dev/deploy/deploy.sh](../deploy/deploy.sh) | 服务器部署/更新/回滚脚本（root 执行，幂等） |
| [hrm-dev/deploy/hrm-server.service](../deploy/hrm-server.service) | systemd 服务单元样例（与宝塔 Java 项目管理器二选一） |
| [hrm-dev/deploy/nginx.conf.example](../deploy/nginx.conf.example) | Nginx 站点配置样例（SPA 回退 + /api 反代 + gzip + 缓存） |
| [env.example](../../env.example) | 配置模板（仅参数名与 change_me 占位，可提交） |
| [docs/db.md](db.md) 5.2 | 初始 admin 账号与初始密码约定 |
| [docs/api.md](api.md) 4.1 | 登录接口出入参（curl 冒烟依据） |

### 附录 D：本手册产出自检记录（2026-09-06）

- 工程结构核对：后端 `hrm-dev/hrm-server`（Spring Boot 3.3.4 / Java 17 / jar=hrm-server-1.0.0.jar / 端口 8080）、前端 `hrm-dev/hrm-admin`（Vite6 / Node≥18 / history 路由）与手册路径一致；
- env.example 参数一一对应核对：见附录 B（含 3 处差异说明：flyway locations、url 参数、employee-init-password 仅存在于内置 application.yml）；
- deploy.sh 在 Windows 环境无法执行 `bash -n` 动态语法检查，已逐行静态审查（引用/转义/管道/异常分支）；
- 所有 IP、域名、密码均为占位符（`<...>` / `change_me_*`），无真实敏感信息；
- 遗留风险：无实际服务器环境，D01-D05 全部命令需在真实部署时按手册逐项执行验证（尤其宝塔面板字段名、宝塔 JDK 实际路径、curl 预期返回码三处与实际可能有细微出入，已标注「以实际为准」）。

***

## 11. 现网实况与 B7 三端入口（2026-09-25 回填）

> **本节为新增，不覆盖前文历史结论。** 回填来源：B7 发布切换执行记录（`update-log.md` 2026-09-25 条目）+ 主智能体 SSH 只读核实，作为事实基线。
> **与前文冲突处以本节为准**（尤其 §0.1 的 `/` → `/www/wwwroot/hrm-admin` 已在上文加「矛盾标注」）。
> **凭据纪律：** 本节不含任何真实凭据 / 口令 / 私钥；域名一律以「**生产主域名**」表述，不对本手册写入实际值。

### 11.1 三端入口与站点根（现网）

| 入口（带尾斜杠） | 端 | 站点根（宿主，容器内同路径） | 上线文件数 |
| ---- | ---- | ---- | ---- |
| `/web/` | 网页端（PC 管理后台） | `/data/www/download/hrm-clients/web` | 167 |
| `/staff/` | 员工端「驿站助手」 | `/data/www/download/hrm-clients/staff` | 80 |
| `/boss/` | 管理端「驿站精灵」 | `/data/www/download/hrm-clients/boss` | 97 |

- **域名根 `/`**：仍由容器 `hrm-demo-static`（演示静态站）提供**端选择页门户**；门户链接已指向 `/web/` `/staff/` `/boss/`（`deploy/docker-demo/portal.html` 与线上容器内 `index.html` 均已更新）。
- **为何站点根是 `/data/www/download/hrm-clients/`（而非 `/data/www/hrm-clients/`）**：`courier-nginx` 容器**只挂载** `/data/photos`、`/data/www/apk`、`/data/www/download`，原计划路径 `/data/www/hrm-clients` **在容器内不可见**；为**不新建挂载、不重建容器**（重建会中断 80/443），改用**已挂载且宿主/容器路径一致**的 `/data/www/download/hrm-clients`。该挂载为 `ro`，仅只读服务静态资源、无新增暴露面。
- 三端产物由 `build:prod`（剥离 Mock）构建后**本地剔除 `*.map`** 再上传（`map=0`）；生产不启用演示态 `build`。

### 11.2 变更载体（改 Nginx 前必读；核实方法）

| 项 | 值 |
| ---- | ---- |
| 容器 | `courier-nginx`（`nginx:1.25-alpine`，`0.0.0.0:80/443`） |
| 宿主配置文件 | `/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`（`ro` 挂载进容器 → 改宿主即改容器所见） |
| 生效方式 | 改宿主文件 → 容器内 `nginx -t` → 容器内 `nginx -s reload`（热重载，**不重启容器**） |
| 配置真源 | **不在本仓库**（仓库内 `deploy/nginx.conf.example` 与 `deploy/docker-demo/nginx.conf` 均非现网真源）→ **以运维记录为准** |

**载体核实方法（先核实再动手）：**

```bash
# ① 看容器挂载关系（确认宿主文件 → /etc/nginx/nginx.conf 的绑定）
docker inspect courier-nginx --format '{{range .Mounts}}{{.Source}} -> {{.Destination}} ro={{not .RW}}{{"\n"}}{{end}}'

# ② 看容器内【生效中】的完整配置（含 include，便于核对插入点与既有 location）
docker exec courier-nginx nginx -T
```

**单文件 bind mount 必须「原地改写」保 inode（重要坑）：** 宿主 `nginx.conf` 是**单文件**绑定挂载，容器绑定的是**该文件的 inode**。用 `mv`/`cp` 换成新文件会**换掉 inode**，容器侧仍指向旧 inode → **改动看不到**。因此编辑必须**原地写回同一 inode**，例如：

```bash
cp -a nginx.conf "nginx.conf.$(date +%Y%m%d%H%M%S)"   # 先备份
cat > nginx.conf <<'EOF'
...（含新增行的完整内容）
EOF
# 不要用： mv 新文件 nginx.conf  /  cp 新文件 nginx.conf（可能换 inode）
```

### 11.3 全站 `.map` 拒绝（D 档红线）与三端缓存头纪律

- **全站 `.map` 一律 404**（项目规则 §10.4 D 档红线：生产 `*.map` 不得公开）。三重防线：本地剔除 + 上传排除 + Nginx 拒绝；**新前缀 `^~` 的 `.map` 拦截必须写在各自 location 内部的局部正则**（server 级正则对 `^~` 前缀不生效）。
- **缓存头纪律：** `index.html` → `Cache-Control: no-store`（引用带 hash 的 chunk，缓存住会「新页面引旧 chunk」）；`assets/*`（带 hash）→ `public, max-age=31536000, immutable`。
- **缺失资源必须 404，不得回退 HTML**（否则 HTML 被当 ES 模块解析 → 整页白屏）；仅**无扩展名**的 history 深链才回退到 `/<prefix>/index.html`。

### 11.4 回滚（一键）

> B7 变更前的备份为 `nginx.conf.20260925121157`（同目录）。回滚即还原备份 + 校验 + 热重载 + 删除新增站点根。

```bash
# ① 还原备份（原地改写保 inode，故用 cat 覆盖而非 mv）
cat /data/www/kdyzzhxt/courier-server/nginx/nginx.conf.20260925121157 \
  > /data/www/kdyzzhxt/courier-server/nginx/nginx.conf
# ② 语法校验（必须过再 reload）
docker exec courier-nginx nginx -t
# ③ 热重载（不要 docker restart）
docker exec courier-nginx nginx -s reload
# ④ 删除新增站点根（属 C 档，仅在确认不再需要新入口时执行）
rm -rf /data/www/download/hrm-clients/web /data/www/download/hrm-clients/staff /data/www/download/hrm-clients/boss
```

- 回滚**不涉数据变更、不重建镜像/容器**；仅新入口消失，既有 location 未受影响。
- 回滚动作与原因须登记 `update-log.md` / `SESSION-STATE.md`（由主智能体执行）。

### 11.5 既有缺陷登记（**非本次引入；2026-09-25 已修复**）

| 路径 | 现象 | 根因（已定位） | 状态 |
| ---- | ---- | ---- | ---- |
| `/admin/` | ~~500~~ → **301** | `alias` + `try_files` 形成内部重定向环（`rewrite or internal redirection cycle`），且容器内 `/usr/share/nginx/html/admin` 目录不存在 | **已修**（2026-09-25，C 档）：该 location 改为 `return 301 /web/`（一期 PC 端已由 `apps/web` 承接）；备份 `nginx.conf.20260925123334` |
| `/download` | ~~404~~ → **200** | `/data/www/download/index.html` 不存在 | **已修**（2026-09-25，**只增**宿主 `/data/www/download/index.html` 下载页，**未改 Nginx**） |

> 修复验证：`/admin/`、`/admin/employee`、`/admin/index.html` 均 301；`/download` 200；既有 location 行为逐条不变，`.map` 仍 404。**注意**：`nginx -s reload` 后新旧 worker 并存，变更后须**复测一次**再判定。
> 另：B7 之前**全站 `.map` 原本无任何拦截**，已在 Nginx 侧补齐（见 §11.3）。

### 11.6 B8 退役判定期（与本节相关）

- **起点** = B7 上线日 **2026-09-25**；依 ADR §3.3，退役须**三项触发条件全部满足**（① 新子路径入口连续 1 个发布周期无 P0 回滚；② 旧入口在此周期末访问量归零或低于阈值；③ 旧壳 APK 完成一个发布周期升级提示）。
- 当前**不满足**（①③ 需 1 个发布周期）→ **旧入口 `mobile.html?as=` 与 `as` 兼容读保留、不得下线**。
- 迁移期**冻结演示站发布**（仅 P0 修复，且发布后须回归旧入口）——详见 `deploy/docker-demo/deploy-demo.sh` 头部横幅与 ADR §6.1 R新-1。



