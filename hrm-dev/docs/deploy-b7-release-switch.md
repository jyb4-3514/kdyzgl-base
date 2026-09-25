# 部署与回滚手册：B7「发布切换」（★ 唯一切换点）

- 文档类型：部署与回滚手册（L6 链路产物：部署/回滚方案 → 手册 → 主智能体授权后执行）
- 编制：运维工程师 `express-station-ops-engineer`
- 日期：2026-09-25
- 上游真源：`hrm-dev/docs/adr-structure-migration.md` §3.7 B7 行、§5.4、§6.1、§3.3、§2.3；安全结论 `hrm-dev/docs/security-release-switch-review.md`（**有条件放行**，必改项 M1–M7、待核实项 V1–V6）
- 现网事实基线来源：主智能体 SSH 只读核实结论（本手册直接引用为事实，未自行读取生产）
- **状态：未执行。本手册内所有服务器命令均未在本地执行；任何实际执行须先经主智能体 §10.3 三步授权（本变更属 C 档）。**
- 纪律：本手册内**不含**真实凭据 / 密钥 / 服务器 IP / 生产域名（按 §7.2 / §12.9，一律以 `<生产主机>` / `<生产主域名>` 占位）。

---

## 1. 范围与原则

### 1.1 变更范围（只此一项）

在现网 `courier-nginx` 的 **443 server 块内**，**新增**三端静态入口：

| 入口前缀 | 端 | 本地工程 | `vite.base` |
| - | - | - | - |
| `/web/` | 网页端（PC 管理后台） | `hrm-dev/hrm-clients/apps/web` | `/web/`（history 路由） |
| `/staff/` | 员工端「驿站助手」H5 | `hrm-dev/hrm-clients/apps/staff-h5` | `/staff/`（hash 路由） |
| `/boss/` | 管理端「驿站精灵」H5 | `hrm-dev/hrm-clients/apps/boss-h5` | `/boss/`（hash 路由） |

统一子路径口径见 ADR §3.3 / §7.2 B-5（迁移期与终态同用子路径，免二次切换）。

### 1.2 硬原则

1. **只增不改**：**不动** `location = /`、`location /`，以及既有全部 location（`/admin/`、`/hrm-api/`、`/api/`、`/photos/`、`/apk/`、`= /download`、`/health`、`= /hrm-api/v1/work-orders/auto-dispatch` 的 403、80 server 的 `^~ /.well-known/acme-challenge/`）。任一既有行被改动即视为越界，须回滚。
2. **新站点根与既有 `location /` 的 root/alias 隔离**（B7 验收 ⑧）：新根独立成目录，不复用演示站根，防产物互相覆盖。
3. **`.map` 不得公开下载**（§10.4 D 档红线 / 安全 M1）：三重防线（本地剔除 + 上传排除 + Nginx 拒绝）。
4. **缺失资源必须 404，不得回退 HTML**（否则 HTML 被当 ES 模块解析 → 整页白屏）。
5. **可逆**：变更前备份 `nginx.conf`，回滚 = 还原备份 + reload。

### 1.3 变更载体（= 安全必改项 6 的答案）

| 项 | 值 |
| - | - |
| 宿主配置文件 | `/data/www/kdyzzhxt/courier-server/nginx/nginx.conf` |
| 容器内挂载点 | `/etc/nginx/nginx.conf`（**`ro` 只读挂载**，改宿主即改容器所见） |
| 生效方式 | 改宿主文件 → 容器内 `nginx -t` 校验 → 容器内 `nginx -s reload`（热重载，**不重启容器**） |
| 现网语法基线 | `docker exec courier-nginx nginx -T` 当前语法通过 |

> 现网还挂载了 `/data/www`（证据：既有 `/apk/` → `alias /data/www/apk/`、`= /download` → `root /data/www/download` 均可用），故新增站点根选在 `/data/www/hrm-clients/` 下，可被容器以**同路径**读取。

---

## 2. 产物准备（本地侧，A 档）

### 2.1 前置

本机 Node 24.19.0 / npm 11.17.0 可用；三端均在 `hrm-dev/hrm-clients` workspace 内。

### 2.2 构建生产态产物（无 Mock）

```powershell
cd d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-clients
npm ci
npm run build:prod -w @kdyzgl/web
npm run build:prod -w @kdyzgl/staff-h5
npm run build:prod -w @kdyzgl/boss-h5
```

**期望输出**：三段均出现 `✓ built in ...` 且无 error；产物落 `apps/{web,staff-h5,boss-h5}/dist`。

> 注：`build` = 演示态（含 Mock），**不得用于生产**；生产只用 `build:prod`（`--mode production`，剥离 Mock）。

### 2.3 剔除 `*.map`（必做，M1 第一道防线）

