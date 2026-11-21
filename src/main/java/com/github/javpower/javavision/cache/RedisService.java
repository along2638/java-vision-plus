package com.github.javpower.javavision.cache;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 访问门面。
 * <p>
 * 缓存是可选的加速层，Redis 不可用时必须降级为直接查库，不能影响接口可用性，
 * 因此所有操作都做了异常吞咽（带日志节流），返回空结果由调用方回源。
 */
@Slf4j
@Service
public class RedisService {

    /**
     * 失败日志节流间隔：避免 Redis 宕机时刷满日志
     */
    private static final long WARN_INTERVAL_MS = 60_000L;

    private final StringRedisTemplate template;

    private volatile long lastWarnAt = 0L;

    public RedisService(StringRedisTemplate template) {
        this.template = template;
    }

    public boolean ping() {
        try {
            String pong = template.execute((RedisCallback<String>) connection -> connection.ping());
            return pong != null;
        } catch (Exception e) {
            warn(e);
            return false;
        }
    }

    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(template.opsForValue().get(key));
        } catch (Exception e) {
            warn(e);
            return Optional.empty();
        }
    }

    public void set(String key, String value, Duration ttl) {
        try {
            template.opsForValue().set(key, value, ttl.getSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            warn(e);
        }
    }

    public <T> Optional<T> getObject(String key, Class<T> type) {
        return get(key).map(json -> {
            try {
                return JSONUtil.toBean(json, type);
            } catch (Exception e) {
                log.warn("缓存反序列化失败，key={}", key, e);
                return null;
            }
        });
    }

    public void setObject(String key, Object value, Duration ttl) {
        try {
            set(key, JSONUtil.toJsonStr(value), ttl);
        } catch (Exception e) {
            warn(e);
        }
    }

    public <T> Optional<List<T>> getList(String key, Class<T> type) {
        return get(key).map(json -> {
            try {
                return JSONUtil.toList(json, type);
            } catch (Exception e) {
                log.warn("缓存列表反序列化失败，key={}", key, e);
                return null;
            }
        });
    }

    public void setList(String key, List<?> value, Duration ttl) {
        try {
            set(key, JSONUtil.toJsonStr(value), ttl);
        } catch (Exception e) {
            warn(e);
        }
    }

    public void delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            template.delete(Arrays.asList(keys));
        } catch (Exception e) {
            warn(e);
        }
    }

    /**
     * 按前缀批量删除（SCAN 实现，不使用 KEYS 阻塞实例）
     */
    public long deleteByPrefix(String pattern) {
        try {
            List<String> keys = (List<String>) template.execute((RedisCallback<List<String>>) connection -> {
                java.util.Set<String> matched = new java.util.HashSet<>();
                java.util.Iterator<byte[]> it = connection.scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(500).build());
                while (it.hasNext()) {
                    matched.add(new String(it.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
                return new java.util.ArrayList<>(matched);
            });
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            Long removed = template.delete(keys);
            return removed == null ? 0 : removed;
        } catch (Exception e) {
            warn(e);
            return 0;
        }
    }

    /**
     * 缓存已用内存等运行信息，用于状态接口；Redis 不可用时返回空 Map
     */
    public java.util.Map<String, String> info(String section) {
        try {
            java.util.Properties properties = template.execute(
                    (RedisCallback<java.util.Properties>) connection -> connection.info(section));
            if (properties == null) {
                return java.util.Collections.emptyMap();
            }
            java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
            for (String name : properties.stringPropertyNames()) {
                map.put(name, properties.getProperty(name));
            }
            return map;
        } catch (Exception e) {
            warn(e);
            return java.util.Collections.emptyMap();
        }
    }

    private void warn(Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastWarnAt > WARN_INTERVAL_MS) {
            lastWarnAt = now;
            log.warn("Redis 操作失败，本次请求按无缓存处理：{}", e.toString());
        }
    }
}
