# JavaVision Plus
[![GitHub stars](https://img.shields.io/github/stars/along2638/java-vision-plus.svg)](https://github.com/along2638/java-vision-plus)
[![GitHub issues](https://img.shields.io/github/issues/along2638/java-vision-plus.svg)](https://github.com/along2638/java-vision-plus/issues)
[![Apache License 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/java-1.8+-orange.svg)](https://adoptopenjdk.net/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-2.7.12-green.svg)](https://spring.io/projects/spring-boot)

基于 Java 的视觉推理服务：OCR、目标检测、人脸识别、以图搜图，一个 `java -jar` 即可启动。

## 📌 二次开发声明

**JavaVision Plus 是 [javpower/JavaVision](https://github.com/javpower/JavaVision) 的二次开发（二开）增强版本，不是上游官方发行版。**

识别算法、模型与核心业务流程沿用上游实现；本仓库在其基础上重做了持久层与文件存储，增加了推理设备动态决策与启动预热，统一了接口响应、错误码与链路追踪，并补充了接口文档与性能实测。原项目版权归其作者及贡献者所有，本项目同样以 [Apache License 2.0](LICENSE) 发布。

| 项目 | 说明 |
| --- | --- |
| 上游仓库（原项目） | <https://github.com/javpower/JavaVision> |
| 本仓库（二开增强） | <https://github.com/along2638/java-vision-plus> |
| 差异范围 | 分叉点 `f4873ed`（2025-05-14，取自 Gitee 主线），此后全部改动可用 `git diff f4873ed..HEAD` 查看 |
| 许可证 | Apache License 2.0，保留原 [LICENSE](LICENSE) 与版权声明 |

> 为便于与上游对比差异、合并上游更新，**Java 包名 `com.github.javpower.javavision` 保持不变**；项目标识（Maven 坐标、应用名、jar 名）已统一为 `java-vision-plus`。

### 二开增强了什么

| 方向 | 内容 |
| --- | --- |
| 持久层 | 人脸库与图库元数据由 JSON 文件改为 PostgreSQL + MyBatis-Plus：幂等建表脚本、历史 JSON 自动导入、逻辑删除、图库分页 |
| 文件存储 | 抽出 `StorageService`，人脸图与图库图存 MinIO 对象存储，未部署 MinIO 时自动回退本地磁盘 |
| 推理设备 | 启动期按「产物 flavor ∩ 显卡与驱动版本」决策 CPU/GPU；`mvn package -Dgpu=true` 构建 CUDA 产物 |
| 性能 | 模型常驻缓存 + 空推理预热（首个请求 3.3s → 0.96s）+ 推理线程数干预；修复 ONNX Runtime 双环境冲突 |
| 缓存 | Redis 缓存人脸特征与图片向量（按 MD5），Redis 不可用时自动降级查库 |
| 接口规范 | 统一 `ApiResult` / `PageResult` 与错误码、语义化 HTTP 状态码（可开关回退旧行为）、traceId 链路、参数校验、Swagger 中文文档 |
| 新增接口 | `GET /system/status`（中间件与推理设备状态）、`POST /system/cache/clear`、`GET /image/list`（分页） |
| 文档与工具 | [docs/API.md](docs/API.md)、[docs/PERF.md](docs/PERF.md)、`tools/` 下的打包重启与压测脚本 |

## 🪧 项目起源

- JavaVision（本项目在此基础上二开）是一个基于 Java 开发的全能视觉智能识别项目。该项目起源于对图像处理和人工智能领域的热情，以及对 Java 作为主要编程语言的坚持。在 AI 领域，大多数解决方案都是使用 Python 实现的，因此决定充分利用 Java 的优势来构建一个功能强大且易于集成的视觉智能识别平台。

  > JavaVision 是完全开源、免费并支持多平台部署的视觉智能识别项目。

## 👏 功能特性

- **OCR 文字识别**：RapidOcr（JNI）与 DJL（PP-OCRv4）两条链路，均可返回纯文本或带坐标、置信度的结构化数组
- **目标检测**：YOLOv8 通用物体识别，以及火焰烟雾、反光衣、安全帽三个专项模型
- **人脸识别**：`/face` 基于虹软算法提取特征 + PostgreSQL 存储，支持 1:N 检索；`/arc/soft/face` 为虹软 SDK + Milvus 的独立实现
- **以图搜图**：向量库可切换，默认 `vectorex`，也支持 `milvus`、`es`（`image.service` 配置）
- **工程能力**：CPU/GPU 双产物、启动预热、模型常驻、Redis 缓存降级、统一响应与 traceId 链路

## 🚀 快速开始

### 1. 环境要求

| 依赖 | 版本 | 是否必需 |
| --- | --- | --- |
| JDK | 8+（实测构建用 11，编译目标 1.8） | 必需 |
| Maven | 3.6+ | 构建时必需 |
| PostgreSQL | 12+ | **必需**，人脸库与图库元数据 |
| Redis | 5+ | 可选，缓存不可用时自动查库 |
| MinIO | 任意较新版本 | 可选，`minio.enabled=false` 回退本地磁盘 |
| Milvus / Elasticsearch | — | 可选，仅在对应向量库实现下需要 |

模型文件与各平台原生库已随仓库提供（`src/main/resources`，约 665MB），无需额外下载。

### 2. 构建

```bash
# CPU 产物（默认，按构建机平台自动选择 win-x86_64 / linux-x86_64 原生库）
mvn clean package

# GPU 产物：CUDA 12.1，体积更大，需要构建机安装对应 toolchain
mvn clean package -Dgpu=true

# 在 Windows 上打 Linux GPU 包
mvn clean package -Dgpu=true -Dgpu.native.classifier=linux-x86_64
```

产物：`target/java-vision-plus-0.0.1-SNAPSHOT.jar`（约 966MB，含模型与原生库）。

> 构建期会向 `inference-flavor.properties` 写入 `cpu` 或 `cu121`，启动时由 `InferenceDevice` 读取，
> 与显卡探测结果取交集决定实际设备。CPU 产物在任何机器上都不会启用 GPU。

### 3. 启动

```bash
java -Xms1g -Xmx2g -jar target/java-vision-plus-0.0.1-SNAPSHOT.jar
```

堆内存建议显式指定：模型常驻的是堆外内存（约 280MB），但 `-Xmx` 不限制时 JVM 会按物理内存的 1/4 取值，内存紧张时被换页会让单请求从 0.7s 退化到 2s 以上。

仓库自带两个启动脚本，用于规避「实例运行中打包导致 jar 被锁、repackage 失败」的问题（脚本会把产物复制到独立目录再运行）：

```powershell
# Windows：用现有产物重启；-Build 表示先 mvn clean package
.\tools\run.ps1
.\tools\run.ps1 -Build
```

```bash
# Linux：停服务 → 打包 → 校验清单 → 启动
./tools/rebuild.sh
./tools/rebuild.sh -Dgpu=true
```

### 4. 验证

| 地址 | 说明 |
| --- | --- |
| `http://localhost:6888/system/status` | 中间件连接、推理设备、预热状态、模型清单 |
| `http://localhost:6888/doc.html` | knife4j 在线接口文档 |
| `http://localhost:6888/v3/api-docs` | OpenAPI 3 原始描述 |

`/system/status` 中 `inference.warmup.state` 变为"完成"后再接流量，可保证第一个真实请求就落在稳态耗时。

## ⚙️ 配置

所有中间件连接均可用环境变量覆盖，`application.yml` 里的默认值是内网测试环境，**生产部署必须覆盖**（仓库公开时建议把默认值清空）。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PG_HOST` / `PG_PORT` / `PG_DB` | `127.0.0.1` / `5432` / `java_vision` | PostgreSQL 连接 |
| `PG_USER` / `PG_PASSWORD` | `postgres` / `postgres` | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB` | `127.0.0.1` / `16377` / `0` | Redis 连接 |
| `REDIS_PASSWORD` | — | Redis 密码 |
| `MINIO_ENDPOINT` / `MINIO_PUBLIC_URL` | `http://127.0.0.1:9000` | MinIO 内网地址与对外访问地址 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | — | MinIO 凭证 |
| `MINIO_BUCKET` | `javavision` | 桶名，沿用上游以免历史图片地址失效 |
| `MILVUS_URI` / `MILVUS_TOKEN` | 上游测试实例 / 空 | 虹软人脸与 `image.service=milvus` 时需要 |
| `LEGACY_HTTP_STATUS` | `false` | `true` 时异常也返回 HTTP 200，用于兼容老前端 |
| `INFERENCE_THREADS` | `0` | 推理线程数，`0` 表示不干预（引擎默认＝逻辑核数） |
| `LOG_HOME` | `logs` | 日志目录，容器部署建议挂盘 |

其他行为开关在 `application.yml`：

- `vision.inference.warmup.enabled` / `async` / `predict`：启动预热，默认开启并异步、加载后空跑一次
- `minio.enabled`：`false` 时文件存本地磁盘，行为与改造前一致
- `image.service`：以图搜图实现，`vectorex`（默认）/ `milvus` / `es`
- `es.enable`：未部署 Elasticsearch 时保持 `false`

**数据库初始化**：启动时自动执行 `classpath:db/schema-pg.sql`（幂等 DDL，表与索引不存在才创建），无需手工建表。历史版本存放在 `image/face/{personId}.json` 的人脸数据，会在首次启动（人员表为空）时自动导入 PostgreSQL，原 JSON 保留作备份。

## 🧩 模块结构

```
com.github.javpower.javavision
├── controller/    REST 接口（人脸、图库、OCR/检测、系统状态、虹软）
├── service/       业务编排：人脸库、图库元数据、向量提取、统一 OCR/检测入口
├── db/            MyBatis-Plus 实体 / Mapper / TypeHandler / 启动迁移
├── storage/       文件存储抽象：MinIO 与本地磁盘两种实现
├── cache/         Redis 访问门面（失败降级，日志节流）
├── config/        启动预热、MinIO 配置、MyBatis-Plus 配置
├── util/          推理设备决策、推理线程数、模型缓存、文件与图像工具
├── detect/        DJL Translator（检测、识别、特征提取、OCR 后处理）
├── milvus/ vectorex/ es/   三套向量库实现，接口一致可切换
├── arcsoft/       虹软人脸 SDK 封装
└── framework/     统一响应、全局异常、TraceId 过滤、OpenAPI 定制、CORS
```

## 🔌 接口一览

所有接口返回统一结构 `ApiResult`：`{ code, success, msg, data, time }`，异常由全局处理器包装，响应头带 `X-Trace-Id`。图片上传统一为 `multipart/form-data`，文件字段名固定 `file`，单文件上限 20MB。字段说明、请求示例与错误码见 **[docs/API.md](docs/API.md)**。

| 模块 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 人脸 | POST | `/face/add` | 添加人脸（图片入库 + 特征提取） |
| 人脸 | POST | `/face/update` | 更新人脸，personId 不存在时按新增处理 |
| 人脸 | POST | `/face/del` | 删除人脸（逻辑删除，保留审计） |
| 人脸 | POST | `/face/search` | 1:N 检索，返回最相似人员 |
| 人脸 | GET | `/face/list` | 人脸库列表 |
| 图库 | POST | `/image/add` | 图片入库（元数据 + 向量） |
| 图库 | POST | `/image/search` | 以图搜图，返回相似度排序结果 |
| 图库 | POST | `/image/del` | 删除图片（逻辑删除） |
| 图库 | GET | `/image/list` | 图库分页列表 |
| OCR | POST | `/ocr` `/ocr/json` | RapidOcr：纯文本 / 结构化文本块 |
| OCR | POST | `/word` `/word/json` | DJL PP-OCRv4：纯文本 / 结构化文本块 |
| 检测 | POST | `/yolo8` `/yolo8/json` | YOLOv8 通用物体识别 |
| 检测 | POST | `/fire/json` | 火焰烟雾检测 |
| 检测 | POST | `/reflective/json` | 反光衣检测 |
| 检测 | POST | `/construction/json` | 安全帽检测 |
| 系统 | GET | `/system/status` | 中间件连接、推理设备、预热状态 |
| 系统 | POST | `/system/cache/clear` | 清理人脸特征与图片向量缓存 |
| 虹软 | POST | `/arc/soft/face/add` `/update` `/del` `/search` | 虹软 SDK + Milvus 的独立人脸库实现 |

## 📈 性能与容量

实测环境：20 vCPU、CPU 产物、`POST /word/json`。完整数据与压测方法见 **[docs/PERF.md](docs/PERF.md)**。

| 指标 | 数值 |
| --- | --- |
| 首个请求耗时 | 3.30s（仅加载模型）→ **0.96s**（加载 + 空推理） |
| 稳态耗时 | 约 0.64s |
| 单请求 CPU 时间 | 约 **6.93 CPU·s**（平均占用 9.5 核） |
| 单实例吞吐 | 20 核下约 1.5~1.8 req/s |

结论：CPU 推理的吞吐天花板由单请求 CPU 量决定，调线程数只能改变排队方式；要提高吞吐就加实例或改用 `-Dgpu=true` 的 GPU 产物。

## 📚 文档

| 文档 | 内容 |
| --- | --- |
| [docs/API.md](docs/API.md) | 24 个接口的完整说明：参数、响应、错误码、字段含义 |
| [docs/PERF.md](docs/PERF.md) | 推理性能实测：预热效果、CPU 用量、并发表现、部署建议 |
| `/doc.html` | 运行期在线文档，与代码注解同源 |

## 开源许可

本项目以 [Apache License 2.0](LICENSE) 发布。

JavaVision Plus 基于 [javpower/JavaVision](https://github.com/javpower/JavaVision) 二次开发，原项目及其贡献者的版权声明与许可条款同样适用于本项目，完整条款见 [LICENSE](LICENSE)。
