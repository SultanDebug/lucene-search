package com.hzq.search.config;

import lombok.Data;

/**
 * @author Huangzq
 * @date 2022/12/5 14:29
 */
@Data
public class FieldDef {
    /**
     * 字段名称
     */
    private String fieldName;
    /**
     * 1-ik分词  
     * 2-拼音分词 
     * 3-简拼分词 
     * 4-全拼 
     * 5-特殊符号分词 
     * 6-归一化不替换特殊字符分词器 
     * 7-归一化去除特殊字符分词器 
     * 8-归一化单字符分词器 
     */
    private int analyzerType;
    /**
     * 1-分词字段【过滤字段】  2-不分词 3-int范围字段 4-double范围字段
     */
    private int fieldType;
    /**
     * 特殊符号分词【analyzerType 分词类型必须是5】
     */
    private char specialChar;
    /**
     * 特殊符号分词是否归一化 true-是  false-否【analyzerType 分词类型必须是5】
     */
    private Boolean specialNormalFlag = true;
    /**
     * 0-不保存  1-保存
     */
    private int stored;
    /**
     * 0-否  1-是 ，标记字段是否是衍生字段，是否属于宽表字段
     */
    private int dbFieldFlag;
    /**
     * 衍生字段父字段【dbFieldFlag 需为 0】
     */
    private String parentField;
}
