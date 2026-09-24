# 三端演示 Demo · 静态站镜像
#
# 为什么是多阶段之外的「单阶段 + 预构建产物」：本机无 JDK/Docker、服务器 CPU 长期 79%，
# 服务器侧跑 Node 构建既慢又与现网抢资源。故 dist/ 由本地 `npm run build`（--mode demo）产出后上传，
# 镜像只负责静态托管，服务器侧零 Node 依赖。
#
# 构建上下文要求：本目录下必须存在 dist/（由 scripts/deploy-demo 上传，见 deploy-demo.sh）
# 基础镜像版本说明（2026-09-24）：服务器访问 Docker Hub 受限（DNS 解析异常 → i/o timeout），
# 线上实际可用基础镜像为 nginx:1.25-alpine，仓库随之对齐以免「仓库与线上漂移」。
# 如需升级版本，须先为服务器配置镜像加速器或修好 DNS，并同步部署目录。
FROM nginx:1.25-alpine

# 演示站路由/缓存全部收敛在 conf.d/default.conf，不覆盖镜像自带的 nginx.conf 主体
COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY dist/ /usr/share/nginx/html/

EXPOSE 80
