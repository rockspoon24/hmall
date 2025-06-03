package com.hmall.item.es;

import cn.hutool.json.JSONUtil;
import com.hmall.common.utils.CollUtils;
import com.hmall.item.domain.po.ItemDoc;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootTest(properties = "spring.profiles.active=local")
public class ElasticSearchTest {

    private RestHighLevelClient client;

    @BeforeEach
    void setUp() {
        client = new RestHighLevelClient(RestClient.builder(HttpHost.create("http://localhost:9200")));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testMatchAll() throws IOException {
        // 1.准备Request对象
        SearchRequest request = new SearchRequest("items");
        // 2.准备请求参数
        request.source()
                .query(QueryBuilders.matchAllQuery());
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析结果
        extracted(response);

    }

    @Test
    void testSearch() throws IOException {
        // 1.准备Request对象
        SearchRequest request = new SearchRequest("items");
        // 2.准备请求参数
        request.source().query(
                QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery("name", "脱脂牛奶"))
                        .filter(QueryBuilders.termQuery("brand", "德亚"))
                        .filter(QueryBuilders.rangeQuery("price").lt(30000))
        );
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析结果
        extracted(response);
    }

    @Test
    void testSearchWithSort() throws IOException {
        int pageNo = 1, pageSize = 10;
        // 1.准备Request对象
        SearchRequest request = new SearchRequest("items");
        // 2.准备请求参数
        request.source()
                .query(QueryBuilders.matchAllQuery());
        // 2.1.分页
        request.source().from((pageNo -1) * pageSize).size(pageSize);
        // 2.2 排序
        request.source()
                .sort("sold", SortOrder.DESC)
                .sort("price", SortOrder.ASC);
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析结果
        extracted(response);
    }

    @Test
    void testHighlight() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        // 2.1.query条件
        request.source().query(QueryBuilders.matchQuery("name", "脱脂牛奶"));
        // 2.2.高亮条件
        request.source().highlighter(
                SearchSourceBuilder.highlight()
                        .field("name")
                        .preTags("<em>")
                        .postTags("</em>")
        );
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        handleResponse(response);
    }

    @Test
    void testAggregation() throws IOException {
        // 1.创建Request
        SearchRequest request = new SearchRequest("items");
        // 2.组织请求参数
        // 2.1.分页
        request.source().size(0);
        // 2.2.聚合条件
        String brandAggName = "brandAgg";
        request.source().aggregation(
                AggregationBuilders.terms(brandAggName)
                        .field("brand")
                        .size(10)
        );
        // 3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4.解析响应
        Aggregations aggregations = response.getAggregations();
        // 4.1.根据聚合名称获取对应的聚合
        Terms aggregation = aggregations.get(brandAggName);
        // 4.2获取buckets
        List<? extends Terms.Bucket> buckets = aggregation.getBuckets();
        // 4.3.变脸每一个bucket
        for (Terms.Bucket bucket : buckets) {
            System.out.println(bucket.getKeyAsString() + "\t" + bucket.getDocCount());
        }
    }


    private static void extracted(SearchResponse response) {
        SearchHits searchHits = response.getHits();
        // 4.1.总条数
        long total = searchHits.getTotalHits().value;
        // 4.3.命中数据
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            String json = hit.getSourceAsString();
            ItemDoc doc = JSONUtil.toBean(json, ItemDoc.class);
            System.out.println(doc.toString());
        }
    }

    private static void handleResponse(SearchResponse response) {
        SearchHits searchHits = response.getHits();
        // 1.获取总条数
        long total = searchHits.getTotalHits().value;
        System.out.println("共搜索到" + total + "条数据");
        // 2.遍历结果数组
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            // 3.得到_source，也就是原始json文档
            String source = hit.getSourceAsString();
            // 4.反序列化
            ItemDoc item = JSONUtil.toBean(source, ItemDoc.class);
            // 5.获取高亮结果
            Map<String, HighlightField> hfs = hit.getHighlightFields();
            if (CollUtils.isNotEmpty(hfs)) {
                // 5.1.有高亮结果，获取name的高亮结果
                HighlightField hf = hfs.get("name");
                if (hf != null) {
                    // 5.2.获取第一个高亮结果片段，就是商品名称的高亮值,有多段需要拼接
                    String hfName = hf.getFragments()[0].string();
                    item.setName(hfName);
                }
            }
            System.out.println(item);
        }
    }


}
