package com.github.javpower.javavision.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.javpower.javavision.db.FaceRecord;

/**
 * 人脸人员表 Mapper，通用 CRUD 由 BaseMapper 提供。
 * 逻辑删除由实体上的 {@code @TableLogic} 自动生效，无需手写 is_deleted 条件。
 */
public interface FaceRecordMapper extends BaseMapper<FaceRecord> {
}
