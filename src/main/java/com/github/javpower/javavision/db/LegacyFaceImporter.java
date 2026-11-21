package com.github.javpower.javavision.db;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.javpower.javavision.config.FileProperties;
import com.github.javpower.javavision.db.mapper.FaceFeatureRecordMapper;
import com.github.javpower.javavision.db.mapper.FaceRecordMapper;
import com.github.javpower.javavision.entity.FaceParam;
import com.github.javpower.javavision.storage.LocalFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 历史数据迁移：改造前人脸库以 image/face/{personId}.json 形式存放，
 * 首次启动（人员表为空）时自动导入，避免升级后数据"消失"。
 * <p>
 * 导入后保留原 JSON 文件作为备份，不做删除。
 */
@Slf4j
@Component
public class LegacyFaceImporter implements ApplicationRunner {

    private final FaceRecordMapper personMapper;

    private final FaceFeatureRecordMapper featureMapper;

    private final FileProperties fileProperties;

    public LegacyFaceImporter(FaceRecordMapper personMapper,
                              FaceFeatureRecordMapper featureMapper,
                              FileProperties fileProperties) {
        this.personMapper = personMapper;
        this.featureMapper = featureMapper;
        this.fileProperties = fileProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (personMapper.selectCount(null) > 0) {
                return;
            }
            Path dir = LocalFileStorage.resolveDir(fileProperties.getUploadPath(), "image").resolve("face");
            if (!Files.isDirectory(dir)) {
                return;
            }
            int imported = 0;
            try (Stream<Path> files = Files.list(dir)) {
                List<Path> jsonFiles = files
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .collect(Collectors.toList());
                for (Path p : jsonFiles) {
                    FaceParam param;
                    try {
                        param = JSONUtil.toBean(
                                new String(Files.readAllBytes(p), StandardCharsets.UTF_8), FaceParam.class);
                    } catch (Exception e) {
                        log.warn("历史人脸数据解析失败，已跳过：{}", p, e);
                        continue;
                    }
                    if (param.getPersonId() == null || existsPerson(param.getPersonId())) {
                        continue;
                    }
                    FaceRecord person = new FaceRecord();
                    person.setPersonId(param.getPersonId());
                    person.setPersonName(param.getPersonName());
                    person.setImageUrl(param.getImageUrl());
                    person.setFeatureDim(param.getFeature() == null ? null : param.getFeature().size());
                    personMapper.insert(person);
                    if (CollectionUtil.isNotEmpty(param.getFeature())) {
                        FaceFeatureRecord feature = new FaceFeatureRecord();
                        feature.setFaceId(person.getId());
                        feature.setFeature(param.getFeature());
                        feature.setFeatureDim(param.getFeature().size());
                        featureMapper.insert(feature);
                    }
                    imported++;
                }
            }
            if (imported == 0) {
                return;
            }
            log.info("已将 {} 条历史人脸数据从 JSON 文件导入 PostgreSQL（原文件保留在 {}）", imported, dir);
        } catch (Exception e) {
            // 迁移失败不应阻塞启动，业务可后续手工处理
            log.warn("历史人脸数据导入失败：{}", e.toString());
        }
    }

    private boolean existsPerson(String personId) {
        return personMapper.selectCount(Wrappers.<FaceRecord>lambdaQuery()
                .eq(FaceRecord::getPersonId, personId)) > 0;
    }
}
