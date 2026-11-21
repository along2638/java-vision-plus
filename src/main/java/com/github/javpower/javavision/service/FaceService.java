package com.github.javpower.javavision.service;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.javpower.javavision.cache.CacheKeys;
import com.github.javpower.javavision.cache.RedisService;
import com.github.javpower.javavision.db.FaceFeatureRecord;
import com.github.javpower.javavision.db.FaceRecord;
import com.github.javpower.javavision.db.mapper.FaceFeatureRecordMapper;
import com.github.javpower.javavision.db.mapper.FaceRecordMapper;
import com.github.javpower.javavision.entity.FaceObject;
import com.github.javpower.javavision.entity.FaceParam;
import com.github.javpower.javavision.entity.PersonObject;
import com.github.javpower.javavision.framework.exception.BusinessException;
import com.github.javpower.javavision.util.FaceDetectUtil;
import com.github.javpower.javavision.util.FaceSimilarityCalculator;
import com.github.javpower.javavision.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 人脸库：图片走 StorageService（MinIO/本地），人员信息存 vision_face_person、
 * 特征向量存 vision_face_feature，全量特征列表再缓存到 Redis，
 * 检索时避免每次全表扫描 + 反序列化。
 * <p>
 * 删除为逻辑删除（is_deleted = 1），人员图片与特征同步清理。
 */
@Service
@Slf4j
public class FaceService {

    /**
     * 人脸相似度阈值，低于该值视为未匹配
     */
    private static final float MATCH_THRESHOLD = 0.8f;

    private static final String FACE_PREFIX = "face";

    private final FileUtil fileUtil;

    private final FaceRecordMapper personMapper;

    private final FaceFeatureRecordMapper featureMapper;

    private final RedisService redis;

    @Value("${cache.face.ttl-minutes:30}")
    private long cacheTtlMinutes;

    public FaceService(FileUtil fileUtil, FaceRecordMapper personMapper,
                       FaceFeatureRecordMapper featureMapper, RedisService redis) {
        this.fileUtil = fileUtil;
        this.personMapper = personMapper;
        this.featureMapper = featureMapper;
        this.redis = redis;
    }

    @Transactional
    public void addFace(FaceParam param, MultipartFile file, HttpServletRequest request) throws IOException, TranslateException, ModelNotFoundException, MalformedModelException, OrtException {
        if (existsPerson(param.getPersonId())) {
            throw new BusinessException("用户已存在：" + param.getPersonId());
        }
        String url = fileUtil.upload(file, FACE_PREFIX);
        List<Float> feature = extractFeatureOrCleanup(url);
        param.setFeature(feature);
        param.setImageUrl(url);

        FaceRecord record = new FaceRecord();
        record.setPersonId(param.getPersonId());
        record.setPersonName(param.getPersonName());
        record.setImageUrl(url);
        record.setFeatureDim(feature.size());
        // 主键由数据库 identity 生成，insert 后回填到实体
        personMapper.insert(record);
        saveFeature(record.getId(), feature);

        evictCache();
        log.info("人脸入库成功：personId={}, id={}, url={}", param.getPersonId(), record.getId(), url);
    }

    @Transactional
    public void updateFace(FaceParam param, MultipartFile file, HttpServletRequest request) throws TranslateException, ModelNotFoundException, MalformedModelException, IOException, OrtException {
        FaceRecord record = findByPersonId(param.getPersonId());
        if (record == null) {
            throw new BusinessException("无该用户：" + param.getPersonId());
        }
        String oldUrl = record.getImageUrl();
        String url = fileUtil.upload(file, FACE_PREFIX);
        List<Float> feature = extractFeatureOrCleanup(url);

        record.setPersonName(param.getPersonName());
        record.setImageUrl(url);
        record.setFeatureDim(feature.size());
        personMapper.updateById(record);
        saveFeature(record.getId(), feature);

        evictCache();
        // 替换成功后再清理旧图，避免中途失败导致两个地址都不可用
        deleteQuietly(oldUrl);
        log.info("人脸更新成功：personId={}, url={}", param.getPersonId(), url);
    }

    @Transactional
    public void delFace(FaceParam param, MultipartFile file, HttpServletRequest request) {
        FaceRecord record = findByPersonId(param.getPersonId());
        if (record == null) {
            throw new BusinessException("无该用户：" + param.getPersonId());
        }
        // 人员为逻辑删除（保留审计），特征与图片属于可再生的派生数据，直接清理
        personMapper.update(null, Wrappers.<FaceRecord>lambdaUpdate()
                .set(FaceRecord::getDeleted, 1)
                .set(FaceRecord::getUpdateTime, LocalDateTime.now())
                .eq(FaceRecord::getId, record.getId()));
        featureMapper.delete(Wrappers.<FaceFeatureRecord>lambdaQuery()
                .eq(FaceFeatureRecord::getFaceId, record.getId()));
        evictCache();
        deleteQuietly(record.getImageUrl());
        log.info("人脸删除成功：personId={}", param.getPersonId());
    }