`build:prod` 的 `sourcemap: 'hidden'` **仍会生成 `.map` 文件**（安全报告附录 A1：web 104 / staff 43 / boss 51），仅去掉了 JS 内的 `sourceMappingURL` 注释。必须在上传前本地剔除：

```powershell
cd d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-clients
"apps/web","apps/staff-h5","apps/boss-h5" | ForEach-Object {
  Get-ChildItem -Path "$_\dist" -Recurse -Filter *.map -File | Remove-Item -Force
}
```

**期望输出**：无输出（全部删除）。复核：

```powershell
"apps/web","apps/staff-h5","apps/boss-h5" | ForEach-Object {
  $n = (Get-ChildItem -Path "$_\dist" -Recurse -Filter *.map -File | Measure-Object).Count
  "$_ : map=$n"
}
```

**期望输出**：三行均为 `map=0`。

### 2.4 产物自检清单（每端逐条）

```powershell
cd d:\Users\16626\Desktop\kdyzgl-base\hrm-dev\hrm-clients
$ends = @{ web = "apps/web"; "staff-h5" = "apps/staff-h5"; "boss-h5" = "apps/boss-h5" }
foreach ($k in $ends.Keys) {
  $dist = "$($ends[$k])\dist"
  $assets = "$dist\assets"
  $mockHit = (Get-ChildItem $assets -File | Where-Object { $_.Name -match 'install|db-|mock' } | Measure-Object).Count
  $mapHit  = (Get-ChildItem $dist -Recurse -Filter *.map -File | Measure-Object).Count
  $files   = (Get-ChildItem $dist -Recurse -File | Measure-Object).Count
  $sizeMB  = [math]::Round(((Get-ChildItem $dist -Recurse -File | Measure-Object Length -Sum).Sum/1MB),2)
  "$k : mock命中=$mockHit map=$mapHit 文件数=$files 总大小=${sizeMB}MB"
  "  index.html 存在: " + (Test-Path "$dist\index.html")
}
```

**期望输出（逐条判定）**：

- `mock命中=0`（`install|db-|mock` 命中 = 0）
- `map=0`
- `index.html 存在: True`
- 文件数 / 总大小记录在案（作变更前后对照基线）
- 可选校验和（留痕）：`Get-FileHash "$dist\index.html" -Algorithm SHA256`

**额外核对**：`dist/index.html` 内资源路径前缀须分别为 `/web/`、`/staff/`、`/boss/`（与 `vite.base` 一致，ADR §3.3「基址一致性」易错点）：

```powershell
Select-String -Path apps/web/dist/index.html,apps/staff-h5/dist/index.html,apps/boss-h5/dist/index.html -Pattern '/(web|staff|boss)/assets/' | Select-Object -First 6
```

**期望输出**：web 命中 `/web/assets/`、staff 命中 `/staff/assets/`、boss 命中 `/boss/assets/`。

---

## 3. 宿主落点规划（B7 验收 ⑧ 隔离）

| 端 | 宿主站点根（新增，独立） | 对应 URI 前缀 |
| - | - | - |
| web | `/data/www/hrm-clients/web` | `/web/` |
| staff | `/data/www/hrm-clients/staff` | `/staff/` |
| boss | `/data/www/hrm-clients/boss` | `/boss/` |

**隔离声明：**

- 演示站当前由**演示容器 `hrm-demo-static` 自身**提供（`location = /` 与 `location /` proxy 到 `hrm-demo-static:80`），**不使用**宿主 `/data/www/hrm-demo`；新站点根与之物理隔离（父目录 `/data/www/hrm-clients/` 独立），**不共用** `location /` 的 root/alias。
- 目录名 `web`/`staff`/`boss` **刻意与 URI 前缀一致**，以配合 `root` 指令（见 §5 为何用 `root`）。

**属主 / 权限建议：**

```bash
# 创建（幂等；属主为 root，容器 worker 以只读方式访问）
install -d -m 755 -o root -g root /data/www/hrm-clients /data/www/hrm-clients/web /data/www/hrm-clients/staff /data/www/hrm-clients/boss
```

**期望输出**：`ls -ld /data/www/hrm-clients/*` 显示三目录 `drwxr-xr-x root root`。

- 目录 `755`、文件 `644`（世界可读；容器 worker 需只读访问）。
- **站点根内不得存放任何凭据 / 密钥 / 业务数据 / `*.map`**（§7.2 / §12.9 / §10.4）。
- 容器内可见性前置校验（V4 残余项落地）：

```bash
docker exec courier-nginx ls -ld /data/www/hrm-clients/web /data/www/hrm-clients/staff /data/www/hrm-clients/boss
```

