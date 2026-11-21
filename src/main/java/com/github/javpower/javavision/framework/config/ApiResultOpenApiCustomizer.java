package com.github.javpower.javavision.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接口文档响应结构补全。
 *
 * <p>所有接口的返回值都会被 {@code RestResponseBodyAdvice} 包装成 {@code ApiResult}，
 * 而 springdoc 默认按方法签名生成响应结构，导致文档与实际返回不一致。这里统一把
 * 每个接口的 200 响应该写成真实结构：{@code {code, success, msg, data, time}}，
 * 原方法签名的返回类型下沉为 {@code data} 字段，从而保留业务字段的中文注释。</p>
 *
 * @author gc.x
 */
@Component
public class ApiResultOpenApiCustomizer implements OpenApiCustomiser {

    private static final String OK = "OK";

    private static final String JSON = "application/json";

    private static final String DEFAULT_DESC = "调用成功。响应体为统一结构 ApiResult："
            + "code 响应编码、success 是否成功、msg 提示信息、data 业务数据、time 响应时间";

    @Override
    public void customise(OpenAPI openApi) {
        wrapResponses(openApi);
        fillPointSchema(openApi);
    }

    /**
     * 把每个接口的 200 响应包装为统一的 ApiResult 结构
     */
    private void wrapResponses(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(this::wrapOperation));
    }

    private void wrapOperation(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return;
        }
        ApiResponse ok = responses.get("200");
        if (ok == null) {
            return;
        }
        Schema<?> dataSchema = extractSchema(ok);
        ok.setContent(new Content().addMediaType(JSON,
                new MediaType().schema(buildWrapper(dataSchema))));
        if (ok.getDescription() == null || OK.equals(ok.getDescription())) {
            ok.setDescription(DEFAULT_DESC);
        }
    }

    /**
     * 取出原响应体 schema，作为包装后的 data 字段
     */
    private Schema<?> extractSchema(ApiResponse response) {
        Content content = response.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        MediaType mediaType = content.get(JSON);
        if (mediaType == null) {
            mediaType = content.values().iterator().next();
        }
        return mediaType == null ? null : mediaType.getSchema();
    }

    /**
     * 构造 ApiResult 包装 schema，data 保留原业务类型
     */
    private Schema<?> buildWrapper(Schema<?> dataSchema) {
        ObjectSchema wrapper = new ObjectSchema();
        wrapper.setDescription("统一响应结构");
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("code", new IntegerSchema().description("响应编码，200 表示成功，其它见错误码说明").example(200));
        properties.put("success", new BooleanSchema().description("是否成功，true 成功 / false 失败").example(true));
        properties.put("msg", new StringSchema().description("响应提示信息，失败时为具体原因").example("操作成功"));
        properties.put("data", dataSchema == null
                ? new ObjectSchema().description("业务数据，该接口无返回数据，恒为 null").nullable(true)
                : dataSchema);
        properties.put("time", new StringSchema().description("响应时间，格式 yyyy-MM-dd HH:mm:ss")
                .example("2026-11-13 10:20:31"));
        wrapper.setProperties(properties);
        return wrapper;
    }

    /**
     * 项目内两个 Point 类（RapidOcr 与 DJL）同名，schema 会被合并。
     * 这里兜底补上坐标字段的中文说明。
     */
    private void fillPointSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Schema<?> point = openApi.getComponents().getSchemas().get("Point");
        if (point == null || point.getProperties() == null) {
            return;
        }
        Schema<?> x = (Schema<?>) point.getProperties().get("x");
        if (x != null && x.getDescription() == null) {
            x.setDescription("横坐标，单位像素");
        }
        Schema<?> y = (Schema<?>) point.getProperties().get("y");
        if (y != null && y.getDescription() == null) {
            y.setDescription("纵坐标，单位像素");
        }
    }
}
