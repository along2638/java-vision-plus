package com.github.javpower.javavision.service;

import ai.djl.ModelException;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.github.javpower.javavision.cache.CacheKeys;
import com.github.javpower.javavision.cache.RedisService;
import com.github.javpower.javavision.util.ImageFeatureUtil;
import com.github.javpower.javavision.util.ImageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 图片特征提取入口。
 * <p>
 * 同一张图（内容 MD5 相同）只推理一次，结果缓存进 Redis：
 * 既省掉重复推理，也避免"上传后为了提特征再从 MinIO 下载回来"这一趟往返。
 */
@Slf4j
@Service
public class ImageVectorService {

    private final RedisService redis;

    @Value("${cache.image.ttl-days:7}")
    private long cacheTtlDays;

    public ImageVectorService(RedisService redis) {
        this.redis = redis;
    }

    public String md5(byte[] bytes) {
        return DigestUtil.md5Hex(bytes);
    }

    /**
     * 原始特征向量（未归一化），ES 的脚本打分使用该形态
     */
    public float[] raw(byte[] bytes) throws IOException, ModelException, TranslateException, OrtException {
        String key = CacheKeys.imageVector(md5(bytes));
        Optional<String> cached = redis.get(key);
        if (cached.isPresent()) {
            try {
                List<Float> list = JSONUtil.toList(cached.get(), Float.class);
                if (list != null && !list.isEmpty()) {
                    log.debug("命中图片特征缓存：{}", key);
                    return toArray(list);
                }
            } catch (Exception e) {
                log.warn("图片特征缓存解析失败，重新推理：{}", key);
            }
        }
        float[] vector;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            Image image = ImageFactory.getInstance().fromInputStream(in);
            vector = ImageFeatureUtil.runOcr(image);
        }
        redis.set(key, JSONUtil.toJsonStr(toList(vector)), Duration.ofDays(cacheTtlDays));
        return vector;
    }

    /**
     * 归一化后的特征向量，vectorex / milvus 使用
     */
    public List<Float> normalized(byte[] bytes) throws IOException, ModelException, TranslateException, OrtException {
        return ImageUtil.normalizeVector(toList(raw(bytes)));
    }

    public byte[] read(InputStream input) throws IOException {
        try (InputStream in = input) {
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }

    public static List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float f : vector) {
            list.add(f);
        }
        return list;
    }

    private static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Float f = list.get(i);
            arr[i] = f == null ? 0f : f;
        }
        return arr;
    }
}