**期望输出**：三个目录均列出（证明 `/data/www` 已挂载入容器、路径一致）。**若容器内不可见 → 停止，按 §7 回报主智能体**（不可自行改挂载，属新增挂载 = 改容器编排）。

---

## 4. 上传方式

### 4.1 为何不用「服务器 `git pull`」

服务器代码**只来源于 `git pull`**（§5 红线）——指**业务源码**。本次上传物是三端 `build:prod` **构建产物**（`dist/**`），属**部署资产**、且被 `.gitignore` 忽略，**不进仓库**；故必须由构建机上传，不得走 `git pull`，也不得在服务器编辑任何业务源码。

### 4.2 上传命令（推荐 `rsync`，幂等 + 排除 `.map`）

```bash
# 在本地（Windows PowerShell 下可用 WSL 或 Git-Bash；路径按需转换）
rsync -avz --delete --exclude='*.map' \
  hrm-dev/hrm-clients/apps/web/dist/      <SSH_USER>@<生产主机>:/data/www/hrm-clients/web/
rsync -avz --delete --exclude='*.map' \
  hrm-dev/hrm-clients/apps/staff-h5/dist/ <SSH_USER>@<生产主机>:/data/www/hrm-clients/staff/
rsync -avz --delete --exclude='*.map' \
  hrm-dev/hrm-clients/apps/boss-h5/dist/  <SSH_USER>@<生产主机>:/data/www/hrm-clients/boss/
```

**期望输出**：每组传送完成后打印 `sent ... bytes  received ... bytes`，无 `rsync error`。

- 无 `rsync` 时的等价 `scp`（**注意 `--delete` 效果缺失，首次部署可接受**）：

```bash
scp -r hrm-dev/hrm-clients/apps/web/dist/*      <SSH_USER>@<生产主机>:/data/www/hrm-clients/web/
scp -r hrm-dev/hrm-clients/apps/staff-h5/dist/* <SSH_USER>@<生产主机>:/data/www/hrm-clients/staff/
scp -r hrm-dev/hrm-clients/apps/boss-h5/dist/*  <SSH_USER>@<生产主机>:/data/www/hrm-clients/boss/
```

### 4.3 上传后文件数与 `.map` 核对

```bash
for d in web staff boss; do
  echo -n "$d: files="; find /data/www/hrm-clients/$d -type f | wc -l
  echo -n "$d: map=";   find /data/www/hrm-clients/$d -name '*.map' -type f | wc -l
done
```

**期望输出**：`files=` 与 §2.4 本地记录一致；`map=0`（三端）。

---

## 5. Nginx 变更片段（**只增**，可直接插入）

### 5.1 设计说明（为何这样写）

1. **`^~` 前缀修饰符（安全 M2）**：`location ^~ /web/` 等，确保「最长前缀命中后**不再评估 server 级正则 location**」，防既有正则（如 `~* \.*\.(js|css)?$`）用**既有 root** 抢占 `/web/assets/*` 而破坏隔离。
2. **用 `root` 而非 `alias`（安全 M2）**：`alias` + `try_files` 存在经典尾斜杠错配坑；`root` 语义直白（`URI` 直接拼到 `root`），且目录名已与前缀一致（§3）。
3. **`.map` 拒绝分两层**：
   - **每个新 location 内部**一个局部 `location ~* \.map$ { return 404; }`（安全 M1 原文要求，**仅作用于本前缀**）；
   - **另加 server 级** `location ~* \.map$ { return 404; }`（本手册第 5 条要求），做既有路径的纵深防御。
   - **优先级关键**：因新 location 用 `^~`，**server 级正则不会作用于 `/web|/staff|/boss` 内部**——故新前缀的 `.map` 拦截**必须**靠 location 内部的局部正则（这也是 M1 要求"必须在各自 location 内部"的根因）。两条并存、各管一段，**不互替**。
4. **正则 location 顺序**：nginx 在同级**正则 location 间按出现顺序**匹配。故 server 级 `~* \.map$` 必须**置于任何既有正则 location 之前**才对该（非 `^~`）路径生效。按主智能体备案的现网实况，443 server 内**未见既有正则 location**（见 §12 事实性纠正 ①）；若现网确有 `location ~* .*\.(js|css)?$`（`expires 12h`），则 `~* \.map$` 必须写在它**之前**。前缀 location 之间的书写位置不影响该规则，但为可读性，统一插入在 `location /admin/` 块之后。
5. **`^~` 与既有 `location /` 的优先级（证明不误捕）**：
   - nginx 前缀 location 采用**最长前缀匹配**。`/web/`、`/staff/`、`/boss/` 均长于 `/`，故只对这三个前缀生效；`/` 仍只接住其余所有路径。
   - 既有 `/admin/`、`/api/`、`/hrm-api/`、`/photos/`、`/apk/`、`= /download`、`/health` 与三个新前缀**无前缀重叠**，其请求仍命中各自原 location，行为不变。
   - `location = /` 为**精确匹配**，仅命中恰好 `/`，不受新前缀影响。
