package com.github.javpower.javavision.es.service;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import ai.onnxruntime.OrtException;
import com.github.javpower.javavision.db.ImageRecord;
import com.github.javpower.javavision.es.config.EsConfig;
import com.github.javpower.javavision.es.response.SearchResult;
import com.github.javpower.javavision.service.IImageService;
import com.github.javpower.javavision.service.ImageRecordService;
import com.github.javpower.javavision.service.ImageVectorService;
import com.github.javpower.javavision.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("ImageSearchService")
@Slf4j
public class ImageSearchService implements IImageService {

    private static final String INDEX_TYPE = "es";

    private final RestHighLevelClient restHighLevelClient;

    private final FileUtil fileUtil;

    private final ImageVectorService imageVectorService;

    private final ImageRecordService imageRecordService;

    public ImageSearchService(ObjectProvider<RestHighLevelClient> restHighLevelClientProvider, FileUtil fileUtil,
                              ImageVectorService imageVectorService, ImageRecordService imageRecordService) {
        // es.enable=false 时可能不存在该 Bean，这里允许为空，真正使用时再提示
        this.restHighLevelClient = restHighLevelClientProvider.getIfAvailable();
        this.fileUtil = fileUtil;
        this.imageVectorService = imageVectorService;
        this.imageRecordService = imageRecordService;
    }

    private RestHighLevelClient client() {
        if (restHighLevelClient == null) {
            throw new IllegalStateException("Elasticsearch 未启用或初始化失败，请检查 es.enable / es.address 配置");
        }
        return restHighLevelClient;
    }

    public void add(String imageId, MultipartFile file) throws IOException, ModelException, TranslateException, OrtException {
        byte[] bytes = file.getBytes();
        String md5 = imageVectorService.md5(bytes);
        float[] vector = imageVectorService.raw(bytes);
        String url = fileUtil.upload(file, "image");

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("url", url);
        jsonMap.put("vector", vector);
        jsonMap.put("imageId", imageId);
        log.debug("上传图片信息----->imageId={}, url={}", imageId, url);
        IndexRequest request = new IndexRequest(EsConfig.IMAGE_SEARCH_INDEX).source(jsonMap, XContentType.JSON);
        client().index(request, RequestOptions.DEFAULT);
        imageRecordService.save(imageId, url, md5, vector.length, INDEX_TYPE);
    }

    //图片及展示k个结果
    public List<SearchResult> search(InputStream input, int k) throws ModelException, TranslateException, IOException, OrtException {
        float[] vector = imageVectorService.raw(imageVectorService.read(input));
        SearchRequest searchRequest = new SearchRequest(EsConfig.IMAGE_SEARCH_INDEX);
        Script script = new Script(
                ScriptType.INLINE,
                "painless",
                "cosineSimilarity(params.queryVector, doc['vector'])",
                Collections.singletonMap("queryVector", vector));
        FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
                QueryBuilders.matchAllQuery(),
                ScoreFunctionBuilders.scriptFunction(script));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(functionScoreQueryBuilder)
                .fetchSource(null, "vector") //不返回vector字段，太多了没用还耗时
                .size(k);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client().search(searchRequest, RequestOptions.DEFAULT);
        SearchHits hits = searchResponse.getHits();
        List<SearchResult> list = new ArrayList<>();
        for (SearchHit hit : hits) {
            SearchResult result = new SearchResult((String) hit.getSourceAsMap().get("url"),
                    (String) hit.getSourceAsMap().get("imageId"), hit.getScore());
            list.add(result);
        }
        return list;
    }

    @Override
    public void del(String imageId) {
        // 原实现为空，等于只删了元数据不删索引；这里补上按 imageId 的删除
        String url = imageRecordService.find(imageId).map(ImageRecord::getUrl).orElse(null);
        if (url != null) {
            fileUtil.delete(url);
        }
        try {
            DeleteByQueryRequest request = new DeleteByQueryRequest(EsConfig.IMAGE_SEARCH_INDEX);
            request.setQuery(QueryBuilders.termQuery("imageId", imageId));
            client().deleteByQuery(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new IllegalStateException("删除 ES 索引失败：" + e.getMessage(), e);
        }
        imageRecordService.delete(imageId);
    }
}
