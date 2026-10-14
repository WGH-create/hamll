package com.hmall.item.es;

import cn.hutool.json.JSONUtil;
import com.hmall.common.utils.CollUtils;
import com.hmall.item.domain.po.ItemDoc;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ElasticSearchTest {

    private RestHighLevelClient client;

    @Test
    void testMatchAll() throws IOException {
        SearchRequest request = new SearchRequest("items");
        //查询
        request.source().query(QueryBuilders.matchAllQuery());
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parsResponseResult(response);
    }

    @Test
    void testSearch() throws IOException {
        SearchRequest request = new SearchRequest("items");
        //搜索关键字为脱脂牛奶，品牌为德亚，价格低于30000
        request.source().query(QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("name","脱脂牛奶"))
                .filter(QueryBuilders.termQuery("brand","德亚"))
                .filter(QueryBuilders.rangeQuery("price").lt(30000)));
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parsResponseResult(response);
    }

    @Test
    void testSortAndPage() throws IOException {
        int pageNo = 1,pageSize = 5;
        SearchRequest request = new SearchRequest("items");
        request.source().query(QueryBuilders.matchAllQuery());
        //分页
        request.source().from((pageNo - 1) * pageSize).size(pageSize);
        //排序
        request.source()
                .sort("sold", SortOrder.DESC)
                .sort("price",SortOrder.ASC);
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parsResponseResult(response);
    }

    @Test
    void testHighlight() throws IOException {
        SearchRequest request = new SearchRequest("items");
       request.source().query(QueryBuilders.matchQuery("name","脱脂牛奶"));
       //高亮条件
        request.source().highlighter(SearchSourceBuilder.highlight().field("name"));
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parsResponseResult(response);
    }

    @Test
    void testAgg() throws IOException {
        SearchRequest request = new SearchRequest("items");
        //分页
        request.source().size(0);
        //聚会条件
        String brandAggName = "brandAgg";
        request.source().aggregation(AggregationBuilders
                .terms(brandAggName).field("brand").size(10));
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //解析聚会结果
        Aggregations aggregations = response.getAggregations();
        //获取聚会名称对应的聚会结果
        Terms brandTerms = aggregations.get(brandAggName);
        //获取buckets
        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        //遍历获取每一个buckets
        for (Terms.Bucket bucket : buckets) {
            //品牌
            System.out.println("brand = " +bucket.getKeyAsString());
            System.out.println("doc_count = " +bucket.getDocCount());
        }



    }


    private static void parsResponseResult(SearchResponse response) {
        //解析结果
        SearchHits searchHits = response.getHits();
        //总条数
        long value = searchHits.getTotalHits().value;
        System.out.println(value);
        //命中的数据
        SearchHit[] hits = searchHits.getHits();
        for(SearchHit hit : hits){
            //获取结果
            String json = hit.getSourceAsString();
            //转成ItemDoc
            ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
            //5.获取高亮结果
            Map<String, HighlightField> hfs = hit.getHighlightFields();
            //有高亮结果获取高亮结果替换原始数据
            if(CollUtils.isNotEmpty(hfs)){
                HighlightField hf = hfs.get("name");
                if(hf != null){
                    String hfName = hf.getFragments()[0].string();
                    itemDoc.setName(hfName);
                }
            }
            System.out.println(itemDoc);
        }
    }




    @BeforeEach
    void setUp(){
        client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.100.128:9200")
        ));
    }

    @AfterEach
    void tearDown() throws IOException {
        if(client != null){
            client.close();
        }
    }

}
