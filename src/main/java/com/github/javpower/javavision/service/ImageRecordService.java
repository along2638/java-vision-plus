package com.github.javpower.javavision.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.javpower.javavision.db.ImageRecord;
import com.github.javpower.javavision.db.mapper.ImageRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 图库元数据（PostgreSQL）。
 * 向量仍由各自的向量库负责，这里只维护业务属性，便于列表查询与删除时定位原图。
 * <p>
 * 删除为逻辑删除（is_deleted = 1），记录保留用于审计。
 */
@Slf4j
@Service
public class ImageRecordService {

    private final ImageRecordMapper mapper;

    public ImageRecordService(ImageRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void save(String imageId, String url, String md5, Integer vectorDim, String indexType) {
        if (imageId == null || imageId.isEmpty()) {
            log.warn("imageId 为空，跳过图库元数据入库：{}", url);
            return;
        }
        ImageRecord record = findByImageId(imageId).orElseGet(ImageRecord::new);
        record.setImageId(imageId);
        record.setUrl(url);
        record.setMd5(md5);
        record.setVectorDim(vectorDim);
        record.setIndexType(indexType);
        if (record.getId() == null) {
            mapper.insert(record);
        } else {
            mapper.updateById(record);
        }
    }

    public Optional<ImageRecord> find(String imageId) {
        return findByImageId(imageId);
    }

    @Transactional
    public void delete(String imageId) {
        findByImageId(imageId).ifPresent(this::logicDelete);
    }

    /**
     * 逻辑删除。MP 的 deleteById 只置 is_deleted，不会触发 update_time 填充，
     * 这里显式更新两者，保证 update_time 能反映"最后一次变更"（含删除）。
     */
    private void logicDelete(ImageRecord record) {
        mapper.update(null, Wrappers.<ImageRecord>lambdaUpdate()
                .set(ImageRecord::getDeleted, 1)
                .set(ImageRecord::getUpdateTime, LocalDateTime.now())
                .eq(ImageRecord::getId, record.getId()));
    }

    public long countActive() {
        return mapper.selectCount(null);
    }

    /**
     * @param page 从 1 开始
     */
    public IPage<ImageRecord> page(int page, int size) {
        int current = page < 1 ? 1 : page;
        int pageSize = size < 1 ? 10 : Math.min(size, 200);
        return mapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<ImageRecord>lambdaQuery().orderByDesc(ImageRecord::getCreateTime));
    }

    /**
     * 按业务编号查未删除记录；@TableLogic 会自动追加 is_deleted = 0
     */
    private Optional<ImageRecord> findByImageId(String imageId) {
        if (imageId == null || imageId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ImageRecord>lambdaQuery()
                .eq(ImageRecord::getImageId, imageId)));
    }
}
