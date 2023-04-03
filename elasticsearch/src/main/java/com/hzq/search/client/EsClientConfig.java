package com.hzq.search.client;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Huangzq
 * @description
 * @date 2023/3/31 17:04
 */
@Configuration
public class EsClientConfig {
    @Value("${es.cluster.name}")
    private String name;

    @Value("${es.host.ip}")
    private String hostIp;
    @Value("${es.host.port}")
    private Integer hostPort;

    @Bean
    public RestHighLevelClient restHighLevelClient(){
        HttpHost httpHost=new HttpHost(hostIp,hostPort,"http");
        return new RestHighLevelClient(RestClient.builder(httpHost));

    }
}
