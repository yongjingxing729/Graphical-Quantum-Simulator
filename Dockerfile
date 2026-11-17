# ============================================================
# 云端部署镜像（多阶段构建）
# 构建：  docker build -t graphical-quantum-simulator .
# 运行：  docker run -d -p 8080:8080 -e QSIM_MODE=cloud graphical-quantum-simulator
# 说明：  默认以「云端模式」启动（绑定 0.0.0.0）。也可用 QSIM_MODE=local。
# ============================================================

# ---- 构建阶段：编译后端 + 上游 Strange 源码 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
# 用 bash 显式执行，避免 Windows 下文件丢失可执行位导致的权限问题
RUN bash build.sh

# ---- 运行阶段：仅保留运行时所需产物 ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/backend/build/classes ./backend/build/classes
COPY --from=build /app/frontend ./frontend
COPY --from=build /app/strange.version ./strange.version
COPY --from=build /app/NOTICE ./NOTICE
COPY --from=build /app/THIRD_PARTY_NOTICES.md ./THIRD_PARTY_NOTICES.md

ENV QSIM_MODE=cloud
ENV QSIM_PORT=8080
EXPOSE 8080

CMD ["java", "-cp", "/app/backend/build/classes", "com.gqs.Main"]
