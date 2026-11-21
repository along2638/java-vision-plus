package com.github.javpower.javavision.framework.config;


import com.github.javpower.javavision.config.FileProperties;
import com.github.javpower.javavision.framework.filter.JsonRequestBodyFilter;
import com.github.javpower.javavision.framework.filter.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Web 层配置：过滤器注册与静态资源映射。
 *
 * @author gc.x
 * @date 2022/3/15
 **/
@Slf4j
@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileProperties fileProperties;

    public WebMvcConfig(FileProperties fileProperties) {
        this.fileProperties = fileProperties;
    }

    /**
     * 请求体缓存过滤器：把 application/json 的请求体读出来放进 attribute，供后续重复读取。
     */
    @Bean
    public FilterRegistrationBean<JsonRequestBodyFilter> jsonRequestBodyFilter() {
        FilterRegistrationBean<JsonRequestBodyFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new JsonRequestBodyFilter());
        List<String> urls = new ArrayList<>();
        urls.add("/*");
        filterRegistrationBean.setUrlPatterns(urls);
        filterRegistrationBean.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return filterRegistrationBean;
    }

    /**
     * traceId 过滤器必须最先执行：后面的日志与异常处理都依赖 MDC 里已有的 traceId。
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new TraceIdFilter());
        List<String> urls = new ArrayList<>();
        urls.add("/*");
        filterRegistrationBean.setUrlPatterns(urls);
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filterRegistrationBean;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 虚拟目录文件映射
        registry.addResourceHandler(fileProperties.getAccessPath())
                .addResourceLocations("file:" + fileProperties.getUploadPath());
    }
}
