package com.hzq.researchtest.config;

import lombok.Data;

/**
 * @author Huangzq
 * @description
 * @date 2022/12/5 14:29
 */
@Data
public class FieldDef {
    //字段名称
    private String fieldName;
    //1-ik分词  2-拼音分词 3-简拼分词 4-全拼
    private int analyzerType;
    //1-分词字段  2-不分词 3-范围字段
    private int fieldType;
    //0-不保存  1-保存
    private int stored;
    //0-否  1-是 ：标记字段是否是衍生字段或者说是否属于宽表字段
    private int dbFieldFlag;
    //衍生字段父字段
    private String parentField;
}
