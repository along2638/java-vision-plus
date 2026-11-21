package com.github.javpower.javavision.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时校正 is_deleted 列类型，并补齐依赖该列的部分唯一索引。
 * <p>
 * 历史版本把 is_deleted 建成 PG 的 boolean（客户端显示为 t / f），与约定的 0 / 1 不一致，
 * 现统一为 smallint（0 未删除 / 1 已删除）。{@code CREATE TABLE IF NOT EXISTS} 不会修改
 * 已经存在的表，因此历史库需要在这里做一次就地转换。
 * <p>
 * 迁移是幂等的：列已经是 smallint 时不执行任何 ALTER，只补建（或不动作）索引。
 * 索引谓词里带着 is_deleted，必须等类型转换完成后再建，否则解析阶段就会因
 * boolean 与 integer 不匹配而失败，所以 schema-pg.sql 里把这两条索引留空。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public SchemaMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            migrateIsDeleted("vision_face_person", "uk_face_person_person_id", "person_id");
            migrateIsDeleted("vision_image", "uk_image_image_id", "image_id");
        } catch (RuntimeException e) {
            // ApplicationRunner 的异常可能被 Spring Boot 的 ErrorHandler 处理掉，这里先打日志保证可见
            log.error("is_deleted 迁移或索引创建失败，请检查数据库权限、锁与连接状态", e);
            throw e;
        }
    }

    /**
     * 把指定表的 is_deleted 从 boolean 就地转为 smallint，并保证部分唯一索引存在。
     *
     * @param table        表名
     * @param indexName    未删除记录内的唯一索引名
     * @param uniqueColumn 唯一列
     */
    private void migrateIsDeleted(String table, String indexName, String uniqueColumn) {
        if (!tableExists(table)) {
            log.warn("表 {} 不存在，跳过 is_deleted 迁移", table);
            return;
        }
        String dataType = columnType(table, "is_deleted");
        if (dataType == null) {
            log.warn("表 {} 没有 is_deleted 列，跳过迁移", table);
            return;
        }

        if ("boolean".equalsIgnoreCase(dataType)) {
            long start = System.currentTimeMillis();
            // 旧索引的谓词是 is_deleted = false，改列类型时无法重新解析，先删掉，稍后按新类型重建
            jdbc.execute("DROP INDEX IF EXISTS " + indexName);
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN is_deleted DROP DEFAULT");
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN is_deleted TYPE smallint"
                    + " USING (CASE WHEN is_deleted THEN 1 ELSE 0 END)");
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN is_deleted SET DEFAULT 0");
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN is_deleted SET NOT NULL");
            log.info("表 {} 的 is_deleted 已从 boolean 转为 smallint（0 未删除 / 1 已删除），耗时 {}ms",
                    table, System.currentTimeMillis() - start);
        } else if (!"smallint".equalsIgnoreCase(dataType)) {
            log.warn("表 {} 的 is_deleted 类型为 {}，与预期的 smallint 不符，请人工确认后再启动", table, dataType);
        }

        // 谓词 is_deleted = 0 只有在列已是 smallint 时才合法
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName
                + " ON " + table + " (" + uniqueColumn + ") WHERE is_deleted = 0");
        log.info("表 {} 的部分唯一索引 {} 已就绪（WHERE is_deleted = 0）", table, indexName);
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables"
                        + " WHERE table_schema = current_schema() AND table_name = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private String columnType(String table, String column) {
        List<String> types = jdbc.queryForList(
                "SELECT data_type FROM information_schema.columns"
                        + " WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                String.class, table, column);
        return types.isEmpty() ? null : types.get(0);
    }
}
