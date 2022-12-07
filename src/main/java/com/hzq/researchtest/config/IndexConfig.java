package com.hzq.researchtest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Huangzq
 * @description
 * @date 2022/12/5 14:40
 */
@ConfigurationProperties(prefix = "configs")
@Configuration
@Data
public class IndexConfig {
    private Map<String,IndexShardConfig> indexMap;
}
