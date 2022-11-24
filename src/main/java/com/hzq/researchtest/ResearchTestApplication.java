package com.hzq.researchtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {/*"com.bird",*/"com.hzq"})
public class ResearchTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchTestApplication.class, args);
    }

}
