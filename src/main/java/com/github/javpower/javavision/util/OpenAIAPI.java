package com.github.javpower.javavision.util;


import cn.hutool.core.convert.ConvertException;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 调用工具。
 *
 * <p>API Key 只从运行环境读取，<b>不允许</b>写死在代码或配置文件里：
 * <ul>
 *   <li>环境变量 {@code OPENAI_API_KEY}；</li>
 *   <li>启动参数 {@code -Dopenai.api.key=...}（容器化场景可用）。</li>
 * </ul>
 *
 * @author Astar
 * @date 2023-03-03
 */
@UtilityClass
public class OpenAIAPI {

    /**
     * 聊天端点
     */
    String chatEndpoint = "https://api.openai.com/v1/chat/completions";

    /**
     * 优先读取的环境变量名
     */
    private static final String API_KEY_ENV = "OPENAI_API_KEY";

    /**
     * 备用的 JVM 系统属性名
     */
    private static final String API_KEY_PROPERTY = "openai.api.key";

    /**
     * 发送消息
     *
     * @param txt 内容
     * @return 模型返回的文本，失败时返回可展示的错误提示
     */
    public static String chat(String txt) {
        return chat(authorization(), txt);
    }

    /**
     * 发送消息，使用调用方指定的 Key。
     *
     * @param key 已带 {@code Bearer } 前缀的授权值，或裸 Key（自动补前缀）
     * @param txt 内容
     * @return 模型返回的文本，失败时返回可展示的错误提示
     */
    public static String chat(String key, String txt) {
        String authorization = key != null && key.startsWith("Bearer ") ? key : "Bearer " + key;
        Map<String, Object> paramMap = new HashMap<>(4);
        paramMap.put("model", "gpt-3.5-turbo-16k");
        List<Map<String, String>> dataList = new ArrayList<>();
        dataList.add(new HashMap<String, String>() {{
            put("role", "user");
            put("content", txt);
        }});
        paramMap.put("messages", dataList);
        try {
            String body = HttpRequest.post(chatEndpoint)
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(paramMap))
                    .execute()
                    .body();
            JSONObject jsonObject = JSONUtil.parseObj(body);
            JSONArray choices = jsonObject.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return "出现了异常";
            }
            JSONObject result = choices.get(0, JSONObject.class, Boolean.TRUE);
            JSONObject message = result == null ? null : result.getJSONObject("message");
            // 上游限流或报错时没有 message 字段，直接取会出现 NPE
            return message == null ? "出现了异常" : message.getStr("content");
        } catch (HttpException e) {
            return "出现了异常";
        } catch (ConvertException e) {
            return "出现了异常";
        }
    }

    /**
     * 生成 Authorization 头，Key 缺失时快速失败，避免带着空 Key 白跑一次网络请求。
     */
    private static String authorization() {
        String key = System.getenv(API_KEY_ENV);
        if (isBlank(key)) {
            key = System.getProperty(API_KEY_PROPERTY);
        }
        if (isBlank(key)) {
            throw new IllegalStateException("未配置 OpenAI API Key，请设置环境变量 " + API_KEY_ENV);
        }
        return "Bearer " + key.trim();
    }

    /**
     * 判空，含纯空白。
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
