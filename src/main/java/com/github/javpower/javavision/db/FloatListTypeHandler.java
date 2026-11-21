package com.github.javpower.javavision.db;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 特征向量 <-> JSON 文本 的类型转换（MyBatis）。
 * <p>
 * 选择 JSON 文本而非 bytea：便于人工排查，也避免跨语言读取时的字节序问题。
 * 在实体上通过 {@code @TableField(typeHandler = FloatListTypeHandler.class)} 指定。
 */
public class FloatListTypeHandler extends BaseTypeHandler<List<Float>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Float> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JSONUtil.toJsonStr(parameter));
    }

    @Override
    public List<Float> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<Float> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<Float> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<Float> parse(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return new ArrayList<>(JSONUtil.toList(json, Float.class));
        } catch (Exception e) {
            return null;
        }
    }
}
