# 云端部署指南（Cloud Deployment）

本项目 MVP 已包含完整的云端部署能力，但**默认以本地模式运行**（由
`backend/src/com/gqs/AppConfig.java` 的 `RUN_MODE = "local"` 控制）。

## 1. 模式切换

### 方式一：修改代码中的单一变量（需求 1）

`backend/src/com/gqs/AppConfig.java`：

```java
public static final String RUN_MODE = "cloud"; // local → cloud
```

### 方式二：环境变量覆盖（更适合云端/CI）

```bash
export QSIM_MODE=cloud
export QSIM_PORT=8080          # 可选
```

两种方式等价；环境变量优先级更高。

## 2. 部署方式

### 2.1 Docker Compose（推荐）

```bash
docker compose up -d --build
```

- 自动构建镜像、以 `QSIM_MODE=cloud` 启动、映射 `8080` 端口、`restart: unless-stopped`。
- 访问：`http://<服务器公网IP>:8080`

### 2.2 Docker

```bash
docker build -t graphical-quantum-simulator .
docker run -d --name gqs -p 8080:8080 -e QSIM_MODE=cloud graphical-quantum-simulator
```

### 2.3 直接在服务器上运行

```bash
# 服务器上安装 JDK 17+
git clone <本项目地址> && cd <项目目录>
export QSIM_MODE=cloud
./run.sh
```

### 2.4 辅助脚本

```bash
python scripts/deploy_cloud.py check    # 检查 Java/Docker 前置条件
python scripts/deploy_cloud.py info     # 打印当前配置
python scripts/deploy_cloud.py docker   # 构建并运行容器（云端模式）
```

## 3. 云服务器注意事项

- **安全组/防火墙**：放行 `8080`（或自定义端口）。
- **反向代理（可选）**：Nginx/Caddy 将 80/443 转发到 `127.0.0.1:8080`，并配置 HTTPS。
- **端口**：`QSIM_PORT` 可改端口，Docker 端口映射同步调整。
- **资源**：默认限制 `n ≤ 12` 比特；大比特线路计算量指数增长，生产环境建议按需调小或加资源。

## 4. Nginx 反向代理示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 5. 验证

```bash
curl http://<服务器IP>:8080/api/meta
# 应返回 "mode":"cloud","bindHost":"0.0.0.0",...
```
