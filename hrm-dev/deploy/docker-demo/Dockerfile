# 三端演示 Demo · 静态站镜像
#
# 为什么是多阶段之外的「单阶段 + 预构建产物」：本机无 JDK/Docker、服务器 CPU 长期 79%，
# 服务器侧跑 Node 构建既慢又与现网抢资源。故 dist/ 由本地 `npm run build`（--mode demo）产出后上传，
# 镜像只负责静态托管，服务器侧零 Node 依赖。
#
# 构建上下文要求：本目录下必须存在 dist/（由 scripts/deploy-demo 上传，见 deploy-demo.sh）
FROM nginx:1.27-alpine

# 演示站路由/缓存/Auth 全部收敛在 conf.d/default.conf，不覆盖镜像自带的 nginx.conf 主体
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 启动时由环境变量生成 Basic Auth 口令文件：
# 仓库与镜像内都不存明文口令，只有哈希，且哈希随部署方的环境变量注入
COPY docker-entrypoint.d/10-basic-auth.sh /docker-entrypoint.d/10-basic-auth.sh
RUN chmod +x /docker-entrypoint.d/10-basic-auth.sh

COPY dist/ /usr/share/nginx/html/

EXPOSE 80
