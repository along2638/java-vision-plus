# JavaVision Plus 接口文档

> 本文档覆盖项目全部对外 HTTP 接口，每个接口的入参与返回参数均标注中文说明。
> 文档与代码注解（springdoc / knife4j）保持同源，改动代码注解后建议同步更新本文档。

## 一、通用说明

### 1.1 服务地址

| 项目 | 值 |
| --- | --- |
| 默认服务地址 | `http://{host}:6888` |
| 上下文路径 | `/`（可通过 `server.servlet.context-path` 修改） |
| 在线接口文档（knife4j） | `http://{host}:6888/doc.html` |
| OpenAPI 3 原始描述 | `http://{host}:6888/v3/api-docs` |

### 1.2 请求约定

- 所有业务接口均为 **POST**，除 `/system/status`、`/face/list`、`/image/list` 为 **GET**。
- 涉及图片上传的接口，请求类型为 `multipart/form-data`，**文件字段名固定为 `file`**。
- 普通参数（如 `personId`、`imageId`）服务端统一用 `@RequestParam` 绑定，
  因此**放在 query string 或 multipart 表单字段中都可以**，下表中位置记为 `query / form`。
- 单文件大小上限 **20MB**，单次请求总大小上限 **200MB**（见 `spring.servlet.multipart`）。
- 支持的图片格式：`jpg` / `jpeg` / `png`。
- 字符编码统一为 `UTF-8`。

### 1.3 统一响应结构

所有接口（含异常）均由 `RestResponseBodyAdvice` 与 `GlobalExceptionHandler` 包装为同一结构：

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `code` | int | 响应编码，200 表示成功，其它见错误码表 |
| `success` | boolean | 是否成功，`true` 成功 / `false` 失败 |
| `msg` | string | 响应提示信息，成功时为"操作成功"，失败时为具体原因 |
| `data` | object | 业务数据，结构随接口不同；无返回数据的接口为 `null` |
| `time` | string | 响应时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `traceId` | string | 本次请求的链路 ID，同时出现在响应头 `X-Trace-Id` 与日志中，报错时可直接反馈 |

