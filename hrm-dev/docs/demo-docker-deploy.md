# 三端演示 Demo · Docker 测试环境部署与回滚

> 版本 v1.0 | 建立日期 2026-09-22 | 分支 `feature/前端演示项目拆分与精细化`
> 适用对象：`hrm-dev/hrm-demo`（三端演示静态站）在云服务器上的测试环境部署
> 授权档位：**C 档（主智能体三步授权）** —— 涉及改动在跑系统的现网 Nginx 配置

> ## ⚠️ 状态变更登记（2026-09-23 补记）
>
> **本文档描述的 Basic Auth 网关在服务器上已被他方移除，且未同步仓库。**
>
> 实测服务器 `/data/www/hrm-demo/` 于 2026-09-22 23:50–23:55 被直接改动：
> `nginx.conf` 移除全部 `auth_basic`（文件内留有理由「认证已收敛到站内登录页，避免两层验身」）、
> `docker-compose.yml` 去掉 `env_file` 与 `BASIC_AUTH_*`、`.env` 删去认证两行、
> `docker-entrypoint.d/10-basic-auth.sh` 删除。
>
> 因此：
> 1. **当前线上演示站无网关鉴权，公网可直接访问**
> 2. **本文档 §3/§4/§6 中与 Basic Auth 相关的步骤与验收项，与当前线上状态不符**，以实测为准
> 3. 仓库内 `hrm-dev/deploy/docker-demo/**` 仍保留 Basic Auth 实现 → **按仓库重新部署会重新引入网关**，
>    需先裁决「对齐仓库（采纳去网关）」还是「恢复网关」，再决定是否改写本节
> 4. 原设计的测试网关账号 `16626369983` 现已不存在于任何位置

## 1. 目标与边界

| 项 | 内容 |
| --- | --- |
| 部署对象 | `hrm-demo` 三端演示站（`index` 端选择 / `pc` 网页端 / `mobile` 移动端），**纯前端 + Mock 假数据** |
| 数据面 | **无后端、无数据库、无 Redis** → 无持久化卷，删容器即彻底回滚 |
| 访问入口 | `kongzhen1.com` 根路径（HTTP 301 → HTTPS，由现网 Nginx 承载证书） |
| 访问控制 | **站内登录页**（纯「账号 + 密码」两字段；原容器内 Basic Auth 网关已移除） |
| 现网影响 | **仅改现网 Nginx 两处 `location`**；现网容器、compose、数据面**零改动** |

## 2. 服务器现状（2026-09-22 实测）

| 项 | 实测值 |
| --- | --- |
| SSH | `root@156.225.23.154:22`（Ubuntu 22.04，磁盘 `/` 剩 17G，`/data` 剩 47G，内存 3.9G / 可用 1.9G，CPU 79.7%） |
| Docker | 29.7.2（已就绪） |
| **现网栈** | compose 项目 `courier-server`，配置 `/data/www/kdyzzhxt/courier-server/docker-compose.yml`，源码来自**另一仓库** `git@gitee.com:jia-yongbin/kdyzzhxt.git` |
| 现网容器 | `courier-nginx`(80/443) · `courier-app`(127.0.0.1:8080) · `courier-mysql`(127.0.0.1:3306) · `courier-redis`(127.0.0.1:6379)，已运行 2 周 |
| 端口占用 | 22 / 53 / 80 / 443 / 3306 / 6379 / 8080 / 8765 / 8888 |
| DNS | `kongzhen1.com`、`www.kongzhen1.com` → `156.225.23.154`；`demo.` / `test.` 子域**无 A 记录** |
| 证书 | `/data/ssl/fullchain.pem` + `/data/ssl/privkey.pem`（现网 Nginx 挂载至 `/etc/nginx/ssl`） |

> ⚠️ **文档漂移登记**：项目规则写「生产架构 = 宝塔 + Nginx + systemd `hrm-server.jar`、库名 `kdyzgl`」，
> 与服务器实况（Docker 栈 `courier-server`、库名 `courier_station`、另一仓库）不符。
> 本文件以**实测**为准；规则文档的修订另行处理。

## 3. C 档三步表单

### ① 影响范围与是否涉生产数据

- **涉生产数据：否。** 本服务是静态文件容器，不读写任何数据库/Redis/现网卷。
- **改动面：** 仅现网 `courier-nginx` 容器内的 `/etc/nginx/nginx.conf`（宿主路径
  `/data/www/kdyzzhxt/courier-server/nginx/nginx.conf`），改动 2 处 `location`。
- **对外表现变化：** 仅 `https://kongzhen1.com/`（根路径）由「快递驿站管理系统 · 后端 API 服务运行中」静态欢迎页
  变为演示站入口。
- **明确不变：** `/admin/`（老板网页端）、`/api/`（后端反代）、`/photos/`、`/apk/`、`/download`、`/health`
  逐条保持原样（nginx 前缀匹配「最长者优先」，`/` 的 catch-all 不会抢占以上任一前缀）。

### ② 是否可逆

- **可逆，且回滚代价极低。**
- 现网 Nginx：改前先备份 `nginx.conf` 到 `/data/backup/`，回滚 = 覆盖回备份 + `nginx -s reload`。
- 本服务：`docker compose down` 删容器即完全消失，无残留数据卷、无残留网络（`courier-net` 为 `external`，不会被删）。
- 回滚不需要重建现网任何容器，**不涉及数据库**。

### ③ 回滚步骤与验证方法

