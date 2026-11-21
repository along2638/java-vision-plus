package com.github.javpower.javavision.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * CORS 配置。
 *
 * <p>允许的来源通过 {@code vision.cors.allowed-origins} 配置，多个用逗号分隔。
 * 默认 {@code *} 是为了兼容内网直连调用；一旦允许携带 Cookie（{@code allowCredentials=true}），
 * 通配来源等于把接口开放给任意网页，生产环境务必改成具体域名白名单。
 *
 * @author gc.x
 * @date 2023/2/16
 **/
@Slf4j
@Configuration
public class CorsConfig {

    /**
     * 允许的来源，逗号分隔，支持通配写法如 http://*.example.com
     */
    private final String allowedOrigins;

    public CorsConfig(@Value("${vision.cors.allowed-origins:*}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 跨域过滤器。
     *
     * @return CORS 过滤器
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        boolean wildcard = false;
        for (String origin : allowedOrigins.split(",")) {
            String value = origin.trim();
            if (value.isEmpty()) {
                continue;
            }
            wildcard = wildcard || "*".equals(value);
            corsConfiguration.addAllowedOriginPattern(value);
        }
        corsConfiguration.addAllowedHeader("*");
        corsConfiguration.setAllowedMethods(Arrays.asList("OPTIONS", "GET", "POST", "PUT", "DELETE"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.addExposedHeader("*");
        if (wildcard && Boolean.TRUE.equals(corsConfiguration.getAllowCredentials())) {
            log.warn("CORS 允许任意来源且允许携带凭证，生产环境请把 vision.cors.allowed-origins 配成域名白名单");
        }
        UrlBasedCorsConfigurationSource configurationSource = new UrlBasedCorsConfigurationSource();
        configurationSource.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsFilter(configurationSource);
    }

}