成功响应示例：

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": null,
  "time": "2026-11-13 10:20:31",
  "traceId": "8f3c1d2a9b7e4c56"
}
```

失败响应示例：

```json
{
  "code": 400,
  "success": false,
  "msg": "缺少必填参数：imageId",
  "data": null,
  "time": "2026-11-13 10:20:31",
  "traceId": "8f3c1d2a9b7e4c56"
}
```

### 1.4 错误码

| code | 中文含义 | 触发场景 |
| --- | --- | --- |
| 200 | 操作成功 | 正常返回 |
| 400 | 参数错误 | 缺少必填参数、参数类型错误、缺少文件、文件超过大小限制、请求方法不支持 |
| 404 | 资源不存在 | 访问路径不存在 |
| 500 | 操作失败 | 业务异常、推理异常、中间件异常等未分类错误 |
| 5001 | Token 为空或已过期 | 预留的鉴权失败码，当前版本未启用鉴权 |

> 注意：HTTP 状态码按语义返回（400/404/405/409/413/500），调用方应同时参考响应体中的 `code` / `success` / `msg`。
> 若前端尚未改造、仍依赖"异常也返回 200"，可把 `vision.api.legacy-http-status` 设为 `true` 退回旧行为，改造完成后删除该配置。

---

## 二、通用数据结构

### 2.1 DetectResult —— 目标检测结果

火焰、反光衣、安全帽三类检测接口的返回元素。坐标为**按图片宽高归一化后的 0~1 数值**。

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `className` | string | 检测类别名称，如 `fire`、`smoke`、`vest`、`helmet`、`person` |
| `probability` | double | 置信度，0~1，越大越可信 |
| `x` | double | 检测框左上角横坐标，按图片宽度归一化，0~1 |
| `y` | double | 检测框左上角纵坐标，按图片高度归一化，0~1 |
| `width` | double | 检测框宽度，按图片宽度归一化，0~1 |
| `height` | double | 检测框高度，按图片高度归一化，0~1 |

```json
{
  "className": "helmet",
  "probability": 0.8743,
  "x": 0.121,
  "y": 0.233,
  "width": 0.31,
  "height": 0.45
}
```

### 2.2 SearchResult —— 以图搜图命中结果

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `url` | string | 命中图片的访问地址（MinIO URL 或本地绝对路径） |
| `imageId` | string | 命中图片的业务编号 |
| `score` | float | 相似度得分，0~1，越大越相似；结果已按该字段降序排列 |

### 2.3 PageResult\<T\> —— 分页结果

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `total` | long | 总条数（不含已逻辑删除的记录） |
| `page` | int | 当前页码，从 1 开始 |
| `size` | int | 每页条数 |
| `pages` | int | 总页数 |
| `records` | array | 当前页数据列表，元素类型见各接口说明 |

---

## 三、人脸应用 `/face`

基于虹软算法提取 **256 维**人脸特征，人员信息与特征分别存 PostgreSQL 的 `vision_face_person`、`vision_face_feature` 表，
人脸图片存对象存储（MinIO 或本地目录）。

### 3.1 添加人脸

- **请求**：`POST /face/add`
- **Content-Type**：`multipart/form-data`
- **说明**：上传人脸图片并绑定人员编号与姓名。图片写入对象存储，特征向量写入数据库，同时清理 Redis 中的人脸特征缓存。
  同一 `personId` 已存在时会因唯一约束失败，需要变更图片请调用 `/face/update`。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `personId` | query / form | string | 是 | 人员编号，业务唯一标识 | `0000` |
| `personName` | query / form | string | 是 | 人员姓名 | `张三` |
| `file` | form(file) | file | 是 | 人脸图片文件，jpg/jpeg/png | `zhangsan.jpg` |

**返回参数**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | null | 无业务数据，仅通过 `code` / `success` / `msg` 判断结果 |

**返回示例**

```json
{ "code": 200, "success": true, "msg": "操作成功", "data": null, "time": "2026-11-13 10:20:31" }
```

### 3.2 更新人脸

- **请求**：`POST /face/update`
- **Content-Type**：`multipart/form-data`
- **说明**：按 `personId` 覆盖更新姓名、人脸图片与特征向量。`personId` 不存在时按新增处理。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `personId` | query / form | string | 是 | 人员编号，已存在则更新，不存在则新增 | `0000` |
| `personName` | query / form | string | 是 | 人员姓名 | `张三` |
| `file` | form(file) | file | 是 | 新的人脸图片文件 | `zhangsan_new.jpg` |

**返回参数**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | null | 无业务数据 |

### 3.3 删除人脸

- **请求**：`POST /face/del`
- **Content-Type**：`multipart/form-data`（也可用 `application/x-www-form-urlencoded`）
- **说明**：按 `personId` 逻辑删除人员及其特征（数据库标记 `is_deleted = 1`），并清理 Redis 人脸特征缓存。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `personId` | query / form | string | 是 | 要删除的人员编号 | `0000` |

**返回参数**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | null | 无业务数据 |

### 3.4 人脸查询（1:N 检索）

- **请求**：`POST /face/search`
- **Content-Type**：`multipart/form-data`
- **说明**：提取上传图片的人脸特征，与库中全部特征做相似度比对，返回最相似的人员。
  库为空或相似度未达阈值时 `data` 为 `null`。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待检索的人脸图片文件 | `unknown.jpg` |

**返回参数（data）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `personId` | string | 命中的人员编号 |
| `personName` | string | 命中的人员姓名 |

**返回示例**

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": { "personId": "0000", "personName": "张三" },
  "time": "2026-11-13 10:20:31"
}
```

未命中时：

