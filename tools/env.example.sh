#!/usr/bin/env bash
# 本地开发环境变量模板（Bash）
#
# 复制为 tools/env.local.sh 后填入真实值：
#     cp tools/env.example.sh tools/env.local.sh
# tools/rebuild.sh 启动前会自动加载 env.local.sh（存在时）。
# env.local.sh 已加入 .gitignore，不会被提交。

# ---- PostgreSQL（必需；缺密码启动会直接报错）----
export PG_HOST='127.0.0.1'
export PG_PORT='5432'
export PG_DB='java_vision'
export PG_USER='postgres'
export PG_PASSWORD='<数据库密码>'

# ---- Redis（可选；密码留空表示免密实例）----
export REDIS_HOST='127.0.0.1'
export REDIS_PORT='6379'
export REDIS_PASSWORD=''

# ---- MinIO（可选；MINIO_ENABLED=true 时必须填密钥）----
export MINIO_ENABLED='false'
export MINIO_ENDPOINT='http://127.0.0.1:9000'
export MINIO_PUBLIC_URL='http://127.0.0.1:9000'
export MINIO_ACCESS_KEY=''
export MINIO_SECRET_KEY=''
export MINIO_BUCKET='javavision'

# ---- 以下按需开启 ----
# export MILVUS_URI=''                 # 虹软人脸 / image.service=milvus 时使用
# export MILVUS_TOKEN=''
# export INFERENCE_THREADS='0'         # 0 = 不干预（引擎默认＝逻辑核数）
# export LEGACY_HTTP_STATUS='false'    # true = 异常也返回 HTTP 200，兼容老前端
# export LOG_HOME='logs'
