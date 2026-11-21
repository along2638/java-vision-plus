package com.github.javpower.javavision.util;

import ai.djl.ModelException;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DJL 模型缓存。
 * <p>
 * 模型（原生 Module）加载一次后常驻内存；{@link ai.djl.inference.Predictor} 不是线程安全的，
 * 因此每次请求通过 {@code model.newPredictor(translator)} 新建，用完即关。
 */
@Slf4j
public final class DjlModelUtil {

    private static final Map<String, ZooModel<Image, ?>> CACHE = new ConcurrentHashMap<>();

    private static final Object LOCK = new Object();

    private DjlModelUtil() {
    }

    /**
     * 获取（首次加载）模型。
     *
     * @param key      模型唯一标识，一般用模型文件名
     * @param criteria 首次加载时使用的 Criteria
     */
    @SuppressWarnings("unchecked")
    public static <T> ZooModel<Image, T> getModel(String key, Criteria<Image, T> criteria) throws ModelException, IOException {
        ZooModel<Image, T> model = (ZooModel<Image, T>) CACHE.get(key);
        if (model != null) {
            return model;
        }
        synchronized (LOCK) {
            model = (ZooModel<Image, T>) CACHE.get(key);
            if (model == null) {
                model = ModelZoo.loadModel(criteria);
                CACHE.put(key, model);
            }
            return model;
        }
    }

    /**
     * 应用关闭时释放已加载的模型，避免原生内存泄漏。
     */
    public static void clear() {
        synchronized (LOCK) {
            for (ZooModel<Image, ?> model : CACHE.values()) {
                try {
                    model.close();
                } catch (Exception e) {
                    log.warn("释放模型失败", e);
                }
            }
            CACHE.clear();
        }
    }
}