```json
{ "code": 200, "success": true, "msg": "操作成功", "data": null, "time": "2026-11-13 10:20:31" }
```

### 3.5 人脸库列表

- **请求**：`GET /face/list`
- **说明**：一次性返回数据库中未删除的全部人员，不分页。为减少传输量，返回结果**不含** `feature` 特征向量（该字段为 `null`）。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 |
| --- | --- | --- | --- | --- |
| 无 | - | - | - | 无入参 |

**返回参数（data 为数组，元素结构如下）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `personId` | string | 人员编号 |
| `personName` | string | 人员姓名 |
| `feature` | array\<float\> | 人脸特征向量，列表接口固定返回 `null` |
| `imageUrl` | string | 人脸图片访问地址（MinIO URL 或本地绝对路径） |

**返回示例**

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": [
    { "personId": "0000", "personName": "张三", "feature": null, "imageUrl": "http://127.0.0.1:9000/javavision/face/xxx.jpg" }
  ],
  "time": "2026-11-13 10:20:31"
}
```

---

## 四、以图搜图应用 `/image`

图片向量库由配置项 `image.service` 决定（`vectorex` / `milvus` / `es`），原图存对象存储，
元数据（含 32 位 MD5、向量维度）存 PostgreSQL 的 `vision_image` 表。

### 4.1 图片入库

- **请求**：`POST /image/add`
- **Content-Type**：`multipart/form-data`
- **说明**：提取图片向量写入向量库，原图存对象存储，元数据写数据库。
  相同 MD5 的图片会复用已缓存的向量，避免重复推理。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `imageId` | query / form | string | 是 | 图片业务编号，由调用方指定，未删除记录内唯一 | `img-0001` |
| `file` | form(file) | file | 是 | 图片文件，jpg/jpeg/png | `cat.jpg` |

**返回参数**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | null | 无业务数据 |

### 4.2 以图搜图

- **请求**：`POST /image/search`
- **Content-Type**：`multipart/form-data`
- **说明**：计算上传图片的向量，在向量库中做相似度检索，按得分从高到低返回前 K 条。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `k` | query / form | int | 否 | 返回条数，不传或 ≤ 0 时默认 **10** | `10` |
| `file` | form(file) | file | 是 | 待检索的图片文件 | `query.jpg` |

**返回参数（data）**

数组，元素为 [2.2 SearchResult](#22-searchresult--以图搜图命中结果)：

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `url` | string | 命中图片的访问地址 |
| `imageId` | string | 命中图片的业务编号 |
| `score` | float | 相似度得分，0~1，越大越相似 |

**返回示例**

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": [
    { "url": "http://127.0.0.1:9000/javavision/image/cat.jpg", "imageId": "img-0001", "score": 0.9312 }
  ],
  "time": "2026-11-13 10:20:31"
}
```

### 4.3 删除图片

- **请求**：`POST /image/del`
- **Content-Type**：`multipart/form-data`（也可用 `application/x-www-form-urlencoded`）
- **说明**：按 `imageId` 删除向量库中的向量、对象存储中的原图，并将数据库元数据标记为已删除（逻辑删除）。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `imageId` | query / form | string | 是 | 要删除的图片业务编号 | `img-0001` |

**返回参数**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | null | 无业务数据 |

### 4.4 图库列表（分页）

- **请求**：`GET /image/list`
- **说明**：从 PostgreSQL 元数据表分页查询未删除的图片记录，不返回向量本身。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `page` | query / form | int | 否 | 页码，从 1 开始，默认 1 | `1` |
| `size` | query / form | int | 否 | 每页条数，默认 10，最大 200 | `10` |

**返回参数（data）**