6. **缺失资源 404（安全 M3）**：带扩展名的分支一律 `try_files $uri =404`，**不得**回退 HTML；仅无扩展名路径（history 深链）才 `try_files ... /<prefix>/index.html`。
7. **缓存纪律（ADR §3.3）**：`index.html` → `Cache-Control: no-store`；`assets/*`（带 hash）→ `public, max-age=31536000, immutable`。
8. **安全响应头（安全 M5）**：三端各 location 内 `nosniff` + `Referrer-Policy`。

> ⚠️ **add_header 继承陷阱（对应残余项 V6）**：nginx 中若某层级定义了 `add_header`，则**不再继承**上级同域 `add_header`。若现网 443 server 级已存在安全头，本片段会**在三个新 location 内屏蔽**它们 → **必须先把 server 级既有 `add_header` 原样补进三个新 location**，再上线（见 §11 残留项 R-V6）。

### 5.2 可直接插入的片段（全站 `.map` 拒绝 + 三端入口）

> 插入锚点：`location /admin/ { ... }` 块**之后**。查锚点行号：

```bash
grep -n "location /admin/" /data/www/kdyzzhxt/courier-server/nginx/nginx.conf
```

```nginx
    # ===== B7 新增（只增不改）开始 =====
    # [1] 全站 .map 拒绝（纵深防御；须位于任何既有正则 location 之前）。
    #     注意：对使用 ^~ 的 /web|/staff|/boss 不生效，其 .map 由各自 location 内局部正则拦截。
    location ~* \.map$ {
        return 404;
    }

    # [2] 网页端（PC 管理后台）入口；history 路由 + base:/web/ → 需 SPA 回退
    location ^~ /web/ {
        root /data/www/hrm-clients;
        index index.html;

        # 新前缀内 .map 拒绝（M1：必须在带扩展名分支之前）
        location ~* \.map$ { return 404; }

        # 带 hash 构建产物：长缓存
        location ^~ /web/assets/ {
            add_header Cache-Control "public, max-age=31536000, immutable" always;
            add_header X-Content-Type-Options "nosniff" always;
            add_header Referrer-Policy "strict-origin-when-cross-origin" always;
            try_files $uri =404;
        }

        # 其它带扩展名资源：缺失必须 404，不得回退 HTML
        location ~* \.[a-z0-9]+$ {
            add_header X-Content-Type-Options "nosniff" always;
            add_header Referrer-Policy "strict-origin-when-cross-origin" always;
            try_files $uri =404;
        }

        # 无扩展名（history 深链）→ 回退本入口自身 index.html
        try_files $uri $uri/ /web/index.html;

        # 入口 HTML 不缓存（M3）
        add_header Cache-Control "no-store" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    }

    # [3] 员工端「驿站助手」入口；hash 路由（不依赖服务端回退），try_files 仅为边界兜底
    location ^~ /staff/ {
        root /data/www/hrm-clients;
        index index.html;

        location ~* \.map$ { return 404; }

        location ^~ /staff/assets/ {
            add_header Cache-Control "public, max-age=31536000, immutable" always;
            add_header X-Content-Type-Options "nosniff" always;
            add_header Referrer-Policy "strict-origin-when-cross-origin" always;
            try_files $uri =404;
        }

        location ~* \.[a-z0-9]+$ {
            add_header X-Content-Type-Options "nosniff" always;
            add_header Referrer-Policy "strict-origin-when-cross-origin" always;
            try_files $uri =404;
        }

        try_files $uri $uri/ /staff/index.html;

        add_header Cache-Control "no-store" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    }

    # [4] 管理端「驿站精灵」入口；hash 路由（同 staff）
    location ^~ /boss/ {
        root /data/www/hrm-clients;
        index index.html;

        location ~* \.map$ { return 404; }

        location ^~ /boss/assets/ {
            add_header Cache-Control "public, max-age=31536000, immutable" always;
            add_header X-Content-Type-Options "nosniff" always;
            add_header Referrer-Policy "strict-origin-when-cross-origin" always;
            try_files $uri =404;
        }

        location ~* \.[a-z0-9]+$ {
            add_header X-Content-Type-Options "nosniff" always;
            add_header Referrer-Policy "strict-origin-when-cross-origin" always;
            try_files $uri =404;
        }

        try_files $uri $uri/ /boss/index.html;

        add_header Cache-Control "no-store" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    }
    # ===== B7 新增结束 =====
```

> 若现网 443 server 级已有 `add_header`（V6 定论为"有"），把它们的**原值**追加进上面三个 location（含各自的嵌套 location）后再上线。

