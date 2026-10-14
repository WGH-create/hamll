package com.hmall.item.es;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.utils.CollUtils;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.po.ItemDoc;
import com.hmall.item.service.IItemService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import java.io.IOException;
import java.util.List;

@SpringBootTest(properties = "spring.profiles.active=local")
@Slf4j
public class DocumentTest {
    private RestHighLevelClient client;

    @Autowired
    private IItemService itemService;

    @Test
    public void testConnection() {
        System.out.println("client = " + client);
    }

    @Test
    void testIndexDocument() throws IOException {
        //1.根据id查询商品数据
        Item item = itemService.getById(317578L);
        //2.转换商品数据为文档类型
        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
        //3.将ItemDTO转为JSON
        String doc = JSONUtil.toJsonStr(itemDoc);

        //4.准备Request对象
        IndexRequest request = new IndexRequest("items").id(itemDoc.getId());
        //5.准备请求参数(JSON文档)
        request.source(doc, XContentType.JSON);
        //6.发送请求
        client.index(request, RequestOptions.DEFAULT);
    }

    @Test
    void testGetIndex() throws IOException {
        //1.创建Request对象
        GetRequest request = new GetRequest("items","317578");
        GetResponse fields = client.get(request, RequestOptions.DEFAULT);
        String json = fields.getSourceAsString();
        ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
        System.out.println(itemDoc);
    }

    @Test
    void testDeleteIndex() throws IOException {
        //1.创建Request对象
        DeleteRequest request = new DeleteRequest("items","317578");
        client.delete(request, RequestOptions.DEFAULT);

    }

    //增量修改
    @Test
    public void testUpdateDocument() throws IOException {
        //1.创建Request对象
        UpdateRequest request = new UpdateRequest("items", "317578");
        //2.准备请求参数
        request.doc(
                "price", 58800,
                "commentCount", 1
        );
        //3.发送请求
        client.update(request, RequestOptions.DEFAULT);
    }

    @Test
    void testBulk() throws IOException{
        //分页查询参数
        int pageNo = 1, pageSize = 1000;
        while (true) {
            //分页查询商品信息
            Page<Item> page = itemService.lambdaQuery()
                    .eq(Item::getStatus, 1).page(new Page<Item>(pageNo, pageSize));
            //非空判断
            List<Item> itemList = page.getRecords();
            if (CollUtils.isEmpty(itemList)) {
                return;
            }
            log.info("当前加载第 {} 页数据，加载 {} 条", pageNo, itemList.size());

            //1.准备Request对象
            BulkRequest bulkRequest = new BulkRequest("items");
            //2.准备参数，添加多个IndexRequest
            for (Item item : itemList) {
                //2.1.转换文档类型ItemDoc
                ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
                //2.1.准备IndexRequest
                IndexRequest indexRequest = new IndexRequest("items")
                        .id(itemDoc.getId())
                        .source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
                //2.2.将新增Request添加到BulkRequest对象
                bulkRequest.add(indexRequest);
            }
            //3.发送请求
            client.bulk(bulkRequest, RequestOptions.DEFAULT);
            //4.翻页
            pageNo++;
        }
    }


    @BeforeEach
    void setUp() {
        this.client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.100.128:9200")
        ));
    }

    @AfterEach
    void tearDown() throws IOException {
        this.client.close();
    }
}