`PageResult`，结构见 [2.3](#23-pageresultt--分页结果)，`records` 元素结构如下：

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `id` | long | 主键，数据库自增 |
| `imageId` | string | 图片业务编号 |
| `url` | string | 图片访问地址（MinIO URL 或本地绝对路径） |
| `md5` | string | 原图内容摘要，32 位小写 MD5，用于去重与向量缓存命中 |
| `vectorDim` | int | 图片向量维度 |
| `indexType` | string | 向量库类型：`vectorex` / `milvus` / `es` |
| `createTime` | string | 创建时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `updateTime` | string | 最后更新时间，格式 `yyyy-MM-dd HH:mm:ss` |

**返回示例**

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "page": 1,
    "size": 10,
    "pages": 1,
    "records": [
      {
        "id": 1,
        "imageId": "img-0001",
        "url": "http://127.0.0.1:9000/javavision/image/cat.jpg",
        "md5": "9e107d9d372bb6826bd81d3542a419d6",
        "vectorDim": 512,
        "indexType": "vectorex",
        "createTime": "2026-11-13 10:00:00",
        "updateTime": "2026-11-13 10:00:00"
      }
    ]
  },
  "time": "2026-11-13 10:20:31"
}
```

---

## 五、OCR 与目标检测

所有接口均为 `POST` + `multipart/form-data`，文件字段名固定为 `file`。
同一能力提供两个版本：纯文本版返回拼接后的字符串，`/json` 版返回结构化数组。

### 5.1 文字识别 —— 纯文本（RapidOcr）

- **请求**：`POST /ocr`
- **说明**：使用 RapidOcr 引擎识别图片中的文字，多个文本块按行拼接为一个字符串返回。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `key` | query / form | string | 否 | OCR 补充参数，当前版本未使用，可不传 | - |
| `content` | query / form | array\<string\> | 否 | OCR 补充参数，当前版本未使用，可不传 | - |
| `file` | form(file) | file | 是 | 待识别的图片文件 | `text.png` |

**返回参数（data）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | string | 识别出的全部文字，多个文本块以换行分隔；未识别到文字时为空字符串 |

**返回示例**

```json
{ "code": 200, "success": true, "msg": "操作成功", "data": "安全生产\n人人有责", "time": "2026-11-13 10:20:31" }
```

### 5.2 文字识别 —— 结构化数组（RapidOcr）

- **请求**：`POST /ocr/json`
- **说明**：返回每个文本块的文字、四点坐标、检测置信度、方向类别与各阶段耗时。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待识别的图片文件 | `text.png` |

**返回参数（data 为数组，元素结构如下）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `text` | string | 识别出的文字内容 |
| `boxPoint` | array | 文本框顶点坐标，4 个点按顺时针排列，每个点含 `x`、`y`（像素） |
| `boxScore` | float | 文本框检测置信度，0~1 |
| `angleIndex` | int | 文本方向类别：0=0°、1=90°、2=180°、3=270° |
| `angleScore` | float | 文本方向分类置信度，0~1 |
| `angleTime` | double | 文本方向分类耗时，单位毫秒 |
| `charScores` | array\<float\> | 每个字符的置信度，与 `text` 字符一一对应，0~1 |
| `crnnTime` | double | 文字识别（CRNN）耗时，单位毫秒 |
| `blockTime` | double | 该文本块的总处理耗时，单位毫秒 |

**返回示例**

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": [
    {
      "text": "安全生产",
      "boxPoint": [{ "x": 12, "y": 30 }, { "x": 210, "y": 30 }, { "x": 210, "y": 66 }, { "x": 12, "y": 66 }],
      "boxScore": 0.95,
      "angleIndex": 0,
      "angleScore": 0.99,
      "angleTime": 1.2,
      "charScores": [0.99, 0.98, 0.97, 0.96],
      "crnnTime": 5.4,
      "blockTime": 8.1
    }
  ],
  "time": "2026-11-13 10:20:31"
}
```

### 5.3 文字识别 —— 纯文本（DJL）

- **请求**：`POST /word`
- **说明**：使用 DJL 加载的文字识别模型识别图片，多个文本块拼接为一个字符串返回。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待识别的图片文件 | `text.png` |