---

## 6. 执行步骤（编号；每步命令 + 期望输出）

> **全部为服务器侧操作；未执行，执行须 C 档授权。**

**① 备份 `nginx.conf`（先备份后变更，§5 红线）**

```bash
cd /data/www/kdyzzhxt/courier-server/nginx
TS=$(date +%Y%m%d%H%M%S)
cp -a nginx.conf "nginx.conf.$TS"
ls -l nginx.conf "nginx.conf.$TS"
```

**期望输出**：列出两个文件，`nginx.conf.<时间戳>` 与 `nginx.conf` 大小一致、时间戳为当前。记录该 `TS`（回滚依赖它）。

**② 前置校验（容器内语法基线 + 站点根可见性）**

```bash
docker exec courier-nginx nginx -t
docker exec courier-nginx ls -ld /data/www/hrm-clients/web /data/www/hrm-clients/staff /data/www/hrm-clients/boss
```

**期望输出**：`nginx: configuration file /etc/nginx/nginx.conf test is successful`；三目录均列出。任一项失败 → 停手回报。

**③ 上传产物并核对文件数（见 §4）**

上传后执行 §4.3 的 `files=`/`map=0` 核对。**期望**：三端文件数与本地一致、`map=0`。

**④ 应用只增片段**

用编辑器在宿主 `/data/www/kdyzzhxt/courier-server/nginx/nginx.conf` 的 `location /admin/ { ... }` 块**之后**插入 §5.2 片段（**仅插入，不修改任何既有行**）。

```bash
grep -n "B7 新增（只增不改）开始" /data/www/kdyzzhxt/courier-server/nginx/nginx.conf
git -C /data/www/kdyzzhxt status --porcelain   # 若为 git 目录，确认未误改其它文件（可选）
```

**期望输出**：`grep` 命中 1 行（片段已就位）。

**⑤ 语法校验（必须先过再 reload）**

```bash
docker exec courier-nginx nginx -t
```

**期望输出**：`... test is successful`。**失败 → 立即按 §9 回滚（还原备份），不得 reload。**

**⑥ 热重载（不要 `docker restart`）**

```bash
docker exec courier-nginx nginx -s reload
```

**期望输出**：无报错（reload 无输出即成功）。若 reload 失败（非语法问题），**先按 §9 回滚**，再回报主智能体；**不得**用 `docker restart` 规避（会中断既有站点、且属额外变更）。

**⑦ 逐条验证（见 §7 与 §8）**

---

## 7. 验证清单（curl；逐条期望码）

> 占位 `<生产主域名>`；本机无服务器访问，命令未执行。**变更前后各跑一次并落盘比对**（"既有行为不变"以**基线一致**为准，非固定码）。

### 7.1 变更前基线（务必先做）

```bash
BASE=/tmp/b7-baseline.txt
for u in / /admin/ /api/ /hrm-api/ /photos/ /apk/ /download /health "/.well-known/acme-challenge/probe"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "https://<生产主域名>$u")
  echo "$u $code"
done | tee "$BASE"
```

**期望输出**：8 行 URL + 状态码。保存 `$BASE` 供 §7.4 比对。

### 7.2 新入口（B7 验收 ①②③④⑤⑦）

```bash
# 三端入口 200
for u in /web/ /staff/ /boss/; do curl -s -o /dev/null -w "$u %{http_code}\n" "https://<生产主域名>$u"; done
# 期望：三条均 200

# web history 深链刷新落自身入口（200，且为 HTML）
curl -s -o /dev/null -w '/web/dashboard %{http_code}\n' "https://<生产主域名>/web/dashboard"
# 期望：200

# 取真实 js 名并探测 .map 404
JS=$(curl -s "https://<生产主域名>/web/" | grep -oE '/web/assets/[A-Za-z0-9._-]+\.js' | head -1)
echo "js=$JS"
curl -s -o /dev/null -w 'map=%{http_code}\n' "https://<生产主域名>${JS}.map"
# 期望：map=404

# 缺失资源 404 且为 404（不得 200 回退 HTML）
curl -s -o /dev/null -w 'missing=%{http_code}\n' "https://<生产主域名>/staff/assets/nonexist-abc123.js"
# 期望：missing=404

# index.html 不缓存
curl -sI "https://<生产主域名>/staff/" | grep -i cache-control
# 期望：含 no-store

# 安全响应头（M5）
curl -sI "https://<生产主域名>/boss/" | grep -iE 'x-content-type-options|referrer-policy'
# 期望：两项均出现

# HTTPS 强制（80 → 301，依赖现网现状；现网 80 server 其余路径 return 301 已具备）
curl -sI "http://<生产主域名>/web/" | head -1
# 期望：HTTP/1.1 301（或 308）

# 旧入口并存可达（M4 / 验收 ⑦）
for u in /mobile.html /pc.html; do curl -s -o /dev/null -w "$u %{http_code}\n" "https://<生产主域名>$u"; done
# 期望：均 200
```