```bash
# 回滚现网 Nginx（唯一需要人工确认的一步）
cp /data/backup/nginx.conf.<时间戳> /data/www/kdyzzhxt/courier-server/nginx/nginx.conf
docker exec courier-nginx nginx -t
docker exec courier-nginx nginx -s reload
curl -sk -o /dev/null -w '%{http_code}\n' -H 'Host: kongzhen1.com' https://127.0.0.1/   # 期望回到 200 欢迎页

# 回滚本服务
cd /data/www/hrm-demo && docker compose down --remove-orphans
docker compose ls                       # 期望只剩 courier-server
```

**验证方法：** 回滚后逐条复验 ① 根路径恢复欢迎页 ② `/admin/` 200 ③ `/api/` 与 `/health` 正常
④ `docker compose ls` 仅剩 `courier-server`。

## 4. 现网 Nginx 精确改动

改动点（在 443 server 块内）：

```diff
-        # 根路径欢迎页
-        location = / {
-            default_type text/html;
-            add_header Cache-Control "no-cache";
-            return 200 '<!DOCTYPE html>…后端 API 服务运行中</body></html>';
-        }
+        # 根路径与前端路由交给三端演示 Demo 静态站（Basic Auth 由上游容器自持）
+        # 为什么用容器名：courier-net 内 DNS 可直接解析，无需暴露宿主机端口
+        location = / {
+            proxy_pass http://hrm-demo-static:80;
+            proxy_set_header Host $host;
+            proxy_set_header X-Real-IP $remote_addr;
+            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
+            proxy_set_header X-Forwarded-Proto $scheme;
+        }
+
+        # 演示站三入口的深链回退在容器内实现；此处只做透传
+        # 不影响 /admin/ /api/ /photos/ /apk/ /download /health —— 前缀匹配最长者优先
+        location / {
+            proxy_pass http://hrm-demo-static:80;
+            proxy_set_header Host $host;
+            proxy_set_header X-Real-IP $remote_addr;
+            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
+            proxy_set_header X-Forwarded-Proto $scheme;
+        }
```

改动后校验：

```bash
docker exec courier-nginx nginx -t          # 必须 syntax is ok / test is successful
docker exec courier-nginx nginx -s reload
```

## 5. 部署步骤

前置：本地已 `npm run build --mode demo` 产出三入口 `dist/`（`index.html` / `pc.html` / `mobile.html`）。

```bash
# 1) 本地打包产物 + 部署产物，上传到服务器
#    （仓库不存 dist/ 与 .env，两者都随本次部署单独投递）
# 2) 服务器解包到 /data/www/hrm-demo
mkdir -p /data/www/hrm-demo && tar -xzf /tmp/hrm-demo-deploy.tgz -C /data/www/hrm-demo

# 3) 参数（无凭据）：仅 DEMO_TAG / HOST_PORT，可直接复制模板
cd /data/www/hrm-demo
cp .env.example .env

# 4) 起服务（仅绑回环，公网入口经现网 Nginx）
bash deploy-demo.sh up

# 5) 接现网 Nginx（C 档：见 §3/§4，改前必须备份）
```

## 6. 验证清单

**功能**
- `curl http://127.0.0.1:8090/` → 200，内容为端选择页（免鉴权直连）
- `/healthz` → 200；缺失资源 `/assets/not-exist-abcdefgh.js` → 404
- `https://kongzhen1.com/` → 200，端选择页三入口可达（**无 Basic Auth 弹窗**）
- PC 深链 `https://kongzhen1.com/dashboard` 刷新 → 落到 `pc.html`（**不得回退到端选择页**）
- 缺失资源 `https://kongzhen1.com/assets/not-exist-abcdefgh.js` → **404**（不得回退成 HTML）
- 登录演示账号后 15 个 PC 路由逐页无白屏；移动端三 Tab 正常

**性能**
- 首屏 gzip：PC 入口 73.1 KB / CSS 11.3 KB（本地实测口径，服务器侧用 `curl -H 'Accept-Encoding: gzip' -w '%{size_download}'` 复核）
- 静态资源响应时间；带哈希资源命中 `immutable` 缓存头

**安全**
- 站点对公网完全开放，认证由**站内登录页**承担（纯账号 + 密码）；站内仅演示 Mock 数据，无真实业务数据
- 响应头无 `Server` 版本泄露（`server_tokens off`）
- 仓库与镜像内无网关口令（Basic Auth 已移除）；`.env` 只含 `DEMO_TAG` / `HOST_PORT`，无凭据
- 端口 8090 **仅绑 127.0.0.1**，公网不可直连（`ss -lntp` 复核）
- 证书链有效、TLS 1.2/1.3

## 7. 已知限制与遗留

1. **预留账号（用户名 `16626369983`）已落在站内**：作为 Mock 固定员工（管理员角色、`pwd_changed=1` 免首登改密）写入
   `src/shared/mock/db.js` 的 `FIXED_EMPLOYEES`，登录页纯「账号 + 密码」校验，失败按错误码回文案。
   **注意**：口令写在公开 JS bundle 里，无法真正保密，仅因演示站本就是全公开 Mock 数据才可接受。
2. 演示站是全公开 Mock 数据，站内登录页只是演示流程的一环，**不等于**访问控制体系。
3. 未启用 HTTPS 独立证书：复用现网证书与 443 入口，故依赖现网 Nginx 存活。
4. `hrm-server`（一期生产后端）**不在本次部署范围**；其 B17/C11/D01–D05/E01–E06 仍未执行。
5. 服务器 CPU 长期 79.7%、内存可用 1.9G：本服务为纯静态，占用可忽略；**禁止**在服务器上跑 Node 构建。