**返回参数（data）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | string | 识别出的全部文字；未识别到文字时为空字符串 |

### 5.4 文字识别 —— 结构化数组（DJL）

- **请求**：`POST /word/json`
- **说明**：返回每个文本块的文字内容、逐字符置信度与四点坐标。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待识别的图片文件 | `text.png` |

**返回参数（data 为数组，元素结构如下）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `text` | string | 识别出的文字内容 |
| `charScores` | array\<float\> | 每个字符的置信度，与 `text` 字符一一对应，0~1 |
| `boxPoint` | array | 文本框顶点坐标，固定 4 个点，按顺时针排列；每个点含 `x`、`y`（像素） |

### 5.5 物体识别 —— 多行文本（YOLOv8 通用模型）

- **请求**：`POST /yolo8`
- **说明**：使用 YOLOv8 通用模型检测图片中的物体，每条结果拼成一行文本返回，便于人工查看与日志打印。需要程序解析请用 `/yolo8/json`。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待检测的图片文件 | `street.jpg` |

**返回参数（data）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | string | 每条检测结果一行，含类别、类别编号、左上角与右下角坐标、置信度；未检测到物体时为空字符串 |

**返回示例**

```
  label=person 	 clsId=0 	 x0=100.0 	 y0=120.0 	 x1=260.0 	 y1=380.0 	 score=0.92
```

### 5.6 物体识别 —— 结构化数组（YOLOv8 通用模型）

- **请求**：`POST /yolo8/json`
- **说明**：返回类别、类别编号、检测框像素坐标与置信度。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待检测的图片文件 | `street.jpg` |

**返回参数（data 为数组，元素结构如下）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `label` | string | 类别标签名称，如 `person`、`car`、`dog` |
| `clsId` | int | 类别编号，对应模型 labels 列表下标，从 0 开始 |
| `bbox` | array\<float\> | 检测框坐标，长度 4：左上角 x、左上角 y、右下角 x、右下角 y（**像素坐标**） |
| `confidence` | float | 置信度，0~1，越大越可信 |