### 7.3 穿越实测（安全 M6）

```bash
curl -s -o /dev/null -w '%{http_code}\n' --path-as-is 'https://<生产主域名>/staff/../index.html'
curl -s -o /dev/null -w '%{http_code}\n' --path-as-is 'https://<生产主域名>/staff/%2e%2e/'
curl -s -o /dev/null -w '%{http_code}\n' --path-as-is 'https://<生产主域名>/staff/..%2f..%2fetc/passwd'
```

**期望输出**：三行均 **非 200**（400/403/404）。

### 7.4 既有 location 行为逐条不变（B7 验收 ⑥）

```bash
for u in / /admin/ /api/ /hrm-api/ /photos/ /apk/ /download /health; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "https://<生产主域名>$u")
  echo "$u $code"
done > /tmp/b7-after.txt
diff /tmp/b7-baseline.txt /tmp/b7-after.txt && echo "既有 location 行为一致 ✅"
```

**期望输出**：`既有 location 行为一致 ✅`（无 diff）。

**ACME 白名单优先性（80）**：

```bash
curl -sI "http://<生产主域名>/.well-known/acme-challenge/probe" | head -1
```

**期望输出**：**404**（由 `root /var/www/le-challenges` 提供，**不得**是 301——301 说明 ACME location 被抢，属回归）。

---

## 8. 切换期观测判据与回滚触发条件

**数据源**：现网 `courier-nginx` access/error log（**不引入新监控组件**，ADR §3.7）。

**基线方法**：变更前取 30 分钟窗口的新入口无请求（新前缀日志为空），故以**变更后即时**为准；既有路径以 §7.1 基线为准。

**任一命中即按 §9 回滚：**

| # | 观测判据 | 判定方法（示例命令，日志路径按现网实际） | 阈值 |
| - | - | - | - |
| 1 | 新入口 4xx/5xx 率超阈值 | `docker logs --since 10m courier-nginx 2>&1 \| grep -E 'GET /(web\|staff\|boss)/'` 中 `" 4\d\d "`/`" 5\d\d "` 占比 | 由运维据现网基线定；无基线时暂定 **>1% 且绝对值 >20 次**，并以人工确认 |
| 2 | 深链刷新失败（落不到自身入口） | 手测 + 日志中 `/web/dashboard`、`/web/*` 无扩展名 404 | 出现任一 404 即回滚 |
| 3 | `.map` 可公开下载探测命中 | §7.2 的 `${JS}.map` 返回 200 | 出现 200 即回滚（**D 档红线**） |
| 4 | 资源缺失回退 HTML 致白屏 | §7.2 `missing` 返回 200；或浏览器控制台报 `Unexpected token '<'` | 出现即回滚 |

> 判定人：运维；触发回滚前**先回报主智能体**，除"命中即回滚"条款外不擅自扩大变更。

---

## 9. 回滚手册（编号步骤）

> 回滚点（ADR §6）：删除新增 location + 还原 `nginx.conf.<时间戳>` + `nginx -t` + reload。**可逆**：仅新入口消失，既有 location 未受影响。**留痕**：回滚动作与原因登记 `update-log.md` / `SESSION-STATE.md`（由主智能体执行）。

**R① 还原备份配置**

```bash
cd /data/www/kdyzzhxt/courier-server/nginx
cp -a "nginx.conf.$TS" nginx.conf        # $TS 取自 §6 步骤①
```

**期望输出**：命令成功；`nginx.conf` 内容与备份一致。

**R② 语法校验**

```bash
docker exec courier-nginx nginx -t
```

**期望输出**：`... test is successful`。失败 → 停手回报（此时尚未 reload，线上仍跑旧配置）。

**R③ 热重载**

```bash
docker exec courier-nginx nginx -s reload
```

**期望输出**：无报错。

**R④ 复验既有 location 与旧入口（逐条）**

```bash
for u in / /admin/ /api/ /hrm-api/ /photos/ /apk/ /download /health; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "https://<生产主域名>$u")
  echo "$u $code"
done > /tmp/b7-rollback.txt
diff /tmp/b7-baseline.txt /tmp/b7-rollback.txt && echo "回滚后既有行为一致 ✅"

for u in /web/ /staff/ /boss/; do curl -s -o /dev/null -w "$u %{http_code}\n" "https://<生产主域名>$u"; done
# 期望：新入口不再由新配置提供（回落到既有 location / 的演示站，通常 404 或演示站首页）
```

**R⑤ 删除新增站点根目录（清理回滚，属 C 档）**

