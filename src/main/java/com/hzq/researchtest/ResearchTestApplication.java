package com.hzq.researchtest;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(scanBasePackages = {"com.hzq"},exclude = {DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class})
public class ResearchTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchTestApplication.class, args);
    }

}
