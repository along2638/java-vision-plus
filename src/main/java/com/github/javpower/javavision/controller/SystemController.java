package com.github.javpower.javavision.controller;

import com.github.javpower.javavision.cache.CacheKeys;
import com.github.javpower.javavision.cache.RedisService;
import com.github.javpower.javavision.config.InferenceWarmup;
import com.github.javpower.javavision.db.mapper.FaceRecordMapper;
import com.github.javpower.javavision.db.mapper.ImageRecordMapper;
import com.github.javpower.javavision.storage.StorageService;
import com.github.javpower.javavision.util.InferenceDevice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中间件状态与缓存运维接口。
 */
@Slf4j
@RestController
@RequestMapping("/system")
@Tag(name = "系统状态", description = "PostgreSQL / Redis / 对象存储的连通性自检与缓存清理，用于部署后的健康检查。"
        + "所有接口返回统一结构 ApiResult：{code 响应码, success 是否成功, msg 提示信息, data 业务数据, time 响应时间}")
public class SystemController {

    private final DataSource dataSource;

    private final RedisService redisService;

    private final FaceRecordMapper faceRecordMapper;

    private final ImageRecordMapper imageRecordMapper;

    private final StorageService storageService;

    private final InferenceWarmup inferenceWarmup;

    public SystemController(DataSource dataSource, RedisService redisService,
                            FaceRecordMapper faceRecordMapper,
                            ImageRecordMapper imageRecordMapper,
                            StorageService storageService,
                            InferenceWarmup inferenceWarmup) {
        this.dataSource = dataSource;
        this.redisService = redisService;
        this.faceRecordMapper = faceRecordMapper;
        this.imageRecordMapper = imageRecordMapper;
        this.storageService = storageService;
        this.inferenceWarmup = inferenceWarmup;
    }

    @GetMapping("/status")
    @Operation(summary = "中间件连接状态",
            description = "依次探测 PostgreSQL、Redis 与对象存储，返回连接结果与统计数据，不会抛出异常（连接失败时在对应节点给出 error 字段）。"
                    + "data 结构："
                    + "postgres{connected 是否连通, url 连接地址, product 数据库产品名, version 数据库版本, "
                    + "faceCount 人脸库记录数, imageCount 图库记录数（均不含已逻辑删除）, error 连接失败原因}；"
                    + "redis{connected 是否连通, faceFeatureCached 人脸特征缓存是否命中, usedMemory 已用内存可读值, usedMemoryBytes 已用内存字节数}；"
                    + "storage{type 存储类型，minio 或 local}；"
                    + "inference{device 实际推理设备 CPU 或 GPU, flavor 产物原生库类型 cpu 或 cu121, "
                    + "gpuActive 是否启用 GPU, summary 决策摘要（含判定原因）, "
                    + "warmup{enabled 是否开启预热, async 是否后台预热, predict 是否含空推理预热, "
                    + "state 预热状态（未开始/预热中/完成/部分失败/已关闭）, "
                    + "loaded 已加载模型数, failed 加载失败数, costMs 总耗时毫秒，-1 表示尚未完成}}")
    @ApiResponse(responseCode = "200", description = "探测完成，data 为 postgres / redis / storage / inference 四部分状态对象")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postgres", postgresStatus());
        result.put("redis", redisStatus());
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("type", storageService.isRemote() ? "minio" : "local");
        result.put("storage", storage);
        // 推理设备在启动时定稿，这里只读取结果，不会触发重新探测
        Map<String, Object> inference = InferenceDevice.status();
        // 模型预热在后台进行，用状态字段告诉调用方"现在是否已就绪"
        inference.put("warmup", inferenceWarmup.snapshot());
        result.put("inference", inference);
        return result;
    }

    @PostMapping("/cache/clear")
    @Operation(summary = "清理缓存",
            description = "清理 Redis 中的人脸特征缓存（javavision:face:features）与全部图片向量缓存（javavision:image:vector:*），"
                    + "下次检索时会自动回源 PostgreSQL 与向量库重建。仅影响缓存，不删除任何业务数据。")
    @ApiResponse(responseCode = "200", description = "清理完成，data 结构：imageVectorKeys 删除的图片向量缓存键数量，faceFeatureCleared 人脸特征缓存是否已清理")
    public Map<String, Object> clearCache() {
        long imageRemoved = redisService.deleteByPrefix(CacheKeys.imageVectorPattern());
        redisService.delete(CacheKeys.FACE_FEATURES);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imageVectorKeys", imageRemoved);
        result.put("faceFeatureCleared", true);
        return result;
    }

    private Map<String, Object> postgresStatus() {
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            db.put("connected", true);
            db.put("url", metaData.getURL());
            db.put("product", metaData.getDatabaseProductName());
            db.put("version", metaData.getDatabaseProductVersion());
        } catch (Exception e) {
            db.put("connected", false);
            db.put("error", e.getMessage());
            return db;
        }
        try {
            // selectCount(null) 由 @TableLogic 自动过滤已删除记录
            db.put("faceCount", faceRecordMapper.selectCount(null));
            db.put("imageCount", imageRecordMapper.selectCount(null));
        } catch (Exception e) {
            db.put("countError", e.getMessage());
        }
        return db;
    }

    private Map<String, Object> redisStatus() {
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("connected", redisService.ping());
        cache.put("faceFeatureCached", redisService.get(CacheKeys.FACE_FEATURES).isPresent());
        Map<String, String> memory = redisService.info("memory");
        if (!memory.isEmpty()) {
            cache.put("usedMemory", memory.get("used_memory_human"));
            cache.put("usedMemoryBytes", memory.get("used_memory"));
        }
        return cache;
    }
}