> 仅在确认不再需要新入口时执行；**禁用 `rm -rf` 于运行目录/演示站根**（D 档）。

```bash
rm -rf /data/www/hrm-clients/web /data/www/hrm-clients/staff /data/www/hrm-clients/boss
```

**期望输出**：三目录删除。**保留** `nginx.conf.<时间戳>` 备份与 `/data/www/hrm-clients` 父目录（如需彻底清理父目录，另行授权）。

**R⑥ 记录**：备份文件名、`TS`、回滚原因、复验结果 → 交主智能体登记。

---

## 10. 权限档位声明

| 项 | 内容 |
| - | - |
| 档位 | **C 档**（改现网 Nginx 配置：新增 location + 容器 reload；改宿主配置文件） |
| 执行前置 | **须主智能体按 §10.3 三步授权后由运维执行**（本手册仅为方案/手册，未执行） |
| 三步授权表单（安全报告 §5.3，缺一不可） | ① **影响范围与是否涉生产数据**：仅现网 `courier-nginx` 新增 3 条 location + 1 条 server 级 `.map` 拒绝；**不涉生产数据**（无 DB/无数据面改动），新增站点根为静态目录 ② **是否可逆**：可逆（删新 location + 还原 `nginx.conf.<时间戳>` + reload） ③ **回滚步骤与验证方法**：见 §9；复验既有 location 一致 + 新入口消失 + 登记 `update-log.md` |
| 高风险强制措辞（须原文写入授权单） | **M1（`.map` 公开）命中项目规则 §10.4 D 档红线。若本批无法落地并实测 `.map` 拒绝，则不得判定上线成功；此情形不得由主智能体直接放行，须升级用户裁定。** |
| 不得放行的情形 | V1–V4 任一未闭环（尤其 V2 现网正则 location 未清、V4 站点根落点未定）→ 不得实施；M1 未落地/未验证 → 高风险升级用户裁定 |
| D 档禁项（本手册内**不得**出现） | `rm -rf` 运行目录 / 演示站根、在服务器编辑业务源码、`push --force` / `reset --hard` / `clean -f`、把生产 `*.map` 部署为可公开访问 |
| 边界（本手册不做） | 不做安全评估（A08：技术安全评估归网络安全工程师，结论已出具；操作安全评估与授权归主智能体）；不调 MCP（已由主智能体提供现网事实）；只产出本手册文件 |

---

## 11. 风险与假设登记

| # | 类型 | 内容 | 缓解 / 处置 |
| - | - | - | - |
| R新-1 | 已登记风险 | **演示站产物被覆盖 → 旧入口失效**：B7 后若重新构建/发布 `hrm-demo` 并覆盖旧 `dist`（或演示容器重建拉新产物），`location /` 的 `try_files` 回退目标失效，破坏"零中断"前提 | **迁移期冻结 `hrm-demo` 发布**（仅 P0 修复且须回归旧入口）；旧产物只读快照（备份 `dist` + 冻结镜像 tag `hrm-demo-static:20260925-allowance`）；**容器不重建**；变更窗口内禁用/圈定 `deploy.sh` 前端同步目标 |
| R新-2 | 已登记风险 | **无 CI 通道**，构建/壳验收无法机器化 | 本批不涉壳；构建验收为本地实跑 + 产物静态检索（§2.4） |
| R-未登记A | 安全报告补充 | 旧入口可用性**同时依赖演示容器存活**（`location /` proxy 到 `hrm-demo-static`） | 冻结范围含"容器不重建 + 镜像 tag"（并入 R新-1 缓解） |
| R-未登记B | 安全报告补充 | 仓库 `deploy.sh` 前端同步默认目标是既有站点，误跑可能覆盖旧产物 | 变更窗口内**显式禁用/圈定** `deploy.sh` 范围 |
| R-V6 | 待核实残余 | 现网 443 server 级是否已有 `add_header`：若有，新 location 定义 `add_header` 后**不再继承**，会屏蔽既有安全头 | 按 V6 核实现网现状；若有，将原值**原样补进**三端 location（含嵌套 location）再上线 |
| R-V5 | 待核实残余 | 证书路径 / TLS 版本 / 现网是否 HTTP→HTTPS 强制 | 现网 80 server 已 `return 301`（强制成立，验收 ⑤ 可达）；证书与 TLS 版本**不在本批改动范围**，仅登记 |
| R-根路径 | 边界 | 请求 `/web`（无尾斜杠）不匹配 `location ^~ /web/`，会落既有 `location /`（演示站） | 对外入口统一以 **`/web/` `/staff/` `/boss/`（带尾斜杠）** 发布；如需 `/web` 301 到 `/web/`，属**额外只增项**，另行授权 |
| 假设 H5 | 已闭环 | 现网 `location = /` 与 `location /` 由演示容器提供（非 `deploy.md` 所述 `hrm-admin` 站点根） | 采用现网实况（主智能体核实）；`deploy.md:26` 口径待同步（见 §12） |

