package com.hmall.item.es;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.utils.CollUtils;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.po.ItemDoc;
import com.hmall.item.service.IItemService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
public class ElasticDocumentTest {

    private RestHighLevelClient client;

    @Autowired
    private IItemService itemService;

    @Test
    void testIndexDocument() throws IOException {
        // 0.准备文档数据
        Item item = itemService.getById(100000011127L);
        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
        // 1.准备Request对象
        IndexRequest request = new IndexRequest("item").id(item.getId().toString());
        // 2.准备请求参数
        request.source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
        // 3.发送请求
        client.index(request, RequestOptions.DEFAULT); //id存在的时候就是全量update
    }

    @Test
    void testGetDocument() throws IOException {
        // 1.准备Request对象
        GetRequest request = new GetRequest("item").id("100000011127L");
        // 2.发送请求
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        // 3.处理响应结果
        String json = response.getSourceAsString();
        ItemDoc doc = JSONUtil.toBean(json, ItemDoc.class);
        System.out.println(doc.toString());
    }

    @Test
    void testDeleteDocument() throws IOException {
        // 1.准备Request对象
        DeleteRequest request = new DeleteRequest("items").id("100000011127L");
        // 2.发送请求
        client.delete(request, RequestOptions.DEFAULT);
    }

    @Test
    void testUpdateDocument() throws IOException { // 局部update
        // 1.准备Request
        UpdateRequest request = new UpdateRequest("items", "100002644680");
        // 2.准备请求参数
        request.doc(
                "price", 58800,
                "commentCount", 1
        );
        // 3.发送请求
        client.update(request, RequestOptions.DEFAULT);
    }

    @Test
    void testBulk() throws IOException {
        int pageNo = 1, pageSize = 500;
        while (pageNo <= 100) {
            // 0.准备文档
            Page<Item> page = itemService.lambdaQuery()
                    .eq(Item::getStatus, 1)
                    .page(Page.of(pageNo, pageSize));
            List<Item> records = page.getRecords();
            if (records == null) {
                return;
            }
            List<ItemDoc> itemDocs = BeanUtil.copyToList(records, ItemDoc.class);
        /*List<ItemDoc> itemDocs = records
                .stream()
                .map(item -> BeanUtil.copyProperties(item, ItemDoc.class))
                .collect(Collectors.toList());*/
            // 1.创建Request
            BulkRequest request = new BulkRequest();
            // 2.准备请求参数
        /*request.add(new IndexRequest("items").id("1").source("json doc1", XContentType.JSON));
        request.add(new IndexRequest("items").id("2").source("json doc2", XContentType.JSON));*/
            for (ItemDoc itemDoc : itemDocs) {
                request.add(new IndexRequest("items").id(itemDoc.getId()).source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON));
            }
            // 3.发送请求
            client.bulk(request, RequestOptions.DEFAULT);
            pageNo++;
        }
    }

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
}
