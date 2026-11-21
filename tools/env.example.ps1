# 本地开发环境变量模板（PowerShell）
#
# 复制为 tools/env.local.ps1 后填入真实值：
#     Copy-Item tools\env.example.ps1 tools\env.local.ps1
# tools/run.ps1 启动前会自动加载 env.local.ps1（存在时）。
# env.local.ps1 已加入 .gitignore，不会被提交。

# ---- PostgreSQL（必需；缺密码启动会直接报错）----
$env:PG_HOST     = '127.0.0.1'
$env:PG_PORT     = '5432'
$env:PG_DB       = 'java_vision'
$env:PG_USER     = 'postgres'
$env:PG_PASSWORD = '<数据库密码>'

# ---- Redis（可选；密码留空表示免密实例）----
$env:REDIS_HOST     = '127.0.0.1'
$env:REDIS_PORT     = '6379'
$env:REDIS_PASSWORD = ''

# ---- MinIO（可选；MINIO_ENABLED=true 时必须填密钥）----
$env:MINIO_ENABLED    = 'false'
$env:MINIO_ENDPOINT   = 'http://127.0.0.1:9000'
$env:MINIO_PUBLIC_URL = 'http://127.0.0.1:9000'
$env:MINIO_ACCESS_KEY = ''
$env:MINIO_SECRET_KEY = ''
$env:MINIO_BUCKET     = 'javavision'

# ---- 以下按需开启 ----
# $env:MILVUS_URI        = ''            # 虹软人脸 / image.service=milvus 时使用
# $env:MILVUS_TOKEN      = ''
# $env:INFERENCE_THREADS = '0'           # 0 = 不干预（引擎默认＝逻辑核数）
# $env:LEGACY_HTTP_STATUS = 'false'      # true = 异常也返回 HTTP 200，兼容老前端
# $env:LOG_HOME          = 'logs'