---

## 12. 本次不做的项

1. **HSTS**（`Strict-Transport-Security`）与 **80→443 跳转改造**：属 server 级、会改既有行为，**本批不做**，登记遗留（安全报告 §3-4）。
2. **TLS 版本 / 证书调整**：不改既有 443 SSL 配置，登记遗留（V5 残余）。
3. **演示站退役（B8）**：`as` 兼容读与旧入口退役、`portal/main.js` 与 `deploy/docker-demo/portal.html` 链接同步，均属 B8，**本批不动**。
4. **`hrm-demo` / `hrm-admin` / `hrm-server` 工程改动**：零改动；本批只新增 Nginx location 与静态目录。
5. **后端 / 数据库**：无接口、无表结构、无数据面改动。
6. **安卓壳（B6）**：两 APK 与本批无关。
7. **既有 location 任何一行**：不改（只增不改）。

---

## 附录 · 事实性纠正（上游材料与现网实况不符处）

| # | 来源 | 其陈述 | 现网实况（主智能体核实） | 处置 |
| - | - | - | - | - |
| ① | 本任务指令 §5 提及既有 `location ~* .*\.(js|css)?$`（`expires 12h`） | 443 server 内含该正则 location | 主智能体备案的现网 443 server 结构中**未列**任何正则 location | 手册按"**当前无既有正则**"写插入点；同时保留"若存在则 `~* \.map$` 必须在其之前"的说明（V2 形式核对），不臆造该行存在 |
| ② | `deploy.md:26` | `/` → `/www/wwwroot/hrm-admin`（宝塔站点根） | 现网 `location = /` 与 `location /` proxy 到 `hrm-demo-static`（演示站） | 采信现网实况；`deploy.md` 口径待同步（非本批范围，提示主智能体） |
| ③ | ADR §5.4 站点根示例 `/www/wwwroot/hrm-web|hrm-staff|hrm-boss` | 站点根示例 | 现网为容器 `courier-nginx`，`/data/www` 已挂载（`/apk/`、`/download` 可用为证） | 改为 `/data/www/hrm-clients/{web,staff,boss}`（与容器挂载路径族一致），并加容器内可见性校验（§3） |
| ④ | 安全报告 V4「容器内 vs 宿主 bind mount」 | 未定 | `/data/www` 已挂载入容器（既有 alias 依赖） | 采纳宿主 `/data/www/hrm-clients/`，同路径容器可见（§3 前置校验兜底） |

---

## 附录 · 执行留痕模板（交主智能体登记 `update-log.md` / `SESSION-STATE.md`）

```
[2026-??-??] B7 发布切换（C 档，主智能体授权后执行）
- 变更载体：/data/www/kdyzzhxt/courier-server/nginx/nginx.conf
- 备份：nginx.conf.<TS>（TS=__________）
- 变更：新增 location ^~ /web|/staff|/boss/ + server 级 ~* .map$ 404
- 站点根：/data/www/hrm-clients/{web,staff,boss}
- 产物：web/staff-h5/boss-h5 build:prod；map 剔除后 map=0；文件数 __ / __ / __
- 验证：三端 200；深链 200；.map 404；missing 404；no-store；安全头；既有 location diff 一致
- 回滚点：cp -a nginx.conf.<TS> nginx.conf && docker exec courier-nginx nginx -t && nginx -s reload
- 观测：新入口 4xx/5xx 率 __；触发判据 §8
- 遗留：B8（演示站退役 / 旧入口退役 / portal 链接同步）；V5（证书/TLS 登记）；V6（add_header 现状）
```

---

## 附录 · 执行检查点（M02）

- **2026-09-25**：产出 `hrm-dev/docs/deploy-b7-release-switch.md`（**新建**，C 档变更的部署与回滚手册，未执行）。
- **影响文件**：仅本文件（1 个，新建）。未改动任何源码、配置、`.env*`、`deploy/**`、ADR、`update-log.md` 与其它 `docs/**`；未调用 MCP；未执行 git 操作；**未触达生产**。
- **证据来源**：主智能体现网事实基线 + `adr-structure-migration.md`（§2.3 L101-118、§3.3 L169-185、§3.7 L245-267、§5.4 L318-326、§6.1 L347-352）+ `security-release-switch-review.md`（§4 M1-M7、§5、§6 V1-V6、附录 A/B）+ 三端 `hrm-clients/apps/*/{package.json,vite.config.js}`。
- **未运行**：任何构建 / 服务器命令 / curl / reload（本机无服务器访问）。
- **回滚**：删除本文件即可（纯新增文档）。
