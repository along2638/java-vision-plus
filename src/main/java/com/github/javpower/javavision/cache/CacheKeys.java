package com.github.javpower.javavision.cache;

/**
 * Redis 缓存 key 统一定义，避免各处硬编码导致 key 冲突。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static final String PREFIX = "javavision:";

    /**
     * 人脸特征全量列表（检索时避免全表扫描）
     */
    public static final String FACE_FEATURES = PREFIX + "face:features";

    /**
     * 以图搜图的特征向量，按图片内容 MD5 缓存，避免重复推理
     */
    public static String imageVector(String md5) {
        return PREFIX + "image:vector:" + md5;
    }

    public static String imageVectorPattern() {
        return PREFIX + "image:vector:*";
    }
}