    public PersonObject searchFace(FaceParam param, MultipartFile file, HttpServletRequest request) throws IOException, TranslateException, ModelNotFoundException, MalformedModelException, OrtException {
        List<FaceParam> candidates = loadFeatures();
        if (CollectionUtil.isEmpty(candidates)) {
            return null;
        }
        // 待检索的图片只做一次推理，不需要进对象存储
        String path = fileUtil.getTempPath(file);
        try {
            List<FaceObject> faceObjects = FaceDetectUtil.faceDetect(path);
            if (CollectionUtil.isEmpty(faceObjects)) {
                return null;
            }
            Float[] feature = faceObjects.get(0).getFeature().toArray(new Float[0]);
            FaceParam best = null;
            float bestScore = 0f;
            for (FaceParam candidate : candidates) {
                if (CollectionUtil.isEmpty(candidate.getFeature())) {
                    continue;
                }
                float score = FaceSimilarityCalculator.calculateCosineSimilarity(
                        feature, candidate.getFeature().toArray(new Float[0]));
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best != null && bestScore > MATCH_THRESHOLD) {
                PersonObject personObject = new PersonObject();
                personObject.setPersonName(best.getPersonName());
                personObject.setPersonId(best.getPersonId());
                return personObject;
            }
            return null;
        } finally {
            fileUtil.deleteIfNeeded(path);
        }
    }

    public boolean check(FaceParam param) {
        return param != null && param.getPersonId() != null && existsPerson(param.getPersonId());
    }

    /**
     * 人脸库列表（不返回特征向量，避免响应体过大）
     */
    public List<FaceParam> listAll() {
        List<FaceParam> list = new ArrayList<>();
        for (FaceRecord record : personMapper.selectList(null)) {
            FaceParam param = toParam(record);
            param.setFeature(null);
            list.add(param);
        }
        return list;
    }

    /**
     * 人脸特征列表：优先读 Redis，未命中则查 PG 并回填。
     */
    private List<FaceParam> loadFeatures() {
        Optional<List<FaceParam>> cached = redis.getList(CacheKeys.FACE_FEATURES, FaceParam.class);
        if (cached.isPresent() && cached.get() != null) {
            return cached.get();
        }
        List<FaceRecord> persons = personMapper.selectList(null);
        if (persons.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> faceIds = persons.stream().map(FaceRecord::getId).collect(Collectors.toList());
        List<FaceFeatureRecord> features = featureMapper.selectList(Wrappers.<FaceFeatureRecord>lambdaQuery()
                .in(FaceFeatureRecord::getFaceId, faceIds));
        Map<Long, List<Float>> featureMap = new HashMap<>();
        for (FaceFeatureRecord feature : features) {
            featureMap.put(feature.getFaceId(), feature.getFeature());
        }
        List<FaceParam> list = new ArrayList<>();
        for (FaceRecord person : persons) {
            List<Float> feature = featureMap.get(person.getId());
            if (CollectionUtil.isEmpty(feature)) {
                continue;
            }
            FaceParam param = toParam(person);
            param.setFeature(feature);
            list.add(param);
        }
        if (!list.isEmpty()) {
            redis.setList(CacheKeys.FACE_FEATURES, list, Duration.ofMinutes(cacheTtlMinutes));
        }
        return list;
    }

    private void evictCache() {
        redis.delete(CacheKeys.FACE_FEATURES);
    }

    /**
     * 按业务编号查未删除人员；@TableLogic 会自动追加 is_deleted = 0
     */
    private FaceRecord findByPersonId(String personId) {
        return personMapper.selectOne(Wrappers.<FaceRecord>lambdaQuery()
                .eq(FaceRecord::getPersonId, personId));
    }

    private boolean existsPerson(String personId) {
        return personMapper.selectCount(Wrappers.<FaceRecord>lambdaQuery()
                .eq(FaceRecord::getPersonId, personId)) > 0;
    }

    /**
     * 特征 1:1 覆盖写入：存在则更新，不存在则新增
     */
    private void saveFeature(Long faceId, List<Float> feature) {
        FaceFeatureRecord record = featureMapper.selectOne(Wrappers.<FaceFeatureRecord>lambdaQuery()
                .eq(FaceFeatureRecord::getFaceId, faceId));
        if (record == null) {
            record = new FaceFeatureRecord();
            record.setFaceId(faceId);
            record.setFeature(feature);
            record.setFeatureDim(feature.size());
            featureMapper.insert(record);
        } else {
            record.setFeature(feature);
            record.setFeatureDim(feature.size());
            featureMapper.updateById(record);
        }
    }

    /**
     * 人脸特征提取需要本地文件句柄：把存储中的图片物化到本地，用完即清理。
     */
    private List<Float> extractFeature(String url) throws IOException, TranslateException, ModelNotFoundException, MalformedModelException, OrtException {
        Path local = fileUtil.materialize(url);
        try {
            List<FaceObject> faceObjects = FaceDetectUtil.faceDetect(local.toString());
            if (CollectionUtil.isEmpty(faceObjects)) {
                throw new BusinessException("未检测到人脸：" + url);
            }
            return faceObjects.get(0).getFeature();
        } finally {
            fileUtil.release(local);
        }
    }

    /**
     * 提取失败时回滚已上传的图片，避免对象存储里留下无主文件
     */
    private List<Float> extractFeatureOrCleanup(String url) throws IOException, TranslateException, ModelNotFoundException, MalformedModelException, OrtException {
        try {
            return extractFeature(url);
        } catch (Exception e) {
            deleteQuietly(url);
            throw e;
        }
    }

    private void deleteQuietly(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            fileUtil.delete(url);
        } catch (Exception e) {
            log.warn("删除人脸图片失败（可忽略，不影响数据一致性）：{}", url, e);
        }
    }

    private FaceParam toParam(FaceRecord record) {
        FaceParam param = new FaceParam();
        param.setPersonId(record.getPersonId());
        param.setPersonName(record.getPersonName());
        param.setImageUrl(record.getImageUrl());
        return param;
    }
}
