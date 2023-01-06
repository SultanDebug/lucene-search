package com.hzq.search;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(scanBasePackages = {"com.hzq"}, exclude = {DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class})
public class ResearchTestApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(ResearchTestApplication.class, args);
        }catch (Throwable throwable){
            System.out.println(throwable);
        }
    }

}
