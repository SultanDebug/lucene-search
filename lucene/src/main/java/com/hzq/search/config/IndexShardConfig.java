package com.hzq.search.config;

import lombok.Data;

import java.util.Map;

/**
 * @author Huangzq
 * @date 2022/12/5 14:40
 */
@Data
public class IndexShardConfig {
    /**
     * 索引数据库链接
     */
    private String dbUrl;
    /**
     * 索引表名
     */
    private String dbTableName;
    /**
     * 索引数据库用户名
     */
    private String dbUserName;
    /**
     * 索引数据库密码
     */
    private String dbPass;

    /**
     * 索引切换当前索引记录文件
     */
    private String switchIndex;

    /**
     * 文件索引
     */
    private String fsPath;
    /**
     * 初始化备份索引
     */
    private String aliaPath;
    private String fsPathName;

    /**
     * 内存映射索引
     */
    private String incrPath;
    private String incrPathName;

    /**
     * 分片数
     */
    private Integer shardNum;
    /**
     * 分片召回数量
     */
    private Integer recallSize = 10;
    /**
     * 字段配置
     */
    private Map<String, FieldDef> fieldMap;

}
