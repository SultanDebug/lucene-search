package com.hzq.search.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * @author Huangzq
 * @description
 * @date 2023/3/31 17:47
 */
@Service
@Slf4j
public class EsService {

    @Autowired
    private RestHighLevelClient client;

    public SearchResponse query(){
        SearchRequest searchRequest = new SearchRequest("hzq-test-03");

        SearchSourceBuilder builder = new SearchSourceBuilder();
        builder.explain(true);

        builder.query(QueryBuilders.matchAllQuery());

        searchRequest.source(builder);

        try {
            SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);

            SearchHits hits = search.getHits();

            TotalHits totalHits = hits.getTotalHits();

            return search;

        } catch (IOException e) {
            log.error("搜索异常",e);
        }

        return null;
    }
}
