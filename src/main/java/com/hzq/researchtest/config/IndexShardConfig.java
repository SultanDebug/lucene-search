package com.hzq.researchtest.config;

import lombok.Data;

import java.util.Map;

/**
 * @author Huangzq
 * @description
 * @date 2022/12/5 14:40
 */
@Data
public class IndexShardConfig {

    //文件索引
    private String fsPath;
    private String fsPathName;

    //内存映射索引
    private String incrPath;
    private String incrPathName;
    //分片数
    private Integer shardNum;
    //字段配置
    private Map<String, FieldDef> fieldMap;


}
