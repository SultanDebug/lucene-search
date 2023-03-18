package com.hzq.search;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Hello world!
 */
@SpringBootApplication(scanBasePackages = {"com.hzq"}, exclude = {DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class})
@Slf4j
public class ElasticSearchAppication {
    public static void main(String[] args) {
        try {
            SpringApplication.run(ElasticSearchAppication.class, args);
        } catch (Throwable throwable) {
            log.error("启动异常", throwable);
        }
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
