package com.github.javpower.javavision.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动前配置检查。
 *
 * <p>数据库与对象存储的凭证不再写进仓库（{@code application.yml} 里只留空占位），
 * 必须在环境变量或启动参数中提供。本检查在 Environment 就绪之后、容器刷新之前执行：
 * 缺失时直接给出"缺哪个变量、怎么补"的报错，而不是让 Hikari / MinIO 客户端抛一个
 * 看不出根因的连接异常（密码为空时日志里通常只有一句 authentication failed）。
 *
 * <p>为什么不用 {@code ApplicationRunner}：{@code spring.sql.init} 在容器刷新阶段
 * 就会建立数据库连接，等 Runner 执行时应用早就启动失败了。
 *
 * <p>Redis 密码不在此校验：缓存是可降级能力，免密实例也是合法配置。
 *
 * @author gc.x
 */
public class StartupConfigCheck implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        List<String> missing = new ArrayList<>();

        // PostgreSQL 是必需依赖：地址、账号、密码任一为空都无法启动
        check(env, "spring.datasource.url", "PG_HOST / PG_PORT / PG_DB", missing);
        check(env, "spring.datasource.username", "PG_USER", missing);
        check(env, "spring.datasource.password", "PG_PASSWORD", missing);

        // MinIO 只有在显式启用时才要求密钥，避免"以为存了对象存储，实际写在本机"
        boolean minioEnabled = Boolean.TRUE.equals(
                env.getProperty("minio.enabled", Boolean.class, Boolean.FALSE));
        if (minioEnabled) {
            check(env, "minio.endpoint", "MINIO_ENDPOINT", missing);
            check(env, "minio.access-key", "MINIO_ACCESS_KEY", missing);
            check(env, "minio.secret-key", "MINIO_SECRET_KEY", missing);
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(buildMessage(missing, minioEnabled));
        }
    }

    private void check(ConfigurableEnvironment env, String key, String envName, List<String> missing) {
        String value = env.getProperty(key);
        if (!StringUtils.hasText(value)) {
            missing.add(String.format("  %-38s <- %s", key, envName));
        }
    }

    private String buildMessage(List<String> missing, boolean minioEnabled) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("启动配置检查未通过：以下必需项为空（仓库不再提供默认凭证）\n\n");
        missing.forEach(line -> sb.append(line).append('\n'));
        sb.append('\n')
          .append("补齐方式（任选其一）：\n")
          .append("  1) 环境变量   PowerShell: $env:PG_PASSWORD='***'   Bash: export PG_PASSWORD=***\n")
          .append("  2) 启动参数   java -jar app.jar --spring.datasource.password=*** --minio.secret-key=***\n")
          .append("  3) 本地开发   复制 tools/env.example.ps1 为 tools/env.local.ps1 填好后用 tools/run.ps1 启动\n")
          .append("                （env.local.* 已加入 .gitignore，不会被提交）\n")
          .append('\n')
          .append("说明：PostgreSQL 若使用 trust 免密认证，PG_PASSWORD 填任意占位值即可（服务端不校验）。\n")
          .append("      MinIO 当前").append(minioEnabled ? "已启用" : "未启用")
          .append("；不用对象存储时设 MINIO_ENABLED=false 即可跳过密钥校验。");
        return sb.toString();
    }
}