**返回示例**

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": [
    { "label": "person", "clsId": 0, "bbox": [100.0, 120.0, 260.0, 380.0], "confidence": 0.92 }
  ],
  "time": "2026-11-13 10:20:31"
}
```

### 5.7 火焰烟雾检测

- **请求**：`POST /fire/json`
- **说明**：火焰烟雾专用模型检测图片中的火焰与烟雾，坐标为**归一化**后的 0~1 数值。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待检测的图片文件 | `fire.jpg` |

**返回参数（data）**

数组，元素为 [2.1 DetectResult](#21-detectresult--目标检测结果)，`className` 为 `fire` 或 `smoke`；未检测到时为空数组。

### 5.8 反光衣检测

- **请求**：`POST /reflective/json`
- **说明**：检测图片中人员是否正确穿着反光衣，坐标为**归一化**后的 0~1 数值。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待检测的图片文件 | `site.jpg` |

**返回参数（data）**

数组，元素为 [2.1 DetectResult](#21-detectresult--目标检测结果)；未检测到时为空数组。

### 5.9 安全帽检测

- **请求**：`POST /construction/json`
- **说明**：检测图片中人员是否佩戴安全帽，坐标为**归一化**后的 0~1 数值。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待检测的图片文件 | `site.jpg` |

**返回参数（data）**

数组，元素为 [2.1 DetectResult](#21-detectresult--目标检测结果)；未检测到时为空数组。

---

## 六、系统状态 `/system`

用于部署后的健康检查与缓存运维。

### 6.1 中间件连接状态

- **请求**：`GET /system/status`
- **说明**：依次探测 PostgreSQL、Redis 与对象存储，返回连接结果与统计数据。**不会抛出异常**，连接失败时在对应节点给出 `error` 字段。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 |
| --- | --- | --- | --- | --- |
| 无 | - | - | - | 无入参 |

**返回参数（data）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `postgres` | object | PostgreSQL 状态，结构见下 |
| `redis` | object | Redis 状态，结构见下 |
| `storage` | object | 对象存储状态，结构见下 |
| `inference` | object | 推理设备状态，结构见下 |

`postgres` 子字段：

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `connected` | boolean | 是否连通 |
| `url` | string | 数据库连接地址 |
| `product` | string | 数据库产品名 |
| `version` | string | 数据库版本 |
| `faceCount` | long | 人脸库记录数（不含已逻辑删除） |
| `imageCount` | long | 图库记录数（不含已逻辑删除） |
| `error` | string | 连接失败原因，仅在 `connected = false` 时出现 |
| `countError` | string | 统计记录数失败原因，仅在统计异常时出现 |

`redis` 子字段：

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `connected` | boolean | 是否连通 |
| `faceFeatureCached` | boolean | 人脸特征缓存是否已存在 |
| `usedMemory` | string | 已用内存可读值，如 `1.2M`；连接失败时不返回 |
| `usedMemoryBytes` | string | 已用内存字节数；连接失败时不返回 |

`storage` 子字段：

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `type` | string | 存储类型：`minio` 表示远程对象存储，`local` 表示本地目录 |

`inference` 子字段：

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `device` | string | 实际推理设备：`CPU` 或 `GPU` |
| `flavor` | string | 产物自带的原生库类型：`cpu` 或 `cu121` |
| `gpuActive` | boolean | 本次进程是否真正启用了 GPU |
| `gpuName` | string | 探测到的显卡型号，未检测到该字段不出现 |
| `computeCapability` | string | 显卡算力，如 `6.1`；驱动不支持查询时为 `未知` |
| `gpuArchitecture` | string | 按算力推断的微架构：Pascal / Turing / Ampere / Ada Lovelace / Hopper / Blackwell |
| `summary` | string | 决策摘要，含判定原因，用于排查"为什么没用上显卡" |
| `warmup` | object | 模型预热状态，结构见下 |

> 推理设备在进程启动时定稿，本接口只读取结果，不会触发重新探测或更换设备。
> `computeCapability` 落在 5.0~9.0 之外（如 Blackwell 的 10.0+）时，cu121 版原生库没有对应 kernel，会自动回落 CPU 并在 `summary` 中说明。

`inference.warmup` 子字段：

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `enabled` | boolean | 是否开启启动预热（配置项 `vision.inference.warmup.enabled`） |
| `async` | boolean | 是否后台预热（配置项 `vision.inference.warmup.async`）；`false` 表示阻塞到全部模型加载完才对外服务 |
| `predict` | boolean | 是否在加载后各跑一次空推理（配置项 `vision.inference.warmup.predict`），用于把 JIT 与算子首次初始化也走热 |
| `state` | string | 预热状态：`未开始` / `预热中` / `完成` / `部分失败` / `已关闭` |
| `loaded` | int | 已成功加载的模型数量，全部加载完为 7 |
| `failed` | int | 加载失败的模型数量，失败项会在首次请求时重试 |
| `costMs` | long | 预热总耗时（毫秒），`-1` 表示尚未开始或尚未结束 |

> 预热只影响"何时加载"，不改变"只加载一次"：模型仍常驻内存，`Predictor` 仍按请求新建。
> 建议在编排层把这个字段当作就绪探针：`state = 完成` 时再放流量，可避免首个请求超时。

**返回示例**

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": {
    "postgres": {
      "connected": true,
      "url": "jdbc:postgresql://127.0.0.1:5432/java_vision",
      "product": "PostgreSQL",
      "version": "12.17",
      "faceCount": 0,
      "imageCount": 0
    },
    "redis": { "connected": true, "faceFeatureCached": false, "usedMemory": "1.2M", "usedMemoryBytes": "1258291" },
    "storage": { "type": "minio" },
    "inference": {
      "device": "CPU",
      "flavor": "cpu",
      "gpuActive": false,
      "gpuName": "NVIDIA GeForce GTX 1080",
      "computeCapability": "6.1",
      "gpuArchitecture": "Pascal",
      "summary": "产物=cpu, 期望=auto, 结果=CPU, 原因=当前产物为 CPU 版原生库（flavor=cpu），不具备 CUDA 能力",
      "warmup": {
        "enabled": true,
        "async": true,
        "predict": true,
        "state": "完成",
        "loaded": 7,
        "failed": 0,
        "costMs": 3800
      }
    }
  },
  "time": "2026-11-13 10:20:31"
}
```

