---
name: "devops-pipeline"
description: "DevOps 部署运维技能。覆盖 CI/CD、Docker、Nginx、宝塔面板、监控告警、回滚、安全扫描。源自 abdullahkhawer/devops-skills + pfangueiro/claude-code-agents。触发：部署/发布/CI/CD/Docker/Nginx/宝塔/监控/回滚/服务器/域名/SSL。"
---

# DevOps 部署运维

> 综合 [abdullahkhawer/devops-skills](https://github.com/abdullahkhawer/devops-skills)（DevOps 技能集） +
> [pfangueiro/claude-code-agents](https://github.com/pfangueiro/claude-code-agents)（CI/CD Agent 系统） +
> 项目特有宝塔部署规范。

## 部署流程

```
代码合并 main → 打 tag → 服务器 git pull → 编译 → 重启 → 验证
```

### 详细步骤

1. **代码准备**：`feature → dev → main`，打版本 tag
2. **拉取代码**：服务器 `git pull origin main`
3. **编译构建**：`mvn clean package -DskipTests`
4. **备份**：数据库备份 + 旧 Jar 包备份
5. **部署**：替换 Jar 包，重启服务
6. **验证**：健康检查 + 冒烟测试
7. **监控**：观察日志和监控 5 分钟

## 宝塔操作规范

### 安全红线（项目规则 12.3 节）
- 所有宝塔 MCP / SSH MCP 操作须经主智能体安全评估
- 三步审批：安全评估 → 应急预案 → 确认执行
- 高危操作（删除、重启、数据库变更）必须人工确认

### 常用操作
```bash
# 拉取代码
git pull origin main

# 编译
cd /www/wwwroot/kdyzgl && mvn clean package -DskipTests

# 重启服务
systemctl restart kdyzgl-server

# 查看日志
tail -f /www/wwwroot/kdyzgl/logs/app.log

# 健康检查
curl http://localhost:8080/actuator/health
```

## Nginx 配置模板

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate /www/server/panel/vhost/cert/your-domain/fullchain.pem;
    ssl_certificate_key /www/server/panel/vhost/cert/your-domain/privkey.pem;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        root /www/wwwroot/kdyzgl/hrm-admin/dist;
        try_files $uri $uri/ /index.html;
    }
}
```

## Docker 部署（可选）

### Dockerfile 模板
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 构建要求
- 多阶段构建减小镜像体积
- 非 root 用户运行
- 健康检查 `HEALTHCHECK`
- 不硬编码密钥，走环境变量

## 监控与告警

### 关键指标
- CPU / 内存 > 80% → 告警
- 磁盘 > 85% → 告警
- 接口响应时间 P99 > 3s → 告警
- 错误率 > 1% → 告警
- 服务不可用 → 立即告警

### 日志规范
- 应用日志：`/www/wwwroot/kdyzgl/logs/app.log`
- 按天切割，保留 30 天
- 错误日志单独文件

## 回滚方案

1. **快速回滚**：`git checkout <上一个tag>` → 编译 → 重启
2. **Jar 回滚**：恢复备份的旧 Jar 包 → 重启
3. **数据库回滚**：恢复备份的 SQL 文件

### 回滚检查
- [ ] 回滚后服务正常启动
- [ ] 核心接口可用
- [ ] 数据无丢失
- [ ] 通知相关方

## 安全检查清单

- [ ] 无硬编码密钥/密码
- [ ] 环境变量管理敏感配置
- [ ] HTTPS 已启用
- [ ] 防火墙规则正确
- [ ] 数据库端口不对外暴露
- [ ] 日志不包含敏感信息

## 参考来源

- [abdullahkhawer/devops-skills](https://github.com/abdullahkhawer/devops-skills) — DevOps 技能集
- [pfangueiro/claude-code-agents](https://github.com/pfangueiro/claude-code-agents) — CI/CD Agent 系统