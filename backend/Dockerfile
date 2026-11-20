# CardKey 演示后端 —— 一键容器化启动
# 构建：docker build -t cardkey-backend -f backend/Dockerfile .
# 运行：docker run -d -p 8080:8080 \
#         -v $(pwd)/backend/keys:/app/backend/keys \
#         -e ADMIN_TOKEN=change-me \
#         -e PRIVATE_KEY=/app/backend/keys/private.pem \
#         cardkey-backend
# 私钥通过卷挂载进容器，不写进镜像层。

FROM python:3.13-slim

RUN apt-get update && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY tools/cardkey_tool.py tools/
COPY backend/ backend/

ENV ADMIN_TOKEN=change-me \
    PRIVATE_KEY=/app/backend/keys/private.pem \
    PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8080/health')"

CMD ["sh", "-c", "python backend/server.py --private-key \"$PRIVATE_KEY\" --admin-token \"$ADMIN_TOKEN\" --port \"$PORT\""]