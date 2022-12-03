package com.hzq.researchtest.config;

/**
 * @author Huangzq
 * @description
 * @date 2022/12/3 11:11
 */
public interface ShardConfig {
    //文件系统索引
    String FS_PATH = "C:\\Users\\zhenqiang.huang\\Desktop\\searchprocess\\pydata\\shard_index";
    String FS_PATH_NAME = "fsindex";

    //内存映射索引
    String INCR_PATH = "C:\\Users\\zhenqiang.huang\\Desktop\\searchprocess\\pydata\\shard_index";
    String INCR_PATH_NAME = "mmapindex";
    //分片数
    int SHARD_NUM = 10;
}