### 6.2 清理缓存

- **请求**：`POST /system/cache/clear`
- **说明**：清理 Redis 中的人脸特征缓存（`javavision:face:features`）与全部图片向量缓存（`javavision:image:vector:*`）。
  下次检索时会自动回源数据库与向量库重建。**仅影响缓存，不删除任何业务数据。**

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 |
| --- | --- | --- | --- | --- |
| 无 | - | - | - | 无入参 |

**返回参数（data）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `imageVectorKeys` | long | 删除的图片向量缓存键数量 |
| `faceFeatureCleared` | boolean | 人脸特征缓存是否已清理 |

**返回示例**

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": { "imageVectorKeys": 12, "faceFeatureCleared": true },
  "time": "2026-11-13 10:20:31"
}
```

---

## 七、虹软人脸应用 `/arc/soft/face`

虹软 SDK 提取特征 + **Milvus** 存储的人脸库，与 `/face` 接口数据相互独立，需要部署 Milvus 才能使用。

### 7.1 添加人脸

- **请求**：`POST /arc/soft/face/add`
- **Content-Type**：`multipart/form-data`
- **说明**：提取人脸特征写入 Milvus 集合 `face_collection`，`personId` 作为主键，已存在时会覆盖。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `personId` | query / form | string | 是 | 人员编号，Milvus 集合主键 | `0000` |
| `personName` | query / form | string | 是 | 人员姓名 | `张三` |
| `file` | form(file) | file | 是 | 人脸图片文件 | `zhangsan.jpg` |

**返回参数**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | null | 无业务数据 |

### 7.2 更新人脸

- **请求**：`POST /arc/soft/face/update`
- **Content-Type**：`multipart/form-data`
- **说明**：按 `personId` 覆盖更新 Milvus 中的人员姓名、图片地址与特征向量。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `personId` | query / form | string | 是 | 人员编号，已存在则更新 | `0000` |
| `personName` | query / form | string | 是 | 人员姓名 | `张三` |
| `file` | form(file) | file | 是 | 新的人脸图片文件 | `zhangsan_new.jpg` |

**返回参数**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | null | 无业务数据 |

### 7.3 删除人脸

- **请求**：`POST /arc/soft/face/del`
- **Content-Type**：`multipart/form-data`（也可用 `application/x-www-form-urlencoded`）
- **说明**：按 `personId` 删除 Milvus 集合中的记录，同时清理对象存储中的人脸图片。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `personId` | query / form | string | 是 | 要删除的人员编号 | `0000` |

**返回参数**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `data` | null | 无业务数据 |

### 7.4 人脸查询（Milvus 向量检索）

- **请求**：`POST /arc/soft/face/search`
- **Content-Type**：`multipart/form-data`
- **说明**：提取上传图片的特征，在 Milvus 中做向量相似度检索，返回最相似的一条人员记录。未达相似度阈值时 `data` 为 `null`。

**请求参数**

| 参数名 | 位置 | 类型 | 必填 | 中文说明 | 示例 |
| --- | --- | --- | --- | --- | --- |
| `file` | form(file) | file | 是 | 待检索的人脸图片文件 | `unknown.jpg` |

**返回参数（data）**

| 字段 | 类型 | 中文说明 |
| --- | --- | --- |
| `personId` | string | 人员编号，集合主键 |
| `personName` | string | 人员姓名 |
| `url` | string | 人脸图片访问地址 |
| `faceVector` | array\<float\> | 人脸特征向量，256 维，使用内积（IP）度量相似度 |

**返回示例**

```json
{
  "code": 200,
  "success": true,
  "msg": "操作成功",
  "data": {
    "personId": "0000",
    "personName": "张三",
    "url": "http://127.0.0.1:9000/javavision/face/xxx.jpg",
    "faceVector": [0.0123, -0.0456, 0.0789]
  },
  "time": "2026-11-13 10:20:31"
}
```

---

## 八、接口清单速查

| 序号 | 方法 | 路径 | 中文名称 | 返回 data 类型 |
| --- | --- | --- | --- | --- |
| 1 | POST | `/face/add` | 添加人脸 | null |
| 2 | POST | `/face/update` | 更新人脸 | null |
| 3 | POST | `/face/del` | 删除人脸 | null |
| 4 | POST | `/face/search` | 人脸查询（1:N） | PersonObject |
| 5 | GET | `/face/list` | 人脸库列表 | FaceParam[] |
| 6 | POST | `/image/add` | 图片入库 | null |
| 7 | POST | `/image/search` | 以图搜图 | SearchResult[] |
| 8 | POST | `/image/del` | 删除图片 | null |
| 9 | GET | `/image/list` | 图库列表（分页） | PageResult\<ImageRecord\> |
| 10 | POST | `/ocr` | 文字识别（纯文本，RapidOcr） | string |
| 11 | POST | `/ocr/json` | 文字识别（结构化，RapidOcr） | TextBlock[] |
| 12 | POST | `/word` | 文字识别（纯文本，DJL） | string |
| 13 | POST | `/word/json` | 文字识别（结构化，DJL） | WordBlock[] |
| 14 | POST | `/yolo8` | 物体识别（多行文本，YOLOv8） | string |
| 15 | POST | `/yolo8/json` | 物体识别（结构化，YOLOv8） | Detection[] |
| 16 | POST | `/fire/json` | 火焰烟雾检测 | DetectResult[] |
| 17 | POST | `/reflective/json` | 反光衣检测 | DetectResult[] |
| 18 | POST | `/construction/json` | 安全帽检测 | DetectResult[] |
| 19 | GET | `/system/status` | 中间件连接状态 | Map |
| 20 | POST | `/system/cache/clear` | 清理缓存 | Map |
| 21 | POST | `/arc/soft/face/add` | 虹软-添加人脸 | null |
| 22 | POST | `/arc/soft/face/update` | 虹软-更新人脸 | null |
| 23 | POST | `/arc/soft/face/del` | 虹软-删除人脸 | null |
| 24 | POST | `/arc/soft/face/search` | 虹软-人脸查询 | Face |

---

## 九、文档维护说明

本文档与代码注解同源，接口变更时请同步修改以下两处：

1. **Controller 注解**（决定 knife4j 在线文档的内容）
   - 类上：`@Tag(name = "模块名", description = "模块说明与统一响应结构")`
   - 方法上：`@Operation(summary = "接口中文名", description = "业务说明、边界情况、data 为 null 的条件")`
   - 返回说明：`@ApiResponse(responseCode = "200", description = "成功时 data 的含义与异常情况")`
   - 入参：`@Parameter(description = "中文说明", required = true, example = "示例值")`
   - 文件参数：`@RequestPart(value = "file")`，且 `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
2. **返回对象字段**：在 DTO 字段上加 `@Schema(description = "中文说明", example = "示例值")`

统一响应结构的包装由 `ApiResultOpenApiCustomizer` 自动完成，无需在每个接口上重复声明。
`application.yml` 中的 `springdoc.override-with-generic-response: false` 必须保持关闭，
否则 `@ApiResponse` 中定义的中文说明会被 `GlobalExceptionHandler` 的通用响应覆盖。
