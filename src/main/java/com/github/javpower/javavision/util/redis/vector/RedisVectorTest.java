package com.github.javpower.javavision.util.redis.vector;

import cn.hutool.json.JSONUtil;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.search.Document;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@RestController
public class RedisVectorTest {


    public static void main(String[] args) {
        UnifiedJedis unifiedjedis=new UnifiedJedis(new HostAndPort("xxxxx",6379));;
        RedisVectorUtil redisVectorTool=new RedisVectorUtil(unifiedjedis);
        // 定义字段结构来创建索引
        List<FieldSchema> fields = Arrays.asList(
                new FieldSchema("text", FieldType.TEXT, null, null, null),
                new FieldSchema("vector", FieldType.VECTOR, VectorDataType.FLOAT32, 4, DistanceMetric.COSINE)
        );
        // 创建一个名为 "myIndex" 的向量索引
        redisVectorTool.createVectorIndex("testIndex3", fields);
        // 添加一个文档到索引，包含文本和向量
        java.util.Map<String, Object> document = new HashMap<>();
        document.put("text", "This is a sample text");
        document.put("vector", new float[]{0.1f, 0.2f, 0.3f, 0.4f});// 示例向量数据
        redisVectorTool.addDocumentToIndex("testIndex3", "1", document); // 假设文档ID为 "1"
        // 执行向量搜索，假设我们搜索与上面添加的向量相似的文档
        float[] queryVector = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
        List<Document> searchResults = redisVectorTool.searchVector("testIndex3", queryVector, 10); // 限制返回结果为10个
        // 打印搜索结果
        searchResults.forEach(v-> System.out.println(JSONUtil.toJsonStr(v)));
    }

}