package com.hzq.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
