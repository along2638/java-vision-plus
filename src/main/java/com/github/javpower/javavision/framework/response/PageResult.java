package com.github.javpower.javavision.framework.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果包装。MyBatis-Plus 的 IPage 直接序列化字段较多，这里统一成固定结构。
 */
@Data
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "当前页，从 1 开始")
    private int page;

    @Schema(description = "每页条数")
    private int size;

    @Schema(description = "总页数")
    private int pages;

    @Schema(description = "数据列表")
    private List<T> records = new ArrayList<>();

    public static <T> PageResult<T> of(IPage<T> source) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(source.getTotal());
        result.setPage((int) source.getCurrent());
        result.setSize((int) source.getSize());
        result.setPages((int) source.getPages());
        result.setRecords(source.getRecords());
        return result;
    }
}
